package com.ownid.sdk.internal.feature.enrollment

import androidx.annotation.RestrictTo
import com.ownid.sdk.Configuration
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnIdCoreImpl
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.internal.await
import com.ownid.sdk.internal.component.OwnIdInternalLogger
import okhttp3.CacheControl
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal object OwnIdEnrollmentNetworkHelper {

    private fun Configuration.getEnrollmentOptionsUrl(): HttpUrl =
        "https://$appId.server.${env}ownid.com/ownid/attestation/options".toHttpUrl()

    private fun Configuration.getEnrollmentResultUrl(): HttpUrl =
        "https://$appId.server.${env}ownid.com/ownid/attestation/result".toHttpUrl()

    @Throws
    internal suspend fun getEnrollmentOptions(ownIdCore: OwnIdCoreImpl, username: String, displayName: String): String {
        val optionsUrl = ownIdCore.configuration.getEnrollmentOptionsUrl()

        val postData = JSONObject()
            .put("username", username)
            .put("displayName", displayName)
            .toString()

        return doPostRequest(ownIdCore, optionsUrl, postData, null)
    }

    @Throws
    internal suspend fun sendEnrollmentResult(ownIdCore: OwnIdCoreImpl, token: String, fidoCreateJson: String): String {
        val resultUrl = ownIdCore.configuration.getEnrollmentResultUrl()

        val registrationResponse = JSONObject(fidoCreateJson)
        val response = registrationResponse.getJSONObject("response")
        val postData = JSONObject()
            .put("id", registrationResponse.getString("id"))
            .put("type", registrationResponse.getString("type"))
            .put("response", JSONObject().apply {
                put("clientDataJSON", response.getString("clientDataJSON"))
                put("attestationObject", response.getString("attestationObject"))
            })
            .toString()

        return doPostRequest(ownIdCore, resultUrl, postData, token)
    }

    @Throws
    private suspend fun doPostRequest(ownIdCore: OwnIdCoreImpl, url: HttpUrl, postData: String, token: String? = null): String {
        OwnIdInternalLogger.logD(this, "doPostRequest", "$url")

        val request: Request = Request.Builder()
            .apply {
                url(url)
                header("User-Agent", ownIdCore.configuration.userAgent)
                header("Accept-Language", ownIdCore.localeService.currentOwnIdLocale.serverLanguageTag)
                token?.let { header("Authorization", "Bearer $it") }
                post(postData.toRequestBody(DEFAULT_MEDIA_TYPE))
                cacheControl(DEFAULT_CACHE_CONTROL)
            }
            .build()

        val response = ownIdCore.okHttpClient.newCall(request).await()

        return if (response.isSuccessful) response.use { it.body!!.string() }
        else throw OwnIdException("Server response: ${response.code} ${response.message} ($url)")
    }

    private val DEFAULT_MEDIA_TYPE: MediaType = "application/json".toMediaType()
    private val DEFAULT_CACHE_CONTROL: CacheControl = CacheControl.Builder().noCache().noStore().build()
}