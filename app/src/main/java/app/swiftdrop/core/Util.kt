package app.swiftdrop.core

import java.nio.ByteBuffer
import java.security.MessageDigest

object Util {
    const val MAX_FILES = 500
    const val MAX_NAME_LEN = 180

    fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    /**
     * Kode verifikasi 6 digit (SAS). Kedua sisi menghitung nilai yang sama karena
     * urutan sertifikat dinormalisasi. Pengguna membandingkan kode ini di kedua layar.
     */
    fun verificationCode(certA: ByteArray, certB: ByteArray): String {
        val (first, second) = if (hex(certA) <= hex(certB)) certA to certB else certB to certA
        val digest = sha256(first + second)
        val n = (ByteBuffer.wrap(digest).int.toLong() and 0xFFFFFFFFL) % 1_000_000L
        return "%03d %03d".format(n / 1000, n % 1000)
    }

    /** Membuang path, karakter kontrol, dan karakter berbahaya agar nama aman dipakai. */
    fun sanitizeFileName(raw: String): String {
        val base = raw.substringAfterLast('/').substringAfterLast('\\')
        val cleaned = base
            .map { c -> if (c.isISOControl() || c in "<>:\"|?*") '_' else c }
            .joinToString("")
            .trim()
            .trimEnd('.')
        val name = cleaned.take(MAX_NAME_LEN)
        return if (name.isEmpty() || name == "." || name == "..") "file" else name
    }
}
