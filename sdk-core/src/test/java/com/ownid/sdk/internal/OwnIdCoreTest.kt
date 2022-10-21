package com.ownid.sdk.internal

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.truth.content.IntentSubject
import com.google.common.truth.Truth
import com.ownid.sdk.Configuration
import com.ownid.sdk.InstanceName
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnIdCallback
import com.ownid.sdk.RegistrationParameters
import com.ownid.sdk.TestDataCore
import org.json.JSONObject
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

@androidx.annotation.OptIn(InternalOwnIdAPI::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
public class OwnIdCoreTest {

    private class OwnIdCore(instanceName: InstanceName, configuration: Configuration) :
        OwnIdCoreImpl(instanceName, configuration) {
        override fun register(
            email: String, params: RegistrationParameters?, ownIdResponse: OwnIdResponse, callback: OwnIdCallback<Unit>
        ) {

        }

        override fun login(ownIdResponse: OwnIdResponse, callback: OwnIdCallback<Unit>) {
        }
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val ownIdCore: OwnIdCore = OwnIdCore(TestDataCore.validInstanceName, TestDataCore.validServerConfig)

    @Test
    public fun createRegisterIntent() {
        val registerIntent =
            ownIdCore.createRegisterIntent(context, TestDataCore.validLanguage, TestDataCore.validEmail)

        val validSessionVerifier = registerIntent.extras?.getString(OwnIdActivity.KEY_REQUEST)?.let {
            JSONObject(it).optString("sessionVerifier")
        }!!

        val requestJson = OwnIdRequest(
            ownIdCore,
            OwnIdRequest.Type.REGISTER,
            TestDataCore.validLanguage,
            TestDataCore.validEmail,
            validSessionVerifier
        ).toJsonString()

        IntentSubject.assertThat(registerIntent)
            .hasComponent("com.ownid.sdk.test", OwnIdActivity::class.java.name)
        IntentSubject.assertThat(registerIntent).extras().containsKey(OwnIdActivity.KEY_REQUEST)
        Assert.assertEquals(requestJson, registerIntent.extras?.get(OwnIdActivity.KEY_REQUEST))
    }

    @Test
    public fun createLoginIntent() {
        val loginIntent = ownIdCore.createLoginIntent(context, TestDataCore.validLanguage, TestDataCore.validEmail)

        val validSessionVerifier = loginIntent.extras?.getString(OwnIdActivity.KEY_REQUEST)?.let {
            JSONObject(it).optString("sessionVerifier")
        }!!

        val requestJson = OwnIdRequest(
            ownIdCore,
            OwnIdRequest.Type.LOGIN,
            TestDataCore.validLanguage,
            TestDataCore.validEmail,
            validSessionVerifier
        ).toJsonString()

        IntentSubject.assertThat(loginIntent)
            .hasComponent("com.ownid.sdk.test", OwnIdActivity::class.java.name)
        IntentSubject.assertThat(loginIntent).extras().containsKey(OwnIdActivity.KEY_REQUEST)
        Assert.assertEquals(requestJson, loginIntent.extras?.get(OwnIdActivity.KEY_REQUEST))
    }

    @Test
    public fun generateOwnIdPassword() {
        val possibleRegularChars = "abcdefghijklmnopqrstuvwxyz".toCharArray()
        val possibleCapitalChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray()
        val possibleNumberChars = "0123456789".toCharArray()
        val possibleSpecialChars = "@$%*&^-+!#_=".toCharArray()

        repeat(10) {
            val length = Random.nextInt(10, 20)
            val numberCapitalised = Random.nextInt(1, 4)
            val numberNumbers = Random.nextInt(1, 4)
            val numberSpecial = Random.nextInt(1, 4)

            val password = ownIdCore.generatePassword(length, numberCapitalised, numberNumbers, numberSpecial)

            Truth.assertThat(password).hasLength(length)

            Truth.assertThat(password.toCharArray().filter { possibleCapitalChars.contains(it) })
                .hasSize(numberCapitalised)

            Truth.assertThat(password.toCharArray().filter { possibleNumberChars.contains(it) }).hasSize(numberNumbers)

            Truth.assertThat(password.toCharArray().filter { possibleSpecialChars.contains(it) }).hasSize(numberSpecial)

            Truth.assertThat(password.toCharArray().filter { possibleRegularChars.contains(it) })
                .hasSize(length - numberCapitalised - numberNumbers - numberSpecial)
        }
    }
}