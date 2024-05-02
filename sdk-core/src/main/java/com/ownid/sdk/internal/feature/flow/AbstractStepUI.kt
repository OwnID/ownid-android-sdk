package com.ownid.sdk.internal.feature.flow

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.annotation.LayoutRes
import androidx.annotation.RestrictTo
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.os.CancellationSignal
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.R
import com.ownid.sdk.internal.feature.OwnIdActivity
import com.ownid.sdk.internal.component.OwnIdInternalLogger
import com.ownid.sdk.internal.component.events.Metadata
import com.ownid.sdk.internal.component.events.Metric
import com.ownid.sdk.internal.component.locale.OwnIdLocaleKey
import com.ownid.sdk.internal.component.locale.OwnIdLocaleService
import kotlinx.coroutines.runBlocking
import kotlin.math.roundToInt

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal abstract class AbstractStepUI<S : AbstractStep>(@LayoutRes private val contentLayoutId: Int) :
    BottomSheetDialogFragment(), OwnIdLocaleService.LocaleUpdateListener {

    protected abstract fun setStrings()
    protected open val metadata: Metadata? = null

    @JvmField
    protected var isInitSuccessful: Boolean = false
    protected lateinit var currentStep: S

    private val onCancelListener = CancellationSignal.OnCancelListener {
        OwnIdInternalLogger.logD(this@AbstractStepUI, "onCancelListener", "Invoked")
        dismissAllowingStateLoss()
    }

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
            val viewModel = ViewModelProvider(requireActivity()).get(OwnIdFlowViewModelInt::class.java)

            @Suppress("UNCHECKED_CAST")
            currentStep = viewModel.ownIdFlowStep.value as S
            currentStep.ownIdFlowData.canceller.setOnCancelListener(onCancelListener)
            isInitSuccessful = true
        }.onFailure {
            OwnIdInternalLogger.logE(this, "onViewCreated", it.message, it)
            parentFragmentManager.setFragmentResult(
                OwnIdActivity.KEY_RESULT_UI_ERROR, Bundle(1).apply { putSerializable(OwnIdActivity.KEY_RESULT_UI_ERROR, it) }
            )
            dismissAllowingStateLoss()
            return
        }

        currentStep.ownIdFlowData.ownIdCore.localeService.registerLocaleUpdateListener(this)
        setStrings()

        val returningUser = runBlocking { currentStep.ownIdFlowData.ownIdCore.repository.getLoginId() }.isNotEmpty()

        currentStep.ownIdFlowData.ownIdCore.eventsService.sendMetric(
            currentStep.ownIdFlowData.flowType,
            Metric.EventType.Track,
            currentStep.getMetricViewedAction(),
            (metadata ?: Metadata()).copy(returningUser = returningUser),
            currentStep.getMetricSource()
        )
    }

    override fun onDestroyView() {
        OwnIdInternalLogger.logD(this, "onDestroyView", "Invoked")
        if (isInitSuccessful) {
            currentStep.ownIdFlowData.ownIdCore.localeService.unregisterLocaleUpdateListener(this)
            currentStep.ownIdFlowData.canceller.setOnCancelListener(null)
        }
        super.onDestroyView()
    }

    override fun onLocaleUpdated() {
        OwnIdInternalLogger.logD(this, "onLocaleUpdated", "Invoked")
        setStrings()
    }

    protected fun getString(ownIdLocaleKey: OwnIdLocaleKey): String =
        currentStep.ownIdFlowData.ownIdCore.localeService.getString(requireContext(), ownIdLocaleKey)

    protected fun EditText.focusAndShowKeyboard() {
        requestFocus()
        dialog?.window?.let { WindowCompat.getInsetsController(it, this).show(WindowInsetsCompat.Type.ime()) }
    }

    protected val Number.toPx: Int get() = (this.toFloat() * this@AbstractStepUI.resources.displayMetrics.density).roundToInt()
}