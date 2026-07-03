package com.ownid.sdk.api.impl.passkey

import com.ownid.sdk.api.ApiBaseUrl
import com.ownid.sdk.api.ApiFailureScope
import com.ownid.sdk.api.ApiResult
import com.ownid.sdk.api.PasskeyAttestationApi
import com.ownid.sdk.api.PasskeyAttestationCancelApiFailure
import com.ownid.sdk.api.PasskeyAttestationStartApiFailure
import com.ownid.sdk.api.PasskeyAttestationVerifyApiFailure
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
import com.ownid.sdk.model.AttestationOptions
import com.ownid.sdk.model.AttestationResponse
import com.ownid.sdk.model.AttestationResult
import com.ownid.sdk.model.Context
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LoginIdResolutionException
import com.ownid.sdk.model.Reason
import com.ownid.sdk.model.accessToken
import com.ownid.sdk.model.loginId
import com.ownid.sdk.model.runCatchingExceptions

internal class PasskeyAttestationApiImpl(
    private val apiBaseUrl: ApiBaseUrl,
    private val network: Network,
    private val serialization: Serialization,
    private val dispatcherProvider: DispatcherProvider,
    private val context: Context?,
    private val loginIdValidator: LoginIdValidator?,
    private val interceptor: ApiCallInterceptor?,
) : PasskeyAttestationApi {

    override suspend fun start(params: PasskeyAttestationApi.Params?): ApiResult<PasskeyAttestationApi.Controller, PasskeyAttestationStartApiFailure> {
        val accessToken = params?.accessToken ?: context?.accessToken
        val traceParent = params?.traceParent ?: TraceContext.generateTraceparent()
        val response = dispatcherProvider.ioDispatcher().runApiCatching(PasskeyAttestationStartApiFailure::Unexpected) {
            val loginId = try {
                params?.loginId ?: context?.loginId(loginIdValidator)
            } catch (error: LoginIdResolutionException) {
                return@runApiCatching ApiResult.Failure(
                    when (error) {
                        is LoginIdResolutionException.MissingValidator ->
                            PasskeyAttestationStartApiFailure.FailedDependency.MissingProvider(
                                error.errorCode, error.message, "LoginIdValidator", ApiFailureScope.DATA
                            )

                        is LoginIdResolutionException.TypeNotSupported ->
                            PasskeyAttestationStartApiFailure.BadRequest.UnsupportedLoginIdType(error.errorCode, error.message)

                        is LoginIdResolutionException.ValidationFailed ->
                            PasskeyAttestationStartApiFailure.BadRequest.InvalidLoginId(error.errorCode, error.message, error.loginId, error.regex)
                    }
                )
            }

            AttestationOptionsApiCall
                .create(
                    baseApiUrl = apiBaseUrl.getBaseUrlOrThrow(),
                    serialization = serialization,
                    loginId = loginId,
                    accountDisplayName = params?.accountDisplayName ?: context?.accountDisplayName,
                    accessToken = accessToken,
                    traceParent = traceParent
                )
                .executeWithInterceptor(network = network, interceptor = interceptor)
        }

        return response.map { options ->
            object : PasskeyAttestationApi.Controller {
                override val attestationOptions: AttestationOptions = options

                override val verify: suspend (AttestationResult) -> ApiResult<AttestationResponse, PasskeyAttestationVerifyApiFailure> = { attestationResult ->
                    dispatcherProvider.ioDispatcher().runApiCatching(PasskeyAttestationVerifyApiFailure::Unexpected) {
                        AttestationResultApiCall
                            .create(
                                baseApiUrl = apiBaseUrl.getBaseUrlOrThrow(),
                                serialization = serialization,
                                attestationResult = attestationResult,
                                accessToken = accessToken,
                                traceParent = traceParent
                            )
                            .executeWithInterceptor(network = network, interceptor = interceptor)
                    }
                }

                override val cancel: suspend (Reason) -> ApiResult<Unit, PasskeyAttestationCancelApiFailure> = { reason ->
                    dispatcherProvider.ioDispatcher().runApiCatching(PasskeyAttestationCancelApiFailure::Unexpected) {
                        AttestationCancelApiCall
                            .create(
                                baseApiUrl = apiBaseUrl.getBaseUrlOrThrow(),
                                serialization = serialization,
                                challenge = attestationOptions.challenge,
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

        fun create(resolver: DIContainerResolver): PasskeyAttestationApi = resolver.runCatchingExceptions {
            PasskeyAttestationApiImpl(
                apiBaseUrl = getOrThrow<ApiBaseUrl>(),
                network = getOrThrow<Network>(),
                serialization = getOrThrow<Serialization>(),
                dispatcherProvider = getOrThrow<DispatcherProvider>(),
                context = getOrNull<Context>(),
                loginIdValidator = getOrNull<LoginIdValidator>(),
                interceptor = getOrNull<ApiCallInterceptor>()
            )
        }.getOrElse { error ->
            object : PasskeyAttestationApi {
                override suspend fun start(
                    params: PasskeyAttestationApi.Params?
                ): ApiResult<PasskeyAttestationApi.Controller, PasskeyAttestationStartApiFailure> =
                    ApiResult.Failure(
                        failure = PasskeyAttestationStartApiFailure.Unexpected(
                            errorCode = ErrorCode.Unknown,
                            message = if (error is MissingDependencyException) {
                                error.dependencyName
                            } else {
                                error.message ?: error::class.java.simpleName
                            },
                            cause = ApiUnexpectedCause.Runtime(error)
                        )
                    )
            }
        }
    }
}
