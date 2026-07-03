package com.ownid.sdk.ui.webbridge.impl

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.capability.AppConfigProvider
import com.ownid.sdk.capability.DispatcherProvider
import com.ownid.sdk.capability.Serialization
import com.ownid.sdk.di.DIContainer
import com.ownid.sdk.di.DIContainerResolver
import com.ownid.sdk.di.getOrNull
import com.ownid.sdk.di.impl.DIContainerImpl
import com.ownid.sdk.di.register
import com.ownid.sdk.model.Authz
import com.ownid.sdk.model.Context
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.toWebBridgePayload
import com.ownid.sdk.namespace.OwnIdWebBridge
import com.ownid.sdk.namespace.OwnIdWebBridgeNamespace
import com.ownid.sdk.provider.PasswordAuthenticate
import com.ownid.sdk.ui.webbridge.WebBridge
import com.ownid.sdk.ui.webbridge.WebBridgePluginKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(InternalOwnIdAPI::class)
// Covers: WB-060, WB-070, WB-090, WB-BRIDGE-120
internal class WebBridgePluginFactoryStoreImplContractTest {

    @Test
    fun replacesFactoriesByKeyAndCopiesIndependently() {
        val firstKey = WebBridgePluginKey.from("first")
        val secondKey = WebBridgePluginKey.from("second")
        val thirdKey = WebBridgePluginKey.from("third")
        val store = WebBridgePluginFactoryStoreImpl()

        store.register(firstKey) { TestWebBridgePlugin(firstKey, listOf("old")) }
        store.register(secondKey) { TestWebBridgePlugin(secondKey, listOf("second")) }
        store.register(firstKey) { TestWebBridgePlugin(firstKey, listOf("new")) }
        val copy = store.copyStore()
        store.unregister(secondKey)
        store.register(thirdKey) { TestWebBridgePlugin(thirdKey, listOf("third")) }

        assertThat(store.has(firstKey)).isTrue()
        assertThat(store.has(secondKey)).isFalse()
        assertThat(copy.has(secondKey)).isTrue()
        assertThat(store.instantiateAll(NoopWebBridgeResolver).map { it.key }).containsExactly(firstKey, thirdKey).inOrder()
        assertThat(copy.instantiateAll(NoopWebBridgeResolver).map { it.key }).containsExactly(firstKey, secondKey).inOrder()
        assertThat(copy.instantiateAll(NoopWebBridgeResolver).first().actions).containsExactly("new")
    }

    @Test
    fun skipsThrowingAndMismatchedFactories() {
        val expectedKey = WebBridgePluginKey.from("expected")
        val throwingKey = WebBridgePluginKey.from("throwing")
        val mismatchedKey = WebBridgePluginKey.from("mismatch")
        val store = WebBridgePluginFactoryStoreImpl()

        store.register(expectedKey) { TestWebBridgePlugin(expectedKey, listOf("ok")) }
        store.register(throwingKey) { error("boom") }
        store.register(mismatchedKey) { TestWebBridgePlugin(expectedKey, listOf("wrong-key")) }

        assertThat(store.instantiateAll(NoopWebBridgeResolver).map { it.key }).containsExactly(expectedKey)
    }

    @Test
    fun builtInFactoriesReceiveResolverWhenInstantiatedForABridge() {
        val key = WebBridgePluginKey.from("built-in")
        val store = WebBridgePluginFactoryStoreImpl()
        var receivedScopeName: String? = null

        store.registerBuiltIn(key) { resolver ->
            receivedScopeName = resolver.scopeName
            TestWebBridgePlugin(key, listOf("get"))
        }

        assertThat(store.instantiateAll(NoopWebBridgeResolver).map { it.key }).containsExactly(key)
        assertThat(receivedScopeName).isEqualTo(NoopWebBridgeResolver.scopeName)
    }

    @Test
    fun namespaceDefaultFactoryChangesAffectOnlyFutureBridgesWithoutAttachRuntimeBehavior(): Unit = runTest {
        val job = SupervisorJob()
        try {
            val namespace = webBridgeNamespace(job, StandardTestDispatcher(testScheduler))
            val key = WebBridgePluginKey.from("future")
            namespace.defaultPluginFactories.register(key) { TestWebBridgePlugin(key, listOf("initial")) }
            val firstBridge = namespace.create()

            namespace.defaultPluginFactories.register(key) { TestWebBridgePlugin(key, listOf("updated")) }
            val secondBridge = namespace.create()

            assertThat(firstBridge.actionsFor(key)).containsExactly("initial")
            assertThat(secondBridge.actionsFor(key)).containsExactly("updated")
        } finally {
            job.cancel()
        }
    }

    @Test
    fun scopedNamespaceFactoryCopiesAreIsolatedFromParentAndSiblingEdits(): Unit = runTest {
        val job = SupervisorJob()
        try {
            val parentKey = WebBridgePluginKey.from("parent")
            val laterParentKey = WebBridgePluginKey.from("later-parent")
            val scopedKey = WebBridgePluginKey.from("scoped")
            val namespace = webBridgeNamespace(job, StandardTestDispatcher(testScheduler)).also {
                it.defaultPluginFactories.register(parentKey) { TestWebBridgePlugin(parentKey, listOf("parent-original")) }
            }
            val scoped = namespace.withContext(scopeName = "Scoped") {
                accountDisplayName = "Scoped User"
            }

            namespace.defaultPluginFactories.register(parentKey) { TestWebBridgePlugin(parentKey, listOf("parent-updated")) }
            namespace.defaultPluginFactories.register(laterParentKey) { TestWebBridgePlugin(laterParentKey, listOf("later-parent")) }
            scoped.defaultPluginFactories.register(scopedKey) { TestWebBridgePlugin(scopedKey, listOf("scoped")) }

            assertThat(namespace.create().plugins.snapshot().map { it.key }).containsExactly(parentKey, laterParentKey).inOrder()
            assertThat(namespace.create().actionsFor(parentKey)).containsExactly("parent-updated")
            assertThat(scoped.create().plugins.snapshot().map { it.key }).containsExactly(parentKey, scopedKey).inOrder()
            assertThat(scoped.create().actionsFor(parentKey)).containsExactly("parent-original")
            assertThat(scoped.create().actionsFor(scopedKey)).containsExactly("scoped")
        } finally {
            job.cancel()
        }
    }

    @Test
    fun bridgeCreationCapturesScopedContextAndProvidersThroughInternalFactorySeam(): Unit = runTest {
        val job = SupervisorJob()
        try {
            val captureKey = WebBridgePluginKey.from("capture")
            val store = WebBridgePluginFactoryStoreImpl()
            var capturedPayload: String? = null
            var capturedProviderPresent: Boolean? = null
            store.registerBuiltIn(captureKey) { resolver: DIContainerResolver ->
                capturedPayload = resolver.getOrNull<Context>()?.toWebBridgePayload().toString()
                capturedProviderPresent = resolver.getOrNull(PasswordAuthenticate::class.java) != null
                TestWebBridgePlugin(captureKey, listOf("capture"))
            }
            val scoped = webBridgeNamespace(job, StandardTestDispatcher(testScheduler), store)
                .withContext(scopeName = "ContextScope") {
                    authz = Authz.start("person@example.test", LoginId.Type.Email)
                    accountDisplayName = "Person"
                }
                .withProviders(scopeName = "ProviderScope") {
                    passwordAuthenticate {
                        authenticate { Result.failure(IllegalStateException("unused")) }
                    }
                }

            val bridge = scoped.create()

            assertThat(bridge.plugins.get(captureKey)).isNotNull()
            assertThat(capturedPayload).contains("person@example.test")
            assertThat(capturedPayload).contains("Person")
            assertThat(capturedProviderPresent).isTrue()
        } finally {
            job.cancel()
        }
    }

    private fun webBridgeNamespace(
        job: Job,
        dispatcher: CoroutineDispatcher,
        store: WebBridgePluginFactoryStoreImpl = WebBridgePluginFactoryStoreImpl()
    ): OwnIdWebBridge =
        OwnIdWebBridgeNamespace(container = webBridgeContainer(job, dispatcher), pluginFactoryStore = store)

    private fun webBridgeContainer(job: Job, dispatcher: CoroutineDispatcher): DIContainer =
        DIContainerImpl(scopeName = "webbridge").apply {
            register<AppConfigProvider>(MutableAppConfigProvider())
            register<Serialization>(WebBridgeRuntimeTestSerialization)
            register<DispatcherProvider>(TestDispatcherProvider(dispatcher))
            register<Job>(job)
        }

    private fun WebBridge.actionsFor(key: WebBridgePluginKey): List<String>? =
        plugins.get(key)?.actions

    private class TestDispatcherProvider(private val dispatcher: CoroutineDispatcher) : DispatcherProvider {
        override fun defaultDispatcher(): CoroutineDispatcher = dispatcher

        override fun ioDispatcher(): CoroutineDispatcher = dispatcher
    }
}
