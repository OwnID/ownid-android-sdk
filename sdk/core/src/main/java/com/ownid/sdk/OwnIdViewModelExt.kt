@file:JvmName("OwnIdViewModelExt")

package com.ownid.sdk

import androidx.activity.ComponentActivity
import androidx.annotation.MainThread
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelLazy
import com.ownid.sdk.viewmodel.OwnIdBaseViewModel
import com.ownid.sdk.viewmodel.OwnIdEnrollmentViewModel
import com.ownid.sdk.viewmodel.OwnIdLifecycleObserver
import com.ownid.sdk.viewmodel.OwnIdLoginViewModel
import com.ownid.sdk.viewmodel.OwnIdRegisterViewModel
import com.ownid.sdk.viewmodel.OwnIdSocialViewModel

/**
 * Returns a [Lazy] delegate to access an OwnID ViewModel scoped to this [androidx.activity.ComponentActivity].
 *
 * Example usage:
 * ```
 * class MyActivity : ComponentActivity() {
 *     val ownIdViewModel: OwnIdLoginViewModel by ownIdViewModel()
 * }
 * ```
 *
 * This property can be accessed only after this Activity is attached to the Application,
 * Access prior to that will result in an [IllegalArgumentException].
 *
 * The returned ViewModel is tied to the lifecycle of this Activity and will be automatically cleared
 * when the Activity is destroyed.
 *
 * @param ownIdInstance (optional) The [OwnIdInstance] to be used by this ViewModel. Defaults to the first available OwnID instance.
 * @return A [Lazy] delegate to access the specified OwnID ViewModel.
 * @throws IllegalArgumentException if the requested ViewModel type is not supported.
 */
@MainThread
@OptIn(InternalOwnIdAPI::class)
public inline fun <reified VM : OwnIdBaseViewModel> ComponentActivity.ownIdViewModel(ownIdInstance: OwnIdInstance = OwnId.firstInstanceOrThrow()): Lazy<VM> {
    val factory = when (VM::class) {
        OwnIdLoginViewModel::class -> OwnIdLoginViewModel.Factory(ownIdInstance)
        OwnIdRegisterViewModel::class -> OwnIdRegisterViewModel.Factory(ownIdInstance)
        OwnIdEnrollmentViewModel::class -> OwnIdEnrollmentViewModel.Factory(ownIdInstance)
        OwnIdSocialViewModel::class -> OwnIdSocialViewModel.Factory(ownIdInstance)
        else -> throw IllegalArgumentException("Unknown OwnID ViewModel class: ${VM::class}")
    }

    val viewModelLazy = ViewModelLazy(VM::class, { viewModelStore }, { factory })
    OwnIdLifecycleObserver.observe(this) { viewModelLazy.value }
    return viewModelLazy
}

/**
 * Returns a [Lazy] delegate to access an OwnID ViewModel scoped to this [androidx.fragment.app.Fragment].
 *
 * Example usage:
 * ```
 * class MyFragment : Fragment() {
 *     val ownIdViewModel: OwnIdLoginViewModel by ownIdViewModel()
 * }
 * ```
 *
 * This property can be accessed only after this Fragment is attached, i.e., after
 * `Fragment.onAttach()`. Access prior to that will result in an [IllegalArgumentException].
 *
 * The returned ViewModel is tied to the lifecycle of this Fragment and will be automatically cleared
 * when the Fragment is destroyed.
 *
 * @param ownIdInstance (optional) The [OwnIdInstance] to be used by this ViewModel. Defaults to the first available OwnID instance.
 * @return A [Lazy] delegate to access the specified OwnID ViewModel.
 * @throws IllegalArgumentException if the requested ViewModel type is not supported.
 */
@MainThread
@OptIn(InternalOwnIdAPI::class)
public inline fun <reified VM : OwnIdBaseViewModel> Fragment.ownIdViewModel(ownIdInstance: OwnIdInstance = OwnId.firstInstanceOrThrow()): Lazy<VM> {
    val factory = when (VM::class) {
        OwnIdLoginViewModel::class -> OwnIdLoginViewModel.Factory(ownIdInstance)
        OwnIdRegisterViewModel::class -> OwnIdRegisterViewModel.Factory(ownIdInstance)
        OwnIdEnrollmentViewModel::class -> OwnIdEnrollmentViewModel.Factory(ownIdInstance)
        OwnIdSocialViewModel::class -> OwnIdSocialViewModel.Factory(ownIdInstance)
        else -> throw IllegalArgumentException("Unknown OwnID ViewModel class: ${VM::class}")
    }

    val viewModelLazy = ViewModelLazy(VM::class, { viewModelStore }, { factory })
    OwnIdLifecycleObserver.observe(this) { viewModelLazy.value }
    return viewModelLazy
}