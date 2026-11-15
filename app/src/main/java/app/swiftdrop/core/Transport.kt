package app.swiftdrop.core

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

data class Peer(val name: String, val host: InetAddress, val port: Int)

/** Abstraksi transport agar protokol dan UI tidak terikat satu metode koneksi. */
interface Transport {
    fun discover(): Flow<Peer>
    suspend fun connect(peer: Peer): SSLSocket
    fun close()
}

/**
 * Transport jaringan lokal (Wi-Fi yang sama) dengan penemuan mDNS via NsdManager.
 * Wi-Fi Direct belum diimplementasikan.
 */
class LanTransport(private val context: Context, private val identity: Identity) : Transport {

    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val tls = identity.sslContext()

    @Volatile private var server: SSLServerSocket? = null
    private var registration: NsdManager.RegistrationListener? = null

    /** Membuka server TLS dan mengiklankannya. Mengembalikan port yang dipakai. */
    fun startServer(deviceName: String): Int {
        val srv = tls.serverSocketFactory.createServerSocket(0) as SSLServerSocket
        srv.needClientAuth = true
        srv.enabledProtocols = PROTOCOLS
        server = srv

        val info = NsdServiceInfo().apply {
            serviceName = NAME_PREFIX + deviceName.take(40)
            serviceType = SERVICE_TYPE
            port = srv.localPort
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(i: NsdServiceInfo) {}
            override fun onRegistrationFailed(i: NsdServiceInfo, code: Int) {}
            override fun onServiceUnregistered(i: NsdServiceInfo) {}
            override fun onUnregistrationFailed(i: NsdServiceInfo, code: Int) {}
        }
        nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
        registration = listener
        return srv.localPort
    }

    /** Menunggu satu koneksi masuk dan menyelesaikan handshake TLS. */
    fun acceptOne(): SSLSocket {
        val srv = checkNotNull(server) { "Server belum berjalan" }
        val s = srv.accept() as SSLSocket
        s.enabledProtocols = PROTOCOLS
        s.soTimeout = SESSION_TIMEOUT_MS
        s.startHandshake()
        return s
    }

    fun stopServer() {
        registration?.let { runCatching { nsd.unregisterService(it) } }
        registration = null
        server?.let { runCatching { it.close() } }
        server = null
    }

    override fun discover(): Flow<Peer> = callbackFlow {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                close(IllegalStateException("Pencarian perangkat gagal (kode $errorCode)"))
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onServiceLost(info: NsdServiceInfo) {}
            override fun onServiceFound(info: NsdServiceInfo) {
                if (!info.serviceType.contains("_swiftdrop")) return
                @Suppress("DEPRECATION")
                nsd.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(i: NsdServiceInfo, errorCode: Int) {}
                    override fun onServiceResolved(i: NsdServiceInfo) {
                        val host = i.host ?: return
                        trySend(Peer(i.serviceName.removePrefix(NAME_PREFIX), host, i.port))
                    }
                })
            }
        }
        nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        awaitClose { runCatching { nsd.stopServiceDiscovery(listener) } }
    }

    override suspend fun connect(peer: Peer): SSLSocket = withContext(Dispatchers.IO) {
        val s = tls.socketFactory.createSocket(peer.host, peer.port) as SSLSocket
        s.enabledProtocols = PROTOCOLS
        s.soTimeout = SESSION_TIMEOUT_MS
        s.startHandshake()
        s
    }

    override fun close() = stopServer()

    companion object {
        const val SERVICE_TYPE = "_swiftdrop._tcp"
        const val NAME_PREFIX = "SwiftDrop-"
        const val SESSION_TIMEOUT_MS = 180_000
        private val PROTOCOLS = arrayOf("TLSv1.3", "TLSv1.2")
    }
}

/** Alamat IPv4 LAN perangkat ini, untuk ditampilkan di layar penerima. */
fun localIpv4(): String? = runCatching {
    java.util.Collections.list(NetworkInterface.getNetworkInterfaces())
        .flatMap { java.util.Collections.list(it.inetAddresses) }
        .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
        ?.hostAddress
}.getOrNull()
