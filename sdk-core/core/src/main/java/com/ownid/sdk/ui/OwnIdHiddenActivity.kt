package com.ownid.sdk.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RestrictTo
import com.ownid.sdk.OwnId
import com.ownid.sdk.di.OwnIdRootDIContainer
import com.ownid.sdk.di.getOrNull
import com.ownid.sdk.logger.OwnIdLogRouter
import com.ownid.sdk.model.InstanceName
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.ui.capability.HiddenActivityLauncher

/**
 * Hosts SDK UI supplied through [HiddenActivityLauncher.Delegate] implementations.
 *
 * Runtime behavior:
 * - Uses the shared SDK Activity launch lock to keep a single SDK-owned host launch in flight process-wide.
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
            OwnIdActivityHostProtocol.createIntent(context, OwnIdHiddenActivity::class.java, instanceName)

        /**
         * Finishes this activity with `RESULT_OK` and notifies the optional `ResultReceiver`.
         *
         * Completion is reported both through `send(RESULT_OK, null)` and `setResult(RESULT_OK)`.
         */
        @Suppress("DEPRECATION")
        public fun ComponentActivity.closeActivity() {
            val logger = if (this is OwnIdHiddenActivity) logRouter else OwnIdRootDIContainer.getOrNull<OwnIdLogRouter>()
            OwnIdActivityHostProtocol.closeActivity(this, logger)
        }

        /**
         * Finishes this activity with an error result and notifies the optional `ResultReceiver`.
         *
         * Uses the SDK-private error result and includes the close reason in both callback bundle and activity result intent.
         */
        @Suppress("DEPRECATION")
        public fun ComponentActivity.closeActivityWithError(errorDetails: String) {
            val logger = if (this is OwnIdHiddenActivity) logRouter else OwnIdRootDIContainer.getOrNull<OwnIdLogRouter>()
            OwnIdActivityHostProtocol.closeActivityWithError(this, errorDetails, logger)
        }

        /** Intent extra requesting the launcher to hide a visible parent IME before starting this Activity. */
        public const val PREPARE_IME_HANDOFF_BEFORE_LAUNCH_KEY: String = OwnIdActivityHostProtocol.PREPARE_IME_HANDOFF_BEFORE_LAUNCH_KEY
    }

    private lateinit var delegate: HiddenActivityLauncher.Delegate
    private var logRouter: OwnIdLogRouter? = null
    private val launchHostIdentity = Any()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)

        if (!OwnIdActivityHostProtocol.LaunchLock.onHostCreated(intent, launchHostIdentity)) {
            // A timed-out, canceled, superseded or process-restored request no longer owns SDK UI.
            finish()
            return
        }

        val instanceName = OwnIdActivityHostProtocol.instanceNameOrNull(intent) ?: run {
            logRouter = OwnIdRootDIContainer.getOrNull<OwnIdLogRouter>()
            logRouter?.logD(this, "onCreate", "Invoked: ${this.hashCode()}")
            closeActivityWithError("${this@OwnIdHiddenActivity.javaClass.simpleName} onCreate: No instance name found")
            return
        }

        val instanceResolver = OwnId.getInstanceContainer(instanceName) ?: run {
            logRouter = OwnIdRootDIContainer.getOrNull<OwnIdLogRouter>()
            logRouter?.logD(this, "onCreate", "Invoked: ${this.hashCode()}")
            closeActivityWithError("${this@OwnIdHiddenActivity.javaClass.simpleName} onCreate: No instance found for $instanceName")
            return
        }
        logRouter = instanceResolver.getOrNull<OwnIdLogRouter>()
        logRouter?.logD(this, "onCreate", "Invoked: ${this.hashCode()}")

        val delegates = instanceResolver.getAllInstancesOf(HiddenActivityLauncher.Delegate::class.java)
        delegate = delegates.firstOrNull { it.isThis(intent) } ?: run {
            closeActivityWithError("${this@OwnIdHiddenActivity.javaClass.simpleName} onCreate: No delegate found for $intent")
            return
        }

        delegate.onCreate(this, instanceResolver)
    }

    override fun onDestroy() {
        try {
            logRouter?.logD(this, "onDestroy", "Invoked: ${this.hashCode()}")

            if (::delegate.isInitialized) delegate.onDestroy(this)
        } finally {
            val keepLockForRecreation = isChangingConfigurations && !isFinishing
            OwnIdActivityHostProtocol.LaunchLock.onHostDestroyed(launchHostIdentity, keepLockForRecreation)
            super.onDestroy()
        }
    }
}
