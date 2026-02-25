package com.fantamomo.mc.amongus.util.internal.mineskin

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.nio.charset.StandardCharsets

object MultipartBodyBuilder {

    fun build(
        boundary: String,
        fileKey: String,
        filename: String,
        fileStream: InputStream,
        fields: Map<String, String>
    ): ByteArray {

        val output = ByteArrayOutputStream()
        val writer = PrintWriter(
            OutputStreamWriter(output, StandardCharsets.UTF_8),
            true
        )

        fields.forEach { (key, value) ->
            writer.append("--$boundary\r\n")
            writer.append("Content-Disposition: form-data; name=\"$key\"\r\n\r\n")
            writer.append(value).append("\r\n")
        }

        writer.append("--$boundary\r\n")
        writer.append("Content-Disposition: form-data; name=\"$fileKey\"; filename=\"$filename\"\r\n")
        writer.append("Content-Type: application/octet-stream\r\n\r\n")
        writer.flush()

        fileStream.copyTo(output)
        output.write("\r\n".toByteArray(StandardCharsets.UTF_8))

        writer.append("--$boundary--\r\n")
        writer.close()

        return output.toByteArray()
    }
}