package com.ownid.sdk.viewmodel

import android.content.Context
import androidx.annotation.MainThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnIdCore
import com.ownid.sdk.event.OwnIdLoginEvent
import com.ownid.sdk.exception.EmailInvalid
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.internal.OwnIdPayload
import com.ownid.sdk.internal.OwnIdResponse
import com.ownid.sdk.internal.events.MetricItem
import com.ownid.sdk.logD
import com.ownid.sdk.logE
import com.ownid.sdk.logV

/**
 * ViewModel class for OwnID Login flow.
 */
@OptIn(InternalOwnIdAPI::class)
public class OwnIdLoginViewModel(ownIdCore: OwnIdCore) : OwnIdBaseViewModel<OwnIdLoginEvent>(ownIdCore) {

    @PublishedApi
    @Suppress("UNCHECKED_CAST")
    @InternalOwnIdAPI
    internal class Factory(private val ownIdCore: OwnIdCore) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = OwnIdLoginViewModel(ownIdCore) as T
    }

    override val resultRegistryKey: String = "com.ownid.sdk.result.registry.LOGIN"

    /**
     * Expose [OwnIdLoginEvent] as [LiveData]
     */
    public val events: LiveData<out OwnIdLoginEvent> = _events

    @MainThread
    override fun onActivityResult(result: Result<OwnIdResponse>) {
        setBusy(OwnIdLoginEvent.Busy(false))

        result
            .onSuccess { ownIdResponse ->
                setBusy(OwnIdLoginEvent.Busy(true))
                logD("onActivityResult", ownIdResponse)
                _ownIdResponse.value = ownIdResponse

                if (ownIdResponse.payload.type != OwnIdPayload.Type.Login) {
                    setBusy(OwnIdLoginEvent.Busy(false))
                    val cause =
                        OwnIdException("OwnIdResponse.data type unexpected: ${ownIdResponse.payload.asJson()}")
                    sendErrorMetric(MetricItem.Category.Login, null, ownIdResponse.context, cause.message)
                    _events.value = OwnIdLoginEvent.Error(cause)
                    logE("onActivityResult: $cause", cause, ownIdCore)
                    return
                }

                ownIdCore.login(ownIdResponse) {
                    setBusy(OwnIdLoginEvent.Busy(false))
                    onSuccess {
                        sendTrackMetric(MetricItem.Category.Login, "User is Logged in", ownIdResponse.context)
                        _events.value = OwnIdLoginEvent.LoggedIn
                        _ownIdResponse.value = null
                    }
                    onFailure { cause ->
                        sendErrorMetric(MetricItem.Category.Login, null, ownIdResponse.context, cause.message)
                        _events.value = OwnIdLoginEvent.Error(OwnIdException.map("onActivityResult.login: $cause", cause))
                        _ownIdResponse.value = null
                        this@OwnIdLoginViewModel.logE("onActivityResult.login: $cause", cause, ownIdCore)
                    }
                }
            }
            .onFailure { cause ->
                sendErrorMetric(MetricItem.Category.Login, null, "", cause.message)
                _events.value = OwnIdLoginEvent.Error(OwnIdException.map("onActivityResult: $cause", cause))
                _ownIdResponse.value = null
                logE("onActivityResult: $cause", cause, ownIdCore)
            }
    }

    /**
     * Launch OwnID Login intent.
     *
     * @param context       Android [Context]
     * @param languageTags  Language TAGs list for Web App (well-formed [IETF BCP 47 language tag](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Accept-Language))
     * @param email         User email (required for LinkOnLogin)
     */
    @MainThread
    override fun launch(context: Context, languageTags: String, email: String) {
        if (isBusy) {
            logV("launch: Ignored (already busy)", ownIdCore)
            return
        }
        setBusy(OwnIdLoginEvent.Busy(true))

        runCatching {
            if (email.isNotBlank() && email.isValidEmail().not()) throw EmailInvalid()
            launchIntent(ownIdCore.createLoginIntent(context, languageTags, email))
        }
            .onFailure { cause ->
                setBusy(OwnIdLoginEvent.Busy(false))
                _events.value = OwnIdLoginEvent.Error(OwnIdException.map("launch: $cause", cause))
                logE("launch: $cause", cause, ownIdCore)
            }
    }

    override fun undo() {
        super.undo()
        _events.value = null
    }
}