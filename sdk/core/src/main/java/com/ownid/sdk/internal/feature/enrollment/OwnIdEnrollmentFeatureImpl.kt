package com.ownid.sdk.internal.feature.enrollment

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.RestrictTo
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.publickeycredential.CreatePublicKeyCredentialDomException
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.ownid.sdk.InstanceName
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnId
import com.ownid.sdk.OwnIdCoreImpl
import com.ownid.sdk.OwnIdInstance
import com.ownid.sdk.R
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.internal.component.OwnIdInternalLogger
import com.ownid.sdk.internal.component.events.Metric
import com.ownid.sdk.internal.component.locale.OwnIdLocaleKey
import com.ownid.sdk.internal.feature.OwnIdActivity
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.coroutines.cancellation.CancellationException

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class OwnIdEnrollmentFeatureImpl : OwnIdEnrollmentFeature {

    internal companion object {
        private const val KEY_ENROLLMENT_INTENT = "com.ownid.sdk.internal.intent.KEY_ENROLLMENT_INTENT"
        private const val KEY_INSTANCE_NAME = "com.ownid.sdk.internal.intent.KEY_INSTANCE_NAME"
        private const val KEY_LOGIN_ID = "com.ownid.sdk.internal.intent.KEY_LOGIN_ID"
        private const val KEY_FIDO_OPTIONS = "com.ownid.sdk.internal.intent.KEY_FIDO_OPTIONS"
        private const val KEY_TOKEN = "com.ownid.sdk.internal.intent.KEY_TOKEN"

        private const val KEY_ENROLLMENT_STARTED = "com.ownid.sdk.internal.intent.KEY_ENROLLMENT_STARTED"
        private const val KEY_FIDO_REQUESTED = "com.ownid.sdk.internal.intent.KEY_FIDO_REQUESTED"

        internal fun createIntent(
            context: Context, instanceName: InstanceName, loginId: String, fidoOptions: String, token: String
        ): Intent =
            Intent(context, OwnIdActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                .putExtra(KEY_ENROLLMENT_INTENT, true)
                .putExtra(KEY_INSTANCE_NAME, instanceName.toString())
                .putExtra(KEY_LOGIN_ID, loginId)
                .putExtra(KEY_FIDO_OPTIONS, fidoOptions)
                .putExtra(KEY_TOKEN, token)

        internal fun isThisFeature(intent: Intent): Boolean = intent.getBooleanExtra(KEY_ENROLLMENT_INTENT, false)
    }

    private var enrollmentStarted: Boolean = false
    private var fidoRequested: Boolean = false

    override fun onCreate(activity: AppCompatActivity, savedInstanceState: Bundle?) {
        OwnIdInternalLogger.logD(this, "onCreate", "Invoked")

        enrollmentStarted = savedInstanceState?.getBoolean(KEY_ENROLLMENT_STARTED) ?: false
        fidoRequested = savedInstanceState?.getBoolean(KEY_FIDO_REQUESTED) ?: false

        val enrollmentParams = runCatching {
            val instanceName = InstanceName(requireNotNull(activity.intent.getStringExtra(KEY_INSTANCE_NAME)))
            val ownIdCore = OwnId.getInstanceOrThrow<OwnIdInstance>(instanceName).ownIdCore as OwnIdCoreImpl
            val loginId = requireNotNull(activity.intent.getStringExtra(KEY_LOGIN_ID))
            val fidoOptions = requireNotNull(activity.intent.getStringExtra(KEY_FIDO_OPTIONS))
            val token = requireNotNull(activity.intent.getStringExtra(KEY_TOKEN))
            OwnIdEnrollmentParams(ownIdCore, loginId, fidoOptions, token)
        }.getOrElse {
            OwnIdInternalLogger.logW(this, "onCreate", it.message, it)
            sendResult(activity, Result.failure(OwnIdException("OwnIdEnrollmentFeature.onCreate: ${it.message}", it)))
            return
        }

        activity.resources.configuration.setLocale(enrollmentParams.ownIdCore.localeService.currentOwnIdLocale.locale)

        val viewModel = ViewModelProvider(activity).get(OwnIdEnrollmentViewModelInt::class.java)

        viewModel.enrolmentState
            .filterNotNull()
            .onEach { state ->
                OwnIdInternalLogger.logD(this@OwnIdEnrollmentFeatureImpl, "onCreate", "State: $state")
                when (state) {
                    OwnIdEnrollmentViewModelInt.State.Busy -> Unit

                    is OwnIdEnrollmentViewModelInt.State.ShowFido -> {
                        if (fidoRequested.not()) {
                            fidoRequested = true
                            val fidoResult = runFidoCreate(activity, state.options)
                            viewModel.onFidoResult(fidoResult)
                        }
                    }

                    is OwnIdEnrollmentViewModelInt.State.Success -> {
                        viewModel.sendMetric(Metric.EventType.Track, "Completed Device Enrollment")
                        val toastText = enrollmentParams.ownIdCore.localeService.getString(activity, LocaleKeys.getToastSuccess())
                        Toast.makeText(activity, toastText, Toast.LENGTH_LONG).show()
                        sendResult(activity, Result.success(state.message))
                    }

                    is OwnIdEnrollmentViewModelInt.State.Failure -> {
                        if (state.cause is OwnIdEnrollmentSkipped) {
                            viewModel.sendMetric(Metric.EventType.Track, "Skipped Device Enrollment")
                        } else {
                            OwnIdInternalLogger.logW(this, "enrolmentState.onEach.Failure", state.cause.message, state.cause)
                            viewModel.sendMetric(Metric.EventType.Track, "Failed Device Enrollment")
                        }
                        sendResult(activity, Result.failure(state.cause))
                    }
                }
            }
            .catch {
                sendResult(activity, Result.failure(OwnIdException("enrolmentState.onEach.catch: ${it.message}", it)))
            }
            .launchIn(activity.lifecycleScope)

        if (enrollmentStarted.not()) {
            enrollmentStarted = true
            viewModel.enrollmentParams = enrollmentParams
            OwnIdEnrolmentFragment.show(activity.supportFragmentManager)
            viewModel.sendMetric(Metric.EventType.Track, "Viewed Device Enrollment")
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        OwnIdInternalLogger.logD(this, "onSaveInstanceState", "Invoked")
        outState.putBoolean(KEY_ENROLLMENT_STARTED, enrollmentStarted)
        outState.putBoolean(KEY_FIDO_REQUESTED, fidoRequested)
    }

    @Suppress("DEPRECATION")
    override fun sendResult(activity: AppCompatActivity, result: Result<String>) {
        OwnIdInternalLogger.logD(this, "sendResult", result.toString())
        activity.setResult(AppCompatActivity.RESULT_OK, Intent().putExtra(OwnIdActivity.KEY_RESULT, result))
        activity.finish()
        activity.overridePendingTransition(0, 0)
    }

    @SuppressLint("PublicKeyCredential")
    private suspend fun runFidoCreate(context: Context, createOptions: String): Result<String> = runCatching {
        val request = CreatePublicKeyCredentialRequest(createOptions, preferImmediatelyAvailableCredentials = true)

        val result = CredentialManager.create(context).createCredential(context, request)

        if (result is CreatePublicKeyCredentialResponse) result.registrationResponseJson
        else throw OwnIdException("CreateCredentialResponse unsupported result type: ${result.type}")
    }.onFailure { e ->
        when (e) {
            is CreatePublicKeyCredentialDomException -> OwnIdInternalLogger.logI(this, "runFidoCreate", e.domError.type, e)
            is CreateCredentialException -> OwnIdInternalLogger.logI(this, "runFidoCreate", e.errorMessage?.toString() ?: e.message, e)
            is CancellationException -> Unit
            is OwnIdException -> Unit
            else -> OwnIdInternalLogger.logW(this, "runFidoCreate", e.message, e)
        }
    }

    private object LocaleKeys {
        private val prefix = arrayOf("enrollCredential")

        @JvmStatic
        internal fun getToastSuccess() = OwnIdLocaleKey(*prefix, "toast", "success")
            .withFallback(R.string.com_ownid_sdk_internal_ui_enrollment_toast_success)
    }
}