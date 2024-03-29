@file:JvmName("OwnIdViewModelExt")

package com.ownid.sdk

import androidx.activity.ComponentActivity
import androidx.annotation.MainThread
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelLazy
import com.ownid.sdk.event.OwnIdEvent
import com.ownid.sdk.viewmodel.OwnIdBaseViewModel
import com.ownid.sdk.viewmodel.OwnIdLifecycleObserver
import com.ownid.sdk.viewmodel.OwnIdLoginViewModel
import com.ownid.sdk.viewmodel.OwnIdRegisterViewModel


/**
 * Returns a [Lazy] delegate to access OwnID ViewModel scoped to this [androidx.activity.ComponentActivity]:
 *
 * ```
 * class MyActivity : ComponentActivity() {
 *    val ownIdViewModel: OwnIdLoginViewModel by ownIdViewModel(<OwnId Instance>)
 * }
 * ```
 *
 * This property can be accessed only after the Activity is attached to the Application,
 * and access prior to that will result in [IllegalArgumentException].
 *
 * Can be used only for OwnID ViewModels.
 */
@MainThread
@OptIn(InternalOwnIdAPI::class)
public inline fun <reified VM : OwnIdBaseViewModel<out OwnIdEvent, out OwnIdEvent>> ComponentActivity.ownIdViewModel(ownIdInstance: OwnIdInstance): Lazy<VM> {
    val factory = when (VM::class) {
        OwnIdLoginViewModel::class -> OwnIdLoginViewModel.Factory(ownIdInstance)
        OwnIdRegisterViewModel::class -> OwnIdRegisterViewModel.Factory(ownIdInstance)
        else -> throw IllegalArgumentException("Unknown OwnID ViewModel class: ${VM::class}")
    }

    val viewModelLazy = ViewModelLazy(VM::class, { viewModelStore }, { factory })
    OwnIdLifecycleObserver.observe(this) { viewModelLazy.value }
    return viewModelLazy
}

/**
 * Returns a [Lazy] delegate to access OwnID ViewModel scoped to this [androidx.fragment.app.Fragment]:
 * ```
 * class MyFragment : Fragment() {
 *     val ownIdViewModel: OwnIdLoginViewModel by ownIdViewModel(<OwnId Instance>)
 * }
 * ```
 *
 * This property can be accessed only after this Fragment is attached i.e., after
 * `Fragment.onAttach()`, and access prior to that will result in [IllegalArgumentException].
 *
 * Can be used only for OwnID ViewModels.
 */
@MainThread
@OptIn(InternalOwnIdAPI::class)
public inline fun <reified VM : OwnIdBaseViewModel<out OwnIdEvent, out OwnIdEvent>> Fragment.ownIdViewModel(ownIdInstance: OwnIdInstance): Lazy<VM> {
    val factory = when (VM::class) {
        OwnIdLoginViewModel::class -> OwnIdLoginViewModel.Factory(ownIdInstance)
        OwnIdRegisterViewModel::class -> OwnIdRegisterViewModel.Factory(ownIdInstance)
        else -> throw IllegalArgumentException("Unknown OwnID ViewModel class: ${VM::class}")
    }

    val viewModelLazy = ViewModelLazy(VM::class, { viewModelStore }, { factory })
    OwnIdLifecycleObserver.observe(this) { viewModelLazy.value }
    return viewModelLazy
}