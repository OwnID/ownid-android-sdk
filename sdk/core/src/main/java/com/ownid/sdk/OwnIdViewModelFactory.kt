@file:JvmName("OwnIdViewModelFactory")

package com.ownid.sdk

import androidx.activity.ComponentActivity
import androidx.annotation.MainThread
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.ownid.sdk.viewmodel.OwnIdBaseViewModel
import com.ownid.sdk.viewmodel.OwnIdEnrollmentViewModel
import com.ownid.sdk.viewmodel.OwnIdLoginViewModel
import com.ownid.sdk.viewmodel.OwnIdRegisterViewModel
import com.ownid.sdk.viewmodel.OwnIdSocialViewModel

/**
 *  Factory to get OwnID ViewModel instance in Java.
 */

/**
 * Returns OwnID ViewModel for Activity. If there is no requested ViewModel instance, it will be created.
 * ```
 * public class MyActivity extends AppCompatActivity {
 *      private OwnIdLoginViewModel ownIdViewModel;
 *
 *      @Override
 *      protected void onCreate(@Nullable Bundle savedInstanceState) {
 *          super.onCreate(savedInstanceState);
 *
 *          ownIdViewModel = OwnIdViewModelFactory.getOwnIdViewModel(
 *              this,
 *              OwnIdLoginViewModel.class,
 *              OwnIdGigyaFactory.getDefault()
 *          );
 *      }
 * }
 * ```
 * Can be used only for OwnID ViewModels.
 */
@MainThread
@Suppress("unused")
@OptIn(InternalOwnIdAPI::class)
public fun <VM : OwnIdBaseViewModel> getOwnIdViewModel(
    activity: ComponentActivity, modelClass: Class<VM>, ownIdInstance: OwnIdInstance
): VM {
    val factory = when (modelClass) {
        OwnIdLoginViewModel::class.java -> OwnIdLoginViewModel.Factory(ownIdInstance)
        OwnIdRegisterViewModel::class.java -> OwnIdRegisterViewModel.Factory(ownIdInstance)
        OwnIdEnrollmentViewModel::class.java -> OwnIdEnrollmentViewModel.Factory(ownIdInstance)
        OwnIdSocialViewModel::class.java -> OwnIdSocialViewModel.Factory(ownIdInstance)
        else -> throw IllegalArgumentException("Unknown OwnID ViewModel class: $modelClass")
    }

    return ViewModelProvider(activity.viewModelStore, factory).get(modelClass).also {
        it.createResultLauncher(activity.activityResultRegistry, activity)
    }
}

/**
 * Returns OwnID ViewModel for Fragment. If there is no requested ViewModel instance, it will be created.
 * ```
 * class MyFragment : Fragment() {
 *      private OwnIdLoginViewModel ownIdViewModel;
 *
 *      @Override
 *      public void onCreate(@Nullable Bundle savedInstanceState) {
 *          super.onCreate(savedInstanceState);
 *
 *          ownIdViewModel = OwnIdViewModelFactory.getOwnIdViewModel(
 *            this,
 *            OwnIdLoginViewModel.class,
 *            OwnIdGigyaFactory.getDefault()
 *          );
 *      }
 * }
 * ```
 * Can be used only for OwnID ViewModels.
 */
@MainThread
@Suppress("unused")
@OptIn(InternalOwnIdAPI::class)
public fun <VM : OwnIdBaseViewModel> getOwnIdViewModel(
    fragment: Fragment, modelClass: Class<VM>, ownIdInstance: OwnIdInstance
): VM {
    val factory = when (modelClass) {
        OwnIdLoginViewModel::class.java -> OwnIdLoginViewModel.Factory(ownIdInstance)
        OwnIdRegisterViewModel::class.java -> OwnIdRegisterViewModel.Factory(ownIdInstance)
        OwnIdEnrollmentViewModel::class.java -> OwnIdEnrollmentViewModel.Factory(ownIdInstance)
        OwnIdSocialViewModel::class.java -> OwnIdSocialViewModel.Factory(ownIdInstance)
        else -> throw IllegalArgumentException("Unknown OwnID ViewModel class: $modelClass")
    }

    return ViewModelProvider(fragment.viewModelStore, factory).get(modelClass).also {
        it.createResultLauncher((fragment.requireActivity() as ComponentActivity).activityResultRegistry, fragment)
    }
}