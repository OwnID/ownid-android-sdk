package com.ownid.sdk.viewmodel

import android.content.Context
import android.view.View
import androidx.annotation.MainThread
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.LifecycleOwner
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
import com.ownid.sdk.view.OwnIdButton
import com.ownid.sdk.view.delegate.EmailValidator.isValidEmail

/**
 * ViewModel class for OwnID Registration flow.
 */
@androidx.annotation.OptIn(InternalOwnIdAPI::class)
public class OwnIdRegisterViewModel internal constructor(ownIdCore: OwnIdCore) : OwnIdBaseViewModel<OwnIdRegisterEvent>(ownIdCore) {

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

    /**
     * Configures [view] by setting [View.OnClickListener](https://developer.android.com/reference/android/view/View.OnClickListener).
     * The exact OnClickListener depend on current OwnID status.
     *
     * Listens to OwnID updates and notifies when [OwnIdResponse] is available via [onOwnIdResponse].
     *
     * @param view              an instance of [View](https://developer.android.com/reference/android/view/View).
     * It must not be [OwnIdButton]. For [OwnIdButton] use OwnIdButton.setViewModel() method.
     * @param owner             view [LifecycleOwner].
     * @param emailProducer     (optional) a function that returns email as a [String]. If set, then it will be used to get user's email.
     * @param languageProducer  (optional) a function that returns language TAGs list for OwnID Web App
     * (well-formed [IETF BCP 47 language tag](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Accept-Language)) as a [String].
     * If set, then it will be used to get language TAGs list for OwnID Web App.
     * @param onOwnIdResponse   (optional) a function that will be called when OwnID has [OwnIdResponse]. Use it to change [view] UI.
     *
     * @throws IllegalArgumentException if [view] is instance of [OwnIdButton]. For [OwnIdButton] use OwnIdButton.setViewModel() method.
     */
    @MainThread
    @JvmOverloads
    public fun attachToView(
        view: View,
        owner: LifecycleOwner,
        emailProducer: () -> String = { "" },
        languageProducer: (View) -> String = { ConfigurationCompat.getLocales(it.resources.configuration).toLanguageTags() },
        onOwnIdResponse: (Boolean) -> Unit = {}
    ) {
        if (view is OwnIdButton) {
            throw IllegalArgumentException("For OwnIdButton view use OwnIdButton.setViewModel() method")
        } else {
            attachToViewInternal(view, owner, emailProducer, languageProducer, onOwnIdResponse)
            sendMetric(MetricItem.EventType.Track, "Custom OwnID view Loaded")
        }
    }

    @MainThread
    override fun onActivityResult(result: Result<OwnIdResponse>) {
        setBusy(OwnIdRegisterEvent.Busy(false))

        result
            .onSuccess { ownIdResponse ->
                ownIdResponseStatus.value = OwnIdResponseStatus(true, ownIdResponse)

                if (ownIdResponse.payload.type == OwnIdPayload.Type.Login) {
                    setBusy(OwnIdRegisterEvent.Busy(true))

                    ownIdCore.login(ownIdResponse) {
                        setBusy(OwnIdRegisterEvent.Busy(false))
                        onSuccess {
                            sendMetric(MetricItem.Category.Login, MetricItem.EventType.Track, "User is Logged in", ownIdResponse.context)
                            _events.value = OwnIdRegisterEvent.LoggedIn
                            ownIdResponseStatus.value = OwnIdResponseStatus(false, ownIdResponseStatus.value?.response)
                        }
                        onFailure { cause ->
                            sendMetric(MetricItem.Category.Login, MetricItem.EventType.Error, null, ownIdResponse.context, cause.message)
                            _events.value = OwnIdRegisterEvent.Error(OwnIdException.map("onActivityResult.login: $cause", cause))
                            ownIdResponseStatus.value = OwnIdResponseStatus.EMPTY
                            this@OwnIdRegisterViewModel.logE("onActivityResult.login: $cause", cause, ownIdCore)
                        }
                    }
                } else {
                    _events.value = OwnIdRegisterEvent.ReadyToRegister(ownIdResponse.loginId)
                }
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
    @JvmSynthetic
    override fun launch(context: Context, languageTags: String, email: String) {
        if (isBusy) {
            logV("launch: Ignored (already busy)", ownIdCore)
            return
        }

        setBusy(OwnIdRegisterEvent.Busy(true))

        ownIdResponseStatus.value?.response?.let {
            onActivityResult(Result.success(it))
            return
        }

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
        val ownIdResponseValue = ownIdResponseStatus.value?.response

        val cause = when {
            ownIdResponseValue == null -> NoOwnIdResponse()
            ownIdResponseValue.payload.type != OwnIdPayload.Type.Registration ->
                OwnIdException("OwnIdResponse.data type unexpected: ${ownIdResponseValue.payload.asJson()}")
            else -> null
        }

        if (cause != null) {
            sendMetric(MetricItem.EventType.Error, null, ownIdResponseValue?.context, cause.message)
            _events.value = OwnIdRegisterEvent.Error(cause)
            logE("register: $cause", cause, ownIdCore)
            return
        }

        setBusy(OwnIdRegisterEvent.Busy(true))

        ownIdCore.register(email, params, ownIdResponseValue!!) {
            setBusy(OwnIdRegisterEvent.Busy(false))
            onSuccess {
                sendMetric(MetricItem.EventType.Track, "User is Registered", ownIdResponseValue.context)
                _events.value = OwnIdRegisterEvent.LoggedIn
                ownIdResponseStatus.value = OwnIdResponseStatus(false, ownIdResponseStatus.value?.response)
            }
            onFailure { cause ->
                sendMetric(MetricItem.EventType.Error, null, ownIdResponseValue.context, cause.message)
                _events.value = OwnIdRegisterEvent.Error(OwnIdException.map("register: $cause", cause))
                this@OwnIdRegisterViewModel.logE("register: $cause", cause, ownIdCore)
            }
        }
    }

    @JvmSynthetic
    override fun undo() {
        super.undo()
        _events.value = OwnIdRegisterEvent.Undo
    }
}