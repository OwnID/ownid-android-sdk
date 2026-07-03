package com.ownid.sdk.capability.impl

import com.ownid.sdk.capability.Serialization
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

@OptIn(ExperimentalSerializationApi::class)
internal class SerializationImpl : Serialization {

    override val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
//      decodeEnumsCaseInsensitive = true // Need Kotlin 1.9 & serialization 1.6.3
        allowSpecialFloatingPointValues = true
    }
}