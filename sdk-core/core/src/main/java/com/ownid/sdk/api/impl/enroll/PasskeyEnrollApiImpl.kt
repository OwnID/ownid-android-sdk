package com.ownid.sdk.api.impl.enroll

import com.ownid.sdk.api.ApiBaseUrl
import com.ownid.sdk.api.ApiResult
import com.ownid.sdk.api.PasskeyEnrollApi
import com.ownid.sdk.api.PasskeyEnrollApiFailure
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

internal class PasskeyEnrollApiImpl(
    private val apiBaseUrl: ApiBaseUrl,
    private val network: Network,
    private val serialization: Serialization,
    private val dispatcherProvider: DispatcherProvider,
    private val context: Context?,
    private val interceptor: ApiCallInterceptor?
) : PasskeyEnrollApi {

    override suspend fun start(params: PasskeyEnrollApi.Params): ApiResult<Unit, PasskeyEnrollApiFailure> =
        dispatcherProvider.ioDispatcher().runApiCatching(PasskeyEnrollApiFailure::Unexpected) {
            val accessToken = params.accessToken ?: context?.accessToken ?: return@runApiCatching ApiResult.Failure(
                failure = PasskeyEnrollApiFailure.BadRequest.InvalidArgument(errorCode = ErrorCode.InvalidArgument, message = "AccessToken is required")
            )

            PasskeyEnrollApiCall
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

        fun create(resolver: DIContainerResolver): PasskeyEnrollApi = resolver.runCatchingExceptions {
            PasskeyEnrollApiImpl(
                apiBaseUrl = getOrThrow<ApiBaseUrl>(),
                network = getOrThrow<Network>(),
                serialization = getOrThrow<Serialization>(),
                dispatcherProvider = getOrThrow<DispatcherProvider>(),
                context = getOrNull<Context>(),
                interceptor = getOrNull<ApiCallInterceptor>()
            )
        }.getOrElse { error ->
            object : PasskeyEnrollApi {
                override suspend fun start(params: PasskeyEnrollApi.Params): ApiResult<Unit, PasskeyEnrollApiFailure> = ApiResult.Failure(
                    failure = PasskeyEnrollApiFailure.Unexpected(
                        errorCode = ErrorCode.Unknown,
                        message = if (error is MissingDependencyException) error.dependencyName else error.message ?: error::class.java.simpleName,
                        cause = ApiUnexpectedCause.Runtime(error)
                    )
                )
            }
        }
    }
}
