package sg.act.domain.data.local

import android.content.Context

/**
 * Remembers the user's chat model preference across launches: whether the chosen
 * model is the configured cloud provider (true) or the on-device model (false).
 *
 * Which on-device model is active is already persisted by [ModelStore]; this only
 * records the local-vs-cloud routing choice the user made in the chat picker. The
 * value is not sensitive, so a plain SharedPreferences is sufficient.
 */
class SelectionStore(context: Context) {

    private val prefs = context.getSharedPreferences("oracle_selection", Context.MODE_PRIVATE)

    fun preferCloud(): Boolean = prefs.getBoolean(KEY_PREFER_CLOUD, false)

    fun setPreferCloud(value: Boolean) {
        prefs.edit().putBoolean(KEY_PREFER_CLOUD, value).apply()
    }

    private companion object {
        const val KEY_PREFER_CLOUD = "prefer_cloud"
    }
}
