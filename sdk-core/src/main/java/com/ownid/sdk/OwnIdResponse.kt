package com.ownid.sdk

import androidx.annotation.VisibleForTesting
import com.ownid.sdk.exception.OwnIdException
import org.json.JSONException
import org.json.JSONObject
import java.io.Serializable

/**
 * Represent OwnID Register/Login request response.
 *
 * @param context       OwnID flow context.
 * @param loginId       User login id that was used in OwnID flow.
 * @param flowInfo      [OwnIdFlowInfo] in response.
 * @param payload       [OwnIdPayload] in response.
 * @param languageTag   Language tag that was used in OwnID flow (well-formed [IETF BCP 47 language tag](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Accept-Language)).
 */
public class OwnIdResponse @VisibleForTesting @InternalOwnIdAPI constructor(
    @JvmField public val context: String,
    @JvmField public val loginId: String,
    @JvmField public val flowInfo: OwnIdFlowInfo,
    @JvmField public val payload: OwnIdPayload,
    @JvmField public val languageTag: String
) : Serializable {

    @InternalOwnIdAPI
    internal companion object {

        @JvmSynthetic
        @Throws(JSONException::class, OwnIdException::class)
        internal fun fromServerResponse(response: String, languageTag: String): OwnIdResponse {
            val jsonResponse = JSONObject(response)

            val status = jsonResponse.optString("status")
            if ("finished".equals(status, true).not()) throw OwnIdException("Expecting status 'finished' but was '$status'")

            val context = jsonResponse.optString("context")
            val flowInfo = OwnIdFlowInfo.fromJson(jsonResponse.getJSONObject("flowInfo"))
            val payloadJson = jsonResponse.getJSONObject("payload")
            val loginId = payloadJson.optString("loginId")
            val payload = OwnIdPayload.fromJson(payloadJson)

            return OwnIdResponse(context, loginId, flowInfo, payload, languageTag)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as OwnIdResponse
        if (context != other.context) return false
        if (loginId != other.loginId) return false
        if (flowInfo != other.flowInfo) return false
        if (payload != other.payload) return false
        return languageTag == other.languageTag
    }

    override fun hashCode(): Int {
        var result = context.hashCode()
        result = 31 * result + loginId.hashCode()
        result = 31 * result + flowInfo.hashCode()
        result = 31 * result + payload.hashCode()
        result = 31 * result + languageTag.hashCode()
        return result
    }

    override fun toString(): String = "OwnIdResponse(context='$context', loginId=*, $flowInfo, $payload, languageTag='$languageTag')"
}