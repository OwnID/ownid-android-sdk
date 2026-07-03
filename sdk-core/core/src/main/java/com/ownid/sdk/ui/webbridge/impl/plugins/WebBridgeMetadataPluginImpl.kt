package com.ownid.sdk.ui.webbridge.impl.plugins

import com.ownid.sdk.capability.LocalInfo
import com.ownid.sdk.di.DIContainerResolver
import com.ownid.sdk.di.getOrThrow
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.ui.webbridge.WebBridgeMetadataPlugin
import com.ownid.sdk.ui.webbridge.WebBridgePlugin
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Internal METADATA WebBridge plugin that forwards SDK correlation metadata.
 *
 * The plugin exposes the `METADATA` key with the `get` action. Plugin ID and action matching are case-insensitive.
 * `get` ignores params and returns `{ "correlationId": ... }` from [LocalInfo]. Wrong plugin IDs and unknown actions
 * are returned as bridge error payloads. The plugin depends on [LocalInfo] only for the returned metadata and does not
 * expose broader environment, diagnostics, repository, or provider state to the hosted page.
 */
internal class WebBridgeMetadataPluginImpl(
    private val localInfo: LocalInfo
) : WebBridgeMetadataPlugin {

    @InternalOwnIdAPI
    internal companion object {
        internal fun create(resolver: DIContainerResolver): WebBridgeMetadataPlugin = resolver.run {
            WebBridgeMetadataPluginImpl(
                localInfo = getOrThrow<LocalInfo>()
            )
        }
    }

    override suspend fun handle(message: WebBridgePlugin.Message): WebBridgePlugin.Result {
        if (key.id.equals(message.payload.pluginId, ignoreCase = true).not()) {
            return WebBridgePlugin.Result(error = buildJsonObject { put("message", "Wrong plugin ID: ${message.payload.pluginId}") })
        }

        return when (message.payload.action.uppercase()) {
            "GET" -> WebBridgePlugin.Result(success = buildJsonObject { put("correlationId", localInfo.correlationId) })
            else -> WebBridgePlugin.Result(error = buildJsonObject { put("message", "Unknown action: ${message.payload.action}") })
        }
    }
}
