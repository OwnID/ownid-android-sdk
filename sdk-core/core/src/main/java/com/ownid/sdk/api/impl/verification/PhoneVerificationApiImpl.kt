package com.ownid.sdk.api.impl.verification

import com.ownid.sdk.api.ApiBaseUrl
import com.ownid.sdk.api.ApiFailureScope
import com.ownid.sdk.api.ApiResult
import com.ownid.sdk.api.PhoneVerificationApi
import com.ownid.sdk.api.PhoneVerificationCancelApiFailure
import com.ownid.sdk.api.PhoneVerificationCompleteApiFailure
import com.ownid.sdk.api.PhoneVerificationResendApiFailure
import com.ownid.sdk.api.PhoneVerificationStartApiFailure
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
import com.ownid.sdk.model.AccessOrProofToken
import com.ownid.sdk.model.Context
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LoginIdResolutionException
import com.ownid.sdk.model.Reason
import com.ownid.sdk.model.VerificationChallenge
import com.ownid.sdk.model.accessToken
import com.ownid.sdk.model.loginId
import com.ownid.sdk.model.runCatchingExceptions

internal class PhoneVerificationApiImpl(
    private val apiBaseUrl: ApiBaseUrl,
    private val network: Network,
    private val serialization: Serialization,
    private val dispatcherProvider: DispatcherProvider,
    private val context: Context?,
    private val loginIdValidator: LoginIdValidator?,
    private val interceptor: ApiCallInterceptor?
) : PhoneVerificationApi {

    override suspend fun start(params: PhoneVerificationApi.Params?): ApiResult<PhoneVerificationApi.Controller, PhoneVerificationStartApiFailure> {
        val accessToken = params?.accessToken ?: context?.accessToken
        val traceParent = params?.traceParent ?: TraceContext.generateTraceparent()
        val response = dispatcherProvider.ioDispatcher().runApiCatching(PhoneVerificationStartApiFailure::Unexpected) {
            val loginId = try {
                params?.loginId ?: context?.loginId(loginIdValidator)
            } catch (error: LoginIdResolutionException) {
                return@runApiCatching ApiResult.Failure(
                    when (error) {
                        is LoginIdResolutionException.MissingValidator ->
                            PhoneVerificationStartApiFailure.FailedDependency.MissingProvider(
                                error.errorCode, error.message, "LoginIdValidator", ApiFailureScope.DATA
                            )

                        is LoginIdResolutionException.TypeNotSupported ->
                            PhoneVerificationStartApiFailure.BadRequest.UnsupportedLoginIdType(error.errorCode, error.message)

                        is LoginIdResolutionException.ValidationFailed ->
                            PhoneVerificationStartApiFailure.BadRequest.InvalidLoginId(error.errorCode, error.message, error.loginId, error.regex)
                    }
                )
            }

            PhoneVerificationStartApiCall
                .create(
                    baseApiUrl = apiBaseUrl.getBaseUrlOrThrow(),
                    serialization = serialization,
                    loginId = loginId,
                    loginIdHintId = params?.loginIdHintId,
                    accessToken = accessToken,
                    verificationMethods = params?.verificationMethods,
                    magicLinkRedirectUrl = params?.magicLinkRedirectUrl,
                    traceParent = traceParent
                )
                .executeWithInterceptor(network = network, interceptor = interceptor)
        }

        return response.map { verificationChallenge ->
            object : PhoneVerificationApi.Controller {
                override val challenge: VerificationChallenge = verificationChallenge

                override val completeWithCode: suspend (String) -> ApiResult<AccessOrProofToken, PhoneVerificationCompleteApiFailure> = { code ->
                    dispatcherProvider.ioDispatcher().runApiCatching(PhoneVerificationCompleteApiFailure::Unexpected) {
                        PhoneVerificationCompleteApiCall
                            .create(
                                baseApiUrl = apiBaseUrl.getBaseUrlOrThrow(),
                                serialization = serialization,
                                challengeId = challenge.challengeId,
                                code = code,
                                accessToken = accessToken,
                                traceParent = traceParent
                            )
                            .executeWithInterceptor(network = network, interceptor = interceptor)
                    }
                }

                override val resend: suspend () -> ApiResult<Unit, PhoneVerificationResendApiFailure> = {
                    dispatcherProvider.ioDispatcher().runApiCatching(PhoneVerificationResendApiFailure::Unexpected) {
                        PhoneVerificationResendApiCall
                            .create(
                                baseApiUrl = apiBaseUrl.getBaseUrlOrThrow(),
                                serialization = serialization,
                                challengeId = challenge.challengeId,
                                accessToken = accessToken,
                                traceParent = traceParent
                            )
                            .executeWithInterceptor(network = network, interceptor = interceptor)
                    }
                }

                override val cancel: suspend (Reason) -> ApiResult<Unit, PhoneVerificationCancelApiFailure> = { reason ->
                    dispatcherProvider.ioDispatcher().runApiCatching(PhoneVerificationCancelApiFailure::Unexpected) {
                        PhoneVerificationCancelApiCall
                            .create(
                                baseApiUrl = apiBaseUrl.getBaseUrlOrThrow(),
                                serialization = serialization,
                                challengeId = challenge.challengeId,
                                accessToken = accessToken,
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

        fun create(resolver: DIContainerResolver): PhoneVerificationApi = resolver.runCatchingExceptions {
            PhoneVerificationApiImpl(
                apiBaseUrl = getOrThrow<ApiBaseUrl>(),
                network = getOrThrow<Network>(),
                serialization = getOrThrow<Serialization>(),
                dispatcherProvider = getOrThrow<DispatcherProvider>(),
                context = getOrNull<Context>(),
                loginIdValidator = getOrNull<LoginIdValidator>(),
                interceptor = getOrNull<ApiCallInterceptor>()
            )
        }.getOrElse { error ->
            object : PhoneVerificationApi {
                override suspend fun start(params: PhoneVerificationApi.Params?): ApiResult<PhoneVerificationApi.Controller, PhoneVerificationStartApiFailure> =
                    ApiResult.Failure(
                        failure = PhoneVerificationStartApiFailure.Unexpected(
                            errorCode = ErrorCode.Unknown,
                            message = if (error is MissingDependencyException) error.dependencyName else error.message ?: error::class.java.simpleName,
                            cause = ApiUnexpectedCause.Runtime(error)
                        )
                    )
            }
        }
    }
}
