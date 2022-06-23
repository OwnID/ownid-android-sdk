package com.ownid.sdk.viewmodel

import android.content.Context
import androidx.annotation.MainThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnIdCore
import com.ownid.sdk.RegistrationParameters
import com.ownid.sdk.event.OwnIdRegisterEvent
import com.ownid.sdk.exception.EmailInvalid
import com.ownid.sdk.exception.NoOwnIdResponse
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.internal.OwnIdPayload
import com.ownid.sdk.internal.OwnIdResponse
import com.ownid.sdk.internal.events.MetricItem
import com.ownid.sdk.logE
import com.ownid.sdk.logV

/**
 * ViewModel class for OwnID Registration flow.
 */
@OptIn(InternalOwnIdAPI::class)
public class OwnIdRegisterViewModel(ownIdCore: OwnIdCore) : OwnIdBaseViewModel<OwnIdRegisterEvent>(ownIdCore) {

    @PublishedApi
    @Suppress("UNCHECKED_CAST")
    @InternalOwnIdAPI
    internal class Factory(private val ownIdCore: OwnIdCore) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = OwnIdRegisterViewModel(ownIdCore) as T
    }

    override val resultRegistryKey: String = "com.ownid.sdk.result.registry.REGISTER"

    /**
     * Expose [OwnIdRegisterEvent] as [LiveData]
     */
    public val events: LiveData<out OwnIdRegisterEvent> = _events

    @MainThread
    override fun onActivityResult(result: Result<OwnIdResponse>) {
        setBusy(OwnIdRegisterEvent.Busy(false))

        result
            .onSuccess {
                _ownIdResponse.value = it
                _events.value = OwnIdRegisterEvent.ReadyToRegister(it.loginId)
            }
            .onFailure { cause ->
                _events.value = OwnIdRegisterEvent.Error(OwnIdException.map("onActivityResult: $cause", cause))
                logE("onActivityResult: $cause", cause, ownIdCore)
            }
    }

    /**
     * Launch OwnID Register intent.
     *
     * @param context       Android [Context]
     * @param languageTags  Language TAGs list for Web App (well-formed [IETF BCP 47 language tag](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Accept-Language))
     * @param email         User email
     */
    @MainThread
    override fun launch(context: Context, languageTags: String, email: String) {
        if (isBusy) {
            logV("launch: Ignored (already busy)", ownIdCore)
            return
        }
        setBusy(OwnIdRegisterEvent.Busy(true))

        runCatching {
            if (email.isNotBlank() && email.isValidEmail().not()) throw EmailInvalid()
            launchIntent(ownIdCore.createRegisterIntent(context, languageTags, email))
        }
            .onFailure { cause ->
                setBusy(OwnIdRegisterEvent.Busy(false))
                _events.value = OwnIdRegisterEvent.Error(OwnIdException.map("launch: $cause", cause))
                logE("launch: $cause", cause, ownIdCore)
            }
    }

    /**
     * Complete OwnID Registration flow and register new user. Exact registration action depend on integration.
     *
     * User password will be generated automatically.
     *
     * @param email     User email for new account, must be a valid email.
     * @param params    [RegistrationParameters] (optional) Additional parameters for registration. Depend on integration.
     */
    @MainThread
    @JvmOverloads
    public fun register(email: String, params: RegistrationParameters? = null) {
        setBusy(OwnIdRegisterEvent.Busy(true))

        val ownIdResponseValue = _ownIdResponse.value

        val cause = when {
            ownIdResponseValue == null -> NoOwnIdResponse()
            ownIdResponseValue.payload.type != OwnIdPayload.Type.Registration ->
                OwnIdException("OwnIdResponse.data type unexpected: ${ownIdResponseValue.payload.asJson()}")
            else -> null
        }

        if (cause != null) {
            setBusy(OwnIdRegisterEvent.Busy(false))
            sendErrorMetric(MetricItem.Category.Registration, null, ownIdResponseValue?.context ?: "", cause.message)
            _events.value = OwnIdRegisterEvent.Error(cause)
            logE("register: $cause", cause, ownIdCore)
            return
        }

        ownIdCore.register(email, params, ownIdResponseValue!!) {
            setBusy(OwnIdRegisterEvent.Busy(false))
            onSuccess {
                sendTrackMetric(MetricItem.Category.Registration, "User is Registered", ownIdResponseValue.context)
                _events.value = OwnIdRegisterEvent.LoggedIn
                _ownIdResponse.value = null
            }
            onFailure { cause ->
                sendErrorMetric(MetricItem.Category.Registration, null, ownIdResponseValue.context, cause.message)
                _events.value = OwnIdRegisterEvent.Error(OwnIdException.map("register: $cause", cause))
                this@OwnIdRegisterViewModel.logE("register: $cause", cause, ownIdCore)
            }
        }
    }

    override fun undo() {
        super.undo()
        _events.value = OwnIdRegisterEvent.Undo
    }
}