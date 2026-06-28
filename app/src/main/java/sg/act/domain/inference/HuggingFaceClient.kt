package sg.act.domain.inference

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Fetches inference-ready text-generation models from the HF Hub API.
 *
 * This is configuration metadata — no user prompt is ever sent here — so it is
 * performed during setup, independent of the inference kill switch. Actual
 * chat requests go through [RemoteEngine] and [PrivacyRouter] as normal.
 *
 * The resolved base URL for chat completions is [INFERENCE_BASE_URL], which is
 * OpenAI-compatible and works with the existing [RemoteEngine] without changes.
 */
class HuggingFaceClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) {

    data class HfModel(
        val id: String,
        val name: String,
        val downloads: Int,
    )

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchModels(apiKey: String?): List<HfModel> = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url(
                "$HUB_BASE/models" +
                    "?pipeline_tag=text-generation" +
                    "&inference=warm" +
                    "&sort=downloads" +
                    "&direction=-1" +
                    "&limit=30",
            )
        if (!apiKey.isNullOrBlank()) builder.header("Authorization", "Bearer $apiKey")

        client.newCall(builder.build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("HF Hub returned ${response.code}")
            parseModels(body)
        }
    }

    private fun parseModels(body: String): List<HfModel> {
        val array = runCatching {
            json.parseToJsonElement(body).jsonArray
        }.getOrNull() ?: return emptyList()

        return array.mapNotNull { element ->
            val obj = element.jsonObject
            val id = (obj["modelId"] ?: obj["id"])?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val downloads = obj["downloads"]?.jsonPrimitive?.intOrNull ?: 0
            val name = id.substringAfterLast("/")
            HfModel(id = id, name = name, downloads = downloads)
        }
    }

    companion object {
        private const val HUB_BASE = "https://huggingface.co/api"

        /** OpenAI-compatible base URL — pass directly to [RemoteEngine.Config.baseUrl]. */
        const val INFERENCE_BASE_URL = "https://api-inference.huggingface.co/v1"
    }
}
