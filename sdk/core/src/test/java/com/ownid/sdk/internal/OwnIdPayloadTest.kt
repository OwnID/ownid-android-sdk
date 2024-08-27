package com.ownid.sdk.internal

import com.google.common.truth.Truth
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnIdPayload
import com.ownid.sdk.exception.OwnIdException
import org.json.JSONObject
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(InternalOwnIdAPI::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
public class OwnIdPayloadTest {

    @Test
    public fun fromJsonRegisterStringCorrect() {
        val payload = OwnIdPayload.fromJson(
            JSONObject(
                """{ "metadata": { "collectionName": "ownid", "userIdKey": "userId" },
                "loginId": "hdhdh@jdhhd.fff",
                "type": "registrationInfo",
                "data": { "fido2SignatureCounter": "0", "authType": "Fido2", "source": "Register" } }
        """.trimIndent()
            )
        )

        Truth.assertThat(payload.type).isEqualTo(OwnIdPayload.Type.Registration)
        Truth.assertThat(payload.data)
            .isEqualTo("""{"fido2SignatureCounter":"0","authType":"Fido2","source":"Register"}""")
        Truth.assertThat(payload.metadata)
            .isEqualTo("""{"collectionName":"ownid","userIdKey":"userId"}""")
    }

    @Test
    public fun fromJsonLoginStringCorrect() {
        val payload =
            OwnIdPayload.fromJson(JSONObject("""{ "type": "session", "data": { "idToken": "eyJhbGciOiJS" } }""".trimIndent()))

        Truth.assertThat(payload.type).isEqualTo(OwnIdPayload.Type.Login)
        Truth.assertThat(payload.data).isEqualTo("""{"idToken":"eyJhbGciOiJS"}""")
        Truth.assertThat(payload.metadata).isEqualTo("")
    }

    @Test
    public fun fromJsonUnknownStringCorrect() {
        val exception = Assert.assertThrows(OwnIdException::class.java) {
            OwnIdPayload.fromJson(
                JSONObject("""{ "type": "other", "data": { "idToken": "eyJhbmQ_c5jlEsuC4cLQ" }, "metadata": { "userIdKey": "userId" } }""".trimIndent())
            )
        }
        Truth.assertThat(exception).hasMessageThat()
            .isEqualTo("Unexpected payload type: 'other'")
    }

    @Test
    public fun fromJsonStringUnknown() {
        val exception = Assert.assertThrows(OwnIdException::class.java) {
             OwnIdPayload.fromJson(JSONObject("{\"jwtcxv\":\"ioahfl\"}"))
        }
        Truth.assertThat(exception).hasMessageThat()
            .isEqualTo("Unexpected payload type: ''")
    }

    @Test
    public fun fromJsonStringWithError() {
        val exception = Assert.assertThrows(OwnIdException::class.java) {
            OwnIdPayload.fromJson(JSONObject("{\"error\": \"Code verification attempts limit reached\", \"hideError\": false, \"isSuccess\": false}"))
        }
        Truth.assertThat(exception).hasMessageThat()
            .isEqualTo("Code verification attempts limit reached")
    }
}