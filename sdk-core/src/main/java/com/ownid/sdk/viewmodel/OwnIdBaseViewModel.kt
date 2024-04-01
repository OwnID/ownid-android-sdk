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
import androidx.lifecycle.ViewModel
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnIdCoreImpl
import com.ownid.sdk.OwnIdInstance
import com.ownid.sdk.OwnIdLoginType
import com.ownid.sdk.OwnIdPayload
import com.ownid.sdk.OwnIdResponse
import com.ownid.sdk.event.LoginData
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.exception.OwnIdFlowCanceled
import com.ownid.sdk.exception.OwnIdUserError
import com.ownid.sdk.internal.LifecycleCompletableCoroutineScope
import com.ownid.sdk.internal.OwnIdInternalLogger
import com.ownid.sdk.internal.events.Metadata
import com.ownid.sdk.internal.events.Metric
import com.ownid.sdk.internal.flow.OwnIdFlowActivity
import com.ownid.sdk.internal.flow.OwnIdFlowError
import com.ownid.sdk.internal.flow.OwnIdFlowType
import com.ownid.sdk.internal.flow.OwnIdLoginId
import com.ownid.sdk.internal.flow.steps.InitStep
import com.ownid.sdk.view.AbstractOwnIdWidget
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Base ViewModel class for any OwnID ViewModel.
 */
public abstract class OwnIdBaseViewModel @InternalOwnIdAPI constructor(internal val ownIdInstance: OwnIdInstance) : ViewModel() {

    @InternalOwnIdAPI
    protected abstract val flowType: OwnIdFlowType

    @InternalOwnIdAPI
    protected abstract val resultRegistryKey: String

    @MainThread
    @InternalOwnIdAPI
    protected abstract fun endFlow(result: Result<OwnIdResponse>)

    @MainThread
    @InternalOwnIdAPI
    protected abstract fun undo(metadata: Metadata)

    @MainThread
    @InternalOwnIdAPI
    protected abstract fun publishBusy(isBusy: Boolean)

    @MainThread
    @InternalOwnIdAPI
    protected abstract fun publishError(error: OwnIdException)

    @MainThread
    @InternalOwnIdAPI
    protected abstract fun publishFlowResponse(loginId: String, payload: OwnIdPayload, authType: String)

    @MainThread
    @InternalOwnIdAPI
    protected abstract fun publishLoginByIntegration(authType: String, loginData: LoginData?)

    @JvmField
    @InternalOwnIdAPI
    internal val ownIdCore: OwnIdCoreImpl = ownIdInstance.ownIdCore as OwnIdCoreImpl

    @JvmField
    @InternalOwnIdAPI
    protected val hasIntegration: Boolean = ownIdInstance.ownIdIntegration != null

    @JvmField
    @InternalOwnIdAPI
    protected val _busyFlow: MutableStateFlow<Boolean> = MutableStateFlow(false)

    /**
     * A [StateFlow] representing the busy status during the OwnID process.
     *
     * Set to true if OwnID is busy with waiting or processing data.
     */
    @OptIn(InternalOwnIdAPI::class)
    public val busyFlow: StateFlow<Boolean> = _busyFlow.asStateFlow()

    @JvmField
    @InternalOwnIdAPI
    protected val _ownIdResponseFlow: MutableStateFlow<OwnIdResponse?> = MutableStateFlow(null)

    /**
     * A [StateFlow] that holds [OwnIdResponse] as a result of the successful OwnID flow.
     */
    @OptIn(InternalOwnIdAPI::class)
    public val ownIdResponseFlow: StateFlow<OwnIdResponse?> = _ownIdResponseFlow.asStateFlow()

    /**
     * Checks if OwnID has a [OwnIdResponse] as a result of the successful OwnID flow, indicating a ready-to-register state.
     *
     * @return True if OwnID has a [OwnIdResponse] representing the ready-to-register state.
     */
    public val isReadyToRegister: Boolean
        get() = ownIdResponseFlow.value != null

    @JvmField
    @InternalOwnIdAPI
    protected var ownIdResponseUndo: OwnIdResponse? = null

    private var resultLauncher: ActivityResultLauncher<Intent>? = null

    @InternalOwnIdAPI
    @Suppress("DEPRECATION", "UNCHECKED_CAST")
    private fun onActivityResult(result: ActivityResult) {
        OwnIdInternalLogger.logD(this, "onActivityResult", result.toString())
        runCatching {
            if (result.resultCode != Activity.RESULT_OK) throw OwnIdFlowCanceled(OwnIdFlowCanceled.RESULT_PENDING + ":${result.resultCode}")
            (result.data?.getSerializableExtra(OwnIdFlowActivity.KEY_RESULT) as Result<OwnIdResponse>).getOrThrow()
        }.let { endFlow(it) }
    }

    @MainThread
    @JvmSynthetic
    @InternalOwnIdAPI
    internal fun createResultLauncher(resultRegistry: ActivityResultRegistry, owner: LifecycleOwner) {
        resultLauncher = resultRegistry.register(resultRegistryKey, owner, ActivityResultContracts.StartActivityForResult()) { result ->
            onActivityResult(result)
        }
    }

    @MainThread
    @JvmSynthetic
    @InternalOwnIdAPI
    public fun createResultLauncher(resultRegistry: ActivityResultRegistry) {
        unregisterResultLauncher()
        resultLauncher = resultRegistry.register(resultRegistryKey, ActivityResultContracts.StartActivityForResult()) { result ->
            onActivityResult(result)
        }
    }

    @MainThread
    @JvmSynthetic
    @InternalOwnIdAPI
    public fun unregisterResultLauncher() {
        resultLauncher?.unregister()
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
    @OptIn(InternalOwnIdAPI::class)
    public fun setLanguageTagsProvider(provider: (() -> String)?) {
        ownIdCore.localeService.setLanguageTagsProvider(provider)
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
    @OptIn(InternalOwnIdAPI::class)
    public fun setLanguageTags(languageTags: String?) {
        ownIdCore.localeService.setLanguageTags(languageTags)
    }

    @InternalOwnIdAPI
    internal var viewLifecycleCoroutineScope: LifecycleCompletableCoroutineScope? = null

    @MainThread
    @InternalOwnIdAPI
    protected fun attachToViewInternal(
        view: View,
        owner: LifecycleOwner,
        loginIdProvider: (() -> String)?,
        loginType: OwnIdLoginType?
    ) {
        viewLifecycleCoroutineScope?.coroutineContext?.cancel()

        val scope = LifecycleCompletableCoroutineScope(owner.lifecycle) {
            view.setOnClickListener(null)
            view.isClickable = false
            viewLifecycleCoroutineScope = null
        }

        viewLifecycleCoroutineScope = scope

        if (view is AbstractOwnIdWidget) {
            view.setLoginIdProvider(loginIdProvider)
            view.setViewModel(this)
        }

        val metadata = if (view is AbstractOwnIdWidget) view.getMetadata().copy(loginType = loginType)
        else Metadata(widgetType = Metadata.WidgetType.CUSTOM, loginType = loginType)

        when (this) {
            is OwnIdLoginViewModel ->
                view.setOnClickListener { onViewClicked(view, metadata, loginIdProvider, loginType) }

            is OwnIdRegisterViewModel ->
                ownIdResponseFlow.onEach { response ->
                    if (response != null) view.setOnClickListener { undo(metadata) }
                    else view.setOnClickListener { onViewClicked(view, metadata, loginIdProvider, loginType) }
                }.launchIn(scope)
        }

        sendMetric(flowType, Metric.EventType.Track, "OwnID Widget is Loaded", metadata)
    }

    @MainThread
    @InternalOwnIdAPI
    private fun onViewClicked(view: View, metadata: Metadata, loginIdProvider: (() -> String)?, loginType: OwnIdLoginType?) {
        val loginIdString = if (view is AbstractOwnIdWidget) view.getLoginId() else loginIdProvider?.invoke() ?: ""

        val validLoginIdFormat = if (ownIdCore.configuration.isServerConfigurationSet.not()) null
        else OwnIdLoginId.fromString(loginIdString, ownIdCore.configuration).isValid()

        sendMetric(
            flowType, Metric.EventType.Click, "Clicked Skip Password",
            metadata.copy(hasLoginId = loginIdString.isNotBlank(), validLoginIdFormat = validLoginIdFormat)
        )

        startFlow(view.context, loginIdString, loginType)
    }

    @MainThread
    @InternalOwnIdAPI
    protected fun startFlow(context: Context, loginIdString: String, loginType: OwnIdLoginType?) {
        if (_busyFlow.value) {
            OwnIdInternalLogger.logI(this, "startFlow", "Ignored (already busy)")
            return
        }

        publishBusy(true)

        var workingLoginId = loginIdString

        when (flowType) {
            OwnIdFlowType.REGISTER -> ownIdResponseUndo?.let {
                if (it.loginId == loginIdString) {
                    endFlow(Result.success(it))
                    return
                }
            }

            OwnIdFlowType.LOGIN -> workingLoginId = loginIdString.ifBlank { ownIdCore.storageService.getLastLoginId() }
        }

        ownIdResponseUndo = null
        _ownIdResponseFlow.value = null

        ownIdCore.configurationService.ensureConfigurationSet {
            mapCatching {
                ownIdCore.localeService.updateCurrentOwnIdLocale(context)

                requireNotNull(resultLauncher) { "${this@OwnIdBaseViewModel::class.java.simpleName}: resultLauncher is not set" }.launch(
                    OwnIdFlowActivity.createIntent(
                        context, ownIdCore.instanceName, flowType, loginType, workingLoginId, InitStep::class.java.simpleName
                    )
                )
            }.onFailure { endFlow(Result.failure(OwnIdException("OwnIdBaseViewModel.startFlow: ${it.message}", it))) }
        }
    }

    @MainThread
    @InternalOwnIdAPI
    protected fun doRegisterOrLoginWithoutIntegration(response: OwnIdResponse) {
        OwnIdInternalLogger.logD(this, "doRegisterOrLoginWithoutIntegration", "Get new OwnIdResponse")

        ownIdResponseUndo = null
        _ownIdResponseFlow.value = null

        publishBusy(false)

        ownIdCore.storageService.saveLoginId(response.loginId)

        publishFlowResponse(response.loginId, response.payload, response.flowInfo.authType)

        OwnIdInternalLogger.setFlowContext(null)
        ownIdCore.eventsService.setFlowContext(null)
        ownIdCore.eventsService.setFlowLoginId(null)
    }

    @Throws
    @MainThread
    @InternalOwnIdAPI
    protected fun doLoginByIntegration(response: OwnIdResponse) {
        requireNotNull(ownIdInstance.ownIdIntegration) {
            "${this::class.java.simpleName}: No OwnIdIntegration available"
        }.login(response) {
            ownIdResponseUndo = null
            _ownIdResponseFlow.value = null

            publishBusy(false)

            onSuccess { loginData ->
                ownIdCore.storageService.saveLoginId(response.loginId)
                publishLoginByIntegration(response.flowInfo.authType, loginData)
            }
            onFailure { cause ->
                OwnIdInternalLogger.logW(this@OwnIdBaseViewModel, "doLoginByIntegration", "Login: ${cause.message}", cause)
                publishError(OwnIdUserError.map(ownIdCore.localeService, "doLoginByIntegration.onFailure: ${cause.message}", cause))
            }

            OwnIdInternalLogger.setFlowContext(null)
            ownIdCore.eventsService.setFlowContext(null)
            ownIdCore.eventsService.setFlowLoginId(null)
        }
    }

    @CallSuper
    @InternalOwnIdAPI
    override fun onCleared() {
        OwnIdInternalLogger.logD(this, "onCleared", "Invoked")
        resultLauncher = null
        ownIdResponseUndo = null
        _ownIdResponseFlow.value = null
        viewLifecycleCoroutineScope?.coroutineContext?.cancel()
    }

    @InternalOwnIdAPI
    protected fun sendMetric(flowType: OwnIdFlowType, type: Metric.EventType, cause: OwnIdException) {
        val errorCode = when (cause) {
            is OwnIdFlowCanceled -> OwnIdFlowError.CodeLocal.FLOW_CANCELED
            is OwnIdUserError -> cause.code
            else -> null
        }
        sendMetric(flowType, type, cause.message ?: cause.toString(), errorMessage = cause.message, errorCode = errorCode)
    }

    @InternalOwnIdAPI
    protected fun sendMetric(
        flowType: OwnIdFlowType,
        type: Metric.EventType,
        action: String,
        metadata: Metadata? = null,
        errorMessage: String? = null,
        errorCode: String? = null
    ) {
        val meta = (metadata ?: Metadata()).copy(returningUser = ownIdCore.storageService.getLastLoginId().isNotBlank())
        ownIdCore.eventsService.sendMetric(flowType, type, action, meta, errorMessage = errorMessage, errorCode = errorCode)
    }
}