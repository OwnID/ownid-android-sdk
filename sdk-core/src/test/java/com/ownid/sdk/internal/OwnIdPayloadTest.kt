package com.ownid.sdk.internal

import com.google.common.truth.Truth
import com.ownid.sdk.InternalOwnIdAPI
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(InternalOwnIdAPI::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
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
        Truth.assertThat(payload.ownIdData)
            .isEqualTo("""{"fido2SignatureCounter":"0","authType":"Fido2","source":"Register"}""")
        Truth.assertThat(payload.metadata)
            .isEqualTo("""{"collectionName":"ownid","userIdKey":"userId"}""")
    }

    @Test
    public fun fromJsonLoginStringCorrect() {
        val payload =
            OwnIdPayload.fromJson(JSONObject("""{ "type": "session", "data": { "idToken": "eyJhbGciOiJS" } }""".trimIndent()))

        Truth.assertThat(payload.type).isEqualTo(OwnIdPayload.Type.Login)
        Truth.assertThat(payload.ownIdData).isEqualTo("""{"idToken":"eyJhbGciOiJS"}""")
        Truth.assertThat(payload.metadata).isEqualTo("")
    }

    @Test
    public fun fromJsonUnknownStringCorrect() {
        val payload = OwnIdPayload.fromJson(
            JSONObject("""{ "type": "other", "data": { "idToken": "eyJhbmQ_c5jlEsuC4cLQ" }, "metadata": { "userIdKey": "userId" } }""".trimIndent())
        )

        Truth.assertThat(payload.type).isEqualTo(OwnIdPayload.Type.Unknown)
        Truth.assertThat(payload.ownIdData)
            .isEqualTo("""{"idToken":"eyJhbmQ_c5jlEsuC4cLQ"}""")
        Truth.assertThat(payload.metadata)
            .isEqualTo("""{"userIdKey":"userId"}""")
    }

    @Test
    public fun fromJsonStringUnknown() {
        val payload =   OwnIdPayload.fromJson(JSONObject("{\"jwtcxv\":\"ioahfl\"}"))
        Truth.assertThat(payload.type).isEqualTo(OwnIdPayload.Type.Unknown)
        Truth.assertThat(payload.ownIdData).isEqualTo("")
        Truth.assertThat(payload.metadata).isEqualTo("")
    }


    @Test
    public fun asJson() {
        val payload = OwnIdPayload.fromJson(
            JSONObject(
                """{
                "metadata": {
                  "collectionName": "ownid",
                  "userIdKey": "userId"
                },
                "loginId": "hdhdh@jdhhd.fff",
                "type": "registrationInfo",
                "data": { "authType": "Fido2", "source": "Register" }
            }
        """.trimIndent()
            )
        )

        Truth.assertThat(payload.asJson().toString())
            .isEqualTo("""{"type":"registrationInfo","data":"{\"authType\":\"Fido2\",\"source\":\"Register\"}","metadata":"{\"collectionName\":\"ownid\",\"userIdKey\":\"userId\"}"}""")
    }

}