package com.ownid.sdk.internal

import android.net.Uri
import com.google.common.truth.Truth
import com.ownid.sdk.Configuration
import com.ownid.sdk.InstanceName
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnId
import com.ownid.sdk.OwnIdCallback
import com.ownid.sdk.RegistrationParameters
import com.ownid.sdk.TestDataCore
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@androidx.annotation.OptIn(InternalOwnIdAPI::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
public class OwnIdRequestTest {

    private class OwnIdCore(instanceName: InstanceName, configuration: Configuration) :
        OwnIdCoreImpl(instanceName, configuration) {
        override fun register(
            email: String, params: RegistrationParameters?, ownIdResponse: OwnIdResponse, callback: OwnIdCallback<Unit>
        ) {

        }

        override fun login(ownIdResponse: OwnIdResponse, callback: OwnIdCallback<Unit>) {
        }
    }

    private val ownIdCore: OwnIdCore = OwnIdCore(TestDataCore.validInstanceName, TestDataCore.validServerConfig)
    private val validLoginJson =
        """{"name":"TestInstance","type":"LOGIN","language":"en-US,he-IL,ru-UA,zh-Hans-CN","email":"dfgdg@dfgdf.dfgd","sessionVerifier":"F6iKNBq11dJDKtfheNLrfkSh-CaCigEBpgLYcK_8EIs","url":"","context":"","nonce":"","expiration":0}"""
    private lateinit var validLoginOwnIdRequest: OwnIdRequest


    @Before
    public fun prepare() {
        OwnId.putInstance(ownIdCore)

        validLoginOwnIdRequest = OwnIdRequest(
            ownIdCore,
            OwnIdRequest.Type.LOGIN,
            "en-US,he-IL,ru-UA,zh-Hans-CN",
            "dfgdg@dfgdf.dfgd",
            "F6iKNBq11dJDKtfheNLrfkSh-CaCigEBpgLYcK_8EIs",
            "",
            "",
            "",
            0L
        )
    }

    @Test
    public fun fromJsonString() {
        val request = OwnIdRequest.fromJsonString(validLoginJson)
        Truth.assertThat(request).isEqualTo(validLoginOwnIdRequest)
    }

    @Test
    public fun toJsonString() {
        val json = validLoginOwnIdRequest.toJsonString()
        Truth.assertThat(json).isEqualTo(validLoginJson)
    }

    @Test
    public fun fromJsonStringLoginNotStarted() {
        val request = OwnIdRequest.fromJsonString(validLoginJson)
        Truth.assertThat(request.isRequestStarted()).isFalse()
    }

    @Test
    public fun getUriForBrowser() {
        val request = OwnIdRequest.fromJsonString(validLoginJson)
        Truth.assertThat(request.getUriForBrowser())
            .isEqualTo(Uri.parse("?e=dfgdg%40dfgdf.dfgd&redirectURI=ownid%3A%2F%2Fcom.ownid.sdk%2Fredirect%2F%3Fcontext%3D%26instanceName%3DTestInstance"))
    }
}