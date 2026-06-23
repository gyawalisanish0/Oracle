package sg.act.domain.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import sg.act.domain.R

/**
 * Posts and updates a single progress notification for on-device model downloads,
 * so progress is visible even when the app is backgrounded. Safe to call without
 * the POST_NOTIFICATIONS permission — the system simply drops the post.
 */
class DownloadNotifier(private val context: Context) {

    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager?.createNotificationChannel(channel)
    }

    fun progress(modelName: String, fraction: Float) {
        val pct = (fraction * 100).toInt().coerceIn(0, 100)
        post(
            base(context.getString(R.string.notif_downloading, modelName))
                .setProgress(100, pct, false)
                .setOngoing(true)
                .build(),
        )
    }

    fun indeterminate(text: String) {
        post(base(text).setProgress(0, 0, true).setOngoing(true).build())
    }

    fun complete(modelName: String) {
        post(
            base(context.getString(R.string.notif_complete, modelName))
                .setProgress(0, 0, false)
                .setOngoing(false)
                .setAutoCancel(true)
                .build(),
        )
    }

    fun failed() {
        post(
            base(context.getString(R.string.notif_failed))
                .setProgress(0, 0, false)
                .setOngoing(false)
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun base(text: String) = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle(context.getString(R.string.app_name))
        .setContentText(text)
        .setOnlyAlertOnce(true)

    private fun post(notification: Notification) {
        runCatching { manager?.notify(NOTIF_ID, notification) }
    }

    private companion object {
        const val CHANNEL_ID = "model_downloads"
        const val NOTIF_ID = 1001
    }
}
