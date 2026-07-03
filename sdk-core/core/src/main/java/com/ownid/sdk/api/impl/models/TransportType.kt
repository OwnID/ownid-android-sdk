package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
/** Credential transport hints used in passkey credential descriptors. */
internal enum class TransportType {
    @SerialName("usb")
    USB,

    @SerialName("nfc")
    NFC,

    @SerialName("ble")
    BLE,

    @SerialName("smart-card")
    SMART_CARD,

    @SerialName("hybrid")
    HYBRID,

    @SerialName("internal")
    INTERNAL,

    @SerialName("cable")
    CABLE
}
