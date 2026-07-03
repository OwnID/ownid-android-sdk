package com.ownid.sdk.api.impl

import com.ownid.sdk.OwnIdConfiguration
import com.ownid.sdk.api.ApiBaseUrl
import com.ownid.sdk.toStringPrefix
import com.ownid.sdk.toStringSuffix
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Resolves the instance API base URL from SDK configuration.
 *
 * A configured root URL is treated as the deployment root and the SDK API path is appended to it. Without a root URL,
 * the base URL is derived from the instance app ID, environment prefix, and region suffix. Invalid URL configuration is
 * reported by [getBaseUrlOrThrow] before any endpoint request is created.
 */
internal class ApiBaseUrlImpl(
    private val configuration: OwnIdConfiguration
) : ApiBaseUrl {

    override fun getBaseUrlOrThrow(): HttpUrl =
        when (val rootUrl = configuration.rootUrl) {
            null -> "https://${configuration.appId}.server${configuration.toStringPrefix()}.ownid${configuration.region.toStringSuffix()}.com/api".toHttpUrl()
            else -> rootUrl.toHttpUrl().newBuilder().addPathSegment("api").build()
        }
}
