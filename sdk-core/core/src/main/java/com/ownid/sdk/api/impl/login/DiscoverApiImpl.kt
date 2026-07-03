package com.ownid.sdk.api.impl.login

import com.ownid.sdk.api.ApiBaseUrl
import com.ownid.sdk.api.ApiFailureScope
import com.ownid.sdk.api.ApiResult
import com.ownid.sdk.api.DiscoverApi
import com.ownid.sdk.api.DiscoverApiFailure
import com.ownid.sdk.api.impl.ApiCallInterceptor
import com.ownid.sdk.api.impl.executeWithInterceptor
import com.ownid.sdk.api.impl.runApiCatching
import com.ownid.sdk.api.internal.ApiUnexpectedCause
import com.ownid.sdk.capability.DispatcherProvider
import com.ownid.sdk.capability.LoginIdValidator
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
import com.ownid.sdk.model.LoginIdResolutionException
import com.ownid.sdk.model.LoginResponse
import com.ownid.sdk.model.loginId
import com.ownid.sdk.model.runCatchingExceptions

internal class DiscoverApiImpl(
    private val apiBaseUrl: ApiBaseUrl,
    private val network: Network,
    private val serialization: Serialization,
    private val dispatcherProvider: DispatcherProvider,
    private val context: Context?,
    private val loginIdValidator: LoginIdValidator?,
    private val interceptor: ApiCallInterceptor?
) : DiscoverApi {

    override suspend fun start(params: DiscoverApi.Params?): ApiResult<LoginResponse, DiscoverApiFailure> {
        return dispatcherProvider.ioDispatcher().runApiCatching(DiscoverApiFailure::Unexpected) {
            val loginId = try {
                params?.loginId ?: context?.loginId(loginIdValidator)
            } catch (error: LoginIdResolutionException) {
                return@runApiCatching ApiResult.Failure(
                    when (error) {
                        is LoginIdResolutionException.MissingValidator ->
                            DiscoverApiFailure.FailedDependency.MissingProvider(error.errorCode, error.message, "LoginIdValidator", ApiFailureScope.DATA)

                        is LoginIdResolutionException.TypeNotSupported ->
                            DiscoverApiFailure.BadRequest.UnsupportedLoginIdType(error.errorCode, error.message)

                        is LoginIdResolutionException.ValidationFailed ->
                            DiscoverApiFailure.BadRequest.InvalidLoginId(error.errorCode, error.message, error.loginId, error.regex)
                    }
                )
            } ?: return@runApiCatching ApiResult.Failure(
                failure = DiscoverApiFailure.BadRequest.InvalidArgument(errorCode = ErrorCode.InvalidArgument, message = "LoginId is required")
            )

            DiscoverApiCall
                .create(
                    baseApiUrl = apiBaseUrl.getBaseUrlOrThrow(),
                    serialization = serialization,
                    loginId = loginId,
                    traceParent = params?.traceParent ?: TraceContext.generateTraceparent()
                )
                .executeWithInterceptor(network = network, interceptor = interceptor)
        }
    }

    @InternalOwnIdAPI
    internal companion object {

        internal fun create(resolver: DIContainerResolver): DiscoverApi = resolver.runCatchingExceptions {
            DiscoverApiImpl(
                apiBaseUrl = getOrThrow<ApiBaseUrl>(),
                network = getOrThrow<Network>(),
                serialization = getOrThrow<Serialization>(),
                dispatcherProvider = getOrThrow<DispatcherProvider>(),
                context = getOrNull<Context>(),
                loginIdValidator = getOrNull<LoginIdValidator>(),
                interceptor = getOrNull<ApiCallInterceptor>()
            )
        }.getOrElse { error ->
            object : DiscoverApi {
                override suspend fun start(params: DiscoverApi.Params?): ApiResult<LoginResponse, DiscoverApiFailure> = ApiResult.Failure(
                    failure = DiscoverApiFailure.Unexpected(
                        errorCode = ErrorCode.Unknown,
                        message = if (error is MissingDependencyException) error.dependencyName else error.message ?: error::class.java.simpleName,
                        cause = ApiUnexpectedCause.Runtime(error)
                    )
                )
            }
        }
    }
}
