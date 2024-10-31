package com.ownid.sdk

import android.content.Context
import android.os.Build
import com.google.common.truth.Truth
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.verify
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@OptIn(InternalOwnIdAPI::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
public class ConfigurationTest {
    private val configurationAssetFileName = "ownIdFirebaseSdkConfig.json"
    private val product = "OwnIDFirebase/0.4.0"

    @Before
    public fun prepare() {
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "RELEASE", "13")
        ReflectionHelpers.setStaticField(Build::class.java, "MODEL", "Pixel 4a (5G)")
    }

    @Test
    public fun createFromAssetFileCorrect() {
        val contextMockk = mockk<Context>()
        every { contextMockk.packageName } returns TestDataCore.validPackageName
        every { contextMockk.cacheDir } returns TestDataCore.validCacheDir
        every { contextMockk.applicationContext } returns contextMockk

        mockkObject(Configuration)

        val slotFileName = slot<String>()
        every { Configuration.getFileFromAssets(any(), capture(slotFileName)) } returns TestDataCore.validConfigurationJsonByteArray
        every { Configuration.getVersionsFromAssets(any()) } returns TestDataCore.validConfigurationAssets

        val configuration = Configuration.createFromAssetFile(contextMockk, configurationAssetFileName, product)

        verify(exactly = 1) {
            Configuration.getFileFromAssets(any(), capture(slotFileName))
        }

        Truth.assertThat(slotFileName.captured).isEqualTo(configurationAssetFileName)
        Truth.assertThat(configuration.appId).isEqualTo(TestDataCore.validAppId)
        Truth.assertThat(configuration.env).isEqualTo(TestDataCore.validEnv + ".")
        Truth.assertThat(configuration.region).isEqualTo(TestDataCore.validRegion)
        Truth.assertThat(configuration.redirectUrl).isEqualTo(TestDataCore.validRedirectUrl)
        Truth.assertThat(configuration.version).isEqualTo(TestDataCore.validVersion)
        Truth.assertThat(configuration.userAgent).isEqualTo(TestDataCore.validUserAgent)
        Truth.assertThat(configuration.packageName).isEqualTo(TestDataCore.validPackageName)
        Truth.assertThat(configuration.getRedirectUri()).isEqualTo(TestDataCore.validRedirectUrl)
    }

    @Test
    public fun createFromAssetFileCorrectEU() {
        val contextMockk = mockk<Context>()
        every { contextMockk.packageName } returns TestDataCore.validPackageName
        every { contextMockk.cacheDir } returns TestDataCore.validCacheDir
        every { contextMockk.applicationContext } returns contextMockk

        mockkObject(Configuration)

        val slotFileName = slot<String>()
        every { Configuration.getFileFromAssets(any(), capture(slotFileName)) } returns TestDataCore.validConfigurationJsonByteArrayEU
        every { Configuration.getVersionsFromAssets(any()) } returns TestDataCore.validConfigurationAssets

        val configuration = Configuration.createFromAssetFile(contextMockk, configurationAssetFileName, product)

        verify(exactly = 1) {
            Configuration.getFileFromAssets(any(), capture(slotFileName))
        }

        Truth.assertThat(slotFileName.captured).isEqualTo(configurationAssetFileName)
        Truth.assertThat(configuration.appId).isEqualTo(TestDataCore.validAppId)
        Truth.assertThat(configuration.env).isEqualTo(TestDataCore.validEnv + ".")
        Truth.assertThat(configuration.region).isEqualTo("-eu")
        Truth.assertThat(configuration.redirectUrl).isEqualTo(TestDataCore.validRedirectUrl)
        Truth.assertThat(configuration.version).isEqualTo(TestDataCore.validVersion)
        Truth.assertThat(configuration.userAgent).isEqualTo(TestDataCore.validUserAgent)
        Truth.assertThat(configuration.packageName).isEqualTo(TestDataCore.validPackageName)
        Truth.assertThat(configuration.getRedirectUri()).isEqualTo(TestDataCore.validRedirectUrl)
    }

    @Test
    public fun createFromAssetFileBadDC() {
        val contextMockk = mockk<Context>()
        every { contextMockk.packageName } returns TestDataCore.validPackageName
        every { contextMockk.cacheDir } returns TestDataCore.validCacheDir
        every { contextMockk.applicationContext } returns contextMockk

        mockkObject(Configuration)

        val validConfiguration = JSONObject()
            .put("appId", TestDataCore.validAppId)
            .put("env", TestDataCore.validEnv)
            .put("dataCenter", "eudd")
            .put("redirectUrl", TestDataCore.validRedirectUrl)
            .put("enableLogging", true)
            .toString().encodeToByteArray()

        val slotFileName = slot<String>()
        every { Configuration.getFileFromAssets(any(), capture(slotFileName)) } returns validConfiguration
        every { Configuration.getVersionsFromAssets(any()) } returns TestDataCore.validConfigurationAssets

        val configuration = Configuration.createFromAssetFile(contextMockk, configurationAssetFileName, product)

        verify(exactly = 1) {
            Configuration.getFileFromAssets(any(), capture(slotFileName))
        }

        Truth.assertThat(slotFileName.captured).isEqualTo(configurationAssetFileName)
        Truth.assertThat(configuration.appId).isEqualTo(TestDataCore.validAppId)
        Truth.assertThat(configuration.env).isEqualTo(TestDataCore.validEnv + ".")
        Truth.assertThat(configuration.region).isEqualTo("")
        Truth.assertThat(configuration.redirectUrl).isEqualTo(TestDataCore.validRedirectUrl)
        Truth.assertThat(configuration.version).isEqualTo(TestDataCore.validVersion)
        Truth.assertThat(configuration.userAgent).isEqualTo(TestDataCore.validUserAgent)
        Truth.assertThat(configuration.packageName).isEqualTo(TestDataCore.validPackageName)
        Truth.assertThat(configuration.getRedirectUri()).isEqualTo(TestDataCore.validRedirectUrl)
    }

    @Test
    public fun createFromAssetFileBadJson() {
        val contextMockk = mockk<Context>()

        mockkObject(Configuration)
        every { contextMockk.applicationContext } returns contextMockk
        every { contextMockk.packageName } returns TestDataCore.validPackageName

        val slotFileName = slot<String>()
        every { Configuration.getFileFromAssets(any(), capture(slotFileName)) } returns
                """ "appId":"firebase","redirectUrl":"com.ownid.demo:/android","enableLogging": true """.encodeToByteArray()

        val exception = assertThrows(JSONException::class.java) {
            Configuration.createFromAssetFile(contextMockk, configurationAssetFileName, product)
        }

        verify(exactly = 1) {
            Configuration.getFileFromAssets(any(), capture(slotFileName))
        }

        Truth.assertThat(exception).hasMessageThat().isEqualTo("Value appId of type java.lang.String cannot be converted to JSONObject")
    }

    @Test
    public fun createFromJsonCorrect() {
        val contextMockk = mockk<Context>()
        every { contextMockk.packageName } returns TestDataCore.validPackageName
        every { contextMockk.cacheDir } returns TestDataCore.validCacheDir
        every { contextMockk.applicationContext } returns contextMockk

        mockkObject(Configuration)

        every { Configuration.getVersionsFromAssets(any()) } returns TestDataCore.validConfigurationAssets

        val configuration = Configuration.createFromJson(contextMockk, TestDataCore.validConfigurationJson, product)

        Truth.assertThat(configuration.appId).isEqualTo(TestDataCore.validAppId)
        Truth.assertThat(configuration.env).isEqualTo(TestDataCore.validEnv + ".")
        Truth.assertThat(configuration.region).isEqualTo(TestDataCore.validRegion)
        Truth.assertThat(configuration.redirectUrl).isEqualTo(TestDataCore.validRedirectUrl)
        Truth.assertThat(configuration.version).isEqualTo(TestDataCore.validVersion)
        Truth.assertThat(configuration.userAgent).isEqualTo(TestDataCore.validUserAgent)
        Truth.assertThat(configuration.packageName).isEqualTo(TestDataCore.validPackageName)
        Truth.assertThat(configuration.getRedirectUri()).isEqualTo(TestDataCore.validRedirectUrl)
    }

    @Test
    public fun createFromJsonBadJson() {
        val contextMockk = mockk<Context>()
        every { contextMockk.packageName } returns TestDataCore.validPackageName

        mockkObject(Configuration)

        val babJson = """ "app_Id": "firebase", "redirectUrl": "com.ownid.demo:/android", "enableLogging": true """

        val exception = assertThrows(JSONException::class.java) {
            Configuration.createFromJson(contextMockk, babJson, product)
        }

        Truth.assertThat(exception).hasMessageThat().isEqualTo("Value app_Id of type java.lang.String cannot be converted to JSONObject")
    }

    @Test
    public fun configurationCreateCorrect() {
        val configuration = Configuration(
            TestDataCore.validAppId,
            TestDataCore.validEnv + ".",
            TestDataCore.validRegion,
            TestDataCore.validRedirectUrl,
            TestDataCore.validVersion,
            TestDataCore.validUserAgent,
            TestDataCore.validPackageName,
            TestDataCore.validHashSet
        )

        Truth.assertThat(configuration.appId).isEqualTo(TestDataCore.validAppId)
        Truth.assertThat(configuration.env).isEqualTo(TestDataCore.validEnv + ".")
        Truth.assertThat(configuration.region).isEqualTo(TestDataCore.validRegion)
        Truth.assertThat(configuration.redirectUrl).isEqualTo(TestDataCore.validRedirectUrl)
        Truth.assertThat(configuration.version).isEqualTo(TestDataCore.validVersion)
        Truth.assertThat(configuration.userAgent).isEqualTo(TestDataCore.validUserAgent)
        Truth.assertThat(configuration.packageName).isEqualTo(TestDataCore.validPackageName)
        Truth.assertThat(configuration.getRedirectUri()).isEqualTo(TestDataCore.validRedirectUrl)
    }

    @Test
    public fun configurationCreateCorrectEU() {
        val configuration = Configuration(
            TestDataCore.validAppId,
            TestDataCore.validEnv + ".",
            "-eu",
            TestDataCore.validRedirectUrl,
            TestDataCore.validVersion,
            TestDataCore.validUserAgent,
            TestDataCore.validPackageName,
            TestDataCore.validHashSet
        )

        Truth.assertThat(configuration.appId).isEqualTo(TestDataCore.validAppId)
        Truth.assertThat(configuration.env).isEqualTo(TestDataCore.validEnv + ".")
        Truth.assertThat(configuration.region).isEqualTo("-eu")
        Truth.assertThat(configuration.redirectUrl).isEqualTo(TestDataCore.validRedirectUrl)
        Truth.assertThat(configuration.version).isEqualTo(TestDataCore.validVersion)
        Truth.assertThat(configuration.userAgent).isEqualTo(TestDataCore.validUserAgent)
        Truth.assertThat(configuration.packageName).isEqualTo(TestDataCore.validPackageName)
        Truth.assertThat(configuration.getRedirectUri()).isEqualTo(TestDataCore.validRedirectUrl)
    }

    @Test
    public fun configurationCreateBadAppIdKey() {
        val contextMockk = mockk<Context>()
        every { contextMockk.packageName } returns TestDataCore.validPackageName
        every { contextMockk.cacheDir } returns TestDataCore.validCacheDir
        every { contextMockk.applicationContext } returns contextMockk

        mockkObject(Configuration)

        every { Configuration.getVersionsFromAssets(any()) } returns TestDataCore.validConfigurationAssets

        val exception = assertThrows(JSONException::class.java) {
            Configuration.createFromJson(contextMockk, """{ "app_Id": "ybmrs2pxdeazta" }""", product)
        }

        Truth.assertThat(exception).hasMessageThat().isEqualTo("No value for appId")
    }

    @Test
    public fun configurationCreateBadAppIdValue() {
        val contextMockk = mockk<Context>()
        every { contextMockk.packageName } returns TestDataCore.validPackageName
        every { contextMockk.cacheDir } returns TestDataCore.validCacheDir
        every { contextMockk.applicationContext } returns contextMockk

        mockkObject(Configuration)

        every { Configuration.getVersionsFromAssets(any()) } returns TestDataCore.validConfigurationAssets

        val exception = assertThrows(IllegalArgumentException::class.java) {
            Configuration.createFromJson(contextMockk, """{ "appId": "ybmrs2pxdeazta_-" }""", product)
        }

        Truth.assertThat(exception).hasMessageThat().isEqualTo("Wrong 'appId' value:'ybmrs2pxdeazta_-'")
    }

    @Test
    public fun configurationCreateBadEnvKey() {
        val contextMockk = mockk<Context>()
        every { contextMockk.packageName } returns TestDataCore.validPackageName
        every { contextMockk.cacheDir } returns TestDataCore.validCacheDir
        every { contextMockk.applicationContext } returns contextMockk

        mockkObject(Configuration)

        every { Configuration.getVersionsFromAssets(any()) } returns TestDataCore.validConfigurationAssets

        val configuration = Configuration.createFromJson(contextMockk, """{ "appId": "ybmrs2pxdeazta", "envi": "dev" }""", product)

        Truth.assertThat(configuration.appId).isEqualTo(TestDataCore.validAppId)
        Truth.assertThat(configuration.env).isEqualTo("")
        Truth.assertThat(configuration.region).isEqualTo("")
    }

    @Test
    public fun configurationCreateBadEnvValue() {
        val contextMockk = mockk<Context>()
        every { contextMockk.packageName } returns TestDataCore.validPackageName
        every { contextMockk.cacheDir } returns TestDataCore.validCacheDir
        every { contextMockk.applicationContext } returns contextMockk

        mockkObject(Configuration)

        every { Configuration.getVersionsFromAssets(any()) } returns TestDataCore.validConfigurationAssets

        val configuration = Configuration.createFromJson(contextMockk, """{ "appId": "ybmrs2pxdeazta", "env": "devs" }""", product)

        Truth.assertThat(configuration.appId).isEqualTo(TestDataCore.validAppId)
        Truth.assertThat(configuration.env).isEqualTo("")
        Truth.assertThat(configuration.region).isEqualTo("")
    }

    @Test
    public fun configurationCreateBadRedirectUrlKey() {
        val contextMockk = mockk<Context>()
        every { contextMockk.packageName } returns TestDataCore.validPackageName
        every { contextMockk.cacheDir } returns TestDataCore.validCacheDir
        every { contextMockk.applicationContext } returns contextMockk

        mockkObject(Configuration)

        every { Configuration.getVersionsFromAssets(any()) } returns TestDataCore.validConfigurationAssets

        val configuration = Configuration.createFromJson(contextMockk, """{ "appId": "ybmrs2pxdeazta", "redirectUri": "dev" }""", product)

        Truth.assertThat(configuration.appId).isEqualTo(TestDataCore.validAppId)
        Truth.assertThat(configuration.env).isEqualTo("")
        Truth.assertThat(configuration.region).isEqualTo("")
        Truth.assertThat(configuration.redirectUrl).isEqualTo("com.ownid.demo.firebase://ownid/redirect/")
    }

    @Test
    public fun configurationCreateBadRedirectUrlValue() {
        val contextMockk = mockk<Context>()
        every { contextMockk.packageName } returns TestDataCore.validPackageName
        every { contextMockk.cacheDir } returns TestDataCore.validCacheDir
        every { contextMockk.applicationContext } returns contextMockk

        mockkObject(Configuration)

        every { Configuration.getVersionsFromAssets(any()) } returns TestDataCore.validConfigurationAssets

        val exception = assertThrows(IllegalArgumentException::class.java) {
            Configuration.createFromJson(contextMockk, """{ "appId": "ybmrs2pxdeazta", "redirectUrl": "/dev/df" }""", product)
        }

        Truth.assertThat(exception).hasMessageThat().isEqualTo("Redirect Url must contain an explicit scheme")
    }

    @Test
    public fun configurationCreatePackageNameNotUrlValue() {
        val contextMockk = mockk<Context>()
        every { contextMockk.packageName } returns "package.name.in_valid.url"
        every { contextMockk.cacheDir } returns TestDataCore.validCacheDir
        every { contextMockk.applicationContext } returns contextMockk

        mockkObject(Configuration)

        every { Configuration.getVersionsFromAssets(any()) } returns TestDataCore.validConfigurationAssets

        val configuration = Configuration.createFromJson(contextMockk, """{ "appId": "ybmrs2pxdeazta" }""", product)

        Truth.assertThat(configuration.appId).isEqualTo(TestDataCore.validAppId)
        Truth.assertThat(configuration.env).isEqualTo("")
        Truth.assertThat(configuration.region).isEqualTo("")
        Truth.assertThat(configuration.redirectUrl).isEqualTo("")
    }

//    @Test
//    public fun getCertificateHashesValid() {
//        val contextMockk = mockk<Context>()
//        val packageManagerMockk = mockk<PackageManager>()
//        val packageInfoMockk = mockk<PackageInfo>()
//        val signingInfoMockk = mockk<SigningInfo>()
//        val signatureMockk = mockk<Signature>()
//
//        every { contextMockk.packageName } returns TestDataCore.validPackageName
//        every { contextMockk.cacheDir } returns TestDataCore.validCacheDir
//        every { contextMockk.applicationContext } returns contextMockk
//        every { contextMockk.packageManager } returns packageManagerMockk
//        every { packageManagerMockk.getPackageInfo(contextMockk.packageName, PackageManager.GET_SIGNING_CERTIFICATES) } returns packageInfoMockk
//        every { packageInfoMockk.signingInfo } returns signingInfoMockk
//        every { signingInfoMockk.hasMultipleSigners() } returns false
//        every { signingInfoMockk.signingCertificateHistory } returns arrayOf(signatureMockk)
//        every { signatureMockk.toByteArray() } returns "TMKsF24L-q81zY1xzYD4h-mc_I44eiKRN8qrWeUPTY4".fromBase64UrlSafeNoPadding()
//
//
//        mockkObject(Configuration)
//
//        every { Configuration.getVersionsFromAssets(any()) } returns TestDataCore.validConfigurationAssets
//
//        val configuration = Configuration.createFromJson(contextMockk, TestDataCore.validConfigurationJson, product)
//
//        Truth.assertThat(configuration.certificateHashes).isEqualTo(TestDataCore.validPackageName)
//    }
}