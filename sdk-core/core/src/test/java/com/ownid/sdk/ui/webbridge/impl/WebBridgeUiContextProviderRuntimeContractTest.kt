package com.ownid.sdk.ui.webbridge.impl

import android.app.Activity
import android.content.ContextWrapper
import android.content.MutableContextWrapper
import android.webkit.WebView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.capability.AppConfigProvider
import com.ownid.sdk.capability.DispatcherProvider
import com.ownid.sdk.capability.Serialization
import com.ownid.sdk.capability.UiContextProvider
import com.ownid.sdk.capability.UiContextResolutionException
import com.ownid.sdk.di.DIContainer
import com.ownid.sdk.di.DIContainerResolver
import com.ownid.sdk.di.getOrThrow
import com.ownid.sdk.di.impl.DIContainerImpl
import com.ownid.sdk.di.register
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.ui.webbridge.WebBridgePluginKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowOwnIdWebViewFeature::class, ShadowOwnIdWebViewCompat::class])
@OptIn(ExperimentalCoroutinesApi::class, InternalOwnIdAPI::class)
// Covers: WB-BRIDGE-330
internal class WebBridgeUiContextProviderRuntimeContractTest {

    @Test
    fun factoryCreatedBridgesGivePluginsIsolatedActivityBindings() = runTest {
        ShadowOwnIdWebViewFeature.reset()
        ShadowOwnIdWebViewCompat.reset()
        val firstActivity = Robolectric.buildActivity(Activity::class.java).setup()
        val secondActivity = Robolectric.buildActivity(Activity::class.java).setup()
        val instanceJob = SupervisorJob()
        val providers = mutableListOf<UiContextProvider>()
        val key = WebBridgePluginKey.from("capture")
        val store = WebBridgePluginFactoryStoreImpl().apply {
            registerBuiltIn(key) { resolver: DIContainerResolver ->
                providers += resolver.getOrThrow<UiContextProvider>()
                TestWebBridgePlugin(key, listOf("capture"))
            }
        }
        val parentProvider = UiContextProvider {
            UiContextProvider.Result.Failure(IllegalStateException("parent provider"))
        }
        val container = webBridgeContainer(instanceJob, StandardTestDispatcher(testScheduler)).apply {
            register<UiContextProvider>(parentProvider)
        }
        val firstBridge = WebBridgeImpl.create(container, store)
        val secondBridge = WebBridgeImpl.create(container, store)
        try {
            assertThat(providers).hasSize(2)
            assertThat(providers).doesNotContain(parentProvider)
            assertThat(providers[0]).isNotSameInstanceAs(providers[1])
            providers[0].assertFailure(UiContextResolutionException.Reason.Unavailable)
            providers[1].assertFailure(UiContextResolutionException.Reason.Unavailable)

            assertThat(firstBridge.attach(WebView(firstActivity.get()), setOf("example.test"), TestLifecycleOwner())).isNull()
            providers[0].assertActivity(firstActivity.get())
            providers[1].assertFailure(UiContextResolutionException.Reason.Unavailable)

            assertThat(secondBridge.attach(WebView(secondActivity.get()), setOf("example.test"), TestLifecycleOwner())).isNull()
            providers[0].assertActivity(firstActivity.get())
            providers[1].assertActivity(secondActivity.get())
        } finally {
            firstBridge.detach()
            secondBridge.detach()
            instanceJob.cancel()
            firstActivity.destroyIfNeeded()
            secondActivity.destroyIfNeeded()
        }
    }

    @Test
    fun unavailableUntilBindThenKeepsExactUnwrappedActivity() {
        val first = Robolectric.buildActivity(Activity::class.java).setup()
        val second = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val provider = WebBridgeUiContextProvider()
            val mutableContext = MutableContextWrapper(first.get())
            val nestedContext = ContextWrapper(mutableContext)

            provider.assertFailure(UiContextResolutionException.Reason.Unavailable)
            provider.bind(nestedContext)
            mutableContext.setBaseContext(second.get())

            provider.assertActivity(first.get())
        } finally {
            first.destroyIfNeeded()
            second.destroyIfNeeded()
        }
    }

    @Test
    fun bindFailuresDoNotPreventLaterBindingToALiveActivity() {
        val finishing = Robolectric.buildActivity(Activity::class.java).setup()
        val destroyed = Robolectric.buildActivity(Activity::class.java).setup()
        val live = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val provider = WebBridgeUiContextProvider()
            provider.bindFailure(RuntimeEnvironment.getApplication())
                .assertReason(UiContextResolutionException.Reason.Unavailable)

            finishing.get().finish()
            provider.bindFailure(finishing.get())
                .assertReason(UiContextResolutionException.Reason.OwnerClosed)

            val destroyedActivity = destroyed.get()
            destroyed.pause().stop().destroy()
            provider.bindFailure(destroyedActivity)
                .assertReason(UiContextResolutionException.Reason.OwnerClosed)

            provider.bind(live.get())
            provider.assertActivity(live.get())
        } finally {
            finishing.destroyIfNeeded()
            destroyed.destroyIfNeeded()
            live.destroyIfNeeded()
        }
    }

    @Test
    fun failedAttachResetAllowsBindingToAnotherActivity() {
        val first = Robolectric.buildActivity(Activity::class.java).setup()
        val second = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val provider = WebBridgeUiContextProvider()
            provider.bind(first.get())

            provider.resetAfterFailedAttach()
            provider.bind(second.get())

            provider.assertActivity(second.get())
        } finally {
            first.destroyIfNeeded()
            second.destroyIfNeeded()
        }
    }

    @Test
    fun closeAndObservedOwnerClosureAreTerminal() {
        val explicitlyClosed = Robolectric.buildActivity(Activity::class.java).setup()
        val finishing = Robolectric.buildActivity(Activity::class.java).setup()
        val destroyed = Robolectric.buildActivity(Activity::class.java).setup()
        val replacement = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val closedProvider = WebBridgeUiContextProvider()
            closedProvider.bind(explicitlyClosed.get())
            closedProvider.close()
            closedProvider.assertTerminal(replacement.get())

            val finishingProvider = WebBridgeUiContextProvider()
            finishingProvider.bind(finishing.get())
            finishing.get().finish()
            finishingProvider.assertTerminal(replacement.get())

            val destroyedProvider = WebBridgeUiContextProvider()
            destroyedProvider.bind(destroyed.get())
            destroyed.pause().stop().destroy()
            destroyedProvider.assertTerminal(replacement.get())
        } finally {
            explicitlyClosed.destroyIfNeeded()
            finishing.destroyIfNeeded()
            destroyed.destroyIfNeeded()
            replacement.destroyIfNeeded()
        }
    }

    @Test
    fun operationsRequireMainThread() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val failures = listOf(
                failureOnWorker { WebBridgeUiContextProvider().current() },
                failureOnWorker { WebBridgeUiContextProvider().bind(activity.get()) },
                failureOnWorker { WebBridgeUiContextProvider().resetAfterFailedAttach() },
                failureOnWorker { WebBridgeUiContextProvider().close() }
            )

            failures.forEach { failure ->
                assertThat(failure).isInstanceOf(IllegalStateException::class.java)
                assertThat(failure).hasMessageThat().contains("main thread")
            }
        } finally {
            activity.destroyIfNeeded()
        }
    }

    @Test
    fun bridgeDetachClosesTheActivityBinding() = runTest {
        ShadowOwnIdWebViewFeature.reset()
        ShadowOwnIdWebViewCompat.reset()
        val activity = Robolectric.buildActivity(Activity::class.java).setup()
        val provider = WebBridgeUiContextProvider()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val instanceJob = Job()
        Dispatchers.setMain(dispatcher)
        val bridge = WebBridgeImpl(
            plugins = WebBridgePluginRegistryImpl(initialPlugins = emptyList()),
            appConfigProvider = MutableAppConfigProvider(),
            serialization = WebBridgeRuntimeTestSerialization,
            uiContextProvider = provider,
            defaultDispatcher = dispatcher,
            instanceJob = instanceJob
        )
        try {
            val owner = TestLifecycleOwner()

            assertThat(bridge.attach(WebView(activity.get()), setOf("example.test"), owner)).isNull()
            provider.assertActivity(activity.get())

            bridge.detach()

            provider.assertFailure(UiContextResolutionException.Reason.OwnerClosed)
        } finally {
            bridge.detach()
            instanceJob.cancel()
            Dispatchers.resetMain()
            activity.destroyIfNeeded()
        }
    }

    private fun WebBridgeUiContextProvider.bindFailure(context: android.content.Context): UiContextResolutionException =
        checkNotNull(runCatching { bind(context) }.exceptionOrNull()) as UiContextResolutionException

    private fun UiContextProvider.assertActivity(expected: Activity) {
        assertThat((current() as UiContextProvider.Result.Success).activity).isSameInstanceAs(expected)
    }

    private fun UiContextProvider.assertFailure(reason: UiContextResolutionException.Reason) {
        (current() as UiContextProvider.Result.Failure).error.assertReason(reason)
    }

    private fun WebBridgeUiContextProvider.assertTerminal(replacement: Activity) {
        assertFailure(UiContextResolutionException.Reason.OwnerClosed)
        resetAfterFailedAttach()
        assertThat(runCatching { bind(replacement) }.exceptionOrNull()).isInstanceOf(IllegalStateException::class.java)
        assertFailure(UiContextResolutionException.Reason.OwnerClosed)
    }

    private fun Throwable.assertReason(expected: UiContextResolutionException.Reason) {
        assertThat(this).isInstanceOf(UiContextResolutionException::class.java)
        assertThat((this as UiContextResolutionException).reason).isEqualTo(expected)
    }

    private fun failureOnWorker(block: () -> Unit): Throwable? {
        val failure = AtomicReference<Throwable?>()
        val worker = Thread { failure.set(runCatching(block).exceptionOrNull()) }
        worker.start()
        worker.join()
        return failure.get()
    }

    private class TestLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)

        override val lifecycle: Lifecycle
            get() = registry

        init {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        }
    }

    private fun ActivityController<out Activity>.destroyIfNeeded() {
        if (!get().isDestroyed) destroy()
    }

    private fun webBridgeContainer(job: Job, dispatcher: CoroutineDispatcher): DIContainer =
        DIContainerImpl(scopeName = "webbridge-runtime").apply {
            register<AppConfigProvider>(MutableAppConfigProvider())
            register<Serialization>(WebBridgeRuntimeTestSerialization)
            register<DispatcherProvider>(TestDispatcherProvider(dispatcher))
            register<Job>(job)
        }

    private class TestDispatcherProvider(private val dispatcher: CoroutineDispatcher) : DispatcherProvider {
        override fun defaultDispatcher(): CoroutineDispatcher = dispatcher

        override fun ioDispatcher(): CoroutineDispatcher = dispatcher
    }
}
