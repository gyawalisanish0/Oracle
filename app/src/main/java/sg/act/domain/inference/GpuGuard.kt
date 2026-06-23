package sg.act.domain.inference

import android.content.Context

/**
 * Self-healing guard around GPU offload. A failed GPU load on a flaky driver
 * (e.g. Adreno 610) typically *aborts the whole process* rather than throwing,
 * so a Kotlin try/catch can't recover. Instead we persist an "attempting" marker
 * right before each GPU load and clear it right after: if the marker is still set
 * on the next launch, the previous attempt crashed the process, so we disable GPU
 * offload and fall back to CPU — avoiding a crash loop. Soft errors that *do*
 * throw are handled by the caller's catch and simply clear the marker.
 */
class GpuGuard(context: Context) {

    private val prefs = context.getSharedPreferences("oracle_gpu", Context.MODE_PRIVATE)

    /**
     * Layers to request for the next load: full offload (99) unless the user has
     * turned GPU off, or it was auto-disabled after a crash. Detects a crashed
     * previous attempt first.
     */
    fun layersToOffload(): Int {
        if (!userEnabled()) return 0
        if (prefs.getBoolean(KEY_ATTEMPTING, false)) {
            // Marker survived from a previous run → that load aborted the process.
            prefs.edit().putBoolean(KEY_DISABLED, true).putBoolean(KEY_ATTEMPTING, false).commit()
        }
        return if (prefs.getBoolean(KEY_DISABLED, false)) 0 else FULL_OFFLOAD
    }

    /**
     * User preference: whether GPU offload is allowed (default on). It's safe to
     * default on because ggml only offers GPUs it can actually use, and the loader
     * steps down to CPU otherwise — so this never crashes, it just may not help.
     * Use the in-app benchmark to see whether GPU is faster on a given device.
     */
    fun userEnabled(): Boolean = prefs.getBoolean(KEY_USER_ENABLED, true)

    /** Set the user GPU preference. Turning it back on also clears a crash-disable. */
    fun setUserEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_USER_ENABLED, enabled)
            .apply { if (enabled) putBoolean(KEY_DISABLED, false).putBoolean(KEY_ATTEMPTING, false) }
            .commit()
    }

    /** Synchronously persist that a GPU load is about to run (only when offloading). */
    fun beginAttempt(layers: Int) {
        if (layers > 0) prefs.edit().putBoolean(KEY_ATTEMPTING, true).commit()
    }

    /** Clear the attempt marker once a load returns (success or soft failure). */
    fun endAttempt() {
        prefs.edit().putBoolean(KEY_ATTEMPTING, false).commit()
    }

    /** Whether GPU offload has been auto-disabled after a crash. */
    fun isGpuDisabled(): Boolean = prefs.getBoolean(KEY_DISABLED, false)

    /** Re-enable GPU offload for a fresh attempt (user-initiated retry). */
    fun reset() {
        prefs.edit().putBoolean(KEY_DISABLED, false).putBoolean(KEY_ATTEMPTING, false).commit()
    }

    private companion object {
        const val FULL_OFFLOAD = 99
        const val KEY_ATTEMPTING = "attempting"
        const val KEY_DISABLED = "disabled"
        const val KEY_USER_ENABLED = "user_enabled"
    }
}
