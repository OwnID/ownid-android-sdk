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
import com.ownid.sdk.OwnIdPayload
import com.ownid.sdk.OwnIdResponse
import com.ownid.sdk.RegistrationParameters
import com.ownid.sdk.event.OwnIdRegisterEvent
import com.ownid.sdk.event.OwnIdRegisterFlow
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
 * ViewModel class for OwnID Registration flow.
 */
@OptIn(InternalOwnIdAPI::class)
public class OwnIdRegisterViewModel(ownIdInstance: OwnIdInstance) :
    OwnIdBaseViewModel<OwnIdRegisterEvent, OwnIdRegisterFlow>(ownIdInstance) {

    @PublishedApi
    @Suppress("UNCHECKED_CAST")
    @InternalOwnIdAPI
    internal class Factory(private val ownIdInstance: OwnIdInstance) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = OwnIdRegisterViewModel(ownIdInstance) as T
    }

    /**
     * Exposes [OwnIdRegisterEvent] events as [LiveData].
     *
     * Listen to these events when [OwnIdInstance] has [OwnIdIntegration] component set.
     */
    @Deprecated(message = "Deprecated since 3.1.0", replaceWith = ReplaceWith("integrationEvents"))
    public val events: LiveData<out OwnIdRegisterEvent> = ownIdIntegrationEvents

    /**
     * Exposes [OwnIdRegisterEvent] events as [LiveData].
     *
     * Listen to these events when [OwnIdInstance] has [OwnIdIntegration] component set.
     */
    public val integrationEvents: LiveData<out OwnIdRegisterEvent> = ownIdIntegrationEvents

    /**
     * Exposes [OwnIdRegisterFlow] events as [LiveData].
     *
     * Listen to these events when no [OwnIdIntegration] component set in [OwnIdInstance].
     */
    public val flowEvents: LiveData<out OwnIdRegisterFlow> = ownIdFlowEvents

    protected override val flowType: OwnIdFlowType = OwnIdFlowType.REGISTER
    protected override val resultRegistryKey: String = "com.ownid.sdk.result.registry.REGISTER"

    /**
     * Configures [view] to start OwnID Registration flow.
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
        onOwnIdResponse: (Boolean) -> Unit = {}
    ) {
        requireNotNull(owner) { "LifecycleOwner is null. Please provide LifecycleOwner" }

        if (owner.lifecycle.currentState == Lifecycle.State.DESTROYED) return

        attachToViewInternal(view, owner, loginIdProvider, null, onOwnIdResponse)
    }

    @MainThread
    protected override fun endFlow(result: Result<OwnIdResponse>) {
        result.mapCatching { response ->
            OwnIdInternalLogger.logD(this, "endFlow", "Get new OwnIdResponse")

            ownIdResponseLiveData.value = response

            if (hasIntegration) {
                when (response.payload.type) {
                    OwnIdPayload.Type.Login -> doLoginByIntegration(ownIdInstance.ownIdIntegration!!, response)
                    OwnIdPayload.Type.Registration -> {
                        ownIdIntegrationEvents.value = OwnIdRegisterEvent.ReadyToRegister(response.loginId, response.flowInfo.authType)
                        isBusy = false
                    }
                }
            } else {
                when (response.payload.type) {
                    OwnIdPayload.Type.Login -> doLoginWithoutIntegration(response)
                    OwnIdPayload.Type.Registration -> doRegisterWithoutIntegration(response)
                }
            }
        }.onFailure { cause ->
            ownIdResponseLiveData.value = null

            if (cause is OwnIdFlowCanceled) {
                OwnIdInternalLogger.logW(this, "endFlow.onFailure", cause.message, cause)
            } else {
                sendMetric(flowType, Metric.EventType.Error, "Sending error to app", errorMessage = cause.message)
                OwnIdInternalLogger.logE(this, "endFlow.onFailure", cause.message, cause)
            }

            val error = OwnIdUserError.map(ownIdCoreImpl.localeService, "endFlow.onFailure: ${cause.message}", cause)
            if (hasIntegration) {
                ownIdIntegrationEvents.value = OwnIdRegisterEvent.Error(error)
            } else {
                ownIdFlowEvents.value = OwnIdRegisterFlow.Error(error)
            }

            isBusy = false
            OwnIdInternalLogger.setFlowContext(null)
            ownIdCoreImpl.eventsService.setFlowContext(null)
            ownIdCoreImpl.eventsService.setFlowLoginId(null)
        }
    }

    @MainThread
    protected override fun undo(metadata: Metadata) {
        OwnIdInternalLogger.logD(this, "undo", "Undo clicked")
        sendMetric(flowType, Metric.EventType.Click, "Clicked Undo", metadata)
        ownIdResponseUndo = ownIdResponseLiveData.value
        ownIdResponseLiveData.value = null

        if (hasIntegration) {
            ownIdIntegrationEvents.value = OwnIdRegisterEvent.Undo
        } else {
            ownIdFlowEvents.value = OwnIdRegisterFlow.Undo
        }
    }

    /**
     * Complete OwnID Registration process and register new user in identity platform. Exact registration action depend on integration.
     *
     * User password will be generated automatically if required.
     *
     * @param loginIdString     User Login ID for new account.
     * @param params            [RegistrationParameters] (optional) Additional parameters for registration. Depend on integration.
     */
    @MainThread
    @JvmOverloads
    public fun register(loginIdString: String, params: RegistrationParameters? = null) {
        val ownIdResponseValue = ownIdResponseLiveData.value
        val ownIdIntegration = ownIdInstance.ownIdIntegration

        when {
            ownIdResponseValue == null -> OwnIdException("No OwnIdResponse available. Register flow must be run first")
            ownIdResponseValue.payload.type != OwnIdPayload.Type.Registration -> OwnIdException("OwnIdPayload type unexpected: ${ownIdResponseValue.payload.type}")
            ownIdIntegration == null -> OwnIdException("No OwnIdIntegration available")
            else -> null
        }?.let { cause ->
            sendMetric(flowType, Metric.EventType.Error, "Sending error to app", errorMessage = cause.message)
            ownIdIntegrationEvents.value = OwnIdRegisterEvent.Error(cause)
            OwnIdInternalLogger.logE(this, "register", cause.message, cause)
            return
        }

        isBusy = true

        ownIdIntegration!!.register(loginIdString, params, ownIdResponseValue!!) {
            ownIdResponseLiveData.value = null
            onSuccess { loginData ->
                sendMetric(flowType, Metric.EventType.Track, "User is Registered", Metadata(authType = ownIdResponseValue.flowInfo.authType))
                ownIdIntegrationEvents.value = OwnIdRegisterEvent.LoggedIn(ownIdResponseValue.flowInfo.authType, loginData)
                ownIdCoreImpl.storageService.saveLoginId(loginIdString)
            }
            onFailure { cause ->
                sendMetric(flowType, Metric.EventType.Error, "Sending error to app", errorMessage = cause.message)
                OwnIdInternalLogger.logE(this@OwnIdRegisterViewModel, "register.onFailure", cause.message, cause)
                ownIdIntegrationEvents.value = OwnIdRegisterEvent.Error(OwnIdUserError.map(ownIdCoreImpl.localeService, "register: $cause", cause))
            }

            isBusy = false
            OwnIdInternalLogger.setFlowContext(null)
            ownIdCoreImpl.eventsService.setFlowContext(null)
            ownIdCoreImpl.eventsService.setFlowLoginId(null)
        }
    }


    @MainThread
    private fun doRegisterWithoutIntegration(response: OwnIdResponse) {
        OwnIdInternalLogger.logD(this, "doRegisterWithoutIntegration", "Get new OwnIdResponse")

        sendMetric(flowType, Metric.EventType.Track, "User is Registered", Metadata(authType = response.flowInfo.authType))
        ownIdCoreImpl.storageService.saveLoginId(response.loginId)

        ownIdFlowEvents.value = OwnIdRegisterFlow.Response(response.loginId, response.payload, response.flowInfo.authType)

        isBusy = false
        OwnIdInternalLogger.setFlowContext(null)
        ownIdCoreImpl.eventsService.setFlowContext(null)
        ownIdCoreImpl.eventsService.setFlowLoginId(null)
    }
}