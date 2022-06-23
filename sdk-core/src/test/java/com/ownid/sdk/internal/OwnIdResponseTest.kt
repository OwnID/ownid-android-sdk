package com.ownid.sdk.internal

import android.content.Intent
import androidx.test.ext.truth.os.BundleSubject
import com.google.common.truth.Truth
import com.ownid.sdk.Configuration
import com.ownid.sdk.InstanceName
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnId
import com.ownid.sdk.OwnIdCallback
import com.ownid.sdk.RegistrationParameters
import com.ownid.sdk.TestDataCore
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.exception.ServerError
import org.json.JSONException
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(InternalOwnIdAPI::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
public class OwnIdResponseTest {

    private class OwnIdCore(instanceName: InstanceName, configuration: Configuration) :
        OwnIdCoreImpl(instanceName, configuration) {
        override fun register(
            email: String, params: RegistrationParameters?, ownIdResponse: OwnIdResponse, callback: OwnIdCallback<Unit>
        ) {

        }

        override fun login(ownIdResponse: OwnIdResponse, callback: OwnIdCallback<Unit>) {
        }
    }

    private val ownIdCore = OwnIdCore(TestDataCore.validInstanceName, TestDataCore.validServerConfig)

    private val validJsonString = """
{
  "status": "finished",
  "context": "gPKx_DYbxUyom3ov4_lHVw",
  "payload": {
    "metadata": {
      "collectionName": "ownid",
      "docId": "AQQ3zEVraUG",
      "userIdKey": "userId"
    },
    "loginId": "hdhdh@jdhhd.fff",
    "type": "registrationInfo",
    "data": {
      "fido2CredentialId": "AQQ3zEVraUG",
      "fido2SignatureCounter": "0",
      "authType": "Fido2",
      "source": "Register"
    }
  }
}
        """.trimIndent()
    private lateinit var validOwnIdResponse: OwnIdResponse

    @Before
    public fun prepare() {
        OwnId.putInstance(ownIdCore)
        validOwnIdResponse = OwnIdResponse.fromStatusResponse("gPKx_DYbxUyom3ov4_lHVw", validJsonString)
    }

    @Test
    public fun fromStatusResponse() {
        val ownIdResponse = OwnIdResponse.fromStatusResponse("gPKx_DYbxUyom3ov4_lHVw", validJsonString)

        Truth.assertThat(ownIdResponse.context).isEqualTo("gPKx_DYbxUyom3ov4_lHVw")
        Truth.assertThat(ownIdResponse.loginId).isEqualTo("hdhdh@jdhhd.fff")
        Truth.assertThat(ownIdResponse.payload).isInstanceOf(OwnIdPayload::class.java)
    }

    @Test
    public fun fromStatusResponseError() {
        Assert.assertThrows(ServerError::class.java) {
            OwnIdResponse.fromStatusResponse(
                "CNxxRTleLkG1HEd9dVfvuw",
                "{\"status\":\"finished\",\"context\":\"CNxxRTleLkG1HEd9dVfvuw\",\"payload\":{\"error\":\"Account doesn\\u0027t exist or you are using a different phone\",\"isSuccess\":false}}"
            )
        }
    }

    @Test
    public fun fromStatusResponseBadConntextError() {
        Assert.assertThrows(ServerError::class.java) {
            OwnIdResponse.fromStatusResponse(
                "CNxxRTleLkG1HEsdfffd9dVfvuw",
                "{\"status\":\"finished\",\"context\":\"CNxxRTleLkG1HEd9dVfvuw\",\"payload\":{\"error\":\"Account doesn\\u0027t exist or you are using a different phone\",\"isSuccess\":false}}"
            )
        }
    }

    @Test
    public fun fromJsonString() {
        val ownIdResponse =
            OwnIdResponse.fromJsonString("""{"context":"gPKx_DYbxUyom3ov4_lHVw","loginId":"hdhdh@jdhhd.fff","payload":{"type":"registrationInfo","data":{"fido2CredentialId":"AQQ3zEVraUG","fido2SignatureCounter":"0","authType":"Fido2","source":"Register"},"metadata":{"collectionName":"ownid","docId":"AQQ3zEVraUG","userIdKey":"userId"}}}""")

        Truth.assertThat(ownIdResponse.context).isEqualTo("gPKx_DYbxUyom3ov4_lHVw")
        Truth.assertThat(ownIdResponse.loginId).isEqualTo("hdhdh@jdhhd.fff")
        Truth.assertThat(ownIdResponse.payload).isInstanceOf(OwnIdPayload::class.java)
    }

    @Test
    public fun fromJsonStringError() {
        val badJsonString = "{\"instanceName\":\"TestInstance\","

        Assert.assertThrows(JSONException::class.java) {
            OwnIdResponse.fromJsonString(badJsonString)
        }
    }

    @Test
    public fun toJsonString() {
        Truth.assertThat(validOwnIdResponse.toJsonString()).isEqualTo(
            """{"context":"gPKx_DYbxUyom3ov4_lHVw","loginId":"hdhdh@jdhhd.fff","payload":{"type":"registrationInfo","data":"{\"fido2CredentialId\":\"AQQ3zEVraUG\",\"fido2SignatureCounter\":\"0\",\"authType\":\"Fido2\",\"source\":\"Register\"}","metadata":"{\"collectionName\":\"ownid\",\"docId\":\"AQQ3zEVraUG\",\"userIdKey\":\"userId\"}"}}"""
        )
    }

    @Test
    public fun wrapInIntent() {
        val intent = validOwnIdResponse.wrapInIntent()

        BundleSubject.assertThat(intent.extras).containsKey(OwnIdResponse.KEY_RESPONSE_INTENT_DATA)
        BundleSubject.assertThat(intent.extras).string(OwnIdResponse.KEY_RESPONSE_INTENT_DATA)
            .isEqualTo("""{"context":"gPKx_DYbxUyom3ov4_lHVw","loginId":"hdhdh@jdhhd.fff","payload":{"type":"registrationInfo","data":"{\"fido2CredentialId\":\"AQQ3zEVraUG\",\"fido2SignatureCounter\":\"0\",\"authType\":\"Fido2\",\"source\":\"Register\"}","metadata":"{\"collectionName\":\"ownid\",\"docId\":\"AQQ3zEVraUG\",\"userIdKey\":\"userId\"}"}}""")
    }

    @Test
    public fun unwrapFromIntentOrThrow() {
        val intent = validOwnIdResponse.wrapInIntent()

        val ownIdResponse = OwnIdResponse.unwrapFromIntentOrThrow(intent)

        Truth.assertThat(ownIdResponse.context).isEqualTo(validOwnIdResponse.context)
        Truth.assertThat(ownIdResponse.loginId).isEqualTo(validOwnIdResponse.loginId)
        Truth.assertThat(ownIdResponse.payload).isEqualTo(validOwnIdResponse.payload)
    }

    @Test
    public fun unwrapFromIntentOrThrowError() {
        Assert.assertThrows(OwnIdException::class.java) {
            OwnIdResponse.unwrapFromIntentOrThrow(Intent())
        }
    }
}