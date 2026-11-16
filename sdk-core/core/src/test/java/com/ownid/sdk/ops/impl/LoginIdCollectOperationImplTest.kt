package com.ownid.sdk.ops.impl

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.capability.AppConfigProvider
import com.ownid.sdk.capability.LoginIdConfigurationProvider
import com.ownid.sdk.capability.LoginIdValidationException
import com.ownid.sdk.capability.LoginIdValidator
import com.ownid.sdk.capability.impl.LoginIdConfigurationProviderImpl
import com.ownid.sdk.capability.impl.LoginIdValidatorImpl
import com.ownid.sdk.di.impl.DIContainerImpl
import com.ownid.sdk.di.register
import com.ownid.sdk.model.AppConfig
import com.ownid.sdk.model.Authz
import com.ownid.sdk.model.Context
import com.ownid.sdk.model.Availability
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.ErrorStrings
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.LoginIdConfiguration
import com.ownid.sdk.model.OperationType
import com.ownid.sdk.model.Reason
import com.ownid.sdk.ops.LoginIdCollectOperation
import com.ownid.sdk.ops.LoginIdCollectOperationController
import com.ownid.sdk.ops.LoginIdCollectOperationFailure
import com.ownid.sdk.ops.OperationController
import com.ownid.sdk.ops.OperationRegistry
import com.ownid.sdk.ops.OperationResult
import com.ownid.sdk.ops.ui.LoginIdCollectUi
import com.ownid.sdk.ui.capability.ErrorStringsParams
import com.ownid.sdk.ui.capability.ErrorStringsProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class, InternalOwnIdAPI::class)
// Covers: OPS-090
internal class LoginIdCollectOperationImplTest {

    @Test
    fun validParamLoginIdCompletesWithoutStartingUi() = runTest {
        val ui = RecordingLoginIdCollectUi()
        val operation = operation(ui = ui)

        val controller = operation.start(LoginIdCollectOperation.Params(loginId = emailLoginId))
        drainOperations()

        assertThat(withTimeout(1_000) { controller.whenSettled() }.assertSuccessResult()).isEqualTo(emailLoginId)
        assertThat(ui.startCount).isEqualTo(0)
    }

    @Test
    fun rawPrefillRequiresEmbeddedClassificationAndConfiguredValidation() = runTest {
        val configuration = LoginIdConfigurationProviderImpl().apply {
            setConfiguration(listOf(LoginId.Type.PhoneNumber), mapOf(LoginId.Type.PhoneNumber to "1234567|ext-[0-9]+".toRegex()))
        }
        listOf("1234567" to true, "1234568" to false, "ext-123" to false).forEach { (raw, accepted) ->
            val ui = RecordingLoginIdCollectUi()
            val controller = operation(
                loginIdConfigurationProvider = configuration,
                loginIdValidator = LoginIdValidatorImpl(configuration),
                ui = ui,
                context = Context.Builder().apply { authz = Authz.start(raw) }.build("prefill")
            ).start(null)
            drainOperations()

            if (accepted) {
                assertThat(withTimeout(1_000) { controller.whenSettled() }.assertSuccessResult()).isEqualTo(LoginId(raw, LoginId.Type.PhoneNumber))
                assertThat(ui.startCount).isEqualTo(0)
            } else {
                assertThat(ui.startCount).isEqualTo(1)
                assertThat(controller.activeUiState().loginIdValue).isEqualTo(raw)
                controller.abort(Reason.UserClose("test cleanup"))
                drainOperations()
            }
        }
    }

    @Test
    fun availabilityDoesNotCaptureConfigurationBeforeFirstStart() = runTest {
        val appConfigProvider = TestAppConfigProvider(AppConfig.DEFAULT)
        val operation = operation(appConfigProvider = appConfigProvider)
        assertThat(operation.availability(null)).isInstanceOf(Availability.Available::class.java)

        appConfigProvider.currentConfig = AppConfig.DEFAULT.withLocalVersion(0L)
        val controller = operation.start(LoginIdCollectOperation.Params(loginId = emailLoginId))
        drainOperations()

        assertThat(withTimeout(1_000) { controller.whenSettled() }.assertSuccessResult()).isEqualTo(emailLoginId)
    }

    @Test
    fun firstStartCapturesBeforeQueuedProcessingAndRepeatedStartDoesNotRecapture() = runTest {
        val appConfigProvider = TestAppConfigProvider(AppConfig.DEFAULT.withLocalVersion(0L))
        val ui = RecordingLoginIdCollectUi()
        val registry = OperationRegistryImpl()
        val operation = operation(appConfigProvider = appConfigProvider, ui = ui, registry = registry)

        val controller = operation.start(LoginIdCollectOperation.Params(loginId = emailLoginId))
        appConfigProvider.currentConfig = AppConfig.DEFAULT.withLocalVersion(1L)
        assertThat(operation.start(null)).isSameInstanceAs(controller)
        drainOperations()

        controller.assertConfigurationRestart(registry)
        assertThat(ui.startCount).isEqualTo(0)
    }

    @Test
    fun nullSnapshotUsesExistingLoginIdDefaultsAndFirstPublicationRequiresRestart() = runTest {
        listOf(AppConfig.DEFAULT, AppConfig.DEFAULT.withLocalVersion(0L)).forEach { firstConfig ->
            val appConfigProvider = TestAppConfigProvider()
            val loginIdConfigurationProvider = LoginIdConfigurationProviderImpl()
            val registry = OperationRegistryImpl()
            val operation = operation(
                appConfigProvider = appConfigProvider,
                loginIdConfigurationProvider = loginIdConfigurationProvider,
                loginIdValidator = LoginIdValidatorImpl(loginIdConfigurationProvider),
                registry = registry
            )
            val controller = operation.start(null)
            drainOperations()
            val active = controller.activeUiState()
            assertThat(active.collectableLoginIdTypes).containsExactly(LoginId.Type.Email)

            appConfigProvider.currentConfig = firstConfig
            assertThat(controller.state.value).isInstanceOf(LoginIdCollectOperation.State.Active::class.java)
            active.onLoginIdChange(emailLoginId.id)
            drainOperations()

            controller.assertConfigurationRestart(registry)
        }
    }

    @Test
    fun equalVersionContinuesAndLaterChangesCannotReplaceCompletedResult() = runTest {
        val appConfigProvider = TestAppConfigProvider(AppConfig.DEFAULT.withLocalVersion(0L))
        val registry = OperationRegistryImpl()
        val controller = operation(appConfigProvider = appConfigProvider, registry = registry).start(null)
        drainOperations()
        val initialUiState = controller.activeUiState()

        appConfigProvider.currentConfig = AppConfig.DEFAULT.withLocalVersion(0L)
        initialUiState.onLoginIdChange(emailLoginId.id)
        drainOperations()
        controller.activeUiState().onContinue()
        drainOperations()
        val terminal = withTimeout(1_000) { controller.whenSettled() }
        assertThat(terminal.assertSuccessResult()).isEqualTo(emailLoginId)

        appConfigProvider.currentConfig = AppConfig.DEFAULT.withLocalVersion(1L)
        initialUiState.onContinue()
        controller.abort(Reason.UserClose("late abort"))
        drainOperations()

        assertThat(controller.whenSettled()).isSameInstanceAs(terminal)
        assertThat((controller.state.value as LoginIdCollectOperation.State.Completed).result).isSameInstanceAs(terminal)
        assertThat(registry.current.value).isEmpty()
    }

    @Test
    fun configurationChangeDuringInitialValidationPreventsSuccessAndUiStartup() = runTest {
        listOf(false, true).forEach { reject ->
            val appConfigProvider = TestAppConfigProvider(AppConfig.DEFAULT.withLocalVersion(0L))
            val delegate = TestLoginIdValidator()
            val validator = object : LoginIdValidator by delegate {
                override fun validate(loginId: LoginId): LoginId {
                    val validated = delegate.validate(loginId)
                    appConfigProvider.currentConfig = AppConfig.DEFAULT.withLocalVersion(1L)
                    if (reject) throw LoginIdValidationException.ValidationFailed("Rejected", loginId, ".*")
                    return validated
                }
            }
            val ui = RecordingLoginIdCollectUi()
            val registry = OperationRegistryImpl()
            val controller = operation(appConfigProvider = appConfigProvider, loginIdValidator = validator, ui = ui, registry = registry)
                .start(LoginIdCollectOperation.Params(loginId = emailLoginId))
            drainOperations()

            controller.assertConfigurationRestart(registry)
            assertThat(ui.startCount).isEqualTo(0)
        }
    }

    @Test
    fun configurationChangeDuringFailedValidationStopsBeforeTryingAnotherType() = runTest {
        val appConfigProvider = TestAppConfigProvider(AppConfig.DEFAULT.withLocalVersion(0L))
        var validations = 0
        val validator = object : LoginIdValidator by TestLoginIdValidator() {
            override fun validate(loginId: LoginId): LoginId {
                validations++
                appConfigProvider.currentConfig = AppConfig.DEFAULT.withLocalVersion(1L)
                throw LoginIdValidationException.ValidationFailed("Rejected", loginId, ".*")
            }
        }
        val registry = OperationRegistryImpl()
        val configuration = LoginIdConfiguration(listOf(LoginId.Type.Email, LoginId.Type.UserName), emptyMap())
        val controller = operation(
            appConfigProvider = appConfigProvider,
            loginIdConfigurationProvider = TestLoginIdConfigurationProvider(configuration),
            loginIdValidator = validator,
            registry = registry
        ).start(null)
        drainOperations()
        controller.activeUiState().onLoginIdChange("invalid")
        drainOperations()
        controller.activeUiState().onContinue()
        drainOperations()

        controller.assertConfigurationRestart(registry)
        assertThat(validations).isEqualTo(1)
    }

    @Test
    fun registryObserverConfigurationChangePreventsUiStartup() = runTest {
        val appConfigProvider = TestAppConfigProvider(AppConfig.DEFAULT.withLocalVersion(0L))
        val registry = OperationRegistryImpl()
        val ui = RecordingLoginIdCollectUi()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            registry.current.collect { controllers ->
                if (controllers.isNotEmpty()) appConfigProvider.currentConfig = AppConfig.DEFAULT.withLocalVersion(1L)
            }
        }

        val controller = operation(appConfigProvider = appConfigProvider, ui = ui, registry = registry).start(null)
        drainOperations()

        controller.assertConfigurationRestart(registry)
        assertThat(ui.startCount).isEqualTo(0)
    }

    @Test
    fun configurationChangeDuringUiStartupOverridesStartupErrorsAndSettlesOnce() = runTest {
        val appConfigProvider = TestAppConfigProvider(AppConfig.DEFAULT.withLocalVersion(0L))
        val registry = OperationRegistryImpl()
        val ui = object : LoginIdCollectUi {
            override fun start(
                controller: OperationController<LoginId, LoginIdCollectOperationFailure>,
                onStartError: (error: Throwable?, details: String?) -> Unit
            ) {
                appConfigProvider.currentConfig = AppConfig.DEFAULT.withLocalVersion(1L)
                onStartError(IllegalStateException("first"), "first")
                onStartError(IllegalStateException("second"), "second")
            }
        }
        val controller = operation(appConfigProvider = appConfigProvider, ui = ui, registry = registry).start(null)
        drainOperations()
        val terminal = controller.assertConfigurationRestart(registry)

        controller.abort(Reason.UserClose("late abort"))
        drainOperations()
        assertThat(controller.whenSettled()).isSameInstanceAs(terminal)
    }

    @Test
    fun deferredErrorStringsUpdateRechecksConfiguration() = runTest {
        val appConfigProvider = TestAppConfigProvider(AppConfig.DEFAULT.withLocalVersion(0L))
        val strings = MutableSharedFlow<ErrorStrings?>(extraBufferCapacity = 1)
        val errorStringsProvider = object : ErrorStringsProvider {
            override fun getStrings(params: ErrorStringsParams): Flow<ErrorStrings?> = strings
        }
        val registry = OperationRegistryImpl()
        val controller = operation(
            appConfigProvider = appConfigProvider,
            errorStringsProvider = errorStringsProvider,
            registry = registry
        ).start(null)
        drainOperations()
        assertThat(controller.state.value).isInstanceOf(LoginIdCollectOperation.State.Active::class.java)

        appConfigProvider.currentConfig = AppConfig.DEFAULT.withLocalVersion(1L)
        assertThat(strings.tryEmit(ErrorStrings.DEFAULT)).isTrue()
        drainOperations()

        controller.assertConfigurationRestart(registry)
    }

    @Test
    fun uiClickConfigurationChangeStopsValidationAndPreservesQueuedAbort() = runTest {
        listOf(false, true).forEach { abortFromCallback ->
            val appConfigProvider = TestAppConfigProvider(AppConfig.DEFAULT.withLocalVersion(0L))
            val registry = OperationRegistryImpl()
            val reason = Reason.UserClose("callback abort")
            var validations = 0
            val delegate = TestLoginIdValidator()
            val validator = object : LoginIdValidator by delegate {
                override fun validate(loginId: LoginId): LoginId {
                    validations++
                    return delegate.validate(loginId)
                }
            }
            lateinit var controller: LoginIdCollectOperationController
            controller = operation(appConfigProvider = appConfigProvider, loginIdValidator = validator, registry = registry).start(
                LoginIdCollectOperation.Params(onUiClick = {
                    appConfigProvider.currentConfig = AppConfig.DEFAULT.withLocalVersion(1L)
                    if (abortFromCallback) controller.abort(reason)
                })
            )
            drainOperations()

            controller.activeUiState().onContinue()
            drainOperations()
            val terminal = if (abortFromCallback) {
                withTimeout(1_000) { controller.whenSettled() }.also { assertThat(it.assertCanceledResult()).isSameInstanceAs(reason) }
            } else {
                controller.assertConfigurationRestart(registry)
            }

            assertThat(validations).isEqualTo(0)
            assertThat((controller.state.value as LoginIdCollectOperation.State.Completed).result).isSameInstanceAs(terminal)
            assertThat(registry.current.value).isEmpty()
        }
    }

    @Test
    fun missingAppConfigProviderUsesExistingUnavailableAndFailedStartPath() = runTest {
        val registry = OperationRegistryImpl()
        val container = DIContainerImpl("missing-app-config").apply { register<OperationRegistry>(registry) }
        val operation = LoginIdCollectOperationImpl.create(container)

        val availability = operation.availability(null)
        assertThat(availability).isInstanceOf(Availability.Unavailable::class.java)
        assertThat((availability as Availability.Unavailable).message).contains("AppConfigProvider")
        val controller = operation.start(null) as LoginIdCollectOperationController
        val result = withTimeout(1_000) { controller.whenSettled() }

        val failure = result.assertFailureResult()
        assertThat(failure).isInstanceOf(LoginIdCollectOperationFailure.Unexpected::class.java)
        assertThat(failure.message).contains("AppConfigProvider")
        assertThat((controller.state.value as LoginIdCollectOperation.State.Completed).result.assertFailureResult()).isSameInstanceAs(failure)
        assertThat(registry.current.value).isEmpty()
    }

    private fun TestScope.operation(
        appConfigProvider: AppConfigProvider = TestAppConfigProvider(),
        loginIdConfigurationProvider: LoginIdConfigurationProvider = TestLoginIdConfigurationProvider(),
        loginIdValidator: LoginIdValidator = TestLoginIdValidator(),
        ui: LoginIdCollectUi = RecordingLoginIdCollectUi(),
        errorStringsProvider: ErrorStringsProvider? = null,
        registry: OperationRegistryImpl = OperationRegistryImpl(),
        context: Context? = null
    ): LoginIdCollectOperationImpl = LoginIdCollectOperationImpl(
        operationType = OperationType.LOGIN_ID_COLLECT,
        operationRegistry = registry,
        appConfigProvider = appConfigProvider,
        loginIdConfigurationProvider = loginIdConfigurationProvider,
        loginIdValidator = loginIdValidator,
        ui = ui,
        errorStringsProvider = errorStringsProvider,
        defaultDispatcher = operationTestDispatcher(),
        instanceJob = backgroundScope.coroutineContext[Job]!!,
        context = context,
        unsatisfiedDependencies = null,
        logger = null
    )

    private fun LoginIdCollectOperationController.activeUiState(): LoginIdCollectUi.UiState =
        (state.value as LoginIdCollectOperation.State.Active).uiState

    private suspend fun LoginIdCollectOperationController.assertConfigurationRestart(
        registry: OperationRegistryImpl
    ): OperationResult<LoginId, LoginIdCollectOperationFailure> {
        val result = withTimeout(1_000) { whenSettled() }
        val failure = result.assertFailureResult()
        assertThat(failure).isInstanceOf(LoginIdCollectOperationFailure.Unexpected::class.java)
        assertThat(failure.errorCode).isEqualTo(ErrorCode.IntegrationError)
        assertThat(failure.message).isEqualTo("App configuration changed. Restart the operation.")
        assertThat((state.value as LoginIdCollectOperation.State.Completed).result).isSameInstanceAs(result)
        assertThat(registry.current.value).isEmpty()
        return result
    }

    private class RecordingLoginIdCollectUi : LoginIdCollectUi {
        var startCount = 0

        override fun start(
            controller: OperationController<LoginId, LoginIdCollectOperationFailure>,
            onStartError: (error: Throwable?, details: String?) -> Unit
        ) {
            startCount++
        }
    }
}
