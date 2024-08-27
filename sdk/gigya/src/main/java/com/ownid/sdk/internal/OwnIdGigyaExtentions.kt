package com.ownid.sdk.internal

import com.gigya.android.sdk.api.GigyaApiResponse
import com.gigya.android.sdk.network.GigyaError
import com.gigya.android.sdk.session.SessionInfo
import com.ownid.sdk.InternalOwnIdAPI
import org.json.JSONException
import org.json.JSONObject

/**
 * Converts a JSONObject to a [SessionInfo] object.
 *
 * @return A [SessionInfo] object if the string is valid JSON and contains the required `SessionInfo` fields, or `null` otherwise.
 *
 * @throws JSONException if the input string is not valid JSON or if an error occurs during parsing.
 */
@InternalOwnIdAPI
@Throws(JSONException::class)
internal fun JSONObject.toGigyaSession(): SessionInfo? =
     optJSONObject("sessionInfo")?.run {
        val secret = getString("sessionSecret")
        val token = getString("sessionToken")
        val expirationSeconds = optLong("expires_in").takeIf { it > 0L } ?: optLong("expirationTime").takeIf { it > 0L } ?: 0L
        SessionInfo(secret, token, expirationSeconds)
    }

/**
 * Converts a JSONObject to a [GigyaError] object.
 *
 * @return A [GigyaError] object if the JSON string contains an "errorJson" field, or `null` otherwise.
 *
 * @throws JSONException if the input string is not valid JSON or if an error occurs during parsing.
 */
@InternalOwnIdAPI
@Throws(JSONException::class)
internal fun JSONObject.toGigyaError(): GigyaError? =
     optString("errorJson").ifEmpty { null }?.let { value ->
        GigyaError.fromResponse(GigyaApiResponse(value))
    }