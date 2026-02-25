package com.fantamomo.mc.amongus.util.internal.mineskin

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import org.mineskin.MineSkinClientImpl
import org.mineskin.data.CodeAndMessage
import org.mineskin.exception.MineSkinRequestException
import org.mineskin.exception.MineskinException
import org.mineskin.request.RequestHandler
import org.mineskin.response.MineSkinResponse
import org.mineskin.response.ResponseConstructor
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.logging.Level

class HttpClientRequestHandler(
    baseUrl: String,
    userAgent: String,
    apiKey: String?,
    private val timeout: Int,
    gson: Gson
) : RequestHandler(baseUrl, userAgent, apiKey, timeout, gson) {

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(timeout.toLong()))
        .build()

    private fun requestBase(url: String): HttpRequest.Builder {
        val builder = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + url))
            .timeout(Duration.ofMillis(timeout.toLong()))
            .header("User-Agent", userAgent)

        apiKey?.let {
            builder.header("Authorization", "Bearer $it")
        }

        return builder
    }

    private fun <T, R : MineSkinResponse<T>> wrapResponse(
        response: HttpResponse<String>,
        clazz: Class<T>,
        constructor: ResponseConstructor<T, R>
    ): R {
        try {
            val jsonBody = gson.fromJson(response.body(), JsonObject::class.java)

            val headers = response.headers().map()
                .mapValues { it.value.firstOrNull() ?: "" }
                .mapKeys { it.key.lowercase() }

            val wrapped = constructor.construct(
                response.statusCode(),
                headers,
                jsonBody,
                gson,
                clazz
            )

            if (!wrapped.isSuccess) {
                throw MineSkinRequestException(
                    wrapped.firstError.map(CodeAndMessage::code).orElse("request_failed"),
                    wrapped.firstError.map(CodeAndMessage::message).orElse("Request Failed"),
                    wrapped
                )
            }

            return wrapped
        } catch (e: JsonParseException) {
            MineSkinClientImpl.LOGGER.log(
                Level.WARNING,
                "Failed to parse response body: ${response.body()}",
                e
            )
            throw MineskinException("Failed to parse response", e)
        }
    }

    override fun <T, R : MineSkinResponse<T>> getJson(
        url: String,
        clazz: Class<T>,
        constructor: ResponseConstructor<T, R>
    ): R {
        val request = requestBase(url)
            .GET()
            .build()

        return try {
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            wrapResponse(response, clazz, constructor)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException(e)
        }
    }

    override fun <T, R : MineSkinResponse<T>> postJson(
        url: String,
        data: JsonObject,
        clazz: Class<T>,
        constructor: ResponseConstructor<T, R>
    ): R {
        val request = requestBase(url)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(data.toString()))
            .build()

        return try {
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            wrapResponse(response, clazz, constructor)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException(e)
        }
    }

    override fun <T, R : MineSkinResponse<T>> postFormDataFile(
        url: String,
        key: String,
        filename: String,
        input: InputStream,
        data: Map<String, String>,
        clazz: Class<T>,
        constructor: ResponseConstructor<T, R>
    ): R {

        val boundary = "----MineSkinBoundary${System.currentTimeMillis()}"
        val body = MultipartBodyBuilder.build(boundary, key, filename, input, data)

        val request = requestBase(url)
            .header("Content-Type", "multipart/form-data; boundary=$boundary")
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build()

        return try {
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            wrapResponse(response, clazz, constructor)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException(e)
        }
    }
}