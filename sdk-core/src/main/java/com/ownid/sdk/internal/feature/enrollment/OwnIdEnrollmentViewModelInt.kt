package com.ownid.sdk.internal.feature.enrollment

import androidx.annotation.CallSuper
import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import androidx.credentials.exceptions.CreateCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.internal.component.OwnIdInternalLogger
import com.ownid.sdk.internal.component.events.Metric
import com.ownid.sdk.internal.toBase64UrlSafeNoPadding
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject
import kotlin.random.Random

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class OwnIdEnrollmentViewModelInt : ViewModel() {

    internal sealed class State {
        internal object Busy : State()
        internal data class ShowFido(internal val options: String) : State()
        internal data class Success(internal val message: String) : State()
        internal data class Failure(internal val cause: Throwable) : State()
    }

    private val _enrolmentState: MutableStateFlow<State?> = MutableStateFlow(null)
    internal val enrolmentState: StateFlow<State?> = _enrolmentState.asStateFlow()

    internal lateinit var enrollmentParams: OwnIdEnrollmentParams

    init {
        OwnIdInternalLogger.logD(this, "init", "Invoked")
    }

    @MainThread
    internal fun onContinueClick() {
        _enrolmentState.value = State.Busy

        sendMetric(Metric.EventType.Click, "Clicked Enroll Device")

        viewModelScope.launch {
            runCatching {
                OwnIdEnrollmentNetworkHelper.getEnrollmentOptions(
                    enrollmentParams.ownIdCore, enrollmentParams.loginId.value, enrollmentParams.displayName
                )
            }
                .mapCatching { options -> adjustEnrollmentOptions(options) }
                .onFailure { _enrolmentState.value = State.Failure(it) }
                .onSuccess {
                    sendMetric(Metric.EventType.Track, "[Device Enrollment] - FIDO: About To Execute")
                    _enrolmentState.value = State.ShowFido(it)
                }
        }
    }

    @MainThread
    internal fun onSkipClick() {
        sendMetric(Metric.EventType.Click, "Clicked Not Now")

        viewModelScope.launch {
            runCatching { enrollmentParams.ownIdCore.repository.saveLoginIdData(enrollmentParams.loginId.createData()) }
        }
        _enrolmentState.value = State.Failure(OwnIdEnrollmentSkipped)
    }

    @MainThread
    internal fun onCancel() {
        sendMetric(Metric.EventType.Click, "Clicked Close")

        _enrolmentState.value = State.Failure(OwnIdEnrollmentSkipped)
    }

    @MainThread
    internal fun onFidoResult(result: Result<String>) {
        result.onSuccess { fidoCreateJson ->
            sendMetric(Metric.EventType.Track, "[Device Enrollment] - FIDO: Execution Completed Successfully")

            viewModelScope.launch {
                runCatching {
                    OwnIdEnrollmentNetworkHelper.sendEnrollmentResult(enrollmentParams.ownIdCore, enrollmentParams.token, fidoCreateJson)
                }
                    .onFailure { _enrolmentState.value = State.Failure(it) }
                    .onSuccess { _enrolmentState.value = State.Success(it) }
            }
        }

        result.onFailure {
            sendMetric(Metric.EventType.Error, "[Device Enrollment] - FIDO: Execution Did Not Complete", it.message)

            val cause = if (it is CreateCredentialException) OwnIdException(it.toString()) else it
            _enrolmentState.value = State.Failure(cause)
        }
    }

    @MainThread
    internal fun sendMetric(type: Metric.EventType, action: String, errorMessage: String? = null) {
        if (::enrollmentParams.isInitialized.not()) return

        enrollmentParams.ownIdCore.eventsService.sendMetric(
            category = Metric.Category.General,
            type = type,
            action = action,
            source = "Device Enrollment Modal",
            errorMessage = errorMessage
        )
    }

    @CallSuper
    override fun onCleared() {
        super.onCleared()
        OwnIdInternalLogger.logD(this, "onCleared", "Invoked")
    }

    @Throws(JSONException::class)
    @VisibleForTesting
    public fun adjustEnrollmentOptions(options: String): String {
        val fidoOptions = JSONObject(options)

        val challenge = fidoOptions.getString("challenge").encodeToByteArray().toBase64UrlSafeNoPadding()
        fidoOptions.put("challenge", challenge)

        val userObject = fidoOptions.getJSONObject("user")
        if (userObject.has("id").not()) {
            userObject.put("id", Random.nextBytes(32).toBase64UrlSafeNoPadding())
            fidoOptions.put("user", userObject)
        }

        if (fidoOptions.has("timeout").not()) {
            fidoOptions.put("timeout", 2 * 60 * 1000)
        }

        if (fidoOptions.has("attestation").not()) {
            fidoOptions.put("attestation", "none")
        }

        val authenticatorSelection = fidoOptions.getJSONObject("authenticatorSelection")

        if (authenticatorSelection.has("authenticatorAttachment").not()) {
            authenticatorSelection.put("authenticatorAttachment", "platform")
        }
        if (authenticatorSelection.has("userVerification").not()) {
            authenticatorSelection.put("userVerification", "required")
        }
        if (authenticatorSelection.has("requireResidentKey").not()) {
            authenticatorSelection.put("requireResidentKey", false)
        }
        if (authenticatorSelection.has("residentKey").not()) {
            authenticatorSelection.put("residentKey", "preferred")
        }

        fidoOptions.put("authenticatorSelection", authenticatorSelection)

        return fidoOptions.toString()
    }
}