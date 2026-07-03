package com.ownid.sdk.ui.capability.impl

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.ResultReceiver
import androidx.activity.ComponentActivity
import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.capability.UiContextProvider
import com.ownid.sdk.model.InstanceName
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.ui.OwnIdHiddenActivity
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController

@RunWith(RobolectricTestRunner::class)
@OptIn(InternalOwnIdAPI::class)
// Covers: UI-PRESENT-170
internal class HiddenActivityLauncherImplRuntimeContractTest {

    private val app: Application
        get() = RuntimeEnvironment.getApplication()

    @After
    fun tearDown() {
        OwnIdHiddenActivity.LaunchLock.resetStart()
        OwnIdHiddenActivity.LaunchLock.onHiddenDestroyed()
        shadowOf(app).clearNextStartedActivities()
    }

    @Test
    fun launchFromApplicationContextStartsHiddenActivityIntentWithExpectedFlagsAndExtras() {
        val errors = mutableListOf<StartupFailure>()

        launcher(app).launchActivityForResult(
            intentProvider = { context -> OwnIdHiddenActivity.createIntent(context, InstanceName("hidden-ui")) },
            onShowError = { error, details -> errors += StartupFailure(error, details) }
        )

        val startedIntent = shadowOf(app).nextStartedActivity

        assertThat(startedIntent.component?.className).isEqualTo(OwnIdHiddenActivity::class.java.name)
        assertThat(startedIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isEqualTo(Intent.FLAG_ACTIVITY_NEW_TASK)
        assertThat(startedIntent.flags and Intent.FLAG_ACTIVITY_NO_ANIMATION).isEqualTo(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        assertThat(startedIntent.getStringExtra(INSTANCE_NAME_KEY)).isEqualTo("hidden-ui")
        assertThat(startedIntent.resultReceiver()).isNotNull()
        assertThat(errors).isEmpty()
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

    @Test
    fun duplicateLaunchWhileStartupIsInProgressReportsStartupErrorWithoutStartingSecondIntent() {
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
                OwnIdHiddenActivity.createIntent(context, InstanceName("second"))
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
    fun runningHiddenActivityReportsStartupErrorWithoutStartingIntent() {
        OwnIdHiddenActivity.LaunchLock.onHiddenCreated()
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
            Bundle().apply { putString(OwnIdHiddenActivity.CLOSE_REASON_KEY, "receiver-failure") }
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

    private fun launcher(context: Context): HiddenActivityLauncherImpl =
        HiddenActivityLauncherImpl(uiContextProvider = { UiContextProvider { context } }, logger = null)

    @Suppress("DEPRECATION")
    private fun Intent.resultReceiver(): ResultReceiver? =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(OwnIdHiddenActivity.RESULT_RECEIVER_KEY) as? ResultReceiver
        } else {
            getParcelableExtra(OwnIdHiddenActivity.RESULT_RECEIVER_KEY, ResultReceiver::class.java)
        }

    private fun ActivityController<out Activity>.destroyIfNeeded() {
        if (!get().isDestroyed) destroy()
    }

    private data class StartupFailure(val error: Throwable?, val details: String?)

    private class TestActivity : ComponentActivity()

    private companion object {
        private const val INSTANCE_NAME_KEY = "com.ownid.sdk.ui.key.INSTANCE_NAME"
    }
}
