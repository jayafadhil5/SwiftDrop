package app.swiftdrop.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.swiftdrop.core.FileInfo
import app.swiftdrop.core.fileInfo
import app.swiftdrop.core.HistoryEntry
import app.swiftdrop.core.History
import app.swiftdrop.core.Peer
import app.swiftdrop.core.Prefs
import app.swiftdrop.core.Role
import app.swiftdrop.core.Session
import app.swiftdrop.core.Transfers
import java.text.DateFormat
import java.util.Date

// ---------- Beranda ----------

@Composable
fun HomeScreen(onSend: () -> Unit, onReceive: () -> Unit, onHistory: () -> Unit, onSettings: () -> Unit) {
    ScreenColumn {
        Spacer(Modifier.height(24.dp))
        Heading("SwiftDrop")
        Body("Kirim file langsung antarperangkat di Wi-Fi yang sama. Tanpa cloud, tanpa akun.")
        Spacer(Modifier.height(24.dp))
        PrimaryButton("Kirim", onSend)
        SecondaryButton("Terima", onReceive)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onHistory) { Text("Riwayat") }
            TextButton(onClick = onSettings) { Text("Pengaturan") }
        }
    }
}

// ---------- Kirim: ringkasan pilihan ----------

@Composable
fun SendReviewScreen(
    files: List<FileInfo>,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val total = files.sumOf { it.size }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp)) {
        Heading("Pilih file")
        Spacer(Modifier.height(8.dp))
        Body("${files.size} file · ${formatBytes(total)}")
        Spacer(Modifier.height(16.dp))
        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(files) { i, f ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(f.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                        Text(formatBytes(f.size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { onRemove(i) }) { Text("Hapus") }
                }
                HorizontalDivider()
            }
        }
        SecondaryButton("Tambah file", onAdd)
        Spacer(Modifier.height(8.dp))
        PrimaryButton("Pilih perangkat", onNext)
        TextButton(onClick = onBack) { Text("Kembali") }
    }
}

// ---------- Kirim: daftar perangkat terdekat ----------

@Composable
fun PeersScreen(onPick: (Peer) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val own = Prefs.deviceName(context)
    val peers = remember { mutableStateListOf<Peer>() }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            Transfers.discoverPeers().collect { p ->
                if (p.name != own && peers.none { it.name == p.name }) peers.add(p)
            }
        } catch (e: Exception) {
            error = e.message
        }
    }

    ScreenColumn {
        Heading("Perangkat terdekat")
        Body("Pastikan penerima sudah menekan Terima dan berada di Wi-Fi yang sama.")
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (peers.isEmpty() && error == null) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("Mencari perangkat…")
            }
        }
        peers.forEach { p ->
            SecondaryButton(p.name, { onPick(p) })
        }
        TextButton(onClick = onBack) { Text("Kembali") }
    }
}

// ---------- Riwayat ----------

@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var entries by remember { mutableStateOf(History.load(context)) }
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp)) {
        Heading("Riwayat")
        Spacer(Modifier.height(16.dp))
        if (entries.isEmpty()) Body("Belum ada transfer.")
        LazyColumn(Modifier.weight(1f)) {
            items(entries) { e: HistoryEntry ->
                val dir = if (e.role == Role.SEND.name) "Dikirim ke" else "Diterima dari"
                Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    Text("$dir ${e.peer}", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "${fmt.format(Date(e.time))} · ${e.count} file · ${formatBytes(e.bytes)} · ${if (e.ok) "Berhasil" else "Gagal"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
            }
        }
        if (entries.isNotEmpty()) {
            SecondaryButton("Hapus riwayat", {
                History.clear(context)
                entries = emptyList()
            })
        }
        TextButton(onClick = onBack) { Text("Kembali") }
    }
}

// ---------- Pengaturan ----------

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(Prefs.deviceName(context)) }
    var saved by remember { mutableStateOf(false) }
    ScreenColumn {
        Heading("Pengaturan")
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; saved = false },
            label = { Text("Nama perangkat") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        PrimaryButton(if (saved) "Tersimpan" else "Simpan", {
            Prefs.setDeviceName(context, name)
            saved = true
        })
        Spacer(Modifier.height(8.dp))
        Body("Privasi: file dikirim langsung antarperangkat dan tidak diunggah ke server. Tidak ada iklan atau pelacak. Riwayat hanya menyimpan metadata dan dapat dihapus.")
        TextButton(onClick = onBack) { Text("Kembali") }
    }
}

// ---------- Layar sesi (pairing, transfer, hasil) ----------

@Composable
fun SessionScreen(
    session: Session,
    onCancel: () -> Unit,
    onConfirmCode: (Boolean) -> Unit,
    onApprove: (Uri) -> Unit,
    onReject: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            onApprove(uri)
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        when (session) {
            Session.Preparing, is Session.Connecting -> {
                Heading("Menghubungkan…")
                CircularProgressIndicator()
                SecondaryButton("Batal", onCancel)
            }

            is Session.Listening -> {
                Heading("Menunggu pengirim")
                Body("Pengirim akan menemukan perangkat ini di Wi-Fi yang sama.")
                QrImage(session.address, "Alamat penerima ${session.address}")
                KeyValue("Nama", session.deviceName)
                KeyValue("Alamat", session.address)
                Spacer(Modifier.height(8.dp))
                SecondaryButton("Batal", onCancel)
            }

            is Session.Verifying -> {
                Heading("Cocokkan kode")
                Body(
                    if (session.role == Role.SEND) "Pastikan kode ini sama dengan yang tampil di ${session.peerName}."
                    else "Pastikan kode ini sama dengan yang tampil di perangkat pengirim."
                )
                Text(
                    session.code,
                    style = MaterialTheme.typography.displayMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
                PrimaryButton("Kode cocok", { onConfirmCode(true) })
                SecondaryButton("Batal", { onConfirmCode(false) })
            }

            is Session.Incoming -> {
                Heading("Terima file?")
                KeyValue("Dari", session.peerName)
                KeyValue("Jumlah", "${session.files.size} file")
                KeyValue("Total", formatBytes(session.files.sumOf { it.size }))
                Body("Contoh: ${session.files.take(3).joinToString { it.name }}")
                Spacer(Modifier.height(8.dp))
                PrimaryButton("Pilih folder & setujui", { pickFolder.launch(null) })
                SecondaryButton("Tolak", onReject)
            }

            is Session.Transferring -> {
                Heading(if (session.role == Role.SEND) "Mengirim" else "Menerima")
                Body("${session.fileName}")
                LinearProgressIndicator(
                    progress = {
                        if (session.totalBytes > 0) session.bytesDone.toFloat() / session.totalBytes else 0f
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                KeyValue("Berkas", "${session.fileIndex} dari ${session.fileCount}")
                KeyValue("Progres", "${formatBytes(session.bytesDone)} / ${formatBytes(session.totalBytes)}")
                KeyValue("Kecepatan sesaat", formatSpeed(session.speedBps))
                Spacer(Modifier.height(8.dp))
                SecondaryButton("Batalkan", onCancel)
            }

            is Session.Done -> {
                Heading(if (session.ok) "Selesai" else "Gagal")
                Body(session.message)
                KeyValue("Perangkat", session.peerName)
                KeyValue("Jumlah", "${session.fileCount} file · ${formatBytes(session.totalBytes)}")
                session.savedTo?.let { Body("Tersimpan di: $it") }
                Spacer(Modifier.height(8.dp))
                PrimaryButton("Selesai", onDone)
            }

            Session.Idle -> {}
        }
    }
}

// ---------- Root ----------

enum class Tab { HOME, SEND_REVIEW, SEND_PEERS, HISTORY, SETTINGS }

@Composable
fun SwiftDropApp() {
    val session by Transfers.state.collectAsState()
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var files by remember { mutableStateOf<List<FileInfo>>(emptyList()) }

    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            files = uris.map { context.fileInfo(it) }
            tab = Tab.SEND_REVIEW
        }
    }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    // Izin notifikasi diminta saat pengguna mulai transfer, bukan saat peluncuran.
    fun requestNotificationIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    androidx.activity.compose.BackHandler(enabled = session is Session.Idle && tab != Tab.HOME) {
        tab = Tab.HOME
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (session) {
            Session.Idle -> when (tab) {
                Tab.HOME -> HomeScreen(
                    onSend = { pickFiles.launch(arrayOf("*/*")) },
                    onReceive = {
                        requestNotificationIfNeeded()
                        Transfers.startReceive()
                    },
                    onHistory = { tab = Tab.HISTORY },
                    onSettings = { tab = Tab.SETTINGS },
                )
                Tab.SEND_REVIEW -> SendReviewScreen(
                    files = files,
                    onAdd = { pickFiles.launch(arrayOf("*/*")) },
                    onRemove = { i -> files = files.filterIndexed { idx, _ -> idx != i } },
                    onBack = { tab = Tab.HOME },
                    onNext = { if (files.isNotEmpty()) tab = Tab.SEND_PEERS },
                )
                Tab.SEND_PEERS -> PeersScreen(
                    onPick = { peer ->
                        requestNotificationIfNeeded()
                        Transfers.send(files, peer)
                    },
                    onBack = { tab = Tab.SEND_REVIEW },
                )
                Tab.HISTORY -> HistoryScreen(onBack = { tab = Tab.HOME })
                Tab.SETTINGS -> SettingsScreen(onBack = { tab = Tab.HOME })
            }
            else -> SessionScreen(
                session = session,
                onCancel = { Transfers.cancel() },
                onConfirmCode = { Transfers.confirmCode(it) },
                onApprove = { Transfers.approveIncoming(it) },
                onReject = { Transfers.rejectIncoming() },
                onDone = {
                    Transfers.reset()
                    files = emptyList()
                    tab = Tab.HOME
                },
            )
        }
    }
}

