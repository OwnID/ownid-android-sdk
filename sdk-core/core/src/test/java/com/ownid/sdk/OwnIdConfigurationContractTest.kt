package com.ownid.sdk

import android.content.ContextWrapper
import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.capability.impl.SerializationImpl
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertThrows
import org.junit.Test

// Covers: CFG-010, CFG-030, CFG-040, CFG-050, CFG-080, CFG-090, CFG-100, STORAGE-020
internal class OwnIdConfigurationContractTest {

    private val serialization = SerializationImpl()

    @Test
    fun configurationDefaultsAreStable() {
        val configuration: OwnIdConfiguration = OwnIdConfigurationImpl(appId = "app123")

        assertThat(configuration.appId).isEqualTo("app123")
        assertThat(configuration.env).isEqualTo(OwnIdConfiguration.Env.Prod)
        assertThat(configuration.region).isEqualTo(OwnIdConfiguration.Region.US)
        assertThat(configuration.rootUrl).isNull()
    }

    @Test
    fun programmaticBuilderAcceptsNonfunctionalAndroidContextWithoutFrameworkCalls() {
        val builder = OwnIdConfigurationBuilder().apply {
            applicationContext = TestContext
            appId = "app123"
            env = OwnIdConfiguration.Env.Uat
            region = OwnIdConfiguration.Region.EU
            rootUrl = "https://server.example.com/base?debug=true#section"
        }

        val configuration = builder.build()

        assertThat(builder.applicationContext).isSameInstanceAs(TestContext)
        assertThat(configuration.appId).isEqualTo("app123")
        assertThat(configuration.env).isEqualTo(OwnIdConfiguration.Env.Uat)
        assertThat(configuration.region).isEqualTo(OwnIdConfiguration.Region.EU)
        assertThat(configuration.rootUrl).isEqualTo("https://server.example.com/base")
    }

    @Test
    fun rootUrlStripsQueryAndFragment() {
        val configuration: OwnIdConfiguration = OwnIdConfigurationImpl(
            appId = "app123",
            rootUrl = "https://server.example.com/base?debug=true#section"
        )

        assertThat(configuration.rootUrl).isEqualTo("https://server.example.com/base")
    }

    @Test
    fun programmaticConfigurationRejectsInvalidAppIdsAndNonHttpsRootUrls() {
        listOf("", "Tenant-1", "Tenant_1", "Tenant 1", "Tenant.example").forEach { invalidAppId ->
            assertThrows(IllegalArgumentException::class.java) {
                OwnIdConfigurationImpl(appId = invalidAppId)
            }
        }

        listOf("http://root.example.com", "ftp://root.example.com", "root.example.com/path").forEach { invalidRootUrl ->
            assertThrows(IllegalArgumentException::class.java) {
                OwnIdConfigurationImpl(appId = "app123", rootUrl = invalidRootUrl)
            }
        }
    }

    @Test
    fun jsonDecoderDecodesEnvAndRegionCaseInsensitively() {
        val mixedCaseConfiguration = decodeConfiguration(
            """
            {
              "appId": "app123",
              "env": "uAt",
              "region": "eU"
            }
            """.trimIndent()
        )

        assertThat(mixedCaseConfiguration.env).isEqualTo(OwnIdConfiguration.Env.Uat)
        assertThat(mixedCaseConfiguration.region).isEqualTo(OwnIdConfiguration.Region.EU)

        val lowercaseConfiguration = decodeConfiguration(
            """
            {
              "appId": "app123",
              "env": "prod",
              "region": "us"
            }
            """.trimIndent()
        )

        assertThat(lowercaseConfiguration.env).isEqualTo(OwnIdConfiguration.Env.Prod)
        assertThat(lowercaseConfiguration.region).isEqualTo(OwnIdConfiguration.Region.US)
    }

    @Test
    fun jsonDecoderAcceptsAppIdAndRootUrlAliases() {
        val camelCaseConfiguration = decodeConfiguration(
            """
            {
              "appId": "app123",
              "rootUrl": "https://server.example.com/path"
            }
            """.trimIndent()
        )
        val upperIdConfiguration = decodeConfiguration(
            """
            {
              "appID": "app456",
              "rootURL": "https://server.example.com/other"
            }
            """.trimIndent()
        )

        assertThat(camelCaseConfiguration.appId).isEqualTo("app123")
        assertThat(camelCaseConfiguration.rootUrl).isEqualTo("https://server.example.com/path")
        assertThat(upperIdConfiguration.appId).isEqualTo("app456")
        assertThat(upperIdConfiguration.rootUrl).isEqualTo("https://server.example.com/other")
    }

    @Test
    fun jsonBuilderExtractsInternalDevEnvironmentMetadata() {
        val configuration = jsonBuilder(
            """
            {
              "appID": "app123",
              "env": "dev",
              "region": "eu",
              "rootURL": "https://server.example.com/path?debug=true#section"
            }
            """.trimIndent()
        ).build()

        assertThat(configuration.appId).isEqualTo("app123")
        assertThat(configuration.env).isEqualTo(OwnIdConfiguration.Env.Prod)
        assertThat(configuration.region).isEqualTo(OwnIdConfiguration.Region.EU)
        assertThat(configuration.rootUrl).isEqualTo("https://server.example.com/path")
        assertThat(configuration.env()).isEqualTo(InternalEnv.Dev)
    }

    @Test
    fun jsonBuilderExtractsLanguagesAsInitializationMetadata() {
        val builder = jsonBuilder(
            """
            {
              "appId": "app123",
              "rootUrl": "https://server.example.com/base?debug=true#section",
              "languages": ["en-US", "fr-FR"]
            }
            """.trimIndent()
        )

        val configuration = builder.build()

        assertThat(configuration.rootUrl).isEqualTo("https://server.example.com/base")
        assertThat(builder.languages).containsExactly("en-US", "fr-FR").inOrder()
    }

    @Test
    fun jsonDecoderRejectsMissingAppId() {
        assertThrows(SerializationException::class.java) {
            decodeConfiguration(
                """
                {
                  "env": "prod",
                  "region": "us",
                  "rootUrl": "https://server.example.com/path"
                }
                """.trimIndent()
            )
        }
    }

    @Test
    fun jsonDecoderNormalizesRootUrl() {
        val configuration = decodeConfiguration(
            """
            {
              "appId": "app123",
              "rootURL": "https://SERVER.EXAMPLE.COM?debug=true#section"
            }
            """.trimIndent()
        )

        assertThat(configuration.rootUrl).isEqualTo("https://server.example.com/")
    }

    @Test
    fun storageNameIncludesEnvironmentRegionAndAppId() {
        assertThat(OwnIdConfigurationImpl(appId = "app123").storageFileName()).isEqualTo("prod_us_app123")
        assertThat(
            OwnIdConfigurationImpl(
                appId = "app456",
                env = OwnIdConfiguration.Env.Uat,
                region = OwnIdConfiguration.Region.EU
            ).storageFileName()
        ).isEqualTo("uat_eu_app456")

        val devConfiguration = OwnIdConfigurationImpl(
            appId = "app789",
            region = OwnIdConfiguration.Region.EU
        ).apply {
            internalEnv = InternalEnv.Dev
        }

        assertThat(devConfiguration.storageFileName()).isEqualTo("dev_eu_app789")
    }

    @Test
    fun invalidAppIdsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            OwnIdConfigurationImpl(appId = "")
        }

        assertThrows(IllegalArgumentException::class.java) {
            OwnIdConfigurationImpl(appId = "app-123")
        }
    }

    @Test
    fun nonHttpsRootUrlIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            OwnIdConfigurationImpl(appId = "app123", rootUrl = "http://server.example.com")
        }
    }

    private fun decodeConfiguration(json: String): OwnIdConfiguration =
        serialization.decodeFromString(OwnIdConfigurationImpl.serializer(), json)

    private fun jsonBuilder(json: String): OwnIdJsonConfigurationBuilder =
        OwnIdJsonConfigurationBuilder(serialization).apply {
            applicationContext = TestContext
            this.json = json
        }

    private object TestContext : ContextWrapper(null)
}
