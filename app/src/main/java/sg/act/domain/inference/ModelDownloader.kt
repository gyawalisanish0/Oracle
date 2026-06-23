package sg.act.domain.inference

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.coroutines.coroutineContext

/**
 * Streams a single model URL to disk, emitting progress. Only contacts hosts in
 * [ModelCatalog.allowedHosts]; any other URL is refused before a socket opens.
 *
 * Transient failures (timeouts, 429, 5xx) are retried in place with exponential
 * backoff. Hard failures (404/403/401, or retries exhausted) throw — the caller
 * ([ModelManager]) then falls back to the next mirror.
 *
 * This is the one network action Domain AI performs that is NOT gated by the
 * inference kill switch — it transmits no user content, only fetches public
 * weights, and is always triggered by an explicit user confirmation upstream.
 */
class ModelDownloader(
    private val client: OkHttpClient = OkHttpClient(),
) {

    data class Progress(val bytesRead: Long, val totalBytes: Long) {
        val fraction: Float get() = if (totalBytes > 0) bytesRead.toFloat() / totalBytes else 0f
    }

    /** A non-retryable failure for this URL — the caller should try another mirror. */
    class MirrorFailedException(message: String, cause: Throwable? = null) : IOException(message, cause)

    /**
     * Download [url] into [destination] (a temp file), emitting progress. [expectedSize]
     * is used only as a fallback total when the server omits Content-Length.
     */
    fun download(url: String, expectedSize: Long, destination: File): Flow<Progress> = flow {
        val host = url.toHttpHostOrNull()
        require(host != null && host in ModelCatalog.allowedHosts) {
            "Refusing to download from a non-allow-listed host: $host"
        }

        var attempt = 0
        while (true) {
            try {
                streamOnce(url, expectedSize, destination)
                return@flow
            } catch (transient: TransientException) {
                attempt++
                if (attempt >= MAX_RETRIES) {
                    throw MirrorFailedException(
                        "Failed after $attempt attempts: ${transient.message}",
                        transient.cause,
                    )
                }
                // Do NOT delete the partial file — the retry resumes from it via a
                // Range request, so a dropped connection doesn't restart from zero.
                delay(BACKOFF_BASE_MS shl (attempt - 1)) // 1s, 2s, 4s …
            }
        }
    }.flowOn(Dispatchers.IO)

    private class TransientException(message: String, cause: Throwable? = null) :
        IOException(message, cause)

    private suspend fun kotlinx.coroutines.flow.FlowCollector<Progress>.streamOnce(
        url: String,
        expectedSize: Long,
        destination: File,
    ) {
        val existing = if (destination.exists()) destination.length() else 0L
        val builder = Request.Builder().url(url)
        if (existing > 0) builder.header("Range", "bytes=$existing-")

        val response = try {
            client.newCall(builder.build()).execute()
        } catch (io: IOException) {
            throw TransientException("Connection failed", io) // retry network errors
        }

        response.use {
            // Already-complete partial: the server says the range is unsatisfiable.
            if (response.code == 416) {
                emit(Progress(existing, existing))
                return
            }
            if (!response.isSuccessful) {
                if (response.code in RETRYABLE_CODES) throw TransientException("HTTP ${response.code}")
                throw MirrorFailedException("HTTP ${response.code}") // 401/403/404 → next mirror
            }
            val body = response.body ?: throw MirrorFailedException("Empty response body")

            // 206 = resuming; 200 = server ignored Range, so restart from scratch.
            val resuming = response.code == 206
            val total = if (resuming) {
                contentRangeTotal(response) ?: (existing + body.contentLength().coerceAtLeast(0))
            } else {
                body.contentLength().takeIf { it > 0 } ?: expectedSize
            }
            var downloaded = if (resuming) existing else 0L

            body.byteStream().use { input ->
                FileOutputStream(destination, /* append = */ resuming).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var lastEmitted = downloaded
                    emit(Progress(downloaded, total))
                    while (true) {
                        coroutineContext.ensureActive() // cancel cleanly if collector leaves
                        val read = try {
                            input.read(buffer)
                        } catch (io: IOException) {
                            throw TransientException("Stream interrupted", io)
                        }
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (downloaded - lastEmitted >= 1_000_000L) {
                            lastEmitted = downloaded
                            emit(Progress(downloaded, total))
                        }
                    }
                    emit(Progress(downloaded, total))
                }
            }
        }
    }

    /** Parse the absolute total from a `Content-Range: bytes start-end/total` header. */
    private fun contentRangeTotal(response: okhttp3.Response): Long? =
        response.header("Content-Range")?.substringAfter('/')?.toLongOrNull()

    private fun String.toHttpHostOrNull(): String? =
        Regex("^https?://([^/]+)/").find(this)?.groupValues?.get(1)?.substringBefore(':')

    private companion object {
        const val MAX_RETRIES = 3
        const val BACKOFF_BASE_MS = 1_000L
        val RETRYABLE_CODES = setOf(408, 425, 429, 500, 502, 503, 504)
    }
}
