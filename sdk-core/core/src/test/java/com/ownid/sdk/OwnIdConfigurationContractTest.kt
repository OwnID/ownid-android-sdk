package com.ownid.sdk

import android.content.ContextWrapper
import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.capability.impl.SerializationImpl
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertThrows
import org.junit.Test

// Covers: CFG-010, CFG-030, CFG-040, CFG-050, CFG-080, CFG-090, CFG-100, CFG-230, CFG-240, CFG-250, CFG-260, STORAGE-020
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
    fun rootUrlPortBoundsMatchConfigurationContract() {
        val configuration: OwnIdConfiguration = OwnIdConfigurationImpl(
            appId = "app123",
            rootUrl = "https://server.example.com:65535/base"
        )

        assertThat(configuration.rootUrl).isEqualTo("https://server.example.com:65535/base")
        listOf(0, 65536).forEach { port ->
            assertThrows(IllegalArgumentException::class.java) {
                OwnIdConfigurationImpl(appId = "app123", rootUrl = "https://server.example.com:$port/base")
            }
        }
    }

    @Test
    fun allConfigurationValuesRejectRawBackslashesBeforeRootUrlParsing() {
        listOf(
            "https://trusted.example\\@attacker.example/path",
            "https://trusted.example/path\\segment",
            "https://trusted.example/path?value=bad\\query",
            "https://trusted.example/path#bad\\fragment"
        ).forEach { invalidRootUrl ->
            val programmaticError = assertThrows(IllegalArgumentException::class.java) {
                OwnIdConfigurationImpl(appId = "app123", rootUrl = invalidRootUrl)
            }
            assertThat(programmaticError).hasMessageThat().contains("backslashes")

            val jsonRootUrl = invalidRootUrl.replace("\\", "\\\\")
            listOf("rootUrl", "rootURL").forEach { rootKey ->
                val jsonError = assertThrows(IllegalArgumentException::class.java) {
                    jsonBuilder("""{"appId":"app123","$rootKey":"$jsonRootUrl"}""").build()
                }
                assertThat(jsonError).hasMessageThat().contains("backslashes")
            }
        }
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
    fun jsonBuilderUsesCanonicalConfigurationKeysBeforeCompatibilityAliases() {
        val canonical = jsonBuilder(
            """{"appId":"123","rootUrl":"https://canonical.example/path"}"""
        ).build()
        val aliases = jsonBuilder(
            """{"appID":"Alias456","rootURL":"https://alias.example/path"}"""
        ).build()
        val both = jsonBuilder(
            """{"appId":"Canonical789","appID":"Alias789","rootUrl":"https://canonical.example/root","rootURL":"https://alias.example/root"}"""
        ).build()
        val canonicalNullRoot = jsonBuilder(
            """{"appId":"App123","rootUrl":null,"rootURL":"https://alias.example/root"}"""
        ).build()
        val canonicalNullAppId = jsonBuilder(
            """{"appId":null,"appID":"Alias123"}"""
        ).build()

        assertThat(canonical.appId).isEqualTo("123")
        assertThat(canonical.rootUrl).isEqualTo("https://canonical.example/path")
        assertThat(aliases.appId).isEqualTo("Alias456")
        assertThat(aliases.rootUrl).isEqualTo("https://alias.example/path")
        assertThat(both.appId).isEqualTo("Canonical789")
        assertThat(both.rootUrl).isEqualTo("https://canonical.example/root")
        assertThat(canonicalNullRoot.rootUrl).isEqualTo("https://alias.example/root")
        assertThat(canonicalNullAppId.appId).isEqualTo("Alias123")

        assertThrows(IllegalArgumentException::class.java) {
            jsonBuilder(
                """{"appId":"App123","rootUrl":"http://invalid.example/root","rootURL":"https://alias.example/root"}"""
            ).build()
        }
        assertThrows(IllegalArgumentException::class.java) {
            jsonBuilder("""{"appId":"Invalid-App","appID":"ValidAlias123"}""").build()
        }
        assertThrows(IllegalArgumentException::class.java) {
            jsonBuilder(
                """{"appId":"App123","rootUrl":"https://canonical.example\\path","rootURL":"https://alias.example/root"}"""
            ).build()
        }
    }

    @Test
    fun jsonBuilderRejectsEverySelectedNonStringAppId() {
        listOf("7", "true", "{}", "[]").forEach { invalidAppId ->
            listOf(
                """{"appId":$invalidAppId,"appID":"ValidAlias123"}""",
                """{"appID":$invalidAppId}""",
                """{"appId":null,"appID":$invalidAppId}"""
            ).forEach { json ->
                val error = assertThrows(SerializationException::class.java) {
                    jsonBuilder(json).build()
                }

                assertThat(error).hasMessageThat().contains("appId")
            }
        }
    }

    @Test
    fun jsonBuilderTreatsTopLevelNullValuesAsOmitted() {
        val builder = jsonBuilder(
            """{"appId":"App123","env":null,"region":null,"rootUrl":null,"rootURL":null,"languages":null}"""
        )

        val configuration = builder.build()

        assertThat(configuration.env).isEqualTo(OwnIdConfiguration.Env.Prod)
        assertThat(configuration.region).isEqualTo(OwnIdConfiguration.Region.US)
        assertThat(configuration.rootUrl).isNull()
        assertThat(builder.languages).isNull()

        val missingAppIdError = assertThrows(SerializationException::class.java) {
            jsonBuilder("{}").build()
        }
        listOf(
            """{"appId":null}""",
            """{"appID":null}""",
            """{"appId":null,"appID":null}"""
        ).forEach { json ->
            val nullAppIdError = assertThrows(SerializationException::class.java) {
                jsonBuilder(json).build()
            }

            assertThat(nullAppIdError).isInstanceOf(missingAppIdError::class.java)
        }
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

        val emptyLanguagesBuilder = jsonBuilder("""{"appId":"app123","languages":[]}""")
        emptyLanguagesBuilder.build()
        assertThat(emptyLanguagesBuilder.languages).isEmpty()

        val omittedLanguagesBuilder = jsonBuilder("""{"appId":"app123"}""")
        omittedLanguagesBuilder.build()
        assertThat(omittedLanguagesBuilder.languages).isNull()
    }

    @Test
    fun jsonBuilderRejectsNonStringLanguageElements() {
        listOf("7", "true", "null", "{}", "[]").forEach { invalidElement ->
            val error = assertThrows(SerializationException::class.java) {
                jsonBuilder("""{"appId":"app123","languages":["en-US",$invalidElement]}""").build()
            }

            assertThat(error).hasMessageThat().contains("languages[1]")
        }
    }

    @Test
    fun jsonBuilderRejectsNonArrayLanguages() {
        listOf("\"en-US\"", "7", "true", "{}").forEach { invalidLanguages ->
            val error = assertThrows(SerializationException::class.java) {
                jsonBuilder("""{"appId":"app123","languages":$invalidLanguages}""").build()
            }

            assertThat(error).hasMessageThat().contains("array of strings")
        }
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
