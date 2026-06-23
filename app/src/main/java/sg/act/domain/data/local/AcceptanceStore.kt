package sg.act.domain.data.local

import android.content.Context

/**
 * Records that the user has accepted the Terms of Service and Privacy Policy.
 * Bumping [CURRENT_VERSION] re-prompts everyone after a material policy change.
 */
class AcceptanceStore(context: Context) {

    private val prefs = context.getSharedPreferences("oracle_acceptance", Context.MODE_PRIVATE)

    /** True once the user has accepted the current version of the agreements. */
    fun isAccepted(): Boolean = prefs.getInt(KEY_ACCEPTED_VERSION, 0) >= CURRENT_VERSION

    fun setAccepted() {
        prefs.edit().putInt(KEY_ACCEPTED_VERSION, CURRENT_VERSION).apply()
    }

    companion object {
        /** Increment when the Terms or Privacy Policy text changes materially. */
        const val CURRENT_VERSION = 1
        private const val KEY_ACCEPTED_VERSION = "accepted_version"
    }
}
