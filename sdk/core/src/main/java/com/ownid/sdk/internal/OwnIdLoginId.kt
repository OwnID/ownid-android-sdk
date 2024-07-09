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

    internal data class Data(
        internal val isOwnIdLogin: Boolean = false,
        internal val lastEnrollmentTimestamp: Long = 0L
    ) {

        internal companion object {
            internal const val DEFAULT_ENROLLMENT_TIMEOUT: Long = 7 * 24 * 60 * 60 * 1000

            private const val IS_OWNID_LOGIN_KEY = "isOwnIdLogin"
            private const val ENROLLMENT_KEY = "lastEnrollmentTimestamp"

            internal fun fromJsonString(json: String): Data? = runCatching {
                val jsonObject = JSONObject(json)
                Data(
                    isOwnIdLogin = jsonObject.optBoolean(IS_OWNID_LOGIN_KEY),
                    lastEnrollmentTimestamp = jsonObject.optLong(ENROLLMENT_KEY)
                )
            }.getOrNull()
        }

        internal fun toJsonString(): String = JSONObject()
            .put(IS_OWNID_LOGIN_KEY, isOwnIdLogin)
            .put(ENROLLMENT_KEY, lastEnrollmentTimestamp)
            .toString()

        internal fun enrollmentTimeoutPassed(timeout: Long = DEFAULT_ENROLLMENT_TIMEOUT): Boolean =
            System.currentTimeMillis() - lastEnrollmentTimestamp > timeout
    }
}