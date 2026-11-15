package app.swiftdrop.core

import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * Framing sederhana di atas kanal TLS.
 * Setiap frame: 1 byte jenis, lalu:
 *  - JSON: int panjang + bytes UTF-8
 *  - DATA: int panjang + bytes file
 */
object Frames {
    const val KIND_JSON = 1
    const val KIND_DATA = 2
    const val MAX_JSON = 256 * 1024

    /** Ukuran chunk perlu diuji ulang lewat benchmark di perangkat nyata. */
    const val CHUNK_SIZE = 256 * 1024

    fun writeJson(out: DataOutputStream, obj: JSONObject) {
        val bytes = obj.toString().toByteArray(Charsets.UTF_8)
        out.writeByte(KIND_JSON)
        out.writeInt(bytes.size)
        out.write(bytes)
        out.flush()
    }

    fun writeData(out: DataOutputStream, buf: ByteArray, len: Int) {
        out.writeByte(KIND_DATA)
        out.writeInt(len)
        out.write(buf, 0, len)
    }

    fun readKind(input: DataInputStream): Int = input.readUnsignedByte()

    fun readJson(input: DataInputStream): JSONObject {
        require(readKind(input) == KIND_JSON) { "Frame tak terduga" }
        return readJsonBody(input)
    }

    fun readJsonBody(input: DataInputStream): JSONObject {
        val len = input.readInt()
        require(len in 1..MAX_JSON) { "Ukuran pesan tidak valid" }
        val bytes = ByteArray(len)
        input.readFully(bytes)
        return JSONObject(String(bytes, Charsets.UTF_8))
    }

    fun readDataLen(input: DataInputStream): Int {
        val len = input.readInt()
        require(len in 1..CHUNK_SIZE) { "Ukuran chunk tidak valid" }
        return len
    }
}
