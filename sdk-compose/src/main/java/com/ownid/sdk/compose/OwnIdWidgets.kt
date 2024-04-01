package com.ownid.sdk.compose

import androidx.annotation.StyleRes
import androidx.appcompat.view.ContextThemeWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import com.ownid.sdk.LoginId
import com.ownid.sdk.OwnIdInstance
import com.ownid.sdk.OwnIdIntegration
import com.ownid.sdk.OwnIdLoginType
import com.ownid.sdk.OwnIdPayload
import com.ownid.sdk.R
import com.ownid.sdk.event.OwnIdLoginEvent
import com.ownid.sdk.event.OwnIdLoginFlow
import com.ownid.sdk.event.OwnIdRegisterEvent
import com.ownid.sdk.event.OwnIdRegisterFlow
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.view.OwnIdAuthButton
import com.ownid.sdk.view.OwnIdButton
import com.ownid.sdk.viewmodel.OwnIdLoginViewModel
import com.ownid.sdk.viewmodel.OwnIdRegisterViewModel

/**
 * Class representing OwnID response event for OwnID login flow.
 *
 * This event emitted at the end of the successful OwnID login flow.
 *
 * Use event data to do login within the identity platform.
 *
 * @property loginId    User Login ID that was used in OwnID flow.
 * @property payload    [OwnIdPayload] with the result of OwnID flow.
 * @property authType   A string describing the type of authentication that was used during OwnID flow.
 */
@Immutable
public class OwnIdFlowResponse(public val loginId: LoginId, public val payload: OwnIdPayload, public val authType: String) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as OwnIdFlowResponse
        if (loginId != other.loginId) return false
        if (payload != other.payload) return false
        if (authType != other.authType) return false
        return true
    }

    override fun hashCode(): Int {
        var result = loginId.hashCode()
        result = 31 * result + payload.hashCode()
        result = 31 * result + authType.hashCode()
        return result
    }

    override fun toString(): String = "OwnIdFlowResponse(loginId='$loginId', payload=$payload, authType='$authType')"
}

/**
 * A composable [OwnIdLoginButton] with OwnID login functionality, wrapping [OwnIdButton] with [AndroidView].
 *
 * If no [OwnIdIntegration] component is set in [OwnIdInstance] used in [OwnIdLoginViewModel],
 * the functions [onResponse], [onError], and [onBusy] will be called.
 *
 * If [OwnIdIntegration] component is set in [OwnIdInstance] used in [OwnIdLoginViewModel],
 * the functions [onLogin], [onError], and [onBusy] will be called.
 *
 * @param loginIdProvider       A function returning the current user login id (e.g., email or phone number) as [String].
 * @param modifier              (optional) The modifier to be applied to the [OwnIdLoginButton].
 * @param ownIdLoginViewModel   (optional) An instance of [OwnIdLoginViewModel].
 * @param loginType             (optional) A type of login [OwnIdLoginType].
 * @param onLogin               (optional) A function called when the user successfully completes login with OwnID.
 * @param onResponse            (optional) A function called at the end of the successful OwnID login flow with [OwnIdFlowResponse].
 * @param onError               (optional) A function called when an error occurs during the OwnID login process, with [OwnIdException].
 * @param onBusy                (optional) A function called to notify busy status during the OwnID login process.
 * @param styleRes              A style resource reference. Use it to style [OwnIdButton].
 */
@Composable
public fun OwnIdLoginButton(
    loginIdProvider: (() -> String)?,
    modifier: Modifier = Modifier,
    ownIdLoginViewModel: OwnIdLoginViewModel = ownIdViewModel(),
    loginType: OwnIdLoginType = OwnIdLoginButtonDefaults.LoginType,
    onLogin: (() -> Unit)? = null,
    onResponse: ((OwnIdFlowResponse) -> Unit)? = null,
    onError: ((OwnIdException) -> Unit)? = null,
    onBusy: ((Boolean) -> Unit)? = null,
    @StyleRes styleRes: Int = R.style.OwnIdButton_Default,
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        factory = { context ->
            OwnIdButton(
                context = ContextThemeWrapper(context, R.style.OwnIdTheme_Widgets),
                defStyleRes = styleRes
            ).also { ownIdButton ->
                ownIdLoginViewModel.attachToView(ownIdButton, lifecycleOwner, loginIdProvider, loginType)
                ownIdLoginViewModel.integrationEvents.observe(lifecycleOwner) { ownIdEvent ->
                    when (ownIdEvent) {
                        is OwnIdLoginEvent.Busy -> onBusy?.invoke(ownIdEvent.isBusy)
                        is OwnIdLoginEvent.LoggedIn -> onLogin?.invoke()
                        is OwnIdLoginEvent.Error -> onError?.invoke(ownIdEvent.cause)
                    }
                }
                ownIdLoginViewModel.flowEvents.observe(lifecycleOwner) { ownIdFlowEvent ->
                    when (ownIdFlowEvent) {
                        is OwnIdLoginFlow.Busy -> onBusy?.invoke(ownIdFlowEvent.isBusy)
                        is OwnIdLoginFlow.Response ->
                            onResponse?.invoke(OwnIdFlowResponse(ownIdFlowEvent.loginId, ownIdFlowEvent.payload, ownIdFlowEvent.authType))

                        is OwnIdLoginFlow.Error -> onError?.invoke(ownIdFlowEvent.cause)
                    }
                }
            }
        },
        modifier = modifier
    )
}

/**
 * A [OwnIdLoginButton] composable with OwnID login functionality. It wraps [OwnIdButton] with [AndroidView].
 *
 * @param loginIdProvider       A function that returns current user login id (like email or phone number) as [String].
 * @param modifier              The modifier to be applied to the [OwnIdLoginButton].
 * @param loginType             A type of login [OwnIdLoginType].
 * @param ownIdViewModel        An instance of [OwnIdLoginViewModel].
 * @param styleRes              A style resource reference. Use it to style [OwnIdButton]
 */
@Composable
@Deprecated(message = "Deprecated since 3.2.0")
public fun OwnIdLoginButton(
    loginIdProvider: (() -> String)?,
    modifier: Modifier = Modifier,
    loginType: OwnIdLoginType = OwnIdLoginButtonDefaults.LoginType,
    ownIdViewModel: OwnIdLoginViewModel = OwnIdLoginViewModel,
    @StyleRes styleRes: Int = R.style.OwnIdButton_Default,
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        factory = { context ->
            OwnIdButton(
                context = ContextThemeWrapper(context, R.style.OwnIdTheme_Widgets),
                defStyleRes = styleRes
            ).also { ownIdButton ->
                ownIdViewModel.attachToView(ownIdButton, lifecycleOwner, loginIdProvider, loginType)
            }
        },
        modifier = modifier
    )
}

public object OwnIdLoginButtonDefaults {
    public val LoginType: OwnIdLoginType = OwnIdLoginType.Standard
}

/**
 * A composable [OwnIdAuthLoginButton] with OwnID login functionality, wrapping [OwnIdAuthButton] with [AndroidView].
 *
 * If no [OwnIdIntegration] component is set in [OwnIdInstance] used in [OwnIdLoginViewModel],
 * the functions [onResponse], [onError], and [onBusy] will be called.
 *
 * If [OwnIdIntegration] component is set in [OwnIdInstance] used in [OwnIdLoginViewModel],
 * the functions [onLogin], [onError], and [onBusy] will be called.
 *
 * @param loginIdProvider       A function returning the current user login id (e.g., email or phone number) as [String].
 * @param modifier              (optional) The modifier to be applied to the [OwnIdAuthLoginButton].
 * @param ownIdLoginViewModel   (optional) An instance of [OwnIdLoginViewModel].
 * @param loginType             (optional) A type of login [OwnIdLoginType].
 * @param onLogin               (optional) A function called when the user successfully completes login with OwnID.
 * @param onResponse            (optional) A function called at the end of the successful OwnID login flow with [OwnIdFlowResponse].
 * @param onError               (optional) A function called when an error occurs during the OwnID login process, with [OwnIdException].
 * @param onBusy                (optional) A function called to notify busy status during the OwnID login process.
 * @param styleRes              A style resource reference. Use it to style [OwnIdButton].
 */
@Composable
public fun OwnIdAuthLoginButton(
    loginIdProvider: (() -> String)?,
    modifier: Modifier = Modifier,
    ownIdLoginViewModel: OwnIdLoginViewModel = ownIdViewModel(),
    loginType: OwnIdLoginType = OwnIdAuthLoginButtonDefaults.LoginType,
    onLogin: (() -> Unit)? = null,
    onResponse: ((OwnIdFlowResponse) -> Unit)? = null,
    onError: ((OwnIdException) -> Unit)? = null,
    onBusy: ((Boolean) -> Unit)? = null,
    @StyleRes styleRes: Int = R.style.OwnIdAuthButton_Default,
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        factory = { context ->
            OwnIdAuthButton(
                context = ContextThemeWrapper(context, R.style.OwnIdTheme_Widgets),
                defStyleRes = styleRes
            ).also { ownIdButton ->
                ownIdLoginViewModel.attachToView(ownIdButton, lifecycleOwner, loginIdProvider, loginType)
                ownIdLoginViewModel.integrationEvents.observe(lifecycleOwner) { ownIdEvent ->
                    when (ownIdEvent) {
                        is OwnIdLoginEvent.Busy -> onBusy?.invoke(ownIdEvent.isBusy)
                        is OwnIdLoginEvent.LoggedIn -> onLogin?.invoke()
                        is OwnIdLoginEvent.Error -> onError?.invoke(ownIdEvent.cause)
                    }
                }
                ownIdLoginViewModel.flowEvents.observe(lifecycleOwner) { ownIdFlowEvent ->
                    when (ownIdFlowEvent) {
                        is OwnIdLoginFlow.Busy -> onBusy?.invoke(ownIdFlowEvent.isBusy)
                        is OwnIdLoginFlow.Response ->
                            onResponse?.invoke(OwnIdFlowResponse(ownIdFlowEvent.loginId, ownIdFlowEvent.payload, ownIdFlowEvent.authType))

                        is OwnIdLoginFlow.Error -> onError?.invoke(ownIdFlowEvent.cause)
                    }
                }
            }
        },
        modifier = modifier
    )
}

/**
 * A [OwnIdAuthLoginButton] composable with OwnID login functionality. It wraps [OwnIdAuthButton] with [AndroidView].
 *
 * @param loginIdProvider       A function that returns current user login id (like email or phone number) as [String].
 * @param modifier              The modifier to be applied to the [OwnIdLoginButton].
 * @param loginType             A type of login [OwnIdLoginType].
 * @param ownIdViewModel        An instance of [OwnIdLoginViewModel].
 * @param styleRes              A style resource reference. Use it to style [OwnIdAuthButton]
 */
@Composable
@Deprecated(message = "Deprecated since 3.2.0")
public fun OwnIdAuthLoginButton(
    loginIdProvider: (() -> String)?,
    modifier: Modifier = Modifier,
    loginType: OwnIdLoginType = OwnIdAuthLoginButtonDefaults.LoginType,
    ownIdViewModel: OwnIdLoginViewModel = OwnIdLoginViewModel,
    @StyleRes styleRes: Int = R.style.OwnIdAuthButton_Default,
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        factory = { context ->
            OwnIdAuthButton(
                context = ContextThemeWrapper(context, R.style.OwnIdTheme_Widgets),
                defStyleRes = styleRes
            ).also { ownIdButton ->
                ownIdViewModel.attachToView(ownIdButton, lifecycleOwner, loginIdProvider, loginType)
            }
        },
        modifier = modifier
    )
}

public object OwnIdAuthLoginButtonDefaults {
    public val LoginType: OwnIdLoginType = OwnIdLoginType.Standard
}

/**
 * A composable [OwnIdRegisterButton] with OwnID registration functionality, wrapping [OwnIdButton] with [AndroidView].
 *
 * If no [OwnIdIntegration] component is set in [OwnIdInstance] used in [OwnIdRegisterViewModel],
 * the functions [onResponse], [onError], [onUndo], and [onBusy] will be called.
 *
 * If [OwnIdIntegration] component is set in [OwnIdInstance] used in [OwnIdRegisterViewModel],
 * the functions [onReadyToRegister], [onLogin], [onError], [onUndo], and [onBusy] will be called.
 *
 * @param loginId                   Current user login id (e.g., email or phone number) as [String].
 * @param modifier                  (optional) The modifier to be applied to the [OwnIdRegisterButton].
 * @param ownIdRegisterViewModel    (optional) An instance of [OwnIdRegisterViewModel].
 * @param onReadyToRegister         (optional) A function called when the user successfully completes OwnID registration flow.
 * Ready-to-register state: the OwnID SDK is waiting for the user to enter any additional required data to finish the registration process, see [OwnIdRegisterViewModel.register].
 * @param onLogin                   (optional) A function called when the user successfully completes registration with OwnID and is logged in with OwnID.
 * @param onResponse                (optional) A function called at the end of the successful OwnID registration flow with [OwnIdFlowResponse].
 * @param onError                   (optional) A function called when an error occurs during the OwnID registration process, with [OwnIdException].
 * @param onUndo                    (optional) A function called when the user selects the "Undo" option in the ready-to-register state.
 * @param onBusy                    (optional) A function called to notify the busy status during the OwnID registration process.
 * @param styleRes                  A style resource reference. Use it to style [OwnIdButton].
 */
@Composable
public fun OwnIdRegisterButton(
    loginId: String,
    modifier: Modifier = Modifier,
    ownIdRegisterViewModel: OwnIdRegisterViewModel = ownIdViewModel(),
    onReadyToRegister: ((LoginId) -> Unit)? = null,
    onLogin: (() -> Unit)? = null,
    onResponse: ((OwnIdFlowResponse) -> Unit)? = null,
    onError: ((OwnIdException) -> Unit)? = null,
    onUndo: (() -> Unit)? = null,
    onBusy: ((Boolean) -> Unit)? = null,
    @StyleRes styleRes: Int = R.style.OwnIdButton_Default,
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        factory = { context ->
            OwnIdButton(
                context = ContextThemeWrapper(context, R.style.OwnIdTheme_Widgets),
                defStyleRes = styleRes
            ).also { ownIdButton ->
                ownIdRegisterViewModel.attachToView(ownIdButton, lifecycleOwner)
                ownIdButton.setLoginId(loginId)
                ownIdRegisterViewModel.integrationEvents.observe(lifecycleOwner) { ownIdEvent ->
                    when (ownIdEvent) {
                        is OwnIdRegisterEvent.Busy -> onBusy?.invoke(ownIdEvent.isBusy)
                        is OwnIdRegisterEvent.ReadyToRegister -> onReadyToRegister?.invoke(ownIdEvent.loginId)
                        OwnIdRegisterEvent.Undo -> onUndo?.invoke()
                        is OwnIdRegisterEvent.LoggedIn -> onLogin?.invoke()
                        is OwnIdRegisterEvent.Error -> onError?.invoke(ownIdEvent.cause)
                    }
                }
                ownIdRegisterViewModel.flowEvents.observe(lifecycleOwner) { ownIdFlowEvent ->
                    when (ownIdFlowEvent) {
                        is OwnIdRegisterFlow.Busy -> onBusy?.invoke(ownIdFlowEvent.isBusy)
                        is OwnIdRegisterFlow.Response ->
                            onResponse?.invoke(OwnIdFlowResponse(ownIdFlowEvent.loginId, ownIdFlowEvent.payload, ownIdFlowEvent.authType))

                        OwnIdRegisterFlow.Undo -> onUndo?.invoke()
                        is OwnIdRegisterFlow.Error -> onError?.invoke(ownIdFlowEvent.cause)
                    }
                }
            }
        },
        modifier = modifier,
        update = { ownIdButton -> ownIdButton.setLoginId(loginId) }
    )
}

/**
 * A [OwnIdRegisterButton] composable with OwnID registration functionality. It wraps [OwnIdButton] with [AndroidView].
 *
 * @param loginId               Current user login id (like email or phone number).
 * @param modifier              The modifier to be applied to the [OwnIdRegisterButton].
 * @param onReadyToRegister     A callback function to be invoked when [OwnIdRegisterEvent.ReadyToRegister] event happens.
 * @param onUndo                A callback function to be invoked when [OwnIdRegisterEvent.Undo] event happens.
 * @param ownIdViewModel        An instance of [OwnIdRegisterViewModel].
 * @param styleRes              A style resource reference. Use it to style [OwnIdButton]
 */
@Composable
@Deprecated(message = "Deprecated since 3.2.0")
public fun OwnIdRegisterButton(
    loginId: String,
    modifier: Modifier = Modifier,
    onReadyToRegister: (OwnIdRegisterEvent.ReadyToRegister) -> Unit = {},
    onUndo: () -> Unit = {},
    ownIdViewModel: OwnIdRegisterViewModel = OwnIdRegisterViewModel,
    @StyleRes styleRes: Int = R.style.OwnIdButton_Default,
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        factory = { context ->
            OwnIdButton(
                context = ContextThemeWrapper(context, R.style.OwnIdTheme_Widgets),
                defStyleRes = styleRes
            ).also { ownIdButton ->
                ownIdViewModel.attachToView(ownIdButton, lifecycleOwner)
                ownIdButton.setLoginId(loginId)
                ownIdViewModel.integrationEvents.observe(lifecycleOwner) { ownIdEvent ->
                    when (ownIdEvent) {
                        is OwnIdRegisterEvent.Busy -> Unit
                        is OwnIdRegisterEvent.ReadyToRegister -> onReadyToRegister.invoke(ownIdEvent)
                        OwnIdRegisterEvent.Undo -> onUndo.invoke()
                        is OwnIdRegisterEvent.LoggedIn -> Unit
                        is OwnIdRegisterEvent.Error -> Unit
                    }
                }
            }
        },
        modifier = modifier,
        update = { ownIdButton -> ownIdButton.setLoginId(loginId) }
    )
}