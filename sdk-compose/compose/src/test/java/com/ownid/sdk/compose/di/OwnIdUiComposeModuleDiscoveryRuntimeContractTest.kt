package com.ownid.sdk.compose.di

import android.app.Application
import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.OwnId
import com.ownid.sdk.capability.LocalInfo
import com.ownid.sdk.compose.FakeOperationController
import com.ownid.sdk.compose.OwnIdThemeStore
import com.ownid.sdk.compose.RecordingOperationUiContainer
import com.ownid.sdk.compose.ShowRequest
import com.ownid.sdk.compose.ops.EmailVerificationUiProvider
import com.ownid.sdk.compose.ops.LoginIdCollectUiProvider
import com.ownid.sdk.compose.ops.PhoneVerificationUiProvider
import com.ownid.sdk.di.DIContainer
import com.ownid.sdk.di.getOrThrow
import com.ownid.sdk.di.register
import com.ownid.sdk.model.InstanceName
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.OperationID
import com.ownid.sdk.model.OperationType
import com.ownid.sdk.ops.ui.EmailVerificationUi
import com.ownid.sdk.ops.ui.LoginIdCollectUi
import com.ownid.sdk.ops.ui.PhoneVerificationUi
import com.ownid.sdk.ui.capability.OperationUiContainer
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@OptIn(InternalOwnIdAPI::class)
// Covers: INFO-RUNTIME-050, UI-PRESENT-030
internal class OwnIdUiComposeModuleDiscoveryRuntimeContractTest {

    private val app: Application
        get() = RuntimeEnvironment.getApplication()

    private val createdInstances = mutableListOf<InstanceName>()

    @After
    fun tearDown() {
        createdInstances.forEach(OwnId::destroy)
        createdInstances.clear()
    }

    @Test
    fun localInfoReportsCoreAndComposeRuntimeModules(): Unit = runTest {
        val modules = initializeOwnIdInstance("compose-metadata").container
            .getOrThrow<LocalInfo>()
            .modules

        val coreModule = modules.single { it.first == CORE_MODULE_NAME }
        val composeModule = modules.single { it.first == COMPOSE_MODULE_NAME }
        assertThat(coreModule.second).matches(VERSION_PATTERN)
        assertThat(composeModule.second).matches(VERSION_PATTERN)
    }

    @Test
    fun coreInitializationDiscoversComposeModuleAndUsesComposeUiFactories(): Unit = runTest {
        val initializedInstance = initializeOwnIdInstance("compose-discovery")
        val container = initializedInstance.container
        assertComposeModuleBindings(container)

        val operationContainer = RecordingOperationUiContainer()
        container.register<OperationUiContainer>(operationContainer)

        val startupFailures = mutableListOf<StartupFailure>()
        container.getOrThrow<LoginIdCollectUi>().start(
            FakeOperationController(OperationID(OperationType.LOGIN_ID_COLLECT, "login"))
        ) { error, details ->
            startupFailures += StartupFailure(error, details)
        }
        container.getOrThrow<EmailVerificationUi>().start(
            FakeOperationController(OperationID(OperationType.EMAIL_VERIFICATION, "email"))
        ) { error, details ->
            startupFailures += StartupFailure(error, details)
        }
        container.getOrThrow<PhoneVerificationUi>().start(
            FakeOperationController(
                OperationID(OperationType.PHONE_NUMBER_VERIFICATION, "phone")
            )
        ) { error, details ->
            startupFailures += StartupFailure(error, details)
        }

        assertThat(startupFailures).isEmpty()
        assertThat(operationContainer.requests).containsExactly(
            ShowRequest(initializedInstance.name, OperationID(OperationType.LOGIN_ID_COLLECT, "login")),
            ShowRequest(initializedInstance.name, OperationID(OperationType.EMAIL_VERIFICATION, "email")),
            ShowRequest(initializedInstance.name, OperationID(OperationType.PHONE_NUMBER_VERIFICATION, "phone"))
        ).inOrder()
    }

    private fun assertComposeModuleBindings(container: DIContainer) {
        assertThat(container.getOrThrow<OwnIdThemeStore>()).isNotNull()
        assertThat(container.getOrThrow<LoginIdCollectUiProvider>()).isSameInstanceAs(LoginIdCollectUiProvider.Default)
        assertThat(container.getOrThrow<EmailVerificationUiProvider>()).isSameInstanceAs(EmailVerificationUiProvider.Default)
        assertThat(container.getOrThrow<PhoneVerificationUiProvider>()).isSameInstanceAs(PhoneVerificationUiProvider.Default)
        assertThat(container.canResolve(OperationUiContainer::class.java)).isTrue()
        assertThat(container.canResolve(LoginIdCollectUi::class.java)).isTrue()
        assertThat(container.canResolve(EmailVerificationUi::class.java)).isTrue()
        assertThat(container.canResolve(PhoneVerificationUi::class.java)).isTrue()
    }

    private data class StartupFailure(val error: Throwable?, val details: String?)

    private data class InitializedInstance(val name: InstanceName, val container: DIContainer)

    private fun initializeOwnIdInstance(prefix: String): InitializedInstance {
        val instanceName = InstanceName("$prefix-${UUID.randomUUID()}")
        createdInstances += instanceName

        OwnId.initialize(instanceName) {
            applicationContext = app
            appId = "App123"
        }

        return InitializedInstance(instanceName, requireNotNull(OwnId.getInstanceContainer(instanceName)))
    }

    private companion object {
        private const val CORE_MODULE_NAME = "OwnIDCore"
        private const val COMPOSE_MODULE_NAME = "OwnIDCompose"
        private const val VERSION_PATTERN = """\d+\.\d+\.\d+(?:-SNAPSHOT)?"""
    }
}
