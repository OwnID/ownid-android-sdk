package com.ownid.sdk.ui

import android.app.Activity
import android.app.Application
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.OwnId
import com.ownid.sdk.OwnIdConfigurationImpl
import com.ownid.sdk.capability.impl.SerializationImpl
import com.ownid.sdk.di.OwnIdRootDIContainer
import com.ownid.sdk.di.getOrNull
import com.ownid.sdk.di.register
import com.ownid.sdk.logger.ImmediateAppConfigProvider
import com.ownid.sdk.logger.RecordingServerNetwork
import com.ownid.sdk.logger.ServerLogger
import com.ownid.sdk.logger.diagnosticsAppConfig
import com.ownid.sdk.logger.diagnosticsConfiguration
import com.ownid.sdk.logger.diagnosticsLocalInfo
import com.ownid.sdk.model.AppConfig
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
import com.ownid.sdk.ui.webbridge.EliteWebViewClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@OptIn(InternalOwnIdAPI::class)
@Suppress("DEPRECATION")
// Covers: WB-BRIDGE-150, WB-BRIDGE-280, WB-BRIDGE-300
internal class OwnIdWebViewActivityRuntimeContractTest {

    private val instancesToDestroy = mutableListOf<InstanceName>()

    private val app: Application
        get() = RuntimeEnvironment.getApplication()

    @After
    fun tearDown() {
        instancesToDestroy.forEach(OwnId::destroy)
        OwnIdActivityHostProtocol.LaunchLock.resetStart()
        OwnIdActivityHostProtocol.LaunchLock.onHostDestroyed(keepForRecreation = false)
        idleMain()
    }

    @Test
    fun activeControllerConfiguresOneWebViewInjectsBeforeLoadAndUsesBackHistoryBeforeCancel() {
        val operationID = OperationID(OperationType.WEB_BRIDGE, "web-host")
        val events = RecordingUiEvents()
        val controller = FakeWebBridgeOperationController(operationID, WebBridgeOperation.State.Active(uiState(events)))
        val activityController = buildActivity(registerControllers(operationID to controller), operationID)

        try {
            val webView = activityController.get().hostedWebView()
            val shadowWebView = shadowOf(webView)

            assertThat(webView.settings.javaScriptEnabled).isTrue()
            assertThat(webView.settings.domStorageEnabled).isTrue()
            assertThat(webView.settings.allowFileAccess).isFalse()
            assertThat(webView.settings.allowContentAccess).isFalse()
            assertThat(webView.settings.userAgentString).isEqualTo("OwnID-Test-UA")
            assertThat(shadowWebView.webViewClient).isInstanceOf(EliteWebViewClient::class.java)
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
            activityController.destroyIfNeeded()
        }
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun hostFailureKeepsLaunchGenerationRouterAfterSameNameReplacement() = runTest {
        val operationID = OperationID(OperationType.WEB_BRIDGE, "retained-router")
        val events = RecordingUiEvents()
        val controller = FakeWebBridgeOperationController(operationID, WebBridgeOperation.State.Active(uiState(events)))
        val instanceName = registerControllers(operationID to controller)
        val oldNetwork = RecordingServerNetwork()
        val newNetwork = RecordingServerNetwork()
        val oldLoggerJob = Job()
        val newLoggerJob = Job()
        val loggerDispatcher = StandardTestDispatcher(testScheduler)
        OwnId.getInstanceContainer(instanceName)!!.register<ServerLogger>(
            hostServerLogger(instanceName, "old", oldNetwork, oldLoggerJob, loggerDispatcher)
        )
        val activityController = buildActivity(instanceName, operationID)

        try {
            val replacement = OwnIdRootDIContainer.initializeInstanceContainer(
                instanceName,
                OwnIdConfigurationImpl(appId = "Replacement${UUID.randomUUID().toString().replace("-", "")}")
            )
            replacement.register<ServerLogger>(hostServerLogger(instanceName, "new", newNetwork, newLoggerJob, loggerDispatcher))
            runCurrent()
            oldNetwork.requests.clear()
            newNetwork.requests.clear()

            val webView = activityController.get().hostedWebView()
            val client = shadowOf(webView).webViewClient as EliteWebViewClient
            assertThat(client.shouldOverrideUrlLoading(webView, "ownid://on-js-exception?ex=boom")).isTrue()
            runCurrent()

            assertThat(oldNetwork.requests.map { it.url.host }).containsExactly("old.example")
            assertThat(newNetwork.requests).isEmpty()
            assertThat(events.terminalErrors).containsExactly("JavaScript exception: boom")
        } finally {
            activityController.destroyIfNeeded()
            oldLoggerJob.cancel()
            newLoggerJob.cancel()
        }
    }

    @Test
    fun completedStateClosesHostAndDestroyDetachesHostedWebView() {
        val operationID = OperationID(OperationType.WEB_BRIDGE, "web-close")
        val events = RecordingUiEvents()
        val controller = FakeWebBridgeOperationController(operationID, WebBridgeOperation.State.Active(uiState(events)))
        val activityController = buildActivity(registerControllers(operationID to controller), operationID)

        val webView = activityController.get().hostedWebView()
        val shadowWebView = shadowOf(webView)
        controller.emitCompleted()
        idleMain()

        assertThat(activityController.get().isFinishing).isTrue()

        activityController.destroyIfNeeded()

        assertThat(events.detachCount).isEqualTo(1)
        assertThat(shadowWebView.wasClearHistoryCalled()).isTrue()
        assertThat(shadowWebView.lastLoadedUrl).isEqualTo("about:blank")
        assertThat(shadowWebView.wasDestroyCalled()).isTrue()
        assertThat(webView.parent).isNull()
    }

    @Test
    fun pauseStopResumeKeepsLiveSessionWithoutCancellationOrDuplicateSetup() {
        val operationID = OperationID(OperationType.WEB_BRIDGE, "web-lifecycle")
        val events = RecordingUiEvents()
        val controller = FakeWebBridgeOperationController(operationID, WebBridgeOperation.State.Active(uiState(events)))
        val activityController = buildActivity(registerControllers(operationID to controller), operationID)

        try {
            val webView = activityController.get().hostedWebView()

            activityController.pause().stop()
            idleMain()

            assertThat(activityController.get().isFinishing).isFalse()
            assertThat(activityController.get().findHostedWebViewOrNull()).isSameInstanceAs(webView)
            assertThat(events.cancellations).isEmpty()
            assertThat(events.terminalErrors).isEmpty()
            assertThat(events.detachCount).isEqualTo(0)

            activityController.start().resume()
            assertThat(events.injectedWebViews).containsExactly(webView)
            controller.emitCompleted()
            idleMain()

            assertThat(activityController.get().isFinishing).isTrue()
            assertThat(events.cancellations).isEmpty()
        } finally {
            activityController.destroyIfNeeded()
        }
    }

    @Test
    fun missingOrUnsupportedControllerClosesWithoutCreatingWebView() {
        val operationID = OperationID(OperationType.WEB_BRIDGE, "missing")
        val missing = buildActivity(registerControllers(), operationID)
        val unsupportedController = FakeOperationController(operationID)
        val unsupported = buildActivity(registerControllers(operationID to unsupportedController), operationID)

        try {
            assertThat(missing.get().isFinishing).isTrue()
            assertThat(missing.get().findHostedWebViewOrNull()).isNull()
            assertThat(unsupported.get().isFinishing).isTrue()
            assertThat(unsupported.get().findHostedWebViewOrNull()).isNull()
        } finally {
            missing.destroyIfNeeded()
            unsupported.destroyIfNeeded()
        }
    }

    @Test
    fun destroyBeforeActiveStateAbortsControllerWithoutCreatingWebView() {
        val operationID = OperationID(OperationType.WEB_BRIDGE, "early-destroy")
        val controller = FakeWebBridgeOperationController(operationID, WebBridgeOperation.State.Created)
        val activityController = buildActivity(registerControllers(operationID to controller), operationID)

        assertThat(activityController.get().findHostedWebViewOrNull()).isNull()
        activityController.destroyIfNeeded()

        assertThat(controller.abortReasons).hasSize(1)
        assertThat(controller.abortReasons.single()).isInstanceOf(Reason.UserClose::class.java)
    }

    @Test
    fun declaredConfigurationChangePreservesActivityWebViewAndSingleSetup() {
        val operationID = OperationID(OperationType.WEB_BRIDGE, "configuration")
        val events = RecordingUiEvents()
        val controller = FakeWebBridgeOperationController(operationID, WebBridgeOperation.State.Active(uiState(events)))
        val activityController = buildActivity(registerControllers(operationID to controller), operationID)

        try {
            val activity = activityController.get()
            val webView = activity.hostedWebView()
            val root = webView.parent as ViewGroup
            val shadowWebView = shadowOf(webView)
            val initialLoad = shadowOf(webView).lastLoadDataWithBaseURL
            val newConfiguration = Configuration(activity.resources.configuration).apply {
                orientation = if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    Configuration.ORIENTATION_PORTRAIT
                } else {
                    Configuration.ORIENTATION_LANDSCAPE
                }
            }
            shadowWebView.clearWasInvalidated()

            activityController.configurationChange(newConfiguration)
            idleMain()

            val refreshedInsets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 21, 0, 0))
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(13, 0, 0, 34))
                .build()
            ViewCompat.dispatchApplyWindowInsets(root, refreshedInsets)

            assertThat(activityController.get()).isSameInstanceAs(activity)
            assertThat(activityController.get().hostedWebView()).isSameInstanceAs(webView)
            assertThat(events.injectedWebViews).containsExactly(webView)
            assertThat(shadowWebView.lastLoadDataWithBaseURL).isSameInstanceAs(initialLoad)
            assertThat(shadowWebView.wasInvalidated()).isTrue()
            assertThat(webView.margins()).isEqualTo(Insets.of(13, 21, 0, 34))
            assertThat(events.detachCount).isEqualTo(0)
        } finally {
            activityController.destroyIfNeeded()
        }
    }

    @Test
    fun recreatingConfigurationTearsDownOldHostAndRestoredReplacementDoesNotCreateSession() {
        val operationID = OperationID(OperationType.WEB_BRIDGE, "configuration-recreation")
        val events = RecordingUiEvents()
        val controller = FakeWebBridgeOperationController(operationID, WebBridgeOperation.State.Active(uiState(events)))
        val activityController = buildActivity(registerControllers(operationID to controller), operationID)
        val oldActivity = activityController.get()
        val oldWebView = oldActivity.hostedWebView()

        activityController.recreate()
        idleMain()

        val replacement = activityController.get()
        assertThat(replacement).isNotSameInstanceAs(oldActivity)
        assertThat(replacement.isFinishing).isTrue()
        assertThat(replacement.findHostedWebViewOrNull()).isNull()
        assertThat(events.injectedWebViews).containsExactly(oldWebView)
        assertThat(events.detachCount).isEqualTo(1)
        assertThat(oldWebView.parent).isNull()
        assertThat(shadowOf(oldWebView).wasDestroyCalled()).isTrue()

        activityController.destroyIfNeeded()
    }

    @Test
    fun savedStateLaunchClosesWithoutResolvingOperationOrCreatingWebView() {
        val operationID = OperationID(OperationType.WEB_BRIDGE, "restored")
        val instanceName = registerControllers()
        val endpointNetwork = RecordingServerNetwork()
        val loggerJob = Job()
        val instanceContainer = OwnId.getInstanceContainer(instanceName)!!
        runBlocking { instanceContainer.getOrNull<Job>()!!.cancelAndJoin() }
        instanceContainer.register<ServerLogger>(
            hostServerLogger(instanceName, "restored", endpointNetwork, loggerJob)
        )
        endpointNetwork.requests.clear()
        val activityController = Robolectric.buildActivity(
            OwnIdWebViewActivity::class.java,
            OwnIdWebViewActivity.createIntent(app, instanceName, operationID)
        ).create(Bundle().apply { putBoolean("restored", true) })

        try {
            idleMain()
            assertThat(activityController.get().isFinishing).isTrue()
            assertThat(activityController.get().findHostedWebViewOrNull()).isNull()
            assertThat(shadowOf(activityController.get()).resultCode).isEqualTo(Activity.RESULT_OK)
            assertThat(endpointNetwork.requests).isEmpty()
        } finally {
            activityController.destroyIfNeeded()
            loggerJob.cancel()
        }
    }

    @Config(sdk = [35])
    @Test
    fun hostPaintsWhiteNativeSurfacesAndComposesAllPhysicalInsetsWithoutDoubleDispatch() {
        val operationID = OperationID(OperationType.WEB_BRIDGE, "four-edge-insets")
        val controller = FakeWebBridgeOperationController(
            operationID,
            WebBridgeOperation.State.Active(
                uiState(
                    events = RecordingUiEvents(),
                    statusBarColor = Color.RED,
                    navigationBarColor = Color.YELLOW
                )
            )
        )
        val activityController = buildActivity(registerControllers(operationID to controller), operationID)

        try {
            val activity = activityController.get()
            val webView = activity.hostedWebView()
            val layout = webView.hostedLayout()
            val insets = testWindowInsets(imeVisible = true)

            val dispatched = ViewCompat.dispatchApplyWindowInsets(layout.root, insets)

            assertThat(activity.window.decorView.backgroundColor()).isEqualTo(Color.WHITE)
            assertThat(layout.root.backgroundColor()).isEqualTo(Color.WHITE)
            assertThat(shadowOf(webView).backgroundColor).isEqualTo(Color.WHITE)
            assertThat(layout.root.children()).containsExactly(
                layout.top,
                layout.left,
                layout.right,
                layout.bottom,
                webView
            ).inOrder()
            assertThat(layout.left.frameLayoutParams().gravity).isEqualTo(Gravity.LEFT)
            assertThat(layout.right.frameLayoutParams().gravity).isEqualTo(Gravity.RIGHT)
            assertThat(Gravity.getAbsoluteGravity(layout.left.frameLayoutParams().gravity, View.LAYOUT_DIRECTION_RTL))
                .isEqualTo(Gravity.LEFT)
            assertThat(Gravity.getAbsoluteGravity(layout.right.frameLayoutParams().gravity, View.LAYOUT_DIRECTION_RTL))
                .isEqualTo(Gravity.RIGHT)
            assertThat(layout.top.frameLayoutParams().height).isEqualTo(20)
            assertThat(layout.left.frameLayoutParams().width).isEqualTo(30)
            assertThat(layout.right.frameLayoutParams().width).isEqualTo(0)
            assertThat(layout.bottom.frameLayoutParams().height).isEqualTo(40)
            assertThat(layout.top.backgroundColor()).isEqualTo(Color.RED)
            assertThat(layout.left.backgroundColor()).isEqualTo(Color.YELLOW)
            assertThat(layout.right.backgroundColor()).isEqualTo(Color.RED)
            assertThat(layout.bottom.backgroundColor()).isEqualTo(Color.YELLOW)
            assertThat(webView.margins()).isEqualTo(Insets.of(50, 25, 70, 100))
            assertThat(dispatched.getInsets(WindowInsetsCompat.Type.systemBars())).isEqualTo(Insets.NONE)
            assertThat(dispatched.getInsets(WindowInsetsCompat.Type.displayCutout())).isEqualTo(Insets.NONE)
            assertThat(dispatched.getInsets(WindowInsetsCompat.Type.ime())).isEqualTo(Insets.NONE)
            assertThat(dispatched.getInsets(WindowInsetsCompat.Type.systemGestures())).isEqualTo(Insets.of(7, 8, 9, 10))

            val hiddenImeInsets = testWindowInsets(imeVisible = false)
            ViewCompat.dispatchApplyWindowInsets(layout.root, hiddenImeInsets)

            assertThat(webView.margins()).isEqualTo(Insets.of(50, 25, 60, 40))
        } finally {
            activityController.destroyIfNeeded()
        }
    }

    @Config(sdk = [23])
    @Test
    fun api23ActivePresentationPreservesNavigationFallbackAndAppliesStatusPolicy() {
        val initialOperationID = OperationID(OperationType.WEB_BRIDGE, "initial-api23")
        val initialController = FakeWebBridgeOperationController(initialOperationID, WebBridgeOperation.State.Created)
        val initialHost = buildActivity(registerControllers(initialOperationID to initialController), initialOperationID)
        val initialActivity = initialHost.get()
        val initialInsetsController = WindowCompat.getInsetsController(initialActivity.window, initialActivity.window.decorView)
        val navigationFallback = initialActivity.window.navigationBarColor

        assertThat(initialActivity.window.decorView.backgroundColor()).isEqualTo(Color.WHITE)
        assertThat(initialActivity.window.statusBarColor).isEqualTo(Color.WHITE)
        assertThat(initialInsetsController.isAppearanceLightStatusBars).isTrue()
        assertThat(initialInsetsController.isAppearanceLightNavigationBars).isFalse()
        initialHost.destroyIfNeeded()

        val activeOperationID = OperationID(OperationType.WEB_BRIDGE, "active-api23")
        val activeController = FakeWebBridgeOperationController(
            activeOperationID,
            WebBridgeOperation.State.Active(uiState(RecordingUiEvents(), Color.RED, Color.YELLOW))
        )
        val activeHost = buildActivity(registerControllers(activeOperationID to activeController), activeOperationID)

        try {
            val activity = activeHost.get()
            val insetsController = WindowCompat.getInsetsController(activity.window, activity.window.decorView)

            assertThat(activity.window.decorView.backgroundColor()).isEqualTo(Color.WHITE)
            assertThat(activity.window.statusBarColor).isEqualTo(Color.RED)
            assertThat(activity.window.navigationBarColor).isEqualTo(navigationFallback)
            assertThat(insetsController.isAppearanceLightStatusBars).isFalse()
            assertThat(insetsController.isAppearanceLightNavigationBars).isFalse()
        } finally {
            activeHost.destroyIfNeeded()
        }
    }

    @Config(sdk = [26, 29, 34])
    @Test
    fun pre35PresentationAppliesInitialWhiteAndActiveBarColorPolicy() {
        val initialOperationID = OperationID(OperationType.WEB_BRIDGE, "initial-pre35")
        val initialController = FakeWebBridgeOperationController(initialOperationID, WebBridgeOperation.State.Created)
        val initialHost = buildActivity(registerControllers(initialOperationID to initialController), initialOperationID)
        val initialActivity = initialHost.get()
        val initialInsetsController = WindowCompat.getInsetsController(initialActivity.window, initialActivity.window.decorView)

        assertThat(initialActivity.window.decorView.backgroundColor()).isEqualTo(Color.WHITE)
        assertThat(initialActivity.window.statusBarColor).isEqualTo(Color.WHITE)
        assertThat(initialActivity.window.navigationBarColor).isEqualTo(Color.WHITE)
        assertThat(initialInsetsController.isAppearanceLightStatusBars).isTrue()
        assertThat(initialInsetsController.isAppearanceLightNavigationBars).isTrue()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            assertThat(initialActivity.window.isStatusBarContrastEnforced).isFalse()
            assertThat(initialActivity.window.isNavigationBarContrastEnforced).isFalse()
        }
        initialHost.destroyIfNeeded()

        val activeOperationID = OperationID(OperationType.WEB_BRIDGE, "active-pre35")
        val activeController = FakeWebBridgeOperationController(
            activeOperationID,
            WebBridgeOperation.State.Active(uiState(RecordingUiEvents(), Color.RED, Color.YELLOW))
        )
        val activeHost = buildActivity(registerControllers(activeOperationID to activeController), activeOperationID)

        try {
            val activity = activeHost.get()
            val activeInsetsController = WindowCompat.getInsetsController(activity.window, activity.window.decorView)

            assertThat(activity.window.statusBarColor).isEqualTo(Color.RED)
            assertThat(activity.window.navigationBarColor).isEqualTo(Color.YELLOW)
            assertThat(activeInsetsController.isAppearanceLightStatusBars).isFalse()
            assertThat(activeInsetsController.isAppearanceLightNavigationBars).isTrue()
        } finally {
            activeHost.destroyIfNeeded()
        }
    }

    @Config(sdk = [35])
    @Test
    fun api35PresentationUsesTransparentWindowBarsAndNativeProtectionColors() {
        val initialOperationID = OperationID(OperationType.WEB_BRIDGE, "initial-api35")
        val initialController = FakeWebBridgeOperationController(initialOperationID, WebBridgeOperation.State.Created)
        val initialHost = buildActivity(registerControllers(initialOperationID to initialController), initialOperationID)
        val initialActivity = initialHost.get()
        val initialInsetsController = WindowCompat.getInsetsController(initialActivity.window, initialActivity.window.decorView)

        assertThat(initialActivity.window.decorView.backgroundColor()).isEqualTo(Color.WHITE)
        assertThat(initialActivity.window.statusBarColor).isEqualTo(Color.TRANSPARENT)
        assertThat(initialActivity.window.navigationBarColor).isEqualTo(Color.TRANSPARENT)
        assertThat(initialInsetsController.isAppearanceLightStatusBars).isTrue()
        assertThat(initialInsetsController.isAppearanceLightNavigationBars).isTrue()
        assertThat(initialActivity.window.isStatusBarContrastEnforced).isFalse()
        assertThat(initialActivity.window.isNavigationBarContrastEnforced).isFalse()
        initialHost.destroyIfNeeded()

        val operationID = OperationID(OperationType.WEB_BRIDGE, "active-api35")
        val controller = FakeWebBridgeOperationController(
            operationID,
            WebBridgeOperation.State.Active(uiState(RecordingUiEvents(), Color.RED, Color.YELLOW))
        )
        val activityController = buildActivity(registerControllers(operationID to controller), operationID)

        try {
            val activity = activityController.get()
            val webView = activity.hostedWebView()
            val layout = webView.hostedLayout()
            ViewCompat.dispatchApplyWindowInsets(
                layout.root,
                WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 20, 0, 0))
                    .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 40))
                    .build()
            )
            val insetsController = WindowCompat.getInsetsController(activity.window, activity.window.decorView)

            assertThat(activity.window.statusBarColor).isEqualTo(Color.TRANSPARENT)
            assertThat(activity.window.navigationBarColor).isEqualTo(Color.TRANSPARENT)
            assertThat(layout.top.backgroundColor()).isEqualTo(Color.RED)
            assertThat(layout.bottom.backgroundColor()).isEqualTo(Color.YELLOW)
            assertThat(insetsController.isAppearanceLightStatusBars).isFalse()
            assertThat(insetsController.isAppearanceLightNavigationBars).isTrue()
            assertThat(activity.window.isStatusBarContrastEnforced).isFalse()
            assertThat(activity.window.isNavigationBarContrastEnforced).isFalse()
        } finally {
            activityController.destroyIfNeeded()
        }
    }

    @Test
    fun rendererLossUsesStateOnlyCallbackBeforeTerminalFailureAndSuppressesOrdinaryDetach() {
        val operationID = OperationID(OperationType.WEB_BRIDGE, "renderer")
        val events = RecordingUiEvents()
        val controller = FakeWebBridgeOperationController(operationID, WebBridgeOperation.State.Active(uiState(events)))
        val activityController = buildActivity(registerControllers(operationID to controller), operationID)
        val webView = activityController.get().hostedWebView()
        val client = shadowOf(webView).webViewClient as EliteWebViewClient

        assertThat(client.onRenderProcessGone(webView, renderProcessGoneDetail())).isTrue()

        assertThat(events.callbackOrder).containsExactly("renderer-gone", "terminal-error").inOrder()
        assertThat(events.rendererGoneCount).isEqualTo(1)
        assertThat(events.detachCount).isEqualTo(0)
        assertThat(webView.parent).isNull()
        assertThat(shadowOf(webView).wasDestroyCalled()).isTrue()

        controller.emitCompleted()
        idleMain()
        activityController.destroyIfNeeded()

        assertThat(events.detachCount).isEqualTo(0)
    }

    private fun registerControllers(vararg controllers: Pair<OperationID, OperationController<*, *>>): InstanceName {
        OwnIdRootDIContainer.injectRootDefaults(app)
        val identitySuffix = UUID.randomUUID().toString().replace("-", "")
        val instanceName = InstanceName("webview-host-$identitySuffix").also(instancesToDestroy::add)
        val container = OwnIdRootDIContainer.initializeInstanceContainer(
            instanceName,
            OwnIdConfigurationImpl(appId = "WebViewHost$identitySuffix")
        )
        container.register<OperationRegistry>(FakeOperationRegistry(controllers.toMap()))
        return instanceName
    }

    private fun hostServerLogger(
        instanceName: InstanceName,
        endpoint: String,
        network: RecordingServerNetwork,
        job: Job,
        dispatcher: CoroutineDispatcher = Dispatchers.Unconfined
    ): ServerLogger =
        ServerLogger(
            instanceName = instanceName,
            configuration = diagnosticsConfiguration("https://$endpoint.example/diagnostics/"),
            localInfo = diagnosticsLocalInfo(),
            appConfigProvider = ImmediateAppConfigProvider(diagnosticsAppConfig(AppConfig.LogLevel.DEBUG)),
            network = network,
            serialization = SerializationImpl(),
            dispatcher = dispatcher,
            instanceJob = job,
            ownIdLogger = null
        )

    private fun uiState(
        events: RecordingUiEvents,
        statusBarColor: Int = Color.BLACK,
        navigationBarColor: Int = Color.WHITE
    ): WebBridgeUi.UiState =
        WebBridgeUi.UiState(
            baseUrl = "https://login.example.test/path",
            html = "<html>flow</html>",
            userAgent = "OwnID-Test-UA",
            statusBarColor = statusBarColor,
            navigationBarColor = navigationBarColor,
            doWebViewBridgeInject = { webView ->
                events.loadWasStartedWhenInjected += shadowOf(webView).lastLoadDataWithBaseURL != null
                events.injectedWebViews += webView
            },
            onWebViewDetach = { events.detachCount += 1 },
            onWebViewRendererGone = {
                events.rendererGoneCount += 1
                events.callbackOrder += "renderer-gone"
            },
            onWebViewTerminalError = { _, details ->
                events.terminalErrors += details
                events.callbackOrder += "terminal-error"
            },
            onWebViewCancel = { reason -> events.cancellations += reason }
        )

    private fun buildActivity(instanceName: InstanceName, operationID: OperationID): ActivityController<OwnIdWebViewActivity> =
        Robolectric.buildActivity(
            OwnIdWebViewActivity::class.java,
            OwnIdWebViewActivity.createIntent(app, instanceName, operationID)
        ).create().start().resume().also { idleMain() }

    private fun Activity.hostedWebView(): WebView = requireNotNull(findHostedWebViewOrNull()) { "Expected hosted WebView" }

    private fun Activity.findHostedWebViewOrNull(): WebView? =
        findViewById<ViewGroup>(android.R.id.content)?.findViewWithTag(WEB_VIEW_TAG)

    private fun WebView.hostedLayout(): HostedLayout {
        val root = parent as FrameLayout
        assertThat(root.childCount).isEqualTo(5)
        return HostedLayout(
            root = root,
            top = root.getChildAt(0),
            left = root.getChildAt(1),
            right = root.getChildAt(2),
            bottom = root.getChildAt(3)
        )
    }

    private fun View.backgroundColor(): Int = (background as ColorDrawable).color

    private fun View.frameLayoutParams(): FrameLayout.LayoutParams = layoutParams as FrameLayout.LayoutParams

    private fun View.margins(): Insets {
        val params = layoutParams as ViewGroup.MarginLayoutParams
        return Insets.of(params.leftMargin, params.topMargin, params.rightMargin, params.bottomMargin)
    }

    private fun ViewGroup.children(): List<View> = List(childCount, ::getChildAt)

    private fun testWindowInsets(imeVisible: Boolean): WindowInsetsCompat =
        WindowInsetsCompat.toWindowInsetsCompat(
            android.view.WindowInsets.Builder()
                .setInsets(android.view.WindowInsets.Type.statusBars(), android.graphics.Insets.of(0, 20, 0, 0))
                .setInsets(android.view.WindowInsets.Type.navigationBars(), android.graphics.Insets.of(30, 0, 0, 40))
                .setInsets(android.view.WindowInsets.Type.displayCutout(), android.graphics.Insets.of(50, 25, 60, 0))
                .setInsets(android.view.WindowInsets.Type.ime(), android.graphics.Insets.of(0, 0, 70, 100))
                .setVisible(android.view.WindowInsets.Type.ime(), imeVisible)
                .setInsets(android.view.WindowInsets.Type.systemGestures(), android.graphics.Insets.of(7, 8, 9, 10))
                .build()
        )

    private fun idleMain() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun ActivityController<out Activity>.destroyIfNeeded() {
        if (!get().isDestroyed) destroy()
    }

    private fun renderProcessGoneDetail(): RenderProcessGoneDetail =
        object : RenderProcessGoneDetail() {
            override fun didCrash(): Boolean = true
            override fun rendererPriorityAtExit(): Int = 0
        }

    private class FakeOperationRegistry(initial: Map<OperationID, OperationController<*, *>>) : OperationRegistry {
        override val current: StateFlow<Map<OperationID, OperationController<*, *>>> = MutableStateFlow(initial)
    }

    private class FakeWebBridgeOperationController(
        override val operationId: OperationID,
        initialState: WebBridgeOperation.State
    ) : WebBridgeOperationController {
        override val state: MutableStateFlow<WebBridgeOperation.State> = MutableStateFlow(initialState)
        val abortReasons = mutableListOf<Reason>()

        fun emitCompleted() {
            state.value = WebBridgeOperation.State.Completed(OperationResult.Success(Unit))
        }

        override fun abort(reason: Reason) {
            abortReasons += reason
        }

        override suspend fun whenSettled(): OperationResult<Unit, WebBridgeOperationFailure> = OperationResult.Success(Unit)
    }

    private class FakeOperationController(override val operationId: OperationID) : OperationController<Unit, WebBridgeOperationFailure> {
        override fun abort(reason: Reason) = Unit

        override suspend fun whenSettled(): OperationResult<Unit, WebBridgeOperationFailure> = OperationResult.Success(Unit)
    }

    private class RecordingUiEvents {
        val injectedWebViews = mutableListOf<WebView>()
        val loadWasStartedWhenInjected = mutableListOf<Boolean>()
        val terminalErrors = mutableListOf<String?>()
        val cancellations = mutableListOf<Reason>()
        val callbackOrder = mutableListOf<String>()
        var detachCount: Int = 0
        var rendererGoneCount: Int = 0
    }

    private data class HostedLayout(
        val root: FrameLayout,
        val top: View,
        val left: View,
        val right: View,
        val bottom: View
    )

    private companion object {
        private const val WEB_VIEW_TAG = "com.ownid.sdk.internal.flow.KEY_WEB_VIEW_TAG"
    }
}
