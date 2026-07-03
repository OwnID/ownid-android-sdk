@file:Suppress("unused")

package com.ownid.sdk.ui.webbridge.impl

import com.ownid.sdk.di.DIContainerResolver
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.ui.webbridge.WebBridgePlugin
import com.ownid.sdk.ui.webbridge.WebBridgePluginKey

@OptIn(InternalOwnIdAPI::class)
internal class TestWebBridgePlugin(
    override val key: WebBridgePluginKey,
    override val actions: List<String>
) : WebBridgePlugin {
    override suspend fun handle(message: WebBridgePlugin.Message): WebBridgePlugin.Result = WebBridgePlugin.Result()
}

@OptIn(InternalOwnIdAPI::class)
internal object NoopWebBridgeResolver : DIContainerResolver {
    override val scopeName: String = "NoopWebBridgeResolver"

    override fun canResolve(clazz: Class<*>): Boolean = false

    override fun getUnsatisfiedDependencies(clazz: Class<*>): List<String> = listOf("${clazz.name} [missing]")

    override fun <T : Any> getOrThrow(clazz: Class<T>): T = error("Unexpected dependency request: ${clazz.name}")

    override fun <T : Any> getOrNull(clazz: Class<T>): T? = null

    override fun <T : Any> getAllInstancesOf(clazz: Class<T>): List<T> = emptyList()
}
