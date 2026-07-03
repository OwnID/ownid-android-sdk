package com.ownid.sdk.ops.impl

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.ownid.sdk.api.ApiResult
import com.ownid.sdk.api.PasskeyAssertionApi
import com.ownid.sdk.api.PasskeyAssertionCancelApiFailure
import com.ownid.sdk.api.PasskeyAssertionStartApiFailure
import com.ownid.sdk.api.PasskeyAssertionVerifyApiFailure
import com.ownid.sdk.api.PasskeyEnrollApi
import com.ownid.sdk.api.PasskeyEnrollApiFailure
import com.ownid.sdk.capability.Passkey
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.AssertionOptions
import com.ownid.sdk.model.AssertionResult
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.LoginIdConfiguration
import com.ownid.sdk.model.OperationType
import com.ownid.sdk.model.Reason
import com.ownid.sdk.ops.LoginIdCollectOperation
import com.ownid.sdk.ops.LoginIdCollectOperationController
import com.ownid.sdk.ops.LoginIdCollectOperationFailure
import com.ownid.sdk.ops.OperationController
import com.ownid.sdk.ops.OperationResult
import com.ownid.sdk.ops.PasskeyAssertionOperation
import com.ownid.sdk.ops.PasskeyEnrollOperation
import com.ownid.sdk.ops.ui.LoginIdCollectUi
import com.ownid.sdk.ops.ui.PasskeyAssertionUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(InternalOwnIdAPI::class)
// Covers: OPS-030, OPS-040, OPS-050
internal class OperationControllerLifecycleContractTest {

    @Test
    fun controllersSettleOnceAndIgnoreLateAbort(): Unit = runTest {
        lifecycleCases().forEach { createCase ->
            val started = createCase()

            started.completeTerminal(this)
            val terminalResult = started.controller.whenSettled()
            started.controller.abort(Reason.UserClose("late abort"))
            drainOperations()

            assertWithMessage(started.label).that(started.controller.whenSettled()).isSameInstanceAs(terminalResult)
            assertWithMessage(started.label).that(terminalResult).isInstanceOf(OperationResult.Success::class.java)
            assertWithMessage(started.label).that(started.registry.current.value).isEmpty()
        }
    }

    @Test
    fun controllersSettleOnceAndIgnoreLateSuccess(): Unit = runTest {
        lifecycleCases().forEach { createCase ->
            val started = createCase()
            val reason = Reason.UserClose("test")

            started.controller.abort(reason)
            drainOperations()
            val terminalResult = started.controller.whenSettled()
            started.completeLateSuccess(this)
            drainOperations()

            assertWithMessage(started.label).that(started.controller.whenSettled()).isSameInstanceAs(terminalResult)
            assertWithMessage(started.label).that((terminalResult as OperationResult.Canceled).reason).isSameInstanceAs(reason)
            assertWithMessage(started.label).that(started.registry.current.value).isEmpty()
        }
    }

    @Test
    fun repeatedAwaitersReceiveCachedTerminalResult(): Unit = runTest {
        lifecycleCases().forEach { createCase ->
            val started = createCase()
            val firstAwaiter = backgroundScope.async { started.controller.whenSettled() }
            val secondAwaiter = backgroundScope.async { started.controller.whenSettled() }
            drainOperations()

            started.completeTerminal(this)
            val firstResult = firstAwaiter.await()
            val secondResult = secondAwaiter.await()

            assertWithMessage(started.label).that(firstResult).isInstanceOf(OperationResult.Success::class.java)
            assertWithMessage(started.label).that(secondResult).isSameInstanceAs(firstResult)
            assertWithMessage(started.label).that(started.controller.whenSettled()).isSameInstanceAs(firstResult)
        }
    }

    @Test
    fun cancelingAwaiterDoesNotCancelActiveOperation(): Unit = runTest {
        lifecycleCases().forEach { createCase ->
            val started = createCase()
            val awaiter = backgroundScope.launch { started.controller.whenSettled() }
            drainOperations()

            awaiter.cancel()
            drainOperations()

            assertWithMessage(started.label).that(awaiter.isCancelled).isTrue()
            assertWithMessage(started.label).that(started.registry.current.value).containsKey(started.controller.operationId)
            started.assertActiveAfterAwaiterCancellation(this)

            started.completeTerminal(this)

            assertWithMessage(started.label).that(started.controller.whenSettled()).isInstanceOf(OperationResult.Success::class.java)
            assertWithMessage(started.label).that(started.registry.current.value).isEmpty()
            started.assertNoProducerCancellation(this)
        }
    }

    private fun TestScope.lifecycleCases(): List<TestScope.() -> StartedController> = listOf(
        { startPasskeyEnrollController() },
        { startPasskeyAssertionController() },
        { startLoginIdCollectController() }
    )

    private fun TestScope.startPasskeyEnrollController(): StartedController {
        val registry = OperationRegistryImpl()
        val api = DeferredPasskeyEnrollApi()
        val operation = PasskeyEnrollOperationImpl(
            operationType = OperationType.PASSKEY_ENROLLMENT,
            operationRegistry = registry,
            api = api,
            defaultDispatcher = operationTestDispatcher(),
            instanceJob = Job(),
            context = accessTokenContext(accessToken),
            unsatisfiedDependencies = null,
            logger = null
        )

        val controller = operation.start(PasskeyEnrollOperation.Params(proofToken = proofToken))
        drainOperations()

        return StartedController(
            label = "passkey enroll",
            controller = controller,
            registry = registry,
            completeTerminal = {
                api.response.complete(ApiResult.Success(Unit))
                drainOperations()
            },
            assertActiveAfterAwaiterCancellation = {
                assertThat(api.started.isCompleted).isTrue()
                assertThat(api.cancellations).isEqualTo(0)
            },
            assertNoProducerCancellation = {
                assertThat(api.cancellations).isEqualTo(0)
            }
        )
    }

    private fun TestScope.startPasskeyAssertionController(): StartedController {
        val registry = OperationRegistryImpl()
        val apiController = DeferredAssertionController(assertionOptions())
        val api = DeferredPasskeyAssertionApi()
        val ui = DeferredPasskeyAssertionUi()
        val operation = PasskeyAssertionOperationImpl(
            operationType = OperationType.PASSKEY_AUTH,
            operationRegistry = registry,
            ui = ui,
            api = api,
            defaultDispatcher = operationTestDispatcher(),
            instanceJob = Job(),
            context = null,
            loginIdValidator = TestLoginIdValidator(),
            unsatisfiedDependencies = null,
            logger = null
        )

        val controller = operation.start(PasskeyAssertionOperation.Params(loginId = emailLoginId))
        drainOperations()

        return StartedController(
            label = "passkey assertion",
            controller = controller,
            registry = registry,
            completeTerminal = {
                api.startResponse.complete(ApiResult.Success(apiController))
                drainOperations()
                ui.assertionResult.complete(Passkey.Result.Success(assertionResult()))
                drainOperations()
                apiController.verifyResponse.complete(ApiResult.Success(accessToken))
                drainOperations()
            },
            assertActiveAfterAwaiterCancellation = {
                assertThat(api.started.isCompleted).isTrue()
                assertThat(api.cancellations).isEqualTo(0)
            },
            assertNoProducerCancellation = {
                assertThat(api.cancellations).isEqualTo(0)
                assertThat(apiController.verifyCancellations).isEqualTo(0)
                assertThat(apiController.canceledReasons).isEmpty()
            }
        )
    }

    private fun TestScope.startLoginIdCollectController(): StartedController {
        val registry = OperationRegistryImpl()
        val ui = RecordingLoginIdCollectUi()
        val operation = LoginIdCollectOperationImpl(
            operationType = OperationType.LOGIN_ID_COLLECT,
            operationRegistry = registry,
            loginIdConfigurationProvider = TestLoginIdConfigurationProvider(LoginIdConfiguration.DEFAULT_EMAIL),
            loginIdValidator = TestLoginIdValidator(),
            ui = ui,
            errorStringsProvider = null,
            defaultDispatcher = operationTestDispatcher(),
            instanceJob = Job(),
            context = null,
            unsatisfiedDependencies = null,
            logger = null
        )

        val controller = operation.start(null)
        drainOperations()
        val initialUiState = controller.activeUiState()

        return StartedController(
            label = "login ID collect",
            controller = controller,
            registry = registry,
            completeTerminal = {
                val state = controller.activeUiState()
                state.onLoginIdChange(emailLoginId.id)
                drainOperations()
                controller.activeUiState().onContinue()
                drainOperations()
            },
            completeLateSuccess = {
                initialUiState.onLoginIdChange(emailLoginId.id)
                initialUiState.onContinue()
                drainOperations()
            },
            assertActiveAfterAwaiterCancellation = {
                assertThat(ui.startCount).isEqualTo(1)
                assertThat(controller.state.value).isInstanceOf(LoginIdCollectOperation.State.Active::class.java)
            }
        )
    }

    private data class StartedController(
        val label: String,
        val controller: OperationController<*, *>,
        val registry: OperationRegistryImpl,
        val completeTerminal: suspend TestScope.() -> Unit,
        val completeLateSuccess: suspend TestScope.() -> Unit = completeTerminal,
        val assertActiveAfterAwaiterCancellation: TestScope.() -> Unit = {},
        val assertNoProducerCancellation: TestScope.() -> Unit = {}
    )

    private fun LoginIdCollectOperationController.activeUiState(): LoginIdCollectUi.UiState =
        (state.value as LoginIdCollectOperation.State.Active).uiState

    private class DeferredPasskeyEnrollApi : PasskeyEnrollApi {
        val started = CompletableDeferred<PasskeyEnrollApi.Params>()
        val response = CompletableDeferred<ApiResult<Unit, PasskeyEnrollApiFailure>>()
        var cancellations = 0
            private set

        override suspend fun start(params: PasskeyEnrollApi.Params): ApiResult<Unit, PasskeyEnrollApiFailure> {
            started.complete(params)
            return try {
                response.await()
            } catch (error: CancellationException) {
                cancellations += 1
                throw error
            }
        }
    }

    private class DeferredPasskeyAssertionApi : PasskeyAssertionApi {
        val started = CompletableDeferred<PasskeyAssertionApi.Params?>()
        val startResponse = CompletableDeferred<ApiResult<PasskeyAssertionApi.Controller, PasskeyAssertionStartApiFailure>>()
        var cancellations = 0
            private set

        override suspend fun start(params: PasskeyAssertionApi.Params?): ApiResult<PasskeyAssertionApi.Controller, PasskeyAssertionStartApiFailure> {
            started.complete(params)
            return try {
                startResponse.await()
            } catch (error: CancellationException) {
                cancellations += 1
                throw error
            }
        }
    }

    private class DeferredAssertionController(
        override val assertionOptions: AssertionOptions
    ) : PasskeyAssertionApi.Controller {
        val verifyResponse = CompletableDeferred<ApiResult<AccessToken, PasskeyAssertionVerifyApiFailure>>()
        val verifiedAssertions = mutableListOf<AssertionResult>()
        val canceledReasons = mutableListOf<Reason>()
        var verifyCancellations = 0
            private set

        override val verify: suspend (assertion: AssertionResult) -> ApiResult<AccessToken, PasskeyAssertionVerifyApiFailure> = { assertion ->
            verifiedAssertions += assertion
            try {
                verifyResponse.await()
            } catch (error: CancellationException) {
                verifyCancellations += 1
                throw error
            }
        }

        override val cancel: suspend (reason: Reason) -> ApiResult<Unit, PasskeyAssertionCancelApiFailure> = { reason ->
            canceledReasons += reason
            ApiResult.Success(Unit)
        }
    }

    private class DeferredPasskeyAssertionUi : PasskeyAssertionUi {
        val assertionResult = CompletableDeferred<Passkey.Result<AssertionResult>>()

        override suspend fun getCredential(assertionOptions: AssertionOptions): Passkey.Result<AssertionResult> =
            assertionResult.await()
    }

    private class RecordingLoginIdCollectUi : LoginIdCollectUi {
        var startCount = 0
            private set

        override fun start(
            controller: OperationController<LoginId, LoginIdCollectOperationFailure>,
            onStartError: (error: Throwable?, details: String?) -> Unit
        ) {
            startCount += 1
        }
    }
}
