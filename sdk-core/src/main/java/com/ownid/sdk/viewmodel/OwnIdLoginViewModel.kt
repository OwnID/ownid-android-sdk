package com.ownid.sdk.viewmodel

import android.view.View
import androidx.annotation.MainThread
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnIdInstance
import com.ownid.sdk.OwnIdIntegration
import com.ownid.sdk.OwnIdLoginType
import com.ownid.sdk.OwnIdPayload
import com.ownid.sdk.OwnIdResponse
import com.ownid.sdk.event.OwnIdLoginEvent
import com.ownid.sdk.event.OwnIdLoginFlow
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.exception.OwnIdFlowCanceled
import com.ownid.sdk.exception.OwnIdUserError
import com.ownid.sdk.internal.OwnIdInternalLogger
import com.ownid.sdk.internal.events.Metadata
import com.ownid.sdk.internal.events.Metric
import com.ownid.sdk.internal.flow.OwnIdFlowType
import com.ownid.sdk.view.OwnIdAuthButton
import com.ownid.sdk.view.OwnIdButton

/**
 * ViewModel class for OwnID Login flow.
 */
@OptIn(InternalOwnIdAPI::class)
public class OwnIdLoginViewModel(ownIdInstance: OwnIdInstance) :
    OwnIdBaseViewModel<OwnIdLoginEvent, OwnIdLoginFlow>(ownIdInstance) {

    @PublishedApi
    @Suppress("UNCHECKED_CAST")
    @InternalOwnIdAPI
    internal class Factory(private val ownIdInstance: OwnIdInstance) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = OwnIdLoginViewModel(ownIdInstance) as T
    }

    /**
     * Exposes [OwnIdLoginEvent] events as [LiveData].
     *
     * Listen to these events when [OwnIdInstance] has [OwnIdIntegration] component set.
     */
    @Deprecated(message = "Deprecated since 3.1.0", replaceWith = ReplaceWith("integrationEvents"))
    public val events: LiveData<out OwnIdLoginEvent> = ownIdIntegrationEvents

    /**
     * Exposes [OwnIdLoginEvent] events as [LiveData].
     *
     * Listen to these events when [OwnIdInstance] has [OwnIdIntegration] component set.
     */
    public val integrationEvents: LiveData<out OwnIdLoginEvent> = ownIdIntegrationEvents

    /**
     * Exposes [OwnIdLoginFlow] events as [LiveData].
     *
     * Listen to these events when no [OwnIdIntegration] component set in [OwnIdInstance].
     */
    public val flowEvents: LiveData<out OwnIdLoginFlow> = ownIdFlowEvents

    protected override val flowType: OwnIdFlowType = OwnIdFlowType.LOGIN
    protected override val resultRegistryKey: String = "com.ownid.sdk.result.registry.LOGIN"

    /**
     * Configures [view] to start OwnID Login flow.
     *
     * Listens to OwnID updates and notifies when [OwnIdResponse] is available via [onOwnIdResponse].
     *
     * Keeps a strong reference to the [loginIdProvider] and [onOwnIdResponse] as long as the given LifecycleOwner is not destroyed. When it is destroyed, the references are being removed.
     *
     * If the given owner is already in DESTROYED state, method ignores the call.
     *
     * @param view              An instance of [OwnIdButton], [OwnIdAuthButton], or any [View](https://developer.android.com/reference/android/view/View).
     * @param owner             (optional) A [LifecycleOwner] for [view].
     * @param loginIdProvider   (optional) A function that returns user's Login ID as a [String]. If set, then for [OwnIdButton], [OwnIdAuthButton] it will be used as loginIdProvider, for other view types it will be used to get user's Login ID.
     * @param loginType         (optional) A type of login [OwnIdLoginType].
     * @param onOwnIdResponse   (optional) A function that will be called when OwnID has [OwnIdResponse]. Use it to update UI.
     *
     * @throws IllegalArgumentException if [owner] is null.
     */
    @MainThread
    @JvmOverloads
    @Throws(IllegalArgumentException::class)
    public fun attachToView(
        view: View,
        owner: LifecycleOwner? = view.findViewTreeLifecycleOwner(),
        loginIdProvider: (() -> String)? = null,
        loginType: OwnIdLoginType = OwnIdLoginType.Standard,
        onOwnIdResponse: (Boolean) -> Unit = {}
    ) {
        requireNotNull(owner) { "LifecycleOwner is null. Please provide LifecycleOwner" }

        if (owner.lifecycle.currentState == Lifecycle.State.DESTROYED) return

        attachToViewInternal(view, owner, loginIdProvider, loginType, onOwnIdResponse)
    }

    @MainThread
    protected override fun endFlow(result: Result<OwnIdResponse>) {
        result.mapCatching { response ->
            OwnIdInternalLogger.logD(this, "endFlow", "Get new OwnIdResponse")

            if (response.payload.type != OwnIdPayload.Type.Login)
                throw OwnIdException("OwnIdPayload type unexpected: ${response.payload.type}")

            ownIdResponseLiveData.value = response

            if (hasIntegration) {
                doLoginByIntegration(ownIdInstance.ownIdIntegration!!, response)
            } else {
                doLoginWithoutIntegration(response)
            }
        }.onFailure { cause ->
            ownIdResponseLiveData.value = null

            if (cause is OwnIdFlowCanceled) {
                OwnIdInternalLogger.logW(this, "endFlow.onFailure", cause.message, cause)
            } else {
                sendMetric(flowType, Metric.EventType.Error, "Sending error to app: ${cause.message}", errorMessage = cause.message)
                OwnIdInternalLogger.logE(this, "endFlow.onFailure", cause.message, cause)
            }

            val error = OwnIdUserError.map(ownIdCoreImpl.localeService, "endFlow.onFailure: ${cause.message}", cause)
            if (hasIntegration) {
                ownIdIntegrationEvents.value = OwnIdLoginEvent.Error(error)
            } else {
                ownIdFlowEvents.value = OwnIdLoginFlow.Error(error)
            }

            isBusy = false
            OwnIdInternalLogger.setFlowContext(null)
            ownIdCoreImpl.eventsService.setFlowContext(null)
            ownIdCoreImpl.eventsService.setFlowLoginId(null)
        }
    }

    @MainThread
    protected override fun undo(metadata: Metadata) {
        OwnIdInternalLogger.logD(this, "undo", "Undo clicked - Ignored")
    }
}