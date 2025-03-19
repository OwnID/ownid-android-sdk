package com.ownid.sdk.internal

import androidx.annotation.RestrictTo
import com.ownid.sdk.AuthMethod
import com.ownid.sdk.InternalOwnIdAPI
import org.json.JSONObject

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal data class OwnIdLoginIdData(
    internal val authMethod: AuthMethod? = null,
    internal val lastEnrollmentTimestamp: Long = 0L
) {

    internal companion object {
        internal const val DEFAULT_ENROLLMENT_TIMEOUT: Long = 7 * 24 * 60 * 60 * 1000

        private const val AUTH_METHOD_KEY = "authMethod"
        private const val ENROLLMENT_KEY = "lastEnrollmentTimestamp"

        internal fun fromJsonString(json: String): OwnIdLoginIdData? = runCatching {
            val jsonObject = JSONObject(json)
            OwnIdLoginIdData(
                authMethod = jsonObject.optString(AUTH_METHOD_KEY).let { AuthMethod.fromString(it) },
                lastEnrollmentTimestamp = jsonObject.optLong(ENROLLMENT_KEY)
            )
        }.getOrNull()
    }

    internal fun toJsonString(): String = JSONObject()
        .apply { if (authMethod != null) put(AUTH_METHOD_KEY, authMethod.toString()) }
        .put(ENROLLMENT_KEY, lastEnrollmentTimestamp)
        .toString()

    internal fun enrollmentTimeoutPassed(timeout: Long = DEFAULT_ENROLLMENT_TIMEOUT): Boolean =
        System.currentTimeMillis() - lastEnrollmentTimestamp > timeout
}