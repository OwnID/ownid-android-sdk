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
import androidx.lifecycle.viewModelScope
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnIdInstance
import com.ownid.sdk.OwnIdIntegration
import com.ownid.sdk.OwnIdPayload
import com.ownid.sdk.OwnIdResponse
import com.ownid.sdk.RegistrationParameters
import com.ownid.sdk.event.LoginData
import com.ownid.sdk.event.OwnIdRegisterEvent
import com.ownid.sdk.event.OwnIdRegisterFlow
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.exception.OwnIdFlowCanceled
import com.ownid.sdk.exception.OwnIdUserError
import com.ownid.sdk.internal.component.OwnIdInternalLogger
import com.ownid.sdk.internal.component.events.Metadata
import com.ownid.sdk.internal.component.events.Metric
import com.ownid.sdk.internal.feature.nativeflow.OwnIdNativeFlowType
import com.ownid.sdk.view.OwnIdAuthButton
import com.ownid.sdk.view.OwnIdButton
import kotlinx.coroutines.launch

/**
 * ViewModel class for OwnID Registration flow.
 */
@OptIn(InternalOwnIdAPI::class)
public class OwnIdRegisterViewModel(ownIdInstance: OwnIdInstance) : OwnIdFlowViewModel(ownIdInstance) {

    @Suppress("UNCHECKED_CAST")
    @InternalOwnIdAPI
    public class Factory(private val ownIdInstance: OwnIdInstance) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = OwnIdRegisterViewModel(ownIdInstance) as T
    }

    private val _integrationEvents: MutableLiveData<OwnIdRegisterEvent> = MutableLiveData()
    private val _flowEvents: MutableLiveData<OwnIdRegisterFlow> = MutableLiveData()

    /**
     * Exposes [OwnIdRegisterEvent] events as [LiveData].
     *
     * Listen to these events when [OwnIdInstance] has [OwnIdIntegration] component set.
     */
    @Deprecated(message = "Deprecated since 3.1.0", replaceWith = ReplaceWith("integrationEvents"))
    public val events: LiveData<out OwnIdRegisterEvent> = _integrationEvents

    /**
     * Exposes [OwnIdRegisterEvent] events as [LiveData].
     *
     * Listen to these events when [OwnIdInstance] has [OwnIdIntegration] component set.
     */
    public val integrationEvents: LiveData<out OwnIdRegisterEvent> = _integrationEvents

    /**
     * Exposes [OwnIdRegisterFlow] events as [LiveData].
     *
     * Listen to these events when no [OwnIdIntegration] component set in [OwnIdInstance].
     */
    public val flowEvents: LiveData<out OwnIdRegisterFlow> = _flowEvents

    protected override val flowType: OwnIdNativeFlowType = OwnIdNativeFlowType.REGISTER
    protected override val resultRegistryKey: String = "com.ownid.sdk.result.registry.REGISTER"

    override fun publishBusy(isBusy: Boolean) {
        OwnIdInternalLogger.logD(this, "publishBusy", "$isBusy")
        _busyFlow.value = isBusy

        if (hasIntegration) {
            _integrationEvents.value = OwnIdRegisterEvent.Busy(isBusy)
        } else {
            _flowEvents.value = OwnIdRegisterFlow.Busy(isBusy)
        }
    }

    override fun publishError(error: OwnIdException) {
        if (hasIntegration) {
            _integrationEvents.value = OwnIdRegisterEvent.Error(error)
        } else {
            _flowEvents.value = OwnIdRegisterFlow.Error(error)
        }
    }

    override fun publishFlowResponse(loginId: String, payload: OwnIdPayload, authType: String) {
        _flowEvents.value = OwnIdRegisterFlow.Response(loginId, payload, authType)
    }

    override fun publishLoginByIntegration(authType: String, loginData: LoginData?) {
        _integrationEvents.value = OwnIdRegisterEvent.LoggedIn(authType, loginData)
    }

    private fun publishReadyToRegister(loginId: String, authType: String) {
        _integrationEvents.value = OwnIdRegisterEvent.ReadyToRegister(loginId, authType)
    }

    private fun publishUndo() {
        if (hasIntegration) {
            _integrationEvents.value = OwnIdRegisterEvent.Undo
        } else {
            _flowEvents.value = OwnIdRegisterFlow.Undo
        }
    }

    /**
     * Configures [view] to start OwnID Registration flow.
     *
     * Keeps a strong reference to the [loginIdProvider] as long as the given [owner] is not destroyed. When it is destroyed, the references are being removed.
     *
     * If the given owner is already in DESTROYED state, method ignores the call.
     *
     * @param view              An instance of [OwnIdButton], [OwnIdAuthButton], or any [View](https://developer.android.com/reference/android/view/View).
     * @param owner             (optional) A [LifecycleOwner] for [view].
     * @param loginIdProvider   (optional) A function that returns user's Login ID as a [String]. If set, then for [OwnIdButton], [OwnIdAuthButton] it will be used as loginIdProvider, for other view types it will be used to get user's Login ID.
     *
     * @throws IllegalArgumentException if [owner] is null.
     */
    @MainThread
    @JvmOverloads
    @Throws(IllegalArgumentException::class)
    public fun attachToView(
        view: View,
        owner: LifecycleOwner? = view.findViewTreeLifecycleOwner(),
        loginIdProvider: (() -> String)? = null
    ) {
        requireNotNull(owner) { "LifecycleOwner is null. Please provide LifecycleOwner" }

        if (owner.lifecycle.currentState == Lifecycle.State.DESTROYED) return

        attachToViewInternal(view, owner, loginIdProvider, null)
    }

    @MainThread
    protected override fun endFlow(result: Result<OwnIdResponse>) {
        result.mapCatching { response ->
            OwnIdInternalLogger.logD(this, "endFlow", "Get new OwnIdResponse")

            ownIdResponseUndo = null
            _ownIdResponseFlow.value = response

            val action = when (response.payload.type) {
                OwnIdPayload.Type.Registration -> "User is Registered"
                OwnIdPayload.Type.Login -> "User is Logged in"
            }
            sendMetric(flowType, Metric.EventType.Track, action, Metadata(authType = response.flowInfo.authType))

            if (hasIntegration) {
                when (response.payload.type) {
                    OwnIdPayload.Type.Login -> {
                        doLoginByIntegration(response)
                    }

                    OwnIdPayload.Type.Registration -> {
                        publishBusy(false)
                        publishReadyToRegister(response.loginId, response.flowInfo.authType)
                    }
                }
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
        OwnIdInternalLogger.logD(this, "undo", "Undo clicked")
        sendMetric(flowType, Metric.EventType.Click, "Clicked Undo", metadata)

        ownIdResponseUndo = _ownIdResponseFlow.value
        _ownIdResponseFlow.value = null

        publishUndo()
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
        runCatching {
            val response =
                _ownIdResponseFlow.value ?: throw OwnIdException("No OwnIdResponse available. Register flow must be run first")

            if (response.payload.type != OwnIdPayload.Type.Registration)
                throw OwnIdException("OwnIdPayload type unexpected: ${response.payload.type}")

            publishBusy(true)
            doRegisterByIntegration(loginIdString, params, response)
        }.onFailure { cause ->
            OwnIdInternalLogger.logE(this, "register", cause.message, cause)

            publishBusy(false)
            publishError(OwnIdUserError.map(ownIdCore.localeService, "register: ${cause.message}", cause))
        }
    }

    @Throws
    @MainThread
    private fun doRegisterByIntegration(loginIdString: String, params: RegistrationParameters? = null, response: OwnIdResponse) {
        requireNotNull(ownIdInstance.ownIdIntegration) {
            "${this::class.java.simpleName}: No OwnIdIntegration available"
        }.register(loginIdString, params, response) {
            ownIdResponseUndo = null
            _ownIdResponseFlow.value = null

            publishBusy(false)

            onSuccess { loginData ->
                viewModelScope.launch { saveLoginId(response.loginId, response.flowInfo.authType) }
                publishLoginByIntegration(response.flowInfo.authType, loginData)
            }
            onFailure { cause ->
                OwnIdInternalLogger.logW(this@OwnIdRegisterViewModel, "doRegisterByIntegration", "Registration: ${cause.message}", cause)
                publishError(OwnIdUserError.map(ownIdCore.localeService, "doRegisterByIntegration.onFailure: ${cause.message}", cause))
            }

            OwnIdInternalLogger.setFlowContext(null)
            ownIdCore.eventsService.setFlowContext(null)
            ownIdCore.eventsService.setFlowLoginId(null)
        }
    }
}