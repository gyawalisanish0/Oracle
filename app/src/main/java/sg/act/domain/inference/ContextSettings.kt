package sg.act.domain.inference

import android.content.Context

/**
 * Persists the user's chosen on-device context length (n_ctx). Backed by plain
 * SharedPreferences (like [GpuGuard]) so it can be read synchronously at each
 * model load. A value of 0 means "Auto" — defer to the device-recommended size.
 */
class ContextSettings(context: Context) {

    private val prefs = context.getSharedPreferences("oracle_inference", Context.MODE_PRIVATE)

    /** Chosen context length in tokens; 0 means Auto (device-recommended). */
    fun chosenTokens(): Int = prefs.getInt(KEY_CONTEXT, 0)

    /** Set the chosen context length; pass 0 for Auto. */
    fun setChosenTokens(tokens: Int) {
        prefs.edit().putInt(KEY_CONTEXT, tokens.coerceAtLeast(0)).apply()
    }

    private companion object {
        const val KEY_CONTEXT = "context_tokens"
    }
}
