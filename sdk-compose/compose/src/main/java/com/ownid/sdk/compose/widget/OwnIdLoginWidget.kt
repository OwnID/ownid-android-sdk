package com.ownid.sdk.compose.widget

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.currentCompositeKeyHash
import androidx.compose.runtime.getValue
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
import com.ownid.sdk.compose.components.OwnIdIconButton
import com.ownid.sdk.compose.components.widgetStrings
import com.ownid.sdk.flow.FlowResult
import com.ownid.sdk.flow.boost.BoostFlow
import com.ownid.sdk.flow.boost.BoostFlowContext
import com.ownid.sdk.flow.boost.BoostLoginFlowFailure
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
 * Shows an OwnID login widget.
 *
 * Add this widget to a login screen to let the user continue with OwnID. Tapping the widget starts the Boost login
 * flow with [FlowInfo.Source.WIDGET_BUTTON].
 * The SDK owns starting and settling the flow; the app owns any session, navigation, or error UI changes it performs
 * from the callbacks.
 *
 * A successful flow calls [onLogin]. [FlowResult.Canceled] calls [onCancel]. Flow start failures and
 * [FlowResult.Failure] call [onError]. While a flow is running, repeated taps are ignored and the default icon button
 * is disabled. [showSpinner] controls whether the busy state is shown as a spinner; it does not make a running flow
 * accept another tap.
 *
 * The widget uses [widgetStrings] immediately when they are provided. Otherwise it starts from
 * [BoostWidgetStrings.DEFAULT] and automatically uses localized widget strings for [instanceName] when they become
 * available.
 *
 * Pass [theme] when this widget should use a specific OwnID theme instead of the current Compose theme.
 *
 * @param onLogin Called for successful login results.
 * @param modifier Defaults to [Modifier].
 * @param loginId Optional login ID to prefill; blank IDs are ignored. Defaults to `null`.
 * @param onError Optional callback for [BoostLoginFlowFailure]. Branch on the concrete failure type to choose the
 *   screen's next step. Defaults to `null`.
 * @param onCancel Optional cancel callback. Defaults to `null`.
 * @param showSpinner Whether to show a spinner when the flow is running. Defaults to `true`.
 * @param widgetId Optional stable ID for this widget instance. You can usually omit it when the widget appears once
 *     in a fixed place on the screen. Provide it when you render multiple login widgets from repeated or dynamic UI
 *     content so each one keeps its own state.
 * @param position Whether the OwnID Boost button appears before or after the "or" separator. Use
 *   [OwnIdBoostButtonPosition.End] when you place the widget after the app's password field. Defaults to
 *   [OwnIdBoostButtonPosition.Start].
 * @param instanceName OwnID instance used by this widget. Defaults to [InstanceName.Default].
 * @param theme OwnID theme for this widget. Defaults to the current OwnID theme.
 * @param widgetStrings Explicit strings for this widget. Defaults to localized strings for [instanceName].
 * @param iconButton Icon-button slot. Defaults to [OwnIdIconButton]. Custom content receives the tap action, resolved
 *   accessibility content description, busy state, and enabled state.
 * @param orText Optional "or" separator content. Pass `null` to hide it.
 */
@Composable
public fun OwnIdLoginWidget(
    onLogin: (BoostFlow.Response.Login) -> Unit,
    modifier: Modifier = Modifier,
    loginId: String? = null,
    onError: ((BoostLoginFlowFailure) -> Unit)? = null,
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
    orText: @Composable (RowScope.(text: String) -> Unit)? = { text ->
        Text(text = text, maxLines = 1)
    }
) {
    val viewModel: OwnIdLoginWidgetViewModel = viewModel(
        key = "OwnIdLoginWidget:$instanceName:${widgetId ?: "auto:$currentCompositeKeyHash"}",
        factory = OwnIdLoginWidgetViewModel.factory(instanceName)
    )

    OwnIdLoginWidget(
        onLogin = onLogin,
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
        orText = orText
    )
}

/**
 * Shows an OwnID login widget using a caller-provided [OwnIdLoginWidgetViewModel].
 *
 * Use this overload when your screen already owns widget state, for example to share a ViewModel across navigation or
 * reuse it in custom widget UI. The composable collects [OwnIdLoginWidgetViewModel.uiEffects] while the current
 * lifecycle is active and maps them to the callback parameters. The caller owns the ViewModel lifetime and should keep
 * it scoped to the screen or flow that owns the callbacks.
 *
 * A successful flow calls [onLogin]. [FlowResult.Canceled] calls [onCancel]. Flow start failures and
 * [FlowResult.Failure] call [onError]. While a flow is running, repeated taps are ignored and the default icon button
 * is disabled. [showSpinner] controls whether the busy state is shown as a spinner; it does not make a running flow
 * accept another tap.
 *
 * @param onLogin Called for successful login results.
 * @param viewModel ViewModel that drives this widget.
 * @param modifier Defaults to [Modifier].
 * @param loginId Optional login ID to prefill; blank IDs are ignored. Defaults to `null`.
 * @param onError Optional callback for [BoostLoginFlowFailure]. Branch on the concrete failure type to choose the
 *   screen's next step. Defaults to `null`.
 * @param onCancel Optional cancel callback. Defaults to `null`.
 * @param showSpinner Whether to show a spinner when the flow is running. Defaults to `true`.
 * @param position Whether the OwnID Boost button appears before or after the "or" separator. Use
 *   [OwnIdBoostButtonPosition.End] when you place the widget after the app's password field. Defaults to
 *   [OwnIdBoostButtonPosition.Start].
 * @param instanceName OwnID instance used by this widget. Defaults to [InstanceName.Default].
 * @param theme OwnID theme for this widget. Defaults to the current OwnID theme.
 * @param widgetStrings Explicit strings for this widget. Defaults to localized strings for [instanceName].
 * @param iconButton Icon-button slot. Defaults to [OwnIdIconButton]. Custom content receives the tap action, resolved
 *   accessibility content description, busy state, and enabled state.
 * @param orText Optional "or" separator content. Pass `null` to hide it.
 */
@Composable
public fun OwnIdLoginWidget(
    onLogin: (BoostFlow.Response.Login) -> Unit,
    viewModel: OwnIdLoginWidgetViewModel,
    modifier: Modifier = Modifier,
    loginId: String? = null,
    onError: ((BoostLoginFlowFailure) -> Unit)? = null,
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
    orText: @Composable (RowScope.(text: String) -> Unit)? = { text ->
        Text(text = text, maxLines = 1)
    }
) {
    val currentOnLogin = rememberUpdatedState(onLogin)
    val currentOnError = rememberUpdatedState(onError)
    val currentOnCancel = rememberUpdatedState(onCancel)

    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(viewModel, lifecycleOwner) {
        viewModel.uiEffects
            .flowWithLifecycle(lifecycleOwner.lifecycle)
            .collect { effect ->
                when (effect) {
                    is OwnIdLoginWidgetViewModel.UiEffect.Login -> currentOnLogin.value(effect.response)
                    is OwnIdLoginWidgetViewModel.UiEffect.Error -> currentOnError.value?.invoke(effect.failure)
                    is OwnIdLoginWidgetViewModel.UiEffect.Canceled -> currentOnCancel.value?.invoke(effect.reason)
                }
            }
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    OwnIdBoostButton(
        onClick = { viewModel.startFlow(loginId = loginId) },
        isBusy = uiState.isRunning,
        modifier = modifier,
        position = position,
        instanceName = instanceName,
        finished = false, // Login widget does not have a checkmark
        showSpinner = showSpinner,
        theme = theme,
        widgetStrings = widgetStrings,
        iconButton = iconButton,
        orText = orText
    )
}

/**
 * ViewModel that drives Boost login widget state.
 *
 * Use this type when the app needs to own widget lifetime or render custom widget UI. [uiState] contains the current
 * rendering state. [uiEffects] contains one-off events that should be collected while the widget is active and mapped
 * to screen callbacks. The ViewModel owns flow execution; the app owns what to do with emitted login, cancel, and
 * failure events.
 *
 * @param boostLoginFlowProvider Starts the Boost login flow for the supplied context.
 */
public class OwnIdLoginWidgetViewModel(
    private val boostLoginFlowProvider: (BoostFlowContext) -> BoostFlow.Controller<BoostFlow.Response.Login, BoostLoginFlowFailure>
) : ViewModel() {

    public companion object {
        private const val FLOW_CONTROLLER_CLOSEABLE_KEY: String = "com.ownid.sdk.compose.widget.login_flow_controller_key"

        /**
         * Creates a [ViewModelProvider.Factory] for [OwnIdLoginWidgetViewModel].
         *
         * @param instanceName OwnID instance used by the default [boostLoginFlowProvider].
         * @param boostLoginFlowProvider Starts the Boost login flow with the provided context. Defaults to
         *   `OwnId.instance(instanceName).flows.boost.login.start(context)`.
         */
        public fun factory(
            instanceName: InstanceName = InstanceName.Default,
            boostLoginFlowProvider: (BoostFlowContext) -> BoostFlow.Controller<BoostFlow.Response.Login, BoostLoginFlowFailure> =
                { context -> OwnId.instance(instanceName).flows.boost.login.start(context) }
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { OwnIdLoginWidgetViewModel(boostLoginFlowProvider) }
        }
    }

    /**
     * Immutable UI state for [OwnIdLoginWidget].
     *
     * @property isRunning `true` while a login flow is running.
     */
    @Immutable
    public class UiState(public val isRunning: Boolean = false) {
        override fun equals(other: Any?): Boolean = this === other || (other is UiState && isRunning == other.isRunning)
        override fun hashCode(): Int = isRunning.hashCode()
        override fun toString(): String = "UiState(isRunning=$isRunning)"
    }

    /**
     * One-off events emitted after a Boost login flow reaches a terminal outcome.
     *
     * Collect [uiEffects] while the widget is active and map each event to the corresponding screen callback.
     */
    public sealed interface UiEffect {
        /** Login succeeded with [response]. */
        public class Login(public val response: BoostFlow.Response.Login) : UiEffect {
            override fun equals(other: Any?): Boolean = this === other || (other is Login && response == other.response)
            override fun hashCode(): Int = response.hashCode()
            override fun toString(): String = "Login(response=$response)"
        }

        /** Login failed with [failure]. */
        public class Error(public val failure: BoostLoginFlowFailure) : UiEffect {
            override fun equals(other: Any?): Boolean = this === other || (other is Error && failure == other.failure)
            override fun hashCode(): Int = failure.hashCode()
            override fun toString(): String = "Error(failure=$failure)"
        }

        /** Login was canceled with [reason]. */
        public class Canceled(public val reason: Reason) : UiEffect {
            override fun equals(other: Any?): Boolean = this === other || (other is Canceled && reason == other.reason)
            override fun hashCode(): Int = reason.hashCode()
            override fun toString(): String = "Canceled(reason=$reason)"
        }
    }

    private sealed interface State {
        data object Idle : State
        data object Running : State
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

    /** Current rendering state for the login widget. */
    public val uiState: StateFlow<UiState> = state
        .map { UiState(isRunning = it is State.Running) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, UiState())

    /**
     * Starts the Boost login flow when no flow is currently running.
     *
     * If a flow is already running, this call is ignored.
     *
     * The flow context includes [FlowInfo.Source.WIDGET_BUTTON] and the normalized [loginId] when it is not blank.
     *
     * Flow start exceptions and [FlowResult.Failure] emit [UiEffect.Error]. [FlowResult.Canceled] emits
     * [UiEffect.Canceled]. Lifecycle cancellation of the ViewModel scope does not emit a callback.
     *
     * @param loginId Optional login ID to prefill; `null`, blank, and whitespace-only values leave the flow context
     *   without a login ID.
     */
    public fun startFlow(loginId: String?) {
        viewModelScope.launch {
            if (state.value is State.Running) return@launch
            state.value = State.Running

            val loginIdString = loginId?.trim()?.takeIf { it.isNotEmpty() }

            @OptIn(InternalOwnIdAPI::class)
            val flowContext = BoostFlowContext {
                loginIdString?.let { this.loginId(it) }
                this.source = FlowInfo.Source.WIDGET_BUTTON
            }

            val controller = try {
                boostLoginFlowProvider(flowContext)
            } catch (error: Exception) {
                effectsChannel.send(
                    UiEffect.Error(
                        BoostLoginFlowFailure.Unexpected(
                            errorCode = ErrorCode.IntegrationError,
                            message = error.message ?: "Failed to start login flow",
                            error = error
                        )
                    )
                )
                state.value = State.Idle
                return@launch
            }

            addCloseable(FLOW_CONTROLLER_CLOSEABLE_KEY, controller)

            when (val flowResult = controller.whenSettled()) {
                is FlowResult.Failure -> effectsChannel.send(UiEffect.Error(flowResult.failure))
                is FlowResult.Canceled -> effectsChannel.send(UiEffect.Canceled(flowResult.reason))
                is FlowResult.Success -> effectsChannel.send(UiEffect.Login(flowResult.success))
            }
            state.value = State.Idle
        }
    }
}
