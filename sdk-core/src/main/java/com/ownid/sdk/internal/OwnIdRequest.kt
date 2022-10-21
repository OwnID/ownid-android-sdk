package com.ownid.sdk.internal

import android.net.Uri
import android.util.Base64
import com.ownid.sdk.InstanceName
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnId
import com.ownid.sdk.OwnIdCallback
import com.ownid.sdk.OwnIdCore
import com.ownid.sdk.internal.OwnIdRequest.Type
import org.json.JSONException
import org.json.JSONObject
import java.security.MessageDigest
import kotlin.random.Random

/**
 * Represent OwnID Register/Login request.
 *
 * @param ownIdCore         instance of [OwnIdCore].
 * @param type              request type, see [Type].
 * @param languageTags      request language TAGs list (well-formed IETF BCP 47 language tag).
 * @param email             (optional) account email.
 * @param sessionVerifier   request session verifier.
 *
 * @param url               Url string used to launch Custom Tab (or external Browser).
 * @param context           request context string.
 * @param nonce             request nonce string.
 * @param expiration        request expiration time in milliseconds from Unix zero.
 */
@InternalOwnIdAPI
internal class OwnIdRequest internal constructor(
    @get:JvmSynthetic internal val ownIdCore: OwnIdCore,

    private val type: Type,
    private val languageTags: String,
    private val email: String,
    private val sessionVerifier: String = Random.nextBytes(32).toBase64UrlSafeNoPadding,

    private val url: String = "",
    @get:JvmSynthetic internal val context: String = "",
    private val nonce: String = "",
    private val expiration: Long = 0L,
) {
    internal enum class Type { REGISTER, LOGIN }

    internal companion object {
        private const val DEFAULT_REDIRECT_URI_PARAMETER = "redirectURI"

        private const val KEY_OWNID_NAME = "name"

        private const val KEY_REQUEST_TYPE = "type"
        private const val KEY_REQUEST_LANGUAGE = "language"
        private const val KEY_REQUEST_EMAIL = "email"
        private const val KEY_REQUEST_SESSION_VERIFIER = "sessionVerifier"

        private const val KEY_REQUEST_URL = "url"
        private const val KEY_REQUEST_CONTEXT = "context"
        private const val KEY_REQUEST_NONCE = "nonce"
        private const val KEY_REQUEST_EXPIRATION = "expiration"
        internal const val KEY_REQUEST_INSTANCE_NAME = "instanceName"

        private const val KEY_REQUEST_SESSION_CHALLENGE = "sessionChallenge"

        @JvmSynthetic
        @Throws(JSONException::class, IllegalArgumentException::class)
        internal fun fromJsonString(jsonString: String): OwnIdRequest {
            val json = JSONObject(jsonString)

            val ownIdCore = OwnId.getInstanceOrThrow<OwnIdCore>(InstanceName(json.optString(KEY_OWNID_NAME)))

            val type = Type.valueOf(json.optString(KEY_REQUEST_TYPE))
            val language = json.optString(KEY_REQUEST_LANGUAGE)
            val email = json.optString(KEY_REQUEST_EMAIL)
            val sessionVerifier = json.optString(KEY_REQUEST_SESSION_VERIFIER)
            val url = json.optString(KEY_REQUEST_URL)
            val context = json.optString(KEY_REQUEST_CONTEXT)
            val nonce = json.optString(KEY_REQUEST_NONCE)
            val expiration = json.optLong(KEY_REQUEST_EXPIRATION)

            return OwnIdRequest(ownIdCore, type, language, email, sessionVerifier, url, context, nonce, expiration)
        }

        @get:JvmSynthetic
        internal val ByteArray.toBase64UrlSafeNoPadding
            inline get() : String = Base64.encodeToString(this, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
    }

    @JvmSynthetic
    @Throws(JSONException::class)
    internal fun toJsonString(): String {
        return JSONObject()
            .put(KEY_OWNID_NAME, ownIdCore.instanceName.value)
            .put(KEY_REQUEST_TYPE, type.name)
            .put(KEY_REQUEST_LANGUAGE, languageTags)
            .put(KEY_REQUEST_EMAIL, email)
            .put(KEY_REQUEST_SESSION_VERIFIER, sessionVerifier)
            .put(KEY_REQUEST_URL, url)
            .put(KEY_REQUEST_CONTEXT, context)
            .put(KEY_REQUEST_NONCE, nonce)
            .put(KEY_REQUEST_EXPIRATION, expiration)
            .toString()
    }

    @JvmSynthetic
    internal fun isRequestStarted(): Boolean {
        return context.isNotBlank() && nonce.isNotBlank()
    }

    @JvmSynthetic
    internal fun getUriForBrowser(): Uri {
        val redirectUri = ownIdCore.configuration.redirectionUri.buildUpon()
            .appendQueryParameter(KEY_REQUEST_CONTEXT, context)
            .appendQueryParameter(KEY_REQUEST_INSTANCE_NAME, ownIdCore.instanceName.value)
            .build()
            .toString()

        return Uri.parse(url).buildUpon()
            .apply { if (email.isNotBlank()) appendQueryParameter("e", email) }
            .appendQueryParameter(DEFAULT_REDIRECT_URI_PARAMETER, redirectUri)
            .build()
    }

    @JvmSynthetic
    @Throws(UnsupportedOperationException::class)
    internal fun isRedirectionValid(redirectUri: Uri): Boolean {
        return redirectUri.toString()
            .startsWith(ownIdCore.configuration.redirectionUri.toString(), ignoreCase = true) &&
                context == redirectUri.getQueryParameter(KEY_REQUEST_CONTEXT)
    }

    @JvmSynthetic
    internal fun isRequestActive(): Boolean {
        return System.currentTimeMillis() < expiration
    }

    @JvmSynthetic
    internal fun initRequest(callback: OwnIdCallback<OwnIdRequest>) {
        val postJsonData = runCatching {
            val sessionChallenge = sessionVerifier.fromBase64UrlSafeNoPadding.toSHA256Bytes.toBase64UrlSafeNoPadding
            JSONObject()
                .put(KEY_REQUEST_TYPE, type.name.lowercase())
//              .put("partial", true) // no name/email data
                .put(KEY_REQUEST_SESSION_CHALLENGE, sessionChallenge)
                .toString()
        }.getOrElse { callback(Result.failure(it)); return }

        NetworkHelper.getInstance()
            .doPostJsonRequest(ownIdCore, languageTags, ownIdCore.configuration.ownIdUrl, postJsonData) {
                mapCatching {
                    val postResultJson = JSONObject(it)

                    val url = postResultJson.optString(KEY_REQUEST_URL)
                    val context = postResultJson.optString(KEY_REQUEST_CONTEXT)
                    val nonce = postResultJson.optString(KEY_REQUEST_NONCE)
                    val timeout = postResultJson.optLong(KEY_REQUEST_EXPIRATION, 600_000L)
                    val expiration = System.currentTimeMillis() + timeout

                    require(url.isNotBlank()) { "Url cannot be empty" }
                    require(context.isNotBlank()) { "Context cannot be empty" }
                    require(nonce.isNotBlank()) { "Nonce cannot be empty" }
                    require(timeout > 0) { "Expiration cannot be <= 0" }

                    OwnIdRequest(ownIdCore, type, languageTags, email, sessionVerifier, url, context, nonce, expiration)
                }
                    .onSuccess { callback(Result.success(it)) }
                    .onFailure { callback(Result.failure(it)) }
            }
    }

    @JvmSynthetic
    internal fun getRequestStatus(callback: OwnIdCallback<OwnIdResponse>) {
        val postJsonData = runCatching {
            JSONObject()
                .put(KEY_REQUEST_CONTEXT, context)
                .put(KEY_REQUEST_NONCE, nonce)
                .put(KEY_REQUEST_SESSION_VERIFIER, sessionVerifier)
                .toString()
        }.getOrElse { callback(Result.failure(it)); return }

        NetworkHelper.getInstance()
            .doPostJsonRequest(ownIdCore, languageTags, ownIdCore.configuration.ownIdStatusUrl, postJsonData) {
                mapCatching { OwnIdResponse.fromStatusResponse(context, it) }
                    .onSuccess { callback(Result.success(it)) }
                    .onFailure { callback(Result.failure(it)) }
            }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as OwnIdRequest

        if (type != other.type) return false
        if (languageTags != other.languageTags) return false
        if (email != other.email) return false
        if (sessionVerifier != other.sessionVerifier) return false
        if (url != other.url) return false
        if (context != other.context) return false
        if (nonce != other.nonce) return false
        if (expiration != other.expiration) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + languageTags.hashCode()
        result = 31 * result + email.hashCode()
        result = 31 * result + sessionVerifier.hashCode()
        result = 31 * result + url.hashCode()
        result = 31 * result + context.hashCode()
        result = 31 * result + nonce.hashCode()
        result = 31 * result + expiration.hashCode()
        return result
    }

    private val ByteArray.toSHA256Bytes
        inline get() : ByteArray = MessageDigest.getInstance("SHA-256").digest(this)

    private val String.fromBase64UrlSafeNoPadding
        inline get() : ByteArray = Base64.decode(this, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
}