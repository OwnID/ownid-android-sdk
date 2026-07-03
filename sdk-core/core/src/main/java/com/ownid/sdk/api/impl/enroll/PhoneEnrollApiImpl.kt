package com.ownid.sdk.api.impl.enroll

import com.ownid.sdk.api.ApiBaseUrl
import com.ownid.sdk.api.ApiResult
import com.ownid.sdk.api.PhoneEnrollApi
import com.ownid.sdk.api.PhoneEnrollApiFailure
import com.ownid.sdk.api.impl.ApiCallInterceptor
import com.ownid.sdk.api.impl.executeWithInterceptor
import com.ownid.sdk.api.impl.runApiCatching
import com.ownid.sdk.api.internal.ApiUnexpectedCause
import com.ownid.sdk.capability.DispatcherProvider
import com.ownid.sdk.capability.Network
import com.ownid.sdk.capability.Serialization
import com.ownid.sdk.di.DIContainerResolver
import com.ownid.sdk.di.MissingDependencyException
import com.ownid.sdk.di.getOrNull
import com.ownid.sdk.di.getOrThrow
import com.ownid.sdk.logger.TraceContext
import com.ownid.sdk.model.Context
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.accessToken
import com.ownid.sdk.model.runCatchingExceptions

internal class PhoneEnrollApiImpl(
    private val apiBaseUrl: ApiBaseUrl,
    private val network: Network,
    private val serialization: Serialization,
    private val dispatcherProvider: DispatcherProvider,
    private val context: Context?,
    private val interceptor: ApiCallInterceptor?,
) : PhoneEnrollApi {

    override suspend fun start(params: PhoneEnrollApi.Params): ApiResult<Unit, PhoneEnrollApiFailure> =
        dispatcherProvider.ioDispatcher().runApiCatching(PhoneEnrollApiFailure::Unexpected) {
            val accessToken = params.accessToken ?: context?.accessToken ?: return@runApiCatching ApiResult.Failure(
                failure = PhoneEnrollApiFailure.BadRequest.InvalidArgument(errorCode = ErrorCode.InvalidArgument, message = "AccessToken is required")
            )

            PhoneEnrollApiCall
                .create(
                    baseApiUrl = apiBaseUrl.getBaseUrlOrThrow(),
                    serialization = serialization,
                    proofToken = params.proofToken,
                    accessToken = accessToken,
                    traceParent = params.traceParent ?: TraceContext.generateTraceparent()
                )
                .executeWithInterceptor(network = network, interceptor = interceptor)
        }

    @InternalOwnIdAPI
    internal companion object {

        fun create(resolver: DIContainerResolver): PhoneEnrollApi = resolver.runCatchingExceptions {
            PhoneEnrollApiImpl(
                apiBaseUrl = getOrThrow<ApiBaseUrl>(),
                network = getOrThrow<Network>(),
                serialization = getOrThrow<Serialization>(),
                dispatcherProvider = getOrThrow<DispatcherProvider>(),
                context = getOrNull<Context>(),
                interceptor = getOrNull<ApiCallInterceptor>()
            )
        }.getOrElse { error ->
            object : PhoneEnrollApi {
                override suspend fun start(params: PhoneEnrollApi.Params): ApiResult<Unit, PhoneEnrollApiFailure> = ApiResult.Failure(
                    failure = PhoneEnrollApiFailure.Unexpected(
                        errorCode = ErrorCode.Unknown,
                        message = if (error is MissingDependencyException) error.dependencyName else error.message ?: error::class.java.simpleName,
                        cause = ApiUnexpectedCause.Runtime(error)
                    )
                )
            }
        }
    }
}
