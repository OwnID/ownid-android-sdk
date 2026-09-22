package com.ownid.sdk.ui.webbridge.impl

import android.app.Activity
import android.net.Uri
import android.webkit.WebView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.webkit.WebMessageCompat
import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.capability.UiContextProvider
import com.ownid.sdk.capability.UiContextResolutionException
import com.ownid.sdk.logger.OwnIdLogRouter
import com.ownid.sdk.logger.OwnIdLogger
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.ui.webbridge.WebBridgePlugin
import com.ownid.sdk.ui.webbridge.WebBridgePluginKey
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowOwnIdWebViewFeature::class, ShadowOwnIdWebViewCompat::class])
@OptIn(ExperimentalCoroutinesApi::class, InternalOwnIdAPI::class)
// Covers: WB-BRIDGE-010, WB-BRIDGE-020, WB-BRIDGE-030, WB-BRIDGE-070, WB-BRIDGE-080, WB-BRIDGE-090, WB-BRIDGE-100, WB-BRIDGE-110
internal class WebBridgeRuntimeContractTest {

    @Before
    fun setUp() {
        ShadowOwnIdWebViewFeature.reset()
        ShadowOwnIdWebViewCompat.reset()
    }

    @After
    fun tearDown() {
        DispatchersHolder.resetMainIfSet()
    }

    @Test
    fun attachMergesServerOriginsAlreadyObservedByTheBridge() = runTest {
        val harness = webBridgeHarness(
            plugin = RecordingRuntimeWebBridgePlugin("custom", actions = listOf("run", "cancel")),
            appConfigProvider = MutableAppConfigProvider(
                appConfigWithAllowedOrigins(setOf("server.example.test", "*.server.example.test", "https://server-invalid.test/path"))
            )
        )
        try {
            harness.configScheduler.runCurrent()

            val error = harness.bridge.attach(
                webView = harness.webView,
                allowedOriginRules = linkedSetOf(
                    "EXAMPLE.test",
                    "*",
                    "*.Example.test",
                    "https://login.example.test:8443",
                    "https://explicit-invalid.test/path"
                ),
                owner = harness.owner
            )

            assertThat(error).isNull()
            assertThat(ShadowOwnIdWebViewCompat.messageListeners).hasSize(1)
            assertThat(ShadowOwnIdWebViewCompat.documentStartScripts).hasSize(1)
            assertThat(ShadowOwnIdWebViewCompat.messageListeners.single().jsObjectName).isEqualTo("__ownidNativeBridgeHandler")
            assertThat(ShadowOwnIdWebViewCompat.messageListeners.single().allowedOriginRules)
                .containsExactly(
                    "https://example.test",
                    "*",
                    "https://*.example.test",
                    "https://login.example.test:8443",
                    "https://server.example.test",
                    "https://*.server.example.test"
                )
                .inOrder()
            assertThat(ShadowOwnIdWebViewCompat.documentStartScripts.single().allowedOriginRules)
                .containsExactly(
                    "https://example.test",
                    "*",
                    "https://*.example.test",
                    "https://login.example.test:8443",
                    "https://server.example.test",
                    "https://*.server.example.test"
                )
                .inOrder()
            assertThat(ShadowOwnIdWebViewCompat.documentStartScripts.single().script).contains("window.__ownidNativeBridge")
            assertThat(ShadowOwnIdWebViewCompat.documentStartScripts.single().script).contains(""""CUSTOM":["run","cancel"]""")
        } finally {
            harness.cancel()
        }
    }

    @Test
    fun attachUsesExplicitOriginsImmediatelyAndLateConfigDoesNotMutateInstalledRules() = runTest {
        val harness = webBridgeHarness(
            appConfigProvider = MutableAppConfigProvider(
                appConfigWithAllowedOrigins(setOf("server.example.test"))
            )
        )
        try {
            val error = harness.bridge.attach(
                webView = harness.webView,
                allowedOriginRules = setOf("explicit.example.test"),
                owner = harness.owner
            )

            assertThat(error).isNull()
            assertThat(ShadowOwnIdWebViewCompat.messageListeners.single().allowedOriginRules)
                .containsExactly("https://explicit.example.test")
            assertThat(ShadowOwnIdWebViewCompat.documentStartScripts.single().allowedOriginRules)
                .containsExactly("https://explicit.example.test")

            harness.configScheduler.runCurrent()

            assertThat(ShadowOwnIdWebViewCompat.messageListeners.single().allowedOriginRules)
                .containsExactly("https://explicit.example.test")
            assertThat(ShadowOwnIdWebViewCompat.documentStartScripts.single().allowedOriginRules)
                .containsExactly("https://explicit.example.test")
        } finally {
            harness.cancel()
        }
    }

    @Test
    fun attachWithNoEarlyOriginsCanRetryAfterServerOriginsAreObserved() = runTest {
        val harness = webBridgeHarness(
            appConfigProvider = MutableAppConfigProvider(
                appConfigWithAllowedOrigins(setOf("server.example.test"))
            )
        )
        try {
            val firstError = harness.bridge.attach(
                webView = harness.webView,
                allowedOriginRules = emptySet(),
                owner = harness.owner
            )

            assertThat(firstError).hasMessageThat().contains("No valid allowed origins available")
            assertThat(ShadowOwnIdWebViewCompat.messageListeners).isEmpty()
            assertThat(ShadowOwnIdWebViewCompat.documentStartScripts).isEmpty()

            harness.configScheduler.runCurrent()
            val retryError = harness.bridge.attach(
                webView = harness.webView,
                allowedOriginRules = emptySet(),
                owner = harness.owner
            )

            assertThat(retryError).isNull()
            assertThat(ShadowOwnIdWebViewCompat.messageListeners.single().allowedOriginRules)
                .containsExactly("https://server.example.test")
            assertThat(ShadowOwnIdWebViewCompat.documentStartScripts.single().allowedOriginRules)
                .containsExactly("https://server.example.test")
        } finally {
            harness.cancel()
        }
    }

    @Test
    fun attachLogsEmptyPluginInjectionAndSuccessfulBridgeCreation() = runTest {
        val logEntries = mutableListOf<LogEntry>()
        val harness = webBridgeHarness(plugins = emptyList(), logger = recordingRouter(logEntries))
        try {
            val error = harness.bridge.attach(harness.webView, setOf("example.test"), harness.owner)

            assertThat(error).isNull()
            assertThat(logEntries.any { it.level == OwnIdLogger.Level.Warn && it.message.contains("No plugins available for injection") }).isTrue()
            assertThat(logEntries.any { it.level == OwnIdLogger.Level.Debug && it.message.contains("WebBridge injected to origins") }).isTrue()
        } finally {
            harness.cancel()
        }
    }

    @Test
    fun attachFailsClosedWhenNoEffectiveOriginsNormalize() = runTest {
        val harness = webBridgeHarness(
            appConfigProvider = MutableAppConfigProvider(appConfigWithAllowedOrigins(setOf("https://server-invalid.test/path")))
        )
        try {
            harness.configScheduler.runCurrent()

            val firstError = harness.bridge.attach(
                webView = harness.webView,
                allowedOriginRules = setOf(" ", "https://explicit-invalid.test/path"),
                owner = harness.owner
            )

            assertThat(firstError).hasMessageThat().contains("No valid allowed origins available")
            assertThat(ShadowOwnIdWebViewCompat.messageListeners).isEmpty()
            assertThat(ShadowOwnIdWebViewCompat.documentStartScripts).isEmpty()
            harness.uiContextProvider.assertFailure(UiContextResolutionException.Reason.Unavailable)

            val secondError = harness.bridge.attach(
                webView = harness.webView,
                allowedOriginRules = setOf("example.test"),
                owner = harness.owner
            )

            assertThat(ShadowOwnIdWebViewCompat.messageListeners).hasSize(1)
            assertThat(ShadowOwnIdWebViewCompat.messageListeners.single().allowedOriginRules).containsExactly("https://example.test")
            assertThat(secondError).isNull()
        } finally {
            harness.cancel()
        }
    }

    @Test
    fun attachRequiresAnActivityContextAndCanRetry() = runTest {
        val harness = webBridgeHarness()
        try {
            val error = harness.bridge.attach(
                WebView(RuntimeEnvironment.getApplication()),
                setOf("example.test"),
                harness.owner
            )

            error.assertReason(UiContextResolutionException.Reason.Unavailable)
            assertThat(ShadowOwnIdWebViewCompat.messageListeners).isEmpty()
            assertThat(ShadowOwnIdWebViewCompat.removedMessageListeners).isEmpty()
            assertThat(ShadowOwnIdWebViewCompat.documentStartScripts).isEmpty()
            harness.uiContextProvider.assertFailure(UiContextResolutionException.Reason.Unavailable)

            harness.attachSuccessfully()
            harness.uiContextProvider.assertActivity(harness.activity)
        } finally {
            harness.cancel()
        }
    }

    @Test
    fun successfulAttachKeepsTheExactWebViewActivityWhenAnotherActivityAppears() = runTest {
        val harness = webBridgeHarness()
        var otherActivity: ActivityController<Activity>? = null
        try {
            harness.attachSuccessfully()
            otherActivity = Robolectric.buildActivity(Activity::class.java).setup()

            harness.uiContextProvider.assertActivity(harness.activity)
            assertThat(harness.activity).isNotSameInstanceAs(otherActivity.get())
        } finally {
            otherActivity?.let { if (!it.get().isDestroyed) it.destroy() }
            harness.cancel()
        }
    }

    @Test
    fun successfulAttachMakesBridgeSingleUseWithoutInstallingDuplicateWebKitBoundary() = runTest {
        val harness = webBridgeHarness()
        try {
            harness.attachSuccessfully()

            val secondError = harness.bridge.attach(harness.webView, setOf("other.example.test"), harness.owner)

            assertThat(secondError).hasMessageThat().contains("can be injected only once")
            assertThat(ShadowOwnIdWebViewCompat.messageListeners).hasSize(1)
            assertThat(ShadowOwnIdWebViewCompat.documentStartScripts).hasSize(1)
            assertThat(ShadowOwnIdWebViewCompat.messageListeners.single().allowedOriginRules).containsExactly("https://example.test")
            assertThat(ShadowOwnIdWebViewCompat.documentStartScripts.single().allowedOriginRules).containsExactly("https://example.test")
        } finally {
            harness.cancel()
        }
    }

    @Test
    fun detachAndOwnerDestroyRemoveWebKitBridgeBoundary() = runTest {
        val explicitDetachHarness = webBridgeHarness()
        val lifecycleDetachHarness = webBridgeHarness()
        try {
            explicitDetachHarness.attachSuccessfully()
            explicitDetachHarness.uiContextProvider.assertActivity(explicitDetachHarness.activity)
            val explicitScript = ShadowOwnIdWebViewCompat.documentStartScripts.single().handler

            explicitDetachHarness.bridge.detach()

            assertThat(ShadowOwnIdWebViewCompat.removedMessageListeners.map { it.jsObjectName }).containsExactly("__ownidNativeBridgeHandler")
            assertThat(explicitScript.removed).isTrue()
            explicitDetachHarness.uiContextProvider.assertFailure(UiContextResolutionException.Reason.OwnerClosed)

            ShadowOwnIdWebViewCompat.reset()
            lifecycleDetachHarness.attachSuccessfully()
            lifecycleDetachHarness.uiContextProvider.assertActivity(lifecycleDetachHarness.activity)
            val lifecycleScript = ShadowOwnIdWebViewCompat.documentStartScripts.single().handler

            lifecycleDetachHarness.owner.destroy()

            assertThat(ShadowOwnIdWebViewCompat.removedMessageListeners.map { it.jsObjectName }).containsExactly("__ownidNativeBridgeHandler")
            assertThat(lifecycleScript.removed).isTrue()
            lifecycleDetachHarness.uiContextProvider.assertFailure(UiContextResolutionException.Reason.OwnerClosed)
        } finally {
            explicitDetachHarness.cancel()
            lifecycleDetachHarness.cancel()
        }
    }

    @Test
    fun rendererGoneDetachCancelsInFlightWorkWithoutTouchingWebViewBoundary() = runTest {
        val mainScheduler = TestCoroutineScheduler()
        val plugin = GatedRuntimeWebBridgePlugin(id = "custom")
        val harness = webBridgeHarness(plugin = plugin, mainScheduler = mainScheduler)
        try {
            harness.attachSuccessfully()
            val script = ShadowOwnIdWebViewCompat.documentStartScripts.single().handler

            harness.postMessage(pluginId = "CUSTOM", action = "run")
            mainScheduler.runCurrent()
            assertThat(plugin.events).containsExactly("CUSTOM:run:start")

            harness.bridge.detach(webViewRendererGone = true)
            mainScheduler.runCurrent()
            harness.bridge.detach(webViewRendererGone = true)

            assertThat(plugin.events).containsExactly("CUSTOM:run:start", "CUSTOM:run:cancel").inOrder()
            assertThat(shadowOf(harness.webView).lastEvaluatedJavascript).isNull()
            assertThat(ShadowOwnIdWebViewCompat.removedMessageListeners).isEmpty()
            assertThat(script.removed).isFalse()
            harness.uiContextProvider.assertFailure(UiContextResolutionException.Reason.OwnerClosed)
            assertThat(harness.bridge.attach(harness.webView, setOf("other.example.test"), harness.owner))
                .hasMessageThat().contains("can be injected only once")
        } finally {
            harness.cancel()
        }
    }

    @Test
    fun scriptRegistrationFailureRollsBackAndAllowsRetry() = runTest {
        val harness = webBridgeHarness()
        ShadowOwnIdWebViewCompat.documentStartFailure = IllegalStateException("script failed")
        try {
            val error = harness.bridge.attach(harness.webView, setOf("example.test"), harness.owner)

            assertThat(error).hasMessageThat().contains("script failed")
            assertThat(ShadowOwnIdWebViewCompat.messageListeners).hasSize(1)
            assertThat(ShadowOwnIdWebViewCompat.documentStartScripts).isEmpty()
            assertThat(ShadowOwnIdWebViewCompat.removedMessageListeners.map { it.jsObjectName }).containsExactly("__ownidNativeBridgeHandler")
            harness.uiContextProvider.assertFailure(UiContextResolutionException.Reason.Unavailable)

            ShadowOwnIdWebViewCompat.documentStartFailure = null
            harness.attachSuccessfully()
            harness.uiContextProvider.assertActivity(harness.activity)
        } finally {
            harness.cancel()
        }
    }

    @Test
    fun listenerRegistrationFailureDoesNotRemoveAnUnownedListenerAndAllowsRetry() = runTest {
        val harness = webBridgeHarness()
        ShadowOwnIdWebViewCompat.messageListenerFailure = IllegalStateException("listener already registered")
        try {
            val error = harness.bridge.attach(harness.webView, setOf("example.test"), harness.owner)

            assertThat(error).hasMessageThat().contains("listener already registered")
            assertThat(ShadowOwnIdWebViewCompat.messageListeners).isEmpty()
            assertThat(ShadowOwnIdWebViewCompat.removedMessageListeners).isEmpty()
            assertThat(ShadowOwnIdWebViewCompat.documentStartScripts).isEmpty()
            harness.uiContextProvider.assertFailure(UiContextResolutionException.Reason.Unavailable)

            ShadowOwnIdWebViewCompat.messageListenerFailure = null
            harness.attachSuccessfully()
            harness.uiContextProvider.assertActivity(harness.activity)
        } finally {
            harness.cancel()
        }
    }

    @Test
    fun observerRegistrationFailureRollsBackAllAcquiredResourcesAndAllowsRetry() = runTest {
        val harness = webBridgeHarness()
        val lifecycle = mockk<Lifecycle>()
        val observer = slot<LifecycleObserver>()
        every { lifecycle.currentState } returns Lifecycle.State.CREATED
        every { lifecycle.addObserver(capture(observer)) } answers { throw IllegalStateException("observer failed") }
        every { lifecycle.removeObserver(any()) } just Runs
        val failingOwner = object : LifecycleOwner {
            override val lifecycle: Lifecycle = lifecycle
        }
        try {
            val error = harness.bridge.attach(harness.webView, setOf("example.test"), failingOwner)
            val script = ShadowOwnIdWebViewCompat.documentStartScripts.single().handler

            assertThat(error).hasMessageThat().contains("observer failed")
            verify(exactly = 1) { lifecycle.removeObserver(observer.captured) }
            assertThat(script.removed).isTrue()
            assertThat(ShadowOwnIdWebViewCompat.removedMessageListeners.map { it.jsObjectName }).containsExactly("__ownidNativeBridgeHandler")
            harness.uiContextProvider.assertFailure(UiContextResolutionException.Reason.Unavailable)

            harness.attachSuccessfully()
            harness.uiContextProvider.assertActivity(harness.activity)
        } finally {
            harness.cancel()
        }
    }

    @Test
    fun validMainFrameMessageInvokesAttachedPluginAndEvaluatesCallback() = runTest {
        val mainScheduler = TestCoroutineScheduler()
        val plugin = RecordingRuntimeWebBridgePlugin(
            id = "custom",
            result = WebBridgePlugin.Result(success = buildJsonObject { put("ok", true) })
        )
        val harness = webBridgeHarness(plugin = plugin, mainScheduler = mainScheduler)
        try {
            harness.attachSuccessfully()

            ShadowOwnIdWebViewCompat.messageListeners.single().listener.onPostMessage(
                harness.webView,
                WebMessageCompat("""{"pluginId":"CUSTOM","action":"run","callbackPath":"ownid.callback","params":"{\"value\":1}"}"""),
                Uri.parse("https://example.test"),
                true,
                NoopJavaScriptReplyProxy
            )
            mainScheduler.runCurrent()

            assertThat(plugin.messages).hasSize(1)
            assertThat(plugin.messages.single().webView).isSameInstanceAs(harness.webView)
            assertThat(plugin.messages.single().sourceOrigin).isEqualTo(Uri.parse("https://example.test"))
            assertThat(plugin.messages.single().isMainFrame).isTrue()
            assertThat(plugin.messages.single().allowedOriginRules).containsExactly("https://example.test")
            assertThat(plugin.messages.single().payload.action).isEqualTo("run")
            assertThat(shadowOf(harness.webView).lastEvaluatedJavascript).isEqualTo("""ownid.callback({"ok":true})""")
        } finally {
            harness.cancel()
        }
    }

    @Test
    fun messageValidationRejectsSubframeMalformedPayloadUnsafeCallbackAndUnknownPlugin() = runTest {
        val mainScheduler = TestCoroutineScheduler()
        val plugin = RecordingRuntimeWebBridgePlugin(id = "custom")
        val harness = webBridgeHarness(plugin = plugin, mainScheduler = mainScheduler)
        try {
            harness.attachSuccessfully()
            val listener = ShadowOwnIdWebViewCompat.messageListeners.single().listener

            listener.onPostMessage(
                harness.webView,
                WebMessageCompat("""{"pluginId":"CUSTOM","action":"run","callbackPath":"ownid.callback"}"""),
                Uri.parse("https://example.test"),
                false,
                NoopJavaScriptReplyProxy
            )
            mainScheduler.runCurrent()
            assertThat(plugin.messages).isEmpty()
            assertThat(shadowOf(harness.webView).lastEvaluatedJavascript).isNull()

            listener.onPostMessage(
                harness.webView,
                WebMessageCompat("""{"callbackPath":"ownid.callback"}"""),
                Uri.parse("https://example.test"),
                true,
                NoopJavaScriptReplyProxy
            )
            mainScheduler.runCurrent()
            assertThat(plugin.messages).isEmpty()
            assertThat(shadowOf(harness.webView).lastEvaluatedJavascript).contains("ownid.callback({\"error\":{\"type\":\"UNKNOWN\"")

            val evaluatedAfterMalformedPayload = shadowOf(harness.webView).lastEvaluatedJavascript
            listener.onPostMessage(
                harness.webView,
                WebMessageCompat("""{"pluginId":"CUSTOM","action":"run","callbackPath":"ownid.callback;alert"}"""),
                Uri.parse("https://example.test"),
                true,
                NoopJavaScriptReplyProxy
            )
            mainScheduler.runCurrent()
            assertThat(plugin.messages).isEmpty()
            assertThat(shadowOf(harness.webView).lastEvaluatedJavascript).isEqualTo(evaluatedAfterMalformedPayload)

            listener.onPostMessage(
                harness.webView,
                WebMessageCompat("""{"pluginId":"missing","action":"run","callbackPath":"ownid.callback"}"""),
                Uri.parse("https://example.test"),
                true,
                NoopJavaScriptReplyProxy
            )
            mainScheduler.runCurrent()
            assertThat(plugin.messages).isEmpty()
            assertThat(shadowOf(harness.webView).lastEvaluatedJavascript).contains("Unknown plugin: missing")
        } finally {
            harness.cancel()
        }
    }

    @Test
    fun attachedBridgeUsesPluginSnapshotForRuntimeDispatch() = runTest {
        val mainScheduler = TestCoroutineScheduler()
        val firstPlugin = RecordingRuntimeWebBridgePlugin(id = "custom")
        val replacementPlugin = RecordingRuntimeWebBridgePlugin(id = "custom")
        val harness = webBridgeHarness(plugin = firstPlugin, mainScheduler = mainScheduler)
        try {
            harness.attachSuccessfully()

            harness.bridge.plugins.remove(WebBridgePluginKey.from("custom"))
            harness.bridge.plugins.add(replacementPlugin)
            ShadowOwnIdWebViewCompat.messageListeners.single().listener.onPostMessage(
                harness.webView,
                WebMessageCompat("""{"pluginId":"CUSTOM","action":"run","callbackPath":"ownid.callback"}"""),
                Uri.parse("https://example.test"),
                true,
                NoopJavaScriptReplyProxy
            )
            mainScheduler.runCurrent()

            assertThat(firstPlugin.messages).hasSize(1)
            assertThat(replacementPlugin.messages).isEmpty()
        } finally {
            harness.cancel()
        }
    }

    @Test
    fun thrownPluginErrorMapsToCallbackErrorPayload() = runTest {
        val mainScheduler = TestCoroutineScheduler()
        val plugin = ThrowingRuntimeWebBridgePlugin(id = "custom", failure = IllegalStateException("plugin failed"))
        val harness = webBridgeHarness(plugin = plugin, mainScheduler = mainScheduler)
        try {
            harness.attachSuccessfully()

            harness.postMessage(pluginId = "CUSTOM", action = "run")
            mainScheduler.runCurrent()

            assertThat(shadowOf(harness.webView).lastEvaluatedJavascript).contains("ownid.callback({\"error\"")
            assertThat(shadowOf(harness.webView).lastEvaluatedJavascript).contains("\"type\":\"UNKNOWN\"")
            assertThat(shadowOf(harness.webView).lastEvaluatedJavascript).contains("\"message\":\"plugin failed\"")
        } finally {
            harness.cancel()
        }
    }

    @Test
    fun detachCancelsInFlightPluginWorkBeforeCallbackEvaluation() = runTest {
        val mainScheduler = TestCoroutineScheduler()
        val plugin = GatedRuntimeWebBridgePlugin(id = "custom")
        val harness = webBridgeHarness(plugin = plugin, mainScheduler = mainScheduler)
        try {
            harness.attachSuccessfully()

            harness.postMessage(pluginId = "CUSTOM", action = "run")
            mainScheduler.runCurrent()
            assertThat(plugin.events).containsExactly("CUSTOM:run:start")

            harness.bridge.detach()
            mainScheduler.runCurrent()

            assertThat(plugin.events).containsExactly("CUSTOM:run:start", "CUSTOM:run:cancel").inOrder()
            assertThat(shadowOf(harness.webView).lastEvaluatedJavascript).isNull()
        } finally {
            harness.cancel()
        }
    }

    @Test
    fun messagesForSamePluginKeyAreSerialized() = runTest {
        val mainScheduler = TestCoroutineScheduler()
        val firstResult = CompletableDeferred<WebBridgePlugin.Result>()
        val secondResult = CompletableDeferred<WebBridgePlugin.Result>()
        val plugin = GatedRuntimeWebBridgePlugin(
            id = "custom",
            results = mutableListOf(firstResult, secondResult)
        )
        val harness = webBridgeHarness(plugin = plugin, mainScheduler = mainScheduler)
        try {
            harness.attachSuccessfully()

            harness.postMessage(pluginId = "CUSTOM", action = "first", callbackPath = "ownid.first")
            harness.postMessage(pluginId = "CUSTOM", action = "second", callbackPath = "ownid.second")
            mainScheduler.runCurrent()

            assertThat(plugin.events).containsExactly("CUSTOM:first:start")
            assertThat(shadowOf(harness.webView).lastEvaluatedJavascript).isNull()

            firstResult.complete(WebBridgePlugin.Result(success = buildJsonObject { put("first", true) }))
            mainScheduler.runCurrent()

            assertThat(plugin.events).containsExactly("CUSTOM:first:start", "CUSTOM:first:finish", "CUSTOM:second:start").inOrder()
            assertThat(shadowOf(harness.webView).lastEvaluatedJavascript).isEqualTo("""ownid.first({"first":true})""")

            secondResult.complete(WebBridgePlugin.Result(success = buildJsonObject { put("second", true) }))
            mainScheduler.runCurrent()

            assertThat(plugin.events)
                .containsExactly("CUSTOM:first:start", "CUSTOM:first:finish", "CUSTOM:second:start", "CUSTOM:second:finish")
                .inOrder()
            assertThat(shadowOf(harness.webView).lastEvaluatedJavascript).isEqualTo("""ownid.second({"second":true})""")
        } finally {
            harness.cancel()
        }
    }

    @Test
    fun messagesForDifferentPluginKeysProceedIndependently() = runTest {
        val mainScheduler = TestCoroutineScheduler()
        val blockedPlugin = GatedRuntimeWebBridgePlugin(id = "custom")
        val fastPlugin = RecordingRuntimeWebBridgePlugin(
            id = "other",
            result = WebBridgePlugin.Result(success = buildJsonObject { put("other", true) })
        )
        val harness = webBridgeHarness(plugins = listOf(blockedPlugin, fastPlugin), mainScheduler = mainScheduler)
        try {
            harness.attachSuccessfully()

            harness.postMessage(pluginId = "CUSTOM", action = "run", callbackPath = "ownid.custom")
            harness.postMessage(pluginId = "OTHER", action = "run", callbackPath = "ownid.other")
            mainScheduler.runCurrent()

            assertThat(blockedPlugin.events).containsExactly("CUSTOM:run:start")
            assertThat(fastPlugin.messages).hasSize(1)
            assertThat(shadowOf(harness.webView).lastEvaluatedJavascript).isEqualTo("""ownid.other({"other":true})""")
        } finally {
            harness.cancel()
        }
    }

    private fun webBridgeHarness(
        plugin: WebBridgePlugin = RecordingRuntimeWebBridgePlugin("custom"),
        plugins: List<WebBridgePlugin> = listOf(plugin),
        appConfigProvider: MutableAppConfigProvider = MutableAppConfigProvider(),
        mainScheduler: TestCoroutineScheduler = TestCoroutineScheduler(),
        configScheduler: TestCoroutineScheduler = TestCoroutineScheduler(),
        logger: OwnIdLogRouter? = null
    ): WebBridgeHarness {
        val mainDispatcher = StandardTestDispatcher(mainScheduler)
        val configDispatcher = StandardTestDispatcher(configScheduler)
        DispatchersHolder.setMain(mainDispatcher)
        val instanceJob = Job()
        val activityController = Robolectric.buildActivity(Activity::class.java).setup()
        val uiContextProvider = WebBridgeUiContextProvider()
        val bridge = WebBridgeImpl(
            plugins = WebBridgePluginRegistryImpl(initialPlugins = plugins),
            appConfigProvider = appConfigProvider,
            serialization = WebBridgeRuntimeTestSerialization,
            uiContextProvider = uiContextProvider,
            defaultDispatcher = configDispatcher,
            instanceJob = instanceJob,
            logger = logger
        )
        return WebBridgeHarness(
            bridge = bridge,
            activityController = activityController,
            webView = WebView(activityController.get()),
            uiContextProvider = uiContextProvider,
            owner = TestLifecycleOwner(),
            configScheduler = configScheduler,
            instanceJob = instanceJob
        )
    }

    private data class WebBridgeHarness(
        val bridge: WebBridgeImpl,
        val activityController: ActivityController<Activity>,
        val webView: WebView,
        val uiContextProvider: WebBridgeUiContextProvider,
        val owner: TestLifecycleOwner,
        val configScheduler: TestCoroutineScheduler,
        val instanceJob: Job
    ) {
        val activity: Activity
            get() = activityController.get()

        fun attachSuccessfully() {
            val error = bridge.attach(webView, setOf("example.test"), owner)
            assertThat(error).isNull()
        }

        fun postMessage(
            pluginId: String,
            action: String,
            callbackPath: String = "ownid.callback"
        ) {
            ShadowOwnIdWebViewCompat.messageListeners.single().listener.onPostMessage(
                webView,
                WebMessageCompat("""{"pluginId":"$pluginId","action":"$action","callbackPath":"$callbackPath"}"""),
                Uri.parse("https://example.test"),
                true,
                NoopJavaScriptReplyProxy
            )
        }

        fun cancel() {
            bridge.detach()
            instanceJob.cancel()
            if (!activity.isDestroyed) activityController.destroy()
        }
    }

    private class TestLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)

        override val lifecycle: Lifecycle
            get() = registry

        init {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        }

        fun destroy() {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
    }

    private fun WebBridgeUiContextProvider.assertActivity(expected: Activity) {
        assertThat((current() as UiContextProvider.Result.Success).activity).isSameInstanceAs(expected)
    }

    private fun WebBridgeUiContextProvider.assertFailure(reason: UiContextResolutionException.Reason) {
        current().assertReason(reason)
    }

    private fun Throwable?.assertReason(reason: UiContextResolutionException.Reason) {
        assertThat(this).isInstanceOf(UiContextResolutionException::class.java)
        assertThat((this as UiContextResolutionException).reason).isEqualTo(reason)
    }

    private fun UiContextProvider.Result.assertReason(reason: UiContextResolutionException.Reason) {
        val error = (this as UiContextProvider.Result.Failure).error
        error.assertReason(reason)
    }

    private fun recordingRouter(entries: MutableList<LogEntry>): OwnIdLogRouter {
        val logger = object : OwnIdLogger {
            override val level: OwnIdLogger.Level = OwnIdLogger.Level.Verbose
            override val tag: String = "OwnID-WebBridge-Test"

            override fun log(level: OwnIdLogger.Level, className: String, message: String, cause: Throwable?) {
                entries += LogEntry(level = level, message = message)
            }
        }
        return OwnIdLogRouter(ownIdLoggerProvider = { logger }, serverLoggerProvider = { null })
    }

    private data class LogEntry(val level: OwnIdLogger.Level, val message: String)

    private object DispatchersHolder {
        private var mainSet: Boolean = false

        fun setMain(dispatcher: TestDispatcher) {
            if (mainSet) resetMainIfSet()
            kotlinx.coroutines.Dispatchers.setMain(dispatcher)
            mainSet = true
        }

        fun resetMainIfSet() {
            if (mainSet) {
                kotlinx.coroutines.Dispatchers.resetMain()
                mainSet = false
            }
        }
    }

    private class ThrowingRuntimeWebBridgePlugin(
        id: String,
        private val failure: Throwable
    ) : WebBridgePlugin {
        override val key: WebBridgePluginKey = WebBridgePluginKey.from(id)
        override val actions: List<String> = listOf("run")

        override suspend fun handle(message: WebBridgePlugin.Message): WebBridgePlugin.Result {
            throw failure
        }
    }

    private class GatedRuntimeWebBridgePlugin(
        id: String,
        private val results: MutableList<CompletableDeferred<WebBridgePlugin.Result>> = mutableListOf(CompletableDeferred())
    ) : WebBridgePlugin {
        override val key: WebBridgePluginKey = WebBridgePluginKey.from(id)
        override val actions: List<String> = listOf("run", "first", "second")
        val events: MutableList<String> = mutableListOf()

        override suspend fun handle(message: WebBridgePlugin.Message): WebBridgePlugin.Result {
            val eventPrefix = "${key.key}:${message.payload.action}"
            events += "$eventPrefix:start"
            return try {
                results.removeAt(0).await().also {
                    events += "$eventPrefix:finish"
                }
            } catch (error: kotlinx.coroutines.CancellationException) {
                events += "$eventPrefix:cancel"
                throw error
            }
        }
    }
}
