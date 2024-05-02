@file:JvmName("OwnIdUtils")

package com.ownid.sdk.internal

import android.util.Base64
import androidx.annotation.RestrictTo
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.PublicKeyCredential
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnIdCallback
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@JvmSynthetic
@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal fun String.fromBase64UrlSafeNoPadding(): ByteArray = Base64.decode(this, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)

@JvmSynthetic
@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal fun ByteArray.toBase64UrlSafeNoPadding(): String = Base64.encodeToString(this, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)

@JvmSynthetic
@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal fun ByteArray.toSHA256Bytes(): ByteArray = MessageDigest.getInstance("SHA-256").digest(this)

@JvmSynthetic
@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal fun ByteArray.asHexUpper(): String = this.joinToString(separator = "") { String.format("%02X:", (it.toInt() and 0xFF)) }.dropLast(1)

@JvmSynthetic
@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    enqueue(
        object : Callback {
            override fun onResponse(call: Call, response: Response) {
                if (continuation.isActive) continuation.resume(response)
            }

            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
        }
    )

    continuation.invokeOnCancellation { runCatching { cancel() } }
}

@JvmSynthetic
@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal  suspend fun <T> ((OwnIdCallback<T>) -> Unit).await(): T = suspendCoroutine { continuation ->
    invoke(
        object : (Result<T>) -> Unit {
            override fun invoke(result: Result<T>) {
                result.onSuccess { continuation.resume(it) }
                result.onFailure { continuation.resumeWithException(it) }
            }
        }
    )
}

// https://w3c.github.io/webauthn/#dictionary-makecredentialoptions
@JvmSynthetic
@InternalOwnIdAPI
@Throws(JSONException::class)
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal fun createFidoRegisterOptions(context: String, rpId: String, rpName: String, userId: String, userName: String, userDisplayName: String, credIds: List<String>): String =
    JSONObject()
        .put("challenge", context.encodeToByteArray().toBase64UrlSafeNoPadding())
        .put("rp", JSONObject().put("id", rpId).put("name", rpName))
        .put("user", JSONObject().put("id", userId).put("name", userName).put("displayName", userDisplayName))
        .put(
            "pubKeyCredParams", JSONArray()
                .put(JSONObject("""{"type":"public-key","alg":-7}"""))
                .put(JSONObject("""{"type":"public-key","alg":-257}"""))
        )
        .put("timeout", 2 * 60 * 1000)
        .put("attestation", "none")
        .put("excludeCredentials", JSONArray(credIds.map { JSONObject().put("type", "public-key").put("id", it) }))
        .put(
            "authenticatorSelection", JSONObject()
                .put("authenticatorAttachment", "platform")
                .put("userVerification", "required")
                .put("requireResidentKey", false)
                .put("residentKey", "preferred")
        )
        .toString()

// https://w3c.github.io/webauthn/#dictionary-assertion-options
@JvmSynthetic
@InternalOwnIdAPI
@Throws(JSONException::class)
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal fun createFidoLoginOptions(context: String, rpId: String, credIds: List<String>): String = JSONObject()
    .put("challenge", context.encodeToByteArray().toBase64UrlSafeNoPadding())
    .put("timeout", 2 * 60 * 1000)
    .put("rpId", rpId)
    .put("userVerification", "required")
    .put("allowCredentials", JSONArray(credIds.map { JSONObject().put("type", "public-key").put("id", it) }))
    .toString()

@JvmSynthetic
@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal fun CreatePublicKeyCredentialResponse.toJSONObject(): JSONObject {
    val registrationResponse = JSONObject(registrationResponseJson)
    val response = registrationResponse.getJSONObject("response")
    return JSONObject()
        .put("attestationObject", response.getString("attestationObject"))
        .put("clientDataJSON", response.getString("clientDataJSON"))
        .put("credentialId", registrationResponse.getString("id"))
}

@JvmSynthetic
@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal fun PublicKeyCredential.toJSONObject(): JSONObject {
    val loginResponse = JSONObject(authenticationResponseJson)
    val response = loginResponse.getJSONObject("response")
    return JSONObject()
        .put("authenticatorData", response.getString("authenticatorData"))
        .put("clientDataJSON", response.getString("clientDataJSON"))
        .put("signature", response.getString("signature"))
        .put("credentialId", loginResponse.getString("id"))
}