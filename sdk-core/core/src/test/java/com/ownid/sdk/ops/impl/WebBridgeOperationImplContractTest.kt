package com.ownid.sdk.ops.impl

import android.webkit.WebView
import androidx.lifecycle.LifecycleOwner
import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.OwnIdConfiguration
import com.ownid.sdk.OwnIdConfigurationImpl
import com.ownid.sdk.capability.AppConfigProvider
import com.ownid.sdk.capability.LocalInfo
import com.ownid.sdk.capability.Serialization
import com.ownid.sdk.flow.elite.EliteFlowContext
import com.ownid.sdk.model.AppConfig
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LoginIdConfiguration
import com.ownid.sdk.model.OperationType
import com.ownid.sdk.model.Reason
import com.ownid.sdk.ops.OperationController
import com.ownid.sdk.ops.OperationResult
import com.ownid.sdk.ops.WebBridgeOperation
import com.ownid.sdk.ops.WebBridgeOperationController
import com.ownid.sdk.ops.WebBridgeOperationEventWrapper
import com.ownid.sdk.ops.WebBridgeOperationFailure
import com.ownid.sdk.ops.WebBridgeOperationOptions
import com.ownid.sdk.ops.ui.WebBridgeUi
import com.ownid.sdk.ui.webbridge.WebBridge
import com.ownid.sdk.ui.webbridge.WebBridgeElitePlugin
import com.ownid.sdk.ui.webbridge.WebBridgePlugin
import com.ownid.sdk.ui.webbridge.WebBridgePluginRegistry
import com.ownid.sdk.ui.webbridge.impl.WebBridgePluginRegistryImpl
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
@OptIn(InternalOwnIdAPI::class)
// Covers: FLOW-080, WB-BRIDGE-130, WB-BRIDGE-140
internal class WebBridgeOperationImplContractTest {

    @Test
    fun eliteContextOptionsBuildWebBridgeParamsWithOnlyExplicitValues() {
        val context = EliteFlowContext {
            options {
                baseUrl = "https://elite.example/login?x=1"
                html = "<html>elite</html>"
                userAgent = "EliteTest/1.0"
                statusBarColor = 0x00112233
                navigationBarColor = 0x00445566
            }
        }

        val options = requireNotNull(context.options)

        assertThat(options.baseUrl).isEqualTo("https://elite.example/login?x=1")
        assertThat(options.html).isEqualTo("<html>elite</html>")
        assertThat(options.userAgent).isEqualTo("EliteTest/1.0")
        assertThat(options.statusBarColor).isEqualTo(0x00112233)
        assertThat(options.navigationBarColor).isEqualTo(0x00445566)
        assertThat(EliteFlowContext.Empty.options).isNull()
    }

    @Test
    fun explicitParamsAndPlatformOptionsOverrideServerConfigBeforeUiHostWork() = runTest {
        val order = mutableListOf<String>()
        val ui = FakeWebBridgeUi(onStart = { order += "ui-start" })
        val resolvedBaseUrls = mutableListOf<String>()
        val operation = webBridgeOperation(
            ui = ui,
            appConfig = appConfig(baseUrl = "https://server.example/page", html = "<html>server</html>"),
            localUserAgent = "LocalInfo/1.0"
        )

        val controller = operation.start(
            WebBridgeOperation.Params(
                options = WebBridgeOperationOptions(
                    baseUrl = "https://params.example/path?ignored=true#fragment",
                    html = "<html>params</html>",
                    userAgent = "ParamsUA/2.0",
                    statusBarColor = 0x00123456,
                    navigationBarColor = 0x00654321
                ),
                onBaseUrlResolved = { baseUrl ->
                    resolvedBaseUrls += baseUrl
                    order += "base-url:$baseUrl"
                }
            )
        )
        drainOperations()

        val uiState = controller.activeUiState()
        assertThat(ui.starts).hasSize(1)
        assertThat(uiState.baseUrl).isEqualTo("https://params.example/path?ignored=true#fragment")
        assertThat(uiState.html).isEqualTo("<html>params</html>")
        assertThat(uiState.userAgent).isEqualTo("ParamsUA/2.0")
        assertThat(uiState.statusBarColor).isEqualTo(0x00123456)
        assertThat(uiState.navigationBarColor).isEqualTo(0x00654321)
        assertThat(resolvedBaseUrls).containsExactly("https://params.example/path?ignored=true#fragment")
        assertThat(order).containsExactly("base-url:https://params.example/path?ignored=true#fragment", "ui-start").inOrder()
    }

    @Test
    fun operationInjectionAttachesBridgeWithDerivedPageOriginAndAttachFailureSettlesUiFailure() = runTest {
        val attachFailure = IllegalStateException("listener failed")
        val webBridge = FakeWebBridge(attachError = attachFailure)
        val operation = webBridgeOperation(
            webBridge = webBridge,
            appConfig = appConfig(baseUrl = "https://Login.Example.com:8443/hosted/path?flow=1#fragment", html = "<html>server</html>")
        )

        val controller = operation.start(null)
        drainOperations()

        controller.activeUiState().doWebViewBridgeInject(WebView(RuntimeEnvironment.getApplication()))
        drainOperations()

        assertThat(webBridge.attachments).hasSize(1)
        assertThat(webBridge.attachments.single().allowedOriginRules).containsExactly("https://login.example.com:8443")

        val failure = controller.whenSettled().assertFailureResult()
        assertThat(failure).isInstanceOf(WebBridgeOperationFailure.Ui::class.java)
        assertThat((failure as WebBridgeOperationFailure.Ui).errorCode).isEqualTo(ErrorCode.Unknown)
        assertThat(failure.message).isEqualTo("WebBridge injection failed: listener failed")
        assertThat(failure.error).isSameInstanceAs(attachFailure)
    }

    @Test
    fun serverConfigSuppliesBaseUrlAndHtmlWhenParamsOmitThemAndLocalInfoSuppliesUserAgent() = runTest {
        val ui = FakeWebBridgeUi()
        val operation = webBridgeOperation(
            ui = ui,
            appConfig = appConfig(baseUrl = "https://server.example/hosted", html = "<html>server</html>"),
            localUserAgent = "LocalInfo/3.0"
        )

        val controller = operation.start(WebBridgeOperation.Params(options = WebBridgeOperationOptions()))
        drainOperations()

        val uiState = controller.activeUiState()
        assertThat(ui.starts).hasSize(1)
        assertThat(uiState.baseUrl).isEqualTo("https://server.example/hosted")
        assertThat(uiState.html).isEqualTo("<html>server</html>")
        assertThat(uiState.userAgent).isEqualTo("LocalInfo/3.0")
    }

    @Test
    fun sdkDefaultsSupplyBaseUrlAndHtmlWhenParamsAndServerConfigOmitThem() = runTest {
        val ui = FakeWebBridgeUi()
        val configuration = OwnIdConfigurationImpl(appId = "testApp", env = OwnIdConfiguration.Env.Uat, region = OwnIdConfiguration.Region.EU)
        val operation = webBridgeOperation(
            ui = ui,
            appConfig = appConfig(),
            localUserAgent = "LocalInfo/4.0",
            configuration = configuration
        )

        val controller = operation.start(null)
        drainOperations()

        val uiState = controller.activeUiState()
        assertThat(ui.starts).hasSize(1)
        assertThat(uiState.baseUrl).isEqualTo(WebBridgeUi.WEBVIEW_URL)
        assertThat(uiState.html).isEqualTo(WebBridgeUi.getHTML(configuration))
        assertThat(uiState.userAgent).isEqualTo("LocalInfo/4.0")
    }

    @Test
    fun invalidResolvedBaseUrlFailsBeforeUiStart() = runTest {
        val ui = FakeWebBridgeUi()
        val operation = webBridgeOperation(
            ui = ui,
            appConfig = appConfig(baseUrl = "not-a-url", html = "<html>server</html>")
        )

        val controller = operation.start(null)
        drainOperations()

        val result = controller.whenSettled()
        assertThat(ui.starts).isEmpty()
        val failure = result.assertFailureResult()
        assertThat(failure).isInstanceOf(WebBridgeOperationFailure.Precondition::class.java)
        assertThat((failure as WebBridgeOperationFailure.Precondition).errorCode).isEqualTo(ErrorCode.Unknown)
        assertThat(failure.message).contains("Invalid baseUrl for WebBridge origin derivation: not-a-url")
    }

    @Test
    fun operationEventWrappersAreAddedBeforeUiStartAndTerminalWrapperSettlesSuccess() = runTest {
        val terminalWrapper = TestEventWrapper(action = "onFinish", isTerminal = true)
        val nonTerminalWrapper = TestEventWrapper(action = "onProgress", isTerminal = false)
        val elitePlugin = CapturingElitePlugin()
        val actionsAtUiStart = mutableListOf<List<String>>()
        val ui = FakeWebBridgeUi(onStart = { actionsAtUiStart += elitePlugin.actions })
        val operation = webBridgeOperation(
            ui = ui,
            webBridge = FakeWebBridge(WebBridgePluginRegistryImpl(listOf(elitePlugin))),
            appConfig = appConfig(baseUrl = "https://server.example/hosted", html = "<html>server</html>")
        )

        val controller = operation.start(WebBridgeOperation.Params(eventWrappers = listOf(nonTerminalWrapper, terminalWrapper)))
        drainOperations()

        assertThat(ui.starts).hasSize(1)
        assertThat(elitePlugin.addedWrappers).containsExactly(nonTerminalWrapper, terminalWrapper).inOrder()
        assertThat(actionsAtUiStart).containsExactly(listOf("onProgress", "onFinish"))

        elitePlugin.complete(terminalWrapper)
        drainOperations()

        assertThat(controller.whenSettled()).isInstanceOf(OperationResult.Success::class.java)
    }

    @Test
    fun uiStartErrorSettlesFailureOnceAndLateTerminalWrapperDoesNotOverrideIt() = runTest {
        val uiStartFailure = IllegalStateException("host unavailable")
        val fixture = startOperationWithTerminalElitePlugin(action = "onFinish")

        fixture.ui.failStart(uiStartFailure, "host startup failed")
        drainOperations()
        val result = fixture.controller.whenSettled()

        fixture.elitePlugin.complete(fixture.terminalWrapper)
        drainOperations()
        val repeatedResult = fixture.controller.whenSettled()

        val failure = result.assertFailureResult()
        assertThat(failure).isInstanceOf(WebBridgeOperationFailure.Ui::class.java)
        assertThat((failure as WebBridgeOperationFailure.Ui).message).isEqualTo("host startup failed")
        assertThat(failure.error).isSameInstanceAs(uiStartFailure)
        assertThat(repeatedResult).isSameInstanceAs(result)
    }

    @Test
    fun webViewDetachCancelsOperationOnceEvenWhenBridgeDetachFails() = runTest {
        val fixture = startOperationWithTerminalElitePlugin(
            action = "onClose",
            webBridge = { elitePlugin ->
                FakeWebBridge(
                    plugins = WebBridgePluginRegistryImpl(listOf(elitePlugin)),
                    detachError = IllegalStateException("detach failed")
                )
            }
        )

        fixture.controller.activeUiState().onWebViewDetach()
        drainOperations()
        val result = fixture.controller.whenSettled()

        fixture.elitePlugin.complete(fixture.terminalWrapper)
        fixture.controller.abort(Reason.SystemError("late abort"))
        drainOperations()
        val repeatedResult = fixture.controller.whenSettled()

        assertThat(fixture.webBridge.detachModes).containsExactly(false)
        val reason = result.assertCanceledResult()
        assertThat(reason).isInstanceOf(Reason.UserClose::class.java)
        assertThat((reason as Reason.UserClose).details).isEqualTo("Elite UI detached")
        assertThat(repeatedResult).isSameInstanceAs(result)
    }

    @Test
    fun rendererLossUsesStateOnlyDetachAndTerminalFailureSettlesOnce() = runTest {
        val fixture = startOperationWithTerminalElitePlugin(action = "onFinish")
        val uiState = fixture.controller.activeUiState()
        val rendererFailure = IllegalStateException("renderer gone")

        uiState.onWebViewRendererGone()
        uiState.onWebViewTerminalError(rendererFailure, "renderer terminated")
        drainOperations()
        val result = fixture.controller.whenSettled()

        uiState.onWebViewCancel(Reason.UserClose("late cancel"))
        fixture.elitePlugin.complete(fixture.terminalWrapper)
        fixture.controller.abort(Reason.SystemError("late abort"))
        drainOperations()

        assertThat(fixture.webBridge.detachModes).containsExactly(true)
        val failure = result.assertFailureResult()
        assertThat(failure).isInstanceOf(WebBridgeOperationFailure.Ui::class.java)
        assertThat((failure as WebBridgeOperationFailure.Ui).message).isEqualTo("renderer terminated")
        assertThat(failure.error).isSameInstanceAs(rendererFailure)
        assertThat(fixture.controller.whenSettled()).isSameInstanceAs(result)
    }

    private fun TestScope.startOperationWithTerminalElitePlugin(
        action: String,
        webBridge: (CapturingElitePlugin) -> FakeWebBridge = { elitePlugin ->
            FakeWebBridge(WebBridgePluginRegistryImpl(listOf(elitePlugin)))
        }
    ): StartedEliteOperation {
        val terminalWrapper = TestEventWrapper(action = action, isTerminal = true)
        val elitePlugin = CapturingElitePlugin()
        val ui = FakeWebBridgeUi()
        val bridge = webBridge(elitePlugin)
        val operation = webBridgeOperation(
            ui = ui,
            webBridge = bridge,
            appConfig = appConfig(baseUrl = "https://server.example/hosted", html = "<html>server</html>")
        )

        val controller = operation.start(WebBridgeOperation.Params(eventWrappers = listOf(terminalWrapper)))
        drainOperations()

        return StartedEliteOperation(controller, terminalWrapper, elitePlugin, ui, bridge)
    }

    private fun TestScope.webBridgeOperation(
        ui: FakeWebBridgeUi = FakeWebBridgeUi(),
        webBridge: WebBridge = FakeWebBridge(),
        appConfig: AppConfig = appConfig(),
        localUserAgent: String = "LocalInfo/1.0",
        configuration: OwnIdConfiguration = OwnIdConfigurationImpl(appId = "testApp")
    ): WebBridgeOperationImpl =
        WebBridgeOperationImpl(
            operationType = OperationType.WEB_BRIDGE,
            operationRegistry = OperationRegistryImpl(),
            configuration = configuration,
            appConfigProvider = FakeAppConfigProvider(appConfig),
            localInfo = FakeLocalInfo(localUserAgent),
            ui = ui,
            webBridge = webBridge,
            defaultDispatcher = StandardTestDispatcher(testScheduler),
            instanceJob = Job(),
            unsatisfiedDependencies = null,
            logger = null
        )

    private fun appConfig(
        baseUrl: String? = null,
        html: String? = null
    ): AppConfig =
        AppConfig(
            loginIdConfig = LoginIdConfiguration.DEFAULT_EMAIL.supportedTypes.map { AppConfig.LoginIdConfig(it) },
            webView = AppConfig.WebViewConfig(baseUrl = baseUrl, html = html),
            logLevel = AppConfig.LogLevel.WARNING
        )

    private fun WebBridgeOperationController.activeUiState(): WebBridgeUi.UiState {
        val currentState = state.value
        assertThat(currentState).isInstanceOf(WebBridgeOperation.State.Active::class.java)
        return (currentState as WebBridgeOperation.State.Active).uiState
    }

    private class FakeAppConfigProvider(private val appConfig: AppConfig) : AppConfigProvider {
        override suspend fun getOrFetchConfig(): AppConfig = appConfig

        override val config: Flow<AppConfig> = flowOf(appConfig)
    }

    private class FakeLocalInfo(override val userAgent: String) : LocalInfo {
        override val modules: List<Pair<String, String>> = emptyList()
        override val packageName: String = "com.ownid.test"
        override val appVersion: String = "1"
        override val correlationId: String = "correlation"
        override val isDebuggable: Boolean = false
        override val isSystemFidoCapable: Boolean = false
        override val isDeviceSecured: Boolean? = null
        override val isFingerprintHardwarePresent: Boolean? = null
        override val isFaceHardwarePresent: Boolean? = null
        override val isIrisHardwarePresent: Boolean? = null
        override val isStrongBiometricEnabled: Boolean? = null
    }

    private data class StartedEliteOperation(
        val controller: WebBridgeOperationController,
        val terminalWrapper: TestEventWrapper,
        val elitePlugin: CapturingElitePlugin,
        val ui: FakeWebBridgeUi,
        val webBridge: FakeWebBridge
    )

    private class FakeWebBridgeUi(private val onStart: () -> Unit = {}) : WebBridgeUi {
        val starts = mutableListOf<OperationController<Unit, WebBridgeOperationFailure>>()
        private var onStartError: ((Throwable?, String?) -> Unit)? = null

        override fun start(
            controller: OperationController<Unit, WebBridgeOperationFailure>,
            onStartError: (error: Throwable?, details: String?) -> Unit
        ) {
            starts += controller
            this.onStartError = onStartError
            onStart()
        }

        fun failStart(error: Throwable?, details: String?) {
            requireNotNull(onStartError) { "WebBridge UI was not started" }.invoke(error, details)
        }
    }

    private class FakeWebBridge(
        override val plugins: WebBridgePluginRegistry = WebBridgePluginRegistryImpl(emptyList()),
        private val attachError: Throwable? = null,
        private val detachError: Throwable? = null
    ) : WebBridge {
        val attachments = mutableListOf<Attachment>()
        val detachModes = mutableListOf<Boolean>()

        override fun attach(webView: WebView, allowedOriginRules: Set<String>, owner: LifecycleOwner?): Throwable? {
            attachments += Attachment(webView, allowedOriginRules, owner)
            return attachError
        }

        override fun detach(webViewRendererGone: Boolean) {
            detachModes += webViewRendererGone
            detachError?.let { throw it }
        }

        data class Attachment(val webView: WebView, val allowedOriginRules: Set<String>, val owner: LifecycleOwner?)
    }

    private class CapturingElitePlugin : WebBridgeElitePlugin {
        val addedWrappers = mutableListOf<WebBridgeOperationEventWrapper>()
        private var sideEffect: ((WebBridgeOperationEventWrapper) -> Unit)? = null

        override val actions: List<String>
            get() = addedWrappers.map { it.webBridgePluginAction }

        override fun addEventWrappers(eventWrappers: List<WebBridgeOperationEventWrapper>) {
            addedWrappers += eventWrappers
        }

        override fun setWrapperSideEffect(sideEffect: (wrapper: WebBridgeOperationEventWrapper) -> Unit) {
            this.sideEffect = sideEffect
        }

        fun complete(wrapper: WebBridgeOperationEventWrapper) {
            requireNotNull(sideEffect) { "Wrapper side effect was not registered" }.invoke(wrapper)
        }

        override suspend fun handle(message: WebBridgePlugin.Message): WebBridgePlugin.Result = WebBridgePlugin.Result()
    }

    private class TestEventWrapper(
        action: String,
        override val isTerminal: Boolean
    ) : WebBridgeOperationEventWrapper {
        override val webBridgePluginAction: String = action

        override suspend fun runWrappedFunction(params: String?, serialization: Serialization): JsonElement = JsonPrimitive(true)
    }
}
