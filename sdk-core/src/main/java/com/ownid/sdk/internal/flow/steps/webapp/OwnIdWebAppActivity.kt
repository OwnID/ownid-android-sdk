package com.ownid.sdk.internal.flow.steps.webapp

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.internal.OwnIdInternalLogger

/**
 * Activity receives the OwnID Web Application url in Intent.
 * It starts a Chrome custom Tab or standalone Browser with OwnID Web Application and waits for
 * intent as [Intent] from [OwnIdWebAppRedirectActivity]. Once the redirect intent is received, it sets intent data
 * as result [setResult] and finishes itself.
 */
@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class OwnIdWebAppActivity : AppCompatActivity() {

    internal companion object {
        internal const val KEY_RESULT_REGISTRY = "com.ownid.sdk.internal.intent.KEY_RESULT_REGISTRY"
        internal const val KEY_RESULT = "com.ownid.sdk.internal.intent.KEY_RESULT"

        private const val KEY_WEB_APP_URI = "com.ownid.sdk.internal.intent.KEY_WEB_APP_URI"
        private const val KEY_WEB_APP_LAUNCHED = "com.ownid.sdk.internal.intent.KEY_WEB_APP_LAUNCHED"

        private fun createBaseIntent(context: Context): Intent =
            Intent(context, OwnIdWebAppActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)

        @JvmSynthetic
        internal fun createIntent(context: Context, webAppUri: String): Intent =
            createBaseIntent(context).putExtra(KEY_WEB_APP_URI, webAppUri)

        @JvmSynthetic
        internal fun createRedirectIntent(context: Context, redirectUri: Uri?): Intent =
            createBaseIntent(context)
                .setData(redirectUri)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    @VisibleForTesting
    internal var isWebAppLaunched: Boolean = false

    @VisibleForTesting
    internal var webAppUri: String? = null

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        OwnIdInternalLogger.logD(this, "onCreate", "Invoked")

        isWebAppLaunched = (savedInstanceState ?: intent.extras)?.getBoolean(KEY_WEB_APP_LAUNCHED) ?: false

        if (isWebAppLaunched.not()) {
            webAppUri = intent.extras?.getString(KEY_WEB_APP_URI) ?: run {
                OwnIdInternalLogger.logE(this, "onCreate", "No WebApp Uri set")
                sendResult(Result.failure(OwnIdException("OwnIdWebAppActivity.onCreate: No WebApp Uri set")))
                return
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        OwnIdInternalLogger.logD(this, "onNewIntent", intent.toString())
        setIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        val redirectUri = intent.data
        OwnIdInternalLogger.logD(this, "onResume", "redirectUri: $redirectUri")

        if (isWebAppLaunched) {
            sendResult(Result.success(redirectUri?.toString()))
            return
        }

        runCatching {
            isWebAppLaunched = true
            BrowserHelper.launchUri(this, webAppUri!!)
        }.onFailure {
            OwnIdInternalLogger.logE(this, "onResume", it.message, it)
            sendResult(Result.failure(OwnIdException("OwnIdWebAppActivity.onResume: ${it.message}", it)))
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_WEB_APP_LAUNCHED, isWebAppLaunched)
    }

    @Suppress("DEPRECATION")
    private fun sendResult(result: Result<String?>) {
        OwnIdInternalLogger.logD(this, "sendResult", "result: $result")
        setResult(Activity.RESULT_OK, Intent().putExtra(KEY_RESULT, result))
        finish()
        overridePendingTransition(0, 0)
    }
}