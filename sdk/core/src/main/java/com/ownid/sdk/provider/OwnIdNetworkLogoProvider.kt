package com.ownid.sdk.provider

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnIdProvider
import com.ownid.sdk.internal.component.OwnIdInternalLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import okhttp3.CacheControl
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

@OptIn(InternalOwnIdAPI::class)
public class OwnIdNetworkLogoProvider(context: Context) : OwnIdProvider.LogoProvider {

    private val okHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .callTimeout(30, TimeUnit.SECONDS)
        .cache(Cache(File(context.cacheDir, "ownid_logo_cache"), 4L * 1024L * 1024L))
        .build()

    override fun getLogo(context: Context, logoUrl: String?): StateFlow<Drawable?> {
        val drawableFlow = MutableStateFlow<Drawable?>(null)

        if (logoUrl.isNullOrEmpty()) {
            OwnIdInternalLogger.logD(this@OwnIdNetworkLogoProvider, "getLogo", "Logo URL is empty or null. Skipping logo fetch.")
            return drawableFlow
        }

        val baseRequest = Request.Builder().url(logoUrl).build()

        val cacheOnlyRequest = baseRequest.newBuilder()
            .cacheControl(CacheControl.FORCE_CACHE)
            .build()

        fun fetchFromNetwork() {
            val networkRequest = baseRequest.newBuilder()
                .cacheControl(CacheControl.FORCE_NETWORK)
                .build()

            okHttpClient.newCall(networkRequest).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    OwnIdInternalLogger.logW(
                        this@OwnIdNetworkLogoProvider, "getLogo.onFailure", "Failed to fetch logo ($logoUrl): ${e.message}", e
                    )
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!response.isSuccessful) {
                            OwnIdInternalLogger.logW(
                                this@OwnIdNetworkLogoProvider,
                                "decodeResponseToDrawable",
                                "Server responded unsuccessfully ($logoUrl): ${response.code}"
                            )
                            return
                        }
                        decodeResponseToDrawable(context, response, logoUrl)?.let { drawable ->
                            drawableFlow.value = drawable
                            OwnIdInternalLogger.logD(this@OwnIdNetworkLogoProvider, "getLogo.onResponse", "Logo updated: $logoUrl")
                        }
                    }
                }
            })
        }

        okHttpClient.newCall(cacheOnlyRequest).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                OwnIdInternalLogger.logI(
                    this@OwnIdNetworkLogoProvider, "getLogo.onFailure", "No cached logo or cache fetch failed ($logoUrl): ${e.message}", e
                )
                fetchFromNetwork()
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        OwnIdInternalLogger.logD(
                            this@OwnIdNetworkLogoProvider, "decodeResponseToDrawable", "No cached logo ($logoUrl): ${response.code}"
                        )
                        fetchFromNetwork()
                        return
                    }
                    val cachedDrawable = decodeResponseToDrawable(context, response, logoUrl)
                    if (cachedDrawable != null) {
                        drawableFlow.value = cachedDrawable
                        OwnIdInternalLogger.logD(this@OwnIdNetworkLogoProvider, "getLogo.onResponse", "Loaded logo from cache: $logoUrl")
                    } else {
                        fetchFromNetwork()
                    }
                }
            }
        })

        return drawableFlow
    }

    private fun decodeResponseToDrawable(context: Context, response: Response, logoUrl: String): Drawable? {
        val contentType = response.header("Content-Type", "") ?: ""
        if (!contentType.lowercase().startsWith("image/")) {
            OwnIdInternalLogger.logW(this, "decodeResponseToDrawable", "Content-Type is not an image ($logoUrl): $contentType")
            return null
        }

        response.body?.let { body ->
            return runCatching {
                val bitmap = BitmapFactory.decodeStream(body.byteStream())
                if (bitmap == null) throw IOException("Bitmap was null ($logoUrl)")
                BitmapDrawable(context.resources, bitmap)
            }.onFailure {
                OwnIdInternalLogger.logW(this, "decodeResponseToDrawable", "Failed to decode logo ($logoUrl)", it)
            }.getOrNull()
        }

        return null
    }
}