package com.ownid.sdk.ui.webbridge

/**
 * Bridge-scoped registry of plugins used by a [WebBridge].
 *
 * Use this registry to inspect or adjust the plugins that a [WebBridge] will expose on its next [WebBridge.attach] call.
 * Add distinct [WebBridgePlugin] instances per bridge. Do not reuse the same plugin instance across multiple
 * [WebBridgePluginRegistry] objects or bridge instances.
 *
 * A [WebBridge] captures this registry during [WebBridge.attach].
 * Changes made after a successful attachment do not affect the already attached bridge instance.
 */
public interface WebBridgePluginRegistry {
    /**
     * Adds a [plugin] so it is exposed to the web page via its namespace and actions.
     *
     * If another plugin is already registered with the same [WebBridgePlugin.key], it is replaced.
     * The [plugin] instance must be owned by this registry only and must not be shared with another bridge.
     *
     * Changes made after a successful bridge attachment affect only this registry's future captured copies, not the already
     * attached bridge instance.
     */
    public fun add(plugin: WebBridgePlugin)

    /**
     * Removes a previously added plugin by its [WebBridgePlugin.key].
     *
     * Changes made after a successful bridge attachment affect only this registry's future captured copies, not the already
     * attached bridge instance.
     */
    public fun remove(key: WebBridgePluginKey)

    /**
     * Returns the registered plugin for [key], or `null` if none is registered.
     */
    public fun get(key: WebBridgePluginKey): WebBridgePlugin?

    /**
     * Returns the currently registered plugins in bridge injection order. Replacing a plugin keeps its existing slot.
     */
    public fun snapshot(): List<WebBridgePlugin>
}
