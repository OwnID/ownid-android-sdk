package com.ownid.sdk.compose.widget

import android.app.Activity
import android.net.Uri
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.currentCompositeKeyHash
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ownid.sdk.OwnId
import com.ownid.sdk.analytics.FlowInfo
import com.ownid.sdk.compose.OwnIdTheme
import com.ownid.sdk.compose.components.OwnIdBoostButton
import com.ownid.sdk.compose.components.OwnIdBoostButtonPosition
import com.ownid.sdk.compose.components.OwnIdCheckmark
import com.ownid.sdk.compose.components.OwnIdIconButton
import com.ownid.sdk.compose.components.widgetStrings
import com.ownid.sdk.flow.FlowResult
import com.ownid.sdk.flow.boost.BoostCreatePasskeyFlowFailure
import com.ownid.sdk.flow.boost.BoostFlow
import com.ownid.sdk.flow.boost.BoostFlowContext
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.InstanceName
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.Reason
import com.ownid.sdk.ui.capability.BoostWidgetStrings
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Shows an OwnID create-passkey widget for registration or account-creation UI.
 *
 * Add this widget where the app collects registration details and wants OwnID to handle the create-passkey path.
 * Tapping the widget starts the Boost create-passkey flow with [FlowInfo.Source.WIDGET_BUTTON], scoped to this
 * widget's [Activity] for native UI. The flow can still return an existing-account login result when the resolved
 * account state requires it.
 * The SDK owns starting and settling the flow; the app owns any session, navigation, registration, or error UI changes
 * it performs from the callbacks.
 *
 * A create-passkey result calls [onNewPasskey] and shows the checkmark state. A login result calls [onLogin].
 * [FlowResult.Canceled] calls [onCancel]. Flow start failures and [FlowResult.Failure] call [onError]. When the
 * visible checkmark state is cleared because the login ID no longer matches the in-memory create-passkey result,
 * [onReset] is called. While a flow is running, repeated taps are ignored and the default icon button is disabled.
 * [showSpinner] controls whether the busy state is shown as a spinner; it does not make a running flow accept another
 * tap.
 *
 * The widget uses [widgetStrings] immediately when they are provided. Otherwise it starts from
 * [BoostWidgetStrings.DEFAULT] and automatically uses localized widget strings for [instanceName] when they become
 * available.
 *
 * Pass [theme] when this widget should use a specific OwnID theme instead of the current Compose theme.
 *
 * @param onLogin Called for successful login results.
 * @param onNewPasskey Called when a [BoostFlow.Response.CreatePasskey] result is emitted, including re-emission from
 *   the widget ViewModel while it is still alive. The app owns its editable login ID field and must update it from
 *   `response.loginId.id` before keeping the result for registration. Edits made while a flow is running do not
 *   retarget that flow or cause an intermediate [onReset].
 * @param onReset Called when the widget clears its visible checkmark state. The app should clear any screen state that
 *   was derived from the previous create-passkey callback.
 * @param modifier Defaults to [Modifier].
 * @param loginId Optional login ID to prefill; blank IDs are ignored. Defaults to `null`.
 * @param onError Optional callback for [BoostCreatePasskeyFlowFailure]. Branch on the concrete failure type to choose
 *   the screen's next step. Defaults to `null`.
 * @param onCancel Optional cancel callback. Defaults to `null`.
 * @param showSpinner Whether to show a spinner when the flow is running. Defaults to `true`.
 * @param widgetId Optional stable ID for this logical widget. You can usually omit it when the widget appears once
 *     in a fixed place. Provide a distinct stable value for each create-passkey widget in repeated or dynamic UI. Use
 *     the same value when the same logical widget moves within the Compose tree so it keeps the same ViewModel while
 *     its ViewModel scope remains alive.
 * @param position Whether the OwnID Boost button appears before or after the "or" separator. Use
 *   [OwnIdBoostButtonPosition.End] when you place the widget after the app's password field. Defaults to
 *   [OwnIdBoostButtonPosition.Start].
 * @param instanceName OwnID instance used by this widget. Defaults to [InstanceName.Default].
 * @param theme OwnID theme for this widget. Defaults to the current OwnID theme.
 * @param widgetStrings Explicit strings for this widget. Defaults to localized strings for [instanceName].
 * @param iconButton Icon-button slot. Defaults to [OwnIdIconButton]. Custom content receives the tap action, resolved
 *   accessibility content description, busy state, and enabled state.
 * @param checkmark Checkmark slot shown after create-passkey success. Defaults to [OwnIdCheckmark].
 * @param orText Optional "or" separator content. Pass `null` to hide it.
 */
@Composable
public fun OwnIdCreatePasskeyWidget(
    onLogin: (BoostFlow.Response.Login) -> Unit,
    onNewPasskey: (BoostFlow.Response.CreatePasskey) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
    loginId: String? = null,
    onError: ((BoostCreatePasskeyFlowFailure) -> Unit)? = null,
    onCancel: ((Reason) -> Unit)? = null,
    showSpinner: Boolean = true,
    widgetId: String? = null,
    position: OwnIdBoostButtonPosition = OwnIdBoostButtonPosition.Start,
    instanceName: InstanceName = InstanceName.Default,
    theme: OwnIdTheme = OwnIdTheme.capture(),
    widgetStrings: BoostWidgetStrings = widgetStrings(instanceName),
    iconButton: @Composable (onClick: () -> Unit, contentDescription: String, isBusy: Boolean, enabled: Boolean) -> Unit =
        { onClick, contentDescription, isBusy, enabled ->
            OwnIdIconButton(onClick, contentDescription, isBusy = isBusy, enabled = enabled)
        },
    checkmark: @Composable AnimatedVisibilityScope.() -> Unit = {
        OwnIdCheckmark()
    },
    orText: @Composable (RowScope.(text: String) -> Unit)? = { text ->
        Text(text = text, maxLines = 1)
    }
) {
    val viewModel: OwnIdCreatePasskeyWidgetViewModel = viewModel(
        key = "OwnIdCreatePasskeyWidget:${Uri.encode(instanceName.value)}:" + (widgetId?.let { "id:$it" } ?: "auto:$currentCompositeKeyHash"),
        factory = OwnIdCreatePasskeyWidgetViewModel.factory(instanceName)
    )

    OwnIdCreatePasskeyWidget(
        onLogin = onLogin,
        onNewPasskey = onNewPasskey,
        onReset = onReset,
        modifier = modifier,
        loginId = loginId,
        onError = onError,
        onCancel = onCancel,
        showSpinner = showSpinner,
        position = position,
        instanceName = instanceName,
        viewModel = viewModel,
        theme = theme,
        widgetStrings = widgetStrings,
        iconButton = iconButton,
        checkmark = checkmark,
        orText = orText
    )
}

/**
 * Shows an OwnID create-passkey widget using a caller-provided [OwnIdCreatePasskeyWidgetViewModel].
 *
 * Use this overload when your screen owns the widget state. The caller owns the ViewModel lifetime. Keep it dedicated
 * to one logical widget. You may retain it when that widget is recreated; do not pass it to a different widget or to
 * two simultaneously displayed widget instances.
 * The composable keeps the ViewModel's effective login ID in sync and collects
 * [OwnIdCreatePasskeyWidgetViewModel.uiEffects] while the current lifecycle is active.
 * Use it where the app collects registration details and wants OwnID to handle the create-passkey path.
 *
 * A create-passkey result calls [onNewPasskey] and shows the checkmark state. A login result calls [onLogin].
 * [FlowResult.Canceled] calls [onCancel]. Flow start failures and [FlowResult.Failure] call [onError]. When the
 * visible checkmark state is cleared because the login ID no longer matches the in-memory create-passkey result,
 * [onReset] is called. While a flow is running, repeated taps are ignored and the default icon button is disabled.
 * [showSpinner] controls whether the busy state is shown as a spinner; it does not make a running flow accept another
 * tap.
 *
 * @param onLogin Called for successful login results.
 * @param onNewPasskey Called when a [BoostFlow.Response.CreatePasskey] result is emitted, including re-emission from
 *   the widget ViewModel while it is still alive. The app owns its editable login ID field and must update it from
 *   `response.loginId.id` before keeping the result for registration. Edits made while a flow is running do not
 *   retarget that flow or cause an intermediate [onReset].
 * @param onReset Called when the widget clears its visible checkmark state. The app should clear any screen state that
 *   was derived from the previous create-passkey callback.
 * @param viewModel ViewModel dedicated to this logical widget.
 * @param modifier Defaults to [Modifier].
 * @param loginId Optional login ID to prefill; blank IDs are ignored. Defaults to `null`.
 * @param onError Optional callback for [BoostCreatePasskeyFlowFailure]. Branch on the concrete failure type to choose
 *   the screen's next step. Defaults to `null`.
 * @param onCancel Optional cancel callback. Defaults to `null`.
 * @param showSpinner Whether to show a spinner when the flow is running. Defaults to `true`.
 * @param position Whether the OwnID Boost button appears before or after the "or" separator. Use
 *   [OwnIdBoostButtonPosition.End] when you place the widget after the app's password field. Defaults to
 *   [OwnIdBoostButtonPosition.Start].
 * @param instanceName OwnID instance used for default localized widget strings. The supplied ViewModel's starter
 *   chooses the flow instance. Defaults to [InstanceName.Default].
 * @param theme OwnID theme for this widget. Defaults to the current OwnID theme.
 * @param widgetStrings Explicit strings for this widget. Defaults to localized strings for [instanceName].
 * @param iconButton Icon-button slot. Defaults to [OwnIdIconButton]. Custom content receives the tap action, resolved
 *   accessibility content description, busy state, and enabled state.
 * @param checkmark Checkmark slot shown after create-passkey success. Defaults to [OwnIdCheckmark].
 * @param orText Optional "or" separator content. Pass `null` to hide it.
 */
@Composable
public fun OwnIdCreatePasskeyWidget(
    onLogin: (BoostFlow.Response.Login) -> Unit,
    onNewPasskey: (BoostFlow.Response.CreatePasskey) -> Unit,
    onReset: () -> Unit,
    viewModel: OwnIdCreatePasskeyWidgetViewModel,
    modifier: Modifier = Modifier,
    loginId: String? = null,
    onError: ((BoostCreatePasskeyFlowFailure) -> Unit)? = null,
    onCancel: ((Reason) -> Unit)? = null,
    showSpinner: Boolean = true,
    position: OwnIdBoostButtonPosition = OwnIdBoostButtonPosition.Start,
    instanceName: InstanceName = InstanceName.Default,
    theme: OwnIdTheme = OwnIdTheme.capture(),
    widgetStrings: BoostWidgetStrings = widgetStrings(instanceName),
    iconButton: @Composable (onClick: () -> Unit, contentDescription: String, isBusy: Boolean, enabled: Boolean) -> Unit =
        { onClick, contentDescription, isBusy, enabled ->
            OwnIdIconButton(onClick, contentDescription, isBusy = isBusy, enabled = enabled)
        },
    checkmark: @Composable AnimatedVisibilityScope.() -> Unit = {
        OwnIdCheckmark()
    },
    orText: @Composable (RowScope.(text: String) -> Unit)? = { text ->
        Text(text = text, maxLines = 1)
    }
) {
    val currentOnLogin = rememberUpdatedState(onLogin)
    val currentOnNewPasskey = rememberUpdatedState(onNewPasskey)
    val currentOnReset = rememberUpdatedState(onReset)
    val currentOnError = rememberUpdatedState(onError)
    val currentOnCancel = rememberUpdatedState(onCancel)

    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(viewModel, lifecycleOwner) {
        viewModel.uiEffects
            .flowWithLifecycle(lifecycleOwner.lifecycle)
            .collect { result ->
                when (result) {
                    OwnIdCreatePasskeyWidgetViewModel.UiEffect.ResetRequested -> currentOnReset.value()
                    is OwnIdCreatePasskeyWidgetViewModel.UiEffect.Login -> currentOnLogin.value(result.response)
                    is OwnIdCreatePasskeyWidgetViewModel.UiEffect.CreatePasskey -> currentOnNewPasskey.value(result.response)
                    is OwnIdCreatePasskeyWidgetViewModel.UiEffect.Error -> currentOnError.value?.invoke(result.failure)
                    is OwnIdCreatePasskeyWidgetViewModel.UiEffect.Canceled -> currentOnCancel.value?.invoke(result.reason)
                }
            }
    }

    LaunchedEffect(viewModel, loginId) {
        viewModel.onLoginIdChanged(loginId)
    }

    key(viewModel) {
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val activitySource = rememberWidgetUiActivitySource()
        OwnIdBoostButton(
            onClick = { viewModel.startFlow(activitySource, loginId = loginId) },
            isBusy = uiState.isRunning,
            modifier = modifier,
            position = position,
            instanceName = instanceName,
            finished = uiState.showCheckmark,
            showSpinner = showSpinner,
            theme = theme,
            widgetStrings = widgetStrings,
            iconButton = iconButton,
            checkmark = checkmark,
            orText = orText
        )
    }
}

/**
 * ViewModel that drives create-passkey widget state.
 *
 * Use this type when the app needs to own widget lifetime or supply a custom flow starter. [uiState] contains current
 * rendering state, including whether the success checkmark should be shown. [uiEffects] contains one-off events that
 * should be collected while the widget is active and mapped to screen callbacks. The ViewModel owns flow execution and
 * in-memory completion state; the app owns what to do with emitted create-passkey, login, reset, cancel, and failure
 * events.
 *
 * The ViewModel keeps the most recent create-passkey response in memory while it is alive so the widget can restore or
 * clear the completion state as the effective login ID changes. It does not persist the response outside the ViewModel.
 *
 * @param boostCreatePasskeyFlowProvider Starts the Boost create-passkey flow for the supplied [Activity] and context.
 *   The Activity is request-scoped and must not be retained after the returned flow settles. Custom starters choose
 *   their own SDK instance and UI-context binding.
 */
public class OwnIdCreatePasskeyWidgetViewModel(
    private val boostCreatePasskeyFlowProvider: (Activity, BoostFlowContext) -> BoostFlow.Controller<BoostFlow.Response, BoostCreatePasskeyFlowFailure>
) : ViewModel() {

    public companion object {
        private const val FLOW_CONTROLLER_CLOSEABLE_KEY: String = "com.ownid.sdk.compose.widget.create_passkey_flow_controller_key"

        /**
         * Creates a [ViewModelProvider.Factory] for [OwnIdCreatePasskeyWidgetViewModel].
         *
         * @param instanceName OwnID instance used by the default [boostCreatePasskeyFlowProvider].
         * @param boostCreatePasskeyFlowProvider Starts the Boost create-passkey flow with the provided [Activity] and
         *   context. The Activity is request-scoped and must not be retained after the returned flow settles. The
         *   default scopes [instanceName]'s flows to that Activity for each new flow.
         */
        public fun factory(
            instanceName: InstanceName = InstanceName.Default,
            boostCreatePasskeyFlowProvider: (Activity, BoostFlowContext) -> BoostFlow.Controller<BoostFlow.Response, BoostCreatePasskeyFlowFailure> =
                { activity, context ->
                    OwnId.instance(instanceName).flows.withProviders { uiContext(activity) }.boost.createPasskey.start(context)
                }
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { OwnIdCreatePasskeyWidgetViewModel(boostCreatePasskeyFlowProvider) }
        }
    }

    /**
     * Immutable UI state for [OwnIdCreatePasskeyWidget].
     *
     * @property isRunning `true` while a create-passkey flow is running.
     * @property showCheckmark `true` when a create-passkey response is in visible ready state.
     */
    @Immutable
    public class UiState(public val isRunning: Boolean = false, public val showCheckmark: Boolean = false) {
        override fun equals(other: Any?): Boolean = this === other || (other is UiState && isRunning == other.isRunning && showCheckmark == other.showCheckmark)
        override fun hashCode(): Int = 31 * isRunning.hashCode() + showCheckmark.hashCode()
        override fun toString(): String = "UiState(isRunning=$isRunning, showCheckmark=$showCheckmark)"
    }

    /**
     * One-off events emitted by the create-passkey widget.
     *
     * Collect [uiEffects] while the widget is active and map each event to the corresponding screen callback.
     */
    public sealed interface UiEffect {
        /** Requests UI reset after the visible checkmark state is cleared. */
        public data object ResetRequested : UiEffect

        /** Login succeeded with [response]. */
        public class Login(public val response: BoostFlow.Response.Login) : UiEffect {
            override fun equals(other: Any?): Boolean = this === other || (other is Login && response == other.response)
            override fun hashCode(): Int = response.hashCode()
            override fun toString(): String = "Login(response=$response)"
        }

        /**
         * Create-passkey succeeded with [response]. The collector owns updating any editable login ID field from
         * `response.loginId.id` before keeping it for registration.
         */
        public class CreatePasskey(public val response: BoostFlow.Response.CreatePasskey) : UiEffect {
            override fun equals(other: Any?): Boolean = this === other || (other is CreatePasskey && response == other.response)
            override fun hashCode(): Int = response.hashCode()
            override fun toString(): String = "CreatePasskey(response=$response)"
        }

        /** Flow failed with [failure]. */
        public class Error(public val failure: BoostCreatePasskeyFlowFailure) : UiEffect {
            override fun equals(other: Any?): Boolean = this === other || (other is Error && failure == other.failure)
            override fun hashCode(): Int = failure.hashCode()
            override fun toString(): String = "Error(failure=$failure)"
        }

        /** Flow was canceled with [reason]. */
        public class Canceled(public val reason: Reason) : UiEffect {
            override fun equals(other: Any?): Boolean = this === other || (other is Canceled && reason == other.reason)
            override fun hashCode(): Int = reason.hashCode()
            override fun toString(): String = "Canceled(reason=$reason)"
        }
    }

    private sealed interface State {
        data object Idle : State
        data object Running : State
        data class Ready(val response: BoostFlow.Response.CreatePasskey) : State
        data class Stored(val response: BoostFlow.Response.CreatePasskey) : State
    }

    private val effectsChannel = Channel<UiEffect>(Channel.BUFFERED)

    /**
     * One-off callback events emitted by this ViewModel.
     *
     * Collect this flow while the widget UI is active and map events to screen callbacks. Events are not rendering
     * state; do not use them as durable app state.
     */
    public val uiEffects: Flow<UiEffect> = effectsChannel.receiveAsFlow()

    private val state = MutableStateFlow<State>(State.Idle)

    /** Current rendering state for the create-passkey widget. */
    public val uiState: StateFlow<UiState> = state
        .map { UiState(isRunning = it is State.Running, showCheckmark = it is State.Ready) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, UiState())

    /**
     * Updates in-memory create-passkey presentation state when the effective login ID changes.
     *
     * If the widget is showing a checkmark for a remembered create-passkey result and [loginId] changes, the
     * checkmark is cleared and [UiEffect.ResetRequested] is emitted. If the login ID later matches that in-memory
     * result again, [UiEffect.CreatePasskey] is re-emitted and the checkmark is shown again. Calls while a flow is
     * running are ignored. Blank and whitespace-only values are treated as absent.
     */
    public fun onLoginIdChanged(loginId: String?) {
        viewModelScope.launch {
            if (state.value is State.Running) return@launch
            val loginIdString = loginId?.trim()?.takeIf { it.isNotEmpty() }

            when (val currentState = state.value) {
                is State.Ready -> if (loginIdString != currentState.response.loginId.id) {
                    effectsChannel.send(UiEffect.ResetRequested)
                    state.value = State.Stored(currentState.response)
                }

                is State.Stored -> if (loginIdString == currentState.response.loginId.id) {
                    effectsChannel.send(UiEffect.CreatePasskey(currentState.response))
                    state.value = State.Ready(currentState.response)
                }

                else -> Unit
            }
        }
    }

    /**
     * Starts the create-passkey flow when no flow is currently running, using the widget's Activity for native UI.
     *
     * If a flow is already running, this call is ignored.
     *
     * If a successful create-passkey response is already held by this ViewModel, this may emit
     * [UiEffect.ResetRequested] or re-emit [UiEffect.CreatePasskey] without starting a new flow. Otherwise, the flow
     * context includes [FlowInfo.Source.WIDGET_BUTTON] and the normalized [loginId] when it is not blank.
     *
     * Flow start exceptions and [FlowResult.Failure] emit [UiEffect.Error]. [FlowResult.Canceled] emits
     * [UiEffect.Canceled]. A [FlowResult.Success] with [BoostFlow.Response.Login] emits [UiEffect.Login], and a
     * success with [BoostFlow.Response.CreatePasskey] emits [UiEffect.CreatePasskey] and shows the checkmark.
     * Lifecycle cancellation of the ViewModel scope does not emit a callback.
     *
     * @param activitySource Weak host source owned by the widget composition.
     * @param loginId Optional login ID to prefill; `null`, blank, and whitespace-only values leave the flow context
     *   without a login ID.
     */
    internal fun startFlow(activitySource: WidgetUiActivitySource, loginId: String?) {
        viewModelScope.launch {
            val loginIdString = loginId?.trim()?.takeIf { it.isNotEmpty() }

            when (val currentState = state.value) {
                is State.Running -> return@launch
                is State.Ready -> {
                    effectsChannel.send(UiEffect.ResetRequested)
                    state.value = State.Stored(currentState.response)
                    return@launch
                }

                is State.Stored -> {
                    if (loginIdString == currentState.response.loginId.id) {
                        effectsChannel.send(UiEffect.CreatePasskey(currentState.response))
                        state.value = State.Ready(currentState.response)
                        return@launch
                    }
                    state.value = State.Idle
                }

                is State.Idle -> Unit
            }

            state.value = State.Running

            @OptIn(InternalOwnIdAPI::class)
            val flowContext = BoostFlowContext {
                loginIdString?.let { this.loginId(it) }
                this.source = FlowInfo.Source.WIDGET_BUTTON
            }

            val controller = try {
                boostCreatePasskeyFlowProvider(activitySource.current(), flowContext)
            } catch (error: Exception) {
                effectsChannel.send(
                    UiEffect.Error(
                        BoostCreatePasskeyFlowFailure.Unexpected(
                            errorCode = ErrorCode.IntegrationError,
                            message = error.message ?: "Failed to start create passkey flow",
                            error = error
                        )
                    )
                )
                state.value = State.Idle
                return@launch
            }

            addCloseable(FLOW_CONTROLLER_CLOSEABLE_KEY, controller)

            val nextState = when (val flowResult = controller.whenSettled()) {
                is FlowResult.Failure -> {
                    effectsChannel.send(UiEffect.Error(flowResult.failure))
                    State.Idle
                }

                is FlowResult.Canceled -> {
                    effectsChannel.send(UiEffect.Canceled(flowResult.reason))
                    State.Idle
                }

                is FlowResult.Success -> when (val response = flowResult.success) {
                    is BoostFlow.Response.Login -> {
                        effectsChannel.send(UiEffect.Login(response))
                        State.Idle
                    }

                    is BoostFlow.Response.CreatePasskey -> {
                        effectsChannel.send(UiEffect.CreatePasskey(response))
                        State.Ready(response)
                    }
                }
            }
            state.value = nextState
        }
    }
}
