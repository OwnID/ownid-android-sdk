package com.ownid.sdk.compose

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnId
import com.ownid.sdk.OwnIdInstance
import com.ownid.sdk.ownIdViewModel
import com.ownid.sdk.viewmodel.OwnIdBaseViewModel
import com.ownid.sdk.viewmodel.OwnIdLoginViewModel
import com.ownid.sdk.viewmodel.OwnIdRegisterViewModel

@Composable
@OptIn(InternalOwnIdAPI::class)
public inline fun <reified VM : OwnIdBaseViewModel> ownIdViewModel(
    ownIdInstance: OwnIdInstance = OwnId.instance,
    viewModelStoreOwner: ViewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current) {
        "No ViewModelStoreOwner was provided via LocalViewModelStoreOwner"
    }
): VM {
    val activityResultRegistry = checkNotNull(LocalActivityResultRegistryOwner.current) {
        "No ActivityResultRegistryOwner was provided via LocalActivityResultRegistryOwner"
    }.activityResultRegistry

    val factory = when (VM::class) {
        com.ownid.sdk.viewmodel.OwnIdLoginViewModel::class -> com.ownid.sdk.viewmodel.OwnIdLoginViewModel.Factory(ownIdInstance)
        com.ownid.sdk.viewmodel.OwnIdRegisterViewModel::class -> com.ownid.sdk.viewmodel.OwnIdRegisterViewModel.Factory(ownIdInstance)
        com.ownid.sdk.viewmodel.OwnIdEnrollmentViewModel::class -> com.ownid.sdk.viewmodel.OwnIdEnrollmentViewModel.Factory(ownIdInstance)
        else -> throw IllegalArgumentException("Unknown OwnID ViewModel class: ${VM::class}")
    }

    val ownIdViewModel = viewModel(
        modelClass = VM::class.java,
        viewModelStoreOwner = viewModelStoreOwner,
        factory = factory
    )

    DisposableEffect(ownIdViewModel, activityResultRegistry) {
        ownIdViewModel.createResultLauncher(activityResultRegistry)
        onDispose {
            ownIdViewModel.unregisterResultLauncher()
        }
    }
    return ownIdViewModel
}

/**
 * Returns existing instance of [OwnIdLoginViewModel].
 *
 * The [OwnIdLoginViewModel] must be created before with [ownIdViewModel] within the ComponentActivity current Compose component attached to.
 *
 * The [OwnIdLoginViewModel] is always bound to ComponentActivity viewModelStore.
 */
@Deprecated(message = "Deprecated since 3.2.0", replaceWith = ReplaceWith("ownIdViewModel<OwnIdLoginViewModel>()"))
public val OwnIdLoginViewModel: OwnIdLoginViewModel
    @Composable
    get() = viewModel(LocalContext.current.findComponentActivity())


/**
 * Returns existing instance of [OwnIdRegisterViewModel].
 *
 * The [OwnIdRegisterViewModel] must be created before with [ownIdViewModel] within the ComponentActivity current Compose component attached to.
 *
 * The [OwnIdRegisterViewModel] is always bound to ComponentActivity viewModelStore.
 */
@Deprecated(message = "Deprecated since 3.2.0", replaceWith = ReplaceWith("ownIdViewModel<OwnIdRegisterViewModel>()"))
public val OwnIdRegisterViewModel: OwnIdRegisterViewModel
    @Composable
    get() = viewModel(LocalContext.current.findComponentActivity())


private fun Context.findComponentActivity(): ComponentActivity {
    var context = this
    while (context is ContextWrapper) {
        if (context is ComponentActivity) return context
        context = context.baseContext
    }
    throw IllegalStateException("ViewModel should be called in the context of an ComponentActivity")
}