package com.ownid.sdk.internal

import android.content.Context
import android.content.res.Resources
import com.google.common.truth.Truth
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.TestDataCore
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.internal.component.config.OwnIdConfigurationService
import com.ownid.sdk.internal.component.config.OwnIdServerConfiguration
import com.ownid.sdk.internal.component.events.LogItem
import io.mockk.every
import io.mockk.mockk
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(InternalOwnIdAPI::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
public class OwnIdServerConfigurationTest {

    private val contextMockk = mockk<Context>()
    private val resourcesMockk = mockk<Resources>()


    @Before
    public fun setUp() {
        every { contextMockk.cacheDir } returns TestDataCore.validCacheDir
        every { contextMockk.applicationContext } returns contextMockk
        every { contextMockk.resources } returns resourcesMockk
    }

    @Test
    public fun `test fromServerResponse with valid JSON response`() {
        // given
        val response = """
            {
  "supportedLocales": [
    "en",
    "fr"
  ],
  "logLevel": "Warning",
  "redirectUrl": "https://www.example.com",
  "androidSettings": {
    "packageName": "com.example.app",
    "certificateHashes": [
      "96:CB:FB:4A:0C:49:6E:DA:DA:28:0A:F3:3C:16:9C:54:8C:3B:3A:3F:47:DC:D8:7E:10:51:1B:C9:5D:FA:FC:E2",
      "E2:1A:81:75:BF:CB:9D:47:9F:84:8D:09:10:C1:F5:39:CA:2D:90:81:44:28:9F:7A:F2:4A:ED:43:AE:5F:99:43"
    ],
    "redirectUrlOverride": "https://example.com/redirect"
  },
  "passkeysAutofillEnabled": true,
  "serverUrl": "https://ybmrs2pxdeazta.server.dev.ownid.com",
  "loginId": {
    "type": "email",
    "regex": "^(?=(.{1,64}@.{1,255}))([\\w!#${'$'}%\u0026\u0027*\u002B/=?^\u0060{|}~-]{1,64}(\\.[\\w!#${'$'}%\u0026\u0027*\u002B/=?^\u0060{|}~-]*)*)@((\\[(25[0-5]|2[0-4]\\d|[01]?\\d{1,2})(\\.(25[0-5]|2[0-4]\\d|[01]?\\d{1,2})){3}])|([\\dA-Za-z-]{1,63}(\\.[\\dA-Za-z-]{2,63})\u002B))${'$'}"
  },
  "displayName": "Demo Firebase",
  "instantConnectEnabled": true
}
        """.trimIndent()

        // when
        val config = OwnIdConfigurationService.fromServerResponse(response)

        // then
        Truth.assertThat(config).isNotNull()
        Truth.assertThat(config.logLevel).isEqualTo(LogItem.Level.WARNING)
        Truth.assertThat(config.redirectUrl).isEqualTo("https://www.example.com")
        Truth.assertThat(config.passkeysAutofillEnabled).isEqualTo(true)
        Truth.assertThat(config.supportedLocales).isEqualTo(setOf("en", "fr"))
        Truth.assertThat(config.serverUrl.isHttps).isTrue()
        Truth.assertThat(config.serverUrl.topPrivateDomain()).isEqualTo("ownid.com")
        Truth.assertThat(config.androidSettings).isNotNull()
        Truth.assertThat(config.loginId.type).isInstanceOf(OwnIdServerConfiguration.LoginId.Type.Email::class.java)
    }

    @Test
    public fun `test fromServerResponse with valid JSON response EU`() {
        // given
        val response = """
            {
  "supportedLocales": [
    "en",
    "fr"
  ],
  "logLevel": "Warning",
  "redirectUrl": "https://www.example.com",
  "androidSettings": {
    "packageName": "com.example.app",
    "certificateHashes": [
      "96:CB:FB:4A:0C:49:6E:DA:DA:28:0A:F3:3C:16:9C:54:8C:3B:3A:3F:47:DC:D8:7E:10:51:1B:C9:5D:FA:FC:E2",
      "E2:1A:81:75:BF:CB:9D:47:9F:84:8D:09:10:C1:F5:39:CA:2D:90:81:44:28:9F:7A:F2:4A:ED:43:AE:5F:99:43"
    ],
    "redirectUrlOverride": "https://example.com/redirect"
  },
  "passkeysAutofillEnabled": true,
  "serverUrl": "https://ybmrs2pxdeazta.server.dev.ownid-eu.com",
  "loginId": {
    "type": "email",
    "regex": "^(?=(.{1,64}@.{1,255}))([\\w!#${'$'}%\u0026\u0027*\u002B/=?^\u0060{|}~-]{1,64}(\\.[\\w!#${'$'}%\u0026\u0027*\u002B/=?^\u0060{|}~-]*)*)@((\\[(25[0-5]|2[0-4]\\d|[01]?\\d{1,2})(\\.(25[0-5]|2[0-4]\\d|[01]?\\d{1,2})){3}])|([\\dA-Za-z-]{1,63}(\\.[\\dA-Za-z-]{2,63})\u002B))${'$'}"
  },
  "displayName": "Demo Firebase",
  "instantConnectEnabled": true
}
        """.trimIndent()

        // when
        val config = OwnIdConfigurationService.fromServerResponse(response)

        // then
        Truth.assertThat(config).isNotNull()
        Truth.assertThat(config.logLevel).isEqualTo(LogItem.Level.WARNING)
        Truth.assertThat(config.redirectUrl).isEqualTo("https://www.example.com")
        Truth.assertThat(config.passkeysAutofillEnabled).isEqualTo(true)
        Truth.assertThat(config.supportedLocales).isEqualTo(setOf("en", "fr"))
        Truth.assertThat(config.serverUrl.isHttps).isTrue()
        Truth.assertThat(config.serverUrl.topPrivateDomain()).isEqualTo("ownid-eu.com")
        Truth.assertThat(config.androidSettings).isNotNull()
        Truth.assertThat(config.loginId.type).isInstanceOf(OwnIdServerConfiguration.LoginId.Type.Email::class.java)
    }

    @Test(expected = OwnIdException::class)
    public fun `test fromServerResponse with invalid server URL scheme`() {
        // given
        val response = """
            {
                "serverUrl": "http://www.ownid.com",
                "supportedLocales": ["en"],
                "logLevel": "INFO"
            }
        """.trimIndent()

        // when
        OwnIdConfigurationService.fromServerResponse(response)

        // then expect OwnIdException to be thrown
    }

    @Test(expected = OwnIdException::class)
    public fun `test fromServerResponse with non-ownid com server URL`() {
        // given
        val response = """
            {
                "serverUrl": "https://www.example.com",
                "supportedLocales": ["en"],
                "logLevel": "WARN"
            }
        """.trimIndent()

        // when
        OwnIdConfigurationService.fromServerResponse(response)

        // then expect OwnIdException to be thrown
    }

}