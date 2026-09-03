package com.ownid.sdk.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.ResultReceiver
import androidx.activity.ComponentActivity
import com.ownid.sdk.di.OwnIdRootDIContainer
import com.ownid.sdk.di.getOrNull
import com.ownid.sdk.logger.OwnIdLogRouter
import com.ownid.sdk.model.InstanceName
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.ui.OwnIdActivityHostProtocol.LaunchLock.onHostCreated
import com.ownid.sdk.ui.OwnIdActivityHostProtocol.LaunchLock.onHostDestroyed
import com.ownid.sdk.ui.OwnIdActivityHostProtocol.LaunchLock.resetStart
import com.ownid.sdk.ui.OwnIdActivityHostProtocol.LaunchLock.tryStart
import java.util.concurrent.atomic.AtomicBoolean

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
     * Process-wide gate coordinating launches of SDK-owned host Activities.
     *
     * [tryStart] reserves a pending launch and rejects another launch while a host is starting or running.
     * [onHostCreated] transfers that reservation to the running state. [onHostDestroyed] releases the running state,
     * except during configuration recreation, where the lock stays held across the old and replacement host instances.
     * [resetStart] clears a pending reservation after launch failure or result delivery.
     *
     * Both [OwnIdHiddenActivity] and [OwnIdWebViewActivity] share this gate, so their presentation paths cannot overlap.
     */
    internal object LaunchLock {
        private val start = AtomicBoolean(false)
        private val running = AtomicBoolean(false)

        internal fun tryStart(): Boolean {
            if (running.get()) return false
            return start.compareAndSet(false, true)
        }

        internal fun onHostCreated() {
            running.set(true)
            start.set(false)
        }

        internal fun onHostDestroyed(keepForRecreation: Boolean) {
            if (!keepForRecreation) running.set(false)
        }

        internal fun resetStart() {
            start.set(false)
        }
    }
}
