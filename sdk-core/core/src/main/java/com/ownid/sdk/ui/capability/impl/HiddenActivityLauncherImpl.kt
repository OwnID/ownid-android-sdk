package com.ownid.sdk.ui.capability.impl

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.ownid.sdk.capability.UiContextProvider
import com.ownid.sdk.logger.OwnIdLogRouter
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.ui.OwnIdHiddenActivity
import com.ownid.sdk.ui.capability.HiddenActivityLauncher

/**
 * Default hidden-activity launcher used by SDK-owned UI and WebBridge presentation.
 *
 * Launch requests are marshalled to the main thread, guarded by [OwnIdHiddenActivity.LaunchLock], and reported back
 * through the supplied `onShowError` callback when startup or activity-result delivery fails.
 */
@InternalOwnIdAPI
internal class HiddenActivityLauncherImpl(
    private val uiContextProvider: () -> UiContextProvider,
    private val logger: OwnIdLogRouter?
) : HiddenActivityLauncher {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun launchActivityForResult(
        intentProvider: (Context) -> Intent,
        onShowError: (error: Throwable?, details: String?) -> Unit
    ) {
        fun activityLauncher() {
            if (!OwnIdHiddenActivity.LaunchLock.tryStart()) {
                onShowError(null, "Another launch is already in progress.")
                return
            }

            try {
                val hostContext = uiContextProvider.invoke().current()
                val parentComponentActivity = hostContext.findComponentActivity()
                val context = parentComponentActivity ?: hostContext.applicationContext ?: hostContext

                val intent = intentProvider(context)
                intent.putExtra(OwnIdHiddenActivity.RESULT_RECEIVER_KEY, object : ResultReceiver(mainHandler) {
                    override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                        if (resultCode != Activity.RESULT_OK) {
                            onShowError(null, resultData?.getString(OwnIdHiddenActivity.CLOSE_REASON_KEY))
                        }
                        OwnIdHiddenActivity.LaunchLock.resetStart()
                    }
                })

                if (context !is ComponentActivity) {
                    logger?.logW(this, "launchActivityForResult", "Context is not a ComponentActivity.")
                }

                if (parentComponentActivity == null || !intent.getBooleanExtra(OwnIdHiddenActivity.PREPARE_IME_HANDOFF_BEFORE_LAUNCH_KEY, false)) {
                    context.startActivity(intent)
                    return
                }

                launchActivityAfterImeHandoff(parentComponentActivity, context, intent, onShowError)
            } catch (cause: Exception) {
                logger?.logW(this, "launchActivityForResult", "Failed to start activity: ${cause.message}", cause)
                OwnIdHiddenActivity.LaunchLock.resetStart()
                onShowError(cause, "Failed to launch intent: ${cause.message}")
            }
        }

        if (Looper.myLooper() == Looper.getMainLooper()) activityLauncher() else mainHandler.post { activityLauncher() }
    }

    private fun launchActivityAfterImeHandoff(
        parentComponentActivity: ComponentActivity,
        context: Context,
        intent: Intent,
        onShowError: (error: Throwable?, details: String?) -> Unit
    ) {
        val decorView = parentComponentActivity.window.decorView
        parentComponentActivity.currentFocus?.clearFocus()
        decorView.clearFocus()
        WindowCompat.getInsetsController(parentComponentActivity.window, decorView).hide(WindowInsetsCompat.Type.ime())

        if (ViewCompat.getRootWindowInsets(decorView)?.isVisible(WindowInsetsCompat.Type.ime()) != true) {
            context.startActivity(intent)
            return
        }

        val viewTreeObserver = decorView.viewTreeObserver
        var started = false
        var globalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null
        lateinit var timeoutRunnable: Runnable

        timeoutRunnable = Runnable {
            if (started) return@Runnable
            started = true
            mainHandler.removeCallbacks(timeoutRunnable)
            globalLayoutListener?.let { listener ->
                if (viewTreeObserver.isAlive) viewTreeObserver.removeOnGlobalLayoutListener(listener)
            }
            try {
                context.startActivity(intent)
            } catch (cause: Exception) {
                logger?.logW(this, "launchActivityForResult", "Failed to start activity: ${cause.message}", cause)
                OwnIdHiddenActivity.LaunchLock.resetStart()
                onShowError(cause, "Failed to launch intent: ${cause.message}")
            }
        }

        globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            if (ViewCompat.getRootWindowInsets(decorView)?.isVisible(WindowInsetsCompat.Type.ime()) != true) {
                timeoutRunnable.run()
            }
        }
        viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)

        if (ViewCompat.getRootWindowInsets(decorView)?.isVisible(WindowInsetsCompat.Type.ime()) != true) {
            timeoutRunnable.run()
            return
        }
        mainHandler.postDelayed(timeoutRunnable, 250L)
    }

    private fun Context.findComponentActivity(): ComponentActivity? = when (this) {
        is ComponentActivity -> this
        is ContextWrapper -> baseContext.findComponentActivity()
        else -> null
    }
}
