@file:Suppress("unused")

package com.ownid.sdk.logger

import com.ownid.sdk.OwnIdConfiguration
import com.ownid.sdk.capability.AppConfigProvider
import com.ownid.sdk.capability.LocalInfo
import com.ownid.sdk.capability.Network
import com.ownid.sdk.model.AppConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request

internal fun diagnosticsAppConfig(logLevel: AppConfig.LogLevel): AppConfig =
    AppConfig(loginIdConfig = AppConfig.DEFAULT.loginIdConfig, logLevel = logLevel)

internal fun diagnosticsConfiguration(rootUrl: String): OwnIdConfiguration = object : OwnIdConfiguration {
    override val appId: String = "app123"
    override val env: OwnIdConfiguration.Env = OwnIdConfiguration.Env.Prod
    override val region: OwnIdConfiguration.Region = OwnIdConfiguration.Region.US
    override val rootUrl: String = rootUrl
}

internal fun diagnosticsLocalInfo(
    modules: List<Pair<String, String>> = listOf("Core" to "1.2.3"),
    isFingerprintHardwarePresent: Boolean? = false,
    isFaceHardwarePresent: Boolean? = true,
    isIrisHardwarePresent: Boolean? = false,
    isStrongBiometricEnabled: Boolean? = true
): LocalInfo = object : LocalInfo {
    override val modules: List<Pair<String, String>> = modules
    override val packageName: String = "com.example.diagnostics"
    override val appVersion: String = "42.7"
    override val userAgent: String = "OwnID-Test/1.0"
    override val correlationId: String = "correlation-1"
    override val isDebuggable: Boolean = false
    override val isSystemFidoCapable: Boolean = true
    override val isDeviceSecured: Boolean = true
    override val isFingerprintHardwarePresent: Boolean? = isFingerprintHardwarePresent
    override val isFaceHardwarePresent: Boolean? = isFaceHardwarePresent
    override val isIrisHardwarePresent: Boolean? = isIrisHardwarePresent
    override val isStrongBiometricEnabled: Boolean? = isStrongBiometricEnabled
}

internal class ImmediateAppConfigProvider(initial: AppConfig) : AppConfigProvider {
    private val state = MutableStateFlow(initial)
    override val config: Flow<AppConfig> = state

    override suspend fun getOrFetchConfig(): AppConfig = state.value
}

internal class RecordingServerNetwork(
    private val responseFactory: (Request) -> Network.Response = { request ->
        Network.Response.Success(url = request.url, code = 204, headers = emptyMap(), body = "")
    }
) : Network {
    override val okHttpClient: OkHttpClient = OkHttpClient()
    override val retryConfig: Network.Request.RetryConfig? = null
    val requests: MutableList<Request> = mutableListOf()
    var responseString: String? = null

    override suspend fun execute(okHttpRequest: Request): Network.Response {
        requests += okHttpRequest
        return responseFactory(okHttpRequest).also { responseString = it.toString() }
    }
}
