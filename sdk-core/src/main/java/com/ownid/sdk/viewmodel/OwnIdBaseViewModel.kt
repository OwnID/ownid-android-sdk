package com.ownid.sdk.viewmodel

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.CallSuper
import androidx.annotation.MainThread
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnIdCore
import com.ownid.sdk.event.OwnIdEvent
import com.ownid.sdk.event.OwnIdLoginEvent
import com.ownid.sdk.event.OwnIdRegisterEvent
import com.ownid.sdk.exception.NoResultLauncherSet
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.internal.OwnIdResponse
import com.ownid.sdk.internal.events.MetricItem
import com.ownid.sdk.logD
import com.ownid.sdk.logV

/**
 * Base ViewModel class for any OwnID ViewModel.
 */
@InternalOwnIdAPI
public abstract class OwnIdBaseViewModel<E : OwnIdEvent>(protected val ownIdCore: OwnIdCore) : ViewModel() {

    private val emailRegex =
        """(?:[a-z0-9!#${'$'}%&'*+/=?^_`{|}~-]+(?:\.[a-z0-9!#${'$'}%&'*+/=?^_`{|}~-]+)*|"(?:[\x01-\x08\x0b\x0c\x0e-\x1f\x21\x23-\x5b\x5d-\x7f]|\\[\x01-\x09\x0b\x0c\x0e-\x7f])*")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?|\[(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?|[a-z0-9-]*[a-z0-9]:(?:[\x01-\x08\x0b\x0c\x0e-\x1f\x21-\x5a\x53-\x7f]|\\[\x01-\x09\x0b\x0c\x0e-\x7f])+)])""".toRegex(RegexOption.IGNORE_CASE)

    protected abstract val resultRegistryKey: String

    @MainThread
    protected abstract fun onActivityResult(result: Result<OwnIdResponse>)

    @MainThread
    internal abstract fun launch(context: Context, languageTags: String, email: String)

    private var resultLauncher: ActivityResultLauncher<Intent>? = null
    protected var isBusy: Boolean = false
    protected val _ownIdResponse: MutableLiveData<OwnIdResponse> = MutableLiveData()
    protected val _events: MutableLiveData<out E> = MutableLiveData()

    internal val ownIdResponse: LiveData<OwnIdResponse> = _ownIdResponse

    @MainThread
    internal fun createResultLauncher(resultRegistry: ActivityResultRegistry, owner: LifecycleOwner) {
        resultLauncher = resultRegistry
            .register(resultRegistryKey, owner, ActivityResultContracts.StartActivityForResult()) { result ->
                onActivityResult(result.toOwnIdResponse())
            }
    }

    @Throws(NoResultLauncherSet::class)
    internal fun launchIntent(intent: Intent) {
        resultLauncher?.launch(intent) ?: throw NoResultLauncherSet()
    }

    @MainThread
    protected fun setBusy(busyEvent: E) {
        when (busyEvent) {
            is OwnIdRegisterEvent.Busy -> isBusy = busyEvent.isBusy
            is OwnIdLoginEvent.Busy -> isBusy = busyEvent.isBusy
        }
        logV("setBusy: $isBusy", ownIdCore)
        _events.value = busyEvent
    }

    @MainThread
    @CallSuper
    internal open fun undo() {
        logD("undo", ownIdCore)
        _ownIdResponse.value = null
    }

    @CallSuper
    override fun onCleared() {
        super.onCleared()
        resultLauncher = null
    }

    private fun ActivityResult.toOwnIdResponse(): Result<OwnIdResponse> = runCatching {
        when (resultCode) {
            Activity.RESULT_OK -> OwnIdResponse.unwrapFromIntentOrThrow(data)
            Activity.RESULT_CANCELED -> throw OwnIdException.unwrapFromIntentOrThrow(data)
            else -> throw OwnIdException("Unknown resultCode: $resultCode")
        }
    }

    internal fun sendMetric(
        category: MetricItem.Category, type: MetricItem.EventType, action: String?, context: String, errorMessage: String? = null
    ) {
        ownIdCore.metricService.sendMetric(category, type, action, context, errorMessage)
    }

    internal fun sendTrackMetric(
        category: MetricItem.Category, action: String?, context: String, errorMessage: String? = null
    ) {
        sendMetric(category, MetricItem.EventType.Track, action, context, errorMessage)
    }

    protected fun sendErrorMetric(
        category: MetricItem.Category, action: String?, context: String, errorMessage: String? = null
    ) {
        sendMetric(category, MetricItem.EventType.Error, action ?: "Error: $errorMessage", context, errorMessage)
    }

    protected fun String.isValidEmail(): Boolean = matches(emailRegex)
}