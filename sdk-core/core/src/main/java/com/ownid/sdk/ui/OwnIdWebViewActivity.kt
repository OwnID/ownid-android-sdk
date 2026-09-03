package com.ownid.sdk.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.annotation.ColorInt
import androidx.annotation.RestrictTo
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import com.ownid.sdk.OwnId
import com.ownid.sdk.di.OwnIdRootDIContainer
import com.ownid.sdk.di.getOrNull
import com.ownid.sdk.logger.OwnIdLogRouter
import com.ownid.sdk.model.InstanceName
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.OperationID
import com.ownid.sdk.model.OperationType
import com.ownid.sdk.model.Reason
import com.ownid.sdk.model.runCatchingExceptions
import com.ownid.sdk.ops.OperationRegistry
import com.ownid.sdk.ops.WebBridgeOperation
import com.ownid.sdk.ops.WebBridgeOperationController
import com.ownid.sdk.ui.webbridge.EliteWebViewClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Hosts the SDK-managed WebView for one active WebBridge operation.
 *
 * Configuration changes declared for this Activity are handled in place so the live WebView and its JavaScript session
 * survive. A host recreated with saved state closes instead of reconstructing a partial WebView session. Teardown
 * detaches the bridge and releases the WebView; operation callbacks remain responsible for settling the result. A late
 * launch for an operation that is no longer active closes without creating a WebView.
 */
@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class OwnIdWebViewActivity : ComponentActivity() {

    internal companion object {
        internal fun createIntent(context: Context, instanceName: InstanceName, operationID: OperationID): Intent =
            OwnIdActivityHostProtocol.createIntent(context, OwnIdWebViewActivity::class.java, instanceName)
                .putExtra(OPERATION_TYPE_KEY, operationID.type.name)
                .putExtra(OPERATION_ID_KEY, operationID.id)

        private const val OPERATION_TYPE_KEY = "com.ownid.sdk.key.OPERATION_TYPE_KEY"
        private const val OPERATION_ID_KEY = "com.ownid.sdk.key.OPERATION_ID_KEY"
        private const val KEY_WEB_VIEW_TAG = "com.ownid.sdk.internal.flow.KEY_WEB_VIEW_TAG"
    }

    private var onBackPressedCallback: OnBackPressedCallback? = null
    private var operationController: WebBridgeOperationController? = null
    private var onWebViewDetach: (() -> Unit)? = null
    private var logRouter: OwnIdLogRouter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.decorView.setBackgroundColor(Color.WHITE)
        enableCustomEdgeToEdge(statusBarColor = Color.WHITE, navigationBarColor = Color.WHITE)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        OwnIdActivityHostProtocol.LaunchLock.onHostCreated()

        if (savedInstanceState != null) {
            logRouter = OwnIdRootDIContainer.getOrNull<OwnIdLogRouter>()
            logRouter?.logD(this, "onCreate", "Invoked: ${this.hashCode()}, restored: true")
            closeActivity()
            return
        }

        val instanceName = OwnIdActivityHostProtocol.instanceNameOrNull(intent) ?: run {
            logRouter = OwnIdRootDIContainer.getOrNull<OwnIdLogRouter>()
            logRouter?.logD(this, "onCreate", "Invoked: ${this.hashCode()}, restored: false")
            closeActivityWithError("${this.javaClass.simpleName} onCreate: No instance name found")
            return
        }
        val instanceResolver = OwnId.getInstanceContainer(instanceName) ?: run {
            logRouter = OwnIdRootDIContainer.getOrNull<OwnIdLogRouter>()
            logRouter?.logD(this, "onCreate", "Invoked: ${this.hashCode()}, restored: false")
            closeActivityWithError("${this.javaClass.simpleName} onCreate: No instance found for $instanceName")
            return
        }
        logRouter = instanceResolver.getOrNull<OwnIdLogRouter>()
        logRouter?.logD(this, "onCreate", "Invoked: ${this.hashCode()}, restored: false")

        val typeString = intent.getStringExtra(OPERATION_TYPE_KEY) ?: "null"
        val type = runCatching { OperationType.valueOf(typeString) }.getOrNull()
        if (type == null) {
            closeActivityWithError("${this.javaClass.simpleName} onCreate: No operation type found for [$typeString]")
            return
        }

        val idString = intent.getStringExtra(OPERATION_ID_KEY)
        if (idString == null) {
            closeActivityWithError("${this.javaClass.simpleName} onCreate: No operation ID found [$idString]")
            return
        }
        val uid = OperationID(type, idString)

        val operationRegistry = instanceResolver.getOrNull<OperationRegistry>()
        if (operationRegistry == null) {
            closeActivityWithError("${this.javaClass.simpleName} onCreate: No operation registry found for $instanceName")
            return
        }

        when (val controller = operationRegistry.current.value[uid]) {
            null -> {
                logRouter?.logI(this, "onCreate", "Operation [$uid] is no longer active; closing late host launch")
                closeActivity()
            }

            is WebBridgeOperationController -> configureEliteFlowOperation(controller)

            else -> closeActivityWithError("${this.javaClass.simpleName} onCreate: No supported operation found for $uid")
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Keep the live WebView and refresh only its geometry and drawing for the new configuration.
        val content = findViewById<ViewGroup>(android.R.id.content)
        val webView = content?.findViewWithTag<WebView>(KEY_WEB_VIEW_TAG)
        if (webView != null) {
            (webView.parent as? View)?.let(ViewCompat::requestApplyInsets)
            webView.invalidate()
        }
    }

    private fun configureEliteFlowOperation(controller: WebBridgeOperationController) {
        operationController = controller
        val ownIdLogRouter = logRouter

        controller.state.onEach { state ->
            when (state) {
                WebBridgeOperation.State.Created -> {
                    ownIdLogRouter?.logD(this@OwnIdWebViewActivity, "configureEliteFlowOperation", "Created")
                }

                is WebBridgeOperation.State.Active -> runCatchingExceptions {
                    ownIdLogRouter?.logD(this@OwnIdWebViewActivity, "configureEliteFlowOperation", "Active")

                    enableCustomEdgeToEdge(
                        statusBarColor = state.uiState.statusBarColor ?: Color.WHITE,
                        navigationBarColor = state.uiState.navigationBarColor ?: Color.WHITE
                    )

                    val webView = WebView(this@OwnIdWebViewActivity).apply {
                        tag = KEY_WEB_VIEW_TAG
                        setBackgroundColor(Color.WHITE)

                        settings.apply {
                            @SuppressLint("SetJavaScriptEnabled")
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            allowFileAccess = false
                            allowContentAccess = false
                            layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            userAgentString = state.uiState.userAgent
                        }

                        webViewClient = EliteWebViewClient(
                            this,
                            ownIdLogRouter,
                            onError = { details -> state.uiState.onWebViewTerminalError(null, details) },
                            onRendererGone = { details ->
                                state.uiState.onWebViewRendererGone()
                                // Bridge cleanup after renderer loss is state-only; do not run ordinary WebView-bound
                                // detach from onDestroy.
                                onWebViewDetach = null

                                onBackPressedCallback?.remove()
                                onBackPressedCallback = null

                                state.uiState.onWebViewTerminalError(null, details)
                            }
                        )
                    }

                    setContentView(
                        webView = webView,
                        statusBarColor = state.uiState.statusBarColor ?: Color.WHITE,
                        navigationBarColor = state.uiState.navigationBarColor ?: Color.WHITE
                    )
                    webView.requestFocus()

                    val backPressedCallback = object : OnBackPressedCallback(enabled = true) {
                        override fun handleOnBackPressed() {
                            ownIdLogRouter?.logD(this@OwnIdWebViewActivity, "OnBackPressedCallback.handleOnBackPressed", "Invoked")
                            if (webView.canGoBack()) {
                                webView.goBack()
                            } else {
                                state.uiState.onWebViewCancel(Reason.UserClose("User navigated back"))
                            }
                        }
                    }
                    onBackPressedCallback = backPressedCallback
                    onBackPressedDispatcher.addCallback(backPressedCallback)

                    onWebViewDetach = state.uiState.onWebViewDetach
                    state.uiState.doWebViewBridgeInject(webView)
                    webView.loadDataWithBaseURL(state.uiState.baseUrl, state.uiState.html, "text/html", null, null)
                }.onFailure { cause ->
                    ownIdLogRouter?.logW(this@OwnIdWebViewActivity, "onCreate", "Failed to load WebBridge HTML: ${cause.message}", cause)
                    state.uiState.onWebViewTerminalError(cause, "Failed to load HTML: ${cause.message}")
                }

                is WebBridgeOperation.State.Completed -> {
                    ownIdLogRouter?.logD(this@OwnIdWebViewActivity, "configureEliteFlowOperation", "Completed")
                    closeActivity()
                }
            }
        }
            .catch {
                if (it is CancellationException) throw it
                ownIdLogRouter?.logW(this@OwnIdWebViewActivity, "configureEliteFlowOperation", "WebBridge operation collection failed: ${it.message}", it)
                closeActivityWithError("${this@OwnIdWebViewActivity.javaClass.simpleName} configureEliteFlowOperation: ${it.message}")
            }
            .launchIn(lifecycleScope)
    }

    override fun onDestroy() {
        logRouter?.logD(this, "onDestroy", "Invoked")

        onBackPressedCallback?.remove()
        onBackPressedCallback = null

        val content = findViewById<ViewGroup>(android.R.id.content)
        val webView = content?.findViewWithTag<WebView>(KEY_WEB_VIEW_TAG)
        if (webView != null) {
            onWebViewDetach?.invoke()
            onWebViewDetach = null
            runCatchingExceptions {
                webView.stopLoading()
                webView.webChromeClient = null
                webView.webViewClient = object : WebViewClient() {}
                webView.loadUrl("about:blank")
                webView.clearHistory()
                webView.removeAllViews()
            }.onFailure {
                logRouter?.logI(this, "onDestroy", "Failed to stop WebView during cleanup: ${it.message}", it)
            }

            runCatchingExceptions { (webView.parent as? ViewGroup)?.removeView(webView) }
                .onFailure {
                    logRouter?.logI(this, "onDestroy", "Failed to detach WebView from parent: ${it.message}", it)
                }

            runCatchingExceptions { webView.destroy() }
                .onFailure { logRouter?.logI(this, "onDestroy", "Failed to destroy WebView: ${it.message}", it) }
        } else {
            operationController
                ?.takeIf { it.state.value !is WebBridgeOperation.State.Completed }
                ?.abort(Reason.UserClose("Elite UI detached"))
        }
        operationController = null

        val keepLockForRecreation = isChangingConfigurations && !isFinishing
        OwnIdActivityHostProtocol.LaunchLock.onHostDestroyed(keepLockForRecreation)
        super.onDestroy()
    }

    private fun closeActivity() {
        OwnIdActivityHostProtocol.closeActivity(this, logRouter)
    }

    private fun closeActivityWithError(errorDetails: String) {
        OwnIdActivityHostProtocol.closeActivityWithError(this, errorDetails, logRouter)
    }

    @Suppress("DEPRECATION")
    private fun ComponentActivity.enableCustomEdgeToEdge(@ColorInt statusBarColor: Int, @ColorInt navigationBarColor: Int) {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Transparent on Android 15+
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            window.statusBarColor = statusBarColor
        }

        // No navigationBarColor for API < 26 as it works bad
        // Transparent on Android 15+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            window.navigationBarColor = navigationBarColor
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }

        WindowInsetsControllerCompat(window, window.decorView).run {
            isAppearanceLightStatusBars = ColorUtils.calculateLuminance(statusBarColor) > 0.5

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                isAppearanceLightNavigationBars = Color.luminance(navigationBarColor) > 0.5f
            }
        }
    }

    @SuppressLint("RtlHardcoded")
    private fun ComponentActivity.setContentView(webView: WebView, @ColorInt statusBarColor: Int, @ColorInt navigationBarColor: Int) {
        val leftProtectionView = View(this)
        val topProtectionView = View(this)
        val rightProtectionView = View(this)
        val bottomProtectionView = View(this)

        val rootLayout = FrameLayout(this).apply {
            setBackgroundColor(Color.WHITE)
            // Window insets use physical left/right edges, so these positions must not mirror in RTL.
            addView(topProtectionView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 0, Gravity.TOP))
            addView(leftProtectionView, FrameLayout.LayoutParams(0, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.LEFT))
            addView(rightProtectionView, FrameLayout.LayoutParams(0, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.RIGHT))
            addView(bottomProtectionView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 0, Gravity.BOTTOM))
            addView(webView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }

        setContentView(rootLayout)

        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, windowInsets ->
            val safeTypes = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            val handledTypes = safeTypes or WindowInsetsCompat.Type.ime()
            val status = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navigation = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val visibleIme = if (windowInsets.isVisible(WindowInsetsCompat.Type.ime())) {
                windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            } else {
                Insets.NONE
            }
            val protection = Insets.max(status, navigation)
            val safe = Insets.max(windowInsets.getInsets(safeTypes), visibleIme)

            leftProtectionView.setBackgroundColor(if (navigation.left > 0) navigationBarColor else statusBarColor)
            topProtectionView.setBackgroundColor(if (navigation.top > 0) navigationBarColor else statusBarColor)
            rightProtectionView.setBackgroundColor(if (navigation.right > 0) navigationBarColor else statusBarColor)
            bottomProtectionView.setBackgroundColor(if (navigation.bottom > 0) navigationBarColor else statusBarColor)
            leftProtectionView.updateLayoutParams<FrameLayout.LayoutParams> { width = protection.left }
            topProtectionView.updateLayoutParams<FrameLayout.LayoutParams> { height = protection.top }
            rightProtectionView.updateLayoutParams<FrameLayout.LayoutParams> { width = protection.right }
            bottomProtectionView.updateLayoutParams<FrameLayout.LayoutParams> { height = protection.bottom }
            webView.updateLayoutParams<FrameLayout.LayoutParams> { setMargins(safe.left, safe.top, safe.right, safe.bottom) }
            // The host applied these types; keep dispatching with zero values so WebView does not apply them again.
            WindowInsetsCompat.Builder(windowInsets)
                .setInsets(handledTypes, Insets.NONE)
                .build()
        }
        ViewCompat.requestApplyInsets(rootLayout)
    }
}
