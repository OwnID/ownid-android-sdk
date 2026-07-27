package com.ownid.sdk.flow.elite.impl

import android.webkit.WebView
import androidx.lifecycle.LifecycleOwner
import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.OwnIdConfiguration
import com.ownid.sdk.OwnIdConfigurationImpl
import com.ownid.sdk.capability.AppConfigProvider
import com.ownid.sdk.capability.LocalInfo
import com.ownid.sdk.flow.FlowResult
import com.ownid.sdk.flow.MainDispatcherRule
import com.ownid.sdk.flow.elite.EliteFlowContext
import com.ownid.sdk.flow.elite.EliteFlowFailure
import com.ownid.sdk.model.AppConfig
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.OperationType
import com.ownid.sdk.ops.OperationController
import com.ownid.sdk.ops.OperationResult
import com.ownid.sdk.ops.WebBridgeOperation
import com.ownid.sdk.ops.WebBridgeOperationFailure
import com.ownid.sdk.ops.WebBridgeOperationOptions
import com.ownid.sdk.ops.impl.OperationRegistryImpl
import com.ownid.sdk.ops.impl.WebBridgeOperationImpl
import com.ownid.sdk.ops.ui.WebBridgeUi
import com.ownid.sdk.ui.webbridge.WebBridge
import com.ownid.sdk.ui.webbridge.WebBridgePluginRegistry
import com.ownid.sdk.ui.webbridge.impl.WebBridgePluginRegistryImpl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(InternalOwnIdAPI::class, ExperimentalCoroutinesApi::class)
// Covers: FLOW-090
internal class EliteFlowWebBridgeFailureClassificationContractTest {

    @get:Rule
    val mainDispatcherRule: MainDispatcherRule = MainDispatcherRule()

    @Test
    fun webBridgeInvalidBaseUrlCompletesWithPreconditionBeforeUiOrAttach(): Unit = runTest(mainDispatcherRule.testDispatcher) {
        val ui = RecordingWebBridgeUi()
        val webBridge = RecordingWebBridge()
        val operation = webBridgeOperation(ui = ui, webBridge = webBridge)

        val controller = operation.start(WebBridgeOperation.Params(options = WebBridgeOperationOptions(baseUrl = INVALID_BASE_URL, html = "<html></html>")))

        advanceUntilIdle()
        val result = controller.whenSettled()

        assertThat(result).isInstanceOf(OperationResult.Failure::class.java)
        val failure = (result as OperationResult.Failure).failure
        assertThat(failure).isInstanceOf(WebBridgeOperationFailure.Precondition::class.java)
        assertThat(failure.errorCode).isEqualTo(ErrorCode.Unknown)
        assertThat(failure.message).contains(INVALID_BASE_URL)
        assertThat(ui.startCount).isEqualTo(0)
        assertThat(webBridge.attachCount).isEqualTo(0)
    }

    @Test
    fun eliteInvalidBaseUrlWrapsWebBridgePreconditionBeforeUiOrAttach(): Unit = runTest(mainDispatcherRule.testDispatcher) {
        val ui = RecordingWebBridgeUi()
        val webBridge = RecordingWebBridge()
        val webBridgeOperation = webBridgeOperation(ui = ui, webBridge = webBridge)
        val flow = EliteFlowImpl(
            webBridgeOperation = webBridgeOperation,
            userJourney = null,
            defaultDispatcher = StandardTestDispatcher(testScheduler),
            instanceJob = Job(),
            logger = null
        )

        val controller = flow.start(EliteFlowContext {
            options {
                baseUrl = INVALID_BASE_URL
                html = "<html></html>"
            }
        })

        advanceUntilIdle()
        val result = controller.whenSettled()

        assertThat(result).isInstanceOf(FlowResult.Failure::class.java)
        val failure = (result as FlowResult.Failure).failure
        assertThat(failure).isInstanceOf(EliteFlowFailure.OperationFailed::class.java)
        val operationFailed = failure as EliteFlowFailure.OperationFailed
        assertThat(operationFailed.operationType).isEqualTo(OperationType.WEB_BRIDGE)
        assertThat(operationFailed.operationId).isNotNull()
        assertThat(operationFailed.operationFailure).isInstanceOf(WebBridgeOperationFailure.Precondition::class.java)
        val precondition = operationFailed.operationFailure as WebBridgeOperationFailure.Precondition
        assertThat(operationFailed.errorCode).isEqualTo(precondition.errorCode)
        assertThat(operationFailed.message).contains(precondition.message)
        assertThat(precondition.message).contains(INVALID_BASE_URL)
        assertThat(ui.startCount).isEqualTo(0)
        assertThat(webBridge.attachCount).isEqualTo(0)
    }

    private fun TestScope.webBridgeOperation(
        ui: RecordingWebBridgeUi,
        webBridge: RecordingWebBridge,
        registry: OperationRegistryImpl = OperationRegistryImpl()
    ): WebBridgeOperationImpl =
        WebBridgeOperationImpl(
            operationType = OperationType.WEB_BRIDGE,
            operationRegistry = registry,
            configuration = testConfiguration(),
            appConfigProvider = StaticAppConfigProvider(),
            localInfo = TestLocalInfo,
            ui = ui,
            webBridge = webBridge,
            defaultDispatcher = StandardTestDispatcher(testScheduler),
            instanceJob = Job(),
            unsatisfiedDependencies = null,
            logger = null
        )

    private class RecordingWebBridgeUi : WebBridgeUi {
        var startCount: Int = 0

        override fun start(
            controller: OperationController<Unit, WebBridgeOperationFailure>,
            onStartError: (error: Throwable?, details: String?) -> Unit
        ) {
            startCount += 1
        }
    }

    private class RecordingWebBridge : WebBridge {
        override val plugins: WebBridgePluginRegistry = WebBridgePluginRegistryImpl(emptyList())
        var attachCount: Int = 0

        override fun attach(webView: WebView, allowedOriginRules: Set<String>, owner: LifecycleOwner?): Throwable? {
            attachCount += 1
            return null
        }

        override fun detach(webViewRendererGone: Boolean): Unit = Unit
    }

    private class StaticAppConfigProvider(
        private val appConfig: AppConfig = AppConfig.DEFAULT
    ) : AppConfigProvider {
        override suspend fun getOrFetchConfig(): AppConfig = appConfig

        override val config: Flow<AppConfig> = flowOf(appConfig)
    }

    private object TestLocalInfo : LocalInfo {
        override val modules: List<Pair<String, String>> = emptyList()
        override val packageName: String = "com.ownid.test"
        override val appVersion: String = "1.0"
        override val userAgent: String = "OwnID-Test"
        override val correlationId: String = "correlation-id"
        override val isDebuggable: Boolean = true
        override val isSystemFidoCapable: Boolean = true
        override val isDeviceSecured: Boolean? = null
        override val isFingerprintHardwarePresent: Boolean? = null
        override val isFaceHardwarePresent: Boolean? = null
        override val isIrisHardwarePresent: Boolean? = null
        override val isStrongBiometricEnabled: Boolean? = null
    }

    private companion object {
        const val INVALID_BASE_URL: String = "example.com/path"

        fun testConfiguration(): OwnIdConfiguration = OwnIdConfigurationImpl(appId = "testApp")
    }
}
