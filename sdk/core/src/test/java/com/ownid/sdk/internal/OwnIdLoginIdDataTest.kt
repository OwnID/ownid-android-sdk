package com.ownid.sdk.internal

import com.google.common.truth.Truth
import com.ownid.sdk.AuthMethod
import com.ownid.sdk.InternalOwnIdAPI
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(InternalOwnIdAPI::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
public class OwnIdLoginIdDataTest {
    @Test
    public fun `fromJsonString with valid JSON with authMethod`() {
        val json = "{\"authMethod\":\"passkey\",\"lastEnrollmentTimestamp\":123456789}"
        val ownIdLoginIdData = OwnIdLoginIdData.fromJsonString(json)
        Truth.assertThat(ownIdLoginIdData).isNotNull()
        Truth.assertThat(ownIdLoginIdData!!.authMethod).isEqualTo(AuthMethod.Passkey)
        Truth.assertThat(ownIdLoginIdData.lastEnrollmentTimestamp).isEqualTo(123456789L)
    }

    @Test
    public fun `fromJsonString with valid JSON without authMethod`() {
        val json = "{\"lastEnrollmentTimestamp\":123456789}"
        val ownIdLoginIdData = OwnIdLoginIdData.fromJsonString(json)
        Truth.assertThat(ownIdLoginIdData).isNotNull()
        Truth.assertThat(ownIdLoginIdData!!.authMethod).isNull()
        Truth.assertThat(ownIdLoginIdData.lastEnrollmentTimestamp).isEqualTo(123456789L)
    }

    @Test
    public fun `fromJsonString with invalid JSON`() {
        val json = "invalid_json"
        val ownIdLoginIdData = OwnIdLoginIdData.fromJsonString(json)
        Truth.assertThat(ownIdLoginIdData).isNull()
    }

    @Test
    public fun `toJsonString with authMethod`() {
        val ownIdLoginIdData =
            OwnIdLoginIdData(authMethod = AuthMethod.Otp, lastEnrollmentTimestamp = 123456789)
        val jsonString = ownIdLoginIdData.toJsonString()
        Truth.assertThat(jsonString)
            .isEqualTo("{\"authMethod\":\"otp\",\"lastEnrollmentTimestamp\":123456789}")
    }

    @Test
    public fun `toJsonString without authMethod`() {
        val ownIdLoginIdData = OwnIdLoginIdData(lastEnrollmentTimestamp = 123456789)
        val jsonString = ownIdLoginIdData.toJsonString()
        Truth.assertThat(jsonString).isEqualTo("{\"lastEnrollmentTimestamp\":123456789}")
    }

    @Test
    public fun `enrollmentTimeoutPassed with timeout not passed`() {
        val currentTime = System.currentTimeMillis()
        val ownIdLoginIdData = OwnIdLoginIdData(lastEnrollmentTimestamp = currentTime - 10000)
        Truth.assertThat(ownIdLoginIdData.enrollmentTimeoutPassed()).isFalse()
    }

    @Test
    public fun `enrollmentTimeoutPassed with timeout passed`() {
        val currentTime = System.currentTimeMillis()
        val ownIdLoginIdData =
            OwnIdLoginIdData(lastEnrollmentTimestamp = currentTime - 8 * 24 * 60 * 60 * 1000)
        Truth.assertThat(ownIdLoginIdData.enrollmentTimeoutPassed()).isTrue()
    }

    @Test
    public fun `fromJsonString and toJsonString are consistent`() {
        val originalData = OwnIdLoginIdData(
            authMethod = AuthMethod.Password,
            lastEnrollmentTimestamp = 1678886400000
        )
        val jsonString = originalData.toJsonString()
        val parsedData = OwnIdLoginIdData.fromJsonString(jsonString)
        Truth.assertThat(parsedData).isEqualTo(originalData)
    }
}