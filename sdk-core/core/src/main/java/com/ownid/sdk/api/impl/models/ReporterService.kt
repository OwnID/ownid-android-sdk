package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
/** Reporter service identifiers used in analytics events. */
internal enum class ReporterService {
    @SerialName("web-sdk")
    WEB_SDK,

    @SerialName("android-sdk")
    ANDROID_SDK,

    @SerialName("ios-sdk")
    IOS_SDK
}
