package com.ownid.sdk.internal.flow

import androidx.annotation.RestrictTo
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.exception.OwnIdUserError
import org.json.JSONException
import org.json.JSONObject

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class OwnIdFlowError(
    private val errorCode: String,
    internal val userMessage: String,
    internal val flowFinished: Boolean,
    message: String
) : OwnIdException(message) {

    @InternalOwnIdAPI
    internal companion object {
        @Throws(JSONException::class)
        internal fun fromJson(errorJson: JSONObject): OwnIdFlowError = OwnIdFlowError(
            errorJson.getString("errorCode"),
            errorJson.getString("userMessage"),
            errorJson.optBoolean("flowFinished"),
            errorJson.optString("message")
        )
    }

    internal fun toOwnIdUserError(unspecifiedUserMessage: String): OwnIdUserError = when (errorCode) {
        "AccountNotFound" -> OwnIdUserError(OwnIdUserError.Code.ACCOUNT_NOT_FOUND, userMessage, message ?: "")
        "AccountIsBlocked" -> OwnIdUserError(OwnIdUserError.Code.ACCOUNT_IS_BLOCKED, userMessage, message ?: "")
        "WrongCodeEntered" -> OwnIdUserError(OwnIdUserError.Code.WRONG_CODE, userMessage, message ?: "")
        "WrongCodeLimitReached" -> OwnIdUserError(OwnIdUserError.Code.WRONG_CODE_LIMIT_REACHED, userMessage, message ?: "")
        "SendCodeLimitReached" -> OwnIdUserError(OwnIdUserError.Code.SEND_CODE_LIMIT_REACHED, userMessage, message ?: "")
        "UserNotFound" -> OwnIdUserError(OwnIdUserError.Code.USER_NOT_FOUND, userMessage, message ?: "")
        "RequiresBiometricInput" -> OwnIdUserError(OwnIdUserError.Code.REQUIRES_BIOMETRIC_INPUT, userMessage, message ?: "")

        //UserNotFound, UserAlreadyExists, FlowIsFinished, ...
        else -> OwnIdUserError(OwnIdUserError.Code.UNSPECIFIED, unspecifiedUserMessage, "[$errorCode] $userMessage ($message)")
    }

    override fun toString(): String = "OwnIdFlowError(errorCode='$errorCode', userMessage='$userMessage', flowFinished='$flowFinished', message='$message')"
}