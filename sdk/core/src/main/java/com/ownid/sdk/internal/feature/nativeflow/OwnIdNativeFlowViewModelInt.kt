package com.ownid.sdk.internal.feature.nativeflow

import androidx.annotation.CallSuper
import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.internal.component.OwnIdInternalLogger
import com.ownid.sdk.internal.feature.nativeflow.steps.InitStep

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class OwnIdNativeFlowViewModelInt : ViewModel() {

    private val _ownIdFlowStep: MutableLiveData<AbstractStep> = MutableLiveData()
    internal val ownIdFlowStep: LiveData<AbstractStep> = _ownIdFlowStep

    init {
        OwnIdInternalLogger.logD(this, "init", "Invoked")
    }

    @MainThread
    internal fun startFlow(ownIdNativeFlowData: OwnIdNativeFlowData) {
        ownIdNativeFlowData.ownIdCore.eventsService.setFlowLoginId(ownIdNativeFlowData.loginId.value)
        onNextStep(InitStep.create(ownIdNativeFlowData, ::onNextStep))
    }

    @MainThread
    private fun onNextStep(step: AbstractStep) {
        OwnIdInternalLogger.logD(this, "onNextStep", "New step: $step")
        _ownIdFlowStep.value = step
    }

    @CallSuper
    override fun onCleared() {
        super.onCleared()
        OwnIdInternalLogger.logD(this, "onCleared", "Invoked")
        ownIdFlowStep.value?.ownIdNativeFlowData?.canceller?.cancel()
    }
}