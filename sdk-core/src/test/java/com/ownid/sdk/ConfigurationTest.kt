package com.ownid.sdk

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONException
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.io.File

@OptIn(InternalOwnIdAPI::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
public class ConfigurationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    public fun createFromAssetFileCorrect() {
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "RELEASE", "12")
        ReflectionHelpers.setStaticField(Build::class.java, "MODEL", "Pixel 4a (5G)")

        val configurationAssetFileName = "ownIdSdkFirebaseConfig.json"
        val product = "OwnIDFirebase/0.4.0"

        val contextMockk = mockk<Context>()
        every { contextMockk.packageName } returns "com.ownid.demo.firebase.dev"
        every { contextMockk.cacheDir } returns TestDataCore.validCacheDir

        mockkObject(Configuration)

        val slotFileName = slot<String>()
        every { Configuration.getFileFromAssets(any(), capture(slotFileName)) } returns
                TestDataCore.validConfigurationJsonByteArray

        every { Configuration.getVersionsFromAssets(any()) } returns
                TestDataCore.validConfigurationAssets

        val configuration = Configuration.createFromAssetFile(contextMockk, configurationAssetFileName, product)

        Truth.assertThat(verify(exactly = 1) {
            Configuration.getFileFromAssets(any(), capture(slotFileName))
        })

        Truth.assertThat(slotFileName.captured).isEqualTo(configurationAssetFileName)
        Truth.assertThat(configuration.version).isEqualTo(TestDataCore.validVersion)
        Truth.assertThat(configuration.userAgent).isEqualTo("OwnIDFirebase/0.4.0 (Linux; Android 12; Pixel 4a 5G) OwnIDCore/0.4.0 OwnIDFirebase/0.4.0 com.ownid.demo.firebase.dev")
        Truth.assertThat(configuration.serverUrl).isEqualTo(TestDataCore.validServerUrl)
        Truth.assertThat(configuration.redirectionUri).isEqualTo(TestDataCore.validRedirectionUri)
    }

    @Test
    public fun createFromAssetFileBadJson() {
        val configurationAssetFileName = "ownIdSdkFirebaseConfig.json"
        val product = "OwnIDFirebase/0.4.0"

        val contextMockk = mockk<Context>()

        mockkObject(Configuration)

        val slotFileName = slot<String>()
        every { Configuration.getFileFromAssets(any(), capture(slotFileName)) } returns
                """
  "app_id": "firebase",
  "redirection_uri": "com.ownid.demo:/android",
  "enable_logging": true
""".encodeToByteArray()

        assertThrows(JSONException::class.java) {
            Configuration.createFromAssetFile(contextMockk, configurationAssetFileName, product)
        }

        Truth.assertThat(verify(exactly = 1) {
            Configuration.getFileFromAssets(any(), capture(slotFileName))
        })
    }

    @Test
    public fun createFromJsonCorrect() {
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "RELEASE", "12")
        ReflectionHelpers.setStaticField(Build::class.java, "MODEL", "Pixel 4a (5G)")

        val product = "OwnIDFirebase/0.4.0"

        val contextMockk = mockk<Context>()
        every { contextMockk.packageName } returns "com.ownid.demo.firebase.dev"
        every { contextMockk.cacheDir } returns TestDataCore.validCacheDir

        mockkObject(Configuration)

        every { Configuration.getVersionsFromAssets(any()) } returns
                TestDataCore.validConfigurationAssets

        val configuration = Configuration.createFromJson(contextMockk, TestDataCore.validConfigurationJson, product)

        Truth.assertThat(configuration.version).isEqualTo(TestDataCore.validVersion)
        Truth.assertThat(configuration.userAgent).isEqualTo("OwnIDFirebase/0.4.0 (Linux; Android 12; Pixel 4a 5G) OwnIDCore/0.4.0 OwnIDFirebase/0.4.0 com.ownid.demo.firebase.dev")
        Truth.assertThat(configuration.serverUrl).isEqualTo(TestDataCore.validServerUrl)
        Truth.assertThat(configuration.redirectionUri).isEqualTo(TestDataCore.validRedirectionUri)
    }

    @Test
    public fun createFromJsonBadJson() {
        val product = "OwnIDFirebase/0.4.0"

        val contextMockk = mockk<Context>()

        mockkObject(Configuration)

        val babJson = """
  "app_id": "firebase",
  "redirection_uri": "com.ownid.demo:/android",
  "enable_logging": true
"""

        assertThrows(JSONException::class.java) {
            Configuration.createFromJson(contextMockk, babJson, product)
        }
    }

    @Test
    public fun configurationCreateCorrect() {
        val serverConfig = Configuration(
            TestDataCore.validVersion,
            TestDataCore.validUserAgent,
            TestDataCore.validServerUrl,
            TestDataCore.validRedirectionUri,
            TestDataCore.validLocaleUri,
            TestDataCore.validCacheDir
        )

        Truth.assertThat(serverConfig.userAgent)
            .isEqualTo(TestDataCore.validUserAgent)

        Truth.assertThat(serverConfig.serverUrl)
            .isEqualTo(TestDataCore.validServerUrl)

        Truth.assertThat(serverConfig.ownIdStatusUrl)
            .isEqualTo(
                TestDataCore.validServerUrl.newBuilder()
                    .addEncodedPathSegments(TestDataCore.validStatusFinalSuffix)
                    .build()
            )

        Truth.assertThat(serverConfig.redirectionUri)
            .isEqualTo(TestDataCore.validRedirectionUri)

    }

    @Test
    public fun configurationCreateNoSchemeInRedirectionUri() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            Configuration(
                TestDataCore.validVersion,
                TestDataCore.validUserAgent,
                TestDataCore.validServerUrl,
                Uri.parse("wrong.redirection.scheme"),
                TestDataCore.validLocaleUri,
                TestDataCore.validCacheDir
            )
        }

        Truth.assertThat(exception).hasMessageThat()
            .isEqualTo("Redirection URI must contain an explicit scheme")
    }

    @Test
    public fun configurationCreateEmptyCustomUriSchemaPrefix() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            Configuration(
                TestDataCore.validVersion,
                TestDataCore.validUserAgent,
                TestDataCore.validServerUrl,
                Uri.EMPTY,
                TestDataCore.validLocaleUri,
                TestDataCore.validCacheDir
            )
        }

        Truth.assertThat(exception).hasMessageThat()
            .isEqualTo("Redirection URI must contain an explicit scheme")
    }

    @Test
    public fun configurationCreateHttpServerUrl() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            Configuration(
                TestDataCore.validVersion,
                TestDataCore.validUserAgent,
                "http://gigya.server.dev.ownid.com".toHttpUrl(),
                TestDataCore.validRedirectionUri,
                TestDataCore.validLocaleUri,
                TestDataCore.validCacheDir
            )
        }

        Truth.assertThat(exception).hasMessageThat()
            .isEqualTo("Server url: only https supported")
    }

    @Test
    public fun configurationCreateWrongTopDomain() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            Configuration(
                TestDataCore.validVersion,
                TestDataCore.validUserAgent,
                "https://gigya.server.dev.notownid.com".toHttpUrl(),
                TestDataCore.validRedirectionUri,
                TestDataCore.validLocaleUri,
                TestDataCore.validCacheDir
            )
        }

        Truth.assertThat(exception).hasMessageThat()
            .isEqualTo("Server url: Not *.ownid.com url")
    }

    @Test
    public fun configurationCreateBadAppId() {
        val configJson = """
{
  "app_id": "gigya.server.dev.ownid",
  "redirection_uri": "com.ownid.demo.firebase:/"
}
        """

        val configurationSpyk = spyk<Configuration.Companion>()
        every { configurationSpyk.getFileFromAssets(context, any()) } returns configJson.encodeToByteArray()

        val exception = assertThrows(IllegalArgumentException::class.java) {
            configurationSpyk.createFromAssetFile(context, "config.json", TestDataCore.validVersion)
        }

        Truth.assertThat(exception).hasMessageThat()
            .isEqualTo("Bad or empty App Id (gigya.server.dev.ownid)")
    }

    @Test
    public fun configurationCreateNoppId() {
        val configJson = """
{
  "redirection_uri": "com.ownid.demo.firebase:/"
}
        """

        val configurationSpyk = spyk<Configuration.Companion>()
        every { configurationSpyk.getFileFromAssets(context, any()) } returns configJson.encodeToByteArray()

        val exception = assertThrows(IllegalArgumentException::class.java) {
            configurationSpyk.createFromAssetFile(context, "config.json", TestDataCore.validVersion)
        }

        Truth.assertThat(exception).hasMessageThat()
            .isEqualTo("Bad or empty App Id ()")
    }
}