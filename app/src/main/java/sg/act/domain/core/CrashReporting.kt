package sg.act.domain.core

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Thin, privacy-respecting wrapper over Firebase Crashlytics.
 *
 * - No-ops entirely when Firebase is not configured (no google-services.json), so
 *   the app behaves identically — and ships no telemetry — without a Firebase
 *   project.
 * - Collection is opt-in: it stays disabled until [setEnabled] is called with the
 *   user's explicit crash-reporting consent. Only crash/error reports are sent;
 *   no analytics or usage tracking is wired in.
 */
object CrashReporting {

    @Volatile
    private var available = false

    fun init(context: Context) {
        available = FirebaseApp.getApps(context).isNotEmpty()
        if (available) {
            // Disabled until the user opts in via Settings.
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(false)
        }
    }

    fun setEnabled(enabled: Boolean) {
        if (!available) return
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(enabled)
    }

    /** Record a non-fatal error for diagnostics (only when configured and enabled). */
    fun record(throwable: Throwable) {
        if (!available) return
        FirebaseCrashlytics.getInstance().recordException(throwable)
    }

    /** Add a breadcrumb to the timeline attached to the next crash/non-fatal. */
    fun log(message: String) {
        if (!available) return
        FirebaseCrashlytics.getInstance().log(message)
    }

    /** Attach a custom key so recorded events carry diagnostic context. */
    fun setKey(key: String, value: String) {
        if (!available) return
        FirebaseCrashlytics.getInstance().setCustomKey(key, value)
    }

    fun setKey(key: String, value: Long) {
        if (!available) return
        FirebaseCrashlytics.getInstance().setCustomKey(key, value)
    }

    fun setKey(key: String, value: Int) {
        if (!available) return
        FirebaseCrashlytics.getInstance().setCustomKey(key, value)
    }
}
