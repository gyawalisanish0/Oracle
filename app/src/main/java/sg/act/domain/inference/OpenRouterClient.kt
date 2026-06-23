package sg.act.domain.inference

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Talks to OpenRouter's public catalog to list currently-free chat models.
 *
 * Listing models is configuration metadata — it sends no user prompt content —
 * so it is performed when the user is setting up the provider, independent of the
 * inference kill switch. The completion calls themselves still go through
 * [RemoteEngine] and [PrivacyRouter] like any other cloud request.
 *
 * Free models are treated as data-logging (OpenRouter's free tier may log/train
 * on prompts), so each is flagged accordingly and gated behind the separate
 * data-logging consent.
 */
class OpenRouterClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) {

    data class FreeModel(
        val id: String,
        val name: String,
        val contextLength: Int,
        /** Free OpenRouter models may log/train on prompts. */
        val logsData: Boolean = true,
    )

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchFreeModels(apiKey: String?): List<FreeModel> = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url("$BASE_URL/models")
        if (!apiKey.isNullOrBlank()) builder.header("Authorization", "Bearer $apiKey")

        client.newCall(builder.build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("OpenRouter returned ${response.code}")
            }
            parseFreeModels(body)
        }
    }

    private fun parseFreeModels(body: String): List<FreeModel> {
        val data = json.parseToJsonElement(body).jsonObject["data"]?.jsonArray ?: return emptyList()
        return data.mapNotNull { element ->
            val obj = element.jsonObject
            val pricing = obj["pricing"]?.jsonObject ?: return@mapNotNull null
            val promptPrice = pricing["prompt"]?.jsonPrimitive?.contentOrNull
            val completionPrice = pricing["completion"]?.jsonPrimitive?.contentOrNull
            if (promptPrice != "0" || completionPrice != "0") return@mapNotNull null

            // Keep text-output chat models only (drop image/audio generators).
            val outputs = obj["architecture"]?.jsonObject
                ?.get("output_modalities")?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                .orEmpty()
            if (outputs != listOf("text")) return@mapNotNull null

            val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: id
            val ctx = obj["context_length"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
            FreeModel(id = id, name = name, contextLength = ctx)
        }.sortedBy { it.name.lowercase() }
    }

    companion object {
        const val BASE_URL = "https://openrouter.ai/api/v1"
    }
}
