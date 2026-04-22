package com.fantamomo.mc.amongus.util.log

import com.fantamomo.mc.amongus.data.AmongUsConfig
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URL

object ActionLogUploader {
    private val BASE_URL = AmongUsConfig.ActionLogUpload.url?.takeIf { it.isValidUrl() }?.removeSuffix("/")
    private val BASE_URI = BASE_URL?.let { runCatching { URI.create(it) }.getOrNull() }
    private val UPLOAD_URL_STRING = BASE_URL?.let { "$it/upload" }
    private val UPLOAD_URL = UPLOAD_URL_STRING?.let { runCatching { Url(it) }.getOrNull() }

    private val logger = LoggerFactory.getLogger("AmongUsActionLogUploader")

    private val client: HttpClient by lazy {
        HttpClient {
            expectSuccess = false

        }
    }


    fun enabled() = AmongUsConfig.ActionLogUpload.enabled

    internal suspend fun upload(log: String): URL? {
        checkValid()
        return withContext(Dispatchers.IO) {
            val url = UPLOAD_URL ?: return@withContext null

            try {
                val response = client.post(url) {
                    contentType(ContentType.Application.Json.withCharset(Charsets.UTF_8))
                    header(HttpHeaders.UserAgent, "Fantamomo/among-us-in-minecraft/2.0")
                    setBody(log)
                }

                val status = response.status
                val body = response.bodyAsText()

                if (status.value !in 200..299) {
                    logger.error("Action log upload failed with status code ${status.value} (${status.description}): $body")
                    return@withContext null
                }

                if (body.isEmpty()) {
                    logger.error("Action log upload failed: empty response")
                    return@withContext null
                } else if (body.length != 8) {
                    logger.error("Action log upload failed: invalid response code: $body")
                    return@withContext null
                }

                @Suppress("DEPRECATION")
                return@withContext URL(BASE_URL!! + "/log/" + body)
            } catch (e: Exception) {
                logger.error("Action log upload failed", e)
                null
            }
        }
    }

    private fun checkValid() =
        require(enabled() && BASE_URL != null && UPLOAD_URL_STRING != null) { "Action log upload is not enabled or URL is invalid" }

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