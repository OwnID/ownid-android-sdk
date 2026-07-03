package com.ownid.sdk.ui.webbridge.impl

import com.ownid.sdk.di.DIContainerResolver
import com.ownid.sdk.di.getOrNull
import com.ownid.sdk.logger.OwnIdLogRouter
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.runCatchingExceptions
import com.ownid.sdk.ui.webbridge.WebBridgePlugin
import com.ownid.sdk.ui.webbridge.WebBridgePluginFactoryStore
import com.ownid.sdk.ui.webbridge.WebBridgePluginKey
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Mutable factory registry for plugins that should be installed into future WebBridge instances.
 *
 * This namespace-level template preserves replacement order, copies independently for scoped namespace views, and
 * skips failed or mismatched factories when creating plugins for one bridge.
 */
@OptIn(InternalOwnIdAPI::class)
internal class WebBridgePluginFactoryStoreImpl() : WebBridgePluginFactoryStore {

    private class Entry(
        val key: WebBridgePluginKey,
        val instantiate: (resolver: DIContainerResolver) -> WebBridgePlugin
    )

    private val lock = ReentrantLock()
    private val entriesByKey: LinkedHashMap<WebBridgePluginKey, Entry> = linkedMapOf()

    private constructor(entries: Collection<Entry>) : this() {
        entries.forEach { entry -> entriesByKey[entry.key] = entry }
    }

    override fun register(key: WebBridgePluginKey, instantiate: () -> WebBridgePlugin) = lock.withLock {
        entriesByKey[key] = Entry(key = key, instantiate = { instantiate() })
    }

    override fun unregister(key: WebBridgePluginKey): Unit = lock.withLock {
        entriesByKey.remove(key)
    }

    override fun has(key: WebBridgePluginKey): Boolean = lock.withLock {
        entriesByKey.containsKey(key)
    }

    /** Stores an SDK built-in factory for future bridge instances. */
    internal fun registerBuiltIn(key: WebBridgePluginKey, instantiate: (resolver: DIContainerResolver) -> WebBridgePlugin) = lock.withLock {
        entriesByKey[key] = Entry(key = key, instantiate = instantiate)
    }

    /** Creates concrete plugins for one bridge from the current factory template. */
    internal fun instantiateAll(resolver: DIContainerResolver): List<WebBridgePlugin> {
        val logger = resolver.getOrNull<OwnIdLogRouter>()
        val entries = lock.withLock { entriesByKey.values.toList() }
        return entries.mapNotNull { entry ->
            runCatchingExceptions {
                entry.instantiate(resolver).also { plugin ->
                    require(plugin.key == entry.key) { "Instantiated plugin key ${plugin.key} does not match definition key ${entry.key}" }
                }
            }.onFailure { error ->
                logger?.logW(this, "instantiateAll", "Failed to instantiate WebBridge plugin ${entry.key}: ${error.message}", error)
            }.getOrNull()
        }
    }

    /** Creates an independent factory template with the same definitions in the same order. */
    internal fun copyStore(): WebBridgePluginFactoryStoreImpl = lock.withLock {
        WebBridgePluginFactoryStoreImpl(entriesByKey.values.toList())
    }
}
