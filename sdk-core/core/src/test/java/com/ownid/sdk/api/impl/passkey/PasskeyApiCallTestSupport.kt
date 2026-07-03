@file:Suppress("unused")

package com.ownid.sdk.api.impl.passkey

import com.ownid.sdk.capability.impl.SerializationImpl
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

internal val passkeyTestSerialization = SerializationImpl()

@OptIn(ExperimentalEncodingApi::class)
internal fun paddedBase64UrlFixture(value: String): String =
    Base64.UrlSafe.encode(value.encodeToByteArray())
