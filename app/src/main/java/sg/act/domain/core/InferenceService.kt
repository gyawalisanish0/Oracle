package sg.act.domain.core

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import sg.act.domain.R

/**
 * Foreground service that holds the process at foreground priority while on-device
 * work is in flight, so the Low-Memory Killer doesn't reap a long background
 * generation or download. It carries no logic of its own — it is started, updated,
 * and stopped entirely through [ForegroundWork].
 */
class InferenceService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val text = intent?.getStringExtra(EXTRA_TEXT) ?: getString(R.string.notif_working)
        val progress = intent?.getIntExtra(EXTRA_PROGRESS, ForegroundWork.INDETERMINATE)
            ?: ForegroundWork.INDETERMINATE
        // On Android 14+ a foreground service must declare a type; on-device LLM
        // compute fits none of the predefined types, so it uses "special use".
        val type = if (Build.VERSION.SDK_INT >= 34) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            ForegroundWork.ONGOING_ID,
            ForegroundWork.buildOngoing(this, text, progress),
            type,
        )
        return START_NOT_STICKY
    }

    /** Swipe-away from recents → cancel in-flight work (the user's chosen behavior). */
    override fun onTaskRemoved(rootIntent: Intent?) {
        ForegroundWork.onStopRequested()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_TEXT = "text"
        const val EXTRA_PROGRESS = "progress"
    }
}
