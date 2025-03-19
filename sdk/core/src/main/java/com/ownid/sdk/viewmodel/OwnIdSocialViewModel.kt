package com.ownid.sdk.viewmodel

import android.app.Activity
import android.content.Context
import android.os.Looper
import androidx.activity.result.ActivityResult
import androidx.annotation.MainThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ownid.sdk.AuthMethod
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnIdInstance
import com.ownid.sdk.exception.OwnIdCancellationException
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.internal.component.OwnIdInternalLogger
import com.ownid.sdk.internal.component.events.Metadata
import com.ownid.sdk.internal.component.events.Metric
import com.ownid.sdk.internal.feature.OwnIdActivity
import com.ownid.sdk.internal.feature.social.OwnIdSocialFeature
import com.ownid.sdk.internal.feature.social.OwnIdSocialNetworkHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(InternalOwnIdAPI::class)
public class OwnIdSocialViewModel(ownIdInstance: OwnIdInstance) : OwnIdBaseViewModel(ownIdInstance) {

    /**
     * Represents the result of a social login.
     */
    public sealed class State {

        /**
         * Successful login.
         *
         * @property accessToken The OwnID access token.
         * @property sessionPayload Optional Identity Platform payload
         */
        public class LoggedIn(public val accessToken: String, public val sessionPayload: String?) : State()

        /**
         * Failed login.
         *
         * @property cause The underlying error.
         */
        public class Error(public val cause: OwnIdException) : State()

        override fun toString(): String = "OwnIdSocialViewModel.State.${javaClass.simpleName}"
    }

    /**
     * Factory to create an [OwnIdSocialViewModel] instance.
     */
    @Suppress("UNCHECKED_CAST")
    @InternalOwnIdAPI
    public class Factory(private val ownIdInstance: OwnIdInstance) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = OwnIdSocialViewModel(ownIdInstance) as T
    }

    @InternalOwnIdAPI
    override val resultRegistryKey: String = "com.ownid.sdk.result.registry.SOCIAL"

    @InternalOwnIdAPI
    private val _socialResultFlow: MutableStateFlow<State?> = MutableStateFlow(null)

    /**
     * Emits the result of social login.
     *
     * Observe for a [State.LoggedIn] on success or [State.Error] on failure.
     */
    public val socialResultFlow: StateFlow<State?> = _socialResultFlow.asStateFlow()

    private var challengeId: String? = null

    @InternalOwnIdAPI
    @Suppress("DEPRECATION", "UNCHECKED_CAST")
    override fun onActivityResult(result: ActivityResult) {
        OwnIdInternalLogger.logD(this, "onActivityResult", result.toString())
        runCatching {
            if (result.resultCode != Activity.RESULT_OK) throw OwnIdCancellationException("Social activity canceled [${result.resultCode}]")
            (result.data?.getSerializableExtra(OwnIdActivity.KEY_RESULT) as Result<Pair<String, String>>).getOrThrow()
        }.let { endSignIn(it) }
    }

    /**
     * Initiates the Google social login flow.
     *
     * The result is delivered via [socialResultFlow].
     *
     * @param context Android context.
     * @throws IllegalStateException if called off the main thread.
     */
    @MainThread
    @OptIn(InternalOwnIdAPI::class)
    @Throws(IllegalStateException::class)
    public fun startSignInWithGoogle(context: Context) {
        check(Looper.getMainLooper().isCurrentThread) { "Method must be called on Android main thread" }

        challengeId = null
        _socialResultFlow.value = null

        ownIdCore.eventsService.sendMetric(
            category = Metric.Category.General,
            type = Metric.EventType.Track,
            action = "[Social Login] - Google: Execution Start",
            source = "Social Feature"
        )

        viewModelScope.launch {
            try {
                ownIdCore.configurationService.ensureConfigurationSet()
                ensureActive()
                ownIdCore.localeService.updateCurrentOwnIdLocale(context)

                val challenge = withContext(Dispatchers.IO) {
                    OwnIdSocialNetworkHelper.startOidcChallenge(
                        ownIdCore,
                        OwnIdSocialFeature.Provider.GOOGLE,
                        OwnIdSocialFeature.OauthResponseType.ID_TOKEN
                    )
                }
                challengeId = challenge.challengeId
                ensureActive()

                val intent = OwnIdSocialFeature.createIntent(context, challenge.challengeId, challenge.clientId)
                launchActivity(intent)
            } catch (cause: Throwable) {
                if (cause is CancellationException) {
                    endSignInWithError(OwnIdCancellationException("Sign in canceled", cause))
                    throw cause
                } else {
                    OwnIdInternalLogger.logW(this, "startSignInWithGoogle", cause.message, cause)
                    endSignInWithError(OwnIdException.map("Sign in failed: ${cause.message}", cause))
                }
            }
        }
    }

    private fun endSignIn(result: Result<Pair<String, String>>) {
        OwnIdInternalLogger.logD(this, "endSignIn", result.toString())
        result
            .onFailure { endSignInWithError(it) }
            .onSuccess { (challengeId, idToken) ->
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val (aToken, loginId) = OwnIdSocialNetworkHelper.completeOidcChallengeWithIdToken(ownIdCore, challengeId, idToken)
                        ensureActive()
                        val (accessToken, payload) = OwnIdSocialNetworkHelper.doLogin(ownIdCore, aToken)
                        ownIdCore.eventsService.sendMetric(
                            category = Metric.Category.General,
                            type = Metric.EventType.Track,
                            action = "[Social Login] - Google: Execution Complete",
                            source = "Social Feature"
                        )
                        loginId?.let { saveLoginId(it, "social-google") }
                        _socialResultFlow.value = State.LoggedIn(accessToken, payload)
                        this@OwnIdSocialViewModel.challengeId = null

                        ownIdCore.eventsService.sendMetric(
                            category = Metric.Category.General,
                            type = Metric.EventType.Track,
                            action = "User is Logged in",
                            source = "Social Feature",
                            metadata = Metadata(authType = "Google")
                        )
                    } catch (error: Throwable) {
                        when (error) {
                            is CancellationException -> {
                                endSignInWithError(OwnIdCancellationException("Sign in canceled", error))
                                throw error
                            }

                            else -> {
                                OwnIdInternalLogger.logW(this@OwnIdSocialViewModel, "endSignIn", "Sign in failed: ${error.message}", error)
                                endSignInWithError(error)
                            }
                        }
                    }
                }
            }
    }

    private fun endSignInWithError(error: Throwable) {
        OwnIdInternalLogger.logD(this, "endSignInWithError", error.message)

        if (error is OwnIdCancellationException && challengeId != null) {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { OwnIdSocialNetworkHelper.cancelOidcChallenge(ownIdCore, challengeId!!) }
            }
        }

        val cause = error as? OwnIdException ?: OwnIdException.map("Sign in failed: ${error.message}", error)
        sendMetric(cause)
        _socialResultFlow.value = State.Error(cause)
        challengeId = null
    }

    private fun sendMetric(cause: OwnIdException) {
        when (cause) {
            is OwnIdCancellationException -> {
                ownIdCore.eventsService.sendMetric(
                    category = Metric.Category.General,
                    type = Metric.EventType.Track,
                    action = "[Social Login] - Google: Execution Cancelled",
                    source = "Social Feature"
                )
            }

            else -> {
                ownIdCore.eventsService.sendMetric(
                    category = Metric.Category.General,
                    type = Metric.EventType.Error,
                    action = "[Social Login] - Google: Execution Did Not Complete",
                    source = "Social Feature",
                    errorMessage = cause.message
                )
            }
        }
    }

    private suspend fun saveLoginId(loginId: String, authType: String) {
        withContext(NonCancellable) {
            val authMethod = AuthMethod.fromString(authType)
            runCatching { ownIdCore.repository.saveLoginId(loginId, authMethod) }
            val loginIdData = ownIdCore.repository.getLoginIdData(loginId)
            runCatching { ownIdCore.repository.saveLoginIdData(loginId, loginIdData.copy(authMethod = authMethod)) }
        }
    }
}