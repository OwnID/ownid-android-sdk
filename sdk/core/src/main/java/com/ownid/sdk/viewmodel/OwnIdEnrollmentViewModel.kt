package com.ownid.sdk.viewmodel

import android.app.Activity
import android.content.Context
import android.os.Looper
import androidx.activity.result.ActivityResult
import androidx.annotation.MainThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnIdCallback
import com.ownid.sdk.OwnIdInstance
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.internal.OwnIdLoginId
import com.ownid.sdk.internal.await
import com.ownid.sdk.internal.component.OwnIdInternalLogger
import com.ownid.sdk.internal.feature.OwnIdActivity
import com.ownid.sdk.internal.feature.enrollment.OwnIdEnrollmentFeature
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@OptIn(InternalOwnIdAPI::class)
public class OwnIdEnrollmentViewModel(ownIdInstance: OwnIdInstance) : OwnIdBaseViewModel(ownIdInstance) {

    @Suppress("UNCHECKED_CAST")
    @InternalOwnIdAPI
    public class Factory(private val ownIdInstance: OwnIdInstance) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = OwnIdEnrollmentViewModel(ownIdInstance) as T
    }

    @InternalOwnIdAPI
    override val resultRegistryKey: String = "com.ownid.sdk.result.registry.ENROLLMENT"

    @InternalOwnIdAPI
    private val _enrollmentResultFlow: MutableStateFlow<Result<String>?> = MutableStateFlow(null)

    /**
     * Represents the status of the last enrollment request as a [StateFlow].
     *
     * Emits either a [Result.success] with a success message of type [String] or a [Result.failure] with an enrollment error.
     */
    public val enrollmentResultFlow: StateFlow<Result<String>?> = _enrollmentResultFlow.asStateFlow()

    @InternalOwnIdAPI
    @Suppress("DEPRECATION", "UNCHECKED_CAST")
    override fun onActivityResult(result: ActivityResult) {
        OwnIdInternalLogger.logD(this, "onActivityResult", result.toString())
        runCatching {
            if (result.resultCode != Activity.RESULT_OK) throw OwnIdException("Enrolment activity canceled [${result.resultCode}]")
            (result.data?.getSerializableExtra(OwnIdActivity.KEY_RESULT) as Result<String>).getOrThrow()
        }.let { endEnrolment(it) }
    }

    /**
     * Enrolls a credential with OwnID.
     * Use [enrollmentResultFlow] to observe the enrollment request status.
     *
     * @param context               Android [Context]
     * @param loginId               The user's login ID.
     * @param authToken             The user's authentication token.
     * @param force                 (optional) If set to true, the enrollment will be forced even if the enrollment request timeout (7 days) has not passed. Defaults to false.
     * @throws IllegalStateException if called on a non-main thread.
     */
    @MainThread
    @Throws(IllegalStateException::class)
    public fun enrollCredential(context: Context, loginId: String, authToken: String, force: Boolean = false) {
        enrollCredential(context, { it.invoke(Result.success(loginId)) }, { it.invoke(Result.success(authToken)) }, force)
    }

    /**
     * Enrolls a credential with OwnID.
     * Use [enrollmentResultFlow] to observe the enrollment request status.
     *
     * @param context               Android [Context]
     * @param loginIdProvider       A function that provides the user's login ID. This function should invoke the provided [OwnIdCallback] with the login ID.
     * @param authTokenProvider     A function that provides the user's authentication token. It should invoke the provided [OwnIdCallback] with the authentication token.
     * @param force                 (optional) If set to true, the enrollment will be forced even if the enrollment request timeout (7 days) has not passed. Defaults to false.
     * @throws IllegalStateException if called on a non-main thread.
     */
    @MainThread
    @OptIn(InternalOwnIdAPI::class)
    @Throws(IllegalStateException::class)
    public fun enrollCredential(
        context: Context,
        loginIdProvider: (OwnIdCallback<String>) -> Unit,
        authTokenProvider: (OwnIdCallback<String>) -> Unit,
        force: Boolean = false
    ) {
        check(Looper.getMainLooper().isCurrentThread) { "Method must be called on Android main thread" }

        _enrollmentResultFlow.value = null

        viewModelScope.launch {
            try {
                val loginIdString = loginIdProvider.await()
                ensureActive()

                val ownIdLoginId = OwnIdLoginId(loginIdString)
                runCatching { ownIdCore.repository.saveLoginId(ownIdLoginId) }

                ownIdCore.configurationService.ensureConfigurationSet()
                ensureActive()
                ownIdCore.localeService.updateCurrentOwnIdLocale(context)

                if (ownIdCore.configuration.isFidoPossible().not()) {
                    throw OwnIdException("FIDO is not available")
                }

                ownIdCore.eventsService.setFlowLoginId(ownIdLoginId.value)

                if (force.not()) {
                    val loginIdData = ownIdCore.repository.getLoginIdData(ownIdLoginId)
                    if (loginIdData.isOwnIdLogin) throw OwnIdException("Request ignored. Login was via OwnID")
                    if (loginIdData.enrollmentTimeoutPassed().not()) throw OwnIdException("Request throttled")
                }

                val token = authTokenProvider.await()
                ensureActive()

                val displayName = ownIdLoginId.value
                val intent = OwnIdEnrollmentFeature.createIntent(context, ownIdCore.instanceName, ownIdLoginId, displayName, token)
                launchActivity(intent)
            } catch (cause: Throwable) {
                if (cause is CancellationException) {
                    endEnrolment(Result.failure(OwnIdException("Enrollment canceled")))
                    throw cause
                } else {
                    endEnrolment(Result.failure(cause))
                }
            }
        }
    }

    private fun endEnrolment(result: Result<String>) {
        OwnIdInternalLogger.logD(this, "endEnrolment", result.toString())

        ownIdCore.eventsService.setFlowLoginId(null)

        _enrollmentResultFlow.value = result
    }
}