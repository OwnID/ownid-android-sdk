package com.ownid.sdk.api.impl

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.InternalEnv
import com.ownid.sdk.OwnIdConfiguration
import com.ownid.sdk.OwnIdConfigurationImpl
import org.junit.Test

// Covers: CAP-010
internal class ApiBaseUrlImplContractTest {

    @Test
    fun defaultBaseUrlUsesAppIdEnvironmentAndRegion() {
        assertThat(baseUrl(appId = "produs", env = OwnIdConfiguration.Env.Prod, region = OwnIdConfiguration.Region.US))
            .isEqualTo("https://produs.server.ownid.com/api")
        assertThat(baseUrl(appId = "prodeu", env = OwnIdConfiguration.Env.Prod, region = OwnIdConfiguration.Region.EU))
            .isEqualTo("https://prodeu.server.ownid-eu.com/api")
        assertThat(baseUrl(appId = "uatus", env = OwnIdConfiguration.Env.Uat, region = OwnIdConfiguration.Region.US))
            .isEqualTo("https://uatus.server.uat.ownid.com/api")
        assertThat(baseUrl(appId = "uateu", env = OwnIdConfiguration.Env.Uat, region = OwnIdConfiguration.Region.EU))
            .isEqualTo("https://uateu.server.uat.ownid-eu.com/api")
    }

    @Test
    fun internalDevBaseUrlUsesDevEnvironmentPrefixAndRegion() {
        assertThat(baseUrl(appId = "devus", internalEnv = InternalEnv.Dev, region = OwnIdConfiguration.Region.US))
            .isEqualTo("https://devus.server.dev.ownid.com/api")
        assertThat(baseUrl(appId = "deveu", internalEnv = InternalEnv.Dev, region = OwnIdConfiguration.Region.EU))
            .isEqualTo("https://deveu.server.dev.ownid-eu.com/api")
    }

    @Test
    fun rootUrlBaseUrlAppendsApiPathAfterConfigurationNormalization() {
        val configuration = OwnIdConfigurationImpl(
            appId = "ignoredApp",
            env = OwnIdConfiguration.Env.Uat,
            region = OwnIdConfiguration.Region.EU,
            rootUrl = "https://SERVER.EXAMPLE.COM/custom/root?debug=true#section"
        )

        assertThat(ApiBaseUrlImpl(configuration).getBaseUrlOrThrow().toString())
            .isEqualTo("https://server.example.com/custom/root/api")
    }

    private fun baseUrl(
        appId: String,
        env: OwnIdConfiguration.Env = OwnIdConfiguration.Env.Prod,
        internalEnv: InternalEnv? = null,
        region: OwnIdConfiguration.Region
    ): String {
        val configuration = OwnIdConfigurationImpl(appId = appId, env = env, region = region).apply {
            this.internalEnv = internalEnv
        }

        return ApiBaseUrlImpl(configuration).getBaseUrlOrThrow().toString()
    }
}
