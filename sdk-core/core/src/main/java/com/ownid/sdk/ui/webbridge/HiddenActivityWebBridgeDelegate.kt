package com.ownid.sdk.ui.webbridge

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.annotation.ColorInt
import androidx.annotation.RequiresApi
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import com.ownid.sdk.di.DIContainerResolver
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
import com.ownid.sdk.ops.ui.WebBridgeUi.Defaults.isJSException
import com.ownid.sdk.ops.ui.WebBridgeUi.Defaults.isJSLoadError
import com.ownid.sdk.ui.OwnIdHiddenActivity
import com.ownid.sdk.ui.OwnIdHiddenActivity.Companion.closeActivity
import com.ownid.sdk.ui.OwnIdHiddenActivity.Companion.closeActivityWithError
import com.ownid.sdk.ui.capability.HiddenActivityLauncher
import com.ownid.sdk.ui.webbridge.HiddenActivityWebBridgeDelegate.Companion.createIntent
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Hidden-activity delegate that hosts a WebBridge operation WebView.
 *
 * The delegate is selected by intent extras created by [createIntent], resolves the active
 * [WebBridgeOperationController] from [OperationRegistry], and binds the Android activity lifecycle to the WebBridge
 * operation UI state. A late launch for an already completed operation closes the activity without creating a WebView.
 *
 * The hosted WebView belongs to this activity for the duration of the operation. The delegate applies the
 * operation-provided presentation state, injects the operation bridge before loading the resolved HTML, and routes
 * back navigation either within WebView history or to the operation cancellation callback.
 *
 * Activity teardown invokes the operation-provided detach callback and releases the hosted WebView. Navigation away
 * from the flow document is opened externally after blocking local/script schemes; JavaScript bootstrap failures and
 * render-process loss are surfaced as terminal WebView errors for the operation.
 */
@InternalOwnIdAPI
internal class HiddenActivityWebBridgeDelegate : HiddenActivityLauncher.Delegate {

    internal companion object {
        internal fun createIntent(context: Context, instanceName: InstanceName, operationID: OperationID): Intent =
            OwnIdHiddenActivity.createIntent(context, instanceName)
                .putExtra(KEY_ELITE_DELEGATE, true)
                .putExtra(OPERATION_TYPE_KEY, operationID.type.name)
                .putExtra(OPERATION_ID_KEY, operationID.id)

        private const val KEY_ELITE_DELEGATE = "com.ownid.sdk.key.ui.KEY_ELITE_DELEGATE"
        private const val OPERATION_TYPE_KEY = "com.ownid.sdk.key.OPERATION_TYPE_KEY"
        private const val OPERATION_ID_KEY = "com.ownid.sdk.key.OPERATION_ID_KEY"

        private const val KEY_WEB_VIEW_TAG = "com.ownid.sdk.internal.flow.KEY_WEB_VIEW_TAG"
    }

    override fun isThis(intent: Intent): Boolean = intent.getBooleanExtra(KEY_ELITE_DELEGATE, false)

    private lateinit var onBackPressedCallback: OnBackPressedCallback
    private var onWebViewDetach: (() -> Unit)? = null

    override fun onCreate(activity: ComponentActivity, instanceResolver: DIContainerResolver) {
        val ownIdLogRouter = OwnIdRootDIContainer.getOrNull<OwnIdLogRouter>()
        ownIdLogRouter?.logD(this, "onCreate", "Invoked: ${this.hashCode()}")

        activity.enableEdgeToEdge()
        activity.window.decorView.setBackgroundColor(Color.BLACK)
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).isAppearanceLightStatusBars = false

        val typeString = activity.intent.getStringExtra(OPERATION_TYPE_KEY) ?: "null"
        val type = runCatching { OperationType.valueOf(typeString) }.getOrNull()
        if (type == null) {
            activity.closeActivityWithError("${this.javaClass.simpleName} onCreate: No operation type found for [$typeString]")
            return
        }

        val idString = activity.intent.getStringExtra(OPERATION_ID_KEY)
        if (idString == null) {
            activity.closeActivityWithError("${this.javaClass.simpleName} onCreate: No operation ID found [$idString]")
            return
        }
        val uid = OperationID(type, idString)
        val instanceName = instanceResolver.getOrNull<InstanceName>()

        val operationRegistry = instanceResolver.getOrNull<OperationRegistry>()
        if (operationRegistry == null) {
            activity.closeActivityWithError("${this.javaClass.simpleName} onCreate: No operation registry found for $instanceName")
            return
        }

        @SuppressLint("SourceLockedOrientationActivity")
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        when (val controller = operationRegistry.current.value[uid]) {
            null -> {
                ownIdLogRouter?.logI(this, "onCreate", "Operation [$uid] is no longer active; closing late host launch")
                activity.closeActivity()
            }

            is WebBridgeOperationController -> configureEliteFlowOperation(activity, controller)

            else -> activity.closeActivityWithError("${this@HiddenActivityWebBridgeDelegate.javaClass.simpleName} onCreate: No supported operation found for $uid")
        }
    }

    private fun configureEliteFlowOperation(activity: ComponentActivity, controller: WebBridgeOperationController) {
        val ownIdLogRouter = OwnIdRootDIContainer.getOrNull<OwnIdLogRouter>()

        controller.state.onEach { state ->
            when (state) {
                WebBridgeOperation.State.Created -> {
                    ownIdLogRouter?.logD(this@HiddenActivityWebBridgeDelegate, "configureEliteFlowOperation", "Created")
                }

                is WebBridgeOperation.State.Active -> runCatchingExceptions {
                    ownIdLogRouter?.logD(this@HiddenActivityWebBridgeDelegate, "configureEliteFlowOperation", "Active")

                    activity.enableCustomEdgeToEdge(
                        statusBarColor = state.uiState.statusBarColor ?: Color.WHITE,
                        navigationBarColor = state.uiState.navigationBarColor ?: Color.WHITE
                    )

                    val webView = WebView(activity).apply {
                        tag = KEY_WEB_VIEW_TAG

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

                        webViewClient = EliteWebViewClient(this, ownIdLogRouter) { details ->
                            state.uiState.onWebViewTerminalError(null, details)
                        }
                    }

                    activity.setContentView(
                        webView = webView,
                        statusBarColor = state.uiState.statusBarColor ?: Color.WHITE,
                        navigationBarColor = state.uiState.navigationBarColor ?: Color.WHITE
                    )

                    onWebViewDetach = state.uiState.onWebViewDetach

                    ViewCompat.setOnApplyWindowInsetsListener(webView) { v, windowInsets ->
                        val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                        v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                            topMargin = insets.top
                            leftMargin = insets.left
                            bottomMargin = insets.bottom
                            rightMargin = insets.right
                        }
                        WindowInsetsCompat.CONSUMED
                    }

                    onBackPressedCallback = object : OnBackPressedCallback(enabled = true) {
                        override fun handleOnBackPressed() {
                            ownIdLogRouter?.logD(this@HiddenActivityWebBridgeDelegate, "OnBackPressedCallback.handleOnBackPressed", "Invoked")
                            if (webView.canGoBack()) {
                                webView.goBack()
                            } else {
                                state.uiState.onWebViewCancel(Reason.UserClose("User navigated back"))
                            }
                        }
                    }
                    activity.onBackPressedDispatcher.addCallback(onBackPressedCallback)

                    state.uiState.doWebViewBridgeInject(webView)
                    webView.loadDataWithBaseURL(state.uiState.baseUrl, state.uiState.html, "text/html", null, null)
                }.onFailure { cause ->
                    ownIdLogRouter?.logW(this@HiddenActivityWebBridgeDelegate, "onCreate", "Failed to load WebBridge HTML: ${cause.message}", cause)
                    state.uiState.onWebViewTerminalError(cause, "Failed to load HTML: ${cause.message}")
                }

                is WebBridgeOperation.State.Completed -> {
                    ownIdLogRouter?.logD(this@HiddenActivityWebBridgeDelegate, "configureEliteFlowOperation", "Completed")
                    activity.closeActivity()
                }
            }
        }
            .catch {
                // TODO Revisit cancellation handling here. This delegate currently treats collector
                // cancellation as a close signal because the hidden activity itself can be the source of operation
                // termination (for example system/UI-driven activity closure). The current orientation constraint reduces
                // activity recreation during rotation, but that mitigation will not hold on newer Android versions
                // (notably Android 17). We need a lifecycle-aware distinction between "activity is finishing and should
                // close the operation" and "collector was canceled due to recreation/teardown and should not surface as
                // an operation error" before narrowing this path further. WebView recreation is an issue too.
                ownIdLogRouter?.logW(
                    this@HiddenActivityWebBridgeDelegate,
                    "configureEliteFlowOperation",
                    "WebBridge operation collection failed: ${it.message}",
                    it
                )
                activity.closeActivityWithError("${this.javaClass.simpleName} configureEliteFlowOperation: ${it.message}")
            }
            .launchIn(activity.lifecycleScope)
    }

    override fun onDestroy(activity: ComponentActivity) {
        OwnIdRootDIContainer.getOrNull<OwnIdLogRouter>()?.logD(this, "onDestroy", "Invoked")

        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

        if (::onBackPressedCallback.isInitialized) onBackPressedCallback.remove()

        val content = activity.findViewById<ViewGroup>(android.R.id.content)
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
                OwnIdRootDIContainer.getOrNull<OwnIdLogRouter>()?.logI(this, "onDestroy", "Failed to stop WebView during cleanup: ${it.message}", it)
            }

            runCatchingExceptions { (webView.parent as? ViewGroup)?.removeView(webView) }
                .onFailure {
                    OwnIdRootDIContainer.getOrNull<OwnIdLogRouter>()?.logI(this, "onDestroy", "Failed to detach WebView from parent: ${it.message}", it)
                }

            runCatchingExceptions { webView.destroy() }
                .onFailure { OwnIdRootDIContainer.getOrNull<OwnIdLogRouter>()?.logI(this, "onDestroy", "Failed to destroy WebView: ${it.message}", it) }
        }
    }

    internal class EliteWebViewClient(
        private val webView: WebView,
        private val logger: OwnIdLogRouter?,
        private val onError: (String) -> Unit
    ) : WebViewClient() {

        // NOTE:
        // We intentionally intercept all navigations and open them externally.
        // This prevents the Elite WebView from leaving the flow HTML and avoids
        // state drift. If in-web navigation is ever required, either relax this
        // policy (e.g., allow same-origin) or ensure Elite flow state is preserved.
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            when {
                request.url.isJSLoadError() -> {
                    logger?.logW(this, "shouldOverrideUrlLoading", "isJSLoadError: ${request.url}")
                    onError("Failed to load content: ${request.url}")
                }

                request.url.isJSException() -> {
                    logger?.logW(this, "shouldOverrideUrlLoading", "isJSException: ${request.url}")
                    val errorMessage = runCatching { request.url.getQueryParameter("ex") }.getOrElse { null } ?: request.url.toString()
                    onError.invoke("JavaScript exception: $errorMessage")
                }

                else ->
                    runCatching {
                        val uri = request.url
                        val blockedSchemes = setOf("javascript", "data", "file", "content", "intent")
                        val scheme = uri.scheme?.lowercase()

                        if (scheme != null && blockedSchemes.contains(scheme)) {
                            logger?.logW(this, "shouldOverrideUrlLoading", "Blocked URL by scheme [$scheme]: $uri")
                            return@runCatching
                        }

                        logger?.logD(this, "shouldOverrideUrlLoading", "Open in external browser: ${request.url}")
                        val host = uri.host?.lowercase()
                        if (host == "gmail.com" || host == "www.gmail.com") {
                            val launchedGmail = runCatching {
                                val gmailIntent = view.context.packageManager.getLaunchIntentForPackage("com.google.android.gm")
                                if (gmailIntent == null) {
                                    false
                                } else {
                                    view.context.startActivity(gmailIntent)
                                    true
                                }
                            }.onFailure {
                                logger?.logI(this, "shouldOverrideUrlLoading", "Failed to launch Gmail app: ${it.message}", it)
                            }.getOrDefault(false)

                            if (launchedGmail.not()) {
                                openInExternal(view.context, uri)
                            }
                        } else {
                            openInExternal(view.context, uri)
                        }
                    }.onFailure {
                        logger?.logI(this, "shouldOverrideUrlLoading", "Failed to open external URL: ${it.message}", it)
                    }
            }
            return true
        }

        private fun openInExternal(context: Context, uri: Uri) {
            val externalIntent = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
            if (externalIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(externalIntent)
            } else {
                logger?.logW(this, "shouldOverrideUrlLoading", "No activity to open URL: $uri")
            }
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            super.onReceivedError(view, request, error)
            logger?.logI(this, "onReceivedError", "[${error.errorCode}] ${error.description}: ${request.url}")
        }

        @RequiresApi(Build.VERSION_CODES.O)
        override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean =
            if (view == webView) {
                logger?.logW(this, "onRenderProcessGone", "WebView render process gone. [didCrash: ${detail?.didCrash()}]")
                onError.invoke("WebView render process gone. [didCrash: ${detail?.didCrash()}]")
                true
            } else {
                super.onRenderProcessGone(view, detail)
            }
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                isAppearanceLightStatusBars = Color.luminance(statusBarColor) > 0.5f
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                isAppearanceLightNavigationBars = Color.luminance(navigationBarColor) > 0.5f
            }
        }
    }

    private fun ComponentActivity.setContentView(webView: WebView, @ColorInt statusBarColor: Int, @ColorInt navigationBarColor: Int) {
        val topView = View(this).apply { setBackgroundColor(statusBarColor) }
        val bottomView = View(this).apply { setBackgroundColor(navigationBarColor) }

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(topView, LinearLayout.LayoutParams.MATCH_PARENT, 0)
            addView(webView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f))
            addView(bottomView, LinearLayout.LayoutParams.MATCH_PARENT, 0)
        }

        setContentView(rootLayout)

        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                topView.updateLayoutParams<LinearLayout.LayoutParams> { height = insets.top }
                bottomView.updateLayoutParams<LinearLayout.LayoutParams> { height = insets.bottom }
            } else {
                webView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    setMargins(insets.left, insets.top, insets.right, insets.bottom)
                }
            }
            WindowInsetsCompat.CONSUMED
        }
    }
}
