package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
/** Login ID types supported by the auth API. */
internal enum class LoginIdType {
    @SerialName("Anonymous")
    ANONYMOUS,

    @SerialName("UserName")
    USER_NAME,

    @SerialName("Email")
    EMAIL,

    @SerialName("PhoneNumber")
    PHONE_NUMBER,

    @SerialName("CredentialId")
    CREDENTIAL_ID,

    @SerialName("FaceKeyPersonId")
    FACE_KEY_PERSON_ID
}
