package com.fantamomo.mc.amongus.util.log

import com.fantamomo.mc.amongus.data.AmongUsConfig
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URL
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

object ActionLogUploader {
    private val BASE_URL = AmongUsConfig.ActionLogUpload.url?.takeIf { it.isValidUrl() }?.removeSuffix("/")
    private val BASE_URI = BASE_URL?.let { runCatching { URI.create(it) }.getOrNull() }
    private val UPLOAD_URL_STRING = BASE_URL?.let { "$it/upload" }
    private val UPLOAD_URL = UPLOAD_URL_STRING?.let { runCatching { Url(it) }.getOrNull() }
    private val ttl = AmongUsConfig.ActionLogUpload.ttl?.inWholeSeconds

    private val logger = LoggerFactory.getLogger("AmongUsActionLogUploader")

    private val client: HttpClient by lazy {
        HttpClient {
            expectSuccess = false

        }
    }


    fun enabled() = AmongUsConfig.ActionLogUpload.enabled

    internal suspend fun upload(log: String): Pair<URL, Duration?>? {
        checkValid()
        return withContext(Dispatchers.IO) {
            val url = UPLOAD_URL ?: return@withContext null

            try {
                val response = client.post(url) {
                    contentType(ContentType.Application.Json.withCharset(Charsets.UTF_8))
                    header(HttpHeaders.UserAgent, "Fantamomo/among-us-in-minecraft/2.0")
                    setBody(log)
                    if (ttl != null) {
                        parameter("ttl", ttl)
                    }
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
                }

                val json = Json.parseToJsonElement(body)

                var ttl: Duration? = null
                var code: String?
                when (json) {
                    is JsonPrimitive -> code = json.contentOrNull
                    is JsonObject -> {
                        code = (json["id"] as? JsonPrimitive)?.contentOrNull
                        ttl = (json["ttl"] as? JsonPrimitive)?.let { it.contentOrNull?.toIntOrNull()?.seconds }
                    }
                    else -> {
                        logger.error("Action log upload failed: invalid response: $body")
                        return@withContext null
                    }
                }

                if (code == null) {
                    logger.error("Action log upload failed: invalid response: $body")
                    return@withContext null
                } else if (code.length != 8) {
                    logger.error("Action log upload failed: invalid response code: $code")
                    return@withContext null
                }

                @Suppress("DEPRECATION")
                return@withContext URL(BASE_URL!! + "/log/" + code) to ttl?.takeIf { it > Duration.ZERO }
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