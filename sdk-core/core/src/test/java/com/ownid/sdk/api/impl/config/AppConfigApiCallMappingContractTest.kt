package com.ownid.sdk.api.impl.config

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.api.impl.apiHttpError
import com.ownid.sdk.api.impl.apiSuccess
import com.ownid.sdk.api.impl.failureValue
import com.ownid.sdk.api.impl.successValue
import com.ownid.sdk.api.internal.ApiUnexpectedCause
import com.ownid.sdk.api.internal.AppConfigApiFailure
import com.ownid.sdk.capability.Network
import com.ownid.sdk.capability.impl.SerializationImpl
import com.ownid.sdk.model.AppConfig
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.LoginId
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.junit.Test
import java.io.IOException

// Covers: WIRE-240, WIRE-250
internal class AppConfigApiCallMappingContractTest {

    private val serialization = SerializationImpl()
    private val baseApiUrl = "https://example.com/api/".toHttpUrl()
    private val url = "https://example.com/api/config/app".toHttpUrl()
    private val request = Request.Builder().url(url).build()
    private val call = AppConfigApiCall(serialization, request)

    @Test
    fun createBuildsGetRequestForAppConfigEndpointWithOptionalTraceParent() {
        val traceParent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-00"

        val request = AppConfigApiCall.create(
            baseApiUrl = baseApiUrl,
            serialization = serialization,
            traceParent = traceParent
        ).request

        assertThat(request.url).isEqualTo(url)
        assertThat(request.method).isEqualTo("GET")
        assertThat(request.body).isNull()
        assertThat(request.header("traceparent")).isEqualTo(traceParent)
        assertThat(request.header("Accept")).isEqualTo("application/json")
        assertThat(request.header("Cache-Control")).isEqualTo("no-store")
    }

    @Test
    fun mapHttpSuccessMapsGeneratedAppAuthConfigToRuntimeAppConfig() {
        val body = """
            {
              "displayName": "Example App",
              "loginIdConfig": [
                {
                  "type": "Email",
                  "regex": ".+@example\\.com"
                },
                {
                  "type": "PhoneNumber"
                }
              ],
              "webView": {
                "baseUrl": "https://login.example.com",
                "html": "<html>OwnID</html>",
                "allowedOrigins": [
                  "https://login.example.com",
                  "https://app.example.com",
                  "https://login.example.com"
                ]
              },
              "ui": {
                "default": {
                  "logoUrl": "https://cdn.example.com/logo-light.png"
                },
                "dark": {
                  "logoUrl": "https://cdn.example.com/logo-dark.png"
                }
              },
              "logLevel": "Debug"
            }
        """.trimIndent()

        val config = call.mapHttpSuccess(success(body)).successValue()

        assertThat(config.displayName).isEqualTo("Example App")
        assertThat(config.loginIdConfig).containsExactly(
            AppConfig.LoginIdConfig(type = LoginId.Type.Email, regex = ".+@example\\.com"),
            AppConfig.LoginIdConfig(type = LoginId.Type.PhoneNumber)
        ).inOrder()
        assertThat(config.webView).isEqualTo(
            AppConfig.WebViewConfig(
                baseUrl = "https://login.example.com",
                html = "<html>OwnID</html>",
                allowedOrigins = setOf("https://login.example.com", "https://app.example.com")
            )
        )
        assertThat(config.ui).isEqualTo(
            AppConfig.UIConfig(
                default = AppConfig.UIConfig.UIThemeConfig(logoUrl = "https://cdn.example.com/logo-light.png"),
                dark = AppConfig.UIConfig.UIThemeConfig(logoUrl = "https://cdn.example.com/logo-dark.png")
            )
        )
        assertThat(config.logLevel).isEqualTo(AppConfig.LogLevel.DEBUG)
    }

    @Test
    fun mapHttpSuccessDefaultsOptionalServerFieldsWithoutProviderFallback() {
        val config = call.mapHttpSuccess(
            success(
                body = """
                    {
                      "loginIdConfig": [
                        {
                          "type": "UserName"
                        }
                      ]
                    }
                """.trimIndent()
            )
        ).successValue()

        assertThat(config.loginIdConfig).containsExactly(AppConfig.LoginIdConfig(type = LoginId.Type.UserName))
        assertThat(config.displayName).isNull()
        assertThat(config.webView).isNull()
        assertThat(config.ui).isNull()
        assertThat(config.logLevel).isEqualTo(AppConfig.LogLevel.WARNING)
    }

    @Test
    fun mapHttpSuccessMapsMalformedSuccessBodyToUnexpected() {
        val failure = call.mapHttpSuccess(success("""{"displayName":"missing login id config"}""")).failureValue()

        assertThat(failure).isInstanceOf(AppConfigApiFailure.Unexpected::class.java)
        assertThat(failure.errorCode).isEqualTo(ErrorCode.Unknown)
        failure as AppConfigApiFailure.Unexpected
        assertThat(failure.cause).isInstanceOf(ApiUnexpectedCause.ApiContract::class.java)
        val cause = failure.cause as ApiUnexpectedCause.ApiContract
        assertThat(cause.failure).isInstanceOf(Network.Response.Fail.ResponseError::class.java)
        val responseError = cause.failure as Network.Response.Fail.ResponseError
        assertThat(responseError.statusCode).isEqualTo(200)
        assertThat(responseError.error).isInstanceOf(IllegalStateException::class.java)
        assertThat(responseError.error.cause).isNotNull()
    }

    @Test
    fun mapHttpErrorMapsBadRequestAndUnhandledStatus() {
        val badRequest = call.mapHttpError(
            apiHttpError(
                url = url,
                statusCode = 400,
                body = """
                    {
                      "errorCode": "invalid_argument",
                      "message": "app config request is invalid"
                    }
                """.trimIndent()
            )
        )

        assertThat(badRequest).isInstanceOf(AppConfigApiFailure.BadRequest::class.java)
        assertThat(badRequest.errorCode).isEqualTo(ErrorCode.InvalidArgument)

        val unhandled = call.mapHttpError(
            apiHttpError(url = url, statusCode = 503, body = """{"message":"unavailable"}""")
        )

        assertThat(unhandled).isInstanceOf(AppConfigApiFailure.Unexpected::class.java)
        assertThat(unhandled.errorCode).isEqualTo(ErrorCode.Unknown)
        assertThat((unhandled as AppConfigApiFailure.Unexpected).cause).isInstanceOf(ApiUnexpectedCause.ApiContract::class.java)
    }

    @Test
    fun mapUnhandledMapsNetworkFailureToUnexpectedNetworkError() {
        val failure = call.mapUnhandled(Network.Response.Fail.NetworkError(url = url, error = IOException("offline")))

        assertThat(failure).isInstanceOf(AppConfigApiFailure.Unexpected::class.java)
        assertThat(failure.errorCode).isEqualTo(ErrorCode.Network)
        assertThat((failure as AppConfigApiFailure.Unexpected).cause).isInstanceOf(ApiUnexpectedCause.Network::class.java)
    }

    private fun success(body: String): Network.Response.Success =
        apiSuccess(url, code = 200, body = body)
}
