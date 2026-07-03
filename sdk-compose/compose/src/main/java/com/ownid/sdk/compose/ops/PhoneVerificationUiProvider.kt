package com.ownid.sdk.compose.ops

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ownid.sdk.compose.OwnIdColors
import com.ownid.sdk.compose.OwnIdOperationUi
import com.ownid.sdk.compose.ops.impl.PhoneVerificationDefaultUi
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.ops.ui.PhoneVerificationUi
import com.ownid.sdk.ui.capability.PhoneVerificationStrings

/**
 * Content contract for phone verification operation UI.
 *
 * The SDK calls this content only while the phone verification operation is active. After the operation succeeds, is
 * canceled, or fails terminally, [OwnIdOperationUi] stops rendering operation content and the caller should observe the
 * terminal result from its operation controller.
 *
 * Custom content owns rendering, focus, loading indicators, error presentation, and user-event wiring. The operation
 * owns the challenge, busy/error state, callback effects, cancellation, timeout, and final settlement. Submit the OTP
 * through [PhoneVerificationUi.UiState.onCodeEntered] when the user enters the required code length, request another
 * code through [PhoneVerificationUi.UiState.onResend] only when the challenge resend policy allows it, and use
 * [PhoneVerificationUi.UiState.onCancel] or [PhoneVerificationUi.UiState.onNotYou] for those user actions. Do not
 * complete or cancel the operation from unrelated app state.
 *
 * - `uiState`: Current phone verification state and callbacks. Use `uiState.challenge.channel.channel` as the selected
 *   delivery phone number and `uiState.challenge.methods.otp?.length` as the expected OTP length, falling back only for
 *   defensive rendering.
 * - `strings`: Localized verification text. `strings.message` may contain `%CODE_LENGTH%` and `%LOGIN_ID%`
 *   placeholders for the visible code length and selected delivery phone number.
 * - `modifier`: Modifier supplied to [OwnIdOperationUi].
 * - `colors`: Resolved OwnID color tokens for the current theme.
 * - `errorTextProvider`: Optional provider from SDK-reported [ErrorCode] values to display text. When it is `null`, use
 *   the current `UiError.localizedMessage`.
 * - `isReadyForInitialFocus`: `true` when the surrounding presentation is ready for one-time focus requests.
 */
public typealias PhoneVerificationUiContent =
        @Composable (
            uiState: PhoneVerificationUi.UiState,
            strings: PhoneVerificationStrings,
            modifier: Modifier,
            colors: OwnIdColors,
            errorTextProvider: ((ErrorCode) -> String)?,
            isReadyForInitialFocus: Boolean
        ) -> Unit

/**
 * Supplies custom Compose content for phone verification.
 *
 * Register an implementation on the OwnID SDK instance when you want all phone-verification operation UI for that
 * instance to use app-provided content. For a local override, pass `phoneVerificationContent` to
 * [OwnIdOperationUi]. A local override takes precedence for that app-hosted operation view.
 *
 * App-provided content remains responsible only for rendering and user-event wiring; the operation continues to own
 * challenge completion, resend, cancel, timeout, and result settlement.
 */
public interface PhoneVerificationUiProvider {

    /**
     * Returns the content used to render the phone verification form.
     *
     * Return a stable content function when possible. The SDK may invoke it repeatedly as operation state or localized
     * strings change.
     */
    public fun getContent(): PhoneVerificationUiContent

    /**
     * Built-in Compose content provider for phone verification.
     *
     * The default form requests one-time initial OTP focus when allowed, normalizes entered digits, and submits the
     * code through [PhoneVerificationUi.UiState.onCodeEntered] as soon as the required length is entered while the
     * operation is not busy. It displays the operation busy state, maps errors through the supplied error text provider
     * when present, clears the OTP field when an error arrives, hides resend until the challenge debounce allows it,
     * disables "not you" while busy, and invokes the supplied resend, cancel, and "not you" callbacks only from the
     * matching user actions.
     */
    public companion object Default : PhoneVerificationUiProvider {
        override fun getContent(): PhoneVerificationUiContent = { uiState, uiStrings, modifier, colors, errorTextProvider, isReadyForInitialFocus ->
            PhoneVerificationDefaultUi(uiState, uiStrings, modifier, colors, errorTextProvider, isReadyForInitialFocus)
        }
    }
}
