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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Implements the WebBridge `SOCIAL` action with providers captured by this bridge.
 *
 * The `Google` action is advertised only when a [SignInWithGoogle] provider is registered. A request is checked with
 * [SignInWithGoogle.isAvailable] before resolving the bridge Activity; resolution and sign-in then run together on the
 * main thread. The ID token is returned as the success payload, provider cancellation becomes `ABORTED`, and other
 * failures become bridge errors. Coroutine cancellation is propagated.
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

        val providerParams = SignInWithSocial.Params(clientId, challengeId.value)
        if (!signInWithGoogle.isAvailable(providerParams)) {
            return WebBridgePlugin.Result(error = buildJsonObject { put("message", "WebBridgeSocialPlugin: Google Sign In is unavailable") })
        }

        val result = withContext(Dispatchers.Main.immediate) {
            when (val resolution = uiContextProvider.invoke().current()) {
                is UiContextProvider.Result.Success -> signInWithGoogle.signIn(resolution.activity, providerParams)
                is UiContextProvider.Result.Failure -> SignInWithSocial.Result.Fail(resolution.error)
            }
        }

        when (result) {
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
