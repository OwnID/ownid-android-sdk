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
        val json = "{\"loginId\":\"test_login_id\",\"lastPasskeyEnrollmentTimestamp\":123456789}"
        val data = OwnIdLoginId.Data.fromJsonString(json)
        Truth.assertThat(data).isNotNull()
        Truth.assertThat("test_login_id").isEqualTo(data!!.loginId.value)
        Truth.assertThat(123456789L).isEqualTo(data.lastPasskeyEnrollmentTimestamp)
    }

    @Test
    public fun `test fromJsonString with invalid JSON`() {
        val json = "invalid_json"
        val data = OwnIdLoginId.Data.fromJsonString(json)
        Truth.assertThat(data).isNull()
    }

    @Test
    public fun `test toJsonString`() {
        val loginId = OwnIdLoginId("test_login_id")
        val data = OwnIdLoginId.Data(loginId, 123456789)
        val jsonString = data.toJsonString()
        Truth.assertThat("{\"loginId\":\"test_login_id\",\"lastPasskeyEnrollmentTimestamp\":123456789}").isEqualTo(jsonString)
    }

    @Test
    public fun `test passkeyEnrollmentTimeoutPassed with timeout not passed`() {
        val currentTime = System.currentTimeMillis()
        val data = OwnIdLoginId.Data(OwnIdLoginId("test_login_id"), currentTime - 10000)
        Truth.assertThat(data.passkeyEnrollmentTimeoutPassed()).isFalse()
    }

    @Test
    public fun `test passkeyEnrollmentTimeoutPassed with timeout passed`() {
        val currentTime = System.currentTimeMillis()
        val data = OwnIdLoginId.Data(OwnIdLoginId("test_login_id"), currentTime - 8 * 24 * 60 * 60 * 1000)
        Truth.assertThat(data.passkeyEnrollmentTimeoutPassed()).isTrue()
    }
}