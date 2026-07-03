package com.ownid.sdk.api.impl.oidc

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.api.impl.apiSuccess
import com.ownid.sdk.api.impl.assertJsonPostRequest
import com.ownid.sdk.api.impl.assertJsonStringsExactly
import com.ownid.sdk.api.impl.assertUnitSuccess
import com.ownid.sdk.api.impl.jsonBody
import com.ownid.sdk.api.impl.successValue
import com.ownid.sdk.capability.Network
import com.ownid.sdk.capability.impl.SerializationImpl
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.ChallengeId
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.OauthResponseType
import com.ownid.sdk.model.Reason
import com.ownid.sdk.model.SocialProvider
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Test

// Covers: WIRE-190
internal class OidcApiCallMappingContractTest {

    private val serialization = SerializationImpl()
    private val baseUrl = "https://auth.example.test/api/".toHttpUrl()

    @Test
    fun startCreateBuildsProviderRequestAndMapsCreatedChallenge() {
        val call = OidcChallengeStartApiCall.create(
            baseApiUrl = baseUrl,
            serialization = serialization,
            provider = SocialProvider.GOOGLE,
            oauthResponseType = OauthResponseType.CODE,
            accessToken = AccessToken("access-token"),
            loginIdHint = "user@example.com",
            redirectUri = "ownid://callback",
            traceParent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-00"
        )

        assertJsonPostRequest(
            request = call.request,
            url = "https://auth.example.test/api/oidc/idp/start/google",
            authorization = "Bearer access-token",
            traceParent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-00"
        )
        call.request.jsonBody(serialization).assertJsonStringsExactly(
            "loginIdHint" to "user@example.com",
            "oauthResponseType" to "code",
            "redirectUri" to "ownid://callback"
        )

        val result = call.mapHttpSuccess(
            success(
                url = call.request.url,
                code = 201,
                body = """
                    {
                      "challengeId": "oidc-challenge-1",
                      "timeout": 45000,
                      "clientId": "google-client-id",
                      "challengeUrl": "https://accounts.example.test/challenge"
                    }
                """.trimIndent()
            )
        )

        val challenge = result.successValue()
        assertThat(challenge.challengeId).isEqualTo(ChallengeId("oidc-challenge-1"))
        assertThat(challenge.timeout.milliseconds).isEqualTo(45000)
        assertThat(challenge.clientId).isEqualTo("google-client-id")
        assertThat(challenge.challengeUrl).isEqualTo("https://accounts.example.test/challenge")
    }

    @Test
    fun completeWithCodeCreateBuildsCodeRequestAndMapsAccessTokenWithUserInfo() {
        val call = OidcChallengeCompleteWithCodeApiCall.create(
            baseApiUrl = baseUrl,
            serialization = serialization,
            challengeId = ChallengeId("oidc-challenge-2"),
            code = "provider-code",
            accessToken = AccessToken("access-token"),
            traceParent = null
        )

        assertJsonPostRequest(
            request = call.request,
            url = "https://auth.example.test/api/oidc/idp/complete",
            authorization = "Bearer access-token",
            traceParent = null
        )
        call.request.jsonBody(serialization).assertJsonStringsExactly(
            "challengeId" to "oidc-challenge-2",
            "code" to "provider-code"
        )

        val result = call.mapHttpSuccess(success(url = call.request.url, code = 200, body = accessTokenWithUserInfoResponse("Google")))

        val token = result.successValue()
        assertThat(token.accessToken.token).isEqualTo("ownid-access-token")
        assertThat(token.loginId).isEqualTo(LoginId("user@example.com", LoginId.Type.Email))
        assertThat(token.userInfo).containsExactly("name", "Ada", "email", "user@example.com")
        assertThat(token.provider).isEqualTo(SocialProvider.GOOGLE)
    }

    @Test
    fun completeWithIdTokenCreateBuildsIdTokenRequestAndMapsAppleProvider() {
        val call = OidcChallengeCompleteWithIdTokenApiCall.create(
            baseApiUrl = baseUrl,
            serialization = serialization,
            challengeId = ChallengeId("oidc-challenge-3"),
            idToken = "provider-id-token",
            accessToken = null,
            traceParent = "00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-bbbbbbbbbbbbbbbb-01"
        )

        assertJsonPostRequest(
            request = call.request,
            url = "https://auth.example.test/api/oidc/idp/complete",
            authorization = null,
            traceParent = "00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-bbbbbbbbbbbbbbbb-01"
        )
        call.request.jsonBody(serialization).assertJsonStringsExactly(
            "challengeId" to "oidc-challenge-3",
            "idToken" to "provider-id-token"
        )

        val result = call.mapHttpSuccess(success(url = call.request.url, code = 200, body = accessTokenWithUserInfoResponse("Apple")))

        val token = result.successValue()
        assertThat(token.provider).isEqualTo(SocialProvider.APPLE)
        assertThat(token.loginId).isEqualTo(LoginId("user@example.com", LoginId.Type.Email))
        assertThat(token.accessToken.token).isEqualTo("ownid-access-token")
    }

    @Test
    fun cancelCreateBuildsCancelRequestAndMapsNoContentToUnit() {
        val call = OidcChallengeCancelApiCall.create(
            baseApiUrl = baseUrl,
            serialization = serialization,
            challengeId = ChallengeId("oidc-challenge-4"),
            reason = Reason.UserClose(),
            accessToken = AccessToken("access-token"),
            traceParent = null
        )

        assertJsonPostRequest(
            request = call.request,
            url = "https://auth.example.test/api/oidc/idp/cancel",
            authorization = "Bearer access-token",
            traceParent = null
        )
        call.request.jsonBody(serialization).assertJsonStringsExactly(
            "challengeId" to "oidc-challenge-4",
            "reason" to "userClose"
        )

        val result = call.mapHttpSuccess(success(url = call.request.url, code = 204, body = ""))

        result.assertUnitSuccess()
    }

    private fun success(url: HttpUrl, code: Int, body: String): Network.Response.Success =
        apiSuccess(url, code, body)

    private fun accessTokenWithUserInfoResponse(provider: String): String = """
        {
          "accessToken": "ownid-access-token",
          "loginId": { "id": "user@example.com", "type": "Email" },
          "userInfo": {
            "name": "Ada",
            "email": "user@example.com"
          },
          "provider": "$provider"
        }
    """.trimIndent()

}
