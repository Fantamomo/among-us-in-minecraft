package com.fantamomo.mc.amongus.util.log

import com.fantamomo.mc.amongus.data.AmongUsConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URL
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

object ActionLogUploader {
    private val BASE_URL = AmongUsConfig.ActionLogUpload.url?.takeIf { it.isValidUrl() }?.removeSuffix("/")
    private val UPLOAD_URL = BASE_URL?.let { "$it/upload" }

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
            val urlString = UPLOAD_URL ?: return@withContext null

            try {
                val request = HttpRequest.newBuilder()
                    .uri(URI.create(urlString))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("User-Agent", "Fantamomo/among-us-in-minecraft/1.0")
                    .POST(HttpRequest.BodyPublishers.ofString(log))
                    .build()

                val response = client.send(request, HttpResponse.BodyHandlers.ofString())
                if (response.statusCode() != 200) {
                    logger.error("Action log upload failed with status code ${response.statusCode()}: ${response.body()}")
                    return@withContext null
                }

                val json = Json.parseToJsonElement(response.body()).jsonObject
                val resultUrl = json["url"]?.jsonPrimitive?.contentOrNull ?: return@withContext null

                @Suppress("DEPRECATION")
                return@withContext URL(BASE_URL!! + resultUrl)
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