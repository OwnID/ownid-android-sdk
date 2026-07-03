package com.ownid.sdk.ui.webbridge.impl.plugins

import com.ownid.sdk.capability.Serialization
import com.ownid.sdk.capability.SignInWithGoogle
import com.ownid.sdk.capability.SignInWithSocial
import com.ownid.sdk.capability.UiContextProvider
import com.ownid.sdk.di.DIContainerResolver
import com.ownid.sdk.di.getOrNull
import com.ownid.sdk.di.getOrThrow
import com.ownid.sdk.model.ChallengeId
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.ui.webbridge.WebBridgePlugin
import com.ownid.sdk.ui.webbridge.WebBridgeSocialPlugin
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Bridge-scoped SOCIAL plugin backed by configured social sign-in providers.
 *
 * This is an internal WebBridge contract for hosted pages, not a stable app-developer API. Injection metadata advertises
 * only provider actions available to this bridge. This implementation exposes the `Google` action when [SignInWithGoogle]
 * is registered.
 *
 * The hosted page must supply `clientId` and `challengeId`. The plugin forwards them as [SignInWithSocial.Params] with
 * the current [UiContextProvider] context, checks provider availability, then starts provider sign-in. Success returns
 * the provider ID token as the WebBridge success payload. Provider cancellation maps to bridge error type `ABORTED`;
 * missing provider support, unavailable providers, invalid parameters, and provider failures return bridge error
 * messages.
 */
internal class WebBridgeSocialPluginImpl(
    private val uiContextProvider: () -> UiContextProvider,
    private val signInWithGoogle: SignInWithGoogle?,
    private val serialization: Serialization
) : WebBridgeSocialPlugin {

    @InternalOwnIdAPI
    internal companion object {
        internal fun create(resolver: DIContainerResolver): WebBridgeSocialPlugin = resolver.run {
            WebBridgeSocialPluginImpl(
                uiContextProvider = { getOrThrow<UiContextProvider>() },
                signInWithGoogle = getOrNull<SignInWithGoogle>(),
                serialization = getOrThrow<Serialization>()
            )
        }
    }

    override val actions: List<String> = buildList {
        if (signInWithGoogle != null) add("Google")
    }

    override suspend fun handle(message: WebBridgePlugin.Message): WebBridgePlugin.Result {
        if (key.id.equals(message.payload.pluginId, ignoreCase = true).not()) {
            return WebBridgePlugin.Result(error = buildJsonObject { put("message", "Wrong plugin ID: ${message.payload.pluginId}") })
        }

        return when (message.payload.action.uppercase()) {
            "GOOGLE" -> handleActionGoogle(message)
            else -> WebBridgePlugin.Result(error = buildJsonObject { put("message", "Unknown action: ${message.payload.action}") })
        }
    }

    private suspend fun handleActionGoogle(message: WebBridgePlugin.Message): WebBridgePlugin.Result = runCatching {
        val signInWithGoogle = signInWithGoogle
            ?: return WebBridgePlugin.Result(error = buildJsonObject { put("message", "WebBridgeSocialPlugin: Google Sign In is not supported") })

        val paramsJsonObject = message.payload.params?.let { serialization.parseToJsonElement(it).jsonObject } ?: run {
            throw IllegalArgumentException("WebBridgeSocialPlugin: No params set for '${message.payload.action}'")
        }

        val clientId = paramsJsonObject["clientId"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("WebBridgeSocialPlugin: 'clientId' is required")

        val challengeId = paramsJsonObject["challengeId"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let(::ChallengeId)
            ?: throw IllegalArgumentException("WebBridgeSocialPlugin: Parameter 'challengeId' is required")

        val providerParams = SignInWithSocial.Params(uiContextProvider.invoke().current(), clientId, challengeId.value)
        if (!signInWithGoogle.isAvailable(providerParams)) {
            return WebBridgePlugin.Result(error = buildJsonObject { put("message", "WebBridgeSocialPlugin: Google Sign In is unavailable") })
        }

        when (val result = signInWithGoogle.signIn(providerParams)) {
            is SignInWithSocial.Result.Fail ->
                WebBridgePlugin.Result(error = buildJsonObject { put("message", result.error.message) })

            is SignInWithSocial.Result.Canceled ->
                WebBridgePlugin.Result(error = buildJsonObject { put("type", "ABORTED"); put("message", result.reason.toString()) })

            is SignInWithSocial.Result.Success -> WebBridgePlugin.Result(success = JsonPrimitive(result.idToken))
        }
    }.fold(
        onSuccess = { it },
        onFailure = { error ->
            if (error is CancellationException) throw error
            WebBridgePlugin.Result(error = buildJsonObject { put("message", error.message) })
        }
    )
}
