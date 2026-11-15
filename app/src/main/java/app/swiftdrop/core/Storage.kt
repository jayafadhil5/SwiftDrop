package app.swiftdrop.core

import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

object Prefs {
    private const val FILE = "swiftdrop"

    fun deviceName(ctx: Context): String =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("device_name", null)
            ?: Build.MODEL

    fun setDeviceName(ctx: Context, name: String) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString("device_name", name.trim().take(40).ifEmpty { Build.MODEL }).apply()
    }
}

/** Riwayat hanya berisi metadata: tidak ada nama file lengkap atau isi file. */
data class HistoryEntry(
    val time: Long,
    val role: String,
    val peer: String,
    val count: Int,
    val bytes: Long,
    val ok: Boolean,
)

object History {
    private const val FILE = "swiftdrop"
    private const val KEY = "history"
    private const val MAX = 200

    fun load(ctx: Context): List<HistoryEntry> {
        val raw = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        val arr = JSONArray(raw)
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            HistoryEntry(
                time = o.getLong("time"),
                role = o.getString("role"),
                peer = o.getString("peer"),
                count = o.getInt("count"),
                bytes = o.getLong("bytes"),
                ok = o.getBoolean("ok"),
            )
        }
    }

    fun add(ctx: Context, entry: HistoryEntry) {
        val list = (listOf(entry) + load(ctx)).take(MAX)
        save(ctx, list)
    }

    fun clear(ctx: Context) = save(ctx, emptyList())

    private fun save(ctx: Context, list: List<HistoryEntry>) {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(
                JSONObject()
                    .put("time", e.time).put("role", e.role).put("peer", e.peer)
                    .put("count", e.count).put("bytes", e.bytes).put("ok", e.ok)
            )
        }
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }
}
