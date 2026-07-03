package com.ownid.sdk.compose.ops

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ownid.sdk.compose.OwnIdColors
import com.ownid.sdk.compose.OwnIdOperationUi
import com.ownid.sdk.compose.ops.impl.EmailVerificationDefaultUi
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.ops.ui.EmailVerificationUi
import com.ownid.sdk.ui.capability.EmailVerificationStrings

/**
 * Content contract for email verification operation UI.
 *
 * Use this slot for app-hosted operation UI or from an [EmailVerificationUiProvider] when the app needs to replace the
 * SDK-provided email verification form. A local `emailVerificationContent` override passed to [OwnIdOperationUi] takes
 * precedence over the provider registered on the OwnID instance.
 *
 * The content receives:
 * - `uiState`: current email verification state and callbacks. Use `uiState.challenge.channel.channel` as the selected
 *   delivery address and `uiState.challenge.methods.otp?.length` as the expected OTP length, falling back only for
 *   defensive rendering.
 * - `strings`: localized verification text. `strings.message` may contain `%CODE_LENGTH%` and `%LOGIN_ID%`
 *   placeholders for the visible code length and selected delivery address.
 * - `modifier`: modifier supplied to [OwnIdOperationUi].
 * - `colors`: resolved OwnID color tokens for the current theme.
 * - `errorTextProvider`: optional provider from SDK-reported [ErrorCode] values to display text. When it is `null`, use
 *   the current `UiError.localizedMessage`.
 * - `isReadyForInitialFocus`: `true` when the surrounding presentation is ready for one-time focus requests.
 *
 * Custom content owns rendering, user input collection, loading display, and error presentation. Invoke callbacks from
 * `uiState` only for matching user actions: submit the entered code through `uiState.onCodeEntered`, request a new code
 * through `uiState.onResend`, and leave cancel/"not you" settlement to `uiState.onCancel` and `uiState.onNotYou`.
 * Avoid duplicate non-cancel actions while `uiState.busy` is `true`. The operation owns challenge completion,
 * cancellation, timeout, callback failures, and the terminal `OperationResult`.
 */
public typealias EmailVerificationUiContent =
        @Composable (
            uiState: EmailVerificationUi.UiState,
            strings: EmailVerificationStrings,
            modifier: Modifier,
            colors: OwnIdColors,
            errorTextProvider: ((ErrorCode) -> String)?,
            isReadyForInitialFocus: Boolean
        ) -> Unit

/**
 * Supplies custom Compose content for email verification.
 *
 * Register an implementation on the OwnID SDK instance when you want all email-verification operation UI for that
 * instance to use app-provided content. For a local override, pass `emailVerificationContent` to
 * [OwnIdOperationUi]; that override wins for the affected Compose subtree.
 */
public interface EmailVerificationUiProvider {

    /**
     * Returns the content used to render the email verification form.
     *
     * The returned composable may be called repeatedly as operation state, localized strings, theme, or focus readiness
     * changes. Keep business effects behind callbacks from `uiState` so recomposition does not submit codes, resend
     * challenges, or cancel the operation on its own.
     */
    public fun getContent(): EmailVerificationUiContent

    /**
     * Built-in Compose content provider for email verification.
     *
     * The default form requests one-time initial OTP focus when allowed, normalizes entered digits, and submits the
     * code through `uiState.onCodeEntered` as soon as the required length is entered while the operation is not busy.
     * It owns the visible busy spinner, clears the OTP field when an error arrives, hides resend until the challenge
     * debounce allows it, disables "not you" while busy, and invokes the supplied resend, cancel, and "not you"
     * callbacks only from the matching user actions.
     */
    public companion object Default : EmailVerificationUiProvider {
        override fun getContent(): EmailVerificationUiContent = { uiState, uiStrings, modifier, colors, errorTextProvider, isReadyForInitialFocus ->
            EmailVerificationDefaultUi(uiState, uiStrings, modifier, colors, errorTextProvider, isReadyForInitialFocus)
        }
    }
}
