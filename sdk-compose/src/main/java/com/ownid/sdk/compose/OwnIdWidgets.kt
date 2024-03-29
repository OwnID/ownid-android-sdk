package com.ownid.sdk.compose

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.annotation.StyleRes
import androidx.appcompat.view.ContextThemeWrapper
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ownid.sdk.OwnIdLoginType
import com.ownid.sdk.R
import com.ownid.sdk.event.OwnIdRegisterEvent
import com.ownid.sdk.ownIdViewModel
import com.ownid.sdk.view.OwnIdAuthButton
import com.ownid.sdk.view.OwnIdButton
import com.ownid.sdk.viewmodel.OwnIdLoginViewModel
import com.ownid.sdk.viewmodel.OwnIdRegisterViewModel


/**
 * Returns existing instance of [OwnIdLoginViewModel].
 *
 * The [OwnIdLoginViewModel] must be created before with [ownIdViewModel] within the ComponentActivity current Compose component attached to.
 *
 * The [OwnIdLoginViewModel] is always bound to ComponentActivity viewModelStore.
 */
public val OwnIdLoginViewModel: OwnIdLoginViewModel
    @Composable
    get() = viewModel(LocalContext.current.findComponentActivity())

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
 * A [OwnIdAuthLoginButton] composable with OwnID login functionality. It wraps [OwnIdAuthButton] with [AndroidView].
 *
 * @param loginIdProvider       A function that returns current user login id (like email or phone number) as [String].
 * @param modifier              The modifier to be applied to the [OwnIdLoginButton].
 * @param loginType             A type of login [OwnIdLoginType].
 * @param ownIdViewModel        An instance of [OwnIdLoginViewModel].
 * @param styleRes              A style resource reference. Use it to style [OwnIdAuthButton]
 */
@Composable
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
 * Returns existing instance of [OwnIdRegisterViewModel].
 *
 * The [OwnIdRegisterViewModel] must be created before with [ownIdViewModel] within the ComponentActivity current Compose component attached to.
 *
 * The [OwnIdRegisterViewModel] is always bound to ComponentActivity viewModelStore.
 */
public val OwnIdRegisterViewModel: OwnIdRegisterViewModel
    @Composable
    get() = viewModel(LocalContext.current.findComponentActivity())

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

private fun Context.findComponentActivity(): ComponentActivity {
    var context = this
    while (context is ContextWrapper) {
        if (context is ComponentActivity) return context
        context = context.baseContext
    }
    throw IllegalStateException("ViewModel should be called in the context of an ComponentActivity")
}