package com.ownid.sdk.namespace

import android.webkit.WebView
import com.ownid.sdk.OwnId
import com.ownid.sdk.di.DIContainer
import com.ownid.sdk.di.getOrThrow
import com.ownid.sdk.model.Context
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.ui.webbridge.WebBridge
import com.ownid.sdk.ui.webbridge.WebBridgePluginFactoryStore
import com.ownid.sdk.ui.webbridge.impl.WebBridgeImpl
import com.ownid.sdk.ui.webbridge.impl.WebBridgePluginFactoryStoreImpl

/**
 * OwnID WebBridge namespace for WebView integrations.
 *
 * Use this namespace to scope future bridge instances with [withContext] for [Context] values or [withProviders],
 * customize [defaultPluginFactories], and create a new [WebBridge] for each WebView session. Scope changes and default
 * factory edits affect bridges created from the returned namespace after the change; existing bridge instances are not
 * updated.
 *
 * Namespace handles are captured views bound to the SDK scope they were obtained from.
 * After [OwnId.destroy] or same-name reinitialization, previously returned namespace handles are invalid and should be
 * reacquired from [OwnId.webBridge] or the current [OwnIdInstance].
 *
 * Access this API via [OwnId.webBridge] or [OwnIdInstance.webBridge].
 */
public interface OwnIdWebBridge : OwnIdNamespace<OwnIdWebBridge> {

    /**
     * Creates a new [WebBridge] using the current namespace configuration.
     *
     * The returned bridge starts with fresh plugin instances created from the current [defaultPluginFactories].
     * Plugin definitions that cannot create an instance are skipped with a warning, so the rest of the bridge remains
     * usable.
     *
     * You can further adjust the returned bridge through [WebBridge.plugins] before calling [WebBridge.attach], for
     * example to add or replace plugins for that specific bridge instance.
     *
     * @return A new bridge instance for a single [WebView] session.
     */
    public fun create(): WebBridge

    /**
     * Default plugin factories used for future [WebBridge] instances created by this namespace.
     *
     * The default namespace starts with the built-in SDK plugins. Update this store to add, replace, or unregister
     * defaults for bridges created from this namespace. The store keeps factory functions and invokes them once for each
     * new bridge so every bridge receives fresh plugin instances.
     *
     * Scoped namespace views receive a copy of the current store, so later edits are isolated to that namespace handle.
     * Existing bridge instances are not affected.
     */
    public val defaultPluginFactories: WebBridgePluginFactoryStore
}

@InternalOwnIdAPI
internal val DIContainer.webBridgeNamespace: OwnIdWebBridge
    get() = OwnIdWebBridgeNamespace(
        container = this,
        pluginFactoryStore = getOrThrow<WebBridgePluginFactoryStoreImpl>().copyStore()
    )

@InternalOwnIdAPI
internal class OwnIdWebBridgeNamespace(
    override val container: DIContainer,
    private val pluginFactoryStore: WebBridgePluginFactoryStoreImpl
) : OwnIdWebBridge, OwnIdNamespaceSupport<OwnIdWebBridge> {

    override fun rebind(container: DIContainer): OwnIdWebBridge =
        OwnIdWebBridgeNamespace(container = container, pluginFactoryStore = pluginFactoryStore.copyStore())

    override fun create(): WebBridge = WebBridgeImpl.create(container, pluginFactoryStore.instantiateAll(container))

    override val defaultPluginFactories: WebBridgePluginFactoryStore
        get() = pluginFactoryStore
}
