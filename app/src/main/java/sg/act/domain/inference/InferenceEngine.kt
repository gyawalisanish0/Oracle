package sg.act.domain.inference

import sg.act.domain.data.model.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList

/** A source of completions. Implementations may be on-device or remote. */
interface InferenceEngine {

    val displayName: String

    /**
     * Stream a reply for [prompt] given prior [history], emitting incremental
     * text deltas as they are produced. On-device models emit token-by-token;
     * engines without native streaming emit a single chunk.
     *
     * @param prompt the (already redacted, if applicable) user text.
     * @param history prior turns for context.
     */
    fun generate(prompt: String, history: List<Message>): Flow<String>
}

/** Collect a full reply from [generate]. Convenient for tests and non-stream callers. */
suspend fun InferenceEngine.complete(prompt: String, history: List<Message>): String =
    generate(prompt, history).toList().joinToString("")
