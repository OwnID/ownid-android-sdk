package com.ownid.sdk.internal.feature.flow

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
    internal object CodeServer {
        internal const val ACCOUNT_NOT_FOUND = "AccountNotFound"
        internal const val ACCOUNT_IS_BLOCKED = "AccountIsBlocked"
        internal const val WRONG_CODE = "WrongCodeEntered"
        internal const val WRONG_CODE_LIMIT_REACHED = "WrongCodeLimitReached"
        internal const val SEND_CODE_LIMIT_REACHED = "SendCodeLimitReached"
        internal const val USER_NOT_FOUND = "UserNotFound"
        internal const val REQUIRES_BIOMETRIC_INPUT = "RequiresBiometricInput"
        internal const val USER_ALREADY_EXISTS = "UserAlreadyExists"
        internal const val FLOW_IS_FINISHED = "FlowIsFinished"

    }

    @InternalOwnIdAPI
    internal object CodeLocal { // Not a part of server errors
        internal const val UNSPECIFIED = "Unspecified"
        internal const val INVALID_LOGIN_ID = "InvalidLoginId"
        internal const val FLOW_CANCELED = "FlowCanceled"
    }

    internal fun toOwnIdUserError(unspecifiedUserMessage: String): OwnIdUserError = when {
        errorCode.equals(CodeServer.ACCOUNT_NOT_FOUND, true) -> OwnIdUserError(CodeServer.ACCOUNT_NOT_FOUND, userMessage, message ?: "")
        errorCode.equals(CodeServer.ACCOUNT_IS_BLOCKED, true) -> OwnIdUserError(CodeServer.ACCOUNT_IS_BLOCKED, userMessage, message ?: "")
        errorCode.equals(CodeServer.WRONG_CODE, true) -> OwnIdUserError(CodeServer.WRONG_CODE, userMessage, message ?: "")
        errorCode.equals(CodeServer.WRONG_CODE_LIMIT_REACHED, true) -> OwnIdUserError(CodeServer.WRONG_CODE_LIMIT_REACHED, userMessage, message ?: "")
        errorCode.equals(CodeServer.SEND_CODE_LIMIT_REACHED, true) -> OwnIdUserError(CodeServer.SEND_CODE_LIMIT_REACHED, userMessage, message ?: "")
        errorCode.equals(CodeServer.USER_NOT_FOUND, true) -> OwnIdUserError(CodeServer.USER_NOT_FOUND, userMessage, message ?: "")
        errorCode.equals(CodeServer.REQUIRES_BIOMETRIC_INPUT, true) -> OwnIdUserError(CodeServer.REQUIRES_BIOMETRIC_INPUT, userMessage, message ?: "")
        errorCode.equals(CodeServer.USER_ALREADY_EXISTS, true) -> OwnIdUserError(CodeServer.USER_ALREADY_EXISTS, userMessage, message ?: "")
        errorCode.equals(CodeServer.FLOW_IS_FINISHED, true) -> OwnIdUserError(CodeServer.FLOW_IS_FINISHED, userMessage, message ?: "")

        else -> OwnIdUserError(CodeLocal.UNSPECIFIED, unspecifiedUserMessage, "[$errorCode] $userMessage ($message)")
    }

    override fun toString(): String = "OwnIdFlowError(errorCode='$errorCode', userMessage='$userMessage', flowFinished='$flowFinished', message='$message')"
}