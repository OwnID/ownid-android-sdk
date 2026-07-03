package com.ownid.sdk.compose

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.compose.di.OwnIdUiComposeInjector
import com.ownid.sdk.compose.ops.EmailVerificationUiProvider
import com.ownid.sdk.compose.ops.LoginIdCollectUiProvider
import com.ownid.sdk.compose.ops.PhoneVerificationUiProvider
import com.ownid.sdk.di.DIContainer
import com.ownid.sdk.di.DIContainerResolver
import com.ownid.sdk.di.getOrThrow
import com.ownid.sdk.model.Availability
import com.ownid.sdk.model.InstanceName
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.OperationID
import com.ownid.sdk.model.OperationType
import com.ownid.sdk.model.Reason
import com.ownid.sdk.namespace.OperationEntry
import com.ownid.sdk.namespace.ScopedOperationEntry
import com.ownid.sdk.ops.LoginIdCollectOperation
import com.ownid.sdk.ops.LoginIdCollectOperationFailure
import com.ownid.sdk.ops.OperationController
import com.ownid.sdk.ops.OperationFailure
import com.ownid.sdk.ops.OperationResult
import com.ownid.sdk.ops.ui.EmailVerificationUi
import com.ownid.sdk.ops.ui.LoginIdCollectUi
import com.ownid.sdk.ops.ui.PhoneVerificationUi
import com.ownid.sdk.ui.capability.HiddenActivityLauncher
import com.ownid.sdk.ui.capability.OperationUiContainer
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(InternalOwnIdAPI::class)
// Covers: UI-030, UI-070, UI-080, UI-110
internal class OwnIdOperationUiHostingContractTest {

    @Test
    fun composeInjectorRegistersOptionalUiCapabilitiesAndProviders() {
        val container = RecordingContainer()

        OwnIdUiComposeInjector().injectIntoInstanceContainer(container)

        assertThat(container.instanceClasses()).contains(OwnIdThemeStore::class.java)
        assertThat(container.factoryDependencies<OperationUiContainer>())
            .containsExactly(HiddenActivityLauncher::class.java, com.ownid.sdk.compose.capability.HiddenActivityComposeDelegate::class.java)
            .inOrder()
        assertThat(container.factoryClasses()).containsAtLeast(
            LoginIdCollectUiProvider::class.java,
            EmailVerificationUiProvider::class.java,
            PhoneVerificationUiProvider::class.java,
            LoginIdCollectUi::class.java,
            EmailVerificationUi::class.java,
            PhoneVerificationUi::class.java
        )
        assertThat(container.factoryDependencies<LoginIdCollectUi>())
            .containsExactly(LoginIdCollectUiProvider::class.java, OperationUiContainer::class.java, InstanceName::class.java)
            .inOrder()
        assertThat(container.factoryDependencies<EmailVerificationUi>())
            .containsExactly(EmailVerificationUiProvider::class.java, OperationUiContainer::class.java, InstanceName::class.java)
            .inOrder()
        assertThat(container.factoryDependencies<PhoneVerificationUi>())
            .containsExactly(PhoneVerificationUiProvider::class.java, OperationUiContainer::class.java, InstanceName::class.java)
            .inOrder()
    }

    @Test
    fun composeInjectedOperationUiDelegatesStartupToRegisteredContainerWithoutThrowing() {
        val container = RecordingContainer()
        val instanceName = InstanceName("compose-ui")
        val operationContainer = RecordingOperationUiContainer()
        val resolver = MapResolver(
            mapOf(
                LoginIdCollectUiProvider::class.java to LoginIdCollectUiProvider.Default,
                EmailVerificationUiProvider::class.java to EmailVerificationUiProvider.Default,
                PhoneVerificationUiProvider::class.java to PhoneVerificationUiProvider.Default,
                OperationUiContainer::class.java to operationContainer,
                InstanceName::class.java to instanceName
            )
        )

        OwnIdUiComposeInjector().injectIntoInstanceContainer(container)
        val loginUi = container.create<LoginIdCollectUi>(resolver)
        val emailUi = container.create<EmailVerificationUi>(resolver)
        val phoneUi = container.create<PhoneVerificationUi>(resolver)

        loginUi.start(
            FakeOperationController(
                OperationID(OperationType.LOGIN_ID_COLLECT, "login")
            )
        ) { _, _ ->
            error("startup should be delegated to the container")
        }
        emailUi.start(
            FakeOperationController(
                OperationID(OperationType.EMAIL_VERIFICATION, "email")
            )
        ) { _, _ ->
            error("startup should be delegated to the container")
        }
        phoneUi.start(
            FakeOperationController(
                OperationID(OperationType.PHONE_NUMBER_VERIFICATION, "phone")
            )
        ) { _, _ ->
            error("startup should be delegated to the container")
        }

        assertThat(operationContainer.requests).containsExactly(
            ShowRequest(instanceName, OperationID(OperationType.LOGIN_ID_COLLECT, "login")),
            ShowRequest(instanceName, OperationID(OperationType.EMAIL_VERIFICATION, "email")),
            ShowRequest(instanceName, OperationID(OperationType.PHONE_NUMBER_VERIFICATION, "phone"))
        ).inOrder()
    }

    @Test
    fun appHostedEntryPreservesAvailabilityParamsAndStartsDelegateWithNoOpContainerScope(): Unit = runTest {
        val operationController = FakeOperationController<LoginId, LoginIdCollectOperationFailure>(
            OperationID(OperationType.LOGIN_ID_COLLECT, "hosted")
        )
        val delegate = RecordingOperationEntry<LoginIdCollectOperation.Params?, LoginId, LoginIdCollectOperationFailure>(
            operationType = OperationType.LOGIN_ID_COLLECT,
            controller = operationController
        )
        val scopedEntry = RecordingScopedOperationEntry(
            instanceName = InstanceName("hosted-instance"),
            delegate = delegate
        )

        val hostedEntry = scopedEntry.appHostedComponent
        val params = LoginIdCollectOperation.Params(loginId = LoginId("person@example.com", LoginId.Type.Email))

        assertThat(hostedEntry.operationType).isEqualTo(OperationType.LOGIN_ID_COLLECT)
        assertThat(hostedEntry.availability(params)).isInstanceOf(Availability.Available::class.java)
        val controller = hostedEntry.start(params)

        assertThat(scopedEntry.scopeName).isEqualTo("OperationUIHosting.${OperationType.LOGIN_ID_COLLECT.value}")
        assertThat(scopedEntry.scopeContainer.getOrThrow<OperationUiContainer>()).isNotNull()
        assertThat(delegate.availabilityParams).containsExactly(params)
        assertThat(delegate.startParams).containsExactly(params)
        assertThat(controller.operationId).isEqualTo(OperationID(OperationType.LOGIN_ID_COLLECT, "hosted"))
        assertThat(controller.whenSettled()).isInstanceOf(OperationResult.Canceled::class.java)
        val abortReason = Reason.UserClose("app dismissed hosted operation")
        controller.abort(abortReason)
        assertThat(operationController.abortReasons).containsExactly(abortReason)

        val showFailures = mutableListOf<String?>()
        scopedEntry.scopeContainer.getOrThrow<OperationUiContainer>().show(
            InstanceName("hosted-instance"),
            OperationID(OperationType.LOGIN_ID_COLLECT, "hosted")
        ) { _, details ->
            showFailures += details
        }

        assertThat(showFailures).isEmpty()
    }

    private class RecordingContainer : DIContainer {
        override val scopeName: String = "recording"
        private val instances = linkedMapOf<Class<*>, Any>()
        private val factories = linkedMapOf<Class<*>, FactoryRegistration>()

        override fun canResolve(clazz: Class<*>): Boolean = instances.containsKey(clazz) || factories.containsKey(clazz)
        override fun getUnsatisfiedDependencies(clazz: Class<*>): List<String>? = null
        override fun <T : Any> getOrThrow(clazz: Class<T>): T = getOrNull(clazz) ?: error("Missing ${clazz.name}")
        override fun <T : Any> getOrNull(clazz: Class<T>): T? = clazz.cast(instances[clazz])
        override fun <T : Any> getAllInstancesOf(clazz: Class<T>): List<T> =
            instances.values.filter { instance -> clazz.isInstance(instance) }.mapNotNull { instance -> clazz.cast(instance) }

        override fun createScope(scopeName: String): DIContainer = RecordingContainer()
        override fun <T : Any> register(clazz: Class<T>, instance: T) {
            instances[clazz] = instance
        }

        override fun <T : Any> registerFactory(clazz: Class<T>, dependencies: List<Class<*>>, factory: DIContainerResolver.() -> T) {
            factories[clazz] = FactoryRegistration(dependencies, factory)
        }

        override fun <T : Any> remove(clazz: Class<T>) {
            instances.remove(clazz)
            factories.remove(clazz)
        }

        fun instanceClasses(): Set<Class<*>> = instances.keys
        fun factoryClasses(): Set<Class<*>> = factories.keys

        inline fun <reified T : Any> factoryDependencies(): List<Class<*>> =
            factories.getValue(T::class.java).dependencies

        inline fun <reified T : Any> create(resolver: DIContainerResolver): T {
            val factory = factories.getValue(T::class.java).factory
            return T::class.java.cast(factory.invoke(resolver)) ?: error("Factory returned null for ${T::class.java.name}")
        }
    }

    private data class FactoryRegistration(
        val dependencies: List<Class<*>>,
        val factory: DIContainerResolver.() -> Any
    )

    private class RecordingScopedOperationEntry(
        override val instanceName: InstanceName,
        private val delegate: RecordingOperationEntry<LoginIdCollectOperation.Params?, LoginId, LoginIdCollectOperationFailure>
    ) : ScopedOperationEntry<LoginIdCollectOperation.Params?, LoginId, LoginIdCollectOperationFailure> {
        lateinit var scopeName: String
        lateinit var scopeContainer: RecordingContainer
        override val operationType: OperationType = delegate.operationType

        override suspend fun availability(params: LoginIdCollectOperation.Params?): Availability =
            delegate.availability(params)

        override fun start(params: LoginIdCollectOperation.Params?): OperationController<LoginId, LoginIdCollectOperationFailure> =
            delegate.start(params)

        override fun withOperationScope(
            scopeName: String,
            configure: DIContainer.() -> Unit
        ): OperationEntry<LoginIdCollectOperation.Params?, LoginId, LoginIdCollectOperationFailure> {
            this.scopeName = scopeName
            this.scopeContainer = RecordingContainer()
            scopeContainer.configure()
            return delegate
        }
    }

    private class RecordingOperationEntry<Params, Success, Failure : OperationFailure>(
        override val operationType: OperationType,
        private val controller: OperationController<Success, Failure>
    ) : OperationEntry<Params, Success, Failure> {
        val availabilityParams = mutableListOf<Params>()
        val startParams = mutableListOf<Params>()

        override suspend fun availability(params: Params): Availability {
            availabilityParams += params
            return Availability.Available()
        }

        override fun start(params: Params): OperationController<Success, Failure> {
            startParams += params
            return controller
        }
    }

}
