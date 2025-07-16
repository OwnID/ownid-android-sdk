package com.ownid.sdk.internal.feature.webflow

import android.content.Intent
import android.os.Build
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.internal.component.OwnIdInternalLogger
import com.ownid.sdk.internal.feature.webflow.OwnIdFlowFeatureWebView.JsConstants.isJSException
import com.ownid.sdk.internal.feature.webflow.OwnIdFlowFeatureWebView.JsConstants.isJSLoadError

@InternalOwnIdAPI
internal class OwnIdFlowWebViewClient(private val webView: WebView, private val onError: (OwnIdException) -> Unit) : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        when {
            request.url.isJSLoadError() -> {
                OwnIdInternalLogger.logW(this, "shouldOverrideUrlLoading", "isJSLoadError: ${request.url}")
                onError.invoke(OwnIdException("Failed to load content"))
            }

            request.url.isJSException() -> {
                OwnIdInternalLogger.logW(this, "shouldOverrideUrlLoading", "isJSException: ${request.url}")
                val errorMessage = runCatching { request.url.getQueryParameter("ex") }.getOrElse { null } ?: request.url.toString()
                onError.invoke(OwnIdException(errorMessage))
            }

            else ->
                runCatching {
                    OwnIdInternalLogger.logD(this, "shouldOverrideUrlLoading", "Open in external browser: ${request.url}")
                    if ("https://gmail.com/".equals(request.url.toString(), ignoreCase = true)) {
                        runCatching {
                            val intent = view.context.packageManager.getLaunchIntentForPackage("com.google.android.gm")
                            view.context.startActivity(intent)
                        }.onFailure {
                            OwnIdInternalLogger.logI(this, "shouldOverrideUrlLoading", it.message, it)
                            view.context.startActivity(Intent(Intent.ACTION_VIEW, request.url))
                        }
                    } else {
                        view.context.startActivity(Intent(Intent.ACTION_VIEW, request.url))
                    }
                }.onFailure {
                    OwnIdInternalLogger.logI(this, "shouldOverrideUrlLoading", it.message, it)
                }
        }
        return true
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        super.onReceivedError(view, request, error)
        OwnIdInternalLogger.logI(this, "onReceivedError", "[${error.errorCode}] ${error.description}: ${request.url}")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean =
        if (view == webView) {
            OwnIdInternalLogger.logW(this, "onRenderProcessGone", "didCrash: ${detail?.didCrash()}")
            onError.invoke(OwnIdException("WebView's render process has exited"))
            true
        } else {
            super.onRenderProcessGone(view, detail)
        }
}