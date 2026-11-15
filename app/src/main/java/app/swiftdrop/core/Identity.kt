package app.swiftdrop.core

import android.annotation.SuppressLint
import android.content.Context
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Identitas TLS perangkat: sertifikat self-signed EC P-256 yang dibuat sekali
 * dan disimpan di penyimpanan privat aplikasi.
 *
 * Verifikasi peer TIDAK bergantung pada CA. Keamanan dijamin oleh kode verifikasi
 * (SAS) yang harus dikonfirmasi pengguna di kedua perangkat sebelum transfer.
 */
class Identity(val cert: X509Certificate, private val key: PrivateKey) {

    fun sslContext(): SSLContext {
        val ks = KeyStore.getInstance("PKCS12").apply {
            load(null, null)
            setKeyEntry(ALIAS, key, CharArray(0), arrayOf(cert))
        }
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(ks, CharArray(0))
        return SSLContext.getInstance("TLS").apply {
            init(kmf.keyManagers, arrayOf<TrustManager>(AcceptAnyPeer), SecureRandom())
        }
    }

    companion object {
        private const val ALIAS = "swiftdrop"

        fun loadOrCreate(context: Context): Identity {
            val dir = File(context.filesDir, "identity").apply { mkdirs() }
            val certFile = File(dir, "cert.der")
            val keyFile = File(dir, "key.pk8")

            if (certFile.exists() && keyFile.exists()) {
                val cert = CertificateFactory.getInstance("X.509")
                    .generateCertificate(certFile.inputStream()) as X509Certificate
                val key = KeyFactory.getInstance("EC")
                    .generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
                return Identity(cert, key)
            }

            val kp: KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
            val cert = selfSign(kp)
            certFile.writeBytes(cert.encoded)
            keyFile.writeBytes(kp.private.encoded)
            return Identity(cert, kp.private)
        }

        private fun selfSign(kp: KeyPair): X509Certificate {
            val now = System.currentTimeMillis()
            val name = X500Name("CN=SwiftDrop")
            val serial = BigInteger(64, SecureRandom())
            val builder = JcaX509v3CertificateBuilder(
                name,
                serial,
                Date(now - DAY_MS),
                Date(now + 10L * 365 * DAY_MS),
                name,
                kp.public
            )
            val signer = JcaContentSignerBuilder("SHA256withECDSA").build(kp.private)
            return JcaX509CertificateConverter().getCertificate(builder.build(signer))
        }

        private const val DAY_MS = 86_400_000L
    }
}

@SuppressLint("CustomX509TrustManager")
object AcceptAnyPeer : X509TrustManager {
    override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}
    override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {}
    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}
