package com.ownid.sdk

import android.app.Application
import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.capability.ActivityProvider
import com.ownid.sdk.capability.DispatcherProvider
import com.ownid.sdk.capability.LanguageTagsProvider
import com.ownid.sdk.capability.LocalInfo
import com.ownid.sdk.di.DIContainer
import com.ownid.sdk.di.OwnIdRootDIContainer
import com.ownid.sdk.di.getOrNull
import com.ownid.sdk.di.getOrThrow
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.Authz
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.InstanceName
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.OperationID
import com.ownid.sdk.model.OperationType
import com.ownid.sdk.model.Reason
import com.ownid.sdk.model.accessToken
import com.ownid.sdk.namespace.OwnIdInstance
import com.ownid.sdk.ops.OperationController
import com.ownid.sdk.ops.OperationFailure
import com.ownid.sdk.ops.OperationRegistry
import com.ownid.sdk.ops.OperationResult
import com.ownid.sdk.ops.impl.OperationRegistryImpl
import com.ownid.sdk.provider.SessionCreate
import com.ownid.sdk.provider.SessionOutput
import kotlinx.coroutines.Job
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.UUID
import com.ownid.sdk.model.Context as OwnIdContext

@RunWith(RobolectricTestRunner::class)
@OptIn(InternalOwnIdAPI::class)
// Covers: CFG-RUNTIME-010, CFG-RUNTIME-030, CFG-RUNTIME-040
internal class OwnIdRootRuntimeContractTest {

    private val namesToDestroy = mutableListOf<InstanceName>()

    @After
    fun tearDown() {
        namesToDestroy.forEach { OwnId.destroy(it) }
        OwnId.setLanguage(emptyList())
    }

    @Test
    fun fileInitializationLoadsJsonAssetAndWiresRootDefaultsForNamedInstance() {
        val instanceName = uniqueInstanceName()

        OwnId.initializeFromFile(instanceName) {
            applicationContext = app
            fileName = VALID_CONFIG_ASSET
        }

        val container = checkNotNull(OwnId.getInstanceContainer(instanceName))
        val instance = OwnId.instance(instanceName)

        assertThat(instance.configuration.appId).isEqualTo("AssetApp123")
        assertThat(instance.configuration.env).isEqualTo(OwnIdConfiguration.Env.Uat)
        assertThat(instance.configuration.region).isEqualTo(OwnIdConfiguration.Region.EU)
        assertThat(instance.configuration.rootUrl).isEqualTo("https://127.0.0.1:1/root")

        assertThat(container.getOrThrow<InstanceName>()).isEqualTo(instanceName)
        assertThat(container.getOrThrow<OwnIdConfiguration>()).isSameInstanceAs(instance.configuration)
        assertThat(container.getOrThrow<Job>().isActive).isTrue()

        assertThat(OwnIdRootDIContainer.getOrThrow<Context>()).isSameInstanceAs(app.applicationContext)
        assertThat(container.getOrThrow<Context>()).isSameInstanceAs(app.applicationContext)
        assertThat(container.getOrThrow<LocalInfo>().packageName).isEqualTo(app.packageName)
        assertThat(container.getOrThrow<ActivityProvider>()).isNotNull()
        assertThat(container.getOrThrow<DispatcherProvider>()).isNotNull()
        assertThat(
            container.getOrThrow<LanguageTagsProvider>()
                .languageTagsFlow
                .value
                .map { it.tagString }
        ).containsExactly("en-US", "fr-FR").inOrder()
    }

    @Test
    fun invalidFileInitializationLeavesExistingNamedInstanceUnchanged() {
        val instanceName = uniqueInstanceName()
        OwnId.initialize(instanceName) {
            applicationContext = app
            appId = "StableApp123"
            env = OwnIdConfiguration.Env.Prod
            region = OwnIdConfiguration.Region.US
        }
        val existingContainer = checkNotNull(OwnId.getInstanceContainer(instanceName))
        val existingJob = existingContainer.getOrThrow<Job>()
        val state = OwnId.getInstanceContainerStateFlow(instanceName)

        OwnId.initializeFromFile(instanceName) {
            applicationContext = app
            fileName = INVALID_CONFIG_ASSET
        }

        assertThat(OwnId.getInstanceContainer(instanceName)).isSameInstanceAs(existingContainer)
        assertThat(state.value).isSameInstanceAs(existingContainer)
        assertThat(existingJob.isActive).isTrue()
        assertThat(OwnId.instance(instanceName).configuration.appId).isEqualTo("StableApp123")
    }

    @Test
    fun sameNameReplacementCancelsPreviousInstanceJobAndPublishesReplacement() {
        val instanceName = uniqueInstanceName()
        val state = OwnId.getInstanceContainerStateFlow(instanceName)
        OwnId.initialize(instanceName) {
            applicationContext = app
            appId = "FirstApp123"
        }
        val firstContainer = checkNotNull(OwnId.getInstanceContainer(instanceName))
        val firstJob = firstContainer.getOrThrow<Job>()

        OwnId.initialize(instanceName) {
            applicationContext = app
            appId = "SecondApp123"
        }

        val secondContainer = checkNotNull(OwnId.getInstanceContainer(instanceName))
        val secondJob = secondContainer.getOrThrow<Job>()
        assertThat(secondContainer).isNotSameInstanceAs(firstContainer)
        assertThat(state.value).isSameInstanceAs(secondContainer)
        assertThat(firstJob.isCancelled).isTrue()
        assertThat(secondJob.isActive).isTrue()
        assertThat(OwnId.instance(instanceName).configuration.appId).isEqualTo("SecondApp123")
    }

    @Test
    fun destroyIsIdempotentClearsStateAndCancelsCurrentInstanceJob() {
        val instanceName = uniqueInstanceName()
        val state = OwnId.getInstanceContainerStateFlow(instanceName)
        OwnId.initialize(instanceName) {
            applicationContext = app
            appId = "DestroyApp123"
        }
        val job = checkNotNull(OwnId.getInstanceContainer(instanceName)).getOrThrow<Job>()

        OwnId.destroy(instanceName)
        OwnId.destroy(instanceName)

        assertThat(state.value).isNull()
        assertThat(OwnId.getInstanceContainer(instanceName)).isNull()
        assertThat(job.isCancelled).isTrue()
    }

    @Test
    fun sameNameReplacementPublishesFreshContextProviderAndOperationScope() {
        val instanceName = uniqueInstanceName()
        OwnId.initialize(instanceName) {
            applicationContext = app
            appId = "FirstScopedApp123"
        }
        val staleInstance = OwnId.instance(instanceName)
        val staleContainer = checkNotNull(OwnId.getInstanceContainer(instanceName))
        installScopedState(staleInstance, token = "old-token", session = "old-session")
        val staleProvider = staleContainer.getOrThrow<SessionCreate>()
        val staleController = TestController(OperationID(OperationType.EMAIL_VERIFICATION, "old-operation"))
        staleContainer.operationRegistry().register(staleController)

        OwnId.initialize(instanceName) {
            applicationContext = app
            appId = "ReplacementScopedApp123"
        }

        val currentInstance = OwnId.instance(instanceName)
        val currentContainer = checkNotNull(OwnId.getInstanceContainer(instanceName))
        assertThat(currentContainer).isNotSameInstanceAs(staleContainer)
        assertThat(currentInstance.configuration.appId).isEqualTo("ReplacementScopedApp123")
        assertThat(currentContainer.getOrNull<OwnIdContext>()).isNull()
        assertThat(currentContainer.getOrNull<SessionCreate>()).isNull()
        assertThat(currentContainer.operationRegistry().current.value).isEmpty()

        installScopedState(staleInstance, token = "stale-after-replace", session = "stale-session")
        staleContainer.operationRegistry().register(TestController(OperationID(OperationType.PHONE_NUMBER_VERIFICATION, "stale-operation")))
        installScopedState(currentInstance, token = "current-token", session = "current-session")
        val currentProvider = currentContainer.getOrThrow<SessionCreate>()

        assertThat(staleContainer.getOrThrow<OwnIdContext>().accessToken).isEqualTo(AccessToken("stale-after-replace"))
        assertThat(staleContainer.getOrThrow<SessionCreate>()).isNotSameInstanceAs(staleProvider)
        assertThat(staleContainer.operationRegistry().current.value).containsKey(staleController.operationId)
        assertThat(currentContainer.getOrThrow<OwnIdContext>().accessToken).isEqualTo(AccessToken("current-token"))
        assertThat(currentContainer.getOrThrow<SessionCreate>()).isSameInstanceAs(currentProvider)
        assertThat(currentContainer.operationRegistry().current.value).isEmpty()
    }

    @Test
    fun destroyThenReinitializePublishesFreshContextProviderAndOperationScope() {
        val instanceName = uniqueInstanceName()
        OwnId.initialize(instanceName) {
            applicationContext = app
            appId = "DestroyScopedApp123"
        }
        val staleInstance = OwnId.instance(instanceName)
        val staleContainer = checkNotNull(OwnId.getInstanceContainer(instanceName))
        installScopedState(staleInstance, token = "old-token", session = "old-session")
        val staleController = TestController(OperationID(OperationType.EMAIL_VERIFICATION, "destroyed-operation"))
        staleContainer.operationRegistry().register(staleController)

        OwnId.destroy(instanceName)
        OwnId.initialize(instanceName) {
            applicationContext = app
            appId = "ReinitializedScopedApp123"
        }

        val currentInstance = OwnId.instance(instanceName)
        val currentContainer = checkNotNull(OwnId.getInstanceContainer(instanceName))
        assertThat(currentContainer).isNotSameInstanceAs(staleContainer)
        assertThat(currentInstance.configuration.appId).isEqualTo("ReinitializedScopedApp123")
        assertThat(currentContainer.getOrNull<OwnIdContext>()).isNull()
        assertThat(currentContainer.getOrNull<SessionCreate>()).isNull()
        assertThat(currentContainer.operationRegistry().current.value).isEmpty()

        installScopedState(staleInstance, token = "stale-after-destroy", session = "stale-session")
        staleContainer.operationRegistry().register(TestController(OperationID(OperationType.PHONE_NUMBER_VERIFICATION, "stale-operation")))
        installScopedState(currentInstance, token = "current-token", session = "current-session")
        val currentProvider = currentContainer.getOrThrow<SessionCreate>()

        assertThat(staleContainer.getOrThrow<OwnIdContext>().accessToken).isEqualTo(AccessToken("stale-after-destroy"))
        assertThat(staleContainer.operationRegistry().current.value).containsKey(staleController.operationId)
        assertThat(currentContainer.getOrThrow<OwnIdContext>().accessToken).isEqualTo(AccessToken("current-token"))
        assertThat(currentContainer.getOrThrow<SessionCreate>()).isSameInstanceAs(currentProvider)
        assertThat(currentContainer.operationRegistry().current.value).isEmpty()
    }

    private val app: Application
        get() = OwnIdRootDIContainer.getOrNull<Context>() as? Application ?: RuntimeEnvironment.getApplication()

    private fun installScopedState(instance: OwnIdInstance, token: String, session: String) {
        instance.setContext {
            authz = Authz.fromToken(token)
        }
        instance.setProviders {
            sessionCreate {
                create { Result.success(SessionOutput(session)) }
            }
        }
    }

    private fun DIContainer.operationRegistry(): OperationRegistryImpl =
        getOrThrow<OperationRegistry>() as OperationRegistryImpl

    private fun uniqueInstanceName(): InstanceName =
        InstanceName("root-runtime-${UUID.randomUUID()}").also { namesToDestroy += it }

    private class TestController(
        override val operationId: OperationID
    ) : OperationController<Unit, TestFailure> {
        override suspend fun whenSettled(): OperationResult<Unit, TestFailure> =
            error("Root lifecycle tests do not assert terminal operation semantics")

        override fun abort(reason: Reason): Unit = Unit
    }

    private object TestFailure : OperationFailure {
        override val errorCode: ErrorCode = ErrorCode.Unknown
        override val message: String = "unused"
    }

    private companion object {
        private const val VALID_CONFIG_ASSET = "runtime-config/ownid-valid-config.json"
        private const val INVALID_CONFIG_ASSET = "runtime-config/ownid-invalid-config.json"
    }
}
