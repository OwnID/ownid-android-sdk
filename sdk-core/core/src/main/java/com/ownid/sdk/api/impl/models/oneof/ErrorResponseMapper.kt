package com.ownid.sdk.api.impl.models.oneof

import com.ownid.sdk.capability.Serialization
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.ownid.sdk.api.impl.models.ErrorCode as ApiErrorCode

internal class ErrorResponseCase<T>(
    val errorCode: ApiErrorCode,
    val map: Serialization.(String) -> T
)

internal fun <Response, T> case(
    errorCode: ApiErrorCode,
    deserializer: DeserializationStrategy<Response>,
    map: (Response) -> T
): ErrorResponseCase<T> = ErrorResponseCase(errorCode) { body ->
    map(decodeFromString(deserializer, body))
}

internal fun <T> Serialization.mapErrorResponse(
    body: String,
    expected: String,
    vararg cases: ErrorResponseCase<T>
): T {
    val errorCode = parseToJsonElement(body).jsonObject["errorCode"] ?: error("Missing errorCode")
    val errorCodeValue = errorCode.jsonPrimitive.content
    val case = cases.firstOrNull { it.errorCode.value == errorCodeValue } ?: error("Unexpected errorCode=$errorCodeValue for $expected")
    return case.map(this, body)
}
