package sg.act.domain.inference

import sg.act.domain.data.model.Message
import sg.act.domain.data.model.Role
import sg.act.domain.llama.LLamaAndroid
import kotlinx.coroutines.flow.Flow

/**
 * Adapts a loaded llama.cpp context ([LLamaAndroid]) to the app's
 * [LocalEngine.NativeBackend] streaming contract. The wrapper runs all native
 * work on its dedicated thread and emits token deltas; this is a thin mapping
 * plus chat-template formatting.
 *
 * Prompts are formatted with each model's *own* embedded chat template (read
 * from the GGUF metadata), so any imported model — Gemma, Qwen, Llama, Phi,
 * Mistral — is prompted the way it was trained. Hardcoding one template (e.g.
 * Gemma's `<start_of_turn>`) makes other models echo the raw markers and run
 * away, never hitting their real stop token.
 */
class LlamaCppBackend(
    override val modelName: String,
    private val llama: LLamaAndroid,
) : LocalEngine.NativeBackend {

    override fun generate(prompt: String, history: List<Message>): Flow<String> =
        llama.sendChat(ChatFormat.turns(prompt, history), ChatFormat::fallback)
}

/**
 * Builds chat turns from prior messages plus the new prompt. The system
 * instruction is folded into the first user turn rather than sent as a separate
 * "system" role, because that works for every template — including ones with no
 * system role, like Gemma.
 */
object ChatFormat {

    private const val SYSTEM =
        "You are Domain AI, a concise and helpful assistant. If asked your name, " +
            "say you are Domain AI. Reply directly with the answer only — never " +
            "begin your message with your name or a label such as \"Domain AI:\"."

    fun turns(prompt: String, history: List<Message>): List<LLamaAndroid.ChatTurn> {
        val turns = ArrayList<LLamaAndroid.ChatTurn>(history.size + 1)
        history.forEach {
            val role = if (it.role == Role.USER) "user" else "assistant"
            turns += LLamaAndroid.ChatTurn(role, it.text.trim())
        }
        turns += LLamaAndroid.ChatTurn("user", prompt.trim())

        // Prepend the system instruction to the first user turn.
        val firstUser = turns.indexOfFirst { it.role == "user" }
        if (firstUser >= 0) {
            val merged = SYSTEM + "\n\n" + turns[firstUser].content
            turns[firstUser] = turns[firstUser].copy(content = merged)
        }
        return turns
    }

    /**
     * Generic ChatML prompt, used only when a GGUF carries no embedded template.
     * Most modern instruct models do, so this is a rare last resort.
     */
    fun fallback(turns: List<LLamaAndroid.ChatTurn>): String {
        val sb = StringBuilder()
        for (turn in turns) {
            sb.append("<|im_start|>").append(turn.role).append('\n')
                .append(turn.content).append("<|im_end|>\n")
        }
        sb.append("<|im_start|>assistant\n")
        return sb.toString()
    }
}
