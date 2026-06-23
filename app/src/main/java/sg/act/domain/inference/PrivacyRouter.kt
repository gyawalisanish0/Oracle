package sg.act.domain.inference

import sg.act.domain.data.model.Message
import sg.act.domain.data.model.Route
import sg.act.domain.privacy.NetworkGuard
import sg.act.domain.privacy.PiiRedactor
import sg.act.domain.privacy.PrivacyState
import kotlinx.coroutines.flow.Flow

/**
 * Decides where a prompt is answered and enforces every privacy guarantee on the
 * way. The rules, in order:
 *
 *  1. If the user did not request cloud for this turn → answer LOCAL. Always.
 *  2. If cloud was requested, [NetworkGuard] must approve it (kill switch off and
 *     consent given) or the turn falls back to LOCAL with a note.
 *  3. Before any cloud call, the prompt and history are run through [PiiRedactor]
 *     (when enabled) and the redacted preview is recorded so the UI can show
 *     exactly what left the device.
 *
 * The routing decision and redaction are made synchronously up front; only the
 * reply body streams. The router never silently escalates from local to cloud —
 * escalation is only ever caused by an explicit per-message user action.
 */
class PrivacyRouter(
    private val local: InferenceEngine,
    private val remoteProvider: () -> Remote?,
) {

    /** The configured cloud engine plus whether it may log/train on prompts. */
    data class Remote(
        val engine: InferenceEngine,
        val logsData: Boolean = false,
    )

    data class StreamingOutcome(
        val route: Route,
        /** Redacted text actually transmitted, for CLOUD outcomes only. */
        val sentPayloadPreview: String? = null,
        /** Set when a cloud attempt was refused before streaming and we fell back. */
        val note: String? = null,
        /** Incremental text deltas of the reply. */
        val tokens: Flow<String>,
    )

    fun answer(
        prompt: String,
        history: List<Message>,
        privacy: PrivacyState,
        useCloudForThisTurn: Boolean,
    ): StreamingOutcome {
        if (!useCloudForThisTurn) {
            return StreamingOutcome(Route.LOCAL, tokens = local.generate(prompt, history))
        }

        // The user asked for cloud. Validate against the hard guarantees first.
        try {
            NetworkGuard.assertNetworkAllowed(privacy)
        } catch (e: NetworkGuard.NetworkBlockedException) {
            return localFallback(prompt, history, note = e.message)
        }

        val remote = remoteProvider()
            ?: return localFallback(
                prompt, history,
                note = "No cloud provider is configured. Answered on-device.",
            )

        // Data-logging models (e.g. OpenRouter free tier) need a second, explicit
        // consent on top of general cloud consent. Without it, stay on-device.
        if (remote.logsData && !privacy.allowDataLoggingModels) {
            return localFallback(
                prompt, history,
                note = "This model may log your data. Enable data-logging models in " +
                    "Settings to use it. Answered on-device.",
            )
        }

        val toSend: String
        val historyToSend: List<Message>
        if (privacy.redactBeforeCloud) {
            toSend = PiiRedactor.redact(prompt).redactedText
            historyToSend = history.map { it.copy(text = PiiRedactor.redact(it.text).redactedText) }
        } else {
            toSend = prompt
            historyToSend = history
        }

        return StreamingOutcome(
            route = Route.CLOUD,
            sentPayloadPreview = toSend,
            tokens = remote.engine.generate(toSend, historyToSend),
        )
    }

    private fun localFallback(
        prompt: String,
        history: List<Message>,
        note: String?,
    ): StreamingOutcome = StreamingOutcome(
        route = Route.LOCAL,
        note = note,
        tokens = local.generate(prompt, history),
    )
}
