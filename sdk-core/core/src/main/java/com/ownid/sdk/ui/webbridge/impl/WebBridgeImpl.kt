package com.ownid.sdk.ui.webbridge.impl

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebView
import androidx.annotation.UiThread
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.webkit.ScriptHandler
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.ownid.sdk.capability.AppConfigProvider
import com.ownid.sdk.capability.DispatcherProvider
import com.ownid.sdk.capability.Serialization
import com.ownid.sdk.di.DIContainerResolver
import com.ownid.sdk.di.getOrNull
import com.ownid.sdk.di.getOrThrow
import com.ownid.sdk.logger.OwnIdLogRouter
import com.ownid.sdk.model.AppConfig
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.runCatchingExceptions
import com.ownid.sdk.ui.webbridge.WebBridge
import com.ownid.sdk.ui.webbridge.WebBridgePlugin
import com.ownid.sdk.ui.webbridge.WebBridgePluginKey
import com.ownid.sdk.ui.webbridge.WebBridgePluginRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.lang.ref.WeakReference

/**
 * Android WebView-backed [WebBridge] implementation.
 *
 * A bridge instance is single-use: [attach] captures the currently registered plugins, merges explicit origins with the
 * latest server WebView configuration available in this instance, normalizes the effective allowlist, and enables native
 * bridge messaging for that allowlist before page content runs. Registry mutations after attachment do not affect the
 * attached WebView.
 *
 * The Android WebView boundary is owned by the caller-provided [LifecycleOwner]. Destroying the owner calls [detach].
 * Detach removes bridge-owned page messaging for future loads, clears the captured plugin set, and cancels in-flight
 * plugin work before callbacks are evaluated. Renderer termination uses state-only cleanup because the affected WebView
 * can no longer be accessed.
 *
 * Message handling accepts only main-frame messages from normalized allowed origins, with valid payloads and safe
 * callback paths, before invoking plugins. Plugin work is ordered per [WebBridgePluginKey].
 */
@OptIn(InternalOwnIdAPI::class)
internal class WebBridgeImpl(
    override val plugins: WebBridgePluginRegistry,
    private val appConfigProvider: AppConfigProvider,
    private val serialization: Serialization,
    defaultDispatcher: CoroutineDispatcher,
    instanceJob: Job,
    private val logger: OwnIdLogRouter? = null
) : WebBridge, CoroutineScope by CoroutineScope(defaultDispatcher + SupervisorJob(instanceJob) + CoroutineName("OwnId.WebBridge")) {

    private enum class AttachmentState { NEW, ATTACHED, DETACHED }

    companion object {
        internal fun create(resolver: DIContainerResolver, initialPlugins: List<WebBridgePlugin>): WebBridge = resolver.runCatchingExceptions {
            WebBridgeImpl(
                plugins = WebBridgePluginRegistryImpl(initialPlugins = initialPlugins),
                appConfigProvider = getOrThrow<AppConfigProvider>(),
                serialization = getOrThrow<Serialization>(),
                defaultDispatcher = getOrThrow<DispatcherProvider>().defaultDispatcher(),
                instanceJob = getOrThrow<Job>(),
                logger = getOrNull<OwnIdLogRouter>()
            )
        }.getOrElse { error -> InitFailureFallback(resolver.getOrNull<OwnIdLogRouter>(), error) }

        private val callbackPathPattern = Regex("^[a-zA-Z_$][0-9a-zA-Z_$]*(\\.[a-zA-Z_$][0-9a-zA-Z_$]*)*$")

        private const val JS_HANDLER_NAME = "__ownidNativeBridgeHandler"
        private fun createNativeBridgeJS(namespaces: String): String = """
            (function () {
                try {
                    // Only inject into the main frame; cross-origin safe guard
                    if (window.top !== window) { return; }
                } catch (e) { return; }

                if (!window.__ownidNativeBridge) {
                    window.__ownidNativeBridge = {
                        getNamespaces: function getNamespaces() { return '$namespaces'; },
                        invokeNative: function invokeNative(namespace, action, callbackPath, params, metadata) {
                            try {
                                window.$JS_HANDLER_NAME.postMessage(JSON.stringify({ pluginId: namespace, action, callbackPath, params }));
                            } catch (error) {
                                setTimeout(function () { try { eval(callbackPath + '(false);'); } catch (_) {} });
                            }
                        }
                    };
                }
            })();
        """.trimIndent()
    }

    private var attachmentState: AttachmentState = AttachmentState.NEW
    private val pluginLocks: MutableMap<WebBridgePluginKey, Mutex> = mutableMapOf()
    private var attachedOwner: LifecycleOwner? = null
    private var attachedWebViewRef: WeakReference<WebView>? = null
    private var destroyObserver: DefaultLifecycleObserver? = null
    private var documentStartScript: ScriptHandler? = null

    @Volatile
    private var attachedPluginsByKey: Map<WebBridgePluginKey, WebBridgePlugin> = emptyMap()

    @Volatile
    private var latestWebViewConfig: AppConfig.WebViewConfig? = null

    init {
        appConfigProvider.config
            .onEach { latestWebViewConfig = it.webView }
            .launchIn(this)
    }

    @Synchronized
    @SuppressLint("RequiresFeature")
    override fun attach(webView: WebView, allowedOriginRules: Set<String>, owner: LifecycleOwner?): Throwable? {
        var localScriptHandler: ScriptHandler? = null
        var snapshotPublished = false

        return try {
            require(attachmentState == AttachmentState.NEW) {
                "This WebBridge instance can be injected only once. Create a new WebBridge via InstanceScope for each WebView session."
            }
            val registeredPlugins = plugins.snapshot()
            require(owner != null) { "WebView LifecycleOwner must not be null." }
            require(owner.lifecycle.currentState != Lifecycle.State.DESTROYED) { "WebView Lifecycle must not be destroyed." }
            require(WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) { "WEB_MESSAGE_LISTENER not supported." }
            require(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) { "DOCUMENT_START_SCRIPT not supported." }
            registeredPlugins.ifEmpty { logger?.logW(this, "attach", "No plugins available for injection.") }

            val observer = object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    detach()
                }
            }
            attachedOwner = owner
            destroyObserver = observer
            owner.lifecycle.addObserver(observer)

            val effectiveAllowedOriginRules = buildSet {
                addAll(allowedOriginRules)
                latestWebViewConfig?.allowedOrigins?.let(::addAll)
            }
            val normalization = OriginNormalizer.normalizeAllowedOriginRules(effectiveAllowedOriginRules)
            val validOriginRules = normalization.valid
            val skippedOriginRules = normalization.skipped
            if (skippedOriginRules.isNotEmpty()) {
                val skipped = skippedOriginRules.joinToString { "'$it'" }
                logger?.logW(this, "attach", "Skipped ${skippedOriginRules.size} invalid allowed origins: $skipped")
            }
            require(validOriginRules.isNotEmpty()) { "No valid allowed origins available" }

            if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_AUTHENTICATION)) {
                runCatchingExceptions {
                    WebSettingsCompat.setWebAuthenticationSupport(
                        webView.settings,
                        WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_FOR_APP
                    )
                }.onFailure { error ->
                    logger?.logW(this, "attach", "Failed to enable Web Authentication: ${error.message}", error)
                }
            }

            attachedPluginsByKey = registeredPlugins.associateBy(WebBridgePlugin::key)
            snapshotPublished = true

            // Version 1
            WebViewCompat.addWebMessageListener(webView, JS_HANDLER_NAME, validOriginRules) { view, message, sourceOrigin, isMainFrame, _ ->
                handlePostMessage(view, message, sourceOrigin, isMainFrame, validOriginRules, this.plus(Dispatchers.Main.immediate))
            }

            val namespacesToInject = registeredPlugins.mapNotNull { it.getInjectionData() }.associate { it.first to it.second }
            val namespaces = serialization.encodeToString(
                MapSerializer(String.serializer(), ListSerializer(String.serializer())), namespacesToInject
            )
            localScriptHandler = WebViewCompat.addDocumentStartJavaScript(webView, createNativeBridgeJS(namespaces), validOriginRules)

            documentStartScript = localScriptHandler
            attachedWebViewRef = WeakReference(webView)
            attachmentState = AttachmentState.ATTACHED
            logger?.logD(this, "attach", "WebBridge injected to origins $validOriginRules with namespaces: ${namespacesToInject.keys}")
            null
        } catch (ex: Exception) {
            clearLifecycleObserver()
            attachedWebViewRef = null
            if (snapshotPublished) {
                attachedPluginsByKey = emptyMap()
            }
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                runCatchingExceptions { localScriptHandler?.remove() }
            }
            if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                runCatchingExceptions { WebViewCompat.removeWebMessageListener(webView, JS_HANDLER_NAME) }
            }
            logger?.logW(this, "attach", "Injection failed: ${ex.message}", ex)
            ex
        }
    }

    @UiThread
    @Synchronized
    override fun detach(webViewRendererGone: Boolean) {
        clearLifecycleObserver()

        if (attachmentState != AttachmentState.ATTACHED) {
            logger?.logI(this, "detach", "Bridge already detached")
            return
        }

        if (!webViewRendererGone) {
            val attachedWebView = attachedWebViewRef?.get()
            if (attachedWebView != null) {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                    WebViewCompat.removeWebMessageListener(attachedWebView, JS_HANDLER_NAME)
                }
            } else {
                logger?.logI(this, "detach", "Attached WebView unavailable; running state-only cleanup")
            }
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                documentStartScript?.remove()
            }
        }
        documentStartScript = null
        attachedPluginsByKey = emptyMap()
        attachedWebViewRef = null

        attachmentState = AttachmentState.DETACHED
        cancel()
    }

    @Synchronized
    private fun clearLifecycleObserver() {
        attachedOwner?.let { owner ->
            destroyObserver?.let { observer ->
                runCatchingExceptions { owner.lifecycle.removeObserver(observer) }
                    .onFailure { error ->
                        logger?.logI(this, "clearLifecycleObserver", "Failed to remove lifecycle observer: ${error.message}", error)
                    }
            }
        }
        attachedOwner = null
        destroyObserver = null
    }

    @UiThread
    private fun handlePostMessage(
        webView: WebView,
        webMessage: WebMessageCompat,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        validOriginRules: Set<String>,
        scope: CoroutineScope
    ) {
        // Keep bridge-owned validation here so plugin calls only receive main-frame messages with accepted payloads
        // and callback paths.
        scope.launch {
            try {
                if (!isMainFrame) {
                    logger?.logI(this@WebBridgeImpl, "handlePostMessage", "Ignoring message from subframe: $sourceOrigin")
                    return@launch
                }
                val messageData = webMessage.data
                if (messageData == null) {
                    logger?.logW(this@WebBridgeImpl, "handlePostMessage", "Skipping message with null data")
                    return@launch
                }
                val payload = runCatchingExceptions { serialization.decodeFromString(WebBridgePlugin.Message.Payload.serializer(), messageData) }
                    .onFailure { error ->
                        val callbackPath = runCatchingExceptions {
                            serialization.parseToJsonElement(messageData).jsonObject["callbackPath"]?.jsonPrimitive?.content
                        }.getOrNull()

                        val pluginResult = WebBridgePlugin.Result(error = buildJsonObject { put("message", error.message) })
                        val resultJson = pluginResult.toResultString()

                        if (callbackPath == null || callbackPathPattern.matches(callbackPath).not()) {
                            logger?.logW(this@WebBridgeImpl, "handlePostMessage", "Skipping callback evaluation for invalid callback path: $callbackPath")
                        } else {
                            evaluateJavascriptCallback(webView, callbackPath, resultJson)
                        }
                    }
                    .getOrThrow()

                val action = "[${payload.pluginId}:${payload.action}]"
                logger?.logV(this@WebBridgeImpl, "handlePostMessage", "Received $action, origin: $sourceOrigin, isMainFrame: $isMainFrame")

                // Check callback path to prevent code injection
                if (callbackPathPattern.matches(payload.callbackPath).not()) {
                    logger?.logW(this@WebBridgeImpl, "handlePostMessage", "Skipping callback evaluation for invalid callback path: ${payload.callbackPath}")
                    return@launch
                }

                val pluginResult = try {
                    val pluginWithLock = synchronized(pluginLocks) {
                        val pluginKey = WebBridgePluginKey.from(id = payload.pluginId)
                        val plugin = attachedPluginsByKey[pluginKey]
                        plugin?.let {
                            val lock = pluginLocks.getOrPut(pluginKey) { Mutex() }
                            it to lock
                        }
                    }

                    if (pluginWithLock == null) {
                        WebBridgePlugin.Result(error = buildJsonObject { put("message", "Unknown plugin: ${payload.pluginId}") })
                    } else {
                        val (plugin, lock) = pluginWithLock
                        lock.withLock { plugin.handle(WebBridgePlugin.Message(webView, sourceOrigin, isMainFrame, validOriginRules, payload)) }
                    }
                } catch (error: CancellationException) {
                    logger?.logW(
                        this@WebBridgeImpl,
                        "handlePostMessage",
                        "WebBridge plugin handling canceled [plugin=${payload.pluginId}, action=${payload.action}]: ${error.message}",
                        error
                    )
                    WebBridgePlugin.Result(error = buildJsonObject { put("type", "ABORTED"); put("message", error.message) })
                } catch (error: Exception) {
                    logger?.logW(
                        this@WebBridgeImpl,
                        "handlePostMessage",
                        "WebBridge plugin handling failed [plugin=${payload.pluginId}, action=${payload.action}]: ${error.message}",
                        error
                    )
                    WebBridgePlugin.Result(error = buildJsonObject { put("message", error.message) })
                }
                ensureActive()

                val resultJson = pluginResult.toResultString()

                evaluateJavascriptCallback(webView, payload.callbackPath, resultJson)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                logger?.logW(this@WebBridgeImpl, "handlePostMessage", "WebBridge postMessage handling failed: ${error.message}", error)
            }
        }
    }

    private suspend fun evaluateJavascriptCallback(webView: WebView, callbackPath: String, resultJson: String) {
        withContext(Dispatchers.Main.immediate) {
            webView.evaluateJavascript("$callbackPath($resultJson)") { result ->
                if (result != "null") {
                    logger?.logW(this@WebBridgeImpl, "handlePostMessage", "Callback evaluation error: $result")
                }
            }
        }
    }

    private class InitFailureFallback(private val logger: OwnIdLogRouter?, private val initError: Throwable) : WebBridge {
        private class InitFailurePluginRegistry(private val logger: OwnIdLogRouter?, private val initError: Throwable) : WebBridgePluginRegistry {

            override fun add(plugin: WebBridgePlugin) {
                logger?.logD(this, "add", "InitFailureFallback no-op: ${plugin.key}. Initialization failed: ${initError.message}")
            }

            override fun remove(key: WebBridgePluginKey) {
                logger?.logD(this, "remove", "InitFailureFallback no-op: $key. Initialization failed: ${initError.message}")
            }

            override fun get(key: WebBridgePluginKey): WebBridgePlugin? {
                logger?.logD(this, "get", "InitFailureFallback no-op: $key. Initialization failed: ${initError.message}")
                return null
            }

            override fun snapshot(): List<WebBridgePlugin> = emptyList()
        }

        override val plugins: WebBridgePluginRegistry = InitFailurePluginRegistry(logger, initError)

        override fun attach(webView: WebView, allowedOriginRules: Set<String>, owner: LifecycleOwner?): Throwable {
            logger?.logW(this, "attach", "WebBridge initialization failed fallback: ${initError.message}", initError)
            return initError
        }

        override fun detach(webViewRendererGone: Boolean) {
            logger?.logD(this, "detach", "InitFailureFallback no-op. Initialization failed: ${initError.message}")
        }
    }
}
