package com.ownid.sdk.ui.webbridge.impl

import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.ui.webbridge.WebBridge
import com.ownid.sdk.ui.webbridge.WebBridgePlugin
import com.ownid.sdk.ui.webbridge.WebBridgePluginKey
import com.ownid.sdk.ui.webbridge.WebBridgePluginRegistry
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Thread-safe bridge-scoped plugin registry.
 *
 * This registry owns concrete plugin instances for one [WebBridge]. Initial plugins,
 * app-added plugins, and removals are keyed by [WebBridgePluginKey]. Replacing an existing key keeps that key's
 * original injection slot, so attachment order is deterministic even when a built-in plugin is replaced.
 *
 * The current plugin list is returned as an attachment-time copy. Later registry mutations do not rewrite a
 * previously returned copy.
 */
@OptIn(InternalOwnIdAPI::class)
internal class WebBridgePluginRegistryImpl(
    initialPlugins: List<WebBridgePlugin>
) : WebBridgePluginRegistry {

    private val lock = ReentrantLock()
    private val pluginsByKey: LinkedHashMap<WebBridgePluginKey, WebBridgePlugin> = linkedMapOf<WebBridgePluginKey, WebBridgePlugin>().apply {
        initialPlugins.forEach { plugin -> this[plugin.key] = plugin }
    }

    override fun add(plugin: WebBridgePlugin) {
        lock.withLock {
            pluginsByKey[plugin.key] = plugin
        }
    }

    override fun remove(key: WebBridgePluginKey) {
        lock.withLock {
            pluginsByKey.remove(key)
        }
    }

    override fun get(key: WebBridgePluginKey): WebBridgePlugin? = lock.withLock {
        pluginsByKey[key]
    }

    /**
     * Returns the current plugin instances in injection order as a stable copy for WebBridge attachment.
     */
    override fun snapshot(): List<WebBridgePlugin> = lock.withLock {
        pluginsByKey.values.toList()
    }
}
