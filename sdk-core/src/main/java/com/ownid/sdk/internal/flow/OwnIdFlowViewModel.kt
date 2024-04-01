package com.ownid.sdk.internal.flow

import androidx.annotation.CallSuper
import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.internal.OwnIdInternalLogger
import com.ownid.sdk.internal.flow.steps.InitStep

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class OwnIdFlowViewModel : ViewModel() {

    private val _ownIdFlowStep: MutableLiveData<AbstractStep> = MutableLiveData()
    internal val ownIdFlowStep: LiveData<AbstractStep> = _ownIdFlowStep

    init {
        OwnIdInternalLogger.logD(this, "init", "Invoked")
    }

    @MainThread
    internal fun startFlow(ownIdFlowData: OwnIdFlowData, startFrom: String?) {
        ownIdFlowData.ownIdCore.eventsService.setFlowLoginId(ownIdFlowData.loginId.value)
        onNextStep(InitStep.create(ownIdFlowData, ::onNextStep))
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
        ownIdFlowStep.value?.ownIdFlowData?.canceller?.cancel()
    }
}