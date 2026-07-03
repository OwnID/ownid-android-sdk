package com.ownid.sdk.compose.ops

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ownid.sdk.compose.OwnIdColors
import com.ownid.sdk.compose.OwnIdOperationUi
import com.ownid.sdk.compose.ops.impl.LoginIdCollectDefaultUi
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.ops.ui.LoginIdCollectUi
import com.ownid.sdk.ui.capability.LoginIdCollectStrings

/**
 * Content contract for login ID collection operation UI.
 *
 * The SDK calls this content while the operation is active and after localized login ID collection strings have been
 * resolved. The operation owns the current input value, validation, error updates, cancellation, and final settlement;
 * custom content owns only Compose rendering and wiring user actions to the supplied [LoginIdCollectUi.UiState]
 * callbacks.
 *
 * The content receives:
 * - `uiState`: current login ID value, collectable types, optional validation error, and action callbacks.
 * - `strings`: localized text resolved for the collectable login ID types.
 * - `modifier`: modifier supplied to [OwnIdOperationUi].
 * - `colors`: resolved OwnID color tokens for the current theme.
 * - `errorTextProvider`: optional provider from SDK-reported [ErrorCode] values to display text. When it is `null`, use
 *   the current `UiError.localizedMessage`.
 * - `isReadyForInitialFocus`: `true` when the surrounding presentation is ready for one-time focus requests.
 *
 * Invoke `uiState.onLoginIdChange` as the user edits, `uiState.onContinue` when the user submits the value, and
 * `uiState.onCancel` when the user dismisses the form. `onContinue` asks the operation to validate and either settle
 * with a validated login ID or publish a new error in `uiState`; `onCancel` settles the operation as user-close
 * cancellation. Login ID collection has no busy, resend, or "not you" callback in this content contract.
 */
public typealias LoginIdCollectUiContent =
        @Composable (
            uiState: LoginIdCollectUi.UiState,
            strings: LoginIdCollectStrings,
            modifier: Modifier,
            colors: OwnIdColors,
            errorTextProvider: ((ErrorCode) -> String)?,
            isReadyForInitialFocus: Boolean
        ) -> Unit

/**
 * Supplies custom Compose content for login ID collection.
 *
 * Register an implementation on the OwnID SDK instance when you want all login-ID-collection operation UI for that
 * instance to use app-provided content. For a local override, pass `loginIdCollectContent` to
 * [OwnIdOperationUi]. Local overrides take precedence for the `OwnIdOperationUi` subtree that provides them; otherwise
 * the registered provider content is used.
 *
 * The provider is responsible for returning content that can render the supplied state, display or otherwise surface
 * validation errors, and call the state callbacks for submit and cancel actions. The SDK remains responsible for
 * default parameter resolution, validation, timeout, abort/close handling, and operation settlement.
 */
public interface LoginIdCollectUiProvider {

    /**
     * Returns the content used to render the login ID collection form.
     *
     * The returned composable may be invoked multiple times as operation state, strings, theme, or override inputs
     * change. It should not retain ownership of the operation beyond the callbacks supplied in `uiState`.
     */
    public fun getContent(): LoginIdCollectUiContent

    /**
     * Built-in Compose content provider for login ID collection.
     *
     * The default form requests one-time initial focus when allowed, configures keyboard and autofill hints from the
     * collectable login ID types, updates the operation through `uiState.onLoginIdChange`, and submits through
     * `uiState.onContinue`. Login ID validation failures use the localized collection error string; other errors use
     * `errorTextProvider` when supplied and otherwise fall back to `UiError.localizedMessage`. The default form exposes
     * a cancel action that invokes `uiState.onCancel`; it does not add a busy state, resend action, or "not you" action.
     */
    public companion object Default : LoginIdCollectUiProvider {
        override fun getContent(): LoginIdCollectUiContent = { uiState, uiStrings, modifier, colors, errorTextProvider, isReadyForInitialFocus ->
            LoginIdCollectDefaultUi(uiState, uiStrings, modifier, colors, errorTextProvider, isReadyForInitialFocus)
        }
    }
}
