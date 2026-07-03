package com.ownid.sdk.capability.impl

import com.ownid.sdk.capability.Network
import com.ownid.sdk.logger.TraceContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ProtocolException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import kotlin.coroutines.resume
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.milliseconds

/**
 * Default [Network] implementation backed by the configured [OkHttpClient].
 *
 * The implementation returns typed [Network.Response] values for successful HTTP responses, non-2xx responses, and
 * expected I/O failures. Coroutine cancellation cancels the active call and remains cancellation, not a provider
 * failure value. When retry configuration is present, only recoverable network failures are retried.
 */
internal class NetworkImpl(
    override val okHttpClient: OkHttpClient,
    override val retryConfig: Network.Request.RetryConfig?
) : Network {

    override suspend fun execute(okHttpRequest: Request): Network.Response {
        val traceSeed = TraceContext.resolveSeed(okHttpRequest.header(Network.Request.Header.TRACE_PARENT))
        val doCall: suspend () -> Network.Response = {
            val requestForAttempt = okHttpRequest
                .newBuilder()
                .header(Network.Request.Header.TRACE_PARENT, TraceContext.nextTraceparent(traceSeed))
                .build()
            okHttpClient.newCall(requestForAttempt).executeAsync()
        }
        return if (retryConfig == null) doCall() else executeWithRetry(retryConfig, doCall)
    }

    private suspend fun executeWithRetry(
        retryConfig: Network.Request.RetryConfig,
        block: suspend () -> Network.Response
    ): Network.Response {
        var delayMs = retryConfig.initialDelay
        repeat(retryConfig.retries) {
            when (val response = block()) {
                is Network.Response.Fail.NetworkError -> {
                    if (!response.error.isRetriableNetworkError) return response
                    delay(delayMs.milliseconds)
                    delayMs = (delayMs * retryConfig.factor).roundToLong().coerceAtMost(retryConfig.maxDelay)
                }

                else -> return response
            }
        }
        return block()
    }

    private val IOException.isRetriableNetworkError: Boolean
        get() {
            if (this is InterruptedIOException) return false
            if (this is ProtocolException) return false
            if (this is SSLPeerUnverifiedException) return false
            if (this is SSLHandshakeException && cause is CertificateException) return false
            return true
        }
}

/**
 * Executes this OkHttp call as a cancellable SDK network request.
 *
 * The returned value owns the response body string. HTTP response resources are closed before the function resumes.
 */
internal suspend fun Call.executeAsync(): Network.Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { this.cancel() }

    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!continuation.isActive) return
            continuation.resume(Network.Response.Fail.NetworkError(url = call.request().url, error = e))
        }

        override fun onResponse(call: Call, response: Response) {
            if (!continuation.isActive) {
                try {
                    response.close()
                } catch (_: Exception) {
                }
                return
            }

            val netResponse: Network.Response = try {
                response.use {
                    val bodyString = it.body?.string() ?: ""
                    if (it.isSuccessful) {
                        Network.Response.Success(
                            url = call.request().url,
                            code = it.code,
                            headers = it.headers.toMultimap(),
                            body = bodyString
                        )
                    } else {
                        Network.Response.Fail.HttpError(
                            url = call.request().url,
                            statusCode = it.code,
                            headers = it.headers.toMultimap(),
                            body = bodyString
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: IOException) {
                Network.Response.Fail.NetworkError(url = call.request().url, error = error)
            } catch (error: Exception) {
                Network.Response.Fail.ResponseError(
                    url = call.request().url,
                    statusCode = response.code,
                    error = error,
                    headers = response.headers.toMultimap(),
                    body = null
                )
            }

            continuation.resume(netResponse)
        }
    })
}
