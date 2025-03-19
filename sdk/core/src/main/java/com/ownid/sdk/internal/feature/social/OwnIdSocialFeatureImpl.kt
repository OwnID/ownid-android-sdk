package com.ownid.sdk.internal.feature.social

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.exception.OwnIdCancellationException
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.internal.component.OwnIdInternalLogger
import com.ownid.sdk.internal.feature.OwnIdActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@InternalOwnIdAPI
internal class OwnIdSocialFeatureImpl : OwnIdSocialFeature {

    internal companion object {
        private const val KEY_LOGIN_INTENT = "com.ownid.sdk.internal.social.KEY_LOGIN_INTENT"
        private const val KEY_CHALLENGE_ID = "com.ownid.sdk.internal.social.KEY_CHALLENGE_ID"
        private const val KEY_CLIENT_ID = "com.ownid.sdk.internal.social.KEY_CLIENT_ID"

        private const val KEY_LOGIN_STARTED = "com.ownid.sdk.internal.intent.KEY_LOGIN_STARTED"

        internal fun createIntent(context: Context, challengeId: String, clientId: String): Intent =
            Intent(context, OwnIdActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                .putExtra(KEY_LOGIN_INTENT, true)
                .putExtra(KEY_CHALLENGE_ID, challengeId)
                .putExtra(KEY_CLIENT_ID, clientId)


        internal fun isThisFeature(intent: Intent): Boolean =
            intent.getBooleanExtra(KEY_LOGIN_INTENT, false)
    }

    private var loginStarted: Boolean = false

    override fun onCreate(activity: AppCompatActivity, savedInstanceState: Bundle?) {
        OwnIdInternalLogger.logD(this, "onCreate", "Invoked")

        loginStarted = savedInstanceState?.getBoolean(KEY_LOGIN_STARTED) ?: false

        val challenge = runCatching {
            OwnIdSocialFeature.Challenge(
                challengeId = requireNotNull(activity.intent.getStringExtra(KEY_CHALLENGE_ID)?.ifBlank { null }),
                clientId = requireNotNull(activity.intent.getStringExtra(KEY_CLIENT_ID)?.ifBlank { null }),
                challengeUrl = null
            )
        }.getOrElse {
            OwnIdInternalLogger.logW(this, "onCreate", it.message, it)
            sendResult(activity, Result.failure(OwnIdException("OwnIdSocialFeature.onCreate: ${it.message}", it)))
            return
        }

        if (loginStarted.not()) {
            loginStarted = true

            activity.lifecycleScope.launch {
                val result = runGoogleSocialLogin(activity, challenge.clientId, challenge.challengeId)
                    .map { idToken -> challenge.challengeId to idToken }

                sendResult(activity, result)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        OwnIdInternalLogger.logD(this, "onSaveInstanceState", "Invoked")
        outState.putBoolean(KEY_LOGIN_STARTED, loginStarted)
    }

    @Suppress("DEPRECATION")
    override fun sendResult(activity: AppCompatActivity, result: Result<Pair<String, String>>) {
        OwnIdInternalLogger.logD(this, "sendResult", result.toString())
        activity.setResult(AppCompatActivity.RESULT_OK, Intent().putExtra(OwnIdActivity.KEY_RESULT, result))
        activity.finish()
        activity.overridePendingTransition(0, 0)
    }

    private suspend fun runGoogleSocialLogin(context: Context, serverClientId: String, nonce: String?): Result<String> = runCatching {
        val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(serverClientId)
            .setNonce(nonce)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInWithGoogleOption)
            .build()

        val result = CredentialManager.create(context.applicationContext).getCredential(context, request)

        when (val credential = result.credential) {
            is CustomCredential -> {
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    GoogleIdTokenCredential.createFrom(credential.data).idToken
                } else {
                    throw OwnIdException("Unsupported credential type: ${credential.type}")
                }
            }

            else -> throw OwnIdException("Unsupported credential: ${credential::class.java.name}")
        }
    }.recoverCatching { error ->
        when (error) {
            is GetCredentialCancellationException,
            is CancellationException -> throw OwnIdCancellationException("Sign in canceled: ${error.message}", error)

            else -> {
                OwnIdInternalLogger.logW(this, "runGoogleSocialLogin", error.message, error)
                throw error as? OwnIdException ?: OwnIdException(error.toString())
            }
        }
    }
}