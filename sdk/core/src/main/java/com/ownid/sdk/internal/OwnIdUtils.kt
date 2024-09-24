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
import kotlin.random.Random

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
internal suspend fun <T> ((OwnIdCallback<T>) -> Unit).await(): T = suspendCoroutine { continuation ->
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

@JvmSynthetic
@InternalOwnIdAPI
@Throws(JSONException::class)
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal fun adjustEnrollmentOptions(options: String): String {
    val fidoOptions = JSONObject(options)

    val challenge = fidoOptions.getString("challenge").encodeToByteArray().toBase64UrlSafeNoPadding()
    fidoOptions.put("challenge", challenge)

    val userObject = fidoOptions.getJSONObject("user")
    if (userObject.has("id").not()) {
        userObject.put("id", Random.nextBytes(32).toBase64UrlSafeNoPadding())
        fidoOptions.put("user", userObject)
    }

    if (fidoOptions.has("timeout").not()) {
        fidoOptions.put("timeout", 2 * 60 * 1000)
    }

    if (fidoOptions.has("attestation").not()) {
        fidoOptions.put("attestation", "none")
    }

    val authenticatorSelection = fidoOptions.getJSONObject("authenticatorSelection")

    if (authenticatorSelection.has("authenticatorAttachment").not()) {
        authenticatorSelection.put("authenticatorAttachment", "platform")
    }
    if (authenticatorSelection.has("userVerification").not()) {
        authenticatorSelection.put("userVerification", "required")
    }
    if (authenticatorSelection.has("requireResidentKey").not()) {
        authenticatorSelection.put("requireResidentKey", false)
    }
    if (authenticatorSelection.has("residentKey").not()) {
        authenticatorSelection.put("residentKey", "preferred")
    }

    fidoOptions.put("authenticatorSelection", authenticatorSelection)

    return fidoOptions.toString()
}

@JvmSynthetic
@InternalOwnIdAPI
@Throws(JSONException::class)
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal fun enrollmentOptionsHasCredential(options: String): Boolean =
    (JSONObject(options).optJSONArray("excludeCredentials")?.length() ?: 0) > 0

@JvmSynthetic
@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public fun JSONObject.toMap(): Map<String, Any> = buildMap {
    keys().forEach { key ->
        put(
            key, when (val value = this@toMap[key]) {
                is JSONObject -> value.toMap()
                is JSONArray -> value.toList()
                else -> value
            }
        )
    }
}

@JvmSynthetic
@InternalOwnIdAPI
private fun JSONArray.toList(): List<Any> = (0 until length()).map { i ->
    when (val value = this[i]) {
        is JSONObject -> value.toMap()
        is JSONArray -> value.toList()
        else -> value
    }
}