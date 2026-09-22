package com.ownid.sdk.ui.webbridge

import android.net.Uri
import android.webkit.WebView
import androidx.annotation.AnyThread
import com.ownid.sdk.capability.LocalInfo
import com.ownid.sdk.capability.Passkey
import com.ownid.sdk.capability.SignInWithSocial
import com.ownid.sdk.capability.UserRepository
import com.ownid.sdk.model.Context
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.ops.WebBridgeOperationEventWrapper
import com.ownid.sdk.provider.PasswordAuthenticate
import com.ownid.sdk.provider.SessionCreate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Stable identifier of a [WebBridgePlugin].
 *
 * Use [WebBridgePluginKey] when you need to register, replace, remove, or look up a plugin in WebBridge APIs.
 *
 * A key is the JavaScript plugin namespace [id]. Keys are compared by their canonical string representation, so values
 * created from equivalent `id` values are interchangeable.
 *
 * @property id JavaScript plugin namespace identifier.
 * @property key Normalized string representation in the `PLUGIN_ID` form.
 */
public class WebBridgePluginKey private constructor(
    public val id: String
) {

    init {
        require(id.isNotBlank()) { "Id must not be blank" }
    }

    public val key: String = id.uppercase()

    override fun toString(): String = key

    override fun equals(other: Any?): Boolean = this === other || (other is WebBridgePluginKey && key == other.key)

    override fun hashCode(): Int = key.hashCode()

    public companion object {
        /**
         * Creates a plugin key from a JavaScript plugin namespace [id].
         *
         * The resulting key normalizes [id] to uppercase.
         */
        public fun from(id: String): WebBridgePluginKey = WebBridgePluginKey(id = id.uppercase())
    }
}

/**
 * Plugin exposed to the OwnID Web SDK through a [WebBridge].
 *
 * A plugin handles a specific JavaScript namespace identified by [key] and advertises one or more [actions] for
 * document-start injection. [WebBridge] routes allowed main-frame messages to the matching plugin by [key]. The
 * plugin is responsible for interpreting [Message.Payload.action] and returning the [Result] that should be sent to
 * the JavaScript callback path from the message payload.
 *
 * Plugin instances are bridge-scoped. Register a fresh plugin instance for each [WebBridge] you create, and do not
 * reuse the same plugin instance across multiple bridge instances or plugin registries. Implementations that depend on
 * mutable state must keep that state inside the bridge-scoped instance. The SDK does not check instance identity across
 * bridges, so the caller that registers a plugin or factory owns this requirement.
 */
public interface WebBridgePlugin {

    /** Stable plugin identifier used for lookup, replacement, and message routing. */
    public val key: WebBridgePluginKey

    /** Action names advertised to JavaScript for this plugin namespace. */
    public val actions: List<String>

    /**
     * An incoming message from the web page destined for this plugin.
     *
     * [WebBridge] creates this value only after the message payload has been decoded, the callback path has been
     * validated, the message has been limited to the main frame, and the source origin has passed the bridge origin
     * policy enforced by WebView.
     *
     * @property webView The source WebView.
     * @property sourceOrigin The origin of the page that sent the message.
     * @property isMainFrame `true` if the message came from the main frame.
     * @property allowedOriginRules The normalized effective origins captured for this bridge attachment.
     * @property payload The deserialized message payload.
     */
    public class Message(
        public val webView: WebView,
        public val sourceOrigin: Uri,
        public val isMainFrame: Boolean,
        public val allowedOriginRules: Set<String>,
        public val payload: Payload
    ) {

        /**
         * Deserialized payload of a bridge message.
         *
         * The bridge accepts only dot-separated JavaScript identifier paths for [callbackPath] before evaluating the
         * callback in the page.
         *
         * @property pluginId Plugin namespace identifier from JavaScript. Must not be blank. This value is matched
         *     against [WebBridgePluginKey.id].
         * @property action Action name inside the plugin namespace. Must not be blank. The bridge passes this through
         *     to the plugin; the plugin decides whether the action is supported.
         * @property callbackPath JavaScript callback path to invoke with the result payload. Must not be blank.
         * @property params Optional serialized parameters payload.
         */
        @Serializable
        public class Payload(
            public val pluginId: String,
            public val action: String,
            public val callbackPath: String,
            public val params: String? = null
        ) {
            init {
                require(pluginId.isNotBlank()) { "PluginId must not be blank" }
                require(action.isNotBlank()) { "Action must not be blank" }
                require(callbackPath.isNotBlank()) { "CallbackPath must not be blank" }
            }
        }
    }

    /**
     * The result sent back to the web page after handling a message.
     *
     * Set either [success] or [error]. If [error] does not include a `type`, serialization adds `UNKNOWN`. If both
     * values are `null`, the value sent back to JavaScript is normalized into the bridge failure payload shape.
     *
     * @property success Successful result payload returned to JavaScript.
     * @property error Failure payload returned to JavaScript.
     */
    public class Result(
        public val success: JsonElement? = null,
        public val error: JsonObject? = null,
    ) {
        /** Serializes the result to a JSON string suitable for sending back to the web page. */
        public fun toResultString(): String = success?.toString() ?: buildJsonObject {
            val normalizedError = (error ?: JsonObject(emptyMap())).let { obj ->
                if ("type" in obj) obj else JsonObject(mapOf("type" to JsonPrimitive("UNKNOWN")) + obj)
            }
            put("error", normalizedError)
        }.toString()
    }

    /**
     * Returns JavaScript injection metadata for this plugin, or `null` to skip injection.
     *
     * The default exposes [key] and [actions] at document start. Override this when a plugin should handle messages
     * without contributing document-start injection metadata, or when the available actions depend on configured
     * capabilities.
     */
    public fun getInjectionData(): Pair<String, List<String>>? = actions.takeIf { it.isNotEmpty() }?.let { key.id to it }

    /**
     * Handles an incoming bridge message and returns the result to send back to the web page.
     *
     * Implementations may be called from a background dispatcher. They must be thread-safe, should switch to the main
     * thread only for platform APIs that require it, and should return a [Result] instead of throwing for expected
     * bridge outcomes. Unexpected exceptions are converted to bridge error payloads. Cancellation during message
     * handling is converted to an aborted bridge error when the bridge can still call back into the page.
     */
    @AnyThread
    public suspend fun handle(message: Message): Result
}

/** Built-in FIDO namespace backed by [Passkey] for WebBridge passkey availability, assertion, and creation calls. */
internal interface WebBridgePasskeyPlugin : WebBridgePlugin {
    companion object {
        val KEY: WebBridgePluginKey = WebBridgePluginKey.from(id = "FIDO")
    }

    override val key: WebBridgePluginKey
        get() = KEY

    override val actions: List<String>
        get() = listOf("isAvailable", "create", "get")
}

/** Built-in STORAGE namespace backed by [UserRepository] for reading and updating the SDK's stored last user. */
internal interface WebBridgeUserRepositoryPlugin : WebBridgePlugin {
    companion object {
        val KEY: WebBridgePluginKey = WebBridgePluginKey.from(id = "STORAGE")
    }

    override val key: WebBridgePluginKey
        get() = KEY

    override val actions: List<String>
        get() = listOf("setLastUser", "getLastUser")
}

/** Built-in SOCIAL namespace backed by configured [SignInWithSocial] providers. */
internal interface WebBridgeSocialPlugin : WebBridgePlugin {
    companion object {
        val KEY: WebBridgePluginKey = WebBridgePluginKey.from(id = "SOCIAL")
    }

    override val key: WebBridgePluginKey
        get() = KEY
}

/** Built-in METADATA namespace that returns SDK metadata from [LocalInfo] to the hosted page. */
internal interface WebBridgeMetadataPlugin : WebBridgePlugin {
    companion object {
        val KEY: WebBridgePluginKey = WebBridgePluginKey.from(id = "METADATA")
    }

    override val key: WebBridgePluginKey
        get() = KEY

    override val actions: List<String>
        get() = listOf("get")
}

/**
 * Built-in CONTEXT namespace that forwards the scoped OwnID [Context] to the hosted page when a
 * context is available.
 */
internal interface WebBridgeContextPlugin : WebBridgePlugin {
    companion object {
        val KEY: WebBridgePluginKey = WebBridgePluginKey.from(id = "CONTEXT")
    }

    override val key: WebBridgePluginKey
        get() = KEY

    override val actions: List<String>
        get() = listOf("get")
}

/**
 * Built-in FLOW namespace used by Elite/WebBridge operations to expose [SessionCreate], [PasswordAuthenticate], and
 * operation-supplied [WebBridgeOperationEventWrapper] callbacks to the hosted page.
 */
internal interface WebBridgeElitePlugin : WebBridgePlugin {
    companion object {
        val KEY: WebBridgePluginKey = WebBridgePluginKey.from(id = "FLOW")
    }

    override val key: WebBridgePluginKey
        get() = KEY

    /** Adds operation-scoped event wrappers before the bridge is attached and injected. */
    @InternalOwnIdAPI
    fun addEventWrappers(eventWrappers: List<WebBridgeOperationEventWrapper>)

    /** Registers a callback invoked after a wrapper completes successfully. */
    @InternalOwnIdAPI
    fun setWrapperSideEffect(sideEffect: (wrapper: WebBridgeOperationEventWrapper) -> Unit)
}
