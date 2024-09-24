package com.ownid.sdk.internal.feature.webflow

import com.ownid.sdk.AuthMethod
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.dsl.PageAction
import com.ownid.sdk.exception.OwnIdException


/**
 * Wrapper for the `onAccountNotFound` event.
 *
 * The `onAccountNotFound` event is triggered when the provided account details do not match any existing accounts.
 * This event allows you to handle scenarios where a user needs to be registered or redirected to a registration screen.
 *
 * **Use `onAccountNotFound` to run your own registration flows.**
 *
 * **Developers should:**
 * * Handle account creation for the given `loginId`.
 * * Add `ownIdData` to the new user profile if available.
 *
 * **Note:** This is a terminal event.
 *
 * @property onAccountNotFound Suspend function invoked when the user's account is not found.
 * This function receives the `loginId`, optional `ownIdData`, and optional `authToken`.
 * It should return a [PageAction] to control the flow.
 */
@InternalOwnIdAPI
internal class OnAccountNotFoundWrapper(
    private val onAccountNotFound: suspend (loginId: String, ownIdData: String?, authToken: String?) -> PageAction
) : OwnIdFlowWrapper<PageAction> {

    override suspend fun invoke(payload: OwnIdFlowPayload): PageAction {
        payload as OnAccountNotFoundEvent.Payload
        return onAccountNotFound.invoke(payload.loginId, payload.ownIdData, payload.authToken)
    }
}

/**
 * Wrapper for the `onFinish` event.
 *
 * The `onFinish` event is triggered when the authentication flow is successfully completed.
 * This event allows you to define actions that should be taken once the user is authenticated.
 *
 * **Note:** This is a terminal event.
 *
 * @property onFinish Suspend function invoked when the authentication flow is successfully completed.
 * This function receives the `loginId`, `authMethod` used, and `authToken`.
 */
@InternalOwnIdAPI
internal class OnFinishWrapper(
    private val onFinish: suspend (loginId: String, authMethod: AuthMethod?, authToken: String?) -> Unit
) : OwnIdFlowWrapper<PageAction> {

    override suspend fun invoke(payload: OwnIdFlowPayload): PageAction {
        payload as OnFinishEvent.Payload
        onFinish.invoke(payload.loginId, payload.authMethod, payload.authToken)
        return PageAction.None
    }

    internal companion object {
        internal val DEFAULT = OnFinishWrapper { _, _, _ -> PageAction.None }
    }
}

/**
 * Wrapper for the `onError` event.
 *
 * The `onError` event is triggered when an error occurs during the authentication flow.
 * This event allows developers to handle errors gracefully, such as by logging them or displaying error messages to the user.
 *
 * **Note:** This is a terminal event.
 *
 * @property onError Suspend function invoked when an error occurs during the OwnID flow.
 * This function receives the `OwnIdException` representing the error.
 */
@InternalOwnIdAPI
internal class OnErrorWrapper(
    private val onError: suspend (cause: OwnIdException) -> Unit
) : OwnIdFlowWrapper<PageAction> {

    override suspend fun invoke(payload: OwnIdFlowPayload): PageAction {
        payload as OnErrorEvent.Payload
        onError.invoke(payload.cause)
        return PageAction.None
    }

    internal companion object {
        internal val DEFAULT = OnErrorWrapper { PageAction.None }
    }
}

/**
 * Wrapper for the `onClose` event.
 *
 * The `onClose` event is triggered when the authentication flow is closed, either by user action or automatically.
 * This event allows developers to define what should happen when the authentication flow is interrupted or completed without a successful login.
 *
 * **Note:** This is a terminal event.
 *
 * @property onClose Suspend function invoked when the OwnID flow is closed.
 */
@InternalOwnIdAPI
internal class OnCloseWrapper(
    private val onClose: suspend () -> Unit
) : OwnIdFlowWrapper<PageAction> {

    override suspend fun invoke(payload: OwnIdFlowPayload): PageAction {
        onClose.invoke()
        return PageAction.None
    }

    internal companion object {
        internal val DEFAULT = OnCloseWrapper { PageAction.None }
    }
}