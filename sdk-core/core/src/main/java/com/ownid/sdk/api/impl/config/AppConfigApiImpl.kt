package com.ownid.sdk.api.impl.config

import com.ownid.sdk.api.ApiBaseUrl
import com.ownid.sdk.api.ApiResult
import com.ownid.sdk.api.impl.ApiCallInterceptor
import com.ownid.sdk.api.impl.executeWithInterceptor
import com.ownid.sdk.api.impl.runApiCatching
import com.ownid.sdk.api.internal.AppConfigApi
import com.ownid.sdk.api.internal.AppConfigApiFailure
import com.ownid.sdk.capability.DispatcherProvider
import com.ownid.sdk.capability.Network
import com.ownid.sdk.capability.Serialization
import com.ownid.sdk.logger.TraceContext
import com.ownid.sdk.model.AppConfig

// Not a part of DI, created internally by AppConfigProvider
internal class AppConfigApiImpl(
    private val apiBaseUrl: ApiBaseUrl,
    private val network: Network,
    private val serialization: Serialization,
    private val dispatcherProvider: DispatcherProvider,
    private val interceptor: ApiCallInterceptor?
) : AppConfigApi {

    override suspend fun start(params: AppConfigApi.Params?): ApiResult<AppConfig, AppConfigApiFailure> =
        dispatcherProvider.ioDispatcher().runApiCatching(AppConfigApiFailure::Unexpected) {
            AppConfigApiCall
                .create(
                    baseApiUrl = apiBaseUrl.getBaseUrlOrThrow(),
                    serialization = serialization,
                    traceParent = params?.traceParent ?: TraceContext.generateTraceparent()
                )
                .executeWithInterceptor(network = network, interceptor = interceptor)
        }
}
