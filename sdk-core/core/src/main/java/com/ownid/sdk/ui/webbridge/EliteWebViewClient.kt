package com.ownid.sdk.ui.webbridge

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import com.ownid.sdk.logger.OwnIdLogRouter
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.runCatchingExceptions
import com.ownid.sdk.ops.ui.WebBridgeUi.Defaults.isJSException
import com.ownid.sdk.ops.ui.WebBridgeUi.Defaults.isJSLoadError

@OptIn(InternalOwnIdAPI::class)
internal class EliteWebViewClient(
    private val webView: WebView,
    private val logger: OwnIdLogRouter?,
    private val onError: (String) -> Unit,
    private val onRendererGone: (details: String) -> Unit
) : WebViewClient() {

    // NOTE:
    // We intentionally intercept all navigations and open them externally.
    // This prevents the Elite WebView from leaving the flow HTML and avoids
    // state drift. If in-web navigation is ever required, either relax this
    // policy (e.g., allow same-origin) or ensure Elite flow state is preserved.
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = handleUrl(view, url.toUri())

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = handleUrl(view, request.url)

    private fun handleUrl(view: WebView, uri: Uri): Boolean {
        when {
            uri.isJSLoadError() -> {
                logger?.logW(this, "shouldOverrideUrlLoading", "isJSLoadError: $uri")
                onError("Failed to load content: $uri")
            }

            uri.isJSException() -> {
                logger?.logW(this, "shouldOverrideUrlLoading", "isJSException: $uri")
                val errorMessage = runCatching { uri.getQueryParameter("ex") }.getOrElse { null } ?: uri.toString()
                onError.invoke("JavaScript exception: $errorMessage")
            }

            else ->
                runCatching {
                    val blockedSchemes = setOf("javascript", "data", "file", "content", "intent")
                    val scheme = uri.scheme?.lowercase()

                    if (scheme != null && blockedSchemes.contains(scheme)) {
                        logger?.logW(this, "shouldOverrideUrlLoading", "Blocked URL by scheme [$scheme]: $uri")
                        return@runCatching
                    }

                    logger?.logD(this, "shouldOverrideUrlLoading", "Open in external browser: $uri")
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
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE))
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        super.onReceivedError(view, request, error)
        logger?.logI(this, "onReceivedError", "[${error.errorCode}] ${error.description}: ${request.url}")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean =
        if (view == webView) {
            val details = "WebView render process gone. [didCrash: ${detail?.didCrash()}]"
            logger?.logW(this, "onRenderProcessGone", details)
            onRendererGone(details)
            runCatchingExceptions { (webView.parent as? ViewGroup)?.removeView(webView) }
                .onFailure {
                    logger?.logI(this, "onRenderProcessGone", "Failed to detach terminated WebView from parent: ${it.message}", it)
                }
            runCatchingExceptions { webView.destroy() }
                .onFailure {
                    logger?.logI(this, "onRenderProcessGone", "Failed to destroy terminated WebView: ${it.message}", it)
                }
            true
        } else {
            super.onRenderProcessGone(view, detail)
        }
}
