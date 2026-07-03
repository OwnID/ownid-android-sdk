package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
/** Operation types returned by authentication requirements and analytics flows. */
internal enum class OperationType {
    @SerialName("LoginIdCollect")
    LOGIN_ID_COLLECT,

    @SerialName("EmailVerification")
    EMAIL_VERIFICATION,

    @SerialName("EmailEnrollment")
    EMAIL_ENROLLMENT,

    @SerialName("PhoneNumberVerification")
    PHONE_NUMBER_VERIFICATION,

    @SerialName("PhoneNumberEnrollment")
    PHONE_NUMBER_ENROLLMENT,

    @SerialName("PasskeyCreation")
    PASSKEY_CREATION,

    @SerialName("PasskeyAuth")
    PASSKEY_AUTH,

    @SerialName("PasskeyEnrollment")
    PASSKEY_ENROLLMENT,

    @SerialName("SessionCreation")
    SESSION_CREATION,

    @SerialName("DeferredAuthentication")
    DEFERRED_AUTHENTICATION,

    @SerialName("ExternalAuthentication")
    EXTERNAL_AUTHENTICATION,

    @SerialName("ProfileCollection")
    PROFILE_COLLECTION,

    @SerialName("PasswordAuthentication")
    PASSWORD_AUTHENTICATION,

    @SerialName("OidcAuthenticationApple")
    OIDC_AUTHENTICATION_APPLE,

    @SerialName("OidcAuthenticationGoogle")
    OIDC_AUTHENTICATION_GOOGLE,

    @SerialName("Registration")
    REGISTRATION,

    @SerialName("ProfileUpdate")
    PROFILE_UPDATE,

    @SerialName("SessionManagement")
    SESSION_MANAGEMENT,

    @SerialName("WebBridge")
    WEB_BRIDGE,

    @SerialName("FaceKeyVerification")
    FACE_KEY_VERIFICATION,

    @SerialName("FaceKeyCreation")
    FACE_KEY_CREATION,

    @SerialName("FaceKeyEnrollment")
    FACE_KEY_ENROLLMENT
}
