package sg.act.domain.privacy

/**
 * The single chokepoint every outbound request must pass through.
 *
 * Even though the cloud engine holds an OkHttp client, it is physically unable
 * to dial out unless [assertNetworkAllowed] returns without throwing. This keeps
 * the "kill switch" guarantee enforceable in code rather than by convention:
 * one place to audit, impossible to bypass from the UI.
 */
object NetworkGuard {

    class NetworkBlockedException(message: String) : SecurityException(message)

    /**
     * @throws NetworkBlockedException when the user's privacy state forbids any
     *   network access for this request.
     */
    fun assertNetworkAllowed(state: PrivacyState) {
        if (state.networkKillSwitch) {
            throw NetworkBlockedException(
                "Network Kill Switch is ON. Domain AI refused to send anything off-device.",
            )
        }
        if (!state.cloudConsentGiven) {
            throw NetworkBlockedException(
                "Cloud has not been consented to. Domain AI stayed on-device.",
            )
        }
    }

    fun isNetworkAllowed(state: PrivacyState): Boolean =
        !state.networkKillSwitch && state.cloudConsentGiven
}
