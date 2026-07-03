package com.ownid.sdk.api.impl.login

import com.ownid.sdk.api.ApiBaseUrl
import com.ownid.sdk.api.ApiResult
import com.ownid.sdk.api.LoginApi
import com.ownid.sdk.api.LoginApiFailure
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
import com.ownid.sdk.model.LoginResponse
import com.ownid.sdk.model.accessToken
import com.ownid.sdk.model.runCatchingExceptions

internal class LoginApiImpl(
    private val apiBaseUrl: ApiBaseUrl,
    private val network: Network,
    private val serialization: Serialization,
    private val dispatcherProvider: DispatcherProvider,
    private val context: Context?,
    private val interceptor: ApiCallInterceptor?
) : LoginApi {

    override suspend fun start(params: LoginApi.Params?): ApiResult<LoginResponse, LoginApiFailure> {
        return dispatcherProvider.ioDispatcher().runApiCatching(LoginApiFailure::Unexpected) {
            val accessToken = params?.accessToken ?: context?.accessToken ?: return@runApiCatching ApiResult.Failure(
                failure = LoginApiFailure.BadRequest.InvalidArgument(errorCode = ErrorCode.InvalidArgument, message = "AccessToken is required")
            )

            LoginApiCall
                .create(
                    baseApiUrl = apiBaseUrl.getBaseUrlOrThrow(),
                    serialization = serialization,
                    accessToken = accessToken,
                    traceParent = params?.traceParent ?: TraceContext.generateTraceparent()
                )
                .executeWithInterceptor(network = network, interceptor = interceptor)
        }
    }

    @InternalOwnIdAPI
    internal companion object {

        internal fun create(resolver: DIContainerResolver): LoginApi = resolver.runCatchingExceptions {
            LoginApiImpl(
                apiBaseUrl = getOrThrow<ApiBaseUrl>(),
                network = getOrThrow<Network>(),
                serialization = getOrThrow<Serialization>(),
                dispatcherProvider = getOrThrow<DispatcherProvider>(),
                context = getOrNull<Context>(),
                interceptor = getOrNull<ApiCallInterceptor>()
            )
        }.getOrElse { error ->
            object : LoginApi {
                override suspend fun start(params: LoginApi.Params?): ApiResult<LoginResponse, LoginApiFailure> = ApiResult.Failure(
                    failure = LoginApiFailure.Unexpected(
                        errorCode = ErrorCode.Unknown,
                        message = if (error is MissingDependencyException) error.dependencyName else error.message ?: error::class.java.simpleName,
                        cause = ApiUnexpectedCause.Runtime(error)
                    )
                )
            }
        }
    }
}
