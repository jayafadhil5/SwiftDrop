package app.swiftdrop.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import app.swiftdrop.core.Role
import app.swiftdrop.core.Session
import app.swiftdrop.core.Transfers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Foreground service agar transfer panjang tetap hidup saat layar mati. */
class TransferService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var collector: Job? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Transfer file", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this, NOTIF_ID, build("Menyiapkan transfer…", null),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
        collector?.cancel()
        collector = scope.launch {
            Transfers.state.collect { s ->
                when (s) {
                    is Session.Transferring -> {
                        val label = if (s.role == Role.SEND) "Mengirim" else "Menerima"
                        val pct = if (s.totalBytes > 0) ((s.bytesDone * 100) / s.totalBytes).toInt() else 0
                        update("$label ${s.fileName} (${s.fileIndex}/${s.fileCount})", pct)
                    }
                    is Session.Done -> {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                    Session.Idle -> stopSelf()
                    else -> {}
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        collector?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun update(text: String, progress: Int?) {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, build(text, progress))
    }

    private fun build(text: String, progress: Int?) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("SwiftDrop")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .apply { if (progress != null) setProgress(100, progress, false) }
            .build()

    companion object {
        private const val CHANNEL_ID = "transfer"
        private const val NOTIF_ID = 1001
    }
}
