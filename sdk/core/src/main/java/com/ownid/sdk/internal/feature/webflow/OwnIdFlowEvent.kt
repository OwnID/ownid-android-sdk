package com.ownid.sdk.internal.feature.webflow

import com.ownid.sdk.AuthMethod
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.JsonSerializable
import com.ownid.sdk.OwnIdCoreImpl
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.internal.component.OwnIdInternalLogger
import com.ownid.sdk.internal.component.events.Metadata
import com.ownid.sdk.internal.component.events.Metric
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Represents an event in the OwnID Elite.
 *
 * This interface defines the event that occur during the flow.
 * Each event is one-to-one match for a specific WebView action in the flow.
 * Each event has a corresponding wrapper class, payload, and side effects to be performed upon receiving the event.
 */
@InternalOwnIdAPI
internal sealed interface OwnIdFlowEvent {

    /**
     * Factory interface for creating instances of [OwnIdFlowEvent].
     */
    public interface Factory {
        @InternalOwnIdAPI
        public fun create(wrapper: OwnIdFlowWrapper<JsonSerializable>, params: String?, webViewCallback: (String?) -> Unit): OwnIdFlowEvent
    }

    /**
     * Indicates whether the event is a terminal (WebView will be closed after this event).
     */
    public val isTerminal: Boolean

    /**
     * The wrapper for public functions associated with the event.
     */
    public val wrapper: OwnIdFlowWrapper<JsonSerializable>

    /**
     * The payload associated with the event.
     */
    public val payload: OwnIdFlowPayload

    /**
     * A callback function to be invoked with the result of the event handling.
     */
    public val webViewCallback: (wrapperResult: String) -> Unit

    /**
     * A function to be invoked when the event is received.
     */
    public val onReceiveSideEffect: suspend (ownIdCore: OwnIdCoreImpl) -> Unit
}

@InternalOwnIdAPI
internal class AccountRegisterEvent(
    override val wrapper: OwnIdFlowWrapper<JsonSerializable>,
    override val payload: OwnIdFlowPayload,
    override val webViewCallback: (String?) -> Unit,
    override val isTerminal: Boolean = OwnIdFlowAction.ACCOUNT_REGISTER.isTerminal,
    override val onReceiveSideEffect: suspend (ownIdCore: OwnIdCoreImpl) -> Unit = {}
) : OwnIdFlowEvent {

    internal class Payload(
        internal val loginId: String, internal val rawProfile: String, internal val ownIdData: String?, internal val authToken: String?
    ) : OwnIdFlowPayload
}

@InternalOwnIdAPI
internal class SessionCreateEvent(
    override val wrapper: OwnIdFlowWrapper<JsonSerializable>,
    override val payload: Payload,
    override val webViewCallback: (String?) -> Unit,
    override val isTerminal: Boolean = OwnIdFlowAction.SESSION_CREATE.isTerminal,
    override val onReceiveSideEffect: suspend (ownIdCore: OwnIdCoreImpl) -> Unit = { ownIdCore ->
        ownIdCore.apply {
            withContext(NonCancellable) {
                runCatching { repository.saveLoginId(payload.loginId, payload.authMethod) }
                val loginIdData = repository.getLoginIdData(payload.loginId)
                runCatching { repository.saveLoginIdData(payload.loginId, loginIdData.copy(authMethod = payload.authMethod)) }
            }
        }
    }
) : OwnIdFlowEvent {

    internal class Payload(
        internal val loginId: String, internal val rawSession: String, internal val authToken: String, internal val authMethod: AuthMethod?
    ) : OwnIdFlowPayload
}

@InternalOwnIdAPI
internal class AuthPasswordEvent(
    override val wrapper: OwnIdFlowWrapper<JsonSerializable>,
    override val payload: OwnIdFlowPayload,
    override val webViewCallback: (String?) -> Unit,
    override val isTerminal: Boolean = OwnIdFlowAction.SESSION_CREATE.isTerminal,
    override val onReceiveSideEffect: suspend (ownIdCore: OwnIdCoreImpl) -> Unit = {}
) : OwnIdFlowEvent {

    internal class Payload(
        internal val loginId: String, internal val password: String
    ) : OwnIdFlowPayload
}

@InternalOwnIdAPI
internal class OnAccountNotFoundEvent(
    override val wrapper: OwnIdFlowWrapper<JsonSerializable>,
    override val payload: OwnIdFlowPayload,
    override val webViewCallback: (String?) -> Unit,
    override val isTerminal: Boolean = OwnIdFlowAction.ON_ACCOUNT_NOT_FOUND.isTerminal,
    override val onReceiveSideEffect: suspend (ownIdCore: OwnIdCoreImpl) -> Unit = { ownIdCore ->
    }
) : OwnIdFlowEvent {

    internal class Payload(
        internal val loginId: String, internal val ownIdData: String? = null, internal val authToken: String? = null
    ) : OwnIdFlowPayload
}

@InternalOwnIdAPI
internal class OnFinishEvent(
    override val wrapper: OwnIdFlowWrapper<JsonSerializable>,
    override val payload: OwnIdFlowPayload,
    override val webViewCallback: (String?) -> Unit,
    override val isTerminal: Boolean = OwnIdFlowAction.ON_FINISH.isTerminal,
    override val onReceiveSideEffect: suspend (ownIdCore: OwnIdCoreImpl) -> Unit = {}
) : OwnIdFlowEvent {

    internal class Payload(
        internal val loginId: String,
        internal val source: String, // 'mobile' : 'desktop'
        internal val context: String? = null, // OwnIdContext
        internal val authMethod: AuthMethod? = null, // authType
        internal val authToken: String? = null,
    ) : OwnIdFlowPayload
}

@InternalOwnIdAPI
internal class OnErrorEvent(
    override val wrapper: OwnIdFlowWrapper<JsonSerializable>,
    override val payload: OwnIdFlowPayload,
    override val webViewCallback: (String?) -> Unit = {},
    override val isTerminal: Boolean = OwnIdFlowAction.ON_ERROR.isTerminal,
    override val onReceiveSideEffect: suspend (ownIdCore: OwnIdCoreImpl) -> Unit = { ownIdCore ->
        payload as Payload
        OwnIdInternalLogger.logW(ownIdCore, "OnErrorEvent", "onReceive: ${payload.cause.message}", payload.cause)
        val metadata = Metadata(resultType = Metadata.ResultType.Failure("onError"))
        ownIdCore.eventsService.sendMetric(
            Metric.Category.General, Metric.EventType.Error, "Flow error", metadata = metadata, errorMessage = payload.cause.message
        )
    }
) : OwnIdFlowEvent {

    internal class Payload(internal val cause: OwnIdException) : OwnIdFlowPayload
}

@InternalOwnIdAPI
internal class OnCloseEvent(
    override val wrapper: OwnIdFlowWrapper<JsonSerializable>,
    override val payload: OwnIdFlowPayload = Payload,
    override val webViewCallback: (String?) -> Unit = {},
    override val isTerminal: Boolean = OwnIdFlowAction.ON_CLOSE.isTerminal,
    override val onReceiveSideEffect: suspend (ownIdCore: OwnIdCoreImpl) -> Unit = {}
) : OwnIdFlowEvent {

    internal object Payload : OwnIdFlowPayload
}