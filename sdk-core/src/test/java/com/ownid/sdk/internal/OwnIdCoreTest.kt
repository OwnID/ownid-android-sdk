package com.ownid.sdk.internal

import com.google.common.truth.Truth
import com.ownid.sdk.Configuration
import com.ownid.sdk.InstanceName
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnIdCore
import com.ownid.sdk.OwnIdWebViewBridge
import com.ownid.sdk.TestDataCore
import com.ownid.sdk.internal.feature.webbridge.OwnIdWebViewBridgeImpl
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
public class OwnIdCoreTest {

    private class OwnIdCoreTest(override val instanceName: InstanceName, override val configuration: Configuration) : OwnIdCore {
        @OptIn(InternalOwnIdAPI::class)
        override fun createWebViewBridge(): OwnIdWebViewBridge  = OwnIdWebViewBridgeImpl(instanceName)
    }

    private val ownIdCoreTest = OwnIdCoreTest(TestDataCore.validInstanceName, TestDataCore.validConfig)

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

            val password = ownIdCoreTest.generatePassword(length, numberCapitalised, numberNumbers, numberSpecial)

            Truth.assertThat(password).hasLength(length)
            Truth.assertThat(password.toCharArray().filter { possibleCapitalChars.contains(it) }).hasSize(numberCapitalised)
            Truth.assertThat(password.toCharArray().filter { possibleNumberChars.contains(it) }).hasSize(numberNumbers)
            Truth.assertThat(password.toCharArray().filter { possibleSpecialChars.contains(it) }).hasSize(numberSpecial)
            Truth.assertThat(password.toCharArray().filter { possibleRegularChars.contains(it) })
                .hasSize(length - numberCapitalised - numberNumbers - numberSpecial)
        }
    }
}