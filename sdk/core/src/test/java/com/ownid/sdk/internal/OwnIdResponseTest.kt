package com.ownid.sdk.internal

import com.google.common.truth.Truth
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnIdFlowInfo
import com.ownid.sdk.OwnIdPayload
import com.ownid.sdk.OwnIdResponse
import org.json.JSONException
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(InternalOwnIdAPI::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
public class OwnIdResponseTest {
    private val validJsonString = """
{
  "flowInfo": {
    "event": "register",
    "authType": "Fido2"
  },
  "status": "finished",
  "context": "6bX9KgHRuUa6Qe-W68uPHw",
  "payload": {
    "metadata": {
      "collectionName": "ownid",
      "docId": "wMtMb-cH8qCsQPawmE19Bw",
      "userIdKey": "userId"
    },
    "loginId": "ffff@fff.hhh",
    "type": "registrationInfo",
    "data": {
      "pubKey": "pQECAyYgASFYII3b2bW9RoHOK-OReMm6-RB87Beq4KwXi3oPqEQ_en0XIlgg_NtC5ympyIRtEDwV_4Amu1tykwPcmLF82JmBhZaGjng",
      "fido2CredentialId": "wMtMb-cH8qCsQPawmE19Bw",
      "fido2SignatureCounter": "0",
      "fido2RpId": "dev.ownid.com",
      "authType": "Fido2",
      "source": "Register",
      "os": "Android",
      "osVersion": "13",
      "creationSource": "Native",
      "createdTimestamp": "2023-03-22T09:08:55.8135698Z"
    }
  }
}
        """.trimIndent()

    @Test
    public fun fromStatusResponse() {
        val ownIdResponse = OwnIdResponse.fromServerResponse(validJsonString, "en")

        Truth.assertThat(ownIdResponse.context).isEqualTo("6bX9KgHRuUa6Qe-W68uPHw")
        Truth.assertThat(ownIdResponse.loginId).isEqualTo("ffff@fff.hhh")
        Truth.assertThat(ownIdResponse.flowInfo).isInstanceOf(OwnIdFlowInfo::class.java)
        Truth.assertThat(ownIdResponse.flowInfo.event).isEqualTo(OwnIdFlowInfo.Event.Register)
        Truth.assertThat(ownIdResponse.flowInfo.authType).isEqualTo("Fido2")
        Truth.assertThat(ownIdResponse.payload).isInstanceOf(OwnIdPayload::class.java)
        Truth.assertThat(ownIdResponse.payload.type).isEqualTo(OwnIdPayload.Type.Registration)
    }

    @Test
    public fun fromStatusResponseBadJson() {
        val exception = Assert.assertThrows(JSONException::class.java) {
            OwnIdResponse.fromServerResponse(
                "\"status\":\"finished\",\"context\":\"CNxxRTleLkG1HEd9dVfvuw\",\"flowInfo\": {\"event\": \"register\"},\"payload\":{\"error\":\"Account doesn\\u0027t exist or you are using a different phone\",\"isSuccess\":false}}",
                "en"
            )
        }

        Truth.assertThat(exception).hasMessageThat()
            .isEqualTo("Value status of type java.lang.String cannot be converted to JSONObject")
    }

//    @Test
//    public fun fromStatusResponseError() {
//        val exception = Assert.assertThrows(JSONException::class.java) {
//            OwnIdResponse.fromServerResponse(
//                "{\"status\":\"finished\",\"context\":\"CNxxRTleLkG1HEd9dVfvuw\",\"flowInfo\": {\"event\": \"register\"},\"payload\":{\"error\":\"Account doesn\\u0027t exist or you are using a different phone\",\"isSuccess\":false}}",
//                "en"
//            )
//        }
//
//        Truth.assertThat(exception).hasMessageThat()
//            .isEqualTo("SessionFlowError.fromJsonString: Value Account of type java.lang.String cannot be converted to JSONObject")
//    }
}