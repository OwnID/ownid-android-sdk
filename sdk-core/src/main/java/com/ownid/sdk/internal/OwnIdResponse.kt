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
 * @param flowInfo      [OwnIdFlowInfo] in response.
 * @param payload       [OwnIdPayload] in response.
 */
public class OwnIdResponse(
    public val context: String,
    public val loginId: String,
    public val flowInfo: OwnIdFlowInfo,
    public val payload: OwnIdPayload
) {

    @InternalOwnIdAPI
    internal companion object {
        internal const val KEY_RESPONSE_INTENT_DATA = "com.ownid.sdk.intent.KEY_RESPONSE_INTENT_DATA"

        private const val KEY_RESPONSE_CONTEXT = "context"
        private const val KEY_RESPONSE_LOGIN_ID = "loginId"
        private const val KEY_RESPONSE_FLOW_INFO = "flowInfo"
        private const val KEY_RESPONSE_PAYLOAD = "payload"

        private const val KEY_RESPONSE_ERROR = "error"

        @JvmStatic
        @JvmSynthetic
        @Throws(ServerError::class, JSONException::class)
        internal fun fromStatusResponse(expectedContext: String, response: String): OwnIdResponse {
            val jsonResponse = JSONObject(response)

            val context = jsonResponse.optString(KEY_RESPONSE_CONTEXT)
            if (expectedContext != context) throw ServerError("Context does not match in status response")

            val payloadJson = jsonResponse.getJSONObject(KEY_RESPONSE_PAYLOAD)
            if (payloadJson.has(KEY_RESPONSE_ERROR)) throw ServerError(payloadJson.optString(KEY_RESPONSE_ERROR))

            val loginId = payloadJson.optString(KEY_RESPONSE_LOGIN_ID)
            val flowInfo = OwnIdFlowInfo.fromJson(jsonResponse.getJSONObject(KEY_RESPONSE_FLOW_INFO))
            val payload = OwnIdPayload.fromJson(payloadJson)

            return OwnIdResponse(context, loginId, flowInfo, payload)
        }

        @JvmStatic
        @JvmSynthetic
        @Throws(JSONException::class, OwnIdException::class)
        internal fun unwrapFromIntentOrThrow(resultData: Intent?): OwnIdResponse =
            resultData?.getStringExtra(KEY_RESPONSE_INTENT_DATA)?.let { fromJsonString(it) }
                ?: throw OwnIdException("OwnIdResponse is not set")

        @JvmStatic
        @JvmSynthetic
        @Throws(JSONException::class)
        internal fun fromJsonString(jsonSting: String): OwnIdResponse {
            val json = JSONObject(jsonSting)

            val context = json.optString(KEY_RESPONSE_CONTEXT)
            val loginId = json.optString(KEY_RESPONSE_LOGIN_ID)
            val flowInfo = OwnIdFlowInfo.fromJson(json.getJSONObject(KEY_RESPONSE_FLOW_INFO))
            val payload = OwnIdPayload.fromJson(json.getJSONObject(KEY_RESPONSE_PAYLOAD))

            return OwnIdResponse(context, loginId, flowInfo, payload)
        }
    }

    init {
        if (flowInfo.event == OwnIdFlowInfo.Event.Register) {
            if (payload.type != OwnIdPayload.Type.Registration)
                throw ServerError("FlowInfo.Event [${flowInfo.event}] != Payload.Type [${payload.type}]")
        }

        if (flowInfo.event == OwnIdFlowInfo.Event.Login) {
            if (payload.type != OwnIdPayload.Type.Login)
                throw ServerError("FlowInfo.Event [${flowInfo.event}] != Payload.Type [${payload.type}]")
        }
    }

    @JvmSynthetic
    @InternalOwnIdAPI
    internal fun wrapInIntent(): Intent = Intent().putExtra(KEY_RESPONSE_INTENT_DATA, toJsonString())

    @JvmSynthetic
    @InternalOwnIdAPI
    internal fun toJsonString(): String {
        return JSONObject()
            .put(KEY_RESPONSE_CONTEXT, context)
            .put(KEY_RESPONSE_LOGIN_ID, loginId)
            .put(KEY_RESPONSE_FLOW_INFO, flowInfo.asJson())
            .put(KEY_RESPONSE_PAYLOAD, payload.asJson())
            .toString()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as OwnIdResponse
        if (context != other.context) return false
        if (loginId != other.loginId) return false
        if (flowInfo != other.flowInfo) return false
        if (payload != other.payload) return false
        return true
    }

    override fun hashCode(): Int {
        var result = context.hashCode()
        result = 31 * result + loginId.hashCode()
        result = 31 * result + flowInfo.hashCode()
        result = 31 * result + payload.hashCode()
        return result
    }
}