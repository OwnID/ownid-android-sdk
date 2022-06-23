package com.ownid.sdk.internal

import android.os.Handler
import android.os.Looper
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnIdCallback
import com.ownid.sdk.OwnIdCore
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.logE
import com.ownid.sdk.logV
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CipherSuite
import okhttp3.ConnectionPool
import okhttp3.ConnectionSpec
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.TlsVersion
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Helper class for performing network requests to OwnID server.
 */
@InternalOwnIdAPI
internal class NetworkHelper private constructor(private val client: OkHttpClient, private val handler: Handler?) {

    internal companion object {

        @Volatile
        private var INSTANCE: NetworkHelper? = null

        @JvmStatic
        internal fun getInstance(
            connectionSpec: ConnectionSpec = DEFAULT_CONNECTION_SPEC,
            handler: Handler? = Handler(Looper.getMainLooper())
        ): NetworkHelper = INSTANCE ?: synchronized(this) {
            if (INSTANCE != null) return@synchronized INSTANCE!!

            val client = OkHttpClient.Builder()
                .connectionPool(ConnectionPool(2, 5, TimeUnit.MINUTES))
                .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
                .followRedirects(false)
                .connectionSpecs(listOf(connectionSpec))
                .callTimeout(30, TimeUnit.SECONDS)
                .build()

            NetworkHelper(client, handler).also { INSTANCE = it }
        }

        @JvmStatic
        private val DEFAULT_CONNECTION_SPEC = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
            .tlsVersions(TlsVersion.TLS_1_2)
            .cipherSuites(
                CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
                CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
                CipherSuite.TLS_DHE_RSA_WITH_AES_128_GCM_SHA256
            )
            .build()

        @JvmStatic
        private val DEFAULT_MEDIA_TYPE: MediaType = "application/json".toMediaType()

        @JvmStatic
        private val DEFAULT_CACHE_CONTROL_FORCE_NETWORK_NO_CACHE: CacheControl =
            CacheControl.Builder().noCache().noStore().build()
    }

    internal fun doPostJsonRequest(
        ownIdCore: OwnIdCore, languageTags: String, url: HttpUrl, postJsonData: String, callback: OwnIdCallback<String>
    ) {
        logV("doPostJsonRequest url: $url postJsonData: $postJsonData", ownIdCore)

        val request: Request = Request.Builder()
            .url(url)
            .apply {
                val userAgent = ownIdCore.configuration.userAgent
                if (userAgent.isNotBlank()) header("User-Agent", userAgent)
                if (languageTags.isNotBlank()) header("Accept-Language", languageTags)
            }
            .post(postJsonData.toRequestBody(DEFAULT_MEDIA_TYPE))
            .cacheControl(DEFAULT_CACHE_CONTROL_FORCE_NETWORK_NO_CACHE)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                this@NetworkHelper.logE("doPostJsonRequest.onFailure: $e ($url)", e, ownIdCore)
                runOnHandler { callback(Result.failure(OwnIdException("Request fail ($url)", e))) }
            }

            override fun onResponse(call: Call, response: Response) {
                val result = response.use {
                    if (response.isSuccessful.not()) {
                        val e = OwnIdException("Server response: ${response.code} - ${response.message}")
                        this@NetworkHelper.logE("doPostJsonRequest.onResponse: $e ($url)", e, ownIdCore)
                        Result.failure(e)
                    } else {
                        val responseString = it.body!!.string()
                        if (responseString.isNotBlank()) {
                            Result.success(responseString)
                        } else {
                            val e = OwnIdException("Empty server response")
                            this@NetworkHelper.logE("doPostJsonRequest.onResponse: $e ($url)", e, ownIdCore)
                            Result.failure(e)
                        }
                    }
                }
                runOnHandler { callback(result) }
            }
        })
    }

    private fun runOnHandler(action: () -> Unit) {
        handler?.post(action) ?: action()
    }
}