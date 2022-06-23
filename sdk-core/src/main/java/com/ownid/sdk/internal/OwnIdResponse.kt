package com.ownid.sdk.internal

import android.content.Intent
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.exception.ServerError
import org.json.JSONException
import org.json.JSONObject

/**
 * Represent OwnID Register/Login request response.
 *
 * @param context       [OwnIdRequest] context.
 * @param loginId       May contain user login id that was used in OwnID Web App for Registration flow. Will be empty if no login id was set.
 * @param payload       [OwnIdPayload] in response.
 */
public class OwnIdResponse(
    public val context: String,
    public val loginId: String,
    public val payload: OwnIdPayload
) {

    @InternalOwnIdAPI
    internal companion object {
        internal const val KEY_RESPONSE_INTENT_DATA = "com.ownid.sdk.intent.KEY_RESPONSE_INTENT_DATA"

        private const val KEY_RESPONSE_CONTEXT = "context"
        private const val KEY_RESPONSE_LOGIN_ID = "loginId"
        private const val KEY_RESPONSE_PAYLOAD = "payload"

        private const val KEY_RESPONSE_ERROR = "error"

        @JvmStatic
        @Throws(ServerError::class, JSONException::class)
        internal fun fromStatusResponse(expectedContext: String, response: String): OwnIdResponse {
            val jsonResponse = JSONObject(response)

            val context = jsonResponse.optString(KEY_RESPONSE_CONTEXT)
            if (expectedContext != context) throw ServerError("Context does not match in status response")

            val payloadJson = jsonResponse.getJSONObject(KEY_RESPONSE_PAYLOAD)
            if (payloadJson.has(KEY_RESPONSE_ERROR)) throw ServerError(payloadJson.optString(KEY_RESPONSE_ERROR))

            val loginId = payloadJson.optString(KEY_RESPONSE_LOGIN_ID)
            val payload = OwnIdPayload.fromJson(payloadJson)

            return OwnIdResponse(context, loginId, payload)
        }

        @JvmStatic
        @Throws(JSONException::class, OwnIdException::class)
        internal fun unwrapFromIntentOrThrow(resultData: Intent?): OwnIdResponse =
            resultData?.getStringExtra(KEY_RESPONSE_INTENT_DATA)?.let { fromJsonString(it) }
                ?: throw OwnIdException("OwnIdResponse is not set")

        @JvmStatic
        @Throws(JSONException::class)
        internal fun fromJsonString(jsonSting: String): OwnIdResponse {
            val json = JSONObject(jsonSting)

            val context = json.optString(KEY_RESPONSE_CONTEXT)
            val loginId = json.optString(KEY_RESPONSE_LOGIN_ID)
            val payload = OwnIdPayload.fromJson(json.getJSONObject(KEY_RESPONSE_PAYLOAD))

            return OwnIdResponse(context, loginId, payload)
        }
    }

    @InternalOwnIdAPI
    internal fun wrapInIntent(): Intent = Intent().putExtra(KEY_RESPONSE_INTENT_DATA, toJsonString())

    @InternalOwnIdAPI
    internal fun toJsonString(): String {
        return JSONObject()
            .put(KEY_RESPONSE_CONTEXT, context)
            .put(KEY_RESPONSE_LOGIN_ID, loginId)
            .put(KEY_RESPONSE_PAYLOAD, payload.asJson())
            .toString()
    }
}