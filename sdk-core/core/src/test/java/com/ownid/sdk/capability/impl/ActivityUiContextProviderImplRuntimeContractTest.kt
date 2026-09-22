package com.ownid.sdk.capability.impl

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.capability.UiContextProvider
import com.ownid.sdk.capability.UiContextResolutionException
import com.ownid.sdk.di.OwnIdRootDIContainer
import com.ownid.sdk.di.getOrThrow
import com.ownid.sdk.di.impl.DIContainerImpl
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.provider.setProviders
import com.ownid.sdk.provider.withProviders
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@OptIn(InternalOwnIdAPI::class)
// Covers: CFG-RUNTIME-140
internal class ActivityUiContextProviderImplRuntimeContractTest {

    private val app: Application
        get() = RuntimeEnvironment.getApplication()

    private lateinit var previousRootUiContextProvider: UiContextProvider
    private lateinit var rootUiContextProvider: UiContextProviderImpl

    @Before
    fun initializeProcessRootUiContextFixture() {
        OwnIdRootDIContainer.injectRootDefaults(app)
        previousRootUiContextProvider = OwnIdRootDIContainer.getOrThrow()
        rootUiContextProvider = UiContextProviderImpl(app)
        OwnIdRootDIContainer.register(UiContextProvider::class.java, rootUiContextProvider)
    }

    @After
    fun restoreProcessRootUiContextFixture() {
        app.unregisterActivityLifecycleCallbacks(rootUiContextProvider)
        OwnIdRootDIContainer.register(UiContextProvider::class.java, previousRootUiContextProvider)
    }

    @Test
    fun boundOwnerRemainsSelectedWhenStoppedAndOtherActivityIsActiveThenClosesWhenFinishing() {
        val owner = Robolectric.buildActivity(TestPlainActivity::class.java).setup()
        var other: ActivityController<TestPlainActivity>? = null
        try {
            val provider = activityProvider(owner.get())
            other = Robolectric.buildActivity(TestPlainActivity::class.java).setup()
            provider.assertActivity(owner.get())

            owner.pause().stop()
            provider.assertActivity(owner.get())

            owner.get().finish()
            provider.assertFailure(UiContextResolutionException.Reason.OwnerClosed)
            provider.assertFailure(UiContextResolutionException.Reason.OwnerClosed)
        } finally {
            other?.destroyIfNeeded()
            owner.destroyIfNeeded()
        }
    }

    @Test
    fun componentActivityRegistrationFollowsConfigurationReplacement() {
        assertRecreationKeepsBoundOwner(TestComponentActivity::class.java)
    }

    @Test
    fun plainActivityRegistrationFollowsSavedStateConfigurationReplacement() {
        assertRecreationKeepsBoundOwner(TestPlainActivity::class.java)
    }

    @Test
    fun plainActivityRegisteredAfterLastStateSaveDoesNotAdoptConfigurationReplacement() {
        val owner = Robolectric.buildActivity(TestPlainActivity::class.java).setup()
        val savedState = Bundle()
        owner.saveInstanceState(savedState)
        val provider = activityProvider(owner.get())
        var replacement: ActivityController<TestPlainActivity>? = null
        try {
            provider.assertActivity(owner.get())
            owner.get().simulateConfigurationChange = true
            owner.pause().stop().destroy()
            provider.assertFailure(UiContextResolutionException.Reason.Unavailable)

            replacement = Robolectric.buildActivity(TestPlainActivity::class.java).create(savedState).start().resume()
            provider.assertFailure(UiContextResolutionException.Reason.Unavailable)
        } finally {
            replacement?.destroyIfNeeded()
            owner.destroyIfNeeded()
        }
    }

    @Test
    fun nonConfigurationOwnerDestructionClosesBindingWithoutAdoptingOtherActivity() {
        val owner = Robolectric.buildActivity(TestPlainActivity::class.java).setup()
        val provider = activityProvider(owner.get())
        var other: ActivityController<TestPlainActivity>? = null
        try {
            owner.pause().stop().destroy()
            provider.assertFailure(UiContextResolutionException.Reason.OwnerClosed)

            other = Robolectric.buildActivity(TestPlainActivity::class.java).setup()
            provider.assertFailure(UiContextResolutionException.Reason.OwnerClosed)
        } finally {
            other?.destroyIfNeeded()
            owner.destroyIfNeeded()
        }
    }

    @Test
    fun processRootFansOutLifecycleToExplicitBindingsAndTerminalCleanupDoesNotInterruptSibling() {
        val first = Robolectric.buildActivity(TestPlainActivity::class.java).setup()
        val second = Robolectric.buildActivity(TestPlainActivity::class.java).setup()
        val firstProvider = activityProvider(first.get())
        val secondProvider = activityProvider(second.get())
        try {
            firstProvider.assertActivity(first.get())
            secondProvider.assertActivity(second.get())

            val originalFirst = first.get()
            first.recreate()

            assertThat(first.get()).isNotSameInstanceAs(originalFirst)
            firstProvider.assertActivity(first.get())
            secondProvider.assertActivity(second.get())

            first.get().finish()
            firstProvider.assertFailure(UiContextResolutionException.Reason.OwnerClosed)

            val originalSecond = second.get()
            second.recreate()

            assertThat(second.get()).isNotSameInstanceAs(originalSecond)
            secondProvider.assertActivity(second.get())
            firstProvider.assertFailure(UiContextResolutionException.Reason.OwnerClosed)
        } finally {
            first.destroyIfNeeded()
            second.destroyIfNeeded()
        }
    }

    @Test
    fun activityBindingUsesProcessRootLifecycleDespiteScopedCustomProvidersAndKeepsSiblingsIsolated() {
        val owner = Robolectric.buildActivity(TestPlainActivity::class.java).setup()
        try {
            val parent = DIContainerImpl(scopeName = "parent")
            val parentCustom = UiContextProvider {
                UiContextProvider.Result.Failure(IllegalStateException("parent custom provider"))
            }
            val siblingCustom = UiContextProvider {
                UiContextProvider.Result.Failure(IllegalStateException("sibling custom provider"))
            }
            parent.setProviders { uiContext(parentCustom) }

            val bound = parent.withProviders(scopeName = "bound") { uiContext(owner.get()) }
            val sibling = parent.withProviders(scopeName = "sibling") { uiContext(siblingCustom) }
            val boundProvider = bound.getOrThrow<UiContextProvider>()

            assertThat(parent.getOrThrow<UiContextProvider>()).isSameInstanceAs(parentCustom)
            assertThat(sibling.getOrThrow<UiContextProvider>()).isSameInstanceAs(siblingCustom)
            boundProvider.assertActivity(owner.get())

            val originalOwner = owner.get()
            owner.recreate()

            assertThat(owner.get()).isNotSameInstanceAs(originalOwner)
            boundProvider.assertActivity(owner.get())
            assertThat(parent.getOrThrow<UiContextProvider>()).isSameInstanceAs(parentCustom)
            assertThat(sibling.getOrThrow<UiContextProvider>()).isSameInstanceAs(siblingCustom)
        } finally {
            owner.destroyIfNeeded()
        }
    }

    @Test
    fun bindingRejectsUnattachedOwnerAndClassifiesAlreadyTerminalOwners() {
        val unattachedError = assertThrows(IllegalStateException::class.java) {
            activityProvider(Activity())
        }
        assertThat(unattachedError).hasMessageThat().contains("attached to an Application")

        val finishing = Robolectric.buildActivity(TestPlainActivity::class.java).setup()
        val destroyed = Robolectric.buildActivity(TestPlainActivity::class.java).setup()
        val configurationDestroyed = Robolectric.buildActivity(TestPlainActivity::class.java).setup()
        try {
            finishing.get().finish()
            activityProvider(finishing.get()).assertFailure(UiContextResolutionException.Reason.OwnerClosed)

            destroyed.pause().stop().destroy()
            activityProvider(destroyed.get()).assertFailure(UiContextResolutionException.Reason.OwnerClosed)

            configurationDestroyed.get().simulateConfigurationChange = true
            configurationDestroyed.pause().stop().destroy()
            activityProvider(configurationDestroyed.get()).assertFailure(UiContextResolutionException.Reason.Unavailable)
        } finally {
            finishing.destroyIfNeeded()
            destroyed.destroyIfNeeded()
            configurationDestroyed.destroyIfNeeded()
        }
    }

    @Test
    fun activityAndCustomOverloadsUseTheLastProviderInEachBlock() {
        val owner = Robolectric.buildActivity(TestPlainActivity::class.java).setup()
        try {
            val container = DIContainerImpl(scopeName = "providers")
            val error = IllegalStateException("app has no host")
            val custom = UiContextProvider { UiContextProvider.Result.Failure(error) }

            container.setProviders {
                uiContext(custom)
                uiContext(owner.get())
            }
            container.getOrThrow<UiContextProvider>().assertActivity(owner.get())

            container.setProviders {
                uiContext(owner.get())
                uiContext(custom)
            }
            assertThat(container.getOrThrow<UiContextProvider>()).isSameInstanceAs(custom)
            val failure = container.getOrThrow<UiContextProvider>().current() as UiContextProvider.Result.Failure
            assertThat(failure.error).isSameInstanceAs(error)
        } finally {
            owner.destroyIfNeeded()
        }
    }

    @Test
    fun activityRegistrationAndResolutionRequireMainThread() {
        val owner = Robolectric.buildActivity(TestPlainActivity::class.java).setup()
        try {
            val provider = activityProvider(owner.get())
            val container = DIContainerImpl(scopeName = "providers")

            assertThat(failureOnWorker { provider.current() }).isInstanceOf(IllegalStateException::class.java)
            assertThat(failureOnWorker { container.withProviders(scopeName = "worker") { uiContext(owner.get()) } })
                .isInstanceOf(IllegalStateException::class.java)
        } finally {
            owner.destroyIfNeeded()
        }
    }

    private fun <T : Activity> assertRecreationKeepsBoundOwner(activityClass: Class<T>) {
        val owner = Robolectric.buildActivity(activityClass).setup()
        val oldActivity = owner.get()
        val provider = DIContainerImpl(scopeName = "parent")
            .withProviders(scopeName = "bound") { uiContext(oldActivity) }
            .getOrThrow<UiContextProvider>()
        var other: ActivityController<TestPlainActivity>? = null
        var gapResult: UiContextProvider.Result? = null
        var afterOtherCreated: UiContextProvider.Result? = null
        val observer = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

            override fun onActivityDestroyed(activity: Activity) {
                if (activity === oldActivity) {
                    gapResult = provider.current()
                    other = Robolectric.buildActivity(TestPlainActivity::class.java).setup()
                    afterOtherCreated = provider.current()
                }
            }
        }
        app.registerActivityLifecycleCallbacks(observer)
        try {
            provider.assertActivity(oldActivity)
            owner.recreate()

            assertThat(gapResult).isNotNull()
            gapResult.assertFailure(UiContextResolutionException.Reason.Unavailable)
            assertThat(afterOtherCreated).isNotNull()
            afterOtherCreated.assertFailure(UiContextResolutionException.Reason.Unavailable)
            assertThat(owner.get()).isNotSameInstanceAs(oldActivity)
            provider.assertActivity(owner.get())
        } finally {
            app.unregisterActivityLifecycleCallbacks(observer)
            owner.destroyIfNeeded()
            other?.destroyIfNeeded()
        }
    }

    private fun activityProvider(activity: Activity): UiContextProvider =
        ActivityUiContextProviderImpl(activity, rootUiContextProvider)

    private fun UiContextProvider.assertActivity(expected: Activity) {
        assertThat((current() as UiContextProvider.Result.Success).activity).isSameInstanceAs(expected)
    }

    private fun UiContextProvider.assertFailure(reason: UiContextResolutionException.Reason) {
        current().assertFailure(reason)
    }

    private fun UiContextProvider.Result?.assertFailure(reason: UiContextResolutionException.Reason) {
        val error = (this as UiContextProvider.Result.Failure).error as UiContextResolutionException
        assertThat(error.reason).isEqualTo(reason)
    }

    private fun failureOnWorker(block: () -> Unit): Throwable? {
        val failure = AtomicReference<Throwable?>()
        val worker = Thread { failure.set(runCatching(block).exceptionOrNull()) }
        worker.start()
        worker.join()
        return failure.get()
    }

    private fun ActivityController<out Activity>.destroyIfNeeded() {
        if (!get().isDestroyed) destroy()
    }

    private class TestPlainActivity : Activity() {
        var simulateConfigurationChange = false

        override fun isChangingConfigurations(): Boolean = simulateConfigurationChange || super.isChangingConfigurations()
    }

    private class TestComponentActivity : ComponentActivity()
}
