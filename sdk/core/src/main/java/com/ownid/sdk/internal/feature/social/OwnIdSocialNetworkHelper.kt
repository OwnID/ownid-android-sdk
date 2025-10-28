package com.ownid.sdk.internal.feature.social

import androidx.annotation.RestrictTo
import com.ownid.sdk.Configuration
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnIdCoreImpl
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.internal.applyAppUrlHeader
import com.ownid.sdk.internal.await
import com.ownid.sdk.internal.component.OwnIdInternalLogger
import okhttp3.CacheControl
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal object OwnIdSocialNetworkHelper {

    private fun Configuration.baseApiUrl(): HttpUrl = apiUrl.newBuilder().addPathSegment("api").build()

    @Throws
    internal suspend fun startOidcChallenge(
        ownIdCore: OwnIdCoreImpl,
        provider: OwnIdSocialFeature.Provider,
        oauthResponseType: OwnIdSocialFeature.OauthResponseType,
        loginIdHint: String? = null,
        redirectUri: String? = null
    ): OwnIdSocialFeature.Challenge {
        val oidcStartUrl = ownIdCore.configuration.baseApiUrl()
            .newBuilder()
            .addEncodedPathSegments("oidc/idp/start")
            .addEncodedPathSegment(provider.name.lowercase())
            .build()

        val postData = JSONObject().apply {
            put("oauthResponseType", oauthResponseType.name.lowercase())
            loginIdHint?.let { put("loginIdHint", it) }
            redirectUri?.let { put("redirectUri", it) }
        }
            .toString()

        val response = doPostRequest(ownIdCore, oidcStartUrl, postData, null)
        val responseJson = JSONObject(response)

        return OwnIdSocialFeature.Challenge(
            challengeId = responseJson.getString("challengeId"),
            clientId = responseJson.getString("clientId"),
            challengeUrl = responseJson.optString("challengeUrl").ifBlank { null }
        )
    }

    @Throws
    internal suspend fun completeOidcChallengeWithIdToken(
        ownIdCore: OwnIdCoreImpl,
        challengeId: String,
        idToken: String
    ): Pair<String, String?> {
        val completeUrl = ownIdCore.configuration.baseApiUrl()
            .newBuilder()
            .addEncodedPathSegments("oidc/idp/complete")
            .build()

        val postData = JSONObject()
            .put("challengeId", challengeId)
            .put("idToken", idToken)
            .toString()

        val response = doPostRequest(ownIdCore, completeUrl, postData, null)
        val responseJson = JSONObject(response)

        val accessToken = responseJson.getString("accessToken")
        val loginId = responseJson.optJSONObject("loginId")?.optString("id")?.ifBlank { null }

        return accessToken to loginId
    }

    @Throws
    internal suspend fun doLogin(ownIdCore: OwnIdCoreImpl, accessToken: String): Pair<String, String> {
        val loginUrl = ownIdCore.configuration.baseApiUrl()
            .newBuilder()
            .addEncodedPathSegments("login")
            .build()

        val postData = JSONObject().toString()

        val response = doPostRequest(ownIdCore, loginUrl, postData, accessToken)
        val responseJson = JSONObject(response)

        val accessToken = responseJson.getString("accessToken")
        val sessionPayload = responseJson.optString("sessionPayload")

        return accessToken to sessionPayload
    }

    @Throws
    internal suspend fun cancelOidcChallenge(ownIdCore: OwnIdCoreImpl, challengeId: String) {
        val cancelUrl = ownIdCore.configuration.baseApiUrl()
            .newBuilder()
            .addEncodedPathSegments("oidc/idp/cancel")
            .build()

        val postData = JSONObject()
            .put("challengeId", challengeId)
            .toString()

        doPostRequest(ownIdCore, cancelUrl, postData, null)
    }

    @Throws
    private suspend fun doPostRequest(ownIdCore: OwnIdCoreImpl, url: HttpUrl, postData: String, token: String? = null): String {
        OwnIdInternalLogger.logD(this, "doPostRequest", "$url")

        val request: Request = Request.Builder()
            .apply {
                url(url)
                applyAppUrlHeader(ownIdCore.configuration)
                header("User-Agent", ownIdCore.configuration.userAgent)
                header("Accept-Language", ownIdCore.localeService.currentOwnIdLocale.serverLanguageTag)
                token?.let { header("Authorization", "Bearer $it") }
                post(postData.toRequestBody(DEFAULT_MEDIA_TYPE))
                cacheControl(DEFAULT_CACHE_CONTROL)
            }
            .build()

        val response = ownIdCore.okHttpClient.newCall(request).await()

        return if (response.isSuccessful) response.use { it.body!!.string() }
        else throw OwnIdException(
            JSONObject().apply {
                put("code", response.code)
                put("url", url)
                response.use { it.body?.string()?.apply { put("body", this) } }
            }.toString()
        )
    }

    private val DEFAULT_MEDIA_TYPE: MediaType = "application/json".toMediaType()
    private val DEFAULT_CACHE_CONTROL: CacheControl = CacheControl.Builder().noCache().noStore().build()
}