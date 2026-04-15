package com.fantamomo.mc.amongus.util.log

import com.fantamomo.mc.amongus.data.AmongUsConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URL
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

object ActionLogUploader {
    private val BASE_URL = AmongUsConfig.ActionLogUpload.url?.takeIf { it.isValidUrl() }?.removeSuffix("/")
    private val BASE_URI = BASE_URL?.let { runCatching { URI.create(it) }.getOrNull() }
    private val UPLOAD_URL = BASE_URL?.let { "$it/upload" }
    private val UPLOAD_URI = UPLOAD_URL?.let { runCatching { URI.create(it) }.getOrNull() }

    private val logger = LoggerFactory.getLogger("AmongUsActionLogUploader")

    private val client: HttpClient by lazy {
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build()
    }


    fun enabled() = AmongUsConfig.ActionLogUpload.enabled

    internal suspend fun upload(log: String): URL? {
        checkValid()
        return withContext(Dispatchers.IO) {
            val uri = UPLOAD_URI ?: return@withContext null

            try {
                val request = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("User-Agent", "Fantamomo/among-us-in-minecraft/1.0")
                    .POST(HttpRequest.BodyPublishers.ofString(log))
                    .build()

                val response = client.send(request, HttpResponse.BodyHandlers.ofString())
                if (response.statusCode() !in 200..299) {
                    logger.error("Action log upload failed with status code ${response.statusCode()}: ${response.body()}")
                    return@withContext null
                }

                val code = response.body()
                if (code.isEmpty()) {
                    logger.error("Action log upload failed: empty response")
                    return@withContext null
                } else if (code.length != 8) {
                    logger.error("Action log upload failed: invalid response code: $code")
                    return@withContext null
                }

                @Suppress("DEPRECATION")
                return@withContext URL(BASE_URL!! + "/log/" + code)
            } catch (e: Exception) {
                logger.error("Action log upload failed", e)
                null
            }
        }
    }

    private fun checkValid() =
        require(enabled() && BASE_URL != null && UPLOAD_URL != null) { "Action log upload is not enabled or URL is invalid" }

    private fun String.isValidUrl() = try {
        val uri = URI(this)
        val schemeValid = uri.scheme == "http" || uri.scheme == "https"
        val hostValid = !uri.host.isNullOrEmpty()
        val noQueryOrFragment = uri.query.isNullOrEmpty() && uri.fragment.isNullOrEmpty()
        schemeValid && hostValid && noQueryOrFragment
    } catch (e: Exception) {
        false
    }
}