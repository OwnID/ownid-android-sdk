package com.ownid.sdk

import android.webkit.WebView
import androidx.annotation.MainThread
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner

/**
 * The OwnID WebView Bridge enables the OwnID Web SDK to leverage native capabilities of the native OwnID Android SDK.
 *
 * It accomplishes this by injecting JavaScript code that facilitates communication between the OwnID Web SDK and the native OwnID Android SDK.
 */
public interface OwnIdWebViewBridge {
    /**
     * Injects a JavaScript object into [WebView] instance, bridging it with this [OwnIdWebViewBridge] instance.
     * Typically called when WebView is created.
     *
     * The JavaScript object will be injected in any frame whose origin matches [allowedOriginRules].
     * Each [allowedOriginRules] entry must follow the format SCHEME "://" [ HOSTNAME_PATTERN [ ":" PORT ] ].
     * Wildcard rule ('*') that matches any origin is not supported.
     *
     * The [allowedOriginRules] will be appended with the values from OwnID application.
     *
     * Check more details as [WebViewCompat.addWebMessageListener](https://developer.android.com/reference/androidx/webkit/WebViewCompat#addWebMessageListener(android.webkit.WebView,java.lang.String,java.util.Set%3Cjava.lang.String%3E,androidx.webkit.WebViewCompat.WebMessageListener))
     *
     * A [LifecycleOwner] responsible for managing the given [webView] must be specified if [findViewTreeLifecycleOwner] returns `null` for [webView].
     *
     * This method must be called before loading WebView content.
     *
     * This method must be called on the main thread.
     *
     * @param webView              An instance of [WebView] to inject into.
     * @param allowedOriginRules   An optional set of allowed origin rules.
     * @param owner                An optional [LifecycleOwner] responsible for managing the given [webView].
     *
     * @throws IllegalStateException if called on a non-main thread or when attempting to attach WebView more than once.
     * @throws IllegalArgumentException if no lifecycle owner available.
     */
    @MainThread
    public fun injectInto(
        webView: WebView,
        allowedOriginRules: Set<String> = emptySet(),
        owner: LifecycleOwner? = webView.findViewTreeLifecycleOwner()
    )
}