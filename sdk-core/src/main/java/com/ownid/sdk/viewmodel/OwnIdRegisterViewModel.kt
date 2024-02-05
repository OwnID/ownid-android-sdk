package com.ownid.sdk.viewmodel

import android.view.View
import androidx.annotation.MainThread
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnIdInstance
import com.ownid.sdk.OwnIdPayload
import com.ownid.sdk.OwnIdResponse
import com.ownid.sdk.RegistrationParameters
import com.ownid.sdk.event.OwnIdRegisterEvent
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
public class OwnIdRegisterViewModel(ownIdInstance: OwnIdInstance) : OwnIdBaseViewModel<OwnIdRegisterEvent>(ownIdInstance) {

    @PublishedApi
    @Suppress("UNCHECKED_CAST")
    @InternalOwnIdAPI
    internal class Factory(private val ownIdInstance: OwnIdInstance) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = OwnIdRegisterViewModel(ownIdInstance) as T
    }

    /**
     * Expose [OwnIdRegisterEvent] as [LiveData]
     */
    public val events: LiveData<out OwnIdRegisterEvent> = ownIdEvents

    protected override val flowType: OwnIdFlowType = OwnIdFlowType.REGISTER
    protected override val resultRegistryKey: String = "com.ownid.sdk.result.registry.REGISTER"

    /**
     * Configures [view] to start OwnID flow.
     *
     * Listens to OwnID updates and notifies when [OwnIdResponse] is available via [onOwnIdResponse].
     *
     * @param view              An instance of [OwnIdButton], [OwnIdAuthButton], or any [View](https://developer.android.com/reference/android/view/View).
     * @param owner             (optional) A [LifecycleOwner] for [view].
     * @param loginIdProvider   (optional) A function that returns user's Login ID as a [String]. If set, then for [OwnIdButton], [OwnIdAuthButton] it will be used as loginIdProvider, for other view types it will be used to get user's Login ID.
     * @param onOwnIdResponse   (optional) A function that will be called when OwnID has [OwnIdResponse]. Use it to change [view] UI.
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
    ): Unit = attachToViewInternal(view, owner, loginIdProvider, null, onOwnIdResponse)

    @MainThread
    protected override fun endFlow(result: Result<OwnIdResponse>) {
        result.onSuccess { response ->
            OwnIdInternalLogger.logD(this, "endFlow", "Get new OwnIdResponse")

            ownIdResponse.value = response
            if (response.payload.type == OwnIdPayload.Type.Login) {
                doLoginByIntegration(response)
            } else {
                isBusy = false
                ownIdEvents.value = OwnIdRegisterEvent.ReadyToRegister(response.loginId, response.flowInfo.authType)
            }
        }.onFailure { cause ->
            ownIdResponse.value = null
            isBusy = false
            if (cause is OwnIdFlowCanceled) {
                OwnIdInternalLogger.logW(this, "endFlow.onFailure", cause.message, cause)
            } else {
                sendMetric(flowType, Metric.EventType.Error, "Sending error to app", errorMessage = cause.message)
                OwnIdInternalLogger.logE(this, "endFlow.onFailure", cause.message, cause)
            }
            ownIdEvents.value = OwnIdRegisterEvent.Error(OwnIdUserError.map(ownIdCoreImpl.localeService, "endFlow.onFailure: ${cause.message}", cause))
            OwnIdInternalLogger.setFlowContext(null)
            ownIdCoreImpl.eventsService.setFlowContext(null)
            ownIdCoreImpl.eventsService.setFlowLoginId(null)
        }
    }

    @MainThread
    protected override fun undo(metadata: Metadata) {
        OwnIdInternalLogger.logD(this, "undo", "Undo clicked")
        sendMetric(flowType, Metric.EventType.Click, "Clicked Undo", metadata)
        ownIdResponseUndo = ownIdResponse.value
        ownIdResponse.value = null
        ownIdEvents.value = OwnIdRegisterEvent.Undo
    }

    /**
     * Complete OwnID Registration flow and register new user. Exact registration action depend on integration.
     *
     * User password will be generated automatically.
     *
     * @param loginIdString     User Login ID for new account.
     * @param params            [RegistrationParameters] (optional) Additional parameters for registration. Depend on integration.
     */
    @MainThread
    @JvmOverloads
    public fun register(loginIdString: String, params: RegistrationParameters? = null) {
        val ownIdResponseValue = ownIdResponse.value

        when {
            ownIdResponseValue == null -> OwnIdException("No OwnIdResponse available. Register flow must be run first")
            ownIdResponseValue.payload.type != OwnIdPayload.Type.Registration -> OwnIdException("OwnIdPayload type unexpected: ${ownIdResponseValue.payload.type}")
            else -> null
        }?.let { cause ->
            sendMetric(flowType, Metric.EventType.Error, "Sending error to app", errorMessage = cause.message)
            ownIdEvents.value = OwnIdRegisterEvent.Error(cause)
            OwnIdInternalLogger.logE(this, "register", cause.message, cause)
            return
        }

        isBusy = true

        ownIdInstance.register(loginIdString, params, ownIdResponseValue!!) {
            ownIdResponse.value = null
            isBusy = false
            onSuccess { loginData ->
                sendMetric(flowType, Metric.EventType.Track, "User is Registered", Metadata(authType = ownIdResponseValue.flowInfo.authType))
                ownIdEvents.value = OwnIdRegisterEvent.LoggedIn(ownIdResponseValue.flowInfo.authType, loginData)
                ownIdCoreImpl.storageService.saveLoginId(loginIdString)
            }
            onFailure { cause ->
                sendMetric(flowType, Metric.EventType.Error, "Sending error to app", errorMessage = cause.message)
                OwnIdInternalLogger.logE(this@OwnIdRegisterViewModel, "register.onFailure", cause.message, cause)
                ownIdEvents.value = OwnIdRegisterEvent.Error(OwnIdUserError.map(ownIdCoreImpl.localeService, "register: $cause", cause))
            }
            OwnIdInternalLogger.setFlowContext(null)
            ownIdCoreImpl.eventsService.setFlowContext(null)
            ownIdCoreImpl.eventsService.setFlowLoginId(null)
        }
    }
}