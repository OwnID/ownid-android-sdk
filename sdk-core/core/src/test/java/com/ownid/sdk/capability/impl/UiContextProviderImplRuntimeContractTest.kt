package com.ownid.sdk.capability.impl

import android.app.Activity
import android.app.Application
import android.view.View
import android.view.Window
import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.capability.UiContextProvider
import com.ownid.sdk.capability.UiContextResolutionException
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.ui.OwnIdHiddenActivity
import com.ownid.sdk.ui.OwnIdWebViewActivity
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@OptIn(InternalOwnIdAPI::class)
// Covers: CFG-RUNTIME-050
internal class UiContextProviderImplRuntimeContractTest {

    private val app: Application
        get() = RuntimeEnvironment.getApplication()

    @Test
    fun startedAndResumedCandidatesFollowLifecycleAndRecency() {
        val provider = UiContextProviderImpl(app)
        val first = Robolectric.buildActivity(TestActivity::class.java)
        val second = Robolectric.buildActivity(TestActivity::class.java)
        try {
            first.create().start()
            assertThat(provider.activity()).isSameInstanceAs(first.get())

            second.create().start().resume()
            assertThat(provider.activity()).isSameInstanceAs(second.get())

            first.resume()
            assertThat(provider.activity()).isSameInstanceAs(first.get())

            first.pause().stop()
            assertThat(provider.activity()).isSameInstanceAs(second.get())

            second.pause().stop()
            provider.assertUnavailable()
        } finally {
            first.destroyIfNeeded()
            second.destroyIfNeeded()
        }
    }

    @Test
    fun focusedStartedCandidateOutranksUnfocusedResumedCandidate() {
        val provider = UiContextProviderImpl(app)
        val view = mockk<View> { every { hasWindowFocus() } returns true }
        val window = mockk<Window> { every { peekDecorView() } returns view }
        val focused = mockk<Activity> {
            every { isFinishing } returns false
            every { isDestroyed } returns false
            every { getWindow() } returns window
        }
        val resumed = mockk<Activity> {
            every { isFinishing } returns false
            every { isDestroyed } returns false
            every { getWindow() } returns null
        }
        provider.onActivityStarted(focused)
        provider.onActivityStarted(resumed)
        provider.onActivityResumed(resumed)

        assertThat(provider.activity()).isSameInstanceAs(focused)
    }

    @Test
    fun pausedActivityRemainsEligibleUntilStopped() {
        val provider = UiContextProviderImpl(app)
        val controller = Robolectric.buildActivity(TestActivity::class.java)
        try {
            controller.create().start().resume()
            controller.pause()

            assertThat(provider.activity()).isSameInstanceAs(controller.get())

            controller.stop()
            provider.assertUnavailable()
        } finally {
            controller.destroyIfNeeded()
        }
    }

    @Test
    fun unfocusedResumedActivityOutranksStartedActivityWithNewerResumeHistory() {
        val provider = UiContextProviderImpl(app)
        val resumed = Robolectric.buildActivity(TestActivity::class.java)
        val started = Robolectric.buildActivity(TestActivity::class.java)
        try {
            resumed.create().start().resume()
            started.create().start().resume().pause()

            assertThat(resumed.get().window.peekDecorView()?.hasWindowFocus()).isNotEqualTo(true)
            assertThat(started.get().window.peekDecorView()?.hasWindowFocus()).isNotEqualTo(true)
            assertThat(provider.activity()).isSameInstanceAs(resumed.get())
        } finally {
            resumed.destroyIfNeeded()
            started.destroyIfNeeded()
        }
    }

    @Test
    fun equallyStartedActivitiesUseMostRecentStart() {
        val provider = UiContextProviderImpl(app)
        val older = Robolectric.buildActivity(TestActivity::class.java)
        val newer = Robolectric.buildActivity(TestActivity::class.java)
        try {
            older.create().start()
            newer.create().start()

            assertThat(provider.activity()).isSameInstanceAs(newer.get())
        } finally {
            older.destroyIfNeeded()
            newer.destroyIfNeeded()
        }
    }

    @Test
    fun sdkHostsAndFinishingOrDestroyedActivitiesAreExcluded() {
        val provider = UiContextProviderImpl(app)
        provider.onActivityStarted(mockk<OwnIdHiddenActivity>())
        provider.onActivityResumed(mockk<OwnIdWebViewActivity>())
        provider.assertUnavailable()

        val finishing = Robolectric.buildActivity(TestActivity::class.java)
        val destroyed = Robolectric.buildActivity(TestActivity::class.java)
        try {
            finishing.create().start().resume()
            assertThat(provider.activity()).isSameInstanceAs(finishing.get())
            finishing.get().finish()
            provider.assertUnavailable()

            destroyed.create().start().resume()
            assertThat(provider.activity()).isSameInstanceAs(destroyed.get())
            destroyed.pause().destroy()
            provider.assertUnavailable()
        } finally {
            finishing.destroyIfNeeded()
            destroyed.destroyIfNeeded()
        }
    }

    @Test
    fun lateRegistrationDoesNotInferAnAlreadyResumedActivity() {
        val controller = Robolectric.buildActivity(TestActivity::class.java).create().start().resume()
        try {
            val provider = UiContextProviderImpl(app)
            provider.assertUnavailable()

            controller.pause().resume()
            assertThat(provider.activity()).isSameInstanceAs(controller.get())
        } finally {
            controller.destroyIfNeeded()
        }
    }

    @Test
    fun currentRequiresMainThread() {
        val provider = UiContextProviderImpl(app)
        val failure = AtomicReference<Throwable?>()
        val worker = Thread {
            try {
                provider.current()
            } catch (error: Throwable) {
                failure.set(error)
            }
        }
        worker.start()
        worker.join()

        assertThat(failure.get()).isInstanceOf(IllegalStateException::class.java)
        assertThat(failure.get()).hasMessageThat().contains("main thread")
    }

    private fun UiContextProvider.activity(): Activity = (current() as UiContextProvider.Result.Success).activity

    private fun UiContextProvider.assertUnavailable() {
        val error = (current() as UiContextProvider.Result.Failure).error as UiContextResolutionException
        assertThat(error.reason).isEqualTo(UiContextResolutionException.Reason.Unavailable)
    }

    private fun ActivityController<out Activity>.destroyIfNeeded() {
        if (!get().isDestroyed) destroy()
    }

    private class TestActivity : Activity()
}
