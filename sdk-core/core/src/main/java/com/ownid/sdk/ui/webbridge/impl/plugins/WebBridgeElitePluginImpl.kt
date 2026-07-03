package com.ownid.sdk.ui.webbridge.impl.plugins

import androidx.annotation.AnyThread
import com.ownid.sdk.capability.LoginIdValidator
import com.ownid.sdk.capability.Serialization
import com.ownid.sdk.capability.appendWithType
import com.ownid.sdk.di.DIContainerResolver
import com.ownid.sdk.di.getOrNull
import com.ownid.sdk.di.getOrThrow
import com.ownid.sdk.logger.OwnIdLogRouter
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.AuthMethod
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.runCatchingExceptions
import com.ownid.sdk.ops.WebBridgeOperationEventWrapper
import com.ownid.sdk.provider.PasswordAuthenticate
import com.ownid.sdk.provider.SessionCreate
import com.ownid.sdk.provider.SessionOutput
import com.ownid.sdk.ui.webbridge.WebBridgeElitePlugin
import com.ownid.sdk.ui.webbridge.WebBridgePlugin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/**
 * Bridge-scoped FLOW plugin for Elite/WebBridge operations.
 *
 * This is an internal WebBridge contract for hosted pages, not a stable app-developer API. The plugin starts with
 * provider-backed wrappers from the current scope, accepts additional operation-scoped wrappers before attachment, and
 * exposes only wrapper action names through injection metadata.
 *
 * Provider-backed wrappers handle `session_create` and `auth_password_authenticate` when the matching provider is
 * registered. Operation-supplied wrappers bridge Elite callbacks such as native action, finish, error, and close events.
 * On a successful wrapper result, the plugin invokes the side-effect callback so the owning WebBridge operation can
 * react to terminal wrappers and close or settle the native operation. Unknown actions, malformed payloads, unavailable
 * providers, and provider failures are returned as WebBridge error payloads; coroutine cancellation is propagated to the
 * bridge so it can map cancellation consistently.
 */
@InternalOwnIdAPI
internal class WebBridgeElitePluginImpl(
    private val serialization: Serialization,
    private val loginIdValidator: LoginIdValidator,
    private val sessionCreate: SessionCreate?,
    private val passwordAuthenticate: PasswordAuthenticate?,
    private val logger: OwnIdLogRouter?
) : WebBridgeElitePlugin {

    internal companion object {
        internal fun create(resolver: DIContainerResolver): WebBridgeElitePlugin = resolver.run {
            WebBridgeElitePluginImpl(
                serialization = getOrThrow<Serialization>(),
                loginIdValidator = getOrThrow<LoginIdValidator>(),
                sessionCreate = getOrNull(SessionCreate::class.java),
                passwordAuthenticate = getOrNull(PasswordAuthenticate::class.java),
                logger = getOrNull<OwnIdLogRouter>()
            )
        }
    }

    private val eventWrappers = CopyOnWriteArrayList<WebBridgeOperationEventWrapper>()

    init {
        sessionCreate?.let { eventWrappers.add(SessionCreateWrapper(it, loginIdValidator)) }
        passwordAuthenticate?.let { eventWrappers.add(PasswordAuthenticateWrapper(it, loginIdValidator)) }
    }

    override val actions: List<String>
        get() = eventWrappers.map { it.webBridgePluginAction }

    override fun getInjectionData(): Pair<String, List<String>>? = if (actions.isNotEmpty()) key.id to actions else null

    override fun addEventWrappers(eventWrappers: List<WebBridgeOperationEventWrapper>) {
        logger?.logD(this, "addEventWrappers", eventWrappers.joinToString { it::class.java.simpleName })
        this.eventWrappers.addAll(eventWrappers)
    }

    private val sideEffectRef = AtomicReference<(WebBridgeOperationEventWrapper) -> Unit>()

    override fun setWrapperSideEffect(sideEffect: (wrapper: WebBridgeOperationEventWrapper) -> Unit) {
        sideEffectRef.set(sideEffect)
    }

    @AnyThread
    override suspend fun handle(message: WebBridgePlugin.Message): WebBridgePlugin.Result {
        if (key.id.equals(message.payload.pluginId, ignoreCase = true).not()) {
            return WebBridgePlugin.Result(error = buildJsonObject { put("message", "Wrong plugin ID: ${message.payload.pluginId}") })
        }

        val wrapper = eventWrappers.firstNotNullOfOrNull { wrapper ->
            if (wrapper.webBridgePluginAction.equals(message.payload.action, ignoreCase = true)) wrapper else null
        } ?: return WebBridgePlugin.Result(error = buildJsonObject { put("message", "Unknown action: ${message.payload.action}") })
        return runCatchingExceptions { wrapper.runWrappedFunction(message.payload.params, serialization) }
            .map { result ->
                sideEffectRef.get()?.invoke(wrapper)
                WebBridgePlugin.Result(success = result)
            }
            .getOrElse { error ->
                if (error is CancellationException) throw error
                logger?.logW(
                    this,
                    "handle",
                    "WebBridge plugin action failed [plugin=${key.id}, action=${wrapper.webBridgePluginAction}]: ${error.message}",
                    error
                )
                WebBridgePlugin.Result(error = buildJsonObject { put("message", error.message) })
            }
    }
}

@InternalOwnIdAPI
private class SessionCreateWrapper(
    private val sessionCreate: SessionCreate,
    private val loginIdValidator: LoginIdValidator
) : WebBridgeOperationEventWrapper {

    @Serializable(with = Payload.Deserializer::class)
    @OptIn(ExperimentalSerializationApi::class)
    private class Payload(
        val loginId: String,
        val authToken: String,
        val authMethod: AuthMethod,
        @JsonNames("session")
        val sessionPayload: String
    ) {
        object Deserializer : KSerializer<Payload> {
            override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Payload") {
                element("loginId", serialDescriptor<String>())
                element("session", serialDescriptor<String>())
                element("authToken", serialDescriptor<String>())
                element("authMethod", AuthMethod.serializer().descriptor)
            }

            override fun deserialize(decoder: Decoder): Payload {
                val jsonDecoder = decoder as? JsonDecoder
                    ?: throw SerializationException("This deserializer can only be used with Json")

                val rootElement = jsonDecoder.decodeJsonElement().jsonObject

                val sessionElement = rootElement["session"] ?: throw SerializationException("Missing 'session' field")
                val rawSession = sessionElement.toString()

                val metadata = rootElement["metadata"]?.jsonObject ?: throw SerializationException("Missing 'metadata' field")

                val loginId = metadata["loginId"]?.jsonPrimitive?.content
                    ?: throw SerializationException("Missing 'loginId' field in metadata")

                val authToken = metadata["authToken"]?.jsonPrimitive?.content
                    ?: throw SerializationException("Missing 'authToken' field in metadata")

                val authMethodRaw = metadata["authMethod"]?.jsonPrimitive?.content
                    ?: metadata["authType"]?.jsonPrimitive?.content
                    ?: throw SerializationException("Missing 'authMethod' (or legacy 'authType') field in metadata")

                val authMethod = jsonDecoder.json.decodeFromString(AuthMethod.serializer(), "\"$authMethodRaw\"")

                return Payload(loginId = loginId, sessionPayload = rawSession, authToken = authToken, authMethod = authMethod)
            }

            override fun serialize(encoder: Encoder, value: Payload) {
                throw NotImplementedError("Serialization not implemented for Payload")
            }
        }
    }

    override val webBridgePluginAction: String = "session_create"
    override val isTerminal: Boolean = false

    @Throws
    override suspend fun runWrappedFunction(params: String?, serialization: Serialization): JsonElement {
        val payload = serialization.decodeFromString(Payload.serializer(), requireNotNull(params) { "Unexpected: params=null" })
        val loginId = loginIdValidator.appendWithType(payload.loginId)
        return withContext(Dispatchers.Main.immediate) {
            val providerParams = SessionCreate.Params(loginId, AccessToken(payload.authToken), payload.authMethod, payload.sessionPayload)
            if (sessionCreate.isAvailable(providerParams)) sessionCreate.create(providerParams) else Result.failure(IllegalStateException("SessionCreate is unavailable"))
        }.toJsonElement()
    }
}

@InternalOwnIdAPI
private class PasswordAuthenticateWrapper(
    private val passwordAuthenticate: PasswordAuthenticate,
    private val loginIdValidator: LoginIdValidator
) : WebBridgeOperationEventWrapper {

    @Serializable
    private class Payload(val loginId: String, val password: String)

    override val webBridgePluginAction: String = "auth_password_authenticate"
    override val isTerminal: Boolean = false

    @Throws
    override suspend fun runWrappedFunction(params: String?, serialization: Serialization): JsonElement {
        val payload = serialization.decodeFromString(Payload.serializer(), requireNotNull(params) { "Unexpected: params=null" })
        val loginId = loginIdValidator.appendWithType(payload.loginId)
        return withContext(Dispatchers.Main.immediate) {
            val providerParams = PasswordAuthenticate.Params(loginId, payload.password)
            if (passwordAuthenticate.isAvailable(providerParams)) passwordAuthenticate.authenticate(providerParams)
            else Result.failure(IllegalStateException("PasswordAuthenticate is unavailable"))
        }.toJsonElement()
    }
}

private fun Result<SessionOutput>.toJsonElement(): JsonElement = fold(
    onSuccess = {
        buildJsonObject {
            put("status", "logged-in")
        }
    },
    onFailure = { throwable ->
        buildJsonObject {
            put("status", "fail")
            put("reason", throwable.message ?: "Unknown error")
        }
    }
)
