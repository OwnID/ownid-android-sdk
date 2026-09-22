package com.ownid.sdk.ui.webbridge

import com.ownid.sdk.namespace.OwnIdWebBridge

/**
 * Mutable set of default plugin factories used for future [WebBridge] instances.
 *
 * This store belongs to an [OwnIdWebBridge] namespace. Factories are managed by [WebBridgePluginKey], and
 * registering the same key again replaces the previous plugin factory.
 *
 * Changes are namespace-scoped. They affect only bridges created from that namespace after the update.
 */
public interface WebBridgePluginFactoryStore {
    /**
     * Registers a default plugin factory for [key].
     *
     * The [instantiate] block must create a fresh plugin instance each time a bridge is created. Construct the plugin
     * inside the block; do not return a captured singleton or an instance registered with another bridge. Freshness is
     * the caller's responsibility and is not checked by the SDK. If another factory is already registered for the same
     * [key], it is replaced in the same factory slot. The factory is stored now and invoked later when a bridge is
     * created.
     *
     * If [instantiate] throws, or if the created plugin reports a different [WebBridgePlugin.key], bridge creation
     * logs a warning and skips that plugin instead of failing the whole bridge.
     */
    public fun register(key: WebBridgePluginKey, instantiate: () -> WebBridgePlugin)

    /** Unregisters the factory registered for [key], if present. */
    public fun unregister(key: WebBridgePluginKey)

    /** Returns `true` if a factory is registered for [key]. */
    public fun has(key: WebBridgePluginKey): Boolean
}
