package com.ownid.sdk.ui.capability.impl

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Parcel
import android.os.ResultReceiver
import androidx.activity.ComponentActivity
import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.OwnId
import com.ownid.sdk.OwnIdConfigurationImpl
import com.ownid.sdk.capability.UiContextProvider
import com.ownid.sdk.di.DIContainerResolver
import com.ownid.sdk.di.OwnIdRootDIContainer
import com.ownid.sdk.di.register
import com.ownid.sdk.model.InstanceName
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.OperationID
import com.ownid.sdk.model.OperationType
import com.ownid.sdk.ui.OwnIdActivityHostProtocol
import com.ownid.sdk.ui.OwnIdHiddenActivity
import com.ownid.sdk.ui.OwnIdWebViewActivity
import com.ownid.sdk.ui.capability.HiddenActivityLauncher
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@OptIn(InternalOwnIdAPI::class)
// Covers: UI-PRESENT-170
internal class HiddenActivityLauncherImplRuntimeContractTest {

    private val instancesToDestroy = mutableListOf<InstanceName>()

    private val app: Application
        get() = RuntimeEnvironment.getApplication()

    @After
    fun tearDown() {
        instancesToDestroy.forEach(OwnId::destroy)
        OwnIdActivityHostProtocol.LaunchLock.resetStart()
        OwnIdActivityHostProtocol.LaunchLock.onHostDestroyed(keepForRecreation = false)
        shadowOf(app).clearNextStartedActivities()
    }

    @Config(sdk = [28])
    @Test
    fun legacyResultReceiverPathCoversBothHostTargetsAndRealCloseLifecycles() {
        verifySharedActivityHostProtocol()
    }

    @Config(sdk = [35])
    @Test
    fun typedResultReceiverPathCoversBothHostTargetsAndRealCloseLifecycles() {
        verifySharedActivityHostProtocol()
    }

    @Test
    fun createIntentFromActivityContextOmitsNewTaskFlagButKeepsHiddenActivityProtocolExtras() {
        val activity = Robolectric.buildActivity(TestActivity::class.java)

        try {
            activity.create()

            val intent = OwnIdHiddenActivity.createIntent(activity.get(), InstanceName("activity-context"))

            assertThat(intent.component?.className).isEqualTo(OwnIdHiddenActivity::class.java.name)
            assertThat(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isEqualTo(0)
            assertThat(intent.flags and Intent.FLAG_ACTIVITY_NO_ANIMATION).isEqualTo(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            assertThat(intent.getStringExtra(INSTANCE_NAME_KEY)).isEqualTo("activity-context")
        } finally {
            activity.destroyIfNeeded()
        }
    }

    private fun verifySharedActivityHostProtocol() {
        val errors = mutableListOf<StartupFailure>()

        launcher(app).launchActivityForResult(
            intentProvider = { context -> OwnIdHiddenActivity.createIntent(context, InstanceName("hidden-ui")) },
            onShowError = { error, details -> errors += StartupFailure(error, details) }
        )

        val hiddenStartedIntent = shadowOf(app).nextStartedActivity
        val hiddenIntent = hiddenStartedIntent.parcelRoundTrip()
        assertThat(hiddenIntent.component?.className).isEqualTo(OwnIdHiddenActivity::class.java.name)
        assertThat(hiddenIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isEqualTo(Intent.FLAG_ACTIVITY_NEW_TASK)
        assertThat(hiddenIntent.flags and Intent.FLAG_ACTIVITY_NO_ANIMATION).isEqualTo(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        assertThat(hiddenIntent.getStringExtra(INSTANCE_NAME_KEY)).isEqualTo("hidden-ui")
        assertThat(hiddenIntent.resultReceiver()).isNotNull()
        hiddenStartedIntent.resultReceiver()?.send(Activity.RESULT_OK, Bundle())
        idleMain()

        launcher(app).launchActivityForResult(
            intentProvider = { context ->
                OwnIdWebViewActivity.createIntent(
                    context,
                    InstanceName("webview-ui"),
                    OperationID(OperationType.WEB_BRIDGE, "operation")
                )
            },
            onShowError = { error, details -> errors += StartupFailure(error, details) }
        )

        val webViewStartedIntent = shadowOf(app).nextStartedActivity
        val webViewIntent = webViewStartedIntent.parcelRoundTrip()
        assertThat(webViewIntent.component?.className).isEqualTo(OwnIdWebViewActivity::class.java.name)
        assertThat(webViewIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isEqualTo(Intent.FLAG_ACTIVITY_NEW_TASK)
        assertThat(webViewIntent.flags and Intent.FLAG_ACTIVITY_NO_ANIMATION).isEqualTo(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        assertThat(webViewIntent.getStringExtra(INSTANCE_NAME_KEY)).isEqualTo("webview-ui")
        assertThat(webViewIntent.getStringExtra(OPERATION_TYPE_KEY)).isEqualTo(OperationType.WEB_BRIDGE.name)
        assertThat(webViewIntent.getStringExtra(OPERATION_ID_KEY)).isEqualTo("operation")
        assertThat(webViewIntent.resultReceiver()).isNotNull()
        webViewStartedIntent.resultReceiver()?.send(Activity.RESULT_OK, Bundle())
        idleMain()

        assertThat(errors).isEmpty()

        verifyHostClose(errorDetails = null, followUpInstanceName = "after-success")
        verifyHostClose(errorDetails = "host-error", followUpInstanceName = "after-error")
    }

    private fun verifyHostClose(errorDetails: String?, followUpInstanceName: String) {
        val delegate = ClosingDelegate(errorDetails)
        val instanceName = registerHiddenHost(delegate)
        val errors = mutableListOf<StartupFailure>()

        launcher(app).launchActivityForResult(
            intentProvider = { context -> OwnIdHiddenActivity.createIntent(context, instanceName) },
            onShowError = { error, details -> errors += StartupFailure(error, details) }
        )

        val host = Robolectric.buildActivity(OwnIdHiddenActivity::class.java, shadowOf(app).nextStartedActivity).create()
        try {
            idleMain()
            assertThat(host.get().isFinishing).isTrue()
            if (errorDetails == null) {
                assertThat(shadowOf(host.get()).resultCode).isEqualTo(Activity.RESULT_OK)
                assertThat(errors).isEmpty()
            } else {
                assertThat(shadowOf(host.get()).resultCode).isNotEqualTo(Activity.RESULT_OK)
                assertThat(shadowOf(host.get()).resultIntent.getStringExtra(OwnIdActivityHostProtocol.CLOSE_REASON_KEY))
                    .isEqualTo(errorDetails)
                assertThat(errors).containsExactly(StartupFailure(null, errorDetails))
            }
        } finally {
            host.close()
            idleMain()
        }
        assertThat(delegate.destroyCount).isEqualTo(1)

        launcher(app).launchActivityForResult(
            intentProvider = { context -> OwnIdHiddenActivity.createIntent(context, InstanceName(followUpInstanceName)) },
            onShowError = { error, details -> errors += StartupFailure(error, details) }
        )

        val followUpIntent = shadowOf(app).nextStartedActivity
        assertThat(followUpIntent.getStringExtra(INSTANCE_NAME_KEY)).isEqualTo(followUpInstanceName)
        followUpIntent.resultReceiver()?.send(Activity.RESULT_OK, Bundle())
        idleMain()

        if (errorDetails == null) {
            assertThat(errors).isEmpty()
        } else {
            assertThat(errors).containsExactly(StartupFailure(null, errorDetails))
        }
    }

    @Test
    fun duplicateLaunchAcrossHostTargetsReportsStartupErrorWithoutStartingSecondIntent() {
        var intentProviderCalls = 0
        val errors = mutableListOf<StartupFailure>()
        val hiddenActivityLauncher = launcher(app)

        hiddenActivityLauncher.launchActivityForResult(
            intentProvider = { context ->
                intentProviderCalls += 1
                OwnIdHiddenActivity.createIntent(context, InstanceName("first"))
            },
            onShowError = { error, details -> errors += StartupFailure(error, details) }
        )
        hiddenActivityLauncher.launchActivityForResult(
            intentProvider = { context ->
                intentProviderCalls += 1
                OwnIdWebViewActivity.createIntent(
                    context,
                    InstanceName("second"),
                    OperationID(OperationType.WEB_BRIDGE, "second")
                )
            },
            onShowError = { error, details -> errors += StartupFailure(error, details) }
        )

        val firstStartedIntent = shadowOf(app).nextStartedActivity

        assertThat(intentProviderCalls).isEqualTo(1)
        assertThat(errors).containsExactly(StartupFailure(null, "Another launch is already in progress."))
        assertThat(firstStartedIntent.getStringExtra(INSTANCE_NAME_KEY)).isEqualTo("first")
        assertThat(shadowOf(app).nextStartedActivity).isNull()
    }

    @Test
    fun runningSdkHostReportsStartupErrorWithoutStartingIntent() {
        OwnIdActivityHostProtocol.LaunchLock.onHostCreated()
        val errors = mutableListOf<StartupFailure>()

        launcher(app).launchActivityForResult(
            intentProvider = { context -> OwnIdHiddenActivity.createIntent(context, InstanceName("blocked")) },
            onShowError = { error, details -> errors += StartupFailure(error, details) }
        )

        assertThat(errors).containsExactly(StartupFailure(null, "Another launch is already in progress."))
        assertThat(shadowOf(app).nextStartedActivity).isNull()
    }

    @Test
    fun nonOkReceiverResultReportsStartupErrorAndReleasesLaunchLock() {
        val errors = mutableListOf<StartupFailure>()
        val hiddenActivityLauncher = launcher(app)

        hiddenActivityLauncher.launchActivityForResult(
            intentProvider = { context -> OwnIdHiddenActivity.createIntent(context, InstanceName("first")) },
            onShowError = { error, details -> errors += StartupFailure(error, details) }
        )

        val firstIntent = shadowOf(app).nextStartedActivity
        firstIntent.resultReceiver()?.send(
            Activity.RESULT_CANCELED,
            Bundle().apply { putString(OwnIdActivityHostProtocol.CLOSE_REASON_KEY, "receiver-failure") }
        )
        shadowOf(android.os.Looper.getMainLooper()).idle()

        hiddenActivityLauncher.launchActivityForResult(
            intentProvider = { context -> OwnIdHiddenActivity.createIntent(context, InstanceName("second")) },
            onShowError = { error, details -> errors += StartupFailure(error, details) }
        )

        val secondStartedIntent = shadowOf(app).nextStartedActivity

        assertThat(errors).containsExactly(StartupFailure(null, "receiver-failure"))
        assertThat(secondStartedIntent.getStringExtra(INSTANCE_NAME_KEY)).isEqualTo("second")
    }

    @Test
    fun launchLockStaysHeldAcrossConfigurationHandoffAndReleasesAfterReplacementDestroy() {
        val errors = mutableListOf<StartupFailure>()
        OwnIdActivityHostProtocol.LaunchLock.onHostCreated()
        OwnIdActivityHostProtocol.LaunchLock.onHostDestroyed(keepForRecreation = true)

        launcher(app).launchActivityForResult(
            intentProvider = { context -> OwnIdHiddenActivity.createIntent(context, InstanceName("during-recreation")) },
            onShowError = { error, details -> errors += StartupFailure(error, details) }
        )

        assertThat(errors).containsExactly(StartupFailure(null, "Another launch is already in progress."))
        assertThat(shadowOf(app).nextStartedActivity).isNull()

        OwnIdActivityHostProtocol.LaunchLock.onHostCreated()
        OwnIdActivityHostProtocol.LaunchLock.onHostDestroyed(keepForRecreation = false)
        launcher(app).launchActivityForResult(
            intentProvider = { context -> OwnIdHiddenActivity.createIntent(context, InstanceName("after-recreation")) },
            onShowError = { error, details -> errors += StartupFailure(error, details) }
        )

        assertThat(shadowOf(app).nextStartedActivity.getStringExtra(INSTANCE_NAME_KEY)).isEqualTo("after-recreation")
    }

    @Test
    fun startupFailureReportsErrorAndReleasesLaunchReservation() {
        val errors = mutableListOf<StartupFailure>()
        val throwingContext = object : android.content.ContextWrapper(app) {
            override fun getApplicationContext(): Context = this

            override fun startActivity(intent: Intent) {
                throw IllegalStateException("cannot launch")
            }
        }

        launcher(throwingContext).launchActivityForResult(
            intentProvider = { context -> OwnIdHiddenActivity.createIntent(context, InstanceName("failure")) },
            onShowError = { error, details -> errors += StartupFailure(error, details) }
        )
        launcher(app).launchActivityForResult(
            intentProvider = { context -> OwnIdHiddenActivity.createIntent(context, InstanceName("retry")) },
            onShowError = { error, details -> errors += StartupFailure(error, details) }
        )

        assertThat(errors).hasSize(1)
        assertThat(errors.single().error).isInstanceOf(IllegalStateException::class.java)
        assertThat(errors.single().details).contains("cannot launch")
        assertThat(shadowOf(app).nextStartedActivity.getStringExtra(INSTANCE_NAME_KEY)).isEqualTo("retry")
    }

    @Test
    fun imeHandoffRequestLaunchesImmediatelyWhenParentImeIsNotVisible() {
        val activity = Robolectric.buildActivity(TestActivity::class.java).setup()
        try {
            launcher(activity.get()).launchActivityForResult(
                intentProvider = { context ->
                    OwnIdHiddenActivity.createIntent(context, InstanceName("ime-handoff"))
                        .putExtra(OwnIdActivityHostProtocol.PREPARE_IME_HANDOFF_BEFORE_LAUNCH_KEY, true)
                },
                onShowError = { _, details -> error("Unexpected launch error: $details") }
            )

            assertThat(shadowOf(activity.get()).nextStartedActivity.getStringExtra(INSTANCE_NAME_KEY)).isEqualTo("ime-handoff")
        } finally {
            activity.destroyIfNeeded()
        }
    }

    private fun launcher(context: Context): HiddenActivityLauncherImpl =
        HiddenActivityLauncherImpl(uiContextProvider = { UiContextProvider { context } }, logger = null)

    private fun registerHiddenHost(delegate: ClosingDelegate): InstanceName {
        OwnIdRootDIContainer.injectRootDefaults(app)
        val instanceName = InstanceName("launcher-host-${UUID.randomUUID()}").also(instancesToDestroy::add)
        OwnIdRootDIContainer.initializeInstanceContainer(instanceName, OwnIdConfigurationImpl(appId = "LauncherHostTest"))
            .register<HiddenActivityLauncher.Delegate>(delegate)
        return instanceName
    }

    private fun Intent.parcelRoundTrip(): Intent {
        val parcel = Parcel.obtain()
        return try {
            writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            Intent.CREATOR.createFromParcel(parcel)
        } finally {
            parcel.recycle()
        }
    }

    private fun idleMain() {
        shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    @Suppress("DEPRECATION")
    private fun Intent.resultReceiver(): ResultReceiver? =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(OwnIdActivityHostProtocol.RESULT_RECEIVER_KEY) as? ResultReceiver
        } else {
            getParcelableExtra(OwnIdActivityHostProtocol.RESULT_RECEIVER_KEY, ResultReceiver::class.java)
        }

    private fun ActivityController<out Activity>.destroyIfNeeded() {
        if (!get().isDestroyed) destroy()
    }

    private data class StartupFailure(val error: Throwable?, val details: String?)

    private class ClosingDelegate(private val errorDetails: String?) : HiddenActivityLauncher.Delegate {
        var destroyCount: Int = 0

        override fun isThis(intent: Intent): Boolean = true

        override fun onCreate(activity: ComponentActivity, instanceResolver: DIContainerResolver) {
            if (errorDetails == null) {
                OwnIdActivityHostProtocol.closeActivity(activity)
            } else {
                OwnIdActivityHostProtocol.closeActivityWithError(activity, errorDetails)
            }
        }

        override fun onDestroy(activity: ComponentActivity) {
            destroyCount += 1
        }
    }

    private class TestActivity : ComponentActivity()

    private companion object {
        private const val INSTANCE_NAME_KEY = "com.ownid.sdk.ui.key.INSTANCE_NAME"
        private const val OPERATION_TYPE_KEY = "com.ownid.sdk.key.OPERATION_TYPE_KEY"
        private const val OPERATION_ID_KEY = "com.ownid.sdk.key.OPERATION_ID_KEY"
    }
}
