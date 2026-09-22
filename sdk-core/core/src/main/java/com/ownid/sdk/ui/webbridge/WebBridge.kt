package com.ownid.sdk.ui.webbridge

import android.webkit.WebView
import androidx.annotation.UiThread
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.ownid.sdk.capability.Capability

/**
 * Enables bidirectional communication between the OwnID Web SDK in a [WebView] and the native OwnID SDK.
 *
 * Create a new bridge each time you attach one to a [WebView], and customize [plugins] before [attach]. The WebView
 * context must resolve to a live [android.app.Activity]. Call [detach] explicitly if the bridge should be removed
 * before the supplied [LifecycleOwner] is destroyed.
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
     * - Merges [allowedOriginRules] with server `webView.allowedOrigins` already observed by this bridge when [attach]
     *   runs. It does not fetch or wait for server configuration.
     * - Captures the normalized effective origin set for this attachment. Server updates observed later do not modify
     *   an attached bridge.
     * - After a successful attachment, this bridge cannot be attached again, including after [detach]. Create a fresh instance before
     *   calling [attach] again.
     * - Requires a live [LifecycleOwner], either from the view tree or from [owner]. Detaches automatically when that lifecycle is destroyed.
     * - Requires [webView] context to resolve to a live [android.app.Activity]. A finishing or destroyed Activity fails.
     *   The bridge binds that exact Activity and does not migrate to another owner.
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
     * Pass every required origin explicitly when the first attachment must succeed deterministically. If no valid
     * explicit or already observed server origin is available, [attach] returns a failure.
     *
     * Failure behavior:
     * - Returns `null` on success.
     * - Returns the attachment failure as a [Throwable]. If an attempt fails before the bridge has attached successfully,
     *   the bridge remains unattached and can be retried after correcting the cause.
     *
     * Plugins may enforce additional checks, such as main-frame-only handling or stricter origin validation. Allow only trusted origins.
     *
     * @param webView The [WebView] to attach to. Its context must resolve to a live [android.app.Activity].
     * @param allowedOriginRules Explicit origins merged with server origins already observed by this bridge. Injection
     *     fails if the normalized effective set is empty.
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
     * Detaches the bridge from the [WebView] and cancels pending bridge work.
     *
     * The [LifecycleOwner] supplied to [attach] does this automatically when destroyed. Call [detach] explicitly to
     * detach earlier. Calling it before a successful attachment or more than once is a no-op. This method must be called
     * on the UI thread.
     *
     * @param webViewRendererGone Set to `true` only after the attached WebView's renderer has terminated and that WebView
     *     must no longer be accessed.
     */
    @UiThread
    public fun detach(webViewRendererGone: Boolean = false)
}
