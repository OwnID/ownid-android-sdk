package com.ownid.sdk.ui.webbridge

import android.webkit.WebView
import androidx.annotation.UiThread
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.ownid.sdk.capability.AppConfigProvider
import com.ownid.sdk.capability.Capability

/**
 * Enables bidirectional communication between the OwnID Web SDK in a [WebView] and the native OwnID SDK.
 *
 * The bridge injects JavaScript into trusted pages and exposes SDK functionality through plugin namespaces such as
 * `FIDO` or `STORAGE`. Create a new bridge for each [WebView] session, customize [plugins] before [attach], and call
 * [detach] when the host lifecycle does not already destroy the attached view.
 */
public interface WebBridge : Capability {

    /**
     * Bridge-scoped plugin registry.
     *
     * Use this registry to inspect or customize the concrete plugin instances that this bridge will expose when it is
     * attached. Register fresh plugin instances for this bridge only.
     *
     * The bridge captures this registry during [attach]. Later changes do not affect an already attached bridge.
     */
    public val plugins: WebBridgePluginRegistry

    /**
     * Injects the OwnID bridge into [webView].
     *
     * Usage and lifecycle:
     * - Call before loading content, or reload the page after a successful injection, so the page sees the bridge from document start.
     * - Uses [allowedOriginRules] first and then merges in the latest available server configuration `webView.allowedOrigins`.
     * - Registers message listeners and document-start scripts for the normalized effective origin set produced by that merge.
     * - Passes the same normalized origin set to plugins for subsequent bridge calls.
     * - Cannot be attached more than once after a successful injection. Create a fresh instance for each [WebView] session.
     * - Requires a [LifecycleOwner], either from the view tree or from [owner]. Detaches automatically when that lifecycle is destroyed.
     * - Must be called on the UI thread.
     *
     * Allowed origin rules:
     * - Accept entries in the form `scheme://host[:port]`, for example `https://example.com`, `https://login.example.com:443`, or `https://[2001:db8::1]`.
     * - Assume `https://` when the scheme is omitted.
     * - Support DNS names, IPv4 literals, and bracketed IPv6 literals.
     * - Allow subdomain wildcard rules only for DNS hosts.
     * - Reject rules with userinfo, path, query, fragment, trailing-dot hosts, or empty ports.
     * - Skip invalid rules and log them.
     * - Fail injection when no valid rules remain after normalization.
     * - Treat the global wildcard rule as allowing any origin; avoid it in production.
     *
     * When attaching very early during app startup, server configuration may still be unavailable. In startup-sensitive
     * integrations such as app-hosted WebView frameworks, pass required origins explicitly in [allowedOriginRules], or
     * wait for [AppConfigProvider.getOrFetchConfig] to complete before calling [attach] if you need server-provided origins deterministically.
     *
     * Failure behavior:
     * - Returns `null` on success.
     * - Returns a [Throwable] instead of throwing it.
     *
     * Plugins may enforce additional checks, such as main-frame-only handling or stricter origin validation. Allow only trusted origins.
     *
     * @param webView The [WebView] to inject the bridge into.
     * @param allowedOriginRules Explicit origins to merge first with the currently available server configuration origins.
     *     Injection fails if the normalized effective set is empty.
     * @param owner Optional [LifecycleOwner]. Required when the [WebView] is not attached to a view tree.
     * @return `null` on success, or the injection failure if the bridge could not be attached.
     */
    @UiThread
    public fun attach(
        webView: WebView,
        allowedOriginRules: Set<String> = emptySet(),
        owner: LifecycleOwner? = webView.findViewTreeLifecycleOwner()
    ): Throwable?

    /**
     * Detaches the bridge from the previously attached [WebView].
     *
     * This removes message listeners, stops this bridge's document-start scripts from applying to future page loads,
     * clears the bridge's captured plugin set, and cancels pending bridge work.
     *
     * Calling `detach()` on a bridge that is not currently attached is a no-op.
     *
     * This method must be called on the UI thread.
     */
    @UiThread
    public fun detach()
}
