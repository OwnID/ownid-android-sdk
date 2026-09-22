package com.ownid.sdk.ui.capability.impl

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.Parcel
import android.os.ResultReceiver
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import android.view.WindowInsets
import androidx.activity.ComponentActivity
import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.OwnId
import com.ownid.sdk.OwnIdConfigurationImpl
import com.ownid.sdk.capability.UiContextProvider
import com.ownid.sdk.di.DIContainerResolver
import com.ownid.sdk.di.OwnIdRootDIContainer
import com.ownid.sdk.di.getOrThrow
import com.ownid.sdk.di.register
import com.ownid.sdk.logger.OwnIdLogRouter
import com.ownid.sdk.logger.OwnIdLogger
import com.ownid.sdk.model.InstanceName
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.OperationID
import com.ownid.sdk.model.OperationType
import com.ownid.sdk.ui.OwnIdActivityHostProtocol
import com.ownid.sdk.ui.OwnIdHiddenActivity
import com.ownid.sdk.ui.OwnIdHiddenActivity.Companion.closeActivityWithError
import com.ownid.sdk.ui.OwnIdWebViewActivity
import com.ownid.sdk.ui.capability.HiddenActivityLauncher
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Job
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowSystemClock
import org.robolectric.shadows.ShadowViewRootImpl
import org.robolectric.util.ReflectionHelpers
import java.util.UUID
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [HiddenActivityLauncherImplRuntimeContractTest.FrameworkResultReceiverShadow::class])
@OptIn(InternalOwnIdAPI::class)
// Covers: UI-PRESENT-170
internal class HiddenActivityLauncherImplRuntimeContractTest {

    private val instancesToDestroy = mutableListOf<InstanceName>()

    private val app: Application
        get() = RuntimeEnvironment.getApplication()

    private val launchActivity: TestActivity by lazy { Robolectric.buildActivity(TestActivity::class.java).setupVisibleSource().get() }

    @After
    fun tearDown() {
        instancesToDestroy.forEach(OwnId::destroy)
        OwnIdActivityHostProtocol.LaunchLock.resetForTesting()
        shadowOf(launchActivity).clearNextStartedActivities()
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

    @Test
    fun plainActivityLaunchResolvesOnceAndBuildsIntentForThatActivity() {
        val activity = Robolectric.buildActivity(Activity::class.java).setupVisibleSource()
        var lookups = 0
        var factories = 0
        val errors = mutableListOf<StartupFailure>()
        try {
            val launcher = HiddenActivityLauncherImpl(
                uiContextProvider = { UiContextProvider { lookups += 1; UiContextProvider.Result.Success(activity.get()) } },
                instanceJob = Job(),
                logger = null
            )
            launcher.launchActivityForResult(
                intentProvider = { source ->
                    factories += 1
                    assertThat(source).isSameInstanceAs(activity.get())
                    OwnIdHiddenActivity.createIntent(source, InstanceName("plain-host"))
                },
                onShowError = { error, details -> errors += StartupFailure(error, details) }
            )

            assertThat(lookups).isEqualTo(1)
            assertThat(factories).isEqualTo(1)
            assertThat(shadowOf(activity.get()).nextStartedActivity.getStringExtra(INSTANCE_NAME_KEY)).isEqualTo("plain-host")
            assertThat(errors).isEmpty()
        } finally {
            activity.destroyIfNeeded()
        }
    }

    @Test
    fun visibleSourceWithoutWindowFocusCanLaunch() {
        val activity = Robolectric.buildActivity(TestActivity::class.java).setupVisibleSource()
        val errors = mutableListOf<StartupFailure>()
        try {
            activity.windowFocusChanged(false)
            activity.get().setWindowVisibilityForTest(View.VISIBLE)
            assertThat(activity.get().window.decorView.hasWindowFocus()).isFalse()

            launcher(activity.get()).launchActivityForResult(
                { OwnIdHiddenActivity.createIntent(it, InstanceName("unfocused-visible")) },
                { error, details -> errors += StartupFailure(error, details) }
            )

            assertThat(shadowOf(activity.get()).nextStartedActivity.getStringExtra(INSTANCE_NAME_KEY))
                .isEqualTo("unfocused-visible")
            assertThat(errors).isEmpty()
        } finally {
            activity.destroyIfNeeded()
        }
    }

    @Test
    fun hiddenSourceFailsBeforeStartAndReleasesLaunchReservation() {
        val activity = Robolectric.buildActivity(TestActivity::class.java).setupVisibleSource()
        val errors = mutableListOf<StartupFailure>()
        try {
            activity.get().setWindowVisibilityForTest(View.GONE)
            launcher(activity.get()).launchActivityForResult(
                { OwnIdHiddenActivity.createIntent(it, InstanceName("hidden-source")) },
                { error, details -> errors += StartupFailure(error, details) }
            )

            assertThat(shadowOf(activity.get()).nextStartedActivity).isNull()
            assertThat(errors).hasSize(1)
            assertThat(errors.single().error).isInstanceOf(IllegalStateException::class.java)
            assertThat(errors.single().details).contains("no visible window")

            activity.get().setWindowVisibilityForTest(View.VISIBLE)
            launcher(activity.get()).launchActivityForResult(
                { OwnIdHiddenActivity.createIntent(it, InstanceName("visible-again")) },
                { error, details -> errors += StartupFailure(error, details) }
            )
            assertThat(shadowOf(activity.get()).nextStartedActivity.getStringExtra(INSTANCE_NAME_KEY))
                .isEqualTo("visible-again")
            assertThat(errors).hasSize(1)
        } finally {
            activity.destroyIfNeeded()
        }
    }

    @Test
    fun detachedSourceFailsBeforeStartAndAllowsRetry() {
        val decor = mockk<View> {
            every { isAttachedToWindow } returns false
            every { windowVisibility } returns View.VISIBLE
        }
        val window = mockk<Window> { every { peekDecorView() } returns decor }
        var startCalls = 0
        val activity = mockk<Activity> {
            every { isFinishing } returns false
            every { isDestroyed } returns false
            every { getWindow() } returns window
            every { startActivity(any()) } answers { startCalls += 1 }
        }
        val errors = mutableListOf<StartupFailure>()

        launcher(activity).launchActivityForResult(
            { Intent(app, OwnIdHiddenActivity::class.java) },
            { error, details -> errors += StartupFailure(error, details) }
        )

        assertThat(startCalls).isEqualTo(0)
        assertThat(errors).hasSize(1)
        assertThat(errors.single().error).isInstanceOf(IllegalStateException::class.java)
        launch("after-detached-source")
        assertThat(shadowOf(launchActivity).nextStartedActivity.getStringExtra(INSTANCE_NAME_KEY))
            .isEqualTo("after-detached-source")
    }

    @Test
    fun resolutionFailureSkipsIntentFactoryReportsSameErrorAndReleasesReservation() {
        val failure = IllegalStateException("no UI Activity")
        var factories = 0
        val errors = mutableListOf<StartupFailure>()
        HiddenActivityLauncherImpl(
            uiContextProvider = { UiContextProvider { UiContextProvider.Result.Failure(failure) } },
            instanceJob = Job(),
            logger = null
        ).launchActivityForResult(
            intentProvider = { factories += 1; OwnIdHiddenActivity.createIntent(it, InstanceName("never")) },
            onShowError = { error, details -> errors += StartupFailure(error, details) }
        )

        assertThat(factories).isEqualTo(0)
        assertThat(errors).hasSize(1)
        assertThat(errors.single().error).isSameInstanceAs(failure)
        assertThat(errors.single().details).contains("Failed to resolve UI Activity")
        launch("after-resolution-failure", errors)
        assertThat(shadowOf(launchActivity).nextStartedActivity.getStringExtra(INSTANCE_NAME_KEY))
            .isEqualTo("after-resolution-failure")
    }

    @Test
    fun immediateFactoryCancellationThenThrowHasNoStaleFailureOrLaunch() {
        val owner = Job()
        val failure = IllegalStateException("factory canceled and threw")
        val errors = mutableListOf<StartupFailure>()
        val launcher = HiddenActivityLauncherImpl(
            uiContextProvider = { UiContextProvider { UiContextProvider.Result.Success(launchActivity) } },
            instanceJob = owner,
            logger = null
        )

        launcher.launchActivityForResult(
            intentProvider = { owner.cancel(); throw failure },
            onShowError = { error, details -> errors += StartupFailure(error, details) }
        )

        assertThat(errors).isEmpty()
        assertThat(shadowOf(launchActivity).nextStartedActivity).isNull()
        launch("after-immediate-cancel-throw")
        assertThat(shadowOf(launchActivity).nextStartedActivity.getStringExtra(INSTANCE_NAME_KEY))
            .isEqualTo("after-immediate-cancel-throw")
    }

    @Config(sdk = [35], shadows = [VisibleImeViewRootShadow::class])
    @Test
    fun nonthrowingProviderOrFactoryCancellationStopsBeforeFactoryImeAndStart() {
        val parent = Robolectric.buildActivity(TestActivity::class.java).setupVisibleSource()
        val errors = mutableListOf<StartupFailure>()
        var factoryCalls = 0
        try {
            val initialInsetsReads = VisibleImeViewRootShadow.insetsReadCount
            val providerOwner = Job()
            HiddenActivityLauncherImpl(
                uiContextProvider = {
                    UiContextProvider {
                        providerOwner.cancel()
                        UiContextProvider.Result.Success(parent.get())
                    }
                },
                instanceJob = providerOwner,
                logger = null
            ).launchActivityForResult(
                intentProvider = { factoryCalls += 1; OwnIdHiddenActivity.createIntent(it, InstanceName("never-built")) },
                onShowError = { error, details -> errors += StartupFailure(error, details) }
            )
            assertThat(factoryCalls).isEqualTo(0)

            val factoryOwner = Job()
            HiddenActivityLauncherImpl(
                uiContextProvider = { UiContextProvider { UiContextProvider.Result.Success(parent.get()) } },
                instanceJob = factoryOwner,
                logger = null
            ).launchActivityForResult(
                intentProvider = {
                    factoryCalls += 1
                    factoryOwner.cancel()
                    OwnIdHiddenActivity.createIntent(it, InstanceName("never-started"))
                        .putExtra(OwnIdHiddenActivity.PREPARE_IME_HANDOFF_BEFORE_LAUNCH_KEY, true)
                },
                onShowError = { error, details -> errors += StartupFailure(error, details) }
            )

            idleMain()

            assertThat(factoryCalls).isEqualTo(1)
            assertThat(VisibleImeViewRootShadow.insetsReadCount).isEqualTo(initialInsetsReads)
            assertThat(shadowOf(parent.get()).nextStartedActivity).isNull()
            assertThat(errors).isEmpty()
            launch("after-nonthrowing-cancel")
            assertThat(shadowOf(launchActivity).nextStartedActivity.getStringExtra(INSTANCE_NAME_KEY))
                .isEqualTo("after-nonthrowing-cancel")
        } finally {
            parent.destroyIfNeeded()
        }
    }

    @Test
    fun hiddenHostErrorCloseKeepsLaunchGenerationRouterAfterSameNameReplacement() {
        OwnIdRootDIContainer.injectRootDefaults(app)
        val identitySuffix = UUID.randomUUID().toString().replace("-", "")
        val instanceName = InstanceName("retained-hidden-router-$identitySuffix").also(instancesToDestroy::add)
        val oldLogs = mutableListOf<String>()
        val newLogs = mutableListOf<String>()
        val delegate = DeferringDelegate()
        val oldContainer = OwnIdRootDIContainer.initializeInstanceContainer(
            instanceName,
            OwnIdConfigurationImpl(appId = "RetainedHiddenOld$identitySuffix")
        )
        oldContainer.register<OwnIdLogger>(recordingLogger(oldLogs))
        oldContainer.register<HiddenActivityLauncher.Delegate>(delegate)
        val host = Robolectric.buildActivity(
            OwnIdHiddenActivity::class.java,
            reserveIntent(OwnIdHiddenActivity.createIntent(app, instanceName))
        ).create()
        val errorDetails = "retained-hidden-router-error-$identitySuffix"

        try {
            assertThat(delegate.createdActivity).isSameInstanceAs(host.get())

            val replacement = OwnIdRootDIContainer.initializeInstanceContainer(
                instanceName,
                OwnIdConfigurationImpl(appId = "RetainedHiddenNew$identitySuffix")
            )
            replacement.register<OwnIdLogger>(recordingLogger(newLogs))

            host.get().closeActivityWithError(errorDetails)
            idleMain()

            assertThat(host.get().isFinishing).isTrue()
            assertThat(shadowOf(host.get()).resultCode).isNotEqualTo(Activity.RESULT_OK)
            assertThat(shadowOf(host.get()).resultIntent.getStringExtra(OwnIdActivityHostProtocol.CLOSE_REASON_KEY))
                .isEqualTo(errorDetails)
            assertThat(oldLogs.any { it.contains(errorDetails) }).isTrue()
            assertThat(newLogs.none { it.contains(errorDetails) }).isTrue()
        } finally {
            host.destroyIfNeeded()
            idleMain()
        }

        assertThat(delegate.destroyCount).isEqualTo(1)
    }

    private fun verifySharedActivityHostProtocol() {
        val errors = mutableListOf<StartupFailure>()

        launcher().launchActivityForResult(
            intentProvider = { context -> OwnIdHiddenActivity.createIntent(context, InstanceName("hidden-ui")) },
            onShowError = { error, details -> errors += StartupFailure(error, details) }
        )

        val hiddenStartedIntent = shadowOf(launchActivity).nextStartedActivity
        val hiddenIntent = hiddenStartedIntent.parcelRoundTrip()
        assertThat(hiddenIntent.component?.className).isEqualTo(OwnIdHiddenActivity::class.java.name)
        assertThat(hiddenIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isEqualTo(0)
        assertThat(hiddenIntent.flags and Intent.FLAG_ACTIVITY_NO_ANIMATION).isEqualTo(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        assertThat(hiddenIntent.getStringExtra(INSTANCE_NAME_KEY)).isEqualTo("hidden-ui")
        assertThat(hiddenIntent.resultReceiver()).isNotNull()
        hiddenIntent.resultReceiver()?.send(Activity.RESULT_OK, Bundle())
        idleMain()

        launcher().launchActivityForResult(
            intentProvider = { context ->
                OwnIdWebViewActivity.createIntent(
                    context,
                    InstanceName("webview-ui"),
                    OperationID(OperationType.WEB_BRIDGE, "operation")
                )
            },
            onShowError = { error, details -> errors += StartupFailure(error, details) }
        )

        val webViewStartedIntent = shadowOf(launchActivity).nextStartedActivity
        val webViewIntent = webViewStartedIntent.parcelRoundTrip()
        assertThat(webViewIntent.component?.className).isEqualTo(OwnIdWebViewActivity::class.java.name)
        assertThat(webViewIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isEqualTo(0)
        assertThat(webViewIntent.flags and Intent.FLAG_ACTIVITY_NO_ANIMATION).isEqualTo(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        assertThat(webViewIntent.getStringExtra(INSTANCE_NAME_KEY)).isEqualTo("webview-ui")
        assertThat(webViewIntent.getStringExtra(OPERATION_TYPE_KEY)).isEqualTo(OperationType.WEB_BRIDGE.name)
        assertThat(webViewIntent.getStringExtra(OPERATION_ID_KEY)).isEqualTo("operation")
        assertThat(webViewIntent.resultReceiver()).isNotNull()
        webViewIntent.resultReceiver()?.send(Activity.RESULT_OK, Bundle())
        idleMain()

        assertThat(errors).isEmpty()

        verifyHostClose(errorDetails = null, followUpInstanceName = "after-success")
        verifyHostClose(errorDetails = "host-error", followUpInstanceName = "after-error")
    }

    private fun verifyHostClose(errorDetails: String?, followUpInstanceName: String) {
        val delegate = ClosingDelegate(errorDetails)
        val instanceName = registerHiddenHost(delegate)
        val errors = mutableListOf<StartupFailure>()

        launcher().launchActivityForResult(
            intentProvider = { context -> OwnIdHiddenActivity.createIntent(context, instanceName) },
            onShowError = { error, details -> errors += StartupFailure(error, details) }
        )

        val host = Robolectric.buildActivity(OwnIdHiddenActivity::class.java, shadowOf(launchActivity).nextStartedActivity).create()
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

        launcher().launchActivityForResult(
            intentProvider = { context -> OwnIdHiddenActivity.createIntent(context, InstanceName(followUpInstanceName)) },
            onShowError = { error, details -> errors += StartupFailure(error, details) }
        )

        val followUpIntent = shadowOf(launchActivity).nextStartedActivity
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
        val hiddenActivityLauncher = launcher()

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

        val firstStartedIntent = shadowOf(launchActivity).nextStartedActivity

        assertThat(intentProviderCalls).isEqualTo(1)
        assertThat(errors).containsExactly(StartupFailure(null, "Another launch is already in progress."))
        assertThat(firstStartedIntent.getStringExtra(INSTANCE_NAME_KEY)).isEqualTo("first")
        assertThat(shadowOf(launchActivity).nextStartedActivity).isNull()
    }

    @Test
    fun silentlyRejectedStartExpiresOnceAndDuplicateAttemptsDoNotExtendDeadline() {
        val errors = mutableListOf<StartupFailure>()
        var startCalls = 0
        val rejectingActivity = Robolectric.buildActivity(TestActivity::class.java).setupVisibleSource().get().apply {
            onStartActivity = { startCalls += 1 }
        }
        launcher(rejectingActivity).launchActivityForResult(
            { OwnIdHiddenActivity.createIntent(it, InstanceName("rejected")) },
            { error, details -> errors += StartupFailure(error, details) }
        )
        repeat(2) {
            shadowOf(android.os.Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
            launch("duplicate-$it", errors)
        }
        assertThat(startCalls).isEqualTo(1)
        assertThat(errors).hasSize(2)
        assertThat(shadowOf(launchActivity).nextStartedActivity).isNull()
        shadowOf(android.os.Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
        assertThat(errors).hasSize(3)
        assertThat(errors.last().details).contains("not acknowledged within 3000 ms")
        launch("retry", errors)
        val retry = shadowOf(launchActivity).nextStartedActivity
        assertThat(retry.getStringExtra(INSTANCE_NAME_KEY)).isEqualTo("retry")
        retry.resultReceiver()?.send(Activity.RESULT_OK, null)
        idleMain()
        shadowOf(android.os.Looper.getMainLooper()).idleFor(15, TimeUnit.MINUTES)
        assertThat(errors).hasSize(3)
    }

    @Test
    fun runningSdkHostReportsStartupErrorWithoutStartingIntent() {
        val intent = reserveIntent(OwnIdHiddenActivity.createIntent(app, InstanceName("running")))
        OwnIdActivityHostProtocol.LaunchLock.onHostCreated(intent, Any())
        val errors = mutableListOf<StartupFailure>()

        launcher().launchActivityForResult(
            intentProvider = { context -> OwnIdHiddenActivity.createIntent(context, InstanceName("blocked")) },
            onShowError = { error, details -> errors += StartupFailure(error, details) }
        )

        assertThat(errors).containsExactly(StartupFailure(null, "Another launch is already in progress."))
        assertThat(shadowOf(launchActivity).nextStartedActivity).isNull()
    }

    @Test
    fun nonOkReceiverResultReportsStartupErrorAndReleasesLaunchLock() {
        val errors = mutableListOf<StartupFailure>()
        val hiddenActivityLauncher = launcher()

        hiddenActivityLauncher.launchActivityForResult(
            intentProvider = { context -> OwnIdHiddenActivity.createIntent(context, InstanceName("first")) },
            onShowError = { error, details -> errors += StartupFailure(error, details) }
        )

        val firstIntent = shadowOf(launchActivity).nextStartedActivity
        firstIntent.resultReceiver()?.send(
            Activity.RESULT_CANCELED,
            Bundle().apply { putString(OwnIdActivityHostProtocol.CLOSE_REASON_KEY, "receiver-failure") }
        )
        shadowOf(android.os.Looper.getMainLooper()).idle()

        hiddenActivityLauncher.launchActivityForResult(
            intentProvider = { context -> OwnIdHiddenActivity.createIntent(context, InstanceName("second")) },
            onShowError = { error, details -> errors += StartupFailure(error, details) }
        )

        val secondStartedIntent = shadowOf(launchActivity).nextStartedActivity

        assertThat(errors).containsExactly(StartupFailure(null, "receiver-failure"))
        assertThat(secondStartedIntent.getStringExtra(INSTANCE_NAME_KEY)).isEqualTo("second")
    }

    @Test
    fun launchLockStaysHeldAcrossConfigurationHandoffAndReleasesAfterReplacementDestroy() {
        val errors = mutableListOf<StartupFailure>()
        val intent = reserveIntent(OwnIdHiddenActivity.createIntent(app, InstanceName("rotation")))
        val oldHost = Any()
        val replacementHost = Any()
        OwnIdActivityHostProtocol.LaunchLock.onHostCreated(intent, oldHost)
        OwnIdActivityHostProtocol.LaunchLock.onHostDestroyed(oldHost, keepForRecreation = true)
        shadowOf(android.os.Looper.getMainLooper()).idleFor(15, TimeUnit.MINUTES)

        launcher().launchActivityForResult(
            intentProvider = { context -> OwnIdHiddenActivity.createIntent(context, InstanceName("during-recreation")) },
            onShowError = { error, details -> errors += StartupFailure(error, details) }
        )

        assertThat(errors).containsExactly(StartupFailure(null, "Another launch is already in progress."))
        assertThat(shadowOf(launchActivity).nextStartedActivity).isNull()

        assertThat(OwnIdActivityHostProtocol.LaunchLock.onHostCreated(intent, replacementHost)).isTrue()
        OwnIdActivityHostProtocol.LaunchLock.onHostDestroyed(oldHost, keepForRecreation = false)
        launch("old-destroy-must-not-release-replacement", errors)
        assertThat(shadowOf(launchActivity).nextStartedActivity).isNull()
        OwnIdActivityHostProtocol.LaunchLock.onHostDestroyed(replacementHost, keepForRecreation = false)
        launcher().launchActivityForResult(
            intentProvider = { context -> OwnIdHiddenActivity.createIntent(context, InstanceName("after-recreation")) },
            onShowError = { error, details -> errors += StartupFailure(error, details) }
        )

        assertThat(shadowOf(launchActivity).nextStartedActivity.getStringExtra(INSTANCE_NAME_KEY)).isEqualTo("after-recreation")
    }

    @Test
    fun startupFailureReportsErrorAndReleasesLaunchReservation() {
        val errors = mutableListOf<StartupFailure>()
        val throwingActivity = Robolectric.buildActivity(TestActivity::class.java).setupVisibleSource().get().apply {
            onStartActivity = { throw IllegalStateException("cannot launch") }
        }

        launcher(throwingActivity).launchActivityForResult(
            intentProvider = { context -> OwnIdHiddenActivity.createIntent(context, InstanceName("failure")) },
            onShowError = { error, details -> errors += StartupFailure(error, details) }
        )
        launcher().launchActivityForResult(
            intentProvider = { context -> OwnIdHiddenActivity.createIntent(context, InstanceName("retry")) },
            onShowError = { error, details -> errors += StartupFailure(error, details) }
        )

        assertThat(errors).hasSize(1)
        assertThat(errors.single().error).isInstanceOf(IllegalStateException::class.java)
        assertThat(errors.single().details).contains("cannot launch")
        assertThat(shadowOf(launchActivity).nextStartedActivity.getStringExtra(INSTANCE_NAME_KEY)).isEqualTo("retry")
    }

    @Test
    fun imeHandoffRequestLaunchesImmediatelyWhenParentImeIsNotVisible() {
        val activity = Robolectric.buildActivity(TestActivity::class.java).setupVisibleSource()
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

    @Test
    fun acknowledgedHostDoesNotExpireAndDuplicateHostCreationCannotClaimIt() {
        val delegate = DeferringDelegate()
        val instanceName = registerHiddenHost(delegate)
        val errors = mutableListOf<StartupFailure>()
        launch(instanceName.value, errors)
        val intent = shadowOf(launchActivity).nextStartedActivity.parcelRoundTrip()
        val host = Robolectric.buildActivity(OwnIdHiddenActivity::class.java, intent).create()
        val duplicate = Robolectric.buildActivity(OwnIdHiddenActivity::class.java, intent).create()
        try {
            assertThat(delegate.createdActivity).isSameInstanceAs(host.get())
            assertThat(duplicate.get().isFinishing).isTrue()
            duplicate.destroyIfNeeded()
            shadowOf(Looper.getMainLooper()).idleFor(15, TimeUnit.MINUTES)
            assertThat(errors).isEmpty()
            launch("still-running", errors)
            assertThat(shadowOf(launchActivity).nextStartedActivity).isNull()
            assertThat(errors.single().details).isEqualTo("Another launch is already in progress.")
        } finally {
            duplicate.destroyIfNeeded()
            host.destroyIfNeeded()
        }
        launch("after-destroy", errors)
        assertThat(shadowOf(launchActivity).nextStartedActivity).isNotNull()
    }

    @Test
    fun expiredHostsAndResultsCannotAffectNewPendingOrRunningHostForEitherTarget() {
        for (webView in listOf(false, true)) {
            for (running in listOf(false, true)) {
                val errors = mutableListOf<StartupFailure>()
                val oldDelegate = DeferringDelegate()
                val oldInstance = registerHiddenHost(oldDelegate)
                launcher().launchActivityForResult(
                    { context ->
                        if (webView) OwnIdWebViewActivity.createIntent(context, oldInstance, OperationID(OperationType.WEB_BRIDGE, "old"))
                        else OwnIdHiddenActivity.createIntent(context, oldInstance)
                    },
                    { error, details -> errors += StartupFailure(error, details) }
                )
                val oldIntent = shadowOf(launchActivity).nextStartedActivity.parcelRoundTrip()
                shadowOf(Looper.getMainLooper()).idleFor(3, TimeUnit.SECONDS)
                assertThat(errors).hasSize(1)
                val newDelegate = DeferringDelegate()
                launch(registerHiddenHost(newDelegate).value, errors)
                val newIntent = shadowOf(launchActivity).nextStartedActivity
                var newHost: ActivityController<OwnIdHiddenActivity>? = null
                if (running) newHost = Robolectric.buildActivity(OwnIdHiddenActivity::class.java, newIntent).create()
                val oldHost = if (webView) Robolectric.buildActivity(OwnIdWebViewActivity::class.java, oldIntent).create()
                else Robolectric.buildActivity(OwnIdHiddenActivity::class.java, oldIntent).create()
                try {
                    assertThat(oldHost.get().isFinishing).isTrue()
                    assertThat(oldDelegate.createdActivity).isNull()
                    oldIntent.resultReceiver()?.send(Activity.RESULT_CANCELED, Bundle().apply {
                        putString(OwnIdActivityHostProtocol.CLOSE_REASON_KEY, "late-old-result")
                    })
                    idleMain()
                    oldHost.destroyIfNeeded()
                    assertThat(errors).hasSize(1)
                    launch("duplicate-new", errors)
                    assertThat(shadowOf(launchActivity).nextStartedActivity).isNull()
                    if (newHost == null) newHost = Robolectric.buildActivity(OwnIdHiddenActivity::class.java, newIntent).create()
                    val acceptedHost = checkNotNull(newHost)
                    assertThat(newDelegate.createdActivity).isSameInstanceAs(acceptedHost.get())
                    assertThat(acceptedHost.get().isFinishing).isFalse()
                } finally {
                    oldHost.destroyIfNeeded()
                    newHost?.destroyIfNeeded()
                }
            }
        }
    }

    @Test
    fun hostClaimChecksDeadlineEvenBeforeQueuedWatchdogRuns() {
        val delegate = DeferringDelegate()
        val errors = mutableListOf<StartupFailure>()
        launch(registerHiddenHost(delegate).value, errors)
        val expiredIntent = shadowOf(launchActivity).nextStartedActivity
        ShadowSystemClock.advanceBy(3, TimeUnit.SECONDS)
        assertThat(errors).isEmpty()
        val host = Robolectric.buildActivity(OwnIdHiddenActivity::class.java, expiredIntent).create()
        try {
            assertThat(host.get().isFinishing).isTrue()
            assertThat(delegate.createdActivity).isNull()
            assertThat(errors.single().details).contains("not acknowledged")
            launch("next", errors)
            assertThat(shadowOf(launchActivity).nextStartedActivity).isNotNull()
            idleMain()
            assertThat(errors).hasSize(1)
        } finally {
            host.destroyIfNeeded()
        }
    }

    @Test
    fun nextAdmissionChecksDeadlineBeforeQueuedWatchdogAndTimeoutCallbackCanReenter() {
        val errors = mutableListOf<StartupFailure>()
        launcher().launchActivityForResult(
            { OwnIdHiddenActivity.createIntent(it, InstanceName("expired")) },
            { error, details ->
                errors += StartupFailure(error, details)
                launch("callback-retry", errors)
            }
        )
        shadowOf(launchActivity).nextStartedActivity
        ShadowSystemClock.advanceBy(3, TimeUnit.SECONDS)
        launch("outer-retry", errors)
        assertThat(shadowOf(launchActivity).nextStartedActivity.getStringExtra(INSTANCE_NAME_KEY)).isEqualTo("callback-retry")
        assertThat(shadowOf(launchActivity).nextStartedActivity).isNull()
        assertThat(errors).hasSize(2)
        assertThat(errors.first().details).contains("not acknowledged")
        assertThat(errors.last().details).isEqualTo("Another launch is already in progress.")
    }

    @Test
    fun normalErrorQueuedAfterDestructionIsDeliveredOnceWithoutReleasingNewPendingHost() {
        val errors = mutableListOf<StartupFailure>()
        val instanceName = registerHiddenHost(DeferringDelegate())
        launch(instanceName.value, errors)
        val intent = shadowOf(launchActivity).nextStartedActivity
        val host = Robolectric.buildActivity(OwnIdHiddenActivity::class.java, intent).create()
        host.get().closeActivityWithError("expected-error")
        host.destroyIfNeeded()
        launch("new-pending", errors)
        assertThat(shadowOf(launchActivity).nextStartedActivity.getStringExtra(INSTANCE_NAME_KEY)).isEqualTo("new-pending")
        intent.resultReceiver()?.send(Activity.RESULT_CANCELED, Bundle().apply {
            putString(OwnIdActivityHostProtocol.CLOSE_REASON_KEY, "duplicate-error")
        })
        idleMain()
        assertThat(errors).containsExactly(StartupFailure(null, "expected-error"))
        launch("must-stay-blocked", errors)
        assertThat(shadowOf(launchActivity).nextStartedActivity).isNull()
        assertThat(errors).hasSize(2)
    }

    @Test
    fun errorResultDoesNotReleaseHostBeforeFinalDestruction() {
        val errors = mutableListOf<StartupFailure>()
        launch(registerHiddenHost(DeferringDelegate()).value, errors)
        val intent = shadowOf(launchActivity).nextStartedActivity
        val host = Robolectric.buildActivity(OwnIdHiddenActivity::class.java, intent).create()
        try {
            host.get().closeActivityWithError("closing")
            idleMain()
            launch("too-soon", errors)
            assertThat(shadowOf(launchActivity).nextStartedActivity).isNull()
            assertThat(errors).hasSize(2)
        } finally {
            host.destroyIfNeeded()
        }
        launch("after-close", errors)
        assertThat(shadowOf(launchActivity).nextStartedActivity).isNotNull()
    }

    @Test
    fun timeoutCallbackThrowingStillReleasesOnlyExpiredAttempt() {
        var callbacks = 0
        launcher().launchActivityForResult(
            { OwnIdHiddenActivity.createIntent(it, InstanceName("timeout")) },
            { _, _ ->
                callbacks += 1
                throw IllegalArgumentException("callback failure")
            }
        )
        val oldIntent = shadowOf(launchActivity).nextStartedActivity
        assertThrows(IllegalArgumentException::class.java) {
            shadowOf(Looper.getMainLooper()).idleFor(3, TimeUnit.SECONDS)
        }
        launch("after-throw")
        assertThat(shadowOf(launchActivity).nextStartedActivity).isNotNull()
        oldIntent.resultReceiver()?.send(Activity.RESULT_CANCELED, null)
        idleMain()
        assertThat(callbacks).isEqualTo(1)
    }

    @Test
    fun startupFailureCallbackCanReenterAndThrowWithoutLeavingReservation() {
        val throwingActivity = Robolectric.buildActivity(TestActivity::class.java).setupVisibleSource().get().apply {
            onStartActivity = { throw IllegalStateException("start rejected") }
        }
        var callbacks = 0
        assertThrows(IllegalArgumentException::class.java) {
            launcher(throwingActivity).launchActivityForResult(
                { OwnIdHiddenActivity.createIntent(it, InstanceName("failed")) },
                { _, _ ->
                    callbacks += 1
                    launch("callback-retry")
                    throw IllegalArgumentException("callback failure")
                }
            )
        }
        assertThat(shadowOf(launchActivity).nextStartedActivity.getStringExtra(INSTANCE_NAME_KEY)).isEqualTo("callback-retry")
        assertThat(callbacks).isEqualTo(1)
        launch("still-busy")
        assertThat(shadowOf(launchActivity).nextStartedActivity).isNull()
    }

    @Test
    fun instanceShutdownCancelsOnlyItsPendingAttemptAndLateHost() {
        val job = Job()
        val errors = mutableListOf<StartupFailure>()
        launch("cancelled", errors, job)
        val oldIntent = shadowOf(launchActivity).nextStartedActivity
        job.cancel()
        launch("other-instance", errors)
        val nextIntent = shadowOf(launchActivity).nextStartedActivity
        val lateHost = Robolectric.buildActivity(OwnIdHiddenActivity::class.java, oldIntent).create()
        lateHost.destroyIfNeeded()
        oldIntent.resultReceiver()?.send(Activity.RESULT_CANCELED, null)
        idleMain()
        assertThat(errors).isEmpty()
        launch("must-stay-busy", errors)
        assertThat(shadowOf(launchActivity).nextStartedActivity).isNull()
        nextIntent.resultReceiver()?.send(Activity.RESULT_OK, null)
        idleMain()
        shadowOf(Looper.getMainLooper()).idleFor(3, TimeUnit.SECONDS)
        assertThat(errors).hasSize(1)
    }

    @Test
    fun queuedBackgroundCancellationCannotReleaseReplacementAdmittedBeforeMainQueueDrains() {
        val oldJob = Job()
        val errors = mutableListOf<StartupFailure>()
        val oldDelegate = DeferringDelegate()
        launch(registerHiddenHost(oldDelegate).value, errors, oldJob)
        val oldIntent = shadowOf(launchActivity).nextStartedActivity
        val worker = Thread { oldJob.cancel() }
        worker.start()
        worker.join()

        // The owner's completion has posted cancellation, but admission must reconcile its canceled Job immediately.
        val replacementDelegate = DeferringDelegate()
        launch(registerHiddenHost(replacementDelegate).value, errors)
        val replacementIntent = shadowOf(launchActivity).nextStartedActivity
        assertThat(replacementIntent).isNotNull()
        idleMain()
        assertThat(errors).isEmpty()

        launch("blocked-by-replacement", errors)
        assertThat(shadowOf(launchActivity).nextStartedActivity).isNull()
        assertThat(errors.single().details).isEqualTo("Another launch is already in progress.")
        val oldHost = Robolectric.buildActivity(OwnIdHiddenActivity::class.java, oldIntent).create()
        val replacementHost = Robolectric.buildActivity(OwnIdHiddenActivity::class.java, replacementIntent).create()
        try {
            assertThat(oldHost.get().isFinishing).isTrue()
            assertThat(oldDelegate.createdActivity).isNull()
            oldHost.destroyIfNeeded()
            assertThat(replacementDelegate.createdActivity).isSameInstanceAs(replacementHost.get())
            assertThat(replacementHost.get().isFinishing).isFalse()
            launch("blocked-by-running-replacement", errors)
            assertThat(shadowOf(launchActivity).nextStartedActivity).isNull()
            assertThat(errors).hasSize(2)
        } finally {
            oldHost.destroyIfNeeded()
            replacementHost.destroyIfNeeded()
        }
    }

    @Test
    fun pendingResultQueuedBeforeOwnerCancellationIsIgnoredWhenMainQueueDrains() {
        val job = Job()
        val errors = mutableListOf<StartupFailure>()
        launch("pending", errors, job)
        val intent = shadowOf(launchActivity).nextStartedActivity.parcelRoundTrip()
        intent.resultReceiver()?.send(Activity.RESULT_CANCELED, Bundle().apply {
            putString(OwnIdActivityHostProtocol.CLOSE_REASON_KEY, "queued-before-cancellation")
        })
        assertThat(errors).isEmpty()
        job.cancel()
        idleMain()
        assertThat(errors).isEmpty()
        launch("replacement", errors)
        assertThat(shadowOf(launchActivity).nextStartedActivity).isNotNull()
    }

    @Test
    fun pendingResultDelayedPastDeadlineReportsOnlyTimeoutBeforeWatchdogRuns() {
        val errors = mutableListOf<StartupFailure>()
        launch("pending", errors)
        val intent = shadowOf(launchActivity).nextStartedActivity.parcelRoundTrip()
        // Queue the result ahead of the watchdog, then delay delivery without draining either callback.
        intent.resultReceiver()?.send(Activity.RESULT_CANCELED, Bundle().apply {
            putString(OwnIdActivityHostProtocol.CLOSE_REASON_KEY, "stale-pending-error")
        })
        ShadowSystemClock.advanceBy(3, TimeUnit.SECONDS)
        assertThat(errors).isEmpty()
        idleMain()
        assertThat(errors).hasSize(1)
        assertThat(errors.single().details).contains("not acknowledged within 3000 ms")
        launch("replacement", errors)
        assertThat(shadowOf(launchActivity).nextStartedActivity).isNotNull()
    }

    @Test
    fun acceptedHostResultRemainsDeliverableAfterOwnerCancellation() {
        val job = Job()
        val errors = mutableListOf<StartupFailure>()
        launch(registerHiddenHost(DeferringDelegate()).value, errors, job)
        val host = Robolectric.buildActivity(OwnIdHiddenActivity::class.java, shadowOf(launchActivity).nextStartedActivity).create()
        try {
            host.get().closeActivityWithError("accepted-host-error")
            job.cancel()
            idleMain()
            assertThat(errors).containsExactly(StartupFailure(null, "accepted-host-error"))
            launch("still-running", errors)
            assertThat(shadowOf(launchActivity).nextStartedActivity).isNull()
        } finally {
            host.destroyIfNeeded()
        }
    }

    @Test
    fun ownerCompletingDuringHandlerRegistrationCannotDispatchOrPoisonNextLaunch() {
        val owner = Job()
        val job = object : Job by owner {
            override fun invokeOnCompletion(handler: kotlinx.coroutines.CompletionHandler): kotlinx.coroutines.DisposableHandle {
                owner.cancel()
                return owner.invokeOnCompletion(handler)
            }
        }
        val errors = mutableListOf<StartupFailure>()
        launch("cancelled-during-registration", errors, job)
        assertThat(shadowOf(launchActivity).nextStartedActivity).isNull()
        assertThat(errors).isEmpty()
        launch("replacement", errors)
        assertThat(shadowOf(launchActivity).nextStartedActivity).isNotNull()
        idleMain()
        launch("still-pending", errors)
        assertThat(shadowOf(launchActivity).nextStartedActivity).isNull()
        assertThat(errors.single().details).isEqualTo("Another launch is already in progress.")
    }

    @Test
    fun instanceShutdownAfterAcknowledgementCannotUnlockRunningHost() {
        val job = Job()
        val errors = mutableListOf<StartupFailure>()
        launch(registerHiddenHost(DeferringDelegate()).value, errors, job)
        val host = Robolectric.buildActivity(OwnIdHiddenActivity::class.java, shadowOf(launchActivity).nextStartedActivity).create()
        try {
            job.cancel()
            shadowOf(Looper.getMainLooper()).idleFor(3, TimeUnit.SECONDS)
            launch("must-stay-busy", errors)
            assertThat(shadowOf(launchActivity).nextStartedActivity).isNull()
            assertThat(errors.single().details).isEqualTo("Another launch is already in progress.")
        } finally {
            host.destroyIfNeeded()
        }
    }

    @Test
    fun defaultLauncherUsesInstanceJobWhenInstanceIsDestroyed() {
        verifyDefaultLauncherOwnerCancellation(replaceSameName = false)
    }

    @Test
    fun defaultLauncherUsesOriginalInstanceJobAfterSameNameReplacement() {
        verifyDefaultLauncherOwnerCancellation(replaceSameName = true)
    }

    private fun verifyDefaultLauncherOwnerCancellation(replaceSameName: Boolean) {
        val oldDelegate = DeferringDelegate()
        val oldName = registerHiddenHost(oldDelegate)
        val oldContainer = checkNotNull(OwnId.getInstanceContainer(oldName))
        oldContainer.register<UiContextProvider>(UiContextProvider { UiContextProvider.Result.Success(launchActivity) })
        val oldJob = oldContainer.getOrThrow<Job>()
        val errors = mutableListOf<StartupFailure>()
        oldContainer.getOrThrow<HiddenActivityLauncher>().launchActivityForResult(
            { OwnIdHiddenActivity.createIntent(it, oldName) },
            { error, details -> errors += StartupFailure(error, details) }
        )
        val oldIntent = shadowOf(launchActivity).nextStartedActivity
        assertThat(oldIntent).isNotNull()

        val newDelegate = DeferringDelegate()
        val newName = if (replaceSameName) {
            OwnIdRootDIContainer.initializeInstanceContainer(
                oldName,
                OwnIdConfigurationImpl(appId = "Replacement${UUID.randomUUID().toString().replace("-", "")}")
            ).register<HiddenActivityLauncher.Delegate>(newDelegate)
            oldName
        } else {
            OwnId.destroy(oldName)
            registerHiddenHost(newDelegate)
        }
        val newContainer = checkNotNull(OwnId.getInstanceContainer(newName))
        newContainer.register<UiContextProvider>(UiContextProvider { UiContextProvider.Result.Success(launchActivity) })
        assertThat(oldJob.isActive).isFalse()
        assertThat(newContainer.getOrThrow<Job>().isActive).isTrue()
        newContainer.getOrThrow<HiddenActivityLauncher>().launchActivityForResult(
            { OwnIdHiddenActivity.createIntent(it, newName) },
            { error, details -> errors += StartupFailure(error, details) }
        )
        val newIntent = shadowOf(launchActivity).nextStartedActivity
        assertThat(newIntent).isNotNull()
        val oldHost = Robolectric.buildActivity(OwnIdHiddenActivity::class.java, oldIntent).create()
        val newHost = Robolectric.buildActivity(OwnIdHiddenActivity::class.java, newIntent).create()
        try {
            assertThat(oldHost.get().isFinishing).isTrue()
            assertThat(oldDelegate.createdActivity).isNull()
            assertThat(newDelegate.createdActivity).isSameInstanceAs(newHost.get())
            oldHost.destroyIfNeeded()
            idleMain()
            assertThat(errors).isEmpty()
            launch("blocked-by-new-instance", errors)
            assertThat(shadowOf(launchActivity).nextStartedActivity).isNull()
            assertThat(errors.single().details).isEqualTo("Another launch is already in progress.")
        } finally {
            oldHost.destroyIfNeeded()
            newHost.destroyIfNeeded()
        }
    }

    @Test
    fun defaultLauncherAvailabilityRequiresItsInstanceJob() {
        val name = registerHiddenHost(DeferringDelegate())
        val container = checkNotNull(OwnId.getInstanceContainer(name))
        container.register<UiContextProvider>(UiContextProvider { UiContextProvider.Result.Success(launchActivity) })
        val job = container.getOrThrow<Job>()
        assertThat(container.canResolve(HiddenActivityLauncher::class.java)).isTrue()
        try {
            container.remove(Job::class.java)
            assertThat(container.canResolve(HiddenActivityLauncher::class.java)).isFalse()
        } finally {
            container.register<Job>(job)
        }
    }

    @Test
    fun throwingDelegateDestructionStillReleasesHostOwnership() {
        val delegate = object : HiddenActivityLauncher.Delegate {
            override fun isThis(intent: Intent): Boolean = true
            override fun onCreate(activity: ComponentActivity, instanceResolver: DIContainerResolver) = Unit
            override fun onDestroy(activity: ComponentActivity) {
                throw IllegalStateException("delegate teardown failed")
            }
        }
        launch(registerHiddenHost(delegate).value)
        val host = Robolectric.buildActivity(OwnIdHiddenActivity::class.java, shadowOf(launchActivity).nextStartedActivity).create()
        assertThrows(IllegalStateException::class.java) { host.destroy() }
        launch("after-throwing-destruction")
        assertThat(shadowOf(launchActivity).nextStartedActivity).isNotNull()
    }

    @Test
    fun realHiddenHostRecreationRetainsOwnershipAndAcceptsReplacement() {
        val delegate = DeferringDelegate()
        val errors = mutableListOf<StartupFailure>()
        launch(registerHiddenHost(delegate).value, errors)
        val host = Robolectric.buildActivity(OwnIdHiddenActivity::class.java, shadowOf(launchActivity).nextStartedActivity).setup()
        val original = host.get()
        try {
            host.recreate()
            assertThat(host.get()).isNotSameInstanceAs(original)
            assertThat(original.isDestroyed).isTrue()
            assertThat(delegate.createdActivity).isSameInstanceAs(host.get())
            assertThat(delegate.destroyCount).isEqualTo(1)
            assertThat(host.get().isFinishing).isFalse()
            shadowOf(Looper.getMainLooper()).idleFor(15, TimeUnit.MINUTES)
            assertThat(errors).isEmpty()
            launch("blocked-by-recreated-host", errors)
            assertThat(shadowOf(launchActivity).nextStartedActivity).isNull()
            assertThat(errors.single().details).isEqualTo("Another launch is already in progress.")
        } finally {
            host.destroyIfNeeded()
        }
        launch("after-recreated-host-destroyed")
        assertThat(shadowOf(launchActivity).nextStartedActivity).isNotNull()
    }

    @Config(sdk = [35], shadows = [VisibleImeViewRootShadow::class])
    @Test
    fun shutdownDuringVisibleImeHandoffCancelsDeferredDispatchAndListener() {
        val parent = Robolectric.buildActivity(TestActivity::class.java).setupVisibleSource()
        val job = Job()
        val errors = mutableListOf<StartupFailure>()
        try {
            val observer = parent.get().window.decorView.viewTreeObserver
            val baselineInsetsReads = observer.dispatchAndCountInsetsReads()
            launcher(parent.get(), job).launchActivityForResult(
                {
                    OwnIdHiddenActivity.createIntent(it, InstanceName("cancelled-ime"))
                        .putExtra(OwnIdHiddenActivity.PREPARE_IME_HANDOFF_BEFORE_LAUNCH_KEY, true)
                },
                { error, details -> errors += StartupFailure(error, details) }
            )
            assertThat(errors).isEmpty()
            assertThat(shadowOf(parent.get()).nextStartedActivity).isNull()
            assertThat(observer.dispatchAndCountInsetsReads()).isGreaterThan(baselineInsetsReads)
            job.cancel()
            idleMain()
            val canceledInsetsReads = observer.dispatchAndCountInsetsReads()
            shadowOf(Looper.getMainLooper()).idleFor(3, TimeUnit.SECONDS)
            assertThat(shadowOf(parent.get()).nextStartedActivity).isNull()
            assertThat(errors).isEmpty()
            assertThat(canceledInsetsReads).isEqualTo(baselineInsetsReads)
            launch("after-ime-cancel", errors)
            assertThat(shadowOf(launchActivity).nextStartedActivity).isNotNull()
        } finally {
            parent.destroyIfNeeded()
        }
    }

    @Config(sdk = [35], shadows = [VisibleImeViewRootShadow::class])
    @Test
    fun visibleImeFallbackDispatchesOnceAfterBoundedWaitAndRemovesListener() {
        val parent = Robolectric.buildActivity(TestActivity::class.java).setupVisibleSource()
        val errors = mutableListOf<StartupFailure>()
        val delegate = DeferringDelegate()
        val name = registerHiddenHost(delegate)
        var host: ActivityController<OwnIdHiddenActivity>? = null
        try {
            val observer = parent.get().window.decorView.viewTreeObserver
            val baselineInsetsReads = observer.dispatchAndCountInsetsReads()
            launcher(parent.get()).launchActivityForResult(
                {
                    OwnIdHiddenActivity.createIntent(it, name)
                        .putExtra(OwnIdHiddenActivity.PREPARE_IME_HANDOFF_BEFORE_LAUNCH_KEY, true)
                },
                { error, details -> errors += StartupFailure(error, details) }
            )
            assertThat(observer.dispatchAndCountInsetsReads()).isGreaterThan(baselineInsetsReads)
            shadowOf(Looper.getMainLooper()).idleFor(249, TimeUnit.MILLISECONDS)
            assertThat(shadowOf(parent.get()).nextStartedActivity).isNull()
            shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.MILLISECONDS)
            val intent = shadowOf(parent.get()).nextStartedActivity
            assertThat(intent).isNotNull()
            assertThat(observer.dispatchAndCountInsetsReads()).isEqualTo(baselineInsetsReads)
            assertThat(shadowOf(parent.get()).nextStartedActivity).isNull()
            host = Robolectric.buildActivity(OwnIdHiddenActivity::class.java, intent).create()
            assertThat(delegate.createdActivity).isSameInstanceAs(host.get())
            shadowOf(Looper.getMainLooper()).idleFor(3, TimeUnit.SECONDS)
            assertThat(errors).isEmpty()
        } finally {
            host?.destroyIfNeeded()
            parent.destroyIfNeeded()
        }
    }

    @Config(sdk = [35], shadows = [VisibleImeViewRootShadow::class])
    @Test
    fun deferredImeLaunchResolvesFinalActivityWithoutRebuildingIntent() {
        val first = Robolectric.buildActivity(TestActivity::class.java).setupVisibleSource()
        val second = Robolectric.buildActivity(TestActivity::class.java).setupVisibleSource()
        var lookups = 0
        var factories = 0
        var intent: Intent? = null
        val startsBySecond = mutableListOf<Intent>()
        val errors = mutableListOf<StartupFailure>()
        try {
            second.get().onStartActivity = { startsBySecond += it }
            val launcher = HiddenActivityLauncherImpl(
                uiContextProvider = {
                    UiContextProvider {
                        lookups += 1
                        UiContextProvider.Result.Success(if (lookups == 1) first.get() else second.get())
                    }
                },
                instanceJob = Job(),
                logger = null
            )
            launcher.launchActivityForResult(
                intentProvider = { source ->
                    factories += 1
                    assertThat(source).isSameInstanceAs(first.get())
                    OwnIdHiddenActivity.createIntent(source, InstanceName("retargeted"))
                        .putExtra(OwnIdHiddenActivity.PREPARE_IME_HANDOFF_BEFORE_LAUNCH_KEY, true)
                        .also { intent = it }
                },
                onShowError = { error, details -> errors += StartupFailure(error, details) }
            )

            assertThat(lookups).isEqualTo(1)
            assertThat(factories).isEqualTo(1)
            assertThat(shadowOf(first.get()).nextStartedActivity).isNull()
            assertThat(shadowOf(second.get()).nextStartedActivity).isNull()

            shadowOf(Looper.getMainLooper()).idleFor(250, TimeUnit.MILLISECONDS)

            assertThat(lookups).isEqualTo(2)
            assertThat(factories).isEqualTo(1)
            assertThat(shadowOf(first.get()).nextStartedActivity).isNull()
            assertThat(startsBySecond).containsExactly(intent)
            assertThat(errors).isEmpty()
        } finally {
            first.destroyIfNeeded()
            second.destroyIfNeeded()
        }
    }

    @Config(sdk = [35], shadows = [VisibleImeViewRootShadow::class])
    @Test
    fun deferredImeLaunchRejectsSourceThatBecomesHidden() {
        val activity = Robolectric.buildActivity(TestActivity::class.java).setupVisibleSource()
        val errors = mutableListOf<StartupFailure>()
        try {
            launcher(activity.get()).launchActivityForResult(
                {
                    OwnIdHiddenActivity.createIntent(it, InstanceName("hidden-after-ime"))
                        .putExtra(OwnIdHiddenActivity.PREPARE_IME_HANDOFF_BEFORE_LAUNCH_KEY, true)
                },
                { error, details -> errors += StartupFailure(error, details) }
            )
            assertThat(shadowOf(activity.get()).nextStartedActivity).isNull()

            activity.get().setWindowVisibilityForTest(View.GONE)
            shadowOf(Looper.getMainLooper()).idleFor(250, TimeUnit.MILLISECONDS)

            assertThat(shadowOf(activity.get()).nextStartedActivity).isNull()
            assertThat(errors).hasSize(1)
            assertThat(errors.single().details).contains("no visible window")
        } finally {
            activity.destroyIfNeeded()
        }
    }

    @Config(sdk = [35], shadows = [VisibleImeViewRootShadow::class])
    @Test
    fun deferredImeResolutionFailureReportsOnceCleansListenerAndAllowsRetry() {
        val parent = Robolectric.buildActivity(TestActivity::class.java).setupVisibleSource()
        val failure = IllegalStateException("host stopped")
        val errors = mutableListOf<StartupFailure>()
        var lookups = 0
        var factories = 0
        try {
            val observer = parent.get().window.decorView.viewTreeObserver
            val baselineInsetsReads = observer.dispatchAndCountInsetsReads()
            val launcher = HiddenActivityLauncherImpl(
                uiContextProvider = {
                    UiContextProvider {
                        lookups += 1
                        if (lookups == 1) UiContextProvider.Result.Success(parent.get())
                        else UiContextProvider.Result.Failure(failure)
                    }
                },
                instanceJob = Job(),
                logger = null
            )
            launcher.launchActivityForResult(
                intentProvider = {
                    factories += 1
                    OwnIdHiddenActivity.createIntent(it, InstanceName("no-final-host"))
                        .putExtra(OwnIdHiddenActivity.PREPARE_IME_HANDOFF_BEFORE_LAUNCH_KEY, true)
                },
                onShowError = { error, details -> errors += StartupFailure(error, details) }
            )
            assertThat(observer.dispatchAndCountInsetsReads()).isGreaterThan(baselineInsetsReads)

            shadowOf(Looper.getMainLooper()).idleFor(250, TimeUnit.MILLISECONDS)
            assertThat(lookups).isEqualTo(2)
            assertThat(factories).isEqualTo(1)
            assertThat(shadowOf(parent.get()).nextStartedActivity).isNull()
            assertThat(errors).hasSize(1)
            assertThat(errors.single().error).isSameInstanceAs(failure)
            assertThat(observer.dispatchAndCountInsetsReads()).isEqualTo(baselineInsetsReads)

            shadowOf(Looper.getMainLooper()).idleFor(3, TimeUnit.SECONDS)
            assertThat(errors).hasSize(1)
            launch("after-deferred-failure")
            assertThat(shadowOf(launchActivity).nextStartedActivity.getStringExtra(INSTANCE_NAME_KEY))
                .isEqualTo("after-deferred-failure")
        } finally {
            parent.destroyIfNeeded()
        }
    }

    @Config(sdk = [35], shadows = [VisibleImeViewRootShadow::class])
    @Test
    fun deferredLookupThrowWithReentrantLoggerCancellationHasNoStaleFailure() {
        val parent = Robolectric.buildActivity(TestActivity::class.java).setupVisibleSource()
        val owner = Job()
        val failure = IllegalStateException("late lookup failed")
        val errors = mutableListOf<StartupFailure>()
        var lookups = 0
        try {
            val observer = parent.get().window.decorView.viewTreeObserver
            val baselineInsetsReads = observer.dispatchAndCountInsetsReads()
            val launcher = HiddenActivityLauncherImpl(
                uiContextProvider = {
                    UiContextProvider {
                        lookups += 1
                        if (lookups == 1) UiContextProvider.Result.Success(parent.get()) else throw failure
                    }
                },
                instanceJob = owner,
                logger = recordingRouter { _, cause ->
                    if (cause === failure) owner.cancel()
                }
            )
            launcher.launchActivityForResult(
                intentProvider = {
                    OwnIdHiddenActivity.createIntent(it, InstanceName("late-throw"))
                        .putExtra(OwnIdHiddenActivity.PREPARE_IME_HANDOFF_BEFORE_LAUNCH_KEY, true)
                },
                onShowError = { error, details -> errors += StartupFailure(error, details) }
            )
            assertThat(observer.dispatchAndCountInsetsReads()).isGreaterThan(baselineInsetsReads)

            shadowOf(Looper.getMainLooper()).idleFor(250, TimeUnit.MILLISECONDS)

            assertThat(lookups).isEqualTo(2)
            assertThat(errors).isEmpty()
            assertThat(shadowOf(parent.get()).nextStartedActivity).isNull()
            assertThat(observer.dispatchAndCountInsetsReads()).isEqualTo(baselineInsetsReads)
            launch("after-reentrant-log-cancel")
            assertThat(shadowOf(launchActivity).nextStartedActivity.getStringExtra(INSTANCE_NAME_KEY))
                .isEqualTo("after-reentrant-log-cancel")
        } finally {
            parent.destroyIfNeeded()
        }
    }

    private fun ViewTreeObserver.dispatchAndCountInsetsReads(): Int {
        val before = VisibleImeViewRootShadow.insetsReadCount
        dispatchOnGlobalLayout()
        return VisibleImeViewRootShadow.insetsReadCount - before
    }

    @Test
    fun unknownRestoredHiddenHostClosesWithoutResolvingDelegateOrHoldingGate() {
        val delegate = DeferringDelegate()
        val instanceName = registerHiddenHost(delegate)
        val host = Robolectric.buildActivity(
            OwnIdHiddenActivity::class.java, OwnIdHiddenActivity.createIntent(app, instanceName)
        ).create(Bundle())
        try {
            assertThat(host.get().isFinishing).isTrue()
            assertThat(delegate.createdActivity).isNull()
            launch("after-process-restore")
            assertThat(shadowOf(launchActivity).nextStartedActivity).isNotNull()
        } finally {
            host.destroyIfNeeded()
        }
        launch("must-still-be-pending")
        assertThat(shadowOf(launchActivity).nextStartedActivity).isNull()
    }

    @Test
    fun backgroundLaunchIsMarshalledToMainAndCanceledOwnerCannotReserve() {
        val job = Job()
        val errors = mutableListOf<StartupFailure>()
        var intentProviderCalls = 0
        val worker = Thread {
            launcher(launchActivity, job).launchActivityForResult(
                {
                    assertThat(Looper.myLooper()).isSameInstanceAs(Looper.getMainLooper())
                    intentProviderCalls += 1
                    OwnIdHiddenActivity.createIntent(it, InstanceName("background"))
                },
                { error, details -> errors += StartupFailure(error, details) }
            )
        }
        worker.start()
        worker.join()
        assertThat(intentProviderCalls).isEqualTo(0)
        job.cancel()
        idleMain()
        assertThat(intentProviderCalls).isEqualTo(0)
        assertThat(errors).isEmpty()
        launch("active-owner")
        assertThat(shadowOf(launchActivity).nextStartedActivity).isNotNull()
    }

    // Robolectric's default shadow delivers send() synchronously and skips Binder forwarding after parceling.
    // Use the real framework implementation to exercise the receiver's Handler and Parcelable transport.
    @Implements(ResultReceiver::class)
    class FrameworkResultReceiverShadow

    @Implements(className = "android.view.ViewRootImpl")
    class VisibleImeViewRootShadow : ShadowViewRootImpl() {
        companion object {
            var insetsReadCount = 0
        }

        @Implementation
        @Suppress("UNUSED_PARAMETER")
        protected fun getWindowInsets(forceConstruct: Boolean): WindowInsets {
            insetsReadCount += 1
            return WindowInsets.Builder()
                .setInsets(WindowInsets.Type.ime(), android.graphics.Insets.of(0, 0, 0, 100))
                .setVisible(WindowInsets.Type.ime(), true)
                .build()
        }
    }

    private fun <T : Activity> ActivityController<T>.setupVisibleSource(): ActivityController<T> = apply {
        setup().visible()
        // Robolectric attaches the decor but leaves its window visibility at GONE.
        get().setWindowVisibilityForTest(View.VISIBLE)
    }

    private fun Activity.setWindowVisibilityForTest(visibility: Int) {
        val attachInfo = ReflectionHelpers.getField<Any>(window.decorView, "mAttachInfo")
        ReflectionHelpers.setField(attachInfo, "mWindowVisibility", visibility)
    }

    private fun launcher(activity: Activity = launchActivity, instanceJob: Job = Job()): HiddenActivityLauncherImpl =
        HiddenActivityLauncherImpl(
            uiContextProvider = { UiContextProvider { UiContextProvider.Result.Success(activity) } },
            instanceJob = instanceJob,
            logger = null
        )

    private fun reserveIntent(intent: Intent): Intent = intent.also {
        checkNotNull(OwnIdActivityHostProtocol.LaunchLock.tryStart(Job(), null) { _, _ -> }).attach(it)
    }

    private fun launch(name: String, errors: MutableList<StartupFailure> = mutableListOf(), instanceJob: Job = Job()) {
        launcher(launchActivity, instanceJob).launchActivityForResult(
            { OwnIdHiddenActivity.createIntent(it, InstanceName(name)) },
            { error, details -> errors += StartupFailure(error, details) }
        )
    }

    private fun recordingLogger(messages: MutableList<String>): OwnIdLogger = object : OwnIdLogger {
        override val level: OwnIdLogger.Level = OwnIdLogger.Level.Verbose
        override val tag: String = "OwnID-HiddenActivity-Test"

        override fun log(level: OwnIdLogger.Level, className: String, message: String, cause: Throwable?) {
            messages += message
        }
    }

    private fun recordingRouter(onLog: (String, Throwable?) -> Unit): OwnIdLogRouter {
        val logger = object : OwnIdLogger {
            override val level: OwnIdLogger.Level = OwnIdLogger.Level.Verbose
            override val tag: String = "OwnID-HiddenActivity-Test"

            override fun log(level: OwnIdLogger.Level, className: String, message: String, cause: Throwable?) {
                onLog(message, cause)
            }
        }
        return OwnIdLogRouter(ownIdLoggerProvider = { logger }, serverLoggerProvider = { null })
    }

    private fun registerHiddenHost(delegate: HiddenActivityLauncher.Delegate): InstanceName {
        OwnIdRootDIContainer.injectRootDefaults(app)
        val identitySuffix = UUID.randomUUID().toString().replace("-", "")
        val instanceName = InstanceName("launcher-host-$identitySuffix").also(instancesToDestroy::add)
        OwnIdRootDIContainer.initializeInstanceContainer(
            instanceName,
            OwnIdConfigurationImpl(appId = "LauncherHost$identitySuffix")
        )
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

    private class DeferringDelegate : HiddenActivityLauncher.Delegate {
        var createdActivity: ComponentActivity? = null
        var destroyCount: Int = 0

        override fun isThis(intent: Intent): Boolean = true

        override fun onCreate(activity: ComponentActivity, instanceResolver: DIContainerResolver) {
            createdActivity = activity
        }

        override fun onDestroy(activity: ComponentActivity) {
            destroyCount += 1
        }
    }

    private class TestActivity : ComponentActivity() {
        var onStartActivity: ((Intent) -> Unit)? = null

        override fun startActivity(intent: Intent) {
            onStartActivity?.let { callback -> callback(intent); return }
            super.startActivity(intent)
        }
    }

    private companion object {
        private const val INSTANCE_NAME_KEY = "com.ownid.sdk.ui.key.INSTANCE_NAME"
        private const val OPERATION_TYPE_KEY = "com.ownid.sdk.key.OPERATION_TYPE_KEY"
        private const val OPERATION_ID_KEY = "com.ownid.sdk.key.OPERATION_ID_KEY"
    }
}
