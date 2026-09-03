package com.ownid.sdk.api.impl.login

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.api.ApiUnexpectedFailure
import com.ownid.sdk.api.DiscoverApiFailure
import com.ownid.sdk.api.LoginApiFailure
import com.ownid.sdk.api.impl.apiSuccess
import com.ownid.sdk.api.impl.assertJsonPostRequest
import com.ownid.sdk.api.impl.assertUnexpectedApiContractResponseFailure
import com.ownid.sdk.api.impl.failureValue
import com.ownid.sdk.api.impl.jsonBody
import com.ownid.sdk.api.impl.jsonString
import com.ownid.sdk.api.impl.successValue
import com.ownid.sdk.capability.Network
import com.ownid.sdk.capability.impl.SerializationImpl
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.LoginResponse
import com.ownid.sdk.model.OperationChannel
import com.ownid.sdk.model.OperationType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.junit.Test

// Covers: MODEL-060, WIRE-010, WIRE-020
internal class LoginDiscoverApiCallSuccessMappingContractTest {

    private val serialization = SerializationImpl()
    private val baseUrl = "https://example.com/api/".toHttpUrl()
    private val url = baseUrl.newBuilder().addEncodedPathSegments("login").build()
    private val request = Request.Builder().url(url).build()
    private val loginCall = LoginApiCall(serialization, request)
    private val discoverCall = DiscoverApiCall(serialization, request)

    @Test
    fun loginCreateBuildsLoginRequestWithAccessTokenTraceParentAndPasskeyPeek() {
        val traceParent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-00"

        val call = LoginApiCall.create(
            baseApiUrl = baseUrl,
            serialization = serialization,
            accessToken = AccessToken("access-token"),
            traceParent = traceParent
        )

        assertJsonPostRequest(
            request = call.request,
            url = "https://example.com/api/login",
            authorization = "Bearer access-token",
            traceParent = traceParent
        )
        assertThat(call.request.url.encodedPath).isEqualTo("/api/login")

        val body = call.request.jsonBody(serialization)
        assertThat(body.keys).containsExactly("extendedClientCapabilities")
        assertThat(body.passkeyPeek()).isTrue()
    }

    @Test
    fun discoverCreateBuildsLoginRequestWithLoginIdTraceParentAndPasskeyPeek() {
        val traceParent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-00"

        val call = DiscoverApiCall.create(
            baseApiUrl = baseUrl,
            serialization = serialization,
            loginId = LoginId("user@example.com", LoginId.Type.Email),
            traceParent = traceParent
        )

        assertJsonPostRequest(
            request = call.request,
            url = "https://example.com/api/login",
            authorization = null,
            traceParent = traceParent
        )
        assertThat(call.request.url.encodedPath).isEqualTo("/api/login")

        val body = call.request.jsonBody(serialization)
        assertThat(body.keys).containsExactly("loginId", "extendedClientCapabilities")
        assertThat(body.getValue("loginId").jsonObject.jsonString("id")).isEqualTo("user@example.com")
        assertThat(body.getValue("loginId").jsonObject.jsonString("type")).isEqualTo("Email")
        assertThat(body.passkeyPeek()).isTrue()
    }

    @Test
    fun mapHttpSuccessMapsCreatedLoginResponseAndPreservesRawSessionPayloadForLoginAndDiscover() {
        val body = """
            {
              "accessToken": "ownid-access-token",
              "sessionPayload": {"sessionId":"session-1","claims":["email",{"verified":true}]}
            }
        """.trimIndent()
        val loginResponse = loginCall.mapHttpSuccess(success(code = 201, body = body)).successValue()
        assertLoginSuccessResponse(loginResponse)

        val discoverResponse = discoverCall.mapHttpSuccess(success(code = 201, body = body)).successValue()
        assertLoginSuccessResponse(discoverResponse)
    }

    @Test
    fun mapHttpSuccessMapsAuthRequirementsOperationsAndChannelsForLoginAndDiscover() {
        val body = """
            {
              "reason": "additional authentication required",
              "authRequirements": {
                "targetScore": 100,
                "operations": [
                  {
                    "type": "PasskeyAuth",
                    "score": 70
                  },
                  {
                    "type": "EmailVerification",
                    "score": 30,
                    "channels": [
                      {
                        "channel": "p***n@example.com",
                        "id": "email-channel-1"
                      }
                    ]
                  }
                ]
              }
            }
        """.trimIndent()

        assertAuthRequired(loginCall.mapHttpSuccess(success(code = 206, body = body)).successValue())
        assertAuthRequired(discoverCall.mapHttpSuccess(success(code = 206, body = body)).successValue())
    }

    @Test
    fun mapHttpSuccessRejectsNegativeAuthRequirementScoresAndAllowsZeroForLoginAndDiscover() {
        val invalidBodies = listOf(
            """
                {
                  "authRequirements": {
                    "targetScore": 1,
                    "operations": [{"type":"EmailVerification","score":-1}]
                  }
                }
            """.trimIndent(),
            """
                {
                  "authRequirements": {
                    "targetScore": -1,
                    "operations": [{"type":"EmailVerification","score":0}]
                  }
                }
            """.trimIndent()
        )

        invalidBodies.forEach { body ->
            val loginFailure = loginCall.mapHttpSuccess(success(code = 206, body = body)).failureValue()
            assertThat(loginFailure).isInstanceOf(LoginApiFailure.Unexpected::class.java)
            assertUnexpectedApiContractResponseFailure(loginFailure as ApiUnexpectedFailure, statusCode = 206)

            val discoverFailure = discoverCall.mapHttpSuccess(success(code = 206, body = body)).failureValue()
            assertThat(discoverFailure).isInstanceOf(DiscoverApiFailure.Unexpected::class.java)
            assertUnexpectedApiContractResponseFailure(discoverFailure as ApiUnexpectedFailure, statusCode = 206)
        }

        val zeroBody = """
            {
              "authRequirements": {
                "targetScore": 0,
                "operations": [{"type":"EmailVerification","score":0}]
              }
            }
        """.trimIndent()

        listOf(
            loginCall.mapHttpSuccess(success(code = 206, body = zeroBody)).successValue(),
            discoverCall.mapHttpSuccess(success(code = 206, body = zeroBody)).successValue()
        ).forEach { response ->
            assertThat(response).isInstanceOf(LoginResponse.AuthRequired::class.java)
            response as LoginResponse.AuthRequired
            assertThat(response.authRequirements.targetScore).isEqualTo(0)
            assertThat(response.authRequirements.operations.single().score).isEqualTo(0)
        }
    }

    @Test
    fun mapHttpSuccessMapsAccountBlockedForLoginAndDiscover() {
        val body = """
            {
              "reason": "account is blocked",
              "accountBlocked": true
            }
        """.trimIndent()

        val loginResponse = loginCall.mapHttpSuccess(success(code = 206, body = body)).successValue()
        assertThat(loginResponse).isInstanceOf(LoginResponse.AccountBlocked::class.java)
        loginResponse as LoginResponse.AccountBlocked
        assertThat(loginResponse.reason).isEqualTo("account is blocked")

        val discoverResponse = discoverCall.mapHttpSuccess(success(code = 206, body = body)).successValue()
        assertThat(discoverResponse).isInstanceOf(LoginResponse.AccountBlocked::class.java)
        discoverResponse as LoginResponse.AccountBlocked
        assertThat(discoverResponse.reason).isEqualTo("account is blocked")
    }

    @Test
    fun mapHttpSuccessMapsAccountNotFoundForLoginAndDiscover() {
        val body = """
            {
              "reason": "account was not found",
              "accountNotFound": true
            }
        """.trimIndent()

        val loginResponse = loginCall.mapHttpSuccess(success(code = 206, body = body)).successValue()
        assertThat(loginResponse).isInstanceOf(LoginResponse.AccountNotFound::class.java)
        loginResponse as LoginResponse.AccountNotFound
        assertThat(loginResponse.reason).isEqualTo("account was not found")

        val discoverResponse = discoverCall.mapHttpSuccess(success(code = 206, body = body)).successValue()
        assertThat(discoverResponse).isInstanceOf(LoginResponse.AccountNotFound::class.java)
        discoverResponse as LoginResponse.AccountNotFound
        assertThat(discoverResponse.reason).isEqualTo("account was not found")
    }

    private fun assertAuthRequired(response: LoginResponse) {
        assertThat(response).isInstanceOf(LoginResponse.AuthRequired::class.java)
        response as LoginResponse.AuthRequired

        assertThat(response.reason).isEqualTo("additional authentication required")
        assertThat(response.authRequirements.targetScore).isEqualTo(100)
        assertThat(response.authRequirements.operations.map { it.type to it.score }).containsExactly(
            OperationType.PASSKEY_AUTH to 70,
            OperationType.EMAIL_VERIFICATION to 30
        ).inOrder()
        assertThat(response.authRequirements.isTargetScoreAchievable()).isTrue()

        val passkey = response.authRequirements.operations[0]
        assertThat(passkey.type).isEqualTo(OperationType.PASSKEY_AUTH)
        assertThat(passkey.score).isEqualTo(70)
        assertThat(passkey.channels).isNull()

        val email = response.authRequirements.operations[1]
        assertThat(email.type).isEqualTo(OperationType.EMAIL_VERIFICATION)
        assertThat(email.score).isEqualTo(30)
        assertThat(email.channels).containsExactly(OperationChannel(channel = "p***n@example.com", id = "email-channel-1"))
    }

    private fun assertLoginSuccessResponse(response: LoginResponse) {
        assertThat(response).isInstanceOf(LoginResponse.Success::class.java)
        response as LoginResponse.Success
        assertThat(response.accessToken).isEqualTo(AccessToken("ownid-access-token"))
        assertThat(response.sessionPayload).isEqualTo("""{"sessionId":"session-1","claims":["email",{"verified":true}]}""")
    }

    private fun JsonObject.passkeyPeek(): Boolean? =
        getValue("extendedClientCapabilities").jsonObject
            .getValue("passkeys").jsonObject
            .getValue("peek").jsonPrimitive.booleanOrNull

    private fun success(code: Int, body: String): Network.Response.Success =
        apiSuccess(url, code, body)
}
