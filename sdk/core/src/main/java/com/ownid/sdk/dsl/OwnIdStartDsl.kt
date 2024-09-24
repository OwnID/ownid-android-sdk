package com.ownid.sdk.dsl

import com.ownid.sdk.AuthMethod
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.JsonSerializable
import com.ownid.sdk.OwnId
import com.ownid.sdk.OwnIdProviders
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.internal.feature.webflow.OnCloseWrapper
import com.ownid.sdk.internal.feature.webflow.OnErrorWrapper
import com.ownid.sdk.internal.feature.webflow.OnFinishWrapper
import com.ownid.sdk.internal.feature.webflow.OwnIdFlowWrapper
import com.ownid.sdk.internal.feature.webflow.start
import org.json.JSONObject

/**
 *
 * Initiates the OwnID Elite authentication flow, allowing for customization through providers and event handlers.
 *
 * OwnID Elite provides a powerful and flexible framework for integrating and customizing authentication processes within your applications.
 * Using **Providers** and **Events**, developers can implement or override specific aspects of the authentication flow, tailoring the user experience to meet the unique needs of their applications.
 *
 * * **Providers**: Manage critical components such as session handling and authentication mechanisms, including traditional password-based logins.
 * They allow developers to define how users are authenticated, how sessions are maintained and how accounts are managed within the application. See [OwnIdProviders].
 *
 * Define providers globally using [OwnId.providers] and override them for specific flows if required.
 *
 * * **Events**: Handle specific actions and responses within the authentication flow. They allow developers to customize behavior when specific events occur.
 * For example, when the authentication process completes, when an error occurs, or when the flow detects a new user and prompts for registration.
 *
 * All **Events** and **Provider** handlers are optional and defined as suspend functions.
 *
 * **Note:** To override **Provider** handlers set at OwnID SDK global level, define them here.
 *
 * Example:
 * ```
 * OwnId.start {
 *    providers { // Optional, if present will override Global providers set in OwnId.providers
 *        session {
 *            create { loginId: String, session: String, authToken: String, authMethod: AuthMethod? ->
 *            }
 *        }
 *        account {
 *            register { loginId: String, profile: String, ownIdData: String?, authToken: String? ->
 *            }
 *        }
 *        auth {
 *            password {
 *                authenticate { loginId: String, password: String ->
 *                }
 *            }
 *        }
 *    }
 *    events {
 *        onFinish { loginId: String, authMethod: AuthMethod?, authToken: String? ->
 *        }
 *        onError { cause: OwnIdException ->
 *        }
 *        onClose {
 *        }
 *    }
 * }
 * ```
 *
 * **Important:** This function must be called from the main thread.
 *
 * @param block Lambda function to configure the flow using an [OwnIdStartBuilder].
 *
 * @return An [AutoCloseable] object that can be used to cancel the OwnID flow.
 *
 * @throws IllegalArgumentException if the function is called from a thread other than the main thread.
 * @throws IllegalStateException if no OwnID instances available.
 */
public fun OwnId.start(block: OwnIdStartBuilder.() -> Unit): AutoCloseable =
    OwnIdStartBuilder().apply(block).build().invoke()

@DslMarker
public annotation class OwnIdStartDsl

/**
 * Builder class for configuring the OwnID Elite flow.
 *
 * This builder allows you to define the events and their corresponding handlers that will be triggered during the OwnID Elite flow.
 */
@OwnIdStartDsl
@OptIn(InternalOwnIdAPI::class)
public class OwnIdStartBuilder {
    private var providers: OwnIdProviders? = null
    private var eventsWrappers: List<OwnIdFlowWrapper<PageAction>>? = null

    /**
     * Configures providers using an [OwnIdProvidersBuilder].
     *
     * Optional, if present will override Global providers set in [OwnId.providers]
     *
     * @param block Lambda function to configure the providers.
     */
    public fun providers(block: OwnIdProvidersBuilder.() -> Unit) {
        providers = OwnIdProvidersBuilder().apply(block).build()
    }

    /**
     * Configures the flow events using an [OwnIdFlowEventsBuilder].
     *
     * @param block Lambda function to configure the flow events.
     */
    public fun events(block: OwnIdFlowEventsBuilder.() -> Unit) {
        eventsWrappers = OwnIdFlowEventsBuilder().apply(block).build()
    }

    /**
     * Builds the [OwnIdStartBuilder] instance.
     *
     * @return The built [OwnIdStartBuilder] instance.
     */
    public fun build(): OwnIdStartBuilder {
        return this
    }

    /**
     * Starts the OwnID flow with the configured providers and events.
     *
     * @return An [AutoCloseable] object that can be used to cancel the OwnID flow.
     */
    @OptIn(InternalOwnIdAPI::class)
    public operator fun invoke(): AutoCloseable {
        return OwnId.start(
            providers = providers,
            eventWrappers = eventsWrappers ?: emptyList(),
        )
    }
}

/**
 * Builder class for defining the handlers for different events in the OwnID Elite flow.
 */
@OwnIdStartDsl
public class OwnIdFlowEventsBuilder {
    //    private var onAccountNotFound: (suspend (loginId: String, ownIdData: String?, authToken: String?) -> PageAction)? = null
    private var onFinish: (suspend (loginId: String, authMethod: AuthMethod?, authToken: String?) -> Unit)? = null
    private var onError: (suspend (cause: OwnIdException) -> Unit)? = null
    private var onClose: (suspend () -> Unit)? = null

//    /**
//     * Sets the handler for the `onAccountNotFound` event.
//     *
//     * The `onAccountNotFound` event is triggered when the provided account details do not match any existing accounts.
//     * This event allows you to handle scenarios where a user needs to be registered or redirected to a registration screen.
//     *
//     * **Use `onAccountNotFound` to run your own registration flows.**
//     *
//     * **Developers should:**
//     * * Handle account creation for the given `loginId`.
//     * * Add `ownIdData` to the new user profile if available.
//     *
//     * **Note:** This is a terminal event. No other handlers will be called after this one.
//     *
//     *  @param block Suspend function invoked when the user's account is not found.
//     *  This function receives the `loginId`, optional `ownIdData`, and optional `authToken`.
//     *  It should return a [PageAction] to control the flow.
//     */
//    public fun onAccountNotFound(block: suspend (loginId: String, ownIdData: String?, authToken: String?) -> PageAction) {
//        onAccountNotFound = block
//    }

    /**
     * Sets the handler for the `onFinish` event.
     *
     * The `onFinish` event is triggered when the authentication flow is successfully completed.
     * This event allows you to define actions that should be taken once the user is authenticated.
     *
     * **Note:** This is a terminal event. No other handlers will be called after this one.
     *
     * @param block Suspend function that will be executed when the `onFinish` event is triggered.
     */
    public fun onFinish(block: suspend (loginId: String, authMethod: AuthMethod?, authToken: String?) -> Unit) {
        onFinish = block
    }

    /**
     * Sets the handler for the `onError` event.
     *
     * The `onError` event is triggered when an error occurs during the authentication flow.
     * This event allows developers to handle errors gracefully, such as by logging them or displaying error messages to the user.
     *
     * **Note:** This is a terminal event. No other handlers will be called after this one.
     *
     * @param block Suspend function that will be executed when the `onError` event is triggered.
     * This function receives an [OwnIdException] object containing details about the error.
     */
    public fun onError(block: suspend (cause: OwnIdException) -> Unit) {
        onError = block
    }

    /**
     * Sets the handler for the `onClose` event.
     *
     * The `onClose` event is triggered when the authentication flow is closed, either by user action or automatically.
     * This event allows developers to define what should happen when the authentication flow is interrupted or completed without a successful login.
     *
     * **Note:** This is a terminal event. No other handlers will be called after this one.
     *
     * @param block Suspend function that will be executed when the `onClose` event is triggered.
     */
    public fun onClose(block: suspend () -> Unit) {
        onClose = block
    }

    /**
     * Builds a list of [OwnIdFlowWrapper] based on the configured event handlers.
     *
     * @return A list of [OwnIdFlowWrapper] instances.
     */
    @OptIn(InternalOwnIdAPI::class)
    public fun build(): List<OwnIdFlowWrapper<PageAction>> = buildList {
//        onAccountNotFound?.let { add(OnAccountNotFoundWrapper(it)) }
        onFinish?.let { add(OnFinishWrapper(it)) }
        onError?.let { add(OnErrorWrapper(it)) }
        onClose?.let { add(OnCloseWrapper(it)) }
    }
}

/**
 * Represents a result of an OwnID Elite flow event.
 */
public sealed class PageAction(private val action: String) : JsonSerializable {
    public object None : PageAction("none")
//    public object UiRegister : PageAction("ui.register")

    @InternalOwnIdAPI
    public override fun toJson(): String = JSONObject().put("action", action).toString()
}