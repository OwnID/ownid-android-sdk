package com.ownid.sdk.ui.webbridge.impl.plugins

import com.ownid.sdk.capability.LoginIdValidator
import com.ownid.sdk.capability.Serialization
import com.ownid.sdk.capability.UserRepository
import com.ownid.sdk.capability.appendWithType
import com.ownid.sdk.di.DIContainerResolver
import com.ownid.sdk.di.getOrThrow
import com.ownid.sdk.model.AuthMethod
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.User
import com.ownid.sdk.ui.webbridge.WebBridgePlugin
import com.ownid.sdk.ui.webbridge.WebBridgeUserRepositoryPlugin
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Internal STORAGE WebBridge plugin backed by the instance [UserRepository].
 *
 * The plugin exposes the `STORAGE` key with `setLastUser` and `getLastUser` actions. Plugin ID and action matching are
 * case-insensitive. `getLastUser` ignores params and returns the repository value as
 * `{ "loginId": ..., "authMethod": ... }`, or JSON `null` when no user is stored. `setLastUser` expects params JSON
 * with a non-blank `loginId` and optional `authMethod`; it validates and normalizes the login ID through
 * [LoginIdValidator], decodes known auth-method aliases through [Serialization], stores a [User], and returns an empty
 * JSON object on success. Missing params, invalid JSON, validation failures, repository failures, wrong plugin IDs, and
 * unknown actions are returned as bridge error payloads; coroutine cancellation is propagated to the bridge lifecycle
 * instead of being converted here. The plugin boundary is limited to last-user repository access and login-ID
 * normalization, and it does not own the underlying persistence policy.
 */
internal class WebBridgeUserRepositoryPluginImpl(
    private val userRepository: UserRepository,
    private val loginIdValidator: LoginIdValidator,
    private val serialization: Serialization
) : WebBridgeUserRepositoryPlugin {

    @InternalOwnIdAPI
    internal companion object {
        internal fun create(resolver: DIContainerResolver): WebBridgeUserRepositoryPlugin = resolver.run {
            WebBridgeUserRepositoryPluginImpl(
                userRepository = getOrThrow<UserRepository>(),
                loginIdValidator = getOrThrow<LoginIdValidator>(),
                serialization = getOrThrow<Serialization>()
            )
        }
    }

    override suspend fun handle(message: WebBridgePlugin.Message): WebBridgePlugin.Result {
        if (key.id.equals(message.payload.pluginId, ignoreCase = true).not()) {
            return WebBridgePlugin.Result(error = buildJsonObject { put("message", "Wrong plugin ID: ${message.payload.pluginId}") }
            )
        }

        val result = when (message.payload.action.uppercase()) {
            "GETLASTUSER" -> handleGetLastUser()
            "SETLASTUSER" -> handleSetLastUser(message)
            else -> WebBridgePlugin.Result(error = buildJsonObject { put("message", "Unknown action: ${message.payload.action}") })
        }

        return result
    }

    @Suppress("unused")
    private suspend fun handleGetLastUser(): WebBridgePlugin.Result = runCatching {
        val lastUser = userRepository.getLastUser() ?: return@runCatching JsonNull
        buildJsonObject {
            put("loginId", lastUser.loginId.id)
            put("authMethod", serialization.encodeToJsonElement(AuthMethod.Serializer, lastUser.authMethod))
        }
    }.fold(
        onSuccess = { WebBridgePlugin.Result(success = it) },
        onFailure = { error ->
            if (error is CancellationException) throw error
            WebBridgePlugin.Result(error = buildJsonObject { put("message", error.message) })
        }
    )

    private suspend fun handleSetLastUser(message: WebBridgePlugin.Message): WebBridgePlugin.Result = runCatching {
        val paramsJsonObject = message.payload.params?.let { serialization.parseToJsonElement(it).jsonObject } ?: run {
            throw IllegalArgumentException("WebBridgeUserRepositoryPlugin: No params set for '${message.payload.action}'")
        }

        val loginIdString = paramsJsonObject["loginId"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("WebBridgeUserRepositoryPlugin: Missing or invalid 'loginId' parameter")

        val loginId = loginIdValidator.appendWithType(loginIdString)

        val user = User(
            loginId = loginId,
            authMethod = paramsJsonObject["authMethod"]?.let {
                serialization.decodeFromJsonElement(AuthMethod.serializer(), it)
            } ?: AuthMethod.Unknown
        )
        userRepository.setLastUser(user)
    }.fold(
        onSuccess = { WebBridgePlugin.Result(success = JsonObject(emptyMap())) },
        onFailure = { error ->
            if (error is CancellationException) throw error
            WebBridgePlugin.Result(error = buildJsonObject { put("message", error.message) })
        }
    )
}
