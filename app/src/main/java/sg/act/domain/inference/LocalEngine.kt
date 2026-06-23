package sg.act.domain.inference

import sg.act.domain.data.model.Message
import sg.act.domain.data.model.Role
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.roundToInt

/**
 * On-device inference. This is the DEFAULT engine and runs with zero network.
 *
 * The real model is supplied at runtime through [backendProvider], which is
 * backed by [ModelManager]: when a model is loaded the provider returns a
 * [NativeBackend] (a llama.cpp context) and replies stream token-by-token; when
 * none is loaded the provider returns null and Domain AI answers with a
 * deterministic, fully-offline fallback responder so the app is always usable.
 *
 * Because the backend is resolved per call, the active model can be loaded,
 * swapped or unloaded at runtime without rebuilding the router or repository.
 */
class LocalEngine(
    private val backendProvider: () -> NativeBackend? = { null },
) : InferenceEngine {

    override val displayName: String = "On-device"

    /** Bridge to a real local model. Streams generated text token-by-token. */
    interface NativeBackend {
        val modelName: String
        fun generate(prompt: String, history: List<Message>): Flow<String>
    }

    override fun generate(prompt: String, history: List<Message>): Flow<String> {
        backendProvider()?.let { return it.generate(prompt, history) }

        return flow {
            // Simulate a brief think time so the offline fallback feels alive.
            delay(250)
            emit(fallbackRespond(prompt, history))
        }
    }

    private fun fallbackRespond(prompt: String, history: List<Message>): String {
        val text = prompt.trim()
        val lower = text.lowercase()

        return when {
            lower.isEmpty() ->
                "I'm here. Ask me anything — and it stays on this device."

            lower.containsAny("hello", "hi", "hey", "namaste") ->
                "Hello! I'm Domain AI, running entirely on your device. Nothing you " +
                    "type leaves the phone unless you explicitly turn on cloud mode."

            lower.containsAny("who are you", "what are you", "what is oracle") ->
                "I'm Domain AI — a local-first chat assistant. By default every answer " +
                    "is generated on-device with the network kill switch engaged."

            lower.containsAny("privacy", "private", "data", "tracking") ->
                "Privacy is the whole point. Conversations are stored encrypted on " +
                    "this device, there's no telemetry, and the cloud is off until you " +
                    "opt in and approve exactly what gets sent."

            lower.contains("how many words") -> {
                val n = history.lastUserText()?.wordCount() ?: text.wordCount()
                "Your previous message had about $n words."
            }

            lower.matches(Regex(""".*\b\d+\s*[+\-*/x]\s*\d+\b.*""")) ->
                tryArithmetic(text) ?: defaultReply(text)

            lower.endsWith("?") ->
                "That's a good question. Running locally I can reason over what you've " +
                    "shared in this chat. For broad, up-to-date knowledge, load an " +
                    "on-device model in Settings or enable cloud mode — I'll show you the " +
                    "redacted text first."

            else -> defaultReply(text)
        }
    }

    private fun defaultReply(text: String): String {
        val words = text.wordCount()
        return "Got it — I read your $words-word message on-device. Load a Gemma model " +
            "in Settings for full conversational answers; until then I can summarize, " +
            "reformat, do quick math, and reason about this conversation without any network."
    }

    /** Tiny, safe arithmetic evaluator for the common "12 * 8" style asks. */
    private fun tryArithmetic(text: String): String? {
        val m = Regex("""(-?\d+(?:\.\d+)?)\s*([+\-*/x])\s*(-?\d+(?:\.\d+)?)""").find(text)
            ?: return null
        val a = m.groupValues[1].toDouble()
        val b = m.groupValues[3].toDouble()
        val result = when (m.groupValues[2]) {
            "+" -> a + b
            "-" -> a - b
            "*", "x" -> a * b
            "/" -> if (b == 0.0) return "Division by zero isn't defined." else a / b
            else -> return null
        }
        val pretty = if (result == result.roundToInt().toDouble()) {
            result.roundToInt().toString()
        } else {
            "%.4f".format(result).trimEnd('0').trimEnd('.')
        }
        return "$pretty — computed locally, no network needed."
    }

    private fun String.containsAny(vararg needles: String) =
        needles.any { this.contains(it) }

    private fun String.wordCount() =
        trim().split(Regex("\\s+")).count { it.isNotBlank() }

    private fun List<Message>.lastUserText() =
        lastOrNull { it.role == Role.USER }?.text
}
