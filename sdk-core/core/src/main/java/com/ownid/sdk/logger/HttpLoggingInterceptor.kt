package com.ownid.sdk.logger

import com.ownid.sdk.capability.Network
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import java.io.EOFException
import java.io.IOException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * OkHttp interceptor that mirrors SDK HTTP traffic into the configured local logger only.
 *
 * The default network client installs this interceptor only when the configured [OwnIdLogger] accepts
 * [OwnIdLogger.Level.Debug]. Debug logs request/response lines. Verbose additionally logs all request/response headers
 * and bodies that appear to be UTF-8; binary bodies are omitted. Requests tagged with [Network.Request.NoHttpLogTag]
 * are skipped, including server diagnostic posts.
 *
 * Verbose output includes header values and UTF-8 bodies as present in the request or response.
 */
internal class HttpLoggingInterceptor(
    private val level: OwnIdLogger.Level,
    private val logger: (message: String) -> Unit
) : Interceptor {

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (request.tag(Network.Request.NoHttpLogTag::class.java) != null) {
            return chain.proceed(request)
        }

        // Only log for Verbose/Debug; skip for Info and above
        if (level != OwnIdLogger.Level.Verbose && level != OwnIdLogger.Level.Debug) {
            return chain.proceed(request)
        }

        // Log bodies only at Verbose
        val doBody = level == OwnIdLogger.Level.Verbose

        logRequest(request, doBody)

        val startNs = System.nanoTime()
        val response = try {
            chain.proceed(request)
        } catch (e: Exception) {
            logger("<-- HTTP FAILED: $e")
            throw e
        }
        val tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)

        return logResponse(response, tookMs, doBody)
    }

    private fun logRequest(request: Request, logBody: Boolean) {
        val log = StringBuilder()
        log.appendLine("--> ${request.method} ${request.url}")

        if (logBody) {
            for ((name, value) in request.headers) {
                log.appendLine("$name: $value")
            }

            request.body?.let { body ->
                val buffer = Buffer().apply { body.writeTo(this) }
                val charset: Charset = body.contentType()?.charset(StandardCharsets.UTF_8) ?: StandardCharsets.UTF_8
                if (buffer.isProbablyUtf8()) {
                    log.appendLine()
                    log.appendLine(buffer.readString(charset))
                } else {
                    log.appendLine("Request body is binary (omitted).")
                }
            }
        }
        log.appendLine("--> END ${request.method}")
        logger(log.toString().trimEnd())
    }

    private fun logResponse(response: Response, tookMs: Long, logBody: Boolean): Response {
        val log = StringBuilder()
        log.appendLine("<-- ${response.code} ${response.message} ${response.request.url} (${tookMs}ms)")

        if (logBody) {
            for ((name, value) in response.headers) {
                log.appendLine("$name: $value")
            }

            response.body?.let { body ->
                val source = body.source()
                source.request(Long.MAX_VALUE)
                val buffer = source.buffer
                val charset: Charset = body.contentType()?.charset(StandardCharsets.UTF_8) ?: StandardCharsets.UTF_8
                if (buffer.isProbablyUtf8()) {
                    log.appendLine()
                    log.appendLine(buffer.clone().readString(charset))
                } else {
                    log.appendLine("Response body is binary (omitted).")
                }
            }
        }
        log.appendLine("<-- END HTTP")
        logger(log.toString().trimEnd())
        return response
    }

    private fun Buffer.isProbablyUtf8(): Boolean {
        try {
            val prefix = Buffer()
            val byteCount = size.coerceAtMost(64)
            copyTo(prefix, 0, byteCount)
            repeat(16) {
                if (prefix.exhausted()) return true
                val codePoint = prefix.readUtf8CodePoint()
                if (Character.isISOControl(codePoint) && !Character.isWhitespace(codePoint)) return false
            }
            return true
        } catch (_: EOFException) {
            return false
        }
    }
}
