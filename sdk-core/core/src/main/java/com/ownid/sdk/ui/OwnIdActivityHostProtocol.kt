package com.ownid.sdk.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.annotation.VisibleForTesting
import com.ownid.sdk.di.OwnIdRootDIContainer
import com.ownid.sdk.di.getOrNull
import com.ownid.sdk.logger.OwnIdLogRouter
import com.ownid.sdk.model.InstanceName
import com.ownid.sdk.model.InternalOwnIdAPI
import kotlinx.coroutines.Job
import java.util.UUID

/**
 * Shared intent, result-delivery, and launch-serialization protocol for SDK-owned host Activities.
 *
 * [OwnIdHiddenActivity] and [OwnIdWebViewActivity] use it so the SDK launcher observes one result contract and both
 * presentation paths participate in the same process-wide launch gate.
 */
@OptIn(InternalOwnIdAPI::class)
internal object OwnIdActivityHostProtocol {

    internal const val CLOSE_REASON_KEY = "com.ownid.sdk.ui.key.CLOSE_REASON"
    internal const val RESULT_RECEIVER_KEY = "com.ownid.sdk.ui.key.RESULT_RECEIVER"
    internal const val PREPARE_IME_HANDOFF_BEFORE_LAUNCH_KEY = "com.ownid.sdk.ui.key.PREPARE_IME_HANDOFF_BEFORE_LAUNCH"
    private const val INSTANCE_NAME_KEY = "com.ownid.sdk.ui.key.INSTANCE_NAME"
    private const val LAUNCH_TOKEN_KEY = "com.ownid.sdk.ui.key.LAUNCH_TOKEN"
    private const val RESULT_ERROR = 2

    internal fun createIntent(context: Context, target: Class<out ComponentActivity>, instanceName: InstanceName): Intent =
        Intent(context, target)
            .apply { if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            .putExtra(INSTANCE_NAME_KEY, instanceName.value)

    internal fun instanceNameOrNull(intent: Intent): InstanceName? = intent.getStringExtra(INSTANCE_NAME_KEY)?.let(::InstanceName)

    @Suppress("DEPRECATION")
    internal fun closeActivity(
        activity: ComponentActivity,
        logger: OwnIdLogRouter? = OwnIdRootDIContainer.getOrNull<OwnIdLogRouter>()
    ) {
        logger?.logD(activity, "closeActivity", "Invoked")
        activity.intent.getResultReceiver()?.send(Activity.RESULT_OK, null)
        activity.setResult(Activity.RESULT_OK)
        activity.finish()
        activity.overridePendingTransition(0, 0)
    }

    @Suppress("DEPRECATION")
    internal fun closeActivityWithError(
        activity: ComponentActivity,
        errorDetails: String,
        logger: OwnIdLogRouter? = OwnIdRootDIContainer.getOrNull<OwnIdLogRouter>()
    ) {
        logger?.logW(activity, "closeActivityWithError", "errorDetails: $errorDetails")
        activity.intent.getResultReceiver()?.send(RESULT_ERROR, Bundle().apply { putString(CLOSE_REASON_KEY, errorDetails) })
        activity.setResult(RESULT_ERROR, Intent().putExtra(CLOSE_REASON_KEY, errorDetails))
        activity.finish()
        activity.overridePendingTransition(0, 0)
    }

    @Suppress("DEPRECATION")
    private fun Intent.getResultReceiver(): ResultReceiver? =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(RESULT_RECEIVER_KEY) as? ResultReceiver
        } else {
            getParcelableExtra(RESULT_RECEIVER_KEY, ResultReceiver::class.java)
        }

    /**
     * Main-thread-owned launch reservation, shared by both SDK hosts. Only an unacknowledged launch expires;
     * an accepted host retains ownership until final destruction, including across configuration recreation.
     */
    internal object LaunchLock {
        // SDK startup policy, including the bounded IME handoff; not a rendering or network deadline.
        internal const val ACKNOWLEDGEMENT_TIMEOUT_MS = 3_000L
        private val handler = Handler(Looper.getMainLooper())
        private var current: Attempt? = null

        internal class Attempt internal constructor(
            private val instanceJob: Job,
            private val logger: OwnIdLogRouter?,
            private val onShowError: (Throwable?, String?) -> Unit
        ) : ResultReceiver(handler) {
            private val token = UUID.randomUUID().toString()
            private val deadline = SystemClock.uptimeMillis() + ACKNOWLEDGEMENT_TIMEOUT_MS
            internal var hostIdentity: Any? = null

            // Once acknowledged, a missing Activity identity means configuration recreation, not pending startup.
            internal var acknowledged = false
            private var completed = false
            private var cancelRegistration: kotlinx.coroutines.DisposableHandle? = null
            internal var cancelDeferredLaunch: (() -> Unit)? = null
            private val reconcile = Runnable { canStart() }

            internal fun attach(intent: Intent) {
                intent.putExtra(LAUNCH_TOKEN_KEY, token)
                intent.putExtra(RESULT_RECEIVER_KEY, this)
            }

            internal fun matches(intent: Intent): Boolean = intent.getStringExtra(LAUNCH_TOKEN_KEY) == token

            internal fun arm() {
                handler.postAtTime(reconcile, deadline)
                // Completion may be synchronous or arrive on another thread; all reconciliation stays on main.
                cancelRegistration = instanceJob.invokeOnCompletion { handler.post(reconcile) }
            }

            private fun isPending(): Boolean = current === this && !acknowledged

            /** Checks authority as well as time: a queued timeout/cancellation may not have run yet. */
            internal fun canStart(): Boolean {
                if (!isPending()) return false
                if (!instanceJob.isActive) {
                    releasePending()
                    return false
                }
                if (SystemClock.uptimeMillis() >= deadline) {
                    val details = "SDK host creation was not acknowledged within $ACKNOWLEDGEMENT_TIMEOUT_MS ms."
                    logger?.logW(this, "launchActivityForResult", details)
                    fail(null, details)
                    return false
                }
                return true
            }

            internal fun clearPendingWork() {
                handler.removeCallbacks(reconcile)
                cancelRegistration?.dispose()
                cancelRegistration = null
                val cancel = cancelDeferredLaunch
                cancelDeferredLaunch = null
                cancel?.invoke()
            }

            private fun releasePending(): Boolean {
                if (!isPending()) return false
                current = null
                completed = true
                clearPendingWork()
                return true
            }

            internal fun fail(cause: Throwable?, details: String) {
                if (releasePending()) onShowError(cause, details)
            }

            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                if (completed) return
                if (isPending() && !canStart()) return
                // A result never releases an accepted host; its final onDestroy owns that transition.
                releasePending()
                completed = true
                // A normal result may be queued until after destruction or even the next launch.
                if (resultCode != Activity.RESULT_OK) onShowError(null, resultData?.getString(CLOSE_REASON_KEY))
            }
        }

        internal fun tryStart(
            instanceJob: Job,
            logger: OwnIdLogRouter?,
            onShowError: (Throwable?, String?) -> Unit
        ): Attempt? {
            current?.canStart()
            current?.let { attempt ->
                val phase = when {
                    !attempt.acknowledged -> "pending"
                    attempt.hostIdentity != null -> "running"
                    else -> "recreating"
                }
                logger?.logW(this, "launchActivityForResult", "SDK host launch rejected: existing host is $phase.")
                onShowError(null, "Another launch is already in progress.")
                return null
            }
            if (!instanceJob.isActive) return null
            return Attempt(instanceJob, logger, onShowError).also {
                current = it
                it.arm()
            }
        }

        internal fun onHostCreated(intent: Intent, hostIdentity: Any): Boolean {
            val attempt = current ?: return false
            if (!attempt.matches(intent)) return false
            if (attempt.hostIdentity != null || (!attempt.acknowledged && !attempt.canStart())) return false
            attempt.acknowledged = true
            attempt.hostIdentity = hostIdentity
            attempt.clearPendingWork()
            return true
        }

        @VisibleForTesting
        internal fun resetForTesting() {
            current?.clearPendingWork()
            current = null
        }

        internal fun onHostDestroyed(hostIdentity: Any, keepForRecreation: Boolean) {
            val attempt = current ?: return
            if (attempt.hostIdentity !== hostIdentity) return
            if (keepForRecreation) {
                attempt.hostIdentity = null
            } else {
                current = null
            }
        }
    }
}
