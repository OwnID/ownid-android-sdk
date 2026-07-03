package com.ownid.sdk.ui.webbridge.impl.plugins

import com.ownid.sdk.di.DIContainerResolver
import com.ownid.sdk.di.getOrNull
import com.ownid.sdk.model.Context
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.toWebBridgePayload
import com.ownid.sdk.ui.webbridge.WebBridgeContextPlugin
import com.ownid.sdk.ui.webbridge.WebBridgePlugin
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Internal CONTEXT WebBridge plugin backed by the scoped [Context] captured when the bridge is created.
 *
 * The plugin exposes the `CONTEXT` key with the `get` action only when a scoped context is available. Plugin ID and
 * action matching are case-insensitive. `get` ignores params and returns the WebBridge context payload:
 * `authz.loginId` for raw or typed login ID authz, `authz.accessToken` for access-token authz, and
 * `accountDisplayName` when present. Wrong plugin IDs, unknown actions, and missing context are returned as bridge
 * error payloads. The plugin depends only on the optional scoped [Context]; it does not read or write storage, app
 * configuration, or provider capabilities.
 */
internal class WebBridgeContextPluginImpl(
    private val context: Context?
) : WebBridgeContextPlugin {

    @InternalOwnIdAPI
    internal companion object {
        internal fun create(resolver: DIContainerResolver): WebBridgeContextPlugin = resolver.run {
            WebBridgeContextPluginImpl(
                context = getOrNull<Context>()
            )
        }
    }

    override fun getInjectionData(): Pair<String, List<String>>? =
        context?.let { key.id to actions }

    override suspend fun handle(message: WebBridgePlugin.Message): WebBridgePlugin.Result {
        if (key.id.equals(message.payload.pluginId, ignoreCase = true).not()) {
            return WebBridgePlugin.Result(error = buildJsonObject {
                put("message", "Wrong plugin ID: ${message.payload.pluginId}")
            })
        }

        return when (message.payload.action.uppercase()) {
            "GET" -> {
                val payload = context?.toWebBridgePayload() ?: return WebBridgePlugin.Result(error = buildJsonObject {
                    put("message", "Context is unavailable")
                })
                WebBridgePlugin.Result(success = payload)
            }

            else -> WebBridgePlugin.Result(error = buildJsonObject {
                put("message", "Unknown action: ${message.payload.action}")
            })
        }
    }
}
