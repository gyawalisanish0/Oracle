package sg.act.domain.inference

import sg.act.domain.data.model.Message
import sg.act.domain.data.model.Role
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * The optional, opt-in cloud provider. Talks to an OpenAI-compatible
 * `/chat/completions` endpoint (works with OpenAI, OpenRouter, local proxies, and
 * many gateways). It is only ever reached after [sg.act.domain.privacy.NetworkGuard]
 * has approved the request — see [PrivacyRouter].
 *
 * Streams token deltas via SSE (`stream: true`). If a server ignores streaming and
 * returns a normal JSON body, the single full reply is emitted instead.
 *
 * Configuration lives only in memory / encrypted prefs and is never bundled.
 */
class RemoteEngine(
    private val config: Config,
) : InferenceEngine {

    data class Config(
        val baseUrl: String,
        val apiKey: String,
        val model: String,
        /** True if this model may log/train on prompts (e.g. OpenRouter free tier). */
        val logsData: Boolean = false,
    )

    override val displayName: String = "Cloud · ${config.model}"

    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override fun generate(prompt: String, history: List<Message>): Flow<String> = flow {
        val request = Request.Builder()
            .url(config.baseUrl.trimEnd('/') + "/chat/completions")
            .header("Authorization", "Bearer ${config.apiKey}")
            .header("Content-Type", "application/json")
            // OpenRouter ranking headers; harmlessly ignored by other providers.
            .header("HTTP-Referer", "https://github.com/oracle-chat/oracle")
            .header("X-Title", "Domain AI")
            .post(buildPayload(prompt, history).toString().toRequestBody(JSON_MEDIA))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                throw IOException("Cloud provider returned ${response.code}: $body")
            }
            val body = response.body ?: throw IOException("Empty response body")
            val isEventStream = response.header("Content-Type").orEmpty().contains("text/event-stream")

            if (isEventStream) {
                val source = body.source()
                while (!source.exhausted()) {
                    coroutineContext.ensureActive() // stop streaming promptly on cancel
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.substringAfter("data:").trim()
                    if (data == "[DONE]") break
                    val delta = parseDelta(data)
                    if (delta.isNotEmpty()) emit(delta)
                }
            } else {
                // Server didn't stream — emit the one full reply.
                emit(parseContent(body.string()))
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun buildPayload(prompt: String, history: List<Message>): JsonObject =
        buildJsonObject {
            put("model", config.model)
            put("temperature", 0.7)
            put("stream", true)
            putJsonArray("messages") {
                add(messageObject("system", SYSTEM_PROMPT))
                for (m in history) {
                    add(messageObject(if (m.role == Role.USER) "user" else "assistant", m.text))
                }
                add(messageObject("user", prompt))
            }
        }

    private fun messageObject(role: String, content: String): JsonObject =
        buildJsonObject {
            put("role", role)
            put("content", content)
        }

    /** Extract the incremental `choices[0].delta.content` from one SSE data frame. */
    private fun parseDelta(data: String): String = runCatching {
        json.parseToJsonElement(data).jsonObject["choices"]?.jsonArray
            ?.firstOrNull()?.jsonObject
            ?.get("delta")?.jsonObject
            ?.get("content")?.jsonPrimitive?.contentOrNull
    }.getOrNull().orEmpty()

    /** Parse a non-streamed full completion body. */
    private fun parseContent(body: String): String = runCatching {
        json.parseToJsonElement(body).jsonObject["choices"]?.jsonArray
            ?.firstOrNull()?.jsonObject
            ?.get("message")?.jsonObject
            ?.get("content")?.jsonPrimitive?.contentOrNull?.trim()
    }.getOrNull() ?: "(Cloud returned an empty message.)"

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        const val SYSTEM_PROMPT =
            "You are Domain AI, a concise and helpful assistant. If asked your name, " +
                "say you are Domain AI. Reply directly with the answer only — never " +
                "begin your message with your name or a label such as \"Domain AI:\"."
    }
}
