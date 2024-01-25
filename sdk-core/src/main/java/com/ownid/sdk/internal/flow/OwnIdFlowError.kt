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
    internal val errorCode: String,
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

    @InternalOwnIdAPI
    internal object Code {
        internal val ACCOUNT_NOT_FOUND = "AccountNotFound".uppercase()
        internal val ACCOUNT_IS_BLOCKED = "AccountIsBlocked".uppercase()
        internal val WRONG_CODE = "WrongCodeEntered".uppercase()
        internal val WRONG_CODE_LIMIT_REACHED = "WrongCodeLimitReached".uppercase()
        internal val SEND_CODE_LIMIT_REACHED = "SendCodeLimitReached".uppercase()
        internal val USER_NOT_FOUND = "UserNotFound".uppercase()
        internal val REQUIRES_BIOMETRIC_INPUT = "RequiresBiometricInput".uppercase()
    }

    internal fun toOwnIdUserError(unspecifiedUserMessage: String): OwnIdUserError = when (errorCode.uppercase()) {
        Code.ACCOUNT_NOT_FOUND -> OwnIdUserError(OwnIdUserError.Code.ACCOUNT_NOT_FOUND, userMessage, message ?: "")
        Code.ACCOUNT_IS_BLOCKED -> OwnIdUserError(OwnIdUserError.Code.ACCOUNT_IS_BLOCKED, userMessage, message ?: "")
        Code.WRONG_CODE -> OwnIdUserError(OwnIdUserError.Code.WRONG_CODE, userMessage, message ?: "")
        Code.WRONG_CODE_LIMIT_REACHED -> OwnIdUserError(OwnIdUserError.Code.WRONG_CODE_LIMIT_REACHED, userMessage, message ?: "")
        Code.SEND_CODE_LIMIT_REACHED -> OwnIdUserError(OwnIdUserError.Code.SEND_CODE_LIMIT_REACHED, userMessage, message ?: "")
        Code.USER_NOT_FOUND -> OwnIdUserError(OwnIdUserError.Code.USER_NOT_FOUND, userMessage, message ?: "")
        Code.REQUIRES_BIOMETRIC_INPUT -> OwnIdUserError(OwnIdUserError.Code.REQUIRES_BIOMETRIC_INPUT, userMessage, message ?: "")

        //UserAlreadyExists, FlowIsFinished, ...
        else -> OwnIdUserError(OwnIdUserError.Code.UNSPECIFIED, unspecifiedUserMessage, "[$errorCode] $userMessage ($message)")
    }

    override fun toString(): String = "OwnIdFlowError(errorCode='$errorCode', userMessage='$userMessage', flowFinished='$flowFinished', message='$message')"
}