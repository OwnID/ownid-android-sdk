package com.ownid.sdk

import android.net.Uri
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.File

internal object TestDataCore {
    internal const val validVersion: String = "OwnIDFirebase/0.4.0 OwnIDCore/0.4.0"
    internal const val validUserAgent: String =
        "OwnIDFirebase/0.4.0 (Linux; Android 12; Pixel 4a (5G)) OwnIDCore/0.4.0 OwnIDFirebase/0.4.0 com.ownid.demo.firebase.dev"
    internal val validServerUrl: HttpUrl = "https://firebase.server.dev.ownid.com".toHttpUrl()
    internal val validRedirectionUri: Uri = Uri.parse("ownid://com.ownid.sdk/redirect/")
    internal val validLocaleUri: HttpUrl = "https://i18n.ownid.com".toHttpUrl()
    internal const val validStatusFinalSuffix: String = "ownid/status/final"

    internal const val validLanguage: String = "en"
    internal const val validEmail: String = "email@test.com"
    internal val validCacheDir: File = File("./build/tmp/cache_test")

    internal val validConfigurationJson =
        """{
  "app_id": "firebase",
   "env": "dev",
  "redirection_uri": "ownid://com.ownid.sdk/redirect/",
  "enable_logging": true
}"""

    internal val validConfigurationJsonByteArray = validConfigurationJson.encodeToByteArray()
    internal val validConfigurationAssets = listOf(
        "OwnIDCore" to "0.4.0",
        "OwnIDFirebase" to "0.4.0",
    )

    internal val validServerConfig: Configuration = Configuration(
        validVersion,
        validUserAgent,
        validServerUrl,
        validRedirectionUri,
        validLocaleUri,
        validCacheDir
    )

    internal val validInstanceName = InstanceName("TestInstance")
}