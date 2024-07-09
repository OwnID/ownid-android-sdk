package com.ownid.sdk.internal

import com.google.common.truth.Truth
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
    public fun `test fromJsonString with valid JSON`() {
        val json = "{\"isOwnIdLogin\":true,\"lastEnrollmentTimestamp\":123456789}"
        val data = OwnIdLoginId.Data.fromJsonString(json)
        Truth.assertThat(data).isNotNull()
        Truth.assertThat(data!!.isOwnIdLogin).isTrue()
        Truth.assertThat(data.lastEnrollmentTimestamp).isEqualTo(123456789L)
    }

    @Test
    public fun `test fromJsonString with invalid JSON`() {
        val json = "invalid_json"
        val data = OwnIdLoginId.Data.fromJsonString(json)
        Truth.assertThat(data).isNull()
    }

    @Test
    public fun `test toJsonString`() {
        val data = OwnIdLoginId.Data(true, 123456789)
        val jsonString = data.toJsonString()
        Truth.assertThat(jsonString).isEqualTo("{\"isOwnIdLogin\":true,\"lastEnrollmentTimestamp\":123456789}")
    }

    @Test
    public fun `test passkeyEnrollmentTimeoutPassed with timeout not passed`() {
        val currentTime = System.currentTimeMillis()
        val data = OwnIdLoginId.Data(false, currentTime - 10000)
        Truth.assertThat(data.enrollmentTimeoutPassed()).isFalse()
    }

    @Test
    public fun `test passkeyEnrollmentTimeoutPassed with timeout passed`() {
        val currentTime = System.currentTimeMillis()
        val data = OwnIdLoginId.Data(true, currentTime - 8 * 24 * 60 * 60 * 1000)
        Truth.assertThat(data.enrollmentTimeoutPassed()).isTrue()
    }
}