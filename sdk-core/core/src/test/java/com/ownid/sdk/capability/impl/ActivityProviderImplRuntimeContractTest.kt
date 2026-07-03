package com.ownid.sdk.capability.impl

import android.app.Activity
import android.app.Application
import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.model.InternalOwnIdAPI
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController

@RunWith(RobolectricTestRunner::class)
@OptIn(InternalOwnIdAPI::class)
// Covers: CFG-RUNTIME-050
internal class ActivityProviderImplRuntimeContractTest {

    private val app: Application
        get() = RuntimeEnvironment.getApplication()

    @Test
    fun startedAndResumedActivitiesBecomeCurrentActivity() {
        val provider = activityProvider()
        val first = Robolectric.buildActivity(TestActivity::class.java)
        val second = Robolectric.buildActivity(TestActivity::class.java)

        try {
            first.create().start()
            assertThat(provider.getActivity()).isSameInstanceAs(first.get())

            second.create().start().resume()
            assertThat(provider.getActivity()).isSameInstanceAs(second.get())

            first.resume()
            assertThat(provider.getActivity()).isSameInstanceAs(first.get())
        } finally {
            provider.close()
            first.destroyIfNeeded()
            second.destroyIfNeeded()
        }
    }

    @Test
    fun stoppedCurrentActivityClearsCurrentActivity() {
        val provider = activityProvider()
        val controller = Robolectric.buildActivity(TestActivity::class.java)

        try {
            controller.create().start().resume()
            assertThat(provider.getActivity()).isSameInstanceAs(controller.get())

            controller.pause().stop()

            assertThat(provider.getActivity()).isNull()
        } finally {
            provider.close()
            controller.destroyIfNeeded()
        }
    }

    @Test
    fun stoppedNonCurrentActivityDoesNotClearTopActivity() {
        val provider = activityProvider()
        val first = Robolectric.buildActivity(TestActivity::class.java)
        val second = Robolectric.buildActivity(TestActivity::class.java)

        try {
            first.create().start().resume()
            second.create().start().resume()
            assertThat(provider.getActivity()).isSameInstanceAs(second.get())

            first.pause().stop()

            assertThat(provider.getActivity()).isSameInstanceAs(second.get())
        } finally {
            provider.close()
            first.destroyIfNeeded()
            second.destroyIfNeeded()
        }
    }

    @Test
    fun finishingActivityIsExcludedFromCurrentActivity() {
        val provider = activityProvider()
        val controller = Robolectric.buildActivity(TestActivity::class.java)

        try {
            controller.create().start().resume()
            assertThat(provider.getActivity()).isSameInstanceAs(controller.get())

            controller.get().finish()

            assertThat(provider.getActivity()).isNull()
        } finally {
            provider.close()
            controller.destroyIfNeeded()
        }
    }

    @Test
    fun destroyedActivityIsExcludedFromCurrentActivity() {
        val controller = Robolectric.buildActivity(TestActivity::class.java)

        activityProvider().use { provider ->
            controller.create().start().resume()
            assertThat(provider.getActivity()).isSameInstanceAs(controller.get())

            controller.pause().destroy()

            assertThat(controller.get().isDestroyed).isTrue()
            assertThat(provider.getActivity()).isNull()
        }
    }

    @Test
    fun closeUnregistersTrackingAndClearsCurrentActivity() {
        val provider = activityProvider()
        val current = Robolectric.buildActivity(TestActivity::class.java)
        val future = Robolectric.buildActivity(TestActivity::class.java)

        try {
            current.create().start().resume()
            assertThat(provider.getActivity()).isSameInstanceAs(current.get())

            provider.close()
            assertThat(provider.getActivity()).isNull()

            future.create().start().resume()
            assertThat(provider.getActivity()).isNull()
        } finally {
            current.destroyIfNeeded()
            future.destroyIfNeeded()
        }
    }

    private fun activityProvider(): ActivityProviderImpl = ActivityProviderImpl(context = app, logger = null)

    private fun ActivityController<out Activity>.destroyIfNeeded() {
        if (!get().isDestroyed) destroy()
    }

    private class TestActivity : Activity()
}
