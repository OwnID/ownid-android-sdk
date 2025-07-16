package com.ownid.sdk.viewmodel

import android.app.Activity
import android.content.Context
import android.view.View
import androidx.activity.result.ActivityResult
import androidx.annotation.CallSuper
import androidx.annotation.MainThread
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.ownid.sdk.AuthMethod
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnIdInstance
import com.ownid.sdk.OwnIdLoginType
import com.ownid.sdk.OwnIdPayload
import com.ownid.sdk.OwnIdResponse
import com.ownid.sdk.event.LoginData
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.exception.OwnIdFlowCanceled
import com.ownid.sdk.exception.OwnIdUserError
import com.ownid.sdk.internal.component.OwnIdInternalLogger
import com.ownid.sdk.internal.component.events.Metadata
import com.ownid.sdk.internal.component.events.Metric
import com.ownid.sdk.internal.feature.OwnIdActivity
import com.ownid.sdk.internal.feature.nativeflow.LifecycleCompletableCoroutineScope
import com.ownid.sdk.internal.feature.nativeflow.OwnIdNativeFlowError
import com.ownid.sdk.internal.feature.nativeflow.OwnIdNativeFlowFeature
import com.ownid.sdk.internal.feature.nativeflow.OwnIdNativeFlowLoginId
import com.ownid.sdk.internal.feature.nativeflow.OwnIdNativeFlowType
import com.ownid.sdk.view.AbstractOwnIdWidget
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Base ViewModel class for OwnID flow ViewModels.
 */
@OptIn(InternalOwnIdAPI::class)
public abstract class OwnIdFlowViewModel(ownIdInstance: OwnIdInstance) : OwnIdBaseViewModel(ownIdInstance) {

    @InternalOwnIdAPI
    protected abstract val flowType: OwnIdNativeFlowType

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
    protected abstract fun publishFlowResponse(loginId: String, payload: OwnIdPayload, authType: String, authToken: String?)

    @MainThread
    @InternalOwnIdAPI
    protected abstract fun publishLoginByIntegration(authType: String, loginData: LoginData?, authToken: String?)

    @JvmField
    @InternalOwnIdAPI
    protected val _busyFlow: MutableStateFlow<Boolean> = MutableStateFlow(false)

    /**
     * A [StateFlow] representing the busy status during the OwnID process.
     *
     * Set to true if OwnID is busy with waiting or processing data.
     */
    public val busyFlow: StateFlow<Boolean> = _busyFlow.asStateFlow()

    @JvmField
    @InternalOwnIdAPI
    protected val _ownIdResponseFlow: MutableStateFlow<OwnIdResponse?> = MutableStateFlow(null)

    /**
     * A [StateFlow] that holds [OwnIdResponse] as a result of the successful OwnID flow.
     */
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

    @InternalOwnIdAPI
    internal var viewLifecycleCoroutineScope: LifecycleCompletableCoroutineScope? = null

    @InternalOwnIdAPI
    @Suppress("DEPRECATION", "UNCHECKED_CAST")
    override fun onActivityResult(result: ActivityResult) {
        OwnIdInternalLogger.logD(this, "onActivityResult", result.toString())
        runCatching {
            if (result.resultCode != Activity.RESULT_OK) throw OwnIdFlowCanceled(OwnIdFlowCanceled.RESULT_PENDING + ":${result.resultCode}")
            (result.data?.getSerializableExtra(OwnIdActivity.KEY_RESULT) as Result<OwnIdResponse>).getOrThrow()
        }.let { endFlow(it) }
    }

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
        else OwnIdNativeFlowLoginId.fromString(loginIdString, ownIdCore.configuration).isValid()

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
            OwnIdNativeFlowType.REGISTER -> ownIdResponseUndo?.let {
                if (it.loginId == loginIdString) {
                    endFlow(Result.success(it))
                    return
                }
            }

            OwnIdNativeFlowType.LOGIN -> workingLoginId = loginIdString.ifBlank {
                runBlocking { ownIdCore.repository.getLoginId() ?: "" }
            }
        }

        ownIdResponseUndo = null
        _ownIdResponseFlow.value = null

        ownIdCore.configurationService.ensureConfigurationSet {
            mapCatching {
                ownIdCore.localeService.updateCurrentOwnIdLocale(context)
                val intent = OwnIdNativeFlowFeature.createIntent(context, ownIdCore.instanceName, flowType, loginType, workingLoginId)
                launchActivity(intent)
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

        viewModelScope.launch { saveLoginId(response.loginId, response.flowInfo.authType) }

        publishFlowResponse(response.loginId, response.payload, response.flowInfo.authType, response.flowInfo.authToken)

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
                viewModelScope.launch { saveLoginId(response.loginId, response.flowInfo.authType) }
                publishLoginByIntegration(response.flowInfo.authType, loginData, response.flowInfo.authToken)
            }
            onFailure { cause ->
                OwnIdInternalLogger.logW(this@OwnIdFlowViewModel, "doLoginByIntegration", "Login: ${cause.message}", cause)
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
        super.onCleared()
        ownIdResponseUndo = null
        _ownIdResponseFlow.value = null
        viewLifecycleCoroutineScope?.coroutineContext?.cancel()
    }

    @InternalOwnIdAPI
    protected fun sendMetric(flowType: OwnIdNativeFlowType, type: Metric.EventType, cause: OwnIdException) {
        val errorCode = when (cause) {
            is OwnIdFlowCanceled -> OwnIdNativeFlowError.CodeLocal.FLOW_CANCELED
            is OwnIdUserError -> cause.code
            else -> null
        }
        sendMetric(flowType, type, cause.message ?: cause.toString(), errorMessage = cause.message, errorCode = errorCode)
    }

    @InternalOwnIdAPI
    protected fun sendMetric(
        flowType: OwnIdNativeFlowType,
        type: Metric.EventType,
        action: String,
        metadata: Metadata? = null,
        errorMessage: String? = null,
        errorCode: String? = null
    ) {
        val returningUser = runBlocking { ownIdCore.repository.getLoginId() }?.isNotBlank() ?: false
        val meta = (metadata ?: Metadata()).copy(returningUser = returningUser)
        ownIdCore.eventsService.sendMetric(flowType, type, action, meta, errorMessage = errorMessage, errorCode = errorCode)
    }

    @InternalOwnIdAPI
    protected suspend fun saveLoginId(loginId: String, authType: String) {
        withContext(NonCancellable) {
            val authMethod = AuthMethod.fromString(authType)
            runCatching { ownIdCore.repository.saveLoginId(loginId, authMethod) }
            val loginIdData = ownIdCore.repository.getLoginIdData(loginId)
            runCatching { ownIdCore.repository.saveLoginIdData(loginId, loginIdData.copy(authMethod = authMethod)) }
        }
    }
}