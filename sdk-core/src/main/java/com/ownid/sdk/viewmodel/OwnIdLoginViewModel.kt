package com.ownid.sdk.viewmodel

import android.view.View
import androidx.annotation.MainThread
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnIdInstance
import com.ownid.sdk.OwnIdIntegration
import com.ownid.sdk.OwnIdLoginType
import com.ownid.sdk.OwnIdPayload
import com.ownid.sdk.OwnIdResponse
import com.ownid.sdk.event.LoginData
import com.ownid.sdk.event.OwnIdLoginEvent
import com.ownid.sdk.event.OwnIdLoginFlow
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.exception.OwnIdFlowCanceled
import com.ownid.sdk.exception.OwnIdUserError
import com.ownid.sdk.internal.component.OwnIdInternalLogger
import com.ownid.sdk.internal.component.events.Metadata
import com.ownid.sdk.internal.component.events.Metric
import com.ownid.sdk.internal.feature.flow.OwnIdFlowType
import com.ownid.sdk.view.OwnIdAuthButton
import com.ownid.sdk.view.OwnIdButton

/**
 * ViewModel class for OwnID Login flow.
 */
@OptIn(InternalOwnIdAPI::class)
public class OwnIdLoginViewModel(ownIdInstance: OwnIdInstance) : OwnIdFlowViewModel(ownIdInstance) {

    @Suppress("UNCHECKED_CAST")
    @InternalOwnIdAPI
    public class Factory(private val ownIdInstance: OwnIdInstance) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = OwnIdLoginViewModel(ownIdInstance) as T
    }

    private val _integrationEvents: MutableLiveData<OwnIdLoginEvent> = MutableLiveData()
    private val _flowEvents: MutableLiveData<OwnIdLoginFlow> = MutableLiveData()

    /**
     * Exposes [OwnIdLoginEvent] events as [LiveData].
     *
     * Listen to these events when [OwnIdInstance] has [OwnIdIntegration] component set.
     */
    @Deprecated(message = "Deprecated since 3.1.0", replaceWith = ReplaceWith("integrationEvents"))
    public val events: LiveData<out OwnIdLoginEvent> = _integrationEvents

    /**
     * Exposes [OwnIdLoginEvent] events as [LiveData].
     *
     * Listen to these events when [OwnIdInstance] has [OwnIdIntegration] component set.
     */
    public val integrationEvents: LiveData<out OwnIdLoginEvent> = _integrationEvents

    /**
     * Exposes [OwnIdLoginFlow] events as [LiveData].
     *
     * Listen to these events when no [OwnIdIntegration] component set in [OwnIdInstance].
     */
    public val flowEvents: LiveData<out OwnIdLoginFlow> = _flowEvents

    protected override val flowType: OwnIdFlowType = OwnIdFlowType.LOGIN
    protected override val resultRegistryKey: String = "com.ownid.sdk.result.registry.LOGIN"

    override fun publishBusy(isBusy: Boolean) {
        OwnIdInternalLogger.logD(this, "publishBusy", "$isBusy")
        _busyFlow.value = isBusy

        if (hasIntegration) {
            _integrationEvents.value = OwnIdLoginEvent.Busy(isBusy)
        } else {
            _flowEvents.value = OwnIdLoginFlow.Busy(isBusy)
        }
    }

    override fun publishError(error: OwnIdException) {
        if (hasIntegration) {
            _integrationEvents.value = OwnIdLoginEvent.Error(error)
        } else {
            _flowEvents.value = OwnIdLoginFlow.Error(error)
        }
    }

    override fun publishFlowResponse(loginId: String, payload: OwnIdPayload, authType: String) {
        _flowEvents.value = OwnIdLoginFlow.Response(loginId, payload, authType)
    }

    override fun publishLoginByIntegration(authType: String, loginData: LoginData?) {
        _integrationEvents.value = OwnIdLoginEvent.LoggedIn(authType, loginData)
    }

    /**
     * Configures [view] to start OwnID Login flow.
     *
     * Keeps a strong reference to the [loginIdProvider] as long as the given [owner] is not destroyed. When it is destroyed, the references are being removed.
     *
     * If the given owner is already in DESTROYED state, method ignores the call.
     *
     * @param view              An instance of [OwnIdButton], [OwnIdAuthButton], or any [View](https://developer.android.com/reference/android/view/View).
     * @param owner             (optional) A [LifecycleOwner] for [view].
     * @param loginIdProvider   (optional) A function that returns user's Login ID as a [String]. If set, then for [OwnIdButton], [OwnIdAuthButton] it will be used as loginIdProvider, for other view types it will be used to get user's Login ID.
     * @param loginType         (optional) A type of login [OwnIdLoginType].
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
        loginType: OwnIdLoginType = OwnIdLoginType.Standard
    ) {
        requireNotNull(owner) { "LifecycleOwner is null. Please provide LifecycleOwner" }

        if (owner.lifecycle.currentState == Lifecycle.State.DESTROYED) return

        attachToViewInternal(view, owner, loginIdProvider, loginType)
    }

    @MainThread
    protected override fun endFlow(result: Result<OwnIdResponse>) {
        result.mapCatching { response ->
            OwnIdInternalLogger.logD(this, "endFlow", "Get new OwnIdResponse")

            if (response.payload.type != OwnIdPayload.Type.Login)
                throw OwnIdException("OwnIdPayload type unexpected: ${response.payload.type}")

            ownIdResponseUndo = null
            _ownIdResponseFlow.value = response

            sendMetric(flowType, Metric.EventType.Track, "User is Logged in", Metadata(authType = response.flowInfo.authType))

            if (hasIntegration) {
                doLoginByIntegration(response)
            } else {
                doRegisterOrLoginWithoutIntegration(response)
            }
        }.onFailure { cause ->
            ownIdResponseUndo = null
            _ownIdResponseFlow.value = null

            when (cause) {
                is OwnIdFlowCanceled -> sendMetric(flowType, Metric.EventType.Track, cause)
                is OwnIdUserError -> sendMetric(flowType, Metric.EventType.Error, cause)
                else -> OwnIdInternalLogger.logE(this, "endFlow.onFailure", cause.message, cause)
            }

            publishBusy(false)
            publishError(OwnIdUserError.map(ownIdCore.localeService, "endFlow.onFailure: ${cause.message}", cause))

            OwnIdInternalLogger.setFlowContext(null)
            ownIdCore.eventsService.setFlowContext(null)
            ownIdCore.eventsService.setFlowLoginId(null)
        }
    }

    @MainThread
    protected override fun undo(metadata: Metadata) {
        OwnIdInternalLogger.logD(this, "undo", "Undo clicked - Ignored")
    }
}