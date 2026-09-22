package com.ownid.sdk

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.ServiceInfo
import android.os.Bundle
import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.capability.DispatcherProvider
import com.ownid.sdk.capability.LanguageTagsProvider
import com.ownid.sdk.capability.LocalInfo
import com.ownid.sdk.capability.UiContextProvider
import com.ownid.sdk.di.DIContainer
import com.ownid.sdk.di.IdentityConflictException
import com.ownid.sdk.di.OwnIdModuleInjector
import com.ownid.sdk.di.OwnIdRootDIContainer
import com.ownid.sdk.di.getOrNull
import com.ownid.sdk.di.getOrThrow
import com.ownid.sdk.logger.OwnIdLogger
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.Authz
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.InstanceName
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LanguageTag
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.util.Collections
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import com.ownid.sdk.model.Context as OwnIdContext

@RunWith(RobolectricTestRunner::class)
@OptIn(InternalOwnIdAPI::class)
// Covers: CFG-RUNTIME-010, CFG-RUNTIME-030, CFG-RUNTIME-040, CFG-RUNTIME-100
internal class OwnIdRootRuntimeContractTest {

    private val namesToDestroy = mutableListOf<InstanceName>()

    @After
    fun tearDown() {
        namesToDestroy.forEach { OwnId.destroy(it) }
        OwnId.setLanguage(emptyList())
        OwnId.logger {
            level = OwnIdLogger.Level.Off
        }
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
        assertThat(container.getOrThrow<UiContextProvider>()).isSameInstanceAs(OwnIdRootDIContainer.getOrThrow<UiContextProvider>())
        assertThat(container.getOrThrow<DispatcherProvider>()).isNotNull()
        assertThat(
            container.getOrThrow<LanguageTagsProvider>()
                .languageTagsFlow
                .value
                .map { it.tagString }
        ).containsExactly("en-US", "fr-FR").inOrder()

        val rootUiProvider = OwnIdRootDIContainer.getOrThrow<UiContextProvider>()
        val secondName = uniqueInstanceName()
        OwnId.initialize(secondName) {
            applicationContext = app
            appId = uniqueAppId("SecondRoot")
        }
        val secondContainer = checkNotNull(OwnId.getInstanceContainer(secondName))
        assertThat(secondContainer.getOrThrow<UiContextProvider>()).isSameInstanceAs(rootUiProvider)
        assertThat(container.getOrThrow<UiContextProvider>()).isSameInstanceAs(rootUiProvider)

        OwnId.destroy(instanceName)
        assertThat(OwnIdRootDIContainer.getOrThrow<UiContextProvider>()).isSameInstanceAs(rootUiProvider)
        assertThat(secondContainer.getOrThrow<UiContextProvider>()).isSameInstanceAs(rootUiProvider)
    }

    @Test
    fun invalidFileInitializationLeavesExistingNamedInstanceUnchanged() {
        val instanceName = uniqueInstanceName()
        OwnId.initialize(instanceName) {
            applicationContext = app
            appId = "StableApp123"
            env = OwnIdConfiguration.Env.Prod
            region = OwnIdConfiguration.Region.US
            languages = listOf("en-US")
        }
        val existingContainer = checkNotNull(OwnId.getInstanceContainer(instanceName))
        val existingJob = existingContainer.getOrThrow<Job>()
        val state = OwnId.getInstanceContainerStateFlow(instanceName)

        listOf(INVALID_CONFIG_ASSET, INVALID_BACKSLASH_CONFIG_ASSET).forEach { invalidAsset ->
            OwnId.initializeFromFile(instanceName) {
                applicationContext = app
                fileName = invalidAsset
            }

            assertThat(OwnId.getInstanceContainer(instanceName)).isSameInstanceAs(existingContainer)
            assertThat(state.value).isSameInstanceAs(existingContainer)
            assertThat(existingJob.isActive).isTrue()
            assertThat(OwnId.instance(instanceName).configuration.appId).isEqualTo("StableApp123")
            assertThat(currentLanguageTags()).containsExactly("en-US")
        }
    }

    @Test
    fun jsonNullMetadataPreservesRootLanguageModeAndInvalidNullAppIdIsAtomic() {
        val instanceName = uniqueInstanceName()
        val appId = uniqueAppId("JsonNull")
        initializeInstance(instanceName, appId = appId, rootUrl = "https://stable.example/root", languages = listOf("en-US"))
        val firstContainer = checkNotNull(OwnId.getInstanceContainer(instanceName))
        val firstJob = firstContainer.getOrThrow<Job>()

        OwnId.initializeFromJson(instanceName) {
            applicationContext = app
            json = """{"appId":"$appId","env":null,"region":null,"rootUrl":null,"languages":null}"""
        }

        val replacementContainer = checkNotNull(OwnId.getInstanceContainer(instanceName))
        val replacementJob = replacementContainer.getOrThrow<Job>()
        assertThat(replacementContainer).isNotSameInstanceAs(firstContainer)
        assertThat(firstJob.isCancelled).isTrue()
        assertThat(replacementJob.isActive).isTrue()
        assertThat(OwnId.instance(instanceName).configuration.appId).isEqualTo(appId)
        assertThat(OwnId.instance(instanceName).configuration.env).isEqualTo(OwnIdConfiguration.Env.Prod)
        assertThat(OwnId.instance(instanceName).configuration.region).isEqualTo(OwnIdConfiguration.Region.US)
        assertThat(OwnId.instance(instanceName).configuration.rootUrl).isNull()
        assertThat(currentLanguageTags()).containsExactly("en-US")

        OwnId.initializeFromJson(instanceName) {
            applicationContext = app
            json = """{"appId":null,"appID":null,"languages":["fr-FR"]}"""
        }

        assertThat(OwnId.getInstanceContainer(instanceName)).isSameInstanceAs(replacementContainer)
        assertThat(replacementJob.isActive).isTrue()
        assertThat(OwnId.instance(instanceName).configuration.appId).isEqualTo(appId)
        assertThat(currentLanguageTags()).containsExactly("en-US")
    }

    @Test
    fun sameNameReplacementWithSameIdentityCancelsPreviousInstanceJobAndPublishesReplacement() {
        val instanceName = uniqueInstanceName()
        val appId = uniqueAppId("SameIdentity")
        val state = OwnId.getInstanceContainerStateFlow(instanceName)
        initializeInstance(instanceName, appId = appId, rootUrl = "https://first.example/root", languages = listOf("en-US"))
        val firstContainer = checkNotNull(OwnId.getInstanceContainer(instanceName))
        val firstJob = firstContainer.getOrThrow<Job>()

        initializeInstance(instanceName, appId = appId, rootUrl = "https://second.example/root", languages = listOf("fr-FR"))

        val secondContainer = checkNotNull(OwnId.getInstanceContainer(instanceName))
        val secondJob = secondContainer.getOrThrow<Job>()
        assertThat(secondContainer).isNotSameInstanceAs(firstContainer)
        assertThat(state.value).isSameInstanceAs(secondContainer)
        assertThat(firstJob.isCancelled).isTrue()
        assertThat(secondJob.isActive).isTrue()
        assertThat(OwnId.instance(instanceName).configuration.appId).isEqualTo(appId)
        assertThat(OwnId.instance(instanceName).configuration.rootUrl).isEqualTo("https://second.example/root")
        assertThat(currentLanguageTags()).containsExactly("fr-FR")
    }

    @Test
    fun lateDirectErrorLeavesExistingSameNameInstanceAndLanguageStateUnchanged() {
        OwnIdRootDIContainer.injectRootDefaults(app)
        val previousLanguageTagsProvider = OwnIdRootDIContainer.getOrThrow<LanguageTagsProvider>()
        val languageTagsProvider = RecordingLanguageTagsProvider(initialTags = listOf("en-US"))
        OwnIdRootDIContainer.register(LanguageTagsProvider::class.java, languageTagsProvider)

        try {
            val instanceName = uniqueInstanceName()
            val appId = uniqueAppId("LateDirectError")
            initializeInstance(instanceName, appId = appId, rootUrl = "https://stable.example/root", languages = listOf("en-US"))
            val existingContainer = checkNotNull(OwnId.getInstanceContainer(instanceName))
            val existingNamespace = OwnId.instance(instanceName)
            val existingConfiguration = existingContainer.getOrThrow<OwnIdConfiguration>()
            val existingJob = existingContainer.getOrThrow<Job>()
            val state = OwnId.getInstanceContainerStateFlow(instanceName)
            val languageState = languageTagsProvider.snapshot()
            val restoreModuleMetadata = installOptionalModuleMetadata(DirectAssertionErrorOwnIdModule::class.java.name)

            DirectAssertionErrorOwnIdModule.reset()
            try {
                val propagated = assertThrows(AssertionError::class.java) {
                    initializeInstance(
                        instanceName = instanceName,
                        appId = appId,
                        rootUrl = "https://candidate.example/root",
                        languages = listOf("fr-FR")
                    )
                }

                assertThat(propagated).isSameInstanceAs(DirectAssertionErrorOwnIdModule.failure)
                assertThat(DirectAssertionErrorOwnIdModule.candidateJob?.isCancelled).isTrue()
                assertThat(OwnId.getInstanceContainer(instanceName)).isSameInstanceAs(existingContainer)
                assertThat(state.value).isSameInstanceAs(existingContainer)
                assertThat(OwnId.instance(instanceName)).isSameInstanceAs(existingNamespace)
                assertThat(OwnId.instance(instanceName).configuration).isSameInstanceAs(existingConfiguration)
                assertThat(checkNotNull(OwnId.getInstanceContainer(instanceName)).getOrThrow<Job>()).isSameInstanceAs(existingJob)
                assertThat(existingJob.isActive).isTrue()
                assertThat(languageTagsProvider.snapshot()).isEqualTo(languageState)
            } finally {
                DirectAssertionErrorOwnIdModule.reset()
                restoreModuleMetadata()
            }
        } finally {
            OwnIdRootDIContainer.register(LanguageTagsProvider::class.java, previousLanguageTagsProvider)
        }
    }

    @Test
    fun differentNamesWithSameIdentityAreRejectedWithoutChangingInstancesOrLanguage() {
        val logs = installRecordingLogger()
        val ownerName = uniqueInstanceName()
        val contenderName = uniqueInstanceName()
        val appId = uniqueAppId("Conflict")
        initializeInstance(
            instanceName = ownerName,
            appId = appId,
            env = OwnIdConfiguration.Env.Uat,
            region = OwnIdConfiguration.Region.EU,
            rootUrl = "https://owner.example/root",
            languages = listOf("en-US")
        )
        val ownerContainer = checkNotNull(OwnId.getInstanceContainer(ownerName))
        val ownerJob = ownerContainer.getOrThrow<Job>()
        val contenderState = OwnId.getInstanceContainerStateFlow(contenderName)

        initializeInstance(
            instanceName = contenderName,
            appId = appId,
            env = OwnIdConfiguration.Env.Uat,
            region = OwnIdConfiguration.Region.EU,
            rootUrl = "https://contender.example/root",
            languages = listOf("fr-FR")
        )

        assertThat(OwnId.getInstanceContainer(ownerName)).isSameInstanceAs(ownerContainer)
        assertThat(ownerJob.isActive).isTrue()
        assertThat(OwnId.instance(ownerName).configuration.rootUrl).isEqualTo("https://owner.example/root")
        assertThat(OwnId.getInstanceContainer(contenderName)).isNull()
        assertThat(contenderState.value).isNull()
        assertThat(currentLanguageTags()).containsExactly("en-US")
        logs.singleIdentityConflict().also { log ->
            assertThat(log.level).isEqualTo(OwnIdLogger.Level.Error)
            assertThat(log.className).isEqualTo("OwnId.initialize")
            assertThat(log.message).contains("initialization failed")
        }
    }

    @Test
    fun differenceInAnyIdentityFieldPermitsCoexistence() {
        val appId = uniqueAppId("Identity")
        val ownerName = uniqueInstanceName()
        val differentAppIdName = uniqueInstanceName()
        val differentEnvironmentName = uniqueInstanceName()
        val differentRegionName = uniqueInstanceName()

        initializeInstance(ownerName, appId = appId)
        initializeInstance(differentAppIdName, appId = uniqueAppId("OtherIdentity"))
        initializeInstance(differentEnvironmentName, appId = appId, env = OwnIdConfiguration.Env.Uat)
        initializeInstance(differentRegionName, appId = appId, region = OwnIdConfiguration.Region.EU)

        assertThat(
            listOf(ownerName, differentAppIdName, differentEnvironmentName, differentRegionName)
                .map(OwnId::getInstanceContainer)
        ).doesNotContain(null)
    }

    @Test
    fun destroyReleasesIdentityForAnotherName() {
        val ownerName = uniqueInstanceName()
        val successorName = uniqueInstanceName()
        val appId = uniqueAppId("ReleasedIdentity")
        initializeInstance(ownerName, appId = appId)
        val ownerJob = checkNotNull(OwnId.getInstanceContainer(ownerName)).getOrThrow<Job>()

        OwnId.destroy(ownerName)
        initializeInstance(successorName, appId = appId)

        assertThat(OwnId.getInstanceContainer(ownerName)).isNull()
        assertThat(ownerJob.isCancelled).isTrue()
        assertThat(OwnId.getInstanceContainer(successorName)).isNotNull()
        assertThat(OwnId.instance(successorName).configuration.appId).isEqualTo(appId)
    }

    @Test
    fun concurrentDifferentNamesWithSameIdentityCreateExactlyOneInstance() {
        val logs = installRecordingLogger()
        val firstName = uniqueInstanceName()
        val secondName = uniqueInstanceName()
        val appId = uniqueAppId("ConcurrentIdentity")
        val application = app
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val initializations = listOf(firstName, secondName).map { instanceName ->
                executor.submit {
                    ready.countDown()
                    check(start.await(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                    OwnId.initialize(instanceName) {
                        applicationContext = application
                        this.appId = appId
                    }
                }
            }
            assertThat(ready.await(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue()
            start.countDown()
            initializations.forEach { it.get(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS) }
        } finally {
            start.countDown()
            executor.shutdownNow()
            assertThat(executor.awaitTermination(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue()
        }

        val containers = listOf(firstName, secondName).mapNotNull(OwnId::getInstanceContainer)
        assertThat(containers).hasSize(1)
        assertThat(containers.single().getOrThrow<Job>().isActive).isTrue()
        assertThat(containers.single().getOrThrow<OwnIdConfiguration>().appId).isEqualTo(appId)
        logs.singleIdentityConflict()
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

    private fun initializeInstance(
        instanceName: InstanceName,
        appId: String,
        env: OwnIdConfiguration.Env = OwnIdConfiguration.Env.Prod,
        region: OwnIdConfiguration.Region = OwnIdConfiguration.Region.US,
        rootUrl: String? = null,
        languages: List<String>? = null
    ) {
        OwnId.initialize(instanceName) {
            applicationContext = app
            this.appId = appId
            this.env = env
            this.region = region
            this.rootUrl = rootUrl
            this.languages = languages
        }
    }

    private fun currentLanguageTags(): List<String> =
        OwnIdRootDIContainer.getOrThrow<LanguageTagsProvider>()
            .languageTagsFlow
            .value
            .map { it.tagString }

    private fun installOptionalModuleMetadata(moduleClassName: String): () -> Unit {
        val context = app
        val packageManager = shadowOf(context.packageManager)
        val packageInfo = packageManager.getInternalMutablePackageInfo(context.packageName)
        val servicesSnapshot = packageInfo.services
        val serviceInfo = ServiceInfo().apply {
            packageName = context.packageName
            name = "${context.packageName}.RootRuntimeOptionalModuleMetadataHolder"
            applicationInfo = ApplicationInfo(context.applicationInfo)
            enabled = true
            exported = false
            metaData = Bundle().apply {
                putString(OPTIONAL_MODULE_INJECTOR_KEY, moduleClassName)
            }
        }
        val componentName = ComponentName(serviceInfo.packageName, serviceInfo.name)

        packageInfo.services = (packageInfo.services?.filterNotNull().orEmpty() + serviceInfo).toTypedArray()
        packageManager.addOrUpdateService(serviceInfo)

        return {
            packageManager.removeService(componentName)
            packageManager.getInternalMutablePackageInfo(context.packageName).services = servicesSnapshot
        }
    }

    private fun installRecordingLogger(): MutableList<LogEntry> {
        val logs = Collections.synchronizedList(mutableListOf<LogEntry>())
        OwnId.logger {
            level = OwnIdLogger.Level.Error
            log { level, className, message, cause ->
                logs += LogEntry(level, className, message, cause)
            }
        }
        return logs
    }

    private fun List<LogEntry>.singleIdentityConflict(): LogEntry {
        val conflicts = synchronized(this) {
            filter { it.cause is IdentityConflictException }
        }
        assertThat(conflicts).hasSize(1)
        return conflicts.single()
    }

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

    private fun uniqueAppId(prefix: String): String =
        prefix + UUID.randomUUID().toString().replace("-", "")

    private data class LogEntry(
        val level: OwnIdLogger.Level,
        val className: String,
        val message: String,
        val cause: Throwable?
    )

    private class RecordingLanguageTagsProvider(initialTags: List<String>) : LanguageTagsProvider {
        enum class Mode { AUTOMATIC, EXPLICIT }

        data class Snapshot(
            val setterInvocationCount: Int,
            val mode: Mode,
            val tags: List<LanguageTag>
        )

        private val mutableLanguageTagsFlow = MutableStateFlow(initialTags.map(::languageTag))

        override val languageTagsFlow: StateFlow<List<LanguageTag>> = mutableLanguageTagsFlow.asStateFlow()

        private var setterInvocationCount: Int = 0
        private var mode: Mode = Mode.AUTOMATIC

        override fun setLanguageTags(tags: List<String>) {
            setterInvocationCount += 1
            mode = if (tags.isEmpty()) Mode.AUTOMATIC else Mode.EXPLICIT
            mutableLanguageTagsFlow.value = tags.map(::languageTag).ifEmpty { listOf(LanguageTag.DEFAULT) }
        }

        fun snapshot(): Snapshot = Snapshot(
            setterInvocationCount = setterInvocationCount,
            mode = mode,
            tags = languageTagsFlow.value
        )

        private fun languageTag(tag: String): LanguageTag = LanguageTag.fromLocale(Locale.forLanguageTag(tag))
    }

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
        private const val INVALID_BACKSLASH_CONFIG_ASSET = "runtime-config/ownid-invalid-backslash-config.json"
        private const val OPTIONAL_MODULE_INJECTOR_KEY = "com.ownid.sdk.ui.INJECTOR_KEY"
        private const val CONCURRENCY_TIMEOUT_SECONDS = 10L
    }
}

@OptIn(InternalOwnIdAPI::class)
internal class DirectAssertionErrorOwnIdModule : OwnIdModuleInjector.Module {
    override fun injectIntoInstanceContainer(container: DIContainer) {
        candidateJob = container.getOrThrow<Job>()
        throw AssertionError("optional module direct error").also { failure = it }
    }

    companion object {
        var candidateJob: Job? = null
            private set

        var failure: AssertionError? = null
            private set

        fun reset() {
            candidateJob = null
            failure = null
        }
    }
}
