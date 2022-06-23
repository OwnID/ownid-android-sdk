package com.ownid.sdk.internal

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.annotation.VisibleForTesting
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.exception.FlowCanceled
import com.ownid.sdk.exception.FlowExpired
import com.ownid.sdk.logD
import com.ownid.sdk.logV
import com.ownid.sdk.logW

/**
 * Activity that receives the Login/Register Intent. It restores [OwnIdRequest] from [Intent] on
 * creation, init request onResume, starts Browser to process OwnId Login/Register flow and waits for
 * [Intent] from [OwnIdRedirectActivity]. Once the redirect intent is received, it checks for [OwnIdRequest] status
 * and sets appropriate result [setResult] and finishes itself.
 *
 * Activity handles possible destroy event by saving current [OwnIdRequest] with [onSaveInstanceState]
 * and restoring it once Activity is recreated.
 *
 * App developers using this library must override the `ownIdRedirectScheme`
 * property in their `build.gradle` to specify the custom scheme that will be used for
 * the OwnID redirect. If you prefer to use https schema, then a custom intent filter should be
 * defined in your application manifest instead.
 * See [Verify Android App Links](https://developer.android.com/training/app-links/verify-site-associations)
 */
@InternalOwnIdAPI
public class OwnIdActivity : Activity() {

    internal companion object {
        internal const val KEY_REQUEST = "com.ownid.sdk.intent.KEY_REQUEST"

        @JvmStatic
        internal fun createBaseIntent(context: Context): Intent {
            return Intent(context, OwnIdActivity::class.java)
        }

        /**
         * Creates an intent to handle the browser redirect. This restores
         * the original OwnIdActivity that was created at the start of the flow.
         *
         * @param context     the package context for the app.
         * @param redirectUri the response URI, which can be empty.
         */
        @JvmStatic
        internal fun createRedirectIntent(context: Context, redirectUri: Uri?): Intent {
            return createBaseIntent(context)
                .setData(redirectUri)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
    }

    @VisibleForTesting
    internal lateinit var ownIdRequest: OwnIdRequest

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logV("onCreate")

        val stateBundle = savedInstanceState ?: intent.extras

        runCatching {
            val requestJson = stateBundle?.getString(KEY_REQUEST) ?: throw OwnIdException("Error: No stored request")
            ownIdRequest = OwnIdRequest.fromJsonString(requestJson)
            logD("onCreate", ownIdRequest)
        }.onFailure { sendErrorResult(OwnIdException.map("Error OwnIdActivity.onCreate", it)) }
    }

    override fun onResume() {
        super.onResume()
        val redirectUri = intent.data

        logD("onResume.redirectUri: $redirectUri", ownIdRequest)

        runCatching {
            when {
                ownIdRequest.isRequestStarted().not() -> ownIdRequest.initRequest {
                    mapCatching { ownIdRequest = it; it.getUriForBrowser() }
                        .onSuccess { BrowserHelper.launchUri(this@OwnIdActivity, it) }
                        .onFailure { sendErrorResult(OwnIdException.map("Error in initRequest", it)) }
                }

                redirectUri == null ->
                    sendErrorResult(FlowCanceled())

                ownIdRequest.isRedirectionValid(redirectUri).not() ->
                    sendErrorResult(OwnIdException("Wrong redirection context"))

                ownIdRequest.isRequestActive().not() ->
                    sendErrorResult(FlowExpired())

                else -> ownIdRequest.getRequestStatus {
                    onSuccess { ownIdResponse -> sendOkResult(ownIdResponse) }
                    onFailure { sendErrorResult(OwnIdException.map("Error in getRequestStatus", it)) }
                }
            }
        }
            .onFailure { sendErrorResult(OwnIdException.map("Error in onResume", it)) }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        logV("onNewIntent: $intent", ownIdRequest)

        setIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        runCatching {
            if (::ownIdRequest.isInitialized)
                outState.putString(KEY_REQUEST, ownIdRequest.toJsonString())
        }.onFailure { sendErrorResult(OwnIdException.map("onSaveInstanceState", it)) }
    }

    private fun sendErrorResult(ownIdException: OwnIdException) {
        logW("sendErrorResult: $ownIdException")

        setResult(RESULT_CANCELED, ownIdException.wrapInIntent())
        finish()
        overridePendingTransition(0, 0)
    }

    private fun sendOkResult(ownIdResponse: OwnIdResponse) {
        logD("sendOkResult", ownIdRequest)

        setResult(RESULT_OK, ownIdResponse.wrapInIntent())
        finish()
        overridePendingTransition(0, 0)
    }
}