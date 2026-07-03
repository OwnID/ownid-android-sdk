package com.ownid.sdk.capability

import com.ownid.sdk.model.AccessToken
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * HTTP networking capability used by the SDK.
 *
 * Executes requests and returns [Response.Success] or [Response.Fail] for expected outcomes;
 * use [Request.RetryConfig] to enable retries for recoverable network errors.
 *
 * Request and response bodies can contain tokens, login IDs, or other sensitive values. The default HTTP logger is
 * local-only and body logging is verbose-level only, but request builders should call [Request.Builder.suppressHttpLog]
 * for server-log uploads or any request whose payload should not appear in local HTTP logs.
 */
public interface Network : Capability {

    /** Represents an HTTP request that can be executed by the network layer. */
    public sealed interface Request {
        /** HTTP method. */
        public enum class Method { GET, POST }

        /**
         * Retry configuration for network calls.
         *
         * Only recoverable [Response.Fail.NetworkError] values are retried; HTTP errors,
         * timeouts, protocol errors, and certificate failures are returned as-is.
         *
         * Defaults to 0 retries, 250 ms initial delay, a 2.0 factor, and a 1000 ms max delay.
         */
        public class RetryConfig(
            public val retries: Int = 0,
            public val initialDelay: Long = 250,
            public val factor: Double = 2.0,
            public val maxDelay: Long = 1_000
        ) {
            public companion object {
                public val Default: RetryConfig = RetryConfig(retries = 0, initialDelay = 250, factor = 2.0, maxDelay = 1_000)
            }

            override fun equals(other: Any?): Boolean =
                this === other || (other is RetryConfig && retries == other.retries && initialDelay == other.initialDelay && factor == other.factor && maxDelay == other.maxDelay)

            override fun hashCode(): Int = (((retries * 31 + initialDelay.hashCode()) * 31 + factor.hashCode()) * 31 + maxDelay.hashCode())

            override fun toString(): String = "RetryConfig(retries=$retries, initialDelay=$initialDelay, factor=$factor, maxDelay=$maxDelay)"
        }

        public object Header {
            internal const val ACCEPT = "Accept"
            internal const val AUTHORIZATION = "Authorization"
            internal const val CACHE_CONTROL = "Cache-Control"
            internal const val CONTENT_TYPE = "Content-Type"
            internal const val ACCEPT_LANGUAGE = "Accept-Language"
            internal const val USER_AGENT = "User-Agent"
            internal const val BAGGAGE = "Baggage"
            internal const val TRACE_PARENT = "traceparent"
            internal const val OWNID_APP_URL = "X-OwnID-AppUrl"

            internal val DEFAULT = mapOf(
                ACCEPT to "application/json",
                CACHE_CONTROL to "no-store"
            )
        }

        public object MediaType {
            internal val JSON = "application/json; charset=utf-8".toMediaTypeOrNull()
        }

        /** Marker tag for requests that should not be logged by the local HTTP logging interceptor. */
        public object NoHttpLogTag

        /**
         * Builder for assembling an OkHttp request.
         *
         * Defaults to POST with an empty JSON body. Media type for POST is derived from a Content-Type header when present; otherwise JSON is used.
         *
         * For singleton headers (Content-Type, Authorization, Accept, Cache-Control, traceparent), user values replace defaults in the built request.
         *
         * When executed by the default network implementation, a provided traceparent value is used as trace seed and the outbound header value is rotated per send attempt.
         *
         * @property url Target URL for the request.
         */
        public class Builder(
            public val url: HttpUrl
        ) {
            internal var method: Method = Method.POST
            internal val headers: MutableList<Pair<String, String>> = mutableListOf()
            internal var body: String = "{}"
            internal var suppressHttpLog: Boolean = false

            /**
             * Queues a header to be applied when [build] is called.
             *
             * Repeated calls may queue multiple values for the same header name. Singleton headers are collapsed in
             * [build]; all other headers are added in queue order.
             *
             * @param header Header name and value.
             *
             * @return This builder.
             */
            public fun queueHeader(header: Pair<String, String>): Builder = apply {
                headers.add(header)
            }

            /**
             * Queues a header only when no queued header with the same case-insensitive name exists.
             *
             * @param header Header name and value.
             *
             * @return This builder.
             */
            public fun queueHeaderIfAbsent(header: Pair<String, String>): Builder = apply {
                val (name, _) = header
                if (headers.none { it.first.equals(name, ignoreCase = true) }) headers.add(header)
            }

            /**
             * Adds authorization headers for [accessToken].
             *
             * Passing `null` is a no-op. Otherwise this queues an `Authorization: Bearer ...` header.
             *
             * @return This builder.
             */
            public fun queueAccessToken(accessToken: AccessToken?): Builder = apply {
                if (accessToken == null) return@apply
                queueHeader(Header.AUTHORIZATION to "Bearer ${accessToken.token}")
            }

            internal fun queueTraceParent(traceParent: String?): Builder = apply {
                if (traceParent == null) return@apply
                queueHeaderIfAbsent(Header.TRACE_PARENT to traceParent)
            }

            /**
             * Suppresses HTTP logging for this request.
             *
             * Suppression affects the SDK's local HTTP logging interceptor only. It does not change network execution,
             * response mapping, server-side API failure logging, or app-owned logging.
             *
             * @param value Whether to suppress logging. Defaults to true.
             *
             * @return This builder.
             */
            public fun suppressHttpLog(value: Boolean = true): Builder = apply { suppressHttpLog = value }

            /**
             * Builds the final OkHttp request.
             *
             * The request includes SDK default headers unless explicitly replaced, uses the configured method, and tags
             * the request with [NoHttpLogTag] when local HTTP logging is suppressed.
             *
             * @return The configured OkHttp request.
             */
            public fun build(): okhttp3.Request {
                val builder = okhttp3.Request.Builder().url(url)

                when (method) {
                    Method.GET -> builder.get()
                    Method.POST -> {
                        val contentTypeHeader: String? = headers
                            .firstOrNull { it.first.equals(Header.CONTENT_TYPE, ignoreCase = true) }
                            ?.second
                        val postMediaType = contentTypeHeader?.toMediaTypeOrNull() ?: MediaType.JSON
                        builder.post(body.toRequestBody(postMediaType))
                    }
                }

                Header.DEFAULT.forEach { (name, value) ->
                    if (headers.none { it.first.equals(name, ignoreCase = true) }) {
                        builder.header(name, value)
                    }
                }

                headers.forEach { (name, value) ->
                    when (name.lowercase()) {
                        Header.CONTENT_TYPE.lowercase(),
                        Header.AUTHORIZATION.lowercase(),
                        Header.CACHE_CONTROL.lowercase(),
                        Header.ACCEPT.lowercase(),
                        Header.TRACE_PARENT.lowercase() -> builder.header(name, value)

                        else -> builder.addHeader(name, value)
                    }
                }

                if (suppressHttpLog) builder.tag(NoHttpLogTag::class.java, NoHttpLogTag)

                return builder.build()
            }
        }
    }

    /** Result of executing a network request. */
    public sealed interface Response {
        /**
         * Successful network request (2xx HTTP response).
         *
         * @param url The request URL.
         * @param code The HTTP status code.
         * @param headers The HTTP response headers.
         * @param body The response body.
         */
        public class Success(
            public val url: HttpUrl,
            public val code: Int,
            public val headers: Map<String, List<String>>,
            public val body: String
        ) : Response

        /**
         * A failed network request.
         */
        public sealed interface Fail : Response {
            /**
             * Non-2xx HTTP response (e.g., 4xx, 5xx).
             *
             * @param url The request URL.
             * @param statusCode The HTTP status code.
             * @param headers The HTTP response headers.
             * @param body The response body, if available.
             */
            public class HttpError(
                public val url: HttpUrl,
                public val statusCode: Int,
                public val headers: Map<String, List<String>>,
                public val body: String
            ) : Fail {
                override fun toString(): String = "HttpError(statusCode=$statusCode)"
            }

            /**
             * Network connectivity error (e.g., timeout, no connection).
             *
             * Some network errors are not safe to retry automatically. The default network implementation does not retry
             * timeouts, protocol errors, or certificate failures.
             *
             * @param url The request URL.
             * @param error The underlying [IOException].
             */
            public class NetworkError(
                public val url: HttpUrl,
                public val error: IOException
            ) : Fail {
                override fun toString(): String = "NetworkError(error=$error)"
            }

            /**
             * Unexpected non-I/O error while processing an HTTP response.
             *
             * @param url The request URL.
             * @param statusCode The HTTP status code, when a response status is available.
             * @param error The underlying exception.
             * @param headers The HTTP response headers.
             * @param body The response body, if available.
             */
            public class ResponseError(
                public val url: HttpUrl,
                public val statusCode: Int?,
                public val error: Throwable,
                public val headers: Map<String, List<String>>,
                public val body: String?
            ) : Fail {
                override fun toString(): String = "ResponseError(statusCode=$statusCode, error=$error)"
            }

            override fun toString(): String
        }
    }

    /** The underlying OkHttp client used for all requests. */
    public val okHttpClient: OkHttpClient

    /** Retry configuration applied to network errors; null means no retries. */
    public val retryConfig: Request.RetryConfig?

    /**
     * Executes [okHttpRequest] and returns a [Response.Success] or [Response.Fail].
     *
     * In the default implementation, [Request.Header.TRACE_PARENT] is finalized at send time:
     * every outbound attempt (including retries) gets a fresh parent-id while preserving the same trace-id/flags seed.
     */
    public suspend fun execute(okHttpRequest: okhttp3.Request): Response
}
