package com.ownid.sdk.compose.widget

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.flow.FlowFailure
import com.ownid.sdk.flow.FlowResult
import com.ownid.sdk.flow.boost.BoostCreatePasskeyFlowFailure
import com.ownid.sdk.flow.boost.BoostFlow
import com.ownid.sdk.flow.boost.BoostFlowContext
import com.ownid.sdk.flow.boost.BoostLoginFlowFailure
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.AuthMethod
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.ProofToken
import com.ownid.sdk.model.Reason
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
// Covers: UI-150, UI-160, UI-180, UI-200, UI-210, UI-220, UI-230, UI-250, UI-270
internal class OwnIdWidgetViewModelContractTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun loginWidgetEmitsTerminalEffectsAndResetsRunningState() = runTest(mainDispatcherRule.testDispatcher) {
        val loginResponse = loginResponse("login@example.com")
        val failure = BoostLoginFlowFailure.Account.NotFound(ErrorCode.UserNotFound, "No account")
        val cancelReason = Reason.UserClose("Dismissed")
        val provider = RecordingBoostFlowProvider<BoostFlow.Response.Login, BoostLoginFlowFailure>(
            FlowResult.Success(loginResponse),
            FlowResult.Failure(failure),
            FlowResult.Canceled(cancelReason)
        )
        val viewModel = OwnIdLoginWidgetViewModel(provider::start)

        viewModel.startFlow("login@example.com")
        advanceUntilIdle()
        viewModel.startFlow(null)
        advanceUntilIdle()
        viewModel.startFlow(null)
        advanceUntilIdle()

        val effects = viewModel.uiEffects.collectExactly(3)

        assertThat(viewModel.uiState.value).isEqualTo(OwnIdLoginWidgetViewModel.UiState(isRunning = false))
        assertThat(effects).containsExactly(
            OwnIdLoginWidgetViewModel.UiEffect.Login(loginResponse),
            OwnIdLoginWidgetViewModel.UiEffect.Error(failure),
            OwnIdLoginWidgetViewModel.UiEffect.Canceled(cancelReason)
        ).inOrder()
        assertWidgetButtonContextPresence(provider.contexts, true, false, false)
    }

    @Test
    fun loginWidgetIgnoresRepeatedStartsWhileRunning() = runTest(mainDispatcherRule.testDispatcher) {
        val result = CompletableDeferred<FlowResult<BoostFlow.Response.Login, BoostLoginFlowFailure>>()
        val provider = RecordingBoostFlowProvider(result)
        val viewModel = OwnIdLoginWidgetViewModel(provider::start)

        viewModel.startFlow("running@example.com")
        runCurrent()
        viewModel.startFlow("ignored@example.com")
        runCurrent()

        assertThat(provider.contexts).hasSize(1)
        assertWidgetButtonContextPresence(provider.contexts, true)
        assertThat(viewModel.uiState.value).isEqualTo(OwnIdLoginWidgetViewModel.UiState(isRunning = true))

        val loginResponse = loginResponse("running@example.com")
        result.complete(FlowResult.Success(loginResponse))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value).isEqualTo(OwnIdLoginWidgetViewModel.UiState(isRunning = false))
        assertThat(viewModel.uiEffects.first()).isEqualTo(OwnIdLoginWidgetViewModel.UiEffect.Login(loginResponse))
    }

    @Test
    fun loginWidgetNormalizesBlankAndWhitespaceLoginIdsBeforeStartingFlow() = runTest(mainDispatcherRule.testDispatcher) {
        val provider = RecordingBoostFlowProvider<BoostFlow.Response.Login, BoostLoginFlowFailure>(
            FlowResult.Success(loginResponse("trimmed")),
            FlowResult.Success(loginResponse("blank"))
        )
        val viewModel = OwnIdLoginWidgetViewModel(provider::start)

        viewModel.startFlow("  trimmed  ")
        advanceUntilIdle()
        viewModel.startFlow("   ")
        advanceUntilIdle()

        assertWidgetButtonRawLoginIds(provider.contexts, "trimmed", null)
    }

    @Test
    fun loginWidgetEmitsFailureAndResetsRunningStateWhenProviderThrows() = runTest(mainDispatcherRule.testDispatcher) {
        val startError = IllegalStateException("login start failed")
        val viewModel = OwnIdLoginWidgetViewModel { throw startError }

        viewModel.startFlow("login@example.com")
        advanceUntilIdle()

        val effect = viewModel.uiEffects.first()

        assertThat(viewModel.uiState.value).isEqualTo(OwnIdLoginWidgetViewModel.UiState(isRunning = false))
        assertThat(effect).isInstanceOf(OwnIdLoginWidgetViewModel.UiEffect.Error::class.java)
        val failure = (effect as OwnIdLoginWidgetViewModel.UiEffect.Error).failure
        assertThat(failure).isInstanceOf(BoostLoginFlowFailure.Unexpected::class.java)
        failure as BoostLoginFlowFailure.Unexpected
        assertThat(failure.errorCode).isEqualTo(ErrorCode.IntegrationError)
        assertThat(failure.message).isEqualTo("login start failed")
        assertThat(failure.error).isSameInstanceAs(startError)
    }

    @Test
    fun createPasskeyWidgetEmitsTerminalEffectsAndTracksCompletionByLoginId() = runTest(mainDispatcherRule.testDispatcher) {
        val loginResponse = loginResponse("existing@example.com")
        val createPasskeyResponse = createPasskeyResponse("new@example.com")
        val provider = RecordingBoostFlowProvider<BoostFlow.Response, BoostCreatePasskeyFlowFailure>(
            FlowResult.Success(loginResponse),
            FlowResult.Success(createPasskeyResponse)
        )
        val viewModel = OwnIdCreatePasskeyWidgetViewModel(provider::start)

        viewModel.startFlow("existing@example.com")
        advanceUntilIdle()
        viewModel.startFlow("new@example.com")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value).isEqualTo(OwnIdCreatePasskeyWidgetViewModel.UiState(isRunning = false, showCheckmark = true))

        viewModel.onLoginIdChanged("other@example.com")
        advanceUntilIdle()
        assertThat(viewModel.uiState.value).isEqualTo(OwnIdCreatePasskeyWidgetViewModel.UiState(isRunning = false, showCheckmark = false))

        viewModel.onLoginIdChanged("  new@example.com \n")
        advanceUntilIdle()
        assertThat(viewModel.uiState.value).isEqualTo(OwnIdCreatePasskeyWidgetViewModel.UiState(isRunning = false, showCheckmark = true))

        val effects = viewModel.uiEffects.collectExactly(4)

        assertThat(effects).containsExactly(
            OwnIdCreatePasskeyWidgetViewModel.UiEffect.Login(loginResponse),
            OwnIdCreatePasskeyWidgetViewModel.UiEffect.CreatePasskey(createPasskeyResponse),
            OwnIdCreatePasskeyWidgetViewModel.UiEffect.ResetRequested,
            OwnIdCreatePasskeyWidgetViewModel.UiEffect.CreatePasskey(createPasskeyResponse)
        ).inOrder()
        assertWidgetButtonContextPresence(provider.contexts, true, true)
    }

    @Test
    fun createPasskeyWidgetEmitsFailureAndCancelEffectsAndIgnoresRepeatedStartsWhileRunning() = runTest(mainDispatcherRule.testDispatcher) {
        val runningResult = CompletableDeferred<FlowResult<BoostFlow.Response, BoostCreatePasskeyFlowFailure>>()
        val runningResponse = createPasskeyResponse("running@example.com")
        val failure = BoostCreatePasskeyFlowFailure.Account.Blocked(ErrorCode.UserBlocked, "Blocked")
        val cancelReason = Reason.UserClose("Dismissed")
        val provider = RecordingBoostFlowProvider(
            runningResult,
            FlowResult.Failure(failure),
            FlowResult.Canceled(cancelReason)
        )
        val viewModel = OwnIdCreatePasskeyWidgetViewModel(provider::start)

        viewModel.startFlow("running@example.com")
        runCurrent()
        viewModel.startFlow("ignored@example.com")
        runCurrent()

        assertThat(provider.contexts).hasSize(1)
        assertWidgetButtonContextPresence(provider.contexts, true)
        assertThat(viewModel.uiState.value).isEqualTo(OwnIdCreatePasskeyWidgetViewModel.UiState(isRunning = true, showCheckmark = false))

        runningResult.complete(FlowResult.Success(runningResponse))
        advanceUntilIdle()
        viewModel.startFlow("running@example.com")
        advanceUntilIdle()
        viewModel.startFlow(null)
        advanceUntilIdle()
        viewModel.startFlow(null)
        advanceUntilIdle()

        val effects = viewModel.uiEffects.collectExactly(4)

        assertThat(effects).containsExactly(
            OwnIdCreatePasskeyWidgetViewModel.UiEffect.CreatePasskey(runningResponse),
            OwnIdCreatePasskeyWidgetViewModel.UiEffect.ResetRequested,
            OwnIdCreatePasskeyWidgetViewModel.UiEffect.Error(failure),
            OwnIdCreatePasskeyWidgetViewModel.UiEffect.Canceled(cancelReason)
        ).inOrder()
        assertWidgetButtonContextPresence(provider.contexts, true, false, false)
        assertThat(viewModel.uiState.value).isEqualTo(OwnIdCreatePasskeyWidgetViewModel.UiState(isRunning = false, showCheckmark = false))
    }

    @Test
    fun createPasskeyWidgetEmitsFailureAndResetsRunningStateWhenProviderThrows() = runTest(mainDispatcherRule.testDispatcher) {
        val startError = IllegalStateException("create passkey start failed")
        val viewModel = OwnIdCreatePasskeyWidgetViewModel { throw startError }

        viewModel.startFlow("new@example.com")
        advanceUntilIdle()

        val effect = viewModel.uiEffects.first()

        assertThat(viewModel.uiState.value).isEqualTo(OwnIdCreatePasskeyWidgetViewModel.UiState(isRunning = false, showCheckmark = false))
        assertThat(effect).isInstanceOf(OwnIdCreatePasskeyWidgetViewModel.UiEffect.Error::class.java)
        val failure = (effect as OwnIdCreatePasskeyWidgetViewModel.UiEffect.Error).failure
        assertThat(failure).isInstanceOf(BoostCreatePasskeyFlowFailure.Unexpected::class.java)
        failure as BoostCreatePasskeyFlowFailure.Unexpected
        assertThat(failure.errorCode).isEqualTo(ErrorCode.IntegrationError)
        assertThat(failure.message).isEqualTo("create passkey start failed")
        assertThat(failure.error).isSameInstanceAs(startError)
    }

    @Test
    fun createPasskeyWidgetNormalizesBlankAndWhitespaceLoginIdsBeforeStartingFlow() = runTest(mainDispatcherRule.testDispatcher) {
        val provider = RecordingBoostFlowProvider<BoostFlow.Response, BoostCreatePasskeyFlowFailure>(
            FlowResult.Success(loginResponse("trimmed")),
            FlowResult.Success(loginResponse("blank"))
        )
        val viewModel = OwnIdCreatePasskeyWidgetViewModel(provider::start)

        viewModel.startFlow("  trimmed  ")
        advanceUntilIdle()
        viewModel.startFlow("   ")
        advanceUntilIdle()

        assertWidgetButtonRawLoginIds(provider.contexts, "trimmed", null)
    }

    private suspend fun <T> Flow<T>.collectExactly(count: Int): List<T> = take(count).toList()

    private fun assertWidgetButtonContextPresence(contexts: List<BoostFlowContext>, vararg expectedRawLoginIdPresent: Boolean) {
        assertThat(contexts).hasSize(expectedRawLoginIdPresent.size)
        contexts.zip(expectedRawLoginIdPresent.toList()).forEach { (context, expectedPresent) ->
            assertWidgetButtonContext(context, expectedPresent)
        }
    }

    private fun assertWidgetButtonContext(context: BoostFlowContext, expectedRawLoginIdPresent: Boolean) {
        val description = context.toString()
        val rawLoginIdDescription = rawLoginIdDescription(description)

        assertThat(context.loginId).isNull()
        if (expectedRawLoginIdPresent) {
            assertThat(rawLoginIdDescription).isNotNull()
        } else {
            assertThat(rawLoginIdDescription).isNull()
        }
        assertThat(description).contains("source=WIDGET_BUTTON")
    }

    private fun assertWidgetButtonRawLoginIds(contexts: List<BoostFlowContext>, vararg expectedRawLoginIds: String?) {
        assertThat(contexts).hasSize(expectedRawLoginIds.size)
        contexts.zip(expectedRawLoginIds.toList()).forEach { (context, expectedRawLoginId) ->
            val description = context.toString()

            assertThat(context.loginId).isNull()
            assertThat(rawLoginIdDescription(description)).isEqualTo(expectedRawLoginId)
            assertThat(description).contains("source=WIDGET_BUTTON")
        }
    }

    private fun rawLoginIdDescription(description: String): String? =
        RAW_LOGIN_ID_PATTERN.find(description)?.groupValues?.get(1)

    private fun loginResponse(id: String): BoostFlow.Response.Login =
        BoostFlow.Response.Login(
            loginId = LoginId(id, LoginId.Type.Email),
            authMethod = AuthMethod.Passkey,
            accessToken = AccessToken("access-token-$id"),
            sessionPayload = "session-payload",
            session = null
        )

    private fun createPasskeyResponse(id: String): BoostFlow.Response.CreatePasskey =
        BoostFlow.Response.CreatePasskey(
            loginId = LoginId(id, LoginId.Type.Email),
            proofToken = ProofToken("proof-token-$id"),
            ownIdData = "own-id-data"
        )

    private class RecordingBoostFlowProvider<Success, Failure : FlowFailure>(
        private val queuedResults: ArrayDeque<suspend () -> FlowResult<Success, Failure>>
    ) {
        val contexts = mutableListOf<BoostFlowContext>()

        constructor(vararg results: FlowResult<Success, Failure>) : this(
            ArrayDeque(results.map { result -> suspend { result } })
        )

        constructor(
            firstResult: CompletableDeferred<FlowResult<Success, Failure>>,
            vararg results: FlowResult<Success, Failure>
        ) : this(
            ArrayDeque(listOf<suspend () -> FlowResult<Success, Failure>> { firstResult.await() } + results.map { result -> suspend { result } })
        )

        fun start(context: BoostFlowContext): BoostFlow.Controller<Success, Failure> {
            contexts += context
            val nextResult = queuedResults.removeFirstOrNull() ?: error("No queued flow result for context $context")
            return object : BoostFlow.Controller<Success, Failure> {
                override suspend fun whenSettled(): FlowResult<Success, Failure> = nextResult()
                override fun abort(reason: Reason): Unit = Unit
            }
        }
    }

    private companion object {
        private val RAW_LOGIN_ID_PATTERN = Regex("rawLoginId=([^,)]+)")
    }
}
