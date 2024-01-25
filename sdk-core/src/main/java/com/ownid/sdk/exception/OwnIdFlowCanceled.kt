package com.ownid.sdk.exception

import com.ownid.sdk.InternalOwnIdAPI

/**
 * Special exception that occurs when user cancelled OwnID flow.
 * @param step     OwnID flow step that was canceled.
 */
@OptIn(InternalOwnIdAPI::class)
public class OwnIdFlowCanceled @InternalOwnIdAPI constructor(public val step: String) :
    OwnIdException("User canceled OwnID flow ($step).") {

    public companion object {
        public const val ID_COLLECT: String = "ID_COLLECT"
        public const val FIDO_REGISTER: String = "FIDO_REGISTER"
        public const val FIDO_LOGIN: String = "FIDO_LOGIN"
        public const val OTP: String = "OTP"
        public const val WEB_APP: String = "WEB_APP"
        public const val RESULT_PENDING: String = "RESULT_PENDING"
    }
}