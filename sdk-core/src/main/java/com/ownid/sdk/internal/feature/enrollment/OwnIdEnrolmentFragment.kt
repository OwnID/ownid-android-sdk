package com.ownid.sdk.internal.feature.enrollment

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.annotation.RestrictTo
import androidx.appcompat.view.ContextThemeWrapper
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnIdCoreImpl
import com.ownid.sdk.R
import com.ownid.sdk.internal.component.OwnIdInternalLogger
import com.ownid.sdk.internal.component.locale.OwnIdLocaleKey
import com.ownid.sdk.internal.component.locale.OwnIdLocaleService
import com.ownid.sdk.internal.feature.OwnIdActivity
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class OwnIdEnrolmentFragment(@LayoutRes private val contentLayoutId: Int = R.layout.com_ownid_sdk_internal_ui_enrollment) :
    BottomSheetDialogFragment(), OwnIdLocaleService.LocaleUpdateListener {

    internal companion object {
        private const val TAG = "com.ownid.sdk.internal.tag.OwnIdEnrolment"

        internal fun show(fragmentManager: FragmentManager) = OwnIdEnrolmentFragment().apply {
            OwnIdInternalLogger.logD(this, "show", "Invoked")
            show(fragmentManager, TAG)
        }
    }

    private lateinit var ownIdCore: OwnIdCoreImpl
    private lateinit var viewModel: OwnIdEnrollmentViewModelInt

    private val titleTextView: TextView by lazy(LazyThreadSafetyMode.NONE) { requireView().findViewById(R.id.com_ownid_sdk_internal_ui_enrollment_title) }
    private val descriptionTextView: TextView by lazy(LazyThreadSafetyMode.NONE) { requireView().findViewById(R.id.com_ownid_sdk_internal_ui_enrollment_description) }
    private val continueButton: Button by lazy(LazyThreadSafetyMode.NONE) { requireView().findViewById(R.id.com_ownid_sdk_internal_ui_enrollment_continue) }
    private val skipButton: Button by lazy(LazyThreadSafetyMode.NONE) { requireView().findViewById(R.id.com_ownid_sdk_internal_ui_enrollment_skip) }
    private val progress: CircularProgressIndicator by lazy(LazyThreadSafetyMode.NONE) { requireView().findViewById(R.id.com_ownid_sdk_internal_ui_enrollment_progress) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(DialogFragment.STYLE_NORMAL, R.style.OwnId_UI_BottomSheetDialog)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = super.onCreateDialog(savedInstanceState).apply {
        (this as BottomSheetDialog).behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        LayoutInflater.from(ContextThemeWrapper(requireContext(), R.style.OwnIdTheme_InternalUI))
            .inflate(contentLayoutId, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        OwnIdInternalLogger.logD(this, "onViewCreated", "Invoked")

        runCatching {
            viewModel = ViewModelProvider(requireActivity()).get(OwnIdEnrollmentViewModelInt::class.java)
            ownIdCore = requireNotNull(viewModel.enrollmentParams.ownIdCore)

            viewModel.enrolmentState
                .filterNotNull()
                .onEach { state ->
                    when (state) {
                        OwnIdEnrollmentViewModelInt.State.Busy -> {
                            continueButton.text = ""
                            progress.visibility = View.VISIBLE
                        }

                        else -> {
                            if (progress.visibility == View.VISIBLE) {
                                progress.visibility = View.GONE
                            }
                            dismissAllowingStateLoss()
                        }
                    }
                }
                .launchIn(viewLifecycleOwner.lifecycleScope)

            continueButton.setOnClickListener { continueButton.isEnabled = false; viewModel.onContinueClick() }
            skipButton.setOnClickListener { continueButton.isEnabled = false; viewModel.onSkipClick() }
        }.onFailure {
            OwnIdInternalLogger.logE(this, "onViewCreated", it.message, it)
            parentFragmentManager.setFragmentResult(
                OwnIdActivity.KEY_RESULT_UI_ERROR, Bundle(1).apply { putSerializable(OwnIdActivity.KEY_RESULT_UI_ERROR, it) }
            )
            dismissAllowingStateLoss()
            return
        }

        ownIdCore.localeService.registerLocaleUpdateListener(this)
        setStrings()
    }

    override fun onDestroyView() {
        OwnIdInternalLogger.logD(this, "onDestroyView", "Invoked")
        ownIdCore.localeService.unregisterLocaleUpdateListener(this)
        super.onDestroyView()
    }

    override fun onCancel(dialog: DialogInterface) {
        if (::viewModel.isInitialized) viewModel.onCancel()
        super.onCancel(dialog)
    }

    override fun onLocaleUpdated() {
        OwnIdInternalLogger.logD(this, "onLocaleUpdated", "Invoked")
        setStrings()
    }

    private fun setStrings() {
        titleTextView.text = getString(LocaleKeys.getTitleKey())
        descriptionTextView.text = getString(LocaleKeys.getDescriptionKey())

        continueButton.text = getString(LocaleKeys.getContinueKey())
        continueButton.minEms = continueButton.text.length

        skipButton.text = getString(LocaleKeys.getSkipKey())
    }

    private fun getString(ownIdLocaleKey: OwnIdLocaleKey): String =
        ownIdCore.localeService.getString(requireContext(), ownIdLocaleKey)

    private object LocaleKeys {
        private val prefix = arrayOf("enrollCredential")

        @JvmStatic
        internal fun getTitleKey() = OwnIdLocaleKey(*prefix, "title")
            .withFallback(R.string.com_ownid_sdk_internal_ui_enrollment_title)

        @JvmStatic
        internal fun getDescriptionKey() = OwnIdLocaleKey(*prefix, "description")
            .withFallback(R.string.com_ownid_sdk_internal_ui_enrollment_description)

        @JvmStatic
        internal fun getContinueKey() = OwnIdLocaleKey(*prefix, "cta")
            .withFallback(R.string.com_ownid_sdk_internal_ui_enrollment_cta)

        @JvmStatic
        internal fun getSkipKey() = OwnIdLocaleKey(*prefix, "skip")
            .withFallback(R.string.com_ownid_sdk_internal_ui_enrollment_skip)
    }
}