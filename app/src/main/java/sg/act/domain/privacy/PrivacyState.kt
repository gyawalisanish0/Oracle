package sg.act.domain.privacy

/**
 * User-facing privacy configuration. Pure data — deliberately free of Android
 * dependencies so the routing and guard logic that consumes it can be unit
 * tested on the JVM.
 *
 * Defaults are intentionally the most private possible:
 *  - the network kill switch is ON,
 *  - cloud usage has NOT been consented to,
 *  - PII redaction is required before any cloud send.
 */
data class PrivacyState(
    val networkKillSwitch: Boolean = true,
    val cloudConsentGiven: Boolean = false,
    val redactBeforeCloud: Boolean = true,
    /**
     * Separate, stricter consent for cloud models that may log or train on
     * prompts (e.g. OpenRouter free models). Off by default; required — on top of
     * [cloudConsentGiven] — before any data-logging model is used.
     */
    val allowDataLoggingModels: Boolean = false,
    /**
     * Opt-in crash reporting (Firebase Crashlytics). Off by default to honor the
     * no-telemetry posture; when enabled, only crash/error reports are sent — no
     * analytics or usage tracking.
     */
    val crashReportingEnabled: Boolean = false,
)
