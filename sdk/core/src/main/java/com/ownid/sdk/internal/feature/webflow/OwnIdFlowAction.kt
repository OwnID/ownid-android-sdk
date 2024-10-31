package com.ownid.sdk.internal.feature.webflow

import com.ownid.sdk.AuthMethod
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.JsonSerializable
import com.ownid.sdk.exception.OwnIdException
import org.json.JSONObject
import kotlin.reflect.KClass

@InternalOwnIdAPI
internal enum class OwnIdFlowAction(
    /**
     * The WebSDK action name.
     */
    internal val webAction: String,

    /**
     * Indicates whether the action is a terminal (WebView will be closed after this action).
     */
    internal val isTerminal: Boolean,

    /**
     * The wrapper class.
     */
    internal val wrapperKlass: KClass<out Any>,

    /**
     * A factory function that creates an [OwnIdFlowEvent] instance for this action.
     */
    internal val eventFactory: OwnIdFlowEvent.Factory
) {

    ACCOUNT_REGISTER(
        webAction = "account_register",
        isTerminal = false,
        wrapperKlass = AccountProviderWrapper::class,
        eventFactory = object : OwnIdFlowEvent.Factory {
            override fun create(
                wrapper: OwnIdFlowWrapper<JsonSerializable>, params: String?, webViewCallback: (String?) -> Unit
            ): OwnIdFlowEvent = AccountRegisterEvent(
                wrapper = wrapper,
                payload = JSONObject(requireNotNull(params) { "Unexpected: params=null" }).run {
                    AccountRegisterEvent.Payload(
                        getString("loginId"),
                        getString("profile"),
                        optString("ownIdData").ifBlank { null },
                        optString("authToken").ifBlank { null },
                    )
                },
                webViewCallback = webViewCallback
            )
        }
    ),

    SESSION_CREATE(
        webAction = "session_create",
        isTerminal = false,
        wrapperKlass = SessionProviderWrapper::class,
        eventFactory = object : OwnIdFlowEvent.Factory {
            override fun create(
                wrapper: OwnIdFlowWrapper<JsonSerializable>, params: String?, webViewCallback: (String?) -> Unit
            ): OwnIdFlowEvent {
                val data = JSONObject(requireNotNull(params) { "Unexpected: params=null" })
                val metadata = data.getJSONObject("metadata")
                return SessionCreateEvent(
                    wrapper = wrapper,
                    payload = SessionCreateEvent.Payload(
                        metadata.getString("loginId"),
                        data.getString("session"),
                        metadata.getString("authToken"),
                        AuthMethod.fromString(metadata.getString("authType"))
                    ),
                    webViewCallback = webViewCallback
                )
            }
        }
    ),

    AUTHENTICATE_PASSWORD(
        webAction = "auth_password_authenticate",
        isTerminal = false,
        wrapperKlass = AuthPasswordWrapper::class,
        eventFactory = object : OwnIdFlowEvent.Factory {
            override fun create(
                wrapper: OwnIdFlowWrapper<JsonSerializable>, params: String?, webViewCallback: (String?) -> Unit
            ): OwnIdFlowEvent {
                val data = JSONObject(requireNotNull(params) { "Unexpected: params=null" })
                return AuthPasswordEvent(
                    wrapper = wrapper,
                    payload = AuthPasswordEvent.Payload(
                        data.getString("loginId"),
                        data.getString("password"),
                    ),
                    webViewCallback = webViewCallback
                )
            }
        }
    ),

    ON_NATIVE_ACTION(
        webAction = "onNativeAction",
        isTerminal = true,
        wrapperKlass = OnNativeActionWrapper::class,
        eventFactory = object : OwnIdFlowEvent.Factory {
            override fun create(
                wrapper: OwnIdFlowWrapper<JsonSerializable>, params: String?, webViewCallback: (String?) -> Unit
            ): OwnIdFlowEvent = OnNativeActionEvent(
                wrapper = wrapper,
                payload = JSONObject(requireNotNull(params) { "Unexpected: params=null" }).run {
                    OnNativeActionEvent.Payload(
                        getString("name"),
                        optString("params").ifBlank { null }
                    )
                },
                webViewCallback = webViewCallback
            )
        }
    ),

    ON_ACCOUNT_NOT_FOUND(
        webAction = "onAccountNotFound",
        isTerminal = false,
        wrapperKlass = OnAccountNotFoundWrapper::class,
        eventFactory = object : OwnIdFlowEvent.Factory {
            override fun create(
                wrapper: OwnIdFlowWrapper<JsonSerializable>, params: String?, webViewCallback: (String?) -> Unit
            ): OwnIdFlowEvent = OnAccountNotFoundEvent(
                wrapper = wrapper,
                payload = JSONObject(requireNotNull(params) { "Unexpected: params=null" }).run {
                    OnAccountNotFoundEvent.Payload(
                        getString("loginId"),
                        optString("ownIdData").ifBlank { null },
                        optString("authToken").ifBlank { null }
                    )
                },
                webViewCallback = webViewCallback
            )
        }
    ),

    ON_FINISH(
        webAction = "onFinish",
        isTerminal = true,
        wrapperKlass = OnFinishWrapper::class,
        eventFactory = object : OwnIdFlowEvent.Factory {
            override fun create(
                wrapper: OwnIdFlowWrapper<JsonSerializable>, params: String?, webViewCallback: (String?) -> Unit
            ): OwnIdFlowEvent = OnFinishEvent(
                wrapper = wrapper,
                payload = JSONObject(requireNotNull(params) { "Unexpected: params=null" }).run {
                    OnFinishEvent.Payload(
                        getString("loginId"),
                        getString("source"),
                        optString("context").ifBlank { null },
                        AuthMethod.fromString(getString("authType")),
                        optString("authToken").ifBlank { null }
                    )
                },
                webViewCallback = webViewCallback
            )
        }
    ),

    ON_ERROR(
        webAction = "onError",
        isTerminal = true,
        wrapperKlass = OnErrorWrapper::class,
        eventFactory = object : OwnIdFlowEvent.Factory {
            override fun create(
                wrapper: OwnIdFlowWrapper<JsonSerializable>, params: String?, webViewCallback: (String?) -> Unit
            ): OwnIdFlowEvent = OnErrorEvent(
                wrapper = wrapper,
                payload = OnErrorEvent.Payload(OwnIdException(params ?: "onError: params=null")),
                webViewCallback = webViewCallback
            )
        }
    ),

    ON_CLOSE(
        webAction = "onClose",
        isTerminal = true,
        wrapperKlass = OnCloseWrapper::class,
        eventFactory = object : OwnIdFlowEvent.Factory {
            override fun create(
                wrapper: OwnIdFlowWrapper<JsonSerializable>, params: String?, webViewCallback: (String?) -> Unit
            ): OwnIdFlowEvent =
                OnCloseEvent(wrapper = wrapper, payload = OnCloseEvent.Payload, webViewCallback = webViewCallback)
        }
    );
}