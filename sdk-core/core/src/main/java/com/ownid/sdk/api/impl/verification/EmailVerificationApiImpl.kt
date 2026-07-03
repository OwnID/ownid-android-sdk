package com.ownid.sdk.api.impl.verification

import com.ownid.sdk.api.ApiBaseUrl
import com.ownid.sdk.api.ApiFailureScope
import com.ownid.sdk.api.ApiResult
import com.ownid.sdk.api.EmailVerificationApi
import com.ownid.sdk.api.EmailVerificationCancelApiFailure
import com.ownid.sdk.api.EmailVerificationCompleteApiFailure
import com.ownid.sdk.api.EmailVerificationResendApiFailure
import com.ownid.sdk.api.EmailVerificationStartApiFailure
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

internal class EmailVerificationApiImpl(
    private val apiBaseUrl: ApiBaseUrl,
    private val network: Network,
    private val serialization: Serialization,
    private val dispatcherProvider: DispatcherProvider,
    private val context: Context?,
    private val loginIdValidator: LoginIdValidator?,
    private val interceptor: ApiCallInterceptor?
) : EmailVerificationApi {

    override suspend fun start(params: EmailVerificationApi.Params?): ApiResult<EmailVerificationApi.Controller, EmailVerificationStartApiFailure> {
        val accessToken = params?.accessToken ?: context?.accessToken
        val traceParent = params?.traceParent ?: TraceContext.generateTraceparent()
        val response = dispatcherProvider.ioDispatcher().runApiCatching(EmailVerificationStartApiFailure::Unexpected) {
            val loginId = try {
                params?.loginId ?: context?.loginId(loginIdValidator)
            } catch (error: LoginIdResolutionException) {
                return@runApiCatching ApiResult.Failure(
                    when (error) {
                        is LoginIdResolutionException.MissingValidator ->
                            EmailVerificationStartApiFailure.FailedDependency.MissingProvider(
                                error.errorCode, error.message, "LoginIdValidator", ApiFailureScope.DATA
                            )

                        is LoginIdResolutionException.TypeNotSupported ->
                            EmailVerificationStartApiFailure.BadRequest.UnsupportedLoginIdType(error.errorCode, error.message)

                        is LoginIdResolutionException.ValidationFailed ->
                            EmailVerificationStartApiFailure.BadRequest.InvalidLoginId(error.errorCode, error.message, error.loginId, error.regex)
                    }
                )
            }

            EmailVerificationStartApiCall
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
            object : EmailVerificationApi.Controller {
                override val challenge: VerificationChallenge = verificationChallenge

                override val completeWithCode: suspend (String) -> ApiResult<AccessOrProofToken, EmailVerificationCompleteApiFailure> = { code ->
                    dispatcherProvider.ioDispatcher().runApiCatching(EmailVerificationCompleteApiFailure::Unexpected) {
                        EmailVerificationCompleteApiCall
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

                override val resend: suspend () -> ApiResult<Unit, EmailVerificationResendApiFailure> = {
                    dispatcherProvider.ioDispatcher().runApiCatching(EmailVerificationResendApiFailure::Unexpected) {
                        EmailVerificationResendApiCall
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

                override val cancel: suspend (Reason) -> ApiResult<Unit, EmailVerificationCancelApiFailure> = { reason ->
                    dispatcherProvider.ioDispatcher().runApiCatching(EmailVerificationCancelApiFailure::Unexpected) {
                        EmailVerificationCancelApiCall
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

        fun create(resolver: DIContainerResolver): EmailVerificationApi = resolver.runCatchingExceptions {
            EmailVerificationApiImpl(
                apiBaseUrl = getOrThrow<ApiBaseUrl>(),
                network = getOrThrow<Network>(),
                serialization = getOrThrow<Serialization>(),
                dispatcherProvider = getOrThrow<DispatcherProvider>(),
                context = getOrNull<Context>(),
                loginIdValidator = getOrNull<LoginIdValidator>(),
                interceptor = getOrNull<ApiCallInterceptor>()
            )
        }.getOrElse { error ->
            object : EmailVerificationApi {
                override suspend fun start(params: EmailVerificationApi.Params?): ApiResult<EmailVerificationApi.Controller, EmailVerificationStartApiFailure> =
                    ApiResult.Failure(
                        failure = EmailVerificationStartApiFailure.Unexpected(
                            errorCode = ErrorCode.Unknown,
                            message = if (error is MissingDependencyException) error.dependencyName else error.message ?: error::class.java.simpleName,
                            cause = ApiUnexpectedCause.Runtime(error)
                        )
                    )
            }
        }
    }
}
