package sg.act.domain.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import sg.act.domain.R
import java.util.concurrent.atomic.AtomicInteger

/**
 * App-facing controller for [InferenceService], the foreground service that keeps
 * the process at foreground priority while on-device work — a reply generation or a
 * model download — is in flight, so Android's Low-Memory Killer doesn't reap a long
 * background job.
 *
 * In-flight operations are reference-counted: the service runs while the count is
 * `> 0` and stops when it reaches 0. A single ongoing notification describes the
 * current work; its text follows the most recent [begin]/[update]. Terminal
 * "ready"/"failed" notices are posted separately so they survive the ongoing
 * notification being cleared.
 */
object ForegroundWork {

    const val ONGOING_ID = 1001
    private const val TERMINAL_ID = 1002
    const val CHANNEL_ID = "ondevice_work"

    /** No progress bar. */
    const val NO_PROGRESS = -1
    /** Indeterminate (spinner) progress bar. */
    const val INDETERMINATE = -2

    private val active = AtomicInteger(0)

    /**
     * Invoked when the user swipes the app away from recents (task removed), so any
     * in-flight background work can be cancelled. Generation runs on the UI scope and
     * is cancelled automatically when the Activity is destroyed; this hook covers work
     * on the app scope (downloads). Set once at startup.
     */
    @Volatile
    var onStopRequested: () -> Unit = {}

    /**
     * Mark an operation started and (re)post the ongoing notification, starting the
     * foreground service if it isn't already running. Must be called while the app is
     * in the foreground (it always is — both generation and downloads begin from a
     * user action), so starting a foreground service is permitted.
     */
    fun begin(context: Context, text: String, progress: Int = INDETERMINATE) {
        active.incrementAndGet()
        val intent = Intent(context, InferenceService::class.java)
            .putExtra(InferenceService.EXTRA_TEXT, text)
            .putExtra(InferenceService.EXTRA_PROGRESS, progress)
        runCatching { ContextCompat.startForegroundService(context, intent) }
    }

    /** Update the ongoing notification (e.g. download progress). No-op when idle. */
    fun update(context: Context, text: String, progress: Int = NO_PROGRESS) {
        if (active.get() <= 0) return
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        runCatching { mgr.notify(ONGOING_ID, buildOngoing(context, text, progress)) }
    }

    /** Mark an operation finished; stops the service when none remain. */
    fun end(context: Context) {
        if (active.decrementAndGet() <= 0) {
            active.set(0)
            // stopService is permitted from the background (unlike startService).
            runCatching { context.stopService(Intent(context, InferenceService::class.java)) }
        }
    }

    /** Post a transient "ready" notice, separate from the ongoing notification. */
    fun complete(context: Context, text: String) = terminal(context, text)

    /** Post a transient "failed" notice, separate from the ongoing notification. */
    fun failed(context: Context, text: String) = terminal(context, text)

    private fun terminal(context: Context, text: String) {
        ensureChannel(context)
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        runCatching { mgr.notify(TERMINAL_ID, notification) }
    }

    /** Build the ongoing notification — shared by the service and [update]. */
    fun buildOngoing(context: Context, text: String, progress: Int): Notification {
        ensureChannel(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        when {
            progress >= 0 -> builder.setProgress(100, progress.coerceIn(0, 100), false)
            progress == INDETERMINATE -> builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    private fun ensureChannel(context: Context) {
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notif_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
    }
}
