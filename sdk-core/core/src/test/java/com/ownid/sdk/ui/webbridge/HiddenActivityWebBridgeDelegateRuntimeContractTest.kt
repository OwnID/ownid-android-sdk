package com.ownid.sdk.ui.webbridge

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.net.Uri
import android.os.Looper
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.activity.ComponentActivity
import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.di.DIContainerResolver
import com.ownid.sdk.logger.OwnIdLogRouter
import com.ownid.sdk.logger.OwnIdLogger
import com.ownid.sdk.model.InstanceName
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.OperationID
import com.ownid.sdk.model.OperationType
import com.ownid.sdk.model.Reason
import com.ownid.sdk.ops.OperationController
import com.ownid.sdk.ops.OperationRegistry
import com.ownid.sdk.ops.OperationResult
import com.ownid.sdk.ops.WebBridgeOperation
import com.ownid.sdk.ops.WebBridgeOperationController
import com.ownid.sdk.ops.WebBridgeOperationFailure
import com.ownid.sdk.ops.ui.WebBridgeUi
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController

@RunWith(RobolectricTestRunner::class)
@OptIn(InternalOwnIdAPI::class)
@Suppress("DEPRECATION")
// Covers: WB-BRIDGE-150, WB-BRIDGE-160
internal class HiddenActivityWebBridgeDelegateRuntimeContractTest {

    @After
    fun tearDown() {
        idleMain()
    }

    @Test
    fun activeControllerConfiguresWebViewInjectsBeforeLoadAndUsesBackHistoryBeforeCancel() {
        val operationID = OperationID(OperationType.WEB_BRIDGE, "web-host")
        val events = RecordingUiEvents()
        val controller = FakeWebBridgeOperationController(operationID, WebBridgeOperation.State.Active(uiState(events)))
        val activityController = buildActivity(HiddenActivityWebBridgeDelegate.createIntent(RuntimeEnvironment.getApplication(), instanceName, operationID))
        val delegate = HiddenActivityWebBridgeDelegate()

        try {
            delegate.onCreate(activityController.get(), FakeResolver(FakeOperationRegistry(mapOf(operationID to controller))))
            idleMain()

            val webView = activityController.get().hostedWebView()
            val shadowWebView = shadowOf(webView)

            assertThat(webView.settings.javaScriptEnabled).isTrue()
            assertThat(webView.settings.domStorageEnabled).isTrue()
            assertThat(webView.settings.allowFileAccess).isFalse()
            assertThat(webView.settings.allowContentAccess).isFalse()
            assertThat(webView.settings.userAgentString).isEqualTo("OwnID-Test-UA")
            assertThat(shadowWebView.webViewClient).isInstanceOf(HiddenActivityWebBridgeDelegate.EliteWebViewClient::class.java)
            assertThat(events.injectedWebViews).containsExactly(webView)
            assertThat(events.loadWasStartedWhenInjected).containsExactly(false)
            assertThat(shadowWebView.lastLoadDataWithBaseURL.baseUrl).isEqualTo("https://login.example.test/path")
            assertThat(shadowWebView.lastLoadDataWithBaseURL.data).isEqualTo("<html>flow</html>")
            assertThat(shadowWebView.lastLoadDataWithBaseURL.mimeType).isEqualTo("text/html")

            shadowWebView.setCanGoBack(true)
            activityController.get().onBackPressedDispatcher.onBackPressed()
            idleMain()

            assertThat(shadowWebView.goBackInvocations).isEqualTo(1)
            assertThat(events.cancellations).isEmpty()

            shadowWebView.setCanGoBack(false)
            activityController.get().onBackPressedDispatcher.onBackPressed()
            idleMain()

            assertThat(events.cancellations.single()).isInstanceOf(Reason.UserClose::class.java)
        } finally {
            delegate.onDestroy(activityController.get())
            activityController.destroyIfNeeded()
        }
    }

    @Test
    fun completedStateClosesHostAndDestroyDetachesHostedWebView() {
        val operationID = OperationID(OperationType.WEB_BRIDGE, "web-close")
        val events = RecordingUiEvents()
        val controller = FakeWebBridgeOperationController(operationID, WebBridgeOperation.State.Active(uiState(events)))
        val activityController = buildActivity(HiddenActivityWebBridgeDelegate.createIntent(RuntimeEnvironment.getApplication(), instanceName, operationID))
        val delegate = HiddenActivityWebBridgeDelegate()

        try {
            delegate.onCreate(activityController.get(), FakeResolver(FakeOperationRegistry(mapOf(operationID to controller))))
            idleMain()
            val webView = activityController.get().hostedWebView()
            val shadowWebView = shadowOf(webView)

            controller.emitCompleted()
            idleMain()

            assertThat(activityController.get().isFinishing).isTrue()

            delegate.onDestroy(activityController.get())

            assertThat(events.detachCount).isEqualTo(1)
            assertThat(shadowWebView.wasClearHistoryCalled()).isTrue()
            assertThat(shadowWebView.lastLoadedUrl).isEqualTo("about:blank")
            assertThat(shadowWebView.wasDestroyCalled()).isTrue()
            assertThat(webView.parent).isNull()
        } finally {
            activityController.destroyIfNeeded()
        }
    }

    @Test
    fun activeHostPauseStopResumeDoesNotCancelOrDoubleSettleWebBridgeOperation() {
        val operationID = OperationID(OperationType.WEB_BRIDGE, "web-lifecycle")
        val events = RecordingUiEvents()
        val controller = FakeWebBridgeOperationController(operationID, WebBridgeOperation.State.Active(uiState(events)))
        val activityController = buildActivity(HiddenActivityWebBridgeDelegate.createIntent(RuntimeEnvironment.getApplication(), instanceName, operationID))
        val delegate = HiddenActivityWebBridgeDelegate()

        try {
            delegate.onCreate(activityController.get(), FakeResolver(FakeOperationRegistry(mapOf(operationID to controller))))
            idleMain()
            val webView = activityController.get().hostedWebView()

            activityController.pause().stop()
            idleMain()

            assertThat(activityController.get().isFinishing).isFalse()
            assertThat(activityController.get().findHostedWebViewOrNull()).isSameInstanceAs(webView)
            assertThat(events.cancellations).isEmpty()
            assertThat(events.terminalErrors).isEmpty()
            assertThat(events.detachCount).isEqualTo(0)

            activityController.start().resume()
            controller.emitCompleted()
            idleMain()

            assertThat(activityController.get().isFinishing).isTrue()
            assertThat(events.cancellations).isEmpty()
            assertThat(events.terminalErrors).isEmpty()
        } finally {
            delegate.onDestroy(activityController.get())
            activityController.destroyIfNeeded()
        }
    }

    @Test
    fun missingOrNonWebBridgeControllerClosesHostWithoutCreatingWebView() {
        val operationID = OperationID(OperationType.WEB_BRIDGE, "missing")
        val missingControllerActivity =
            buildActivity(HiddenActivityWebBridgeDelegate.createIntent(RuntimeEnvironment.getApplication(), instanceName, operationID))
        val wrongControllerActivity =
            buildActivity(HiddenActivityWebBridgeDelegate.createIntent(RuntimeEnvironment.getApplication(), instanceName, operationID))
        val delegate = HiddenActivityWebBridgeDelegate()

        try {
            delegate.onCreate(missingControllerActivity.get(), FakeResolver(FakeOperationRegistry(emptyMap())))
            idleMain()

            assertThat(missingControllerActivity.get().isFinishing).isTrue()
            assertThat(missingControllerActivity.get().findHostedWebViewOrNull()).isNull()

            delegate.onCreate(wrongControllerActivity.get(), FakeResolver(FakeOperationRegistry(mapOf(operationID to FakeOperationController(operationID)))))
            idleMain()

            assertThat(wrongControllerActivity.get().isFinishing).isTrue()
            assertThat(wrongControllerActivity.get().findHostedWebViewOrNull()).isNull()
        } finally {
            missingControllerActivity.destroyIfNeeded()
            wrongControllerActivity.destroyIfNeeded()
        }
    }

    @Test
    fun webViewClientBlocksUnsafeSchemesWithoutExternalDispatch() {
        val activityController = buildActivity(Intent())
        val webView = WebView(activityController.get())
        val errors = mutableListOf<String>()
        val client = HiddenActivityWebBridgeDelegate.EliteWebViewClient(webView, logger = null, onError = errors::add)

        try {
            listOf("javascript", "data", "file", "content", "intent").forEach { scheme ->
                assertThat(client.shouldOverrideUrlLoading(webView, request(Uri.parse("$scheme://blocked.example/path")))).isTrue()
            }

            assertThat(errors).isEmpty()
        } finally {
            activityController.destroyIfNeeded()
        }
    }

    @Test
    fun webViewClientMapsOwnIdJavaScriptUrlsAndRenderProcessLossToTerminalErrors() {
        val activityController = buildActivity(Intent())
        val webView = WebView(activityController.get())
        val errors = mutableListOf<String>()
        val client = HiddenActivityWebBridgeDelegate.EliteWebViewClient(webView, logger = null, onError = errors::add)

        try {
            assertThat(client.shouldOverrideUrlLoading(webView, request(Uri.parse("ownid://on-js-load-error")))).isTrue()
            assertThat(client.shouldOverrideUrlLoading(webView, request(Uri.parse("ownid://on-js-exception?ex=boom")))).isTrue()
            assertThat(client.onRenderProcessGone(webView, renderProcessGoneDetail())).isTrue()

            assertThat(errors).containsExactly(
                "Failed to load content: ownid://on-js-load-error",
                "JavaScript exception: boom",
                "WebView render process gone. [didCrash: true]"
            ).inOrder()
        } finally {
            activityController.destroyIfNeeded()
        }
    }

    @Test
    fun webViewClientLogsGenericLoadErrorsWithoutConvertingToTerminalCallback() {
        val activityController = buildActivity(Intent())
        val webView = WebView(activityController.get())
        val errors = mutableListOf<String>()
        val logs = mutableListOf<LogEntry>()
        val client = HiddenActivityWebBridgeDelegate.EliteWebViewClient(webView, logger = recordingRouter(logs), onError = errors::add)

        try {
            client.onReceivedError(
                webView,
                request(Uri.parse("https://login.example.test/unavailable")),
                webResourceError(errorCode = -2, description = "host unavailable")
            )

            assertThat(errors).isEmpty()
            assertThat(logs.single().level).isEqualTo(OwnIdLogger.Level.Info)
            assertThat(logs.single().message).contains("host unavailable")
            assertThat(logs.single().message).contains("https://login.example.test/unavailable")
        } finally {
            activityController.destroyIfNeeded()
        }
    }

    @Test
    fun webViewClientConsumesGmailAndExternalHttpsUrlsWithoutTerminalError() {
        val activityController = buildActivity(Intent())
        val activity = activityController.get()
        val webView = WebView(activity)
        val errors = mutableListOf<String>()
        val client = HiddenActivityWebBridgeDelegate.EliteWebViewClient(webView, logger = null, onError = errors::add)

        try {
            assertThat(client.shouldOverrideUrlLoading(webView, request(Uri.parse("https://gmail.com/mail/u/0")))).isTrue()
            assertThat(client.shouldOverrideUrlLoading(webView, request(Uri.parse("https://accounts.example.test/continue")))).isTrue()

            assertThat(errors).isEmpty()
        } finally {
            activityController.destroyIfNeeded()
        }
    }

    @Test
    fun webViewClientPrefersGmailPackageIntentForGmailHostsWithoutExternalDispatch() {
        val activityController = buildActivity(Intent())
        val activity = activityController.get()
        val webView = WebView(activity)
        val errors = mutableListOf<String>()
        val gmailLaunchIntent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(GMAIL_PACKAGE)
        shadowOf(activity.packageManager).addResolveInfoForIntent(
            gmailLaunchIntent,
            ResolveInfo().apply {
                activityInfo = ActivityInfo().apply {
                    packageName = GMAIL_PACKAGE
                    name = "$GMAIL_PACKAGE.ConversationListActivity"
                }
            }
        )
        val client = HiddenActivityWebBridgeDelegate.EliteWebViewClient(webView, logger = null, onError = errors::add)

        try {
            assertThat(client.shouldOverrideUrlLoading(webView, request(Uri.parse("https://www.gmail.com/mail/u/0")))).isTrue()

            val startedIntent = shadowOf(activity).nextStartedActivity
            assertThat(startedIntent.`package`).isEqualTo(GMAIL_PACKAGE)
            assertThat(startedIntent.component?.packageName).isEqualTo(GMAIL_PACKAGE)
            assertThat(errors).isEmpty()
        } finally {
            activityController.destroyIfNeeded()
        }
    }

    private fun uiState(events: RecordingUiEvents): WebBridgeUi.UiState =
        WebBridgeUi.UiState(
            baseUrl = "https://login.example.test/path",
            html = "<html>flow</html>",
            userAgent = "OwnID-Test-UA",
            statusBarColor = Color.BLACK,
            navigationBarColor = Color.WHITE,
            doWebViewBridgeInject = { webView ->
                events.loadWasStartedWhenInjected += shadowOf(webView).lastLoadDataWithBaseURL != null
                events.injectedWebViews += webView
            },
            onWebViewDetach = { events.detachCount += 1 },
            onWebViewTerminalError = { _, details -> events.terminalErrors += details },
            onWebViewCancel = { reason -> events.cancellations += reason }
        )

    private fun buildActivity(intent: Intent): ActivityController<TestActivity> =
        Robolectric.buildActivity(TestActivity::class.java, intent).create().start().resume()

    private fun ComponentActivity.hostedWebView(): WebView =
        requireNotNull(findHostedWebViewOrNull()) { "Expected hosted WebView" }

    private fun ComponentActivity.findHostedWebViewOrNull(): WebView? =
        findViewById<android.view.ViewGroup>(android.R.id.content)?.findViewWithTag(WEB_VIEW_TAG)

    private fun idleMain() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun ActivityController<out Activity>.destroyIfNeeded() {
        if (!get().isDestroyed) destroy()
    }

    private fun request(uri: Uri): WebResourceRequest = object : WebResourceRequest {
        override fun getUrl(): Uri = uri
        override fun isForMainFrame(): Boolean = true
        override fun isRedirect(): Boolean = false
        override fun hasGesture(): Boolean = false
        override fun getMethod(): String = "GET"
        override fun getRequestHeaders(): MutableMap<String, String> = mutableMapOf()
    }

    private fun renderProcessGoneDetail(): RenderProcessGoneDetail =
        object : RenderProcessGoneDetail() {
            override fun didCrash(): Boolean = true
            override fun rendererPriorityAtExit(): Int = 0
        }

    private fun webResourceError(errorCode: Int, description: String): WebResourceError =
        mockk<WebResourceError> {
            every { this@mockk.errorCode } returns errorCode
            every { this@mockk.description } returns description
        }

    private class FakeResolver(private val registry: OperationRegistry) : DIContainerResolver {
        override val scopeName: String = "webbridge-host-test"

        override fun canResolve(clazz: Class<*>): Boolean = clazz == OperationRegistry::class.java || clazz == InstanceName::class.java

        override fun getUnsatisfiedDependencies(clazz: Class<*>): List<String>? = null

        override fun <T : Any> getOrThrow(clazz: Class<T>): T =
            requireNotNull(getOrNull(clazz)) { "No fake dependency for ${clazz.name}" }

        override fun <T : Any> getOrNull(clazz: Class<T>): T? {
            val value: Any? = when (clazz) {
                OperationRegistry::class.java -> registry
                InstanceName::class.java -> instanceName
                else -> null
            }
            @Suppress("UNCHECKED_CAST")
            return value as? T
        }

        override fun <T : Any> getAllInstancesOf(clazz: Class<T>): List<T> = emptyList()
    }

    private class FakeOperationRegistry(initial: Map<OperationID, OperationController<*, *>>) : OperationRegistry {
        override val current: StateFlow<Map<OperationID, OperationController<*, *>>> = MutableStateFlow(initial)
    }

    private class FakeWebBridgeOperationController(
        override val operationId: OperationID,
        initialState: WebBridgeOperation.State
    ) : WebBridgeOperationController {
        override val state: MutableStateFlow<WebBridgeOperation.State> = MutableStateFlow(initialState)

        fun emitCompleted() {
            state.value = WebBridgeOperation.State.Completed(OperationResult.Success(Unit))
        }

        override fun abort(reason: Reason) = Unit

        override suspend fun whenSettled(): OperationResult<Unit, WebBridgeOperationFailure> = OperationResult.Success(Unit)
    }

    private class FakeOperationController(override val operationId: OperationID) : OperationController<Unit, WebBridgeOperationFailure> {
        override fun abort(reason: Reason) = Unit

        override suspend fun whenSettled(): OperationResult<Unit, WebBridgeOperationFailure> = OperationResult.Success(Unit)
    }

    private class RecordingUiEvents {
        val injectedWebViews: MutableList<WebView> = mutableListOf()
        val loadWasStartedWhenInjected: MutableList<Boolean> = mutableListOf()
        val terminalErrors: MutableList<String?> = mutableListOf()
        val cancellations: MutableList<Reason> = mutableListOf()
        var detachCount: Int = 0
    }

    private fun recordingRouter(entries: MutableList<LogEntry>): OwnIdLogRouter {
        val logger = object : OwnIdLogger {
            override val level: OwnIdLogger.Level = OwnIdLogger.Level.Verbose
            override val tag: String = "OwnID-WebBridge-Host-Test"

            override fun log(level: OwnIdLogger.Level, className: String, message: String, cause: Throwable?) {
                entries += LogEntry(level = level, message = message)
            }
        }
        return OwnIdLogRouter(ownIdLoggerProvider = { logger }, serverLoggersProvider = { emptyList() })
    }

    private data class LogEntry(
        val level: OwnIdLogger.Level,
        val message: String
    )

    private class TestActivity : ComponentActivity()

    private companion object {
        private const val WEB_VIEW_TAG = "com.ownid.sdk.internal.flow.KEY_WEB_VIEW_TAG"
        private const val GMAIL_PACKAGE = "com.google.android.gm"
        val instanceName: InstanceName = InstanceName("webbridge-host")
    }
}
