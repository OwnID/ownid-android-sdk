package com.ownid.sdk.internal

import com.ownid.sdk.InternalOwnIdAPI
import org.json.JSONObject

@InternalOwnIdAPI
internal class OwnIdLoginId(internal val value: String) {

    internal companion object {
        internal val EMPTY: OwnIdLoginId = OwnIdLoginId("")
    }

    internal fun isEmpty(): Boolean = value.isBlank()

    internal fun isNotEmpty(): Boolean = value.isNotBlank()

    internal fun createData(enrollmentTimestamp: Long = System.currentTimeMillis()): Data = Data(this, enrollmentTimestamp)

    internal class Data(
        internal val loginId: OwnIdLoginId,
        internal val lastPasskeyEnrollmentTimestamp: Long = 0L
    ) {

        internal companion object {
            internal const val DEFAULT_ENROLLMENT_TIMEOUT: Long = 7 * 24 * 60 * 60 * 1000

            private const val LOGIN_ID_KEY = "loginId"
            private const val ENROLLMENT_KEY = "lastPasskeyEnrollmentTimestamp"

            internal fun fromJsonString(json: String): Data? = runCatching {
                val jsonObject = JSONObject(json)
                Data(
                    loginId = OwnIdLoginId(jsonObject.getString(LOGIN_ID_KEY)),
                    lastPasskeyEnrollmentTimestamp = jsonObject.optLong(ENROLLMENT_KEY)
                )
            }.getOrNull()
        }

        internal fun toJsonString(): String = JSONObject()
            .put(LOGIN_ID_KEY, loginId.value)
            .put(ENROLLMENT_KEY, lastPasskeyEnrollmentTimestamp)
            .toString()

        internal fun passkeyEnrollmentTimeoutPassed(timeout: Long = DEFAULT_ENROLLMENT_TIMEOUT): Boolean =
            System.currentTimeMillis() - lastPasskeyEnrollmentTimestamp > timeout
    }
}