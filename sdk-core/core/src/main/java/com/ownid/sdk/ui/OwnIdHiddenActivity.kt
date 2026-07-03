package com.ownid.sdk.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.ResultReceiver
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RestrictTo
import com.ownid.sdk.OwnId
import com.ownid.sdk.di.OwnIdRootDIContainer
import com.ownid.sdk.di.getOrNull
import com.ownid.sdk.logger.OwnIdLogRouter
import com.ownid.sdk.model.InstanceName
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.ui.OwnIdHiddenActivity.Companion.CLOSE_REASON_KEY
import com.ownid.sdk.ui.capability.HiddenActivityLauncher
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Headless host activity for operation UI containers and WebBridge WebView.
 *
 * Runtime behavior:
 * - Uses [LaunchLock] to keep a single hidden-activity launch in flight process-wide.
 * - Resolves SDK instance from intent extras and selects the first [HiddenActivityLauncher.Delegate] matching [Intent].
 * - Closes without transition animation and reports close status via optional `ResultReceiver`.
 */
@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class OwnIdHiddenActivity : ComponentActivity() {

    @InternalOwnIdAPI
    public companion object {
        /**
         * Creates a launch intent targeting this activity for the given SDK [instanceName].
         *
         * Adds `FLAG_ACTIVITY_NEW_TASK` when [context] is not an [Activity], and always applies `FLAG_ACTIVITY_NO_ANIMATION`.
         */
        public fun createIntent(context: Context, instanceName: InstanceName): Intent =
            Intent(context, OwnIdHiddenActivity::class.java)
                .apply { if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                .putExtra(INSTANCE_NAME_KEY, instanceName.value)

        /**
         * Finishes this activity with `RESULT_OK` and notifies the optional `ResultReceiver`.
         *
         * Completion is reported both through `send(RESULT_OK, null)` and `setResult(RESULT_OK)`.
         */
        @Suppress("DEPRECATION")
        public fun ComponentActivity.closeActivity() {
            OwnIdRootDIContainer.getOrNull<OwnIdLogRouter>()?.logD(this, "closeActivity", "Invoked")

            intent.getResultReceiver()?.send(RESULT_OK, null)

            setResult(RESULT_OK)
            finish()

            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        /**
         * Finishes this activity with an error result and notifies the optional `ResultReceiver`.
         *
         * Uses SDK-private `RESULT_ERROR` and includes [CLOSE_REASON_KEY] in both callback bundle and activity result intent.
         */
        @Suppress("DEPRECATION")
        public fun ComponentActivity.closeActivityWithError(errorDetails: String) {
            OwnIdRootDIContainer.getOrNull<OwnIdLogRouter>()?.logW(this, "closeActivityWithError", "errorDetails: $errorDetails")

            intent.getResultReceiver()?.send(RESULT_ERROR, Bundle().apply { putString(CLOSE_REASON_KEY, errorDetails) })

            setResult(RESULT_ERROR, Intent().putExtra(CLOSE_REASON_KEY, errorDetails))
            finish()

            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        @Suppress("DEPRECATION")
        private fun Intent.getResultReceiver(): ResultReceiver? =
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                getParcelableExtra(RESULT_RECEIVER_KEY) as? ResultReceiver
            } else {
                getParcelableExtra(RESULT_RECEIVER_KEY, ResultReceiver::class.java)
            }

        internal const val CLOSE_REASON_KEY = "com.ownid.sdk.ui.key.CLOSE_REASON"
        internal const val RESULT_RECEIVER_KEY = "com.ownid.sdk.ui.key.RESULT_RECEIVER"
        public const val PREPARE_IME_HANDOFF_BEFORE_LAUNCH_KEY: String = "com.ownid.sdk.ui.key.PREPARE_IME_HANDOFF_BEFORE_LAUNCH"
        private const val INSTANCE_NAME_KEY = "com.ownid.sdk.ui.key.INSTANCE_NAME"
        private const val RESULT_ERROR = 2
    }

    /**
     * Global, process-wide gate that coordinates hidden-activity launch and run states.
     *
     * Usage:
     * - tryStart(): set when launching; returns false if a launch is already starting or running.
     * - onHiddenCreated(): called from onCreate to mark the activity as running and clear start.
     * - onHiddenDestroyed(): called from onDestroy to release the running lock.
     * - resetStart(): clears the start flag on start failures or after ResultReceiver callback.
     *
     * During configuration-change recreation, `onDestroy` may keep the running flag set to avoid opening a relaunch
     * window between old-instance destroy and new-instance create.
     */
    internal object LaunchLock {
        private val start = AtomicBoolean(false)
        private val running = AtomicBoolean(false)

        fun tryStart(): Boolean {
            if (running.get()) return false
            return start.compareAndSet(false, true)
        }

        fun onHiddenCreated() {
            running.set(true)
            start.set(false)
        }

        fun onHiddenDestroyed() {
            running.set(false)
        }

        fun resetStart() {
            start.set(false)
        }
    }

    private lateinit var delegate: HiddenActivityLauncher.Delegate

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)

        LaunchLock.onHiddenCreated()

        OwnIdRootDIContainer.getOrNull<OwnIdLogRouter>()?.logD(this, "onCreate", "Invoked: ${this.hashCode()}")

        val instanceNameString = intent.getStringExtra(INSTANCE_NAME_KEY) ?: run {
            closeActivityWithError("${this@OwnIdHiddenActivity.javaClass.simpleName} onCreate: No instance name found")
            return
        }

        val instanceName = InstanceName(instanceNameString)
        val instanceResolver = OwnId.getInstanceContainer(instanceName) ?: run {
            closeActivityWithError("${this@OwnIdHiddenActivity.javaClass.simpleName} onCreate: No instance found for $instanceName")
            return
        }

        val delegates = instanceResolver.getAllInstancesOf(HiddenActivityLauncher.Delegate::class.java)
        delegate = delegates.firstOrNull { it.isThis(intent) } ?: run {
            closeActivityWithError("${this@OwnIdHiddenActivity.javaClass.simpleName} onCreate: No delegate found for $intent")
            return
        }

        delegate.onCreate(this, instanceResolver)
    }

    override fun onDestroy() {
        OwnIdRootDIContainer.getOrNull<OwnIdLogRouter>()?.logD(this, "onDestroy", "Invoked: ${this.hashCode()}")

        if (::delegate.isInitialized) delegate.onDestroy(this)
        val keepLockForRecreation = isChangingConfigurations && !isFinishing
        if (!keepLockForRecreation) {
            LaunchLock.onHiddenDestroyed()
        }
        super.onDestroy()
    }
}
