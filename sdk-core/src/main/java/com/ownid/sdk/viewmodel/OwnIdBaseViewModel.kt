package com.ownid.sdk.viewmodel

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.CallSuper
import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnIdCoreImpl
import com.ownid.sdk.OwnIdInstance
import com.ownid.sdk.OwnIdIntegration
import com.ownid.sdk.OwnIdLoginType
import com.ownid.sdk.OwnIdResponse
import com.ownid.sdk.event.OwnIdEvent
import com.ownid.sdk.event.OwnIdLoginEvent
import com.ownid.sdk.event.OwnIdLoginFlow
import com.ownid.sdk.event.OwnIdRegisterEvent
import com.ownid.sdk.event.OwnIdRegisterFlow
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.exception.OwnIdFlowCanceled
import com.ownid.sdk.exception.OwnIdUserError
import com.ownid.sdk.internal.OwnIdInternalLogger
import com.ownid.sdk.internal.events.Metadata
import com.ownid.sdk.internal.events.Metric
import com.ownid.sdk.internal.flow.OwnIdFlowActivity
import com.ownid.sdk.internal.flow.OwnIdFlowType
import com.ownid.sdk.internal.flow.OwnIdLoginId
import com.ownid.sdk.internal.flow.steps.InitStep
import com.ownid.sdk.view.AbstractOwnIdWidget

/**
 * Base ViewModel class for any OwnID ViewModel.
 */
@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public abstract class OwnIdBaseViewModel<E : OwnIdEvent, R : OwnIdEvent>(internal val ownIdInstance: OwnIdInstance) : ViewModel() {

    protected abstract val flowType: OwnIdFlowType
    protected abstract val resultRegistryKey: String

    @MainThread
    protected abstract fun endFlow(result: Result<OwnIdResponse>)

    @MainThread
    protected abstract fun undo(metadata: Metadata)

    @JvmField
    protected val ownIdCoreImpl: OwnIdCoreImpl = ownIdInstance.ownIdCore as OwnIdCoreImpl

    @JvmField
    protected val hasIntegration: Boolean = ownIdInstance.ownIdIntegration != null

    @JvmField
    protected val ownIdIntegrationEvents: MutableLiveData<E> = MutableLiveData()

    @JvmField
    protected val ownIdFlowEvents: MutableLiveData<R> = MutableLiveData()

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var cleared: Boolean = false

    protected var isBusy: Boolean = false
        @MainThread
        protected set(value) {
            OwnIdInternalLogger.logD(this, "setBusy", "$value")
            field = value
            mainHandler.post {
                if (cleared) return@post
                when (this@OwnIdBaseViewModel) {
                    is OwnIdLoginViewModel ->
                        if (hasIntegration) ownIdIntegrationEvents.value = OwnIdLoginEvent.Busy(value)
                        else ownIdFlowEvents.value = OwnIdLoginFlow.Busy(value)

                    is OwnIdRegisterViewModel ->
                        if (hasIntegration) ownIdIntegrationEvents.value = OwnIdRegisterEvent.Busy(value)
                        else ownIdFlowEvents.value = OwnIdRegisterFlow.Busy(value)
                }
            }
        }

    @get:JvmSynthetic
    internal val ownIdResponseLiveData: MutableLiveData<OwnIdResponse?> = MutableLiveData(null)

    @JvmField
    protected var ownIdResponseUndo: OwnIdResponse? = null

    private var resultLauncher: ActivityResultLauncher<Intent>? = null

    @MainThread
    @JvmSynthetic
    @Suppress("DEPRECATION", "UNCHECKED_CAST")
    internal fun createResultLauncher(resultRegistry: ActivityResultRegistry, owner: LifecycleOwner) {
        resultLauncher = resultRegistry.register(resultRegistryKey, owner, ActivityResultContracts.StartActivityForResult()) { result ->
            OwnIdInternalLogger.logD(this, "resultLauncher", result.toString())
            runCatching {
                if (result.resultCode != Activity.RESULT_OK) throw OwnIdFlowCanceled(OwnIdFlowCanceled.RESULT_PENDING + ":${result.resultCode}")
                (result.data?.getSerializableExtra(OwnIdFlowActivity.KEY_RESULT) as Result<OwnIdResponse>).getOrThrow()
            }.let { endFlow(it) }
        }
    }

    /**
     * Set a language TAGs provider for OwnID SDK.
     *
     * If provider is set and returned TAGs string is not empty, then it will be used in OwnID SDK.
     * The value from [setLanguageTags] will be ignored.
     *
     * @param provider  A function that returns language TAGs (well-formed [IETF BCP 47 language tag](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Accept-Language)) as a [String].
     * To remove existing provider, pass `null` as parameter.
     */
    @MainThread
    public fun setLanguageTagsProvider(provider: (() -> String)?) {
        ownIdCoreImpl.localeService.setLanguageTagsProvider(provider)
    }

    /**
     * Set a language TAGs for OwnID SDK.
     *
     * If language TAGs are set by this method and [languageTags] is not empty string, then it will be used in OwnID SDK.
     *
     * @param languageTags  Language TAGs [String] (well-formed [IETF BCP 47 language tag](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Accept-Language)).
     * To remove existing language TAGs, pass `null` as parameter.
     */
    @MainThread
    public fun setLanguageTags(languageTags: String?) {
        ownIdCoreImpl.localeService.setLanguageTags(languageTags)
    }

    @MainThread
    protected fun attachToViewInternal(
        view: View,
        owner: LifecycleOwner,
        loginIdProvider: (() -> String)?,
        loginType: OwnIdLoginType?,
        onOwnIdResponse: (Boolean) -> Unit
    ) {
        if (view is AbstractOwnIdWidget) view.setLoginIdProvider(loginIdProvider)
        if (view is AbstractOwnIdWidget) view.setViewModel(this, owner) else ownIdResponseLiveData.removeObservers(owner)

        val metadata = if (view is AbstractOwnIdWidget) view.getMetadata().copy(loginType = loginType)
        else Metadata(widgetType = Metadata.WidgetType.CUSTOM, loginType = loginType)

        when (this) {
            is OwnIdLoginViewModel -> view.setOnClickListener { onViewClicked(view, metadata, loginIdProvider, loginType) }

            is OwnIdRegisterViewModel -> ownIdResponseLiveData.observe(owner) { response ->
                if (response != null) view.setOnClickListener { undo(metadata) }
                else view.setOnClickListener { onViewClicked(view, metadata, loginIdProvider, loginType) }
            }
        }

        ownIdResponseLiveData.observe(owner) { response -> onOwnIdResponse.invoke(response != null) }

        owner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                view.setOnClickListener(null)
                view.isClickable = false
                owner.lifecycle.removeObserver(this)
            }
        })

        sendMetric(flowType, Metric.EventType.Track, "OwnID Widget is Loaded", metadata)
    }

    @MainThread
    private fun onViewClicked(view: View, metadata: Metadata, loginIdProvider: (() -> String)?, loginType: OwnIdLoginType?) {
        val loginIdString = if (view is AbstractOwnIdWidget) view.getLoginId() else loginIdProvider?.invoke() ?: ""

        val validLoginIdFormat = if (ownIdCoreImpl.configuration.isServerConfigurationSet.not()) null
        else OwnIdLoginId.fromString(loginIdString, ownIdCoreImpl.configuration).isValid()

        sendMetric(
            flowType, Metric.EventType.Click, "Clicked Skip Password",
            metadata.copy(hasLoginId = loginIdString.isNotBlank(), validLoginIdFormat = validLoginIdFormat)
        )

        startFlow(view.context, loginIdString, loginType)
    }

    @MainThread
    protected fun startFlow(context: Context, loginIdString: String, loginType: OwnIdLoginType?) {
        if (isBusy) {
            OwnIdInternalLogger.logD(this, "startFlow", "Ignored (already busy)")
            return
        }

        var workingLoginId = loginIdString

        when (flowType) {
            OwnIdFlowType.REGISTER -> ownIdResponseUndo?.let {
                if (it.loginId == loginIdString) {
                    endFlow(Result.success(it))
                    return
                }
                ownIdResponseUndo = null
            }

            OwnIdFlowType.LOGIN -> workingLoginId = loginIdString.ifBlank { ownIdCoreImpl.storageService.getLastLoginId() }
        }

        isBusy = true
        ownIdResponseLiveData.value = null

        ownIdCoreImpl.configurationService.ensureConfigurationSet {
            mapCatching {
                ownIdCoreImpl.localeService.updateCurrentOwnIdLocale(context)

                requireNotNull(resultLauncher) { "${this@OwnIdBaseViewModel::class.java.simpleName}: resultLauncher is not set" }.launch(
                    OwnIdFlowActivity.createIntent(
                        context, ownIdCoreImpl.instanceName, flowType, loginType, workingLoginId, InitStep::class.java.simpleName
                    )
                )
            }.onFailure { endFlow(Result.failure(OwnIdException("OwnIdBaseViewModel.startFlow: ${it.message}", it))) }
        }
    }

    @MainThread
    protected fun doLoginWithoutIntegration(response: OwnIdResponse) {
        OwnIdInternalLogger.logD(this, "doLoginWithoutIntegration", "Get new OwnIdResponse")

        sendMetric(flowType, Metric.EventType.Track, "User is Logged in", Metadata(authType = response.flowInfo.authType))
        ownIdCoreImpl.storageService.saveLoginId(response.loginId)

        ownIdResponseLiveData.value = null

        when (this@OwnIdBaseViewModel) {
            is OwnIdLoginViewModel ->
                ownIdFlowEvents.value = OwnIdLoginFlow.Response(response.loginId, response.payload, response.flowInfo.authType)

            is OwnIdRegisterViewModel ->
                ownIdFlowEvents.value = OwnIdRegisterFlow.Response(response.loginId, response.payload, response.flowInfo.authType)
        }

        isBusy = false
        OwnIdInternalLogger.setFlowContext(null)
        ownIdCoreImpl.eventsService.setFlowContext(null)
        ownIdCoreImpl.eventsService.setFlowLoginId(null)
    }

    @Throws
    @MainThread
    protected fun doLoginByIntegration(ownIdIntegration: OwnIdIntegration, response: OwnIdResponse) {
        ownIdIntegration.login(response) {
            ownIdResponseLiveData.value = null
            onSuccess { loginData ->
                sendMetric(flowType, Metric.EventType.Track, "User is Logged in", Metadata(authType = response.flowInfo.authType))
                ownIdCoreImpl.storageService.saveLoginId(response.loginId)

                when (this@OwnIdBaseViewModel) {
                    is OwnIdLoginViewModel -> ownIdIntegrationEvents.value = OwnIdLoginEvent.LoggedIn(response.flowInfo.authType, loginData)
                    is OwnIdRegisterViewModel -> ownIdIntegrationEvents.value = OwnIdRegisterEvent.LoggedIn(response.flowInfo.authType, loginData)
                }
            }
            onFailure { cause ->
                sendMetric(flowType, Metric.EventType.Error, "Sending error to app", errorMessage = cause.message)
                OwnIdInternalLogger.logE(this@OwnIdBaseViewModel, "doLoginByIntegration.onFailure", cause.message, cause)
                val error = OwnIdUserError.map(ownIdCoreImpl.localeService, "doLoginByIntegration.onFailure: ${cause.message}", cause)
                when (this@OwnIdBaseViewModel) {
                    is OwnIdLoginViewModel -> ownIdIntegrationEvents.value = OwnIdLoginEvent.Error(error)
                    is OwnIdRegisterViewModel -> ownIdIntegrationEvents.value = OwnIdRegisterEvent.Error(error)
                }
            }
            isBusy = false
            OwnIdInternalLogger.setFlowContext(null)
            ownIdCoreImpl.eventsService.setFlowContext(null)
            ownIdCoreImpl.eventsService.setFlowLoginId(null)
        }
    }

    @CallSuper
    override fun onCleared() {
        OwnIdInternalLogger.logD(this, "onCleared", "Invoked")
        resultLauncher = null
        ownIdResponseLiveData.value = null
        ownIdResponseUndo = null
        cleared = true
    }

    protected fun sendMetric(
        flowType: OwnIdFlowType, type: Metric.EventType, action: String, metadata: Metadata? = null, errorMessage: String? = null
    ) {
        val meta = (metadata ?: Metadata()).copy(returningUser = ownIdCoreImpl.storageService.getLastLoginId().isNotBlank())
        ownIdCoreImpl.eventsService.sendMetric(flowType, type, action, meta, errorMessage = errorMessage)
    }
}