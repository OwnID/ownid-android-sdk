package com.ownid.sdk

import com.ownid.sdk.internal.config.OwnIdConfigurationService
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.io.File

@OptIn(InternalOwnIdAPI::class)
internal object TestDataCore {
    internal const val validAppId: String = "ybmrs2pxdeazta"
    internal const val validEnv: String = "dev"
    internal const val validPackageName: String = "com.ownid.demo.firebase"
    internal const val validVersion: String = "OwnIDFirebase/0.4.0 OwnIDCore/0.4.0"
    internal const val validUserAgent: String =
        "OwnIDFirebase/0.4.0 (Linux; Android 13; Pixel 4a 5G) OwnIDCore/0.4.0 OwnIDFirebase/0.4.0 $validPackageName"
    internal const val validRedirectUrl: String = "ownid://com.ownid.sdk/redirect/"

    internal val validCacheDir: File = File("./build/tmp/cache_test")

    internal val validConfigurationJson = JSONObject()
        .put("appId", validAppId)
        .put("env", validEnv)
        .put("redirectUrl", validRedirectUrl)
        .put("enableLogging", true)
        .toString()

    internal val validConfigurationJsonByteArray = validConfigurationJson.encodeToByteArray()

    internal val validConfigurationAssets = listOf("OwnIDCore" to "0.4.0", "OwnIDFirebase" to "0.4.0")
    internal val validHashSet = setOf("4C:C2:AC:17:6E:0B:FA:AF:35:CD:8D:71:CD:80:F8:87:E9:9C:FC:8E:38:7A:22:91:37:CA:AB:59:E5:0F:4D:8E")

    internal val validServerConfigurationUrl: HttpUrl = "https://cdn.dev.ownid.com/sdk/ybmrs2pxdeazta/mobile".toHttpUrl()
    internal val validEventsUrl: HttpUrl = "https://ybmrs2pxdeazta.server.dev.ownid.com/events".toHttpUrl()
    internal val validLocaleUri: HttpUrl = "https://i18n.dev.ownid.com/ua/mobile-sdk.json".toHttpUrl()


    internal const val validLanguage: String = "en"
    internal const val validEmail: String = "email@test.com"

    internal val validConfig: Configuration = Configuration(
        validAppId,
        "$validEnv.",
        validRedirectUrl,
        validVersion,
        validUserAgent,
        validPackageName,
        validHashSet
    )

    internal val validInstanceName = InstanceName("TestInstance")

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
    internal val validServerConfig = OwnIdConfigurationService.fromServerResponse(response)
}