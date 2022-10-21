package com.ownid.sdk.viewmodel

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.View
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.CallSuper
import androidx.annotation.MainThread
import androidx.lifecycle.LifecycleOwner
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

    internal class OwnIdResponseStatus(
        internal val newResponse: Boolean = false,
        internal val response: OwnIdResponse? = null
    ) {
        internal companion object {
            internal val EMPTY = OwnIdResponseStatus()
        }

        @JvmSynthetic
        internal fun hasResponse(): Boolean = response != null

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as OwnIdResponseStatus
            if (newResponse != other.newResponse) return false
            if (response != other.response) return false
            return true
        }

        override fun hashCode(): Int {
            var result = newResponse.hashCode()
            result = 31 * result + (response?.hashCode() ?: 0)
            return result
        }
    }

    protected abstract val resultRegistryKey: String

    @MainThread
    @JvmSynthetic
    internal fun attachToViewInternal(
        view: View,
        owner: LifecycleOwner,
        emailProducer: () -> String,
        languageProducer: (View) -> String,
        onOwnIdResponse: (Boolean) -> Unit
    ) {
        view.setOnClickListener {
            sendMetric(MetricItem.EventType.Click, "Clicked Skip Password")
            launch(view.context, languageProducer(view), emailProducer())
        }

        ownIdResponseStatus.observe(owner) { responseStatus ->
            onOwnIdResponse.invoke(responseStatus.newResponse)

            if (responseStatus.newResponse) {
                view.setOnClickListener {
                    sendMetric(MetricItem.EventType.Click, "Clicked Skip Password Undo", responseStatus.response?.context)
                    undo()
                }
            } else {
                view.setOnClickListener {
                    sendMetric(MetricItem.EventType.Click, "Clicked Skip Password")
                    launch(view.context, languageProducer(view), emailProducer())
                }
            }
        }
    }

    @MainThread
    @JvmSynthetic
    protected abstract fun onActivityResult(result: Result<OwnIdResponse>)

    @MainThread
    @JvmSynthetic
    internal abstract fun launch(context: Context, languageTags: String, email: String)

    private var resultLauncher: ActivityResultLauncher<Intent>? = null
    protected var isBusy: Boolean = false
    protected val _events: MutableLiveData<out E> = MutableLiveData()

    @get:JvmSynthetic
    internal val ownIdResponseStatus: MutableLiveData<OwnIdResponseStatus> = MutableLiveData(OwnIdResponseStatus.EMPTY)

    @MainThread
    @JvmSynthetic
    internal fun createResultLauncher(resultRegistry: ActivityResultRegistry, owner: LifecycleOwner) {
        resultLauncher = resultRegistry.register(resultRegistryKey, owner, ActivityResultContracts.StartActivityForResult()) { result ->
            onActivityResult(result.toOwnIdResponse())
        }
    }

    @JvmSynthetic
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

    @CallSuper
    @MainThread
    @JvmSynthetic
    internal open fun undo() {
        logD("undo", ownIdCore)
        ownIdResponseStatus.value = OwnIdResponseStatus(false, ownIdResponseStatus.value?.response)
    }

    @CallSuper
    override fun onCleared() {
        super.onCleared()
        resultLauncher = null
        ownIdResponseStatus.value = OwnIdResponseStatus.EMPTY
        _events.value = null
    }

    private fun ActivityResult.toOwnIdResponse(): Result<OwnIdResponse> = runCatching {
        when (resultCode) {
            Activity.RESULT_OK -> OwnIdResponse.unwrapFromIntentOrThrow(data)
            Activity.RESULT_CANCELED -> throw OwnIdException.unwrapFromIntentOrThrow(data)
            else -> throw OwnIdException("Unknown resultCode: $resultCode")
        }
    }

    @JvmSynthetic
    internal fun sendMetric(type: MetricItem.EventType, action: String?, context: String? = null, errorMessage: String? = null) {
        val category = when (this) {
            is OwnIdRegisterViewModel -> MetricItem.Category.Registration
            is OwnIdLoginViewModel -> MetricItem.Category.Login
            else -> throw IllegalArgumentException("Unexpected ViewModel class: ${this::class.java}")
        }
        sendMetric(category, type, action, context ?: "", errorMessage)
    }

    @JvmSynthetic
    internal fun sendMetric(
        category: MetricItem.Category, type: MetricItem.EventType, action: String?, context: String, errorMessage: String? = null
    ) {
        ownIdCore.metricService.sendMetric(category, type, action, context, errorMessage)
    }
}