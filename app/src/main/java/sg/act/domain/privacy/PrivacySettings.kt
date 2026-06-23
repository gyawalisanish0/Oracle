package sg.act.domain.privacy

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.privacyDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "oracle_privacy",
)

/** Persists [PrivacyState] locally via DataStore. */
class PrivacySettings(private val context: Context) {

    val state: Flow<PrivacyState> = context.privacyDataStore.data.map { prefs ->
        PrivacyState(
            networkKillSwitch = prefs[KEY_KILL_SWITCH] ?: true,
            cloudConsentGiven = prefs[KEY_CLOUD_CONSENT] ?: false,
            redactBeforeCloud = prefs[KEY_REDACT] ?: true,
            allowDataLoggingModels = prefs[KEY_ALLOW_DATALOG] ?: false,
            crashReportingEnabled = prefs[KEY_CRASH_REPORTING] ?: false,
        )
    }

    suspend fun setKillSwitch(enabled: Boolean) = edit(KEY_KILL_SWITCH, enabled)

    suspend fun setCloudConsent(granted: Boolean) = edit(KEY_CLOUD_CONSENT, granted)

    suspend fun setRedactBeforeCloud(enabled: Boolean) = edit(KEY_REDACT, enabled)

    suspend fun setAllowDataLoggingModels(allowed: Boolean) = edit(KEY_ALLOW_DATALOG, allowed)

    suspend fun setCrashReporting(enabled: Boolean) = edit(KEY_CRASH_REPORTING, enabled)

    private suspend fun edit(key: Preferences.Key<Boolean>, value: Boolean) {
        context.privacyDataStore.edit { it[key] = value }
    }

    private companion object {
        val KEY_KILL_SWITCH = booleanPreferencesKey("network_kill_switch")
        val KEY_CLOUD_CONSENT = booleanPreferencesKey("cloud_consent_given")
        val KEY_REDACT = booleanPreferencesKey("redact_before_cloud")
        val KEY_ALLOW_DATALOG = booleanPreferencesKey("allow_data_logging_models")
        val KEY_CRASH_REPORTING = booleanPreferencesKey("crash_reporting_enabled")
    }
}
