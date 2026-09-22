package com.ownid.sdk.ui.capability.impl

import android.app.Activity
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.ownid.sdk.capability.UiContextProvider
import com.ownid.sdk.logger.OwnIdLogRouter
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.ui.OwnIdActivityHostProtocol
import com.ownid.sdk.ui.capability.HiddenActivityLauncher
import kotlinx.coroutines.Job

/**
 * Default launcher for SDK-owned host Activities from a resolved Activity.
 *
 * Launch requests are marshalled to the main thread, guarded by one process-wide lock, and reported back through the
 * supplied `onShowError` callback when host resolution or startup fails, the selected window is not visible, creation
 * is not acknowledged within three seconds of uptime, or the host returns an error result. Accepted hosts retain the
 * gate until final destruction.
 */
@InternalOwnIdAPI
internal class HiddenActivityLauncherImpl(
    private val uiContextProvider: () -> UiContextProvider,
    private val instanceJob: Job,
    private val logger: OwnIdLogRouter?
) : HiddenActivityLauncher {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun launchActivityForResult(
        intentProvider: (Activity) -> Intent,
        onShowError: (error: Throwable?, details: String?) -> Unit
    ) {
        fun activityLauncher() {
            val attempt = OwnIdActivityHostProtocol.LaunchLock.tryStart(instanceJob, logger, onShowError) ?: return

            try {
                val provider = uiContextProvider.invoke()
                if (!attempt.canStart()) return
                val resolution = provider.current()
                if (!attempt.canStart()) return
                val sourceActivity = when (resolution) {
                    is UiContextProvider.Result.Success -> resolution.activity
                    is UiContextProvider.Result.Failure -> {
                        attempt.fail(resolution.error, "Failed to resolve UI Activity: ${resolution.error.message}")
                        return
                    }
                }

                val intent = intentProvider(sourceActivity)
                if (!attempt.canStart()) return
                attempt.attach(intent)

                if (!intent.getBooleanExtra(OwnIdActivityHostProtocol.PREPARE_IME_HANDOFF_BEFORE_LAUNCH_KEY, false)) {
                    if (validateVisibleSource(sourceActivity, attempt) && attempt.canStart()) {
                        sourceActivity.startActivity(intent)
                    }
                    return
                }

                launchActivityAfterImeHandoff(provider, sourceActivity, intent, attempt)
            } catch (cause: Exception) {
                logger?.logW(this, "launchActivityForResult", "Failed to start activity: ${cause.message}", cause)
                if (attempt.canStart()) attempt.fail(cause, "Failed to launch intent: ${cause.message}")
            }
        }

        if (Looper.myLooper() == Looper.getMainLooper()) activityLauncher() else mainHandler.post { activityLauncher() }
    }

    private fun validateVisibleSource(activity: Activity, attempt: OwnIdActivityHostProtocol.LaunchLock.Attempt): Boolean {
        if (!attempt.canStart()) return false
        val decorView = activity.window?.peekDecorView()
        if (activity.isFinishing || activity.isDestroyed || decorView?.isAttachedToWindow != true || decorView.windowVisibility != View.VISIBLE) {
            val error = IllegalStateException("The selected Activity has no visible window")
            attempt.fail(error, "Failed to launch SDK UI: ${error.message}")
            return false
        }
        return true
    }

    /** Waits for a visible source IME to hide, with a bounded fallback if no insets update arrives. */
    private fun launchActivityAfterImeHandoff(
        provider: UiContextProvider,
        sourceActivity: Activity,
        intent: Intent,
        attempt: OwnIdActivityHostProtocol.LaunchLock.Attempt
    ) {
        if (!validateVisibleSource(sourceActivity, attempt)) return
        val window = sourceActivity.window
        val decorView = window?.peekDecorView()
        if (window == null || decorView == null || ViewCompat.getRootWindowInsets(decorView)?.isVisible(WindowInsetsCompat.Type.ime()) != true) {
            if (validateVisibleSource(sourceActivity, attempt) && attempt.canStart()) {
                sourceActivity.startActivity(intent)
            }
            return
        }

        if (!attempt.canStart()) return
        val currentFocus = sourceActivity.currentFocus
        if (!attempt.canStart()) return
        currentFocus?.clearFocus()
        if (!attempt.canStart()) return
        decorView.clearFocus()
        if (!attempt.canStart()) return
        val insetsController = WindowCompat.getInsetsController(window, decorView)
        if (!attempt.canStart()) return
        insetsController.hide(WindowInsetsCompat.Type.ime())
        if (!attempt.canStart()) return

        if (ViewCompat.getRootWindowInsets(decorView)?.isVisible(WindowInsetsCompat.Type.ime()) != true) {
            if (validateVisibleSource(sourceActivity, attempt) && attempt.canStart()) {
                sourceActivity.startActivity(intent)
            }
            return
        }

        val viewTreeObserver = decorView.viewTreeObserver
        if (!viewTreeObserver.isAlive) {
            if (validateVisibleSource(sourceActivity, attempt) && attempt.canStart()) {
                sourceActivity.startActivity(intent)
            }
            return
        }
        var waitFinished = false
        lateinit var globalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener
        lateinit var completeHandoff: Runnable

        fun finishWait(): Boolean {
            if (waitFinished) return false
            waitFinished = true
            mainHandler.removeCallbacks(completeHandoff)
            if (viewTreeObserver.isAlive) viewTreeObserver.removeOnGlobalLayoutListener(globalLayoutListener)
            attempt.cancelDeferredLaunch = null
            return true
        }

        completeHandoff = Runnable {
            if (!finishWait() || !attempt.canStart()) return@Runnable
            try {
                val resolution = provider.current()
                if (!attempt.canStart()) return@Runnable
                when (resolution) {
                    is UiContextProvider.Result.Success -> {
                        val finalActivity = resolution.activity
                        if (validateVisibleSource(finalActivity, attempt) && attempt.canStart()) {
                            finalActivity.startActivity(intent)
                        }
                    }

                    is UiContextProvider.Result.Failure ->
                        attempt.fail(resolution.error, "Failed to resolve UI Activity: ${resolution.error.message}")
                }
            } catch (cause: Exception) {
                logger?.logW(this, "launchActivityForResult", "Failed to start activity: ${cause.message}", cause)
                if (attempt.canStart()) attempt.fail(cause, "Failed to launch intent: ${cause.message}")
            }
        }

        globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            if (ViewCompat.getRootWindowInsets(decorView)?.isVisible(WindowInsetsCompat.Type.ime()) != true) {
                completeHandoff.run()
            }
        }
        if (!attempt.canStart()) return
        attempt.cancelDeferredLaunch = {
            finishWait()
        }
        viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)

        if (!attempt.canStart()) return

        if (ViewCompat.getRootWindowInsets(decorView)?.isVisible(WindowInsetsCompat.Type.ime()) != true) {
            finishWait()
            if (validateVisibleSource(sourceActivity, attempt) && attempt.canStart()) {
                sourceActivity.startActivity(intent)
            }
            return
        }
        if (!mainHandler.postDelayed(completeHandoff, 250L)) {
            finishWait()
            if (validateVisibleSource(sourceActivity, attempt) && attempt.canStart()) {
                sourceActivity.startActivity(intent)
            }
        }
    }
}
