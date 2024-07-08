package com.ownis.sdk

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import com.gigya.android.sdk.Gigya
import com.gigya.android.sdk.account.models.GigyaAccount
import com.google.common.truth.Truth
import com.ownid.sdk.Configuration
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnId
import com.ownid.sdk.OwnIdCoreImpl
import com.ownid.sdk.OwnIdGigya
import com.ownid.sdk.OwnIdGigyaFactory
import com.ownid.sdk.internal.OwnIdGigyaIntegration
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(InternalOwnIdAPI::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
public class OwnIdGigyaFactoryTest {

    private val contextMockk = mockk<Context>()
    private val resourcesMockk = mockk<Resources>()
    private val configurationMockk = mockk<android.content.res.Configuration>()
    private val sharedPreferencesMockk = mockk<SharedPreferences>()
    private val ownIdCoreMockk = mockk<OwnIdCoreImpl>()
    private val gigyaMockk = mockk<Gigya<GigyaAccount>>()

    @Before
    public fun setUp() {
        mockkStatic(OwnId::class)
        mockkStatic(OwnIdGigya::class)
        mockkObject(Configuration)
        mockkObject(OwnIdCoreImpl)
        every { contextMockk.cacheDir } returns TestDataGigya.validCacheDir
        every { contextMockk.resources } returns resourcesMockk
        every { resourcesMockk.configuration } returns configurationMockk
        every { contextMockk.getSharedPreferences(any(), any()) } returns sharedPreferencesMockk
        every { OwnIdCoreImpl.createInstance(any(), any(), any()) } returns ownIdCoreMockk
        every { ownIdCoreMockk.instanceName } returns TestDataGigya.validInstanceName
        every { ownIdCoreMockk.configuration } returns TestDataGigya.validServerConfig
    }

    @Test
    public fun getDefault() {
        val ownIdGigyaMockk = mockk<OwnIdGigya>()

        every { OwnId.getInstanceOrThrow<OwnIdGigya>(OwnIdGigya.DEFAULT_INSTANCE_NAME) } returns ownIdGigyaMockk

        val ownIdGigya = OwnIdGigyaFactory.getDefault()

        verify(exactly = 1) {
            OwnIdGigyaFactory.getDefault()
            OwnIdGigyaFactory.getInstance(OwnIdGigya.DEFAULT_INSTANCE_NAME)
        }

        Truth.assertThat(ownIdGigya).isEqualTo(ownIdGigyaMockk)
    }

    @Test
    public fun getInstance() {
        val ownIdGigyaMockk = mockk<OwnIdGigya>()

        every { OwnId.getInstanceOrThrow<OwnIdGigya>(TestDataGigya.validInstanceName) } returns ownIdGigyaMockk

        val ownIdGigya = OwnIdGigyaFactory.getInstance(TestDataGigya.validInstanceName)

        verify(exactly = 1) {
            OwnIdGigyaFactory.getInstance(TestDataGigya.validInstanceName)
        }

        Truth.assertThat(ownIdGigya).isEqualTo(ownIdGigyaMockk)
    }

    @Test
    public fun createNewInstanceFull() {
        val configurationAssetFileName = "someName.json"

        every { OwnId.getInstanceOrNull<OwnIdGigya>(TestDataGigya.validInstanceName) } returns null

        val slotConfigurationFileName = slot<String>()
        every {
            Configuration.createFromAssetFile(contextMockk, capture(slotConfigurationFileName), any())
        } returns TestDataGigya.validServerConfig

        val ownIdGigya = OwnIdGigyaFactory.createInstanceFromFile(
            contextMockk, configurationAssetFileName, gigyaMockk, TestDataGigya.validInstanceName
        )

        verify(atLeast = 1) {
            OwnId.getInstanceOrNull<OwnIdGigya>(TestDataGigya.validInstanceName)
            Configuration.createFromAssetFile(contextMockk, any(), any())
            OwnIdGigyaIntegration(ownIdCoreMockk, gigyaMockk)
        }

        Truth.assertThat(ownIdGigya).isEqualTo(OwnIdGigyaFactory.getInstance(TestDataGigya.validInstanceName))
        Truth.assertThat(slotConfigurationFileName.captured).isEqualTo(configurationAssetFileName)
    }

    @Test
    public fun createNewInstanceJsonFull() {
        every { OwnId.getInstanceOrNull<OwnIdGigya>(TestDataGigya.validInstanceName) } returns null
        val slotConfigurationJson = slot<String>()
        every {
            Configuration.createFromJson(contextMockk, capture(slotConfigurationJson), any())
        } returns TestDataGigya.validServerConfig

        val ownIdGigya = OwnIdGigyaFactory.createInstanceFromJson(
            contextMockk, TestDataGigya.validJsonConfig, gigyaMockk, TestDataGigya.validInstanceName
        )

        verify(atLeast = 1) {
            OwnId.getInstanceOrNull<OwnIdGigya>(TestDataGigya.validInstanceName)
            OwnIdGigyaIntegration(ownIdCoreMockk, gigyaMockk)
        }

        Truth.assertThat(ownIdGigya).isEqualTo(OwnIdGigyaFactory.getInstance(TestDataGigya.validInstanceName))
        Truth.assertThat(slotConfigurationJson.captured).isEqualTo(TestDataGigya.validJsonConfig)
    }

    @Test
    public fun createNewInstanceDefaultName() {
        val configurationAssetFileName = "someName.json"

        every { OwnId.getInstanceOrNull<OwnIdGigya>(OwnIdGigya.DEFAULT_INSTANCE_NAME) } returns null
        val resourcesMockk = mockk<Resources>()
        val configurationMockk = mockk<android.content.res.Configuration>()
        every { contextMockk.resources } returns resourcesMockk
        every { resourcesMockk.configuration } returns configurationMockk
        val slotConfigurationFileName = slot<String>()
        every {
            Configuration.createFromAssetFile(contextMockk, capture(slotConfigurationFileName), any())
        } returns TestDataGigya.validServerConfig

        val ownIdGigya = OwnIdGigyaFactory.createInstanceFromFile(contextMockk, configurationAssetFileName, gigyaMockk)

        verify(atLeast = 1) {
            OwnId.getInstanceOrNull<OwnIdGigya>(OwnIdGigya.DEFAULT_INSTANCE_NAME)
            Configuration.createFromAssetFile(contextMockk, any(), any())
            OwnIdGigyaIntegration(ownIdCoreMockk, gigyaMockk)
        }

        Truth.assertThat(ownIdGigya).isEqualTo(OwnIdGigyaFactory.getInstance(OwnIdGigya.DEFAULT_INSTANCE_NAME))
    }

    @Test
    public fun createNewInstanceJsonDefaultName() {
        every { OwnId.getInstanceOrNull<OwnIdGigya>(OwnIdGigya.DEFAULT_INSTANCE_NAME) } returns null
        val slotConfigurationJson = slot<String>()
        every {
            Configuration.createFromJson(contextMockk, capture(slotConfigurationJson), any())
        } returns TestDataGigya.validServerConfig

        val ownIdGigya = OwnIdGigyaFactory.createInstanceFromJson(contextMockk, TestDataGigya.validJsonConfig, gigyaMockk)

        verify(atLeast = 1) {
            OwnId.getInstanceOrNull<OwnIdGigya>(OwnIdGigya.DEFAULT_INSTANCE_NAME)
            OwnIdGigyaIntegration(ownIdCoreMockk, gigyaMockk)
        }

        Truth.assertThat(ownIdGigya).isEqualTo(OwnIdGigyaFactory.getInstance(OwnIdGigya.DEFAULT_INSTANCE_NAME))
        Truth.assertThat(slotConfigurationJson.captured).isEqualTo(TestDataGigya.validJsonConfig)
    }

    @Test
    public fun createNewInstanceDefaultFileDefaultName() {
        every { OwnId.getInstanceOrNull<OwnIdGigya>(OwnIdGigya.DEFAULT_INSTANCE_NAME) } returns null
        val slotConfigurationFileName = slot<String>()
        every {
            Configuration.createFromAssetFile(contextMockk, capture(slotConfigurationFileName), any())
        } returns TestDataGigya.validServerConfig

        val ownIdGigya = OwnIdGigyaFactory.createInstanceFromFile(contextMockk, OwnIdGigya.DEFAULT_CONFIGURATION_FILE_NAME, gigyaMockk)

        verify(atLeast = 1) {
            OwnId.getInstanceOrNull<OwnIdGigya>(OwnIdGigya.DEFAULT_INSTANCE_NAME)
            Configuration.createFromAssetFile(contextMockk, any(), any())
            OwnIdGigyaIntegration(ownIdCoreMockk, gigyaMockk)
        }

        Truth.assertThat(ownIdGigya).isEqualTo(OwnIdGigyaFactory.getInstance(OwnIdGigya.DEFAULT_INSTANCE_NAME))
        Truth.assertThat(slotConfigurationFileName.captured).isEqualTo(OwnIdGigya.DEFAULT_CONFIGURATION_FILE_NAME)
    }

    @Test
    public fun createNewInstanceAll() {
        every { OwnId.getInstanceOrNull<OwnIdGigya>(OwnIdGigya.DEFAULT_INSTANCE_NAME) } returns null
        val slotConfigurationFileName = slot<String>()
        every {
            Configuration.createFromAssetFile(contextMockk, capture(slotConfigurationFileName), any())
        } returns TestDataGigya.validServerConfig

        val ownIdGigya = OwnIdGigyaFactory.createInstanceFromFile(contextMockk, OwnIdGigya.DEFAULT_CONFIGURATION_FILE_NAME, gigyaMockk)

        verify(atLeast = 1) {
            OwnId.getInstanceOrNull<OwnIdGigya>(OwnIdGigya.DEFAULT_INSTANCE_NAME)
            Configuration.createFromAssetFile(contextMockk, any(), any())
            OwnIdGigyaIntegration(ownIdCoreMockk, gigyaMockk)
        }

        Truth.assertThat(ownIdGigya).isEqualTo(OwnIdGigyaFactory.getInstance(OwnIdGigya.DEFAULT_INSTANCE_NAME))
        Truth.assertThat(slotConfigurationFileName.captured).isEqualTo(OwnIdGigya.DEFAULT_CONFIGURATION_FILE_NAME)
    }

    @Test
    public fun createNewInstanceWhenExists() {
        val configurationAssetFileName = "someName.json"

        every { OwnId.getInstanceOrNull<OwnIdGigya>(TestDataGigya.validInstanceName) } returns null
        val slotConfigurationFileName = slot<String>()
        every {
            Configuration.createFromAssetFile(contextMockk, capture(slotConfigurationFileName), any())
        } returns TestDataGigya.validServerConfig

        val ownIdGigyaFirst = OwnIdGigyaFactory.createInstanceFromFile(
            contextMockk, configurationAssetFileName, gigyaMockk, TestDataGigya.validInstanceName
        )
        every { OwnId.getInstanceOrNull<OwnIdGigya>(TestDataGigya.validInstanceName) } returns ownIdGigyaFirst

        val ownIdGigyaSecond = OwnIdGigyaFactory.createInstanceFromFile(
            contextMockk, configurationAssetFileName, gigyaMockk, TestDataGigya.validInstanceName
        )

        Truth.assertThat(ownIdGigyaFirst).isSameInstanceAs(ownIdGigyaSecond)
    }

    @Test
    public fun createNewInstanceJsonWhenExists() {
        every { OwnId.getInstanceOrNull<OwnIdGigya>(TestDataGigya.validInstanceName) } returns null
        val slotConfigurationJson = slot<String>()
        every {
            Configuration.createFromJson(contextMockk, capture(slotConfigurationJson), any())
        } returns TestDataGigya.validServerConfig

        val ownIdGigyaFirst = OwnIdGigyaFactory.createInstanceFromJson(
            contextMockk, TestDataGigya.validJsonConfig, gigyaMockk, TestDataGigya.validInstanceName
        )
        every { OwnId.getInstanceOrNull<OwnIdGigya>(TestDataGigya.validInstanceName) } returns ownIdGigyaFirst

        val ownIdGigyaSecond = OwnIdGigyaFactory.createInstanceFromJson(
            contextMockk, TestDataGigya.validJsonConfig, gigyaMockk, TestDataGigya.validInstanceName
        )

        Truth.assertThat(ownIdGigyaFirst).isSameInstanceAs(ownIdGigyaSecond)
    }
}