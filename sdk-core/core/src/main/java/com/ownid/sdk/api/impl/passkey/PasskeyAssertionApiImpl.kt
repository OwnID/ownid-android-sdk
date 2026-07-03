package com.ownid.sdk.api.impl.passkey

import com.ownid.sdk.api.ApiBaseUrl
import com.ownid.sdk.api.ApiFailureScope
import com.ownid.sdk.api.ApiResult
import com.ownid.sdk.api.PasskeyAssertionApi
import com.ownid.sdk.api.PasskeyAssertionCancelApiFailure
import com.ownid.sdk.api.PasskeyAssertionStartApiFailure
import com.ownid.sdk.api.PasskeyAssertionVerifyApiFailure
import com.ownid.sdk.api.impl.ApiCallInterceptor
import com.ownid.sdk.api.impl.executeWithInterceptor
import com.ownid.sdk.api.impl.runApiCatching
import com.ownid.sdk.api.internal.ApiUnexpectedCause
import com.ownid.sdk.api.map
import com.ownid.sdk.capability.DispatcherProvider
import com.ownid.sdk.capability.LoginIdValidator
import com.ownid.sdk.capability.Network
import com.ownid.sdk.capability.Serialization
import com.ownid.sdk.di.DIContainerResolver
import com.ownid.sdk.di.MissingDependencyException
import com.ownid.sdk.di.getOrNull
import com.ownid.sdk.di.getOrThrow
import com.ownid.sdk.logger.TraceContext
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.AssertionOptions
import com.ownid.sdk.model.AssertionResult
import com.ownid.sdk.model.Context
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LoginIdResolutionException
import com.ownid.sdk.model.Reason
import com.ownid.sdk.model.accessToken
import com.ownid.sdk.model.loginId
import com.ownid.sdk.model.runCatchingExceptions

internal class PasskeyAssertionApiImpl(
    private val apiBaseUrl: ApiBaseUrl,
    private val network: Network,
    private val serialization: Serialization,
    private val dispatcherProvider: DispatcherProvider,
    private val context: Context?,
    private val loginIdValidator: LoginIdValidator?,
    private val interceptor: ApiCallInterceptor?
) : PasskeyAssertionApi {

    override suspend fun start(params: PasskeyAssertionApi.Params?): ApiResult<PasskeyAssertionApi.Controller, PasskeyAssertionStartApiFailure> {
        val accessToken = params?.accessToken ?: context?.accessToken
        val traceParent = params?.traceParent ?: TraceContext.generateTraceparent()
        val response = dispatcherProvider.ioDispatcher().runApiCatching(PasskeyAssertionStartApiFailure::Unexpected) {
            val loginId = try {
                params?.loginId ?: context?.loginId(loginIdValidator)
            } catch (error: LoginIdResolutionException) {
                return@runApiCatching ApiResult.Failure(
                    when (error) {
                        is LoginIdResolutionException.MissingValidator ->
                            PasskeyAssertionStartApiFailure.FailedDependency.MissingProvider(
                                error.errorCode, error.message, "LoginIdValidator", ApiFailureScope.DATA
                            )

                        is LoginIdResolutionException.TypeNotSupported ->
                            PasskeyAssertionStartApiFailure.BadRequest.UnsupportedLoginIdType(error.errorCode, error.message)

                        is LoginIdResolutionException.ValidationFailed ->
                            PasskeyAssertionStartApiFailure.BadRequest.InvalidLoginId(error.errorCode, error.message, error.loginId, error.regex)
                    }
                )
            }

            AssertionOptionsApiCall
                .create(
                    baseApiUrl = apiBaseUrl.getBaseUrlOrThrow(),
                    serialization = serialization,
                    loginId = loginId,
                    accessToken = accessToken,
                    traceParent = traceParent
                )
                .executeWithInterceptor(network = network, interceptor = interceptor)
        }

        return response.map { options ->
            object : PasskeyAssertionApi.Controller {
                override val assertionOptions: AssertionOptions = options

                override val verify: suspend (AssertionResult) -> ApiResult<AccessToken, PasskeyAssertionVerifyApiFailure> = { assertionResult ->
                    dispatcherProvider.ioDispatcher().runApiCatching(PasskeyAssertionVerifyApiFailure::Unexpected) {
                        AssertionResultApiCall
                            .create(
                                baseApiUrl = apiBaseUrl.getBaseUrlOrThrow(),
                                serialization = serialization,
                                assertionResult = assertionResult,
                                accessToken = accessToken,
                                traceParent = traceParent
                            )
                            .executeWithInterceptor(network = network, interceptor = interceptor)
                    }
                }

                override val cancel: suspend (Reason) -> ApiResult<Unit, PasskeyAssertionCancelApiFailure> = { reason ->
                    dispatcherProvider.ioDispatcher().runApiCatching(PasskeyAssertionCancelApiFailure::Unexpected) {
                        AssertionCancelApiCall
                            .create(
                                baseApiUrl = apiBaseUrl.getBaseUrlOrThrow(),
                                serialization = serialization,
                                challenge = assertionOptions.challenge,
                                reason = reason,
                                traceParent = traceParent
                            )
                            .executeWithInterceptor(network = network, interceptor = interceptor)
                    }
                }
            }
        }
    }

    @InternalOwnIdAPI
    internal companion object {

        fun create(resolver: DIContainerResolver): PasskeyAssertionApi = resolver.runCatchingExceptions {
            PasskeyAssertionApiImpl(
                apiBaseUrl = getOrThrow<ApiBaseUrl>(),
                network = getOrThrow<Network>(),
                serialization = getOrThrow<Serialization>(),
                dispatcherProvider = getOrThrow<DispatcherProvider>(),
                context = getOrNull<Context>(),
                loginIdValidator = getOrNull<LoginIdValidator>(),
                interceptor = getOrNull<ApiCallInterceptor>()
            )
        }.getOrElse { error ->
            object : PasskeyAssertionApi {
                override suspend fun start(params: PasskeyAssertionApi.Params?): ApiResult<PasskeyAssertionApi.Controller, PasskeyAssertionStartApiFailure> =
                    ApiResult.Failure(
                        failure = PasskeyAssertionStartApiFailure.Unexpected(
                            errorCode = ErrorCode.Unknown,
                            message = if (error is MissingDependencyException) error.dependencyName else error.message ?: error::class.java.simpleName,
                            cause = ApiUnexpectedCause.Runtime(error)
                        )
                    )
            }
        }
    }
}
