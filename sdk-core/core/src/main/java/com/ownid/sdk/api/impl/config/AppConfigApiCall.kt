package com.ownid.sdk.api.impl.config

import com.ownid.sdk.api.ApiResult
import com.ownid.sdk.api.impl.ApiCall
import com.ownid.sdk.api.impl.models.AppAuthConfig
import com.ownid.sdk.api.impl.models.LogLevel.DEBUG
import com.ownid.sdk.api.impl.models.LogLevel.ERROR
import com.ownid.sdk.api.impl.models.LogLevel.INFORMATION
import com.ownid.sdk.api.impl.models.LogLevel.NONE
import com.ownid.sdk.api.impl.models.LogLevel.WARNING
import com.ownid.sdk.api.impl.models.oneof.mapBadRequestErrorResponse
import com.ownid.sdk.api.impl.runApiMappingCatching
import com.ownid.sdk.api.impl.toDomainModel
import com.ownid.sdk.api.impl.toSuccessMappingFail
import com.ownid.sdk.api.impl.toUnexpectedFailure
import com.ownid.sdk.api.impl.toUnexpectedStatusFail
import com.ownid.sdk.api.internal.AppConfigApiFailure
import com.ownid.sdk.capability.Network
import com.ownid.sdk.capability.Serialization
import com.ownid.sdk.model.AppConfig
import okhttp3.HttpUrl
import okhttp3.Request

internal class AppConfigApiCall(
    private val serialization: Serialization,
    override val request: Request
) : ApiCall<AppConfig, AppConfigApiFailure> {

    override fun mapHttpSuccess(successResponse: Network.Response.Success): ApiResult<AppConfig, AppConfigApiFailure> = when (successResponse.code) {
        200 -> runApiMappingCatching {
            val response = serialization.decodeFromString(AppAuthConfig.serializer(), successResponse.body)

            val result = AppConfig(
                loginIdConfig = response.loginIdConfig.map { loginIdConfig ->
                    AppConfig.LoginIdConfig(type = loginIdConfig.type.toDomainModel(), regex = loginIdConfig.regex)
                },
                displayName = response.displayName,
                webView = response.webView?.let { webViewConfig ->
                    AppConfig.WebViewConfig(
                        baseUrl = webViewConfig.baseUrl,
                        html = webViewConfig.html,
                        allowedOrigins = webViewConfig.allowedOrigins?.toSet()
                    )
                },
                ui = response.ui?.let { uiConfig ->
                    AppConfig.UIConfig(
                        default = AppConfig.UIConfig.UIThemeConfig(logoUrl = uiConfig.default.logoUrl),
                        dark = uiConfig.dark?.let { default -> AppConfig.UIConfig.UIThemeConfig(logoUrl = default.logoUrl) }
                    )
                },
                logLevel = when (response.logLevel) {
                    ERROR -> AppConfig.LogLevel.ERROR
                    WARNING -> AppConfig.LogLevel.WARNING
                    INFORMATION -> AppConfig.LogLevel.INFORMATION
                    DEBUG -> AppConfig.LogLevel.DEBUG
                    NONE -> AppConfig.LogLevel.NONE
                    null -> AppConfig.LogLevel.WARNING
                }
            )

            ApiResult.Success(result)
        }.getOrElse { error ->
            ApiResult.Failure(
                mapUnhandled(successResponse.toSuccessMappingFail(message = "Failed to parse success response from ${successResponse.url}", cause = error))
            )
        }

        else -> ApiResult.Failure(mapUnhandled(successResponse.toUnexpectedStatusFail()))
    }

    override fun mapHttpError(failResponse: Network.Response.Fail.HttpError): AppConfigApiFailure = when (failResponse.statusCode) {
        400 -> runApiMappingCatching {
            serialization.mapBadRequestErrorResponse(
                body = failResponse.body,
                invalidArgument = AppConfigApiFailure::BadRequest,
                unknown = AppConfigApiFailure::BadRequest
            )
        }.getOrElse { error ->
            failResponse.toUnexpectedFailure(AppConfigApiFailure::Unexpected, error)
        }

        else -> failResponse.toUnexpectedFailure(AppConfigApiFailure::Unexpected)
    }

    override fun mapUnhandled(failResponse: Network.Response.Fail): AppConfigApiFailure =
        failResponse.toUnexpectedFailure(AppConfigApiFailure::Unexpected)

    internal companion object {
        internal fun create(
            baseApiUrl: HttpUrl,
            serialization: Serialization,
            traceParent: String?
        ): AppConfigApiCall {

            val request = Network.Request.Builder(
                url = baseApiUrl.newBuilder().addEncodedPathSegments("config/app").build()
            ).apply {
                method = Network.Request.Method.GET
                queueTraceParent(traceParent)
            }.build()

            return AppConfigApiCall(serialization, request)
        }
    }
}
