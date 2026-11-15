package app.swiftdrop.core

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import app.swiftdrop.service.TransferService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.OutputStream
import java.security.MessageDigest
import javax.net.ssl.SSLSocket

enum class Role { SEND, RECEIVE }

data class FileInfo(val name: String, val size: Long, val uri: Uri? = null)

sealed interface Session {
    data object Idle : Session
    data object Preparing : Session
    data class Listening(val deviceName: String, val address: String) : Session
    data class Connecting(val peerName: String) : Session
    data class Verifying(val role: Role, val peerName: String, val code: String) : Session
    data class Incoming(val peerName: String, val code: String, val files: List<FileInfo>) : Session
    data class Transferring(
        val role: Role,
        val peerName: String,
        val fileName: String,
        val fileIndex: Int,
        val fileCount: Int,
        val bytesDone: Long,
        val totalBytes: Long,
        val speedBps: Long,
    ) : Session
    data class Done(
        val role: Role,
        val peerName: String,
        val fileCount: Int,
        val totalBytes: Long,
        val ok: Boolean,
        val message: String,
        val savedTo: String?,
    ) : Session
}

/** Satu-satunya sumber kebenaran status sesi. Dipakai UI dan TransferService. */
object Transfers {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<Session>(Session.Idle)
    val state: StateFlow<Session> = _state.asStateFlow()

    private lateinit var app: Context
    private lateinit var identity: Identity
    private lateinit var transport: LanTransport

    private var job: Job? = null
    private var socket: SSLSocket? = null
    @Volatile private var cancelled = false
    @Volatile private var codeDecision: CompletableDeferred<Boolean>? = null
    @Volatile private var incomingDecision: CompletableDeferred<Uri?>? = null

    private var role = Role.SEND
    private var peerName = ""
    private var fileCount = 0
    private var totalBytes = 0L

    @Synchronized
    fun init(context: Context) {
        if (::app.isInitialized) return
        app = context.applicationContext
        identity = Identity.loadOrCreate(app)
        transport = LanTransport(app, identity)
    }

    fun discoverPeers(): Flow<Peer> = transport.discover()

    /** Kirim: hubungkan ke peer, konfirmasi kode, lalu kirim file. */
    fun send(files: List<FileInfo>, peer: Peer) = launchSession {
        role = Role.SEND
        peerName = peer.name
        fileCount = files.size
        totalBytes = files.sumOf { it.size.coerceAtLeast(0) }
        _state.value = Session.Connecting(peer.name)

        val sock = transport.connect(peer)
        socket = sock
        val code = Util.verificationCode(identity.cert.encoded, sock.session.peerCertificates[0].encoded)
        check(awaitCode(Role.SEND, peer.name, code)) { "Kode verifikasi tidak dikonfirmasi" }

        runSend(sock, files)
        finish(true, "Semua file terkirim dan terverifikasi", null)
    }

    /** Terima: buka server, tunggu satu pengirim, konfirmasi kode, lalu simpan file. */
    fun startReceive() = launchSession {
        role = Role.RECEIVE
        val name = Prefs.deviceName(app)
        val port = transport.startServer(name)
        _state.value = Session.Listening(name, "${localIpv4() ?: "-"}:$port")

        val sock = transport.acceptOne()
        transport.stopServer()
        socket = sock
        val code = Util.verificationCode(identity.cert.encoded, sock.session.peerCertificates[0].encoded)
        check(awaitCode(Role.RECEIVE, "Pengirim", code)) { "Kode verifikasi tidak dikonfirmasi" }

        val savedTo = runReceive(sock, code)
        finish(true, "Semua file tersimpan dan terverifikasi", savedTo)
    }

    fun confirmCode(accept: Boolean) {
        codeDecision?.complete(accept)
    }

    fun approveIncoming(treeUri: Uri) {
        incomingDecision?.complete(treeUri)
    }

    fun rejectIncoming() {
        incomingDecision?.complete(null)
    }

    fun cancel() {
        cancelled = true
        codeDecision?.complete(false)
        incomingDecision?.complete(null)
        closeSockets()
        transport.stopServer()
    }

    fun reset() {
        if (_state.value is Session.Done) _state.value = Session.Idle
    }

    // ---------- internal ----------

    private fun launchSession(block: suspend () -> Unit) {
        val current = _state.value
        if (current !is Session.Idle && current !is Session.Done) return
        cancelled = false
        _state.value = Session.Preparing
        ContextCompat.startForegroundService(app, android.content.Intent(app, TransferService::class.java))
        job = scope.launch {
            try {
                block()
            } catch (e: Exception) {
                finish(false, if (cancelled) "Dibatalkan" else (e.message ?: "Terjadi kesalahan"), null)
            } finally {
                closeSockets()
                transport.stopServer()
            }
        }
    }

    private suspend fun awaitCode(r: Role, name: String, code: String): Boolean {
        val d = CompletableDeferred<Boolean>()
        codeDecision = d
        _state.value = Session.Verifying(r, name, code)
        return withTimeoutOrNull(120_000) { d.await() } ?: false
    }

    private fun finish(ok: Boolean, message: String, savedTo: String?) {
        _state.value = Session.Done(role, peerName, fileCount, totalBytes, ok, message, savedTo)
        History.add(app, HistoryEntry(System.currentTimeMillis(), role.name, peerName, fileCount, totalBytes, ok))
    }

    private fun closeSockets() {
        socket?.let { runCatching { it.close() } }
        socket = null
    }

    private suspend fun runSend(sock: SSLSocket, files: List<FileInfo>) {
        val input = DataInputStream(BufferedInputStream(sock.inputStream))
        val out = DataOutputStream(BufferedOutputStream(sock.outputStream))

        val list = JSONArray(files.map { JSONObject().put("name", it.name).put("size", it.size.coerceAtLeast(0)) })
        Frames.writeJson(
            out,
            JSONObject().put("type", "hello").put("name", Prefs.deviceName(app)).put("files", list)
        )
        val reply = Frames.readJson(input)
        check(reply.optString("type") == "accept") { reply.optString("reason", "Sesi ditolak penerima") }

        val meter = Meter(Role.SEND, peerName, files)
        val buf = ByteArray(Frames.CHUNK_SIZE)
        files.forEachIndexed { idx, f ->
            Frames.writeJson(out, JSONObject().put("type", "file_start").put("index", idx).put("name", f.name))
            val digest = MessageDigest.getInstance("SHA-256")
            val source = app.contentResolver.openInputStream(f.uri ?: error("Berkas tidak tersedia"))
                ?: error("Tidak dapat membaca ${f.name}")
            source.use { stream ->
                while (true) {
                    val n = stream.read(buf)
                    if (n <= 0) break
                    digest.update(buf, 0, n)
                    Frames.writeData(out, buf, n)
                    meter.add(n, f.name, idx)
                }
            }
            out.flush()
            Frames.writeJson(
                out,
                JSONObject().put("type", "file_end").put("index", idx).put("sha256", Util.hex(digest.digest()))
            )
            check(Frames.readJson(input).optString("type") == "file_ok") { "Verifikasi gagal untuk ${f.name}" }
        }
        Frames.writeJson(out, JSONObject().put("type", "done"))
        check(Frames.readJson(input).optString("type") == "complete") { "Penerima tidak mengonfirmasi" }
    }

    private suspend fun runReceive(sock: SSLSocket, code: String): String {
        val input = DataInputStream(BufferedInputStream(sock.inputStream))
        val out = DataOutputStream(BufferedOutputStream(sock.outputStream))

        val hello = Frames.readJson(input)
        check(hello.optString("type") == "hello") { "Protokol tidak valid" }
        peerName = Util.sanitizeFileName(hello.optString("name", "Pengirim"))
        val files = parseFiles(hello)
        fileCount = files.size
        totalBytes = files.sumOf { it.size }

        val decision = CompletableDeferred<Uri?>()
        incomingDecision = decision
        _state.value = Session.Incoming(peerName, code, files)
        val tree = withTimeoutOrNull(180_000) { decision.await() }
        if (tree == null) {
            Frames.writeJson(out, JSONObject().put("type", "reject").put("reason", "Penerima menolak sesi"))
            error("Sesi ditolak")
        }

        val root = DocumentFile.fromTreeUri(app, tree) ?: error("Folder tidak dapat diakses")
        Frames.writeJson(out, JSONObject().put("type", "accept"))

        val meter = Meter(Role.RECEIVE, peerName, files)
        val buf = ByteArray(Frames.CHUNK_SIZE)
        var stream: OutputStream? = null
        var doc: DocumentFile? = null
        var digest: MessageDigest? = null
        var idx = -1
        try {
            loop@ while (true) {
                if (Frames.readKind(input) == Frames.KIND_DATA) {
                    val len = Frames.readDataLen(input)
                    input.readFully(buf, 0, len)
                    val s = checkNotNull(stream) { "Data tanpa berkas aktif" }
                    s.write(buf, 0, len)
                    digest!!.update(buf, 0, len)
                    meter.add(len, files[idx].name, idx)
                    continue@loop
                }
                val msg = Frames.readJsonBody(input)
                when (msg.optString("type")) {
                    "file_start" -> {
                        val i = msg.getInt("index")
                        check(i == idx + 1 && i < files.size) { "Urutan berkas tidak valid" }
                        idx = i
                        val d = createUnique(root, Util.sanitizeFileName(msg.getString("name")))
                        doc = d
                        stream = app.contentResolver.openOutputStream(d.uri, "w")
                            ?: throw IOException("Tidak dapat menulis berkas")
                        digest = MessageDigest.getInstance("SHA-256")
                    }
                    "file_end" -> {
                        stream?.close()
                        stream = null
                        val actual = Util.hex(digest!!.digest())
                        if (actual != msg.getString("sha256")) {
                            doc?.delete()
                            Frames.writeJson(out, JSONObject().put("type", "file_bad"))
                            error("Checksum tidak cocok untuk ${files[idx].name}")
                        }
                        Frames.writeJson(out, JSONObject().put("type", "file_ok"))
                    }
                    "done" -> {
                        Frames.writeJson(out, JSONObject().put("type", "complete"))
                        break@loop
                    }
                    else -> error("Pesan tidak dikenal")
                }
            }
        } finally {
            stream?.close()
        }
        return tree.toString()
    }

    private fun parseFiles(hello: JSONObject): List<FileInfo> {
        val arr = hello.getJSONArray("files")
        require(arr.length() in 1..Util.MAX_FILES) { "Jumlah berkas tidak valid" }
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            FileInfo(Util.sanitizeFileName(o.getString("name")), o.getLong("size").coerceAtLeast(0))
        }
    }

    /** Menghindari penimpaan diam-diam: nama bentrok menjadi "nama (1).ext". */
    private fun createUnique(root: DocumentFile, name: String): DocumentFile {
        val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
        val base = name.substring(0, dot)
        val ext = name.substring(dot)
        var candidate = name
        var n = 1
        while (root.findFile(candidate) != null) {
            candidate = "$base ($n)$ext"
            n++
        }
        return root.createFile("application/octet-stream", candidate)
            ?: throw IOException("Tidak dapat membuat berkas")
    }

    /** Menghitung progres dan kecepatan sesaat, diperbarui maksimal tiap 300 ms. */
    private class Meter(private val role: Role, private val peer: String, files: List<FileInfo>) {
        private val count = files.size
        private val total = files.sumOf { it.size.coerceAtLeast(0) }
        private var done = 0L
        private var lastNs = System.nanoTime()
        private var lastBytes = 0L

        fun add(n: Int, fileName: String, idx: Int) {
            done += n
            val now = System.nanoTime()
            val dt = now - lastNs
            if (dt >= 300_000_000L) {
                val speed = ((done - lastBytes) * 1_000_000_000L) / dt
                lastNs = now
                lastBytes = done
                _state.value = Session.Transferring(role, peer, fileName, idx + 1, count, done, total, speed)
            }
        }
    }
}

/** Membaca nama dan ukuran berkas dari URI yang dipilih pengguna. */
fun Context.fileInfo(uri: Uri): FileInfo {
    var name = "berkas"
    var size = 0L
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
        ?.use { c ->
            if (c.moveToFirst()) {
                name = c.getString(0) ?: name
                size = if (c.isNull(1)) 0L else c.getLong(1)
            }
        }
    return FileInfo(Util.sanitizeFileName(name), size, uri)
}

