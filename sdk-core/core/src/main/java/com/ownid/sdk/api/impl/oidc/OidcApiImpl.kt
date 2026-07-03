package com.ownid.sdk.api.impl.oidc

import com.ownid.sdk.api.ApiBaseUrl
import com.ownid.sdk.api.ApiResult
import com.ownid.sdk.api.OidcApi
import com.ownid.sdk.api.OidcCancelApiFailure
import com.ownid.sdk.api.OidcCompleteApiFailure
import com.ownid.sdk.api.OidcStartApiFailure
import com.ownid.sdk.api.impl.ApiCallInterceptor
import com.ownid.sdk.api.impl.executeWithInterceptor
import com.ownid.sdk.api.impl.runApiCatching
import com.ownid.sdk.api.internal.ApiUnexpectedCause
import com.ownid.sdk.api.map
import com.ownid.sdk.capability.DispatcherProvider
import com.ownid.sdk.capability.Network
import com.ownid.sdk.capability.Serialization
import com.ownid.sdk.di.DIContainerResolver
import com.ownid.sdk.di.MissingDependencyException
import com.ownid.sdk.di.getOrNull
import com.ownid.sdk.di.getOrThrow
import com.ownid.sdk.logger.TraceContext
import com.ownid.sdk.model.AccessTokenWithUserInfo
import com.ownid.sdk.model.Context
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.OauthResponseType
import com.ownid.sdk.model.Reason
import com.ownid.sdk.model.SocialProvider
import com.ownid.sdk.model.accessToken
import com.ownid.sdk.model.runCatchingExceptions

internal class OidcApiImpl(
    private val apiBaseUrl: ApiBaseUrl,
    private val network: Network,
    private val serialization: Serialization,
    private val dispatcherProvider: DispatcherProvider,
    private val context: Context?,
    private val interceptor: ApiCallInterceptor?
) : OidcApi {

    override suspend fun start(params: OidcApi.Params?): ApiResult<OidcApi.Controller, OidcStartApiFailure> {
        val accessToken = params?.accessToken ?: context?.accessToken
        val traceParent = params?.traceParent ?: TraceContext.generateTraceparent()
        val expectedResponseType = params?.oauthResponseType ?: OauthResponseType.ID_TOKEN
        val response = dispatcherProvider.ioDispatcher().runApiCatching(OidcStartApiFailure::Unexpected) {
            OidcChallengeStartApiCall
                .create(
                    baseApiUrl = apiBaseUrl.getBaseUrlOrThrow(),
                    serialization = serialization,
                    provider = params?.provider ?: SocialProvider.GOOGLE,
                    oauthResponseType = expectedResponseType,
                    accessToken = accessToken,
                    loginIdHint = null,
                    redirectUri = null,
                    traceParent = traceParent
                )
                .executeWithInterceptor(network = network, interceptor = interceptor)
        }

        return response.map { oidcChallenge ->
            object : OidcApi.Controller {
                override val challenge = oidcChallenge

                override val completeWithCode: suspend (String) -> ApiResult<AccessTokenWithUserInfo, OidcCompleteApiFailure> = { code ->
                    if (expectedResponseType != OauthResponseType.CODE) {
                        val message =
                            "OIDC completion method does not match requested response type. Expected: $expectedResponseType, actual: ${OauthResponseType.CODE}"
                        ApiResult.Failure(OidcCompleteApiFailure.BadRequest.InvalidArgument(ErrorCode.InvalidArgument, message))
                    } else {
                        dispatcherProvider.ioDispatcher().runApiCatching(OidcCompleteApiFailure::Unexpected) {
                            OidcChallengeCompleteWithCodeApiCall.create(
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
                }

                override val completeWithToken: suspend (String) -> ApiResult<AccessTokenWithUserInfo, OidcCompleteApiFailure> = { idToken ->
                    if (expectedResponseType != OauthResponseType.ID_TOKEN) {
                        val message =
                            "OIDC completion method does not match requested response type. Expected: $expectedResponseType, actual: ${OauthResponseType.ID_TOKEN}"
                        ApiResult.Failure(OidcCompleteApiFailure.BadRequest.InvalidArgument(ErrorCode.InvalidArgument, message))
                    } else {
                        dispatcherProvider.ioDispatcher().runApiCatching(OidcCompleteApiFailure::Unexpected) {
                            OidcChallengeCompleteWithIdTokenApiCall.create(
                                baseApiUrl = apiBaseUrl.getBaseUrlOrThrow(),
                                serialization = serialization,
                                challengeId = challenge.challengeId,
                                idToken = idToken,
                                accessToken = accessToken,
                                traceParent = traceParent
                            )
                                .executeWithInterceptor(network = network, interceptor = interceptor)
                        }
                    }
                }

                override val cancel: suspend (Reason) -> ApiResult<Unit, OidcCancelApiFailure> = { reason ->
                    dispatcherProvider.ioDispatcher().runApiCatching(OidcCancelApiFailure::Unexpected) {
                        OidcChallengeCancelApiCall
                            .create(
                                baseApiUrl = apiBaseUrl.getBaseUrlOrThrow(),
                                serialization = serialization,
                                challengeId = challenge.challengeId,
                                reason = reason,
                                accessToken = accessToken,
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

        fun create(resolver: DIContainerResolver): OidcApi = resolver.runCatchingExceptions {
            OidcApiImpl(
                apiBaseUrl = getOrThrow<ApiBaseUrl>(),
                network = getOrThrow<Network>(),
                serialization = getOrThrow<Serialization>(),
                dispatcherProvider = getOrThrow<DispatcherProvider>(),
                context = getOrNull<Context>(),
                interceptor = getOrNull<ApiCallInterceptor>()
            )
        }.getOrElse { error ->
            object : OidcApi {
                override suspend fun start(params: OidcApi.Params?): ApiResult<OidcApi.Controller, OidcStartApiFailure> = ApiResult.Failure(
                    failure = OidcStartApiFailure.Unexpected(
                        errorCode = ErrorCode.Unknown,
                        message = if (error is MissingDependencyException) error.dependencyName else error.message ?: error::class.java.simpleName,
                        cause = ApiUnexpectedCause.Runtime(error)
                    )
                )
            }
        }
    }
}
