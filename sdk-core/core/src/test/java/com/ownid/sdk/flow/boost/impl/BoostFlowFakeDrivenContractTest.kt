package com.ownid.sdk.flow.boost.impl

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.analytics.FlowInfo
import com.ownid.sdk.analytics.UserJourney
import com.ownid.sdk.capability.DispatcherProvider
import com.ownid.sdk.capability.LoginIdValidator
import com.ownid.sdk.capability.Serialization
import com.ownid.sdk.di.getOrNull
import com.ownid.sdk.di.impl.DIContainerImpl
import com.ownid.sdk.di.register
import com.ownid.sdk.di.registerFactory
import com.ownid.sdk.capability.Capability
import com.ownid.sdk.capability.UserRepository
import com.ownid.sdk.flow.AcceptingLoginIdValidator
import com.ownid.sdk.flow.FlowResult
import com.ownid.sdk.flow.JsonSerialization
import com.ownid.sdk.flow.MainDispatcherRule
import com.ownid.sdk.flow.RecordingOperationEntry
import com.ownid.sdk.flow.accessTokenFor
import com.ownid.sdk.flow.boost.BoostCreatePasskeyFlowFailure
import com.ownid.sdk.flow.boost.BoostFlow
import com.ownid.sdk.flow.boost.BoostFlowContext
import com.ownid.sdk.flow.boost.BoostLoginAuthOperation
import com.ownid.sdk.flow.boost.BoostLoginFlowFailure
import com.ownid.sdk.flow.recordingEntry
import com.ownid.sdk.flow.unavailableEntry
import com.ownid.sdk.model.AccessOrProofToken
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.AccessTokenWithUserInfo
import com.ownid.sdk.model.AttestationResponse
import com.ownid.sdk.model.AuthMethod
import com.ownid.sdk.model.AuthRequirements
import com.ownid.sdk.model.Authz
import com.ownid.sdk.model.Availability
import com.ownid.sdk.model.Context
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.InstanceName
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.LoginResponse
import com.ownid.sdk.model.OperationChannel
import com.ownid.sdk.model.OperationID
import com.ownid.sdk.model.OperationRequirement
import com.ownid.sdk.model.OperationType
import com.ownid.sdk.model.ProofToken
import com.ownid.sdk.model.Reason
import com.ownid.sdk.model.User
import com.ownid.sdk.model.loginId
import com.ownid.sdk.model.rawLoginId
import com.ownid.sdk.model.setContext
import com.ownid.sdk.namespace.DIResolvable
import com.ownid.sdk.namespace.OperationEntry
import com.ownid.sdk.namespace.OwnIdOperation
import com.ownid.sdk.ops.EmailVerificationOperation
import com.ownid.sdk.ops.EmailVerificationOperationFailure
import com.ownid.sdk.ops.LoginIdCollectOperation
import com.ownid.sdk.ops.LoginIdCollectOperationFailure
import com.ownid.sdk.ops.LoginOperation
import com.ownid.sdk.ops.LoginOperationFailure
import com.ownid.sdk.ops.OperationController
import com.ownid.sdk.ops.OperationResult
import com.ownid.sdk.ops.PasskeyAssertionOperation
import com.ownid.sdk.ops.PasskeyAssertionOperationFailure
import com.ownid.sdk.ops.PasskeyAttestationOperation
import com.ownid.sdk.ops.PasskeyAttestationOperationFailure
import com.ownid.sdk.ops.PasskeyEnrollOperation
import com.ownid.sdk.ops.PasskeyEnrollOperationFailure
import com.ownid.sdk.ops.PhoneVerificationOperation
import com.ownid.sdk.ops.PhoneVerificationOperationFailure
import com.ownid.sdk.ops.SignInWithGoogleOperation
import com.ownid.sdk.ops.SignInWithGoogleOperationFailure
import com.ownid.sdk.provider.OwnIdProvidersRegistrar
import com.ownid.sdk.provider.SessionCreate
import com.ownid.sdk.provider.SessionOutput
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class, InternalOwnIdAPI::class)
// Covers: FLOW-010, FLOW-020, FLOW-030, FLOW-040, FLOW-120, FLOW-130, FLOW-140, FLOW-150, FLOW-200, FLOW-ORCH-010, FLOW-ORCH-020, FLOW-210, FLOW-220
internal class BoostFlowFakeDrivenContractTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun explicitAbortAndResultCompleteOnlyTheOwnedStepBeforeSummary(): Unit = runTest(mainDispatcherRule.testDispatcher) {
        for (create in listOf(false, true)) {
            for (resultFirst in listOf(false, true)) {
                val events = mutableListOf<String>()
                val journey = RecordingUserJourney(events)
                val pending = CompletableDeferred<OperationResult<LoginResponse, LoginOperationFailure>>()
                val success = OperationResult.Success(LoginResponse.Success(AccessToken("result-token"), "session"))
                val loginEntry = recordingEntry<LoginOperation.Params?, LoginResponse, LoginOperationFailure>(
                    OperationType.SESSION_CREATION, success
                )
                val operations = RecordingOwnIdOperation(loginEntry = loginEntry, delayedLoginResults = mapOf(1 to pending))
                val context = BoostFlowContext { accessToken = accessTokenFor("Email:abort@example.test") }
                val controller: BoostFlow.Controller<out BoostFlow.Response, *> = if (create) {
                    createPasskeyFlow(operations, userJourney = journey).start(context)
                } else {
                    loginFlow(operations, userJourney = journey).start(context)
                }
                advanceUntilIdle()
                val operationId = loginEntry.startedOperationIds.single()
                val reason = Reason.UserClose("explicit abort")
                if (resultFirst) {
                    pending.complete(success)
                    advanceUntilIdle()
                }
                controller.abort(reason)
                controller.abort(reason)
                advanceUntilIdle()
                val first = controller.whenSettled()
                pending.complete(success)
                advanceUntilIdle()
                assertThat(controller.whenSettled()).isSameInstanceAs(first)
                val completion = journey.operationCompletions.single()
                assertThat(completion.first).isEqualTo(operationId)
                if (resultFirst) {
                    assertThat(first).isInstanceOf(FlowResult.Success::class.java)
                    assertThat(completion.second.first).isNull()
                    assertThat(loginEntry.abortReasons).isEmpty()
                    assertThat(events).containsAtLeast(
                        "journey:operation:complete:SessionCreation:success",
                        "journey:flow:complete:LoggedIn:immediate"
                    ).inOrder()
                } else {
                    assertThat(assertFlowCanceled(first)).isSameInstanceAs(reason)
                    assertThat(completion.second.first).isEqualTo(ErrorCode.Aborted)
                    assertThat(loginEntry.abortReasons).containsExactly(reason)
                    assertThat(events).containsAtLeast(
                        "journey:operation:complete:SessionCreation:${ErrorCode.Aborted}",
                        "journey:flow:complete:Error:${ErrorCode.Aborted}"
                    ).inOrder()
                }
                assertThat(journey.completedOutcomes).hasSize(1)
            }
        }
    }

    @Test
    fun delegatedLoginOwnsAbortedStepAndSummaryWithoutParentDuplication(): Unit = runTest(mainDispatcherRule.testDispatcher) {
        val events = mutableListOf<String>()
        val journey = RecordingUserJourney(events)
        val pending = CompletableDeferred<OperationResult<LoginResponse, LoginOperationFailure>>()
        val success = OperationResult.Success(LoginResponse.Success(AccessToken("result-token"), "session"))
        val operations = RecordingOwnIdOperation(
            loginEntry = recordingEntry(
                OperationType.SESSION_CREATION,
                OperationResult.Success(authRequiredResponse(OperationType.PASSKEY_AUTH)), success
            ),
            passkeyAuthEntry = recordingEntry(OperationType.PASSKEY_AUTH, OperationResult.Success(AccessToken("assertion-token"))),
            delayedLoginResults = mapOf(2 to pending)
        )
        val controller = createPasskeyFlow(operations, userJourney = journey).start(BoostFlowContext {
            accessToken = accessTokenFor("Email:delegated-abort@example.test")
        })
        advanceUntilIdle()
        assertThat(operations.loginEntry.startedOperationIds).hasSize(2)
        val discoveryId = operations.loginEntry.startedOperationIds.first()
        val childId = operations.loginEntry.startedOperationIds.last()
        val reason = Reason.UserClose("delegated abort")
        controller.abort(reason)
        controller.abort(reason)
        advanceUntilIdle()
        assertThat(assertFlowCanceled(controller.whenSettled())).isSameInstanceAs(reason)
        pending.complete(success)
        advanceUntilIdle()
        assertThat(journey.operationCompletions.filter { it.first == discoveryId }.single().second.first).isNull()
        assertThat(journey.operationCompletions.filter { it.first == childId }.single().second.first).isEqualTo(ErrorCode.Aborted)
        assertThat(journey.completedOutcomes).hasSize(1)
        assertThat(operations.loginEntry.abortReasons).containsExactly(reason)
        assertThat(events).containsAtLeast(
            "journey:operation:complete:SessionCreation:${ErrorCode.Aborted}",
            "journey:flow:complete:Error:${ErrorCode.Aborted}"
        ).inOrder()
    }

    @Test
    fun loginBlockedAccountCompletesDiscoveryWithoutStepErrorsAndPreservesFlowFailure(): Unit = runTest(mainDispatcherRule.testDispatcher) {
        for (reason in listOf("Blocked by account policy", null, "")) {
            val operations = collectingLoginOperations(LoginId("blocked@example.com", LoginId.Type.Email), LoginResponse.AccountBlocked(reason))
            val journey = RecordingUserJourney(mutableListOf())
            val controller = loginFlow(operations, userJourney = journey).start(BoostFlowContext {})
            advanceUntilIdle()

            val result = controller.whenSettled()
            assertThat(result).isInstanceOf(FlowResult.Failure::class.java)
            val failure = (result as FlowResult.Failure).failure
            assertThat(failure).isInstanceOf(BoostLoginFlowFailure.Account.Blocked::class.java)
            assertThat(failure.errorCode).isEqualTo(ErrorCode.UserBlocked)
            assertThat(failure.message).isEqualTo(reason ?: "Account is blocked")

            val completions = journey.operationCompletions.filter { it.first.type == OperationType.SESSION_CREATION }
            assertThat(completions).hasSize(1)
            val (operationId, diagnostics) = completions.single()
            assertThat(operationId).isEqualTo(operations.loginEntry.startedOperationIds.single())
            assertThat(diagnostics).isEqualTo(Triple(null, null, null))
            val outcome = journey.completedOutcomes.single() as UserJourney.Controller.Outcome.Error
            assertThat(outcome.errorCode).isEqualTo(ErrorCode.UserBlocked)
            assertThat(outcome.source).isEqualTo("BoostLoginFlowImpl.completeFlow.failure")
            assertThat(outcome.message).isEqualTo(reason ?: "Account is blocked")
        }
    }

    @Test
    fun loginMissingAccountCompletesDiscoveryWithoutStepErrorsAndPreservesFlowFailure(): Unit = runTest(mainDispatcherRule.testDispatcher) {
        for (reason in listOf("No matching account", null, "")) {
            val operations = collectingLoginOperations(LoginId("missing@example.com", LoginId.Type.Email), LoginResponse.AccountNotFound(reason))
            val journey = RecordingUserJourney(mutableListOf())
            val controller = loginFlow(operations, userJourney = journey).start(BoostFlowContext {})
            advanceUntilIdle()

            val result = controller.whenSettled()
            assertThat(result).isInstanceOf(FlowResult.Failure::class.java)
            val failure = (result as FlowResult.Failure).failure
            assertThat(failure).isInstanceOf(BoostLoginFlowFailure.Account.NotFound::class.java)
            assertThat(failure.errorCode).isEqualTo(ErrorCode.UserNotFound)
            assertThat(failure.message).isEqualTo(reason ?: "Account not found")

            val completions = journey.operationCompletions.filter { it.first.type == OperationType.SESSION_CREATION }
            assertThat(completions).hasSize(1)
            val (operationId, diagnostics) = completions.single()
            assertThat(operationId).isEqualTo(operations.loginEntry.startedOperationIds.single())
            assertThat(diagnostics).isEqualTo(Triple(null, null, null))
            val outcome = journey.completedOutcomes.single() as UserJourney.Controller.Outcome.Error
            assertThat(outcome.errorCode).isEqualTo(ErrorCode.UserNotFound)
            assertThat(outcome.source).isEqualTo("BoostLoginFlowImpl.completeFlow.failure")
            assertThat(outcome.message).isEqualTo(reason ?: "Account not found")
        }
    }

    @Test
    fun createPasskeyBlockedAccountCompletesDiscoveryWithoutStepErrorsAndPreservesFlowFailure(): Unit = runTest(mainDispatcherRule.testDispatcher) {
        for (reason in listOf("Blocked by account policy", null, "")) {
            val operations = collectingLoginOperations(LoginId("blocked@example.com", LoginId.Type.Email), LoginResponse.AccountBlocked(reason))
            val journey = RecordingUserJourney(mutableListOf())
            val controller = createPasskeyFlow(operations, userJourney = journey).start(BoostFlowContext {})
            advanceUntilIdle()

            val result = controller.whenSettled()
            assertThat(result).isInstanceOf(FlowResult.Failure::class.java)
            val failure = (result as FlowResult.Failure).failure
            assertThat(failure).isInstanceOf(BoostCreatePasskeyFlowFailure.Account.Blocked::class.java)
            assertThat(failure.errorCode).isEqualTo(ErrorCode.UserBlocked)
            assertThat(failure.message).isEqualTo(reason ?: "Account is blocked")

            val completions = journey.operationCompletions.filter { it.first.type == OperationType.SESSION_CREATION }
            assertThat(completions).hasSize(1)
            val (operationId, diagnostics) = completions.single()
            assertThat(operationId).isEqualTo(operations.loginEntry.startedOperationIds.single())
            assertThat(diagnostics).isEqualTo(Triple(null, null, null))
            val outcome = journey.completedOutcomes.single() as UserJourney.Controller.Outcome.Error
            assertThat(outcome.errorCode).isEqualTo(ErrorCode.UserBlocked)
            assertThat(outcome.source).isEqualTo("BoostCreatePasskeyFlowImpl.completeFlow.failure")
            assertThat(outcome.message).isEqualTo(reason ?: "Account is blocked")
        }
    }

    @Test
    fun loginUsesAccessTokenBeforeLoginIdHintsAndPreservesLoginWhenOptionalPasskeyEnrollUnavailable(): Unit = runTest(mainDispatcherRule.testDispatcher) {
        val inputAccessToken = accessTokenFor("UserName:user1")
        val loginAccessToken = AccessToken("login-access-token")
        val proofToken = ProofToken("proof-token")
        val operations = RecordingOwnIdOperation(
            loginEntry = recordingEntry(
                OperationType.SESSION_CREATION,
                OperationResult.Success(LoginResponse.Success(loginAccessToken, """{"raw":true}"""))
            ),
            passkeyEnrollEntry = recordingEntry(
                OperationType.PASSKEY_ENROLLMENT,
                OperationResult.Success(Unit),
                availability = Availability.Unavailable("passkey enroll unavailable")
            )
        )
        val flow = loginFlow(operations)

        val controller = flow.start(BoostFlowContext {
            accessToken = inputAccessToken
            loginId = LoginId("ignored@example.com", LoginId.Type.Email)
            this.proofToken = proofToken
        })
        advanceUntilIdle()

        val result = controller.whenSettled()

        assertThat(result).isInstanceOf(FlowResult.Success::class.java)
        val response = (result as FlowResult.Success).success
        assertThat(response.loginId).isEqualTo(LoginId("user1", LoginId.Type.UserName))
        assertThat(response.accessToken).isSameInstanceAs(loginAccessToken)
        assertThat(response.sessionPayload).isEqualTo("""{"raw":true}""")
        assertThat(operations.loginIdCollectEntry.startParams).isEmpty()
        assertThat(operations.loginEntry.startParams.single()?.accessToken).isSameInstanceAs(inputAccessToken)
        assertThat(operations.loginEntry.startParams.single()?.loginId).isNull()
        assertThat(operations.passkeyEnrollEntry.availabilityParams.single().proofToken).isSameInstanceAs(proofToken)
        assertThat(operations.passkeyEnrollEntry.startParams).isEmpty()
    }

    @Test
    fun loginIdCollectionReceivesFlowContextCurrentContextThenLastUserHints(): Unit = runTest(mainDispatcherRule.testDispatcher) {
        val explicitLoginId = LoginId("explicit@example.com", LoginId.Type.Email)
        val contextLoginId = LoginId("context@example.com", LoginId.Type.Email)
        val lastUserLoginId = LoginId("last@example.com", LoginId.Type.Email)

        val explicitOps = collectingLoginOperations(LoginId("collected-explicit@example.com", LoginId.Type.Email))
        assertThat(loginFlow(explicitOps).start(BoostFlowContext { loginId = explicitLoginId })).isNotNull()
        advanceUntilIdle()
        assertThat(explicitOps.scopedLoginHints.single()).isEqualTo(LoginHint.Typed(explicitLoginId))

        val contextOps = collectingLoginOperations(LoginId("collected-context@example.com", LoginId.Type.Email))
        assertThat(
            loginFlow(contextOps, context = contextWithLoginId(contextLoginId), userRepository = FakeUserRepository(User(lastUserLoginId, AuthMethod.Passkey)))
                .start(BoostFlowContext.Empty)
        ).isNotNull()
        advanceUntilIdle()
        assertThat(contextOps.scopedLoginHints.single()).isEqualTo(LoginHint.Typed(contextLoginId))

        val lastUserOps = collectingLoginOperations(LoginId("collected-last@example.com", LoginId.Type.Email))
        assertThat(
            loginFlow(
                lastUserOps,
                userRepository = FakeUserRepository(User(lastUserLoginId, AuthMethod.Passkey))
            ).start(BoostFlowContext.Empty)
        ).isNotNull()
        advanceUntilIdle()
        assertThat(lastUserOps.scopedLoginHints.single()).isEqualTo(LoginHint.Typed(lastUserLoginId))

        val ignoredLastUserRepository = FakeUserRepository(User(lastUserLoginId, AuthMethod.Passkey))
        val ignoredOps = collectingLoginOperations(LoginId("collected-ignored@example.com", LoginId.Type.Email))
        assertThat(loginFlow(ignoredOps, userRepository = ignoredLastUserRepository).start(BoostFlowContext { ignoreLastUser = true })).isNotNull()
        advanceUntilIdle()
        assertThat(ignoredOps.scopedLoginHints.single()).isEqualTo(LoginHint.None)
        assertThat(ignoredLastUserRepository.getLastUserCalls).isEqualTo(0)
    }

    @Test
    fun loginSessionProviderReceivesRawSessionPayloadAndControlsSessionOutput(): Unit = runTest(mainDispatcherRule.testDispatcher) {
        val loginId = LoginId("session@example.com", LoginId.Type.Email)
        val accessToken = AccessToken("session-access-token")
        val sessionCreate = RecordingSessionCreate(available = true, output = SessionOutput("host-session"))
        val operations = collectingLoginOperations(
            collectedLoginId = loginId,
            loginResponse = LoginResponse.Success(accessToken, "\"raw-session-payload\"")
        )
        val flow = loginFlow(operations, sessionCreate = sessionCreate)

        val controller = flow.start(BoostFlowContext.Empty)
        advanceUntilIdle()

        val result = controller.whenSettled()

        assertThat(result).isInstanceOf(FlowResult.Success::class.java)
        val response = (result as FlowResult.Success).success
        assertThat(response.sessionPayload).isEqualTo("\"raw-session-payload\"")
        assertThat(response.session).isEqualTo("host-session")
        assertThat(sessionCreate.params.single().loginId).isEqualTo(loginId)
        assertThat(sessionCreate.params.single().accessToken).isSameInstanceAs(accessToken)
        assertThat(sessionCreate.params.single().authMethod).isEqualTo(AuthMethod.Immediate)
        assertThat(sessionCreate.params.single().sessionPayload).isEqualTo("\"raw-session-payload\"")
    }

    @Test
    fun loginSessionProviderUnavailableKeepsSuccessfulLoginWithoutCallingCreate(): Unit = runTest(mainDispatcherRule.testDispatcher) {
        val sessionCreate = RecordingSessionCreate(available = false, output = SessionOutput("unused"))
        val flow = loginFlow(
            collectingLoginOperations(
                collectedLoginId = LoginId("unavailable-session@example.com", LoginId.Type.Email),
                loginResponse = LoginResponse.Success(AccessToken("access-token"), "payload")
            ),
            sessionCreate = sessionCreate
        )

        val controller = flow.start(BoostFlowContext.Empty)
        advanceUntilIdle()

        val result = controller.whenSettled()

        assertThat(result).isInstanceOf(FlowResult.Success::class.java)
        assertThat((result as FlowResult.Success).success.session).isNull()
        assertThat(sessionCreate.params).isEmpty()
        assertThat(sessionCreate.availabilityParams).hasSize(1)
    }

    @Test
    fun loginSessionCreateProviderFailureSettlesAsSessionCreationFailure(): Unit = runTest(mainDispatcherRule.testDispatcher) {
        val loginId = LoginId("session-failure@example.com", LoginId.Type.Email)
        val providerFailure = SessionCreateProviderFailure()
        val sessionCreate = RecordingSessionCreate(available = true, output = SessionOutput("unused"), failure = providerFailure)
        val flow = loginFlow(
            collectingLoginOperations(
                collectedLoginId = loginId,
                loginResponse = LoginResponse.Success(AccessToken("session-failure-access-token"), """{"session":"failure"}""")
            ),
            sessionCreate = sessionCreate
        )

        val controller = flow.start(BoostFlowContext.Empty)
        advanceUntilIdle()

        val failure = assertLoginSessionCreationFailure(controller.whenSettled())

        assertThat(failure.errorCode).isEqualTo(ErrorCode.IntegrationError)
        assertUnderlyingSessionCreateProviderFailure(failure.error, providerFailure)
        assertThat(sessionCreate.params).hasSize(1)
    }

    @Test
    fun loginSessionCreateCancellationSettlesAsSystemCancellation(): Unit = runTest(mainDispatcherRule.testDispatcher) {
        val loginId = LoginId("session-canceled@example.com", LoginId.Type.Email)
        val sessionCreate = RecordingSessionCreate(available = true, output = SessionOutput("unused"), failure = CancellationException())
        val flow = loginFlow(
            collectingLoginOperations(
                collectedLoginId = loginId,
                loginResponse = LoginResponse.Success(AccessToken("session-canceled-access-token"), """{"session":"canceled"}""")
            ),
            sessionCreate = sessionCreate
        )

        val controller = flow.start(BoostFlowContext.Empty)
        advanceUntilIdle()

        val reason = assertFlowCanceled(controller.whenSettled())

        assertThat(reason).isInstanceOf(Reason.SystemError::class.java)
        assertThat(sessionCreate.params).hasSize(1)
    }

    @Test
    fun createPasskeyExistingAccountLoginComposesSessionStorageAnalyticsWithoutCreatingPasskey(): Unit = runTest(mainDispatcherRule.testDispatcher) {
        val events = mutableListOf<String>()
        val loginId = LoginId("existing@example.com", LoginId.Type.Email)
        val accessToken = AccessToken("existing-access-token")
        val sessionPayload = """{"existing":true}"""
        val operations = collectingLoginOperations(
            collectedLoginId = loginId,
            loginResponse = LoginResponse.Success(accessToken, sessionPayload)
        )
        val repository = FakeUserRepository(events = events)
        val sessionCreate = RecordingSessionCreate(available = true, output = SessionOutput("host-existing-session"), events = events)
        val journey = RecordingUserJourney(events)
        val flow = createPasskeyFlow(
            operations,
            userRepository = repository,
            sessionCreate = sessionCreate,
            userJourney = journey,
            boostFlowLoginFactory = { error("Login fallback must not be created for an existing-account login") }
        )

        val controller = flow.start(BoostFlowContext {
            source = FlowInfo.Source.WIDGET_BUTTON
            traceParent = "00-00000000000000000000000000000007-0000000000000008-01"
        })
        advanceUntilIdle()

        val result = controller.whenSettled()

        assertThat(result).isInstanceOf(FlowResult.Success::class.java)
        val response = (result as FlowResult.Success).success
        assertThat(response).isInstanceOf(BoostFlow.Response.Login::class.java)
        response as BoostFlow.Response.Login
        assertThat(response.loginId).isEqualTo(loginId)
        assertThat(response.authMethod).isEqualTo(AuthMethod.Immediate)
        assertThat(response.accessToken).isSameInstanceAs(accessToken)
        assertThat(response.sessionPayload).isEqualTo(sessionPayload)
        assertThat(response.session).isEqualTo("host-existing-session")

        val sessionParams = sessionCreate.params.single()
        val availabilityParams = sessionCreate.availabilityParams.single()
        assertThat(availabilityParams).isInstanceOf(SessionCreate.Params::class.java)
        val availabilitySessionParams = availabilityParams as SessionCreate.Params
        assertThat(availabilitySessionParams.loginId).isEqualTo(loginId)
        assertThat(availabilitySessionParams.accessToken).isEqualTo(accessToken)
        assertThat(availabilitySessionParams.authMethod).isEqualTo(AuthMethod.Immediate)
        assertThat(availabilitySessionParams.sessionPayload).isEqualTo(sessionPayload)
        assertThat(sessionParams.loginId).isEqualTo(loginId)
        assertThat(sessionParams.accessToken).isSameInstanceAs(accessToken)
        assertThat(sessionParams.authMethod).isEqualTo(AuthMethod.Immediate)
        assertThat(sessionParams.sessionPayload).isEqualTo(sessionPayload)

        val storedUser = repository.setUsers.single()
        assertThat(storedUser.loginId).isEqualTo(loginId)
        assertThat(storedUser.authMethod).isEqualTo(AuthMethod.Immediate)
        assertThat(journey.completedOutcomes.single()).isInstanceOf(UserJourney.Controller.Outcome.LoggedIn::class.java)

        assertThat(operations.passkeyCreateEntry.startParams).isEmpty()
        assertThat(events).containsExactly(
            "journey:start:create-passkey:WIDGET_BUTTON:00-00000000000000000000000000000007-0000000000000008-01",
            "journey:operation:start:LoginIdCollect",
            "journey:setUserInfo:existing@example.com",
            "journey:operation:complete:LoginIdCollect:success",
            "journey:operation:start:SessionCreation",
            "journey:operation:complete:SessionCreation:success",
            "session:isAvailable:existing@example.com:immediate",
            "session:create:existing@example.com:immediate",
            "repository:set:existing@example.com:immediate",
            "journey:flow:complete:LoggedIn:immediate"
        ).inOrder()
    }

    @Test
    fun createPasskeyExistingAccountSessionCreateProviderFailureSettlesAsSessionCreationFailure(): Unit = runTest(mainDispatcherRule.testDispatcher) {
        val loginId = LoginId("existing-failure@example.com", LoginId.Type.Email)
        val providerFailure = SessionCreateProviderFailure()
        val operations = collectingLoginOperations(
            collectedLoginId = loginId,
            loginResponse = LoginResponse.Success(AccessToken("existing-failure-access-token"), "existing-failure-payload")
        )
        val sessionCreate = RecordingSessionCreate(available = true, output = SessionOutput("unused"), failure = providerFailure)
        val flow = createPasskeyFlow(operations, sessionCreate = sessionCreate)

        val controller = flow.start(BoostFlowContext.Empty)
        advanceUntilIdle()

        val failure = assertCreatePasskeySessionCreationFailure(controller.whenSettled())

        assertThat(failure.errorCode).isEqualTo(ErrorCode.IntegrationError)
        assertUnderlyingSessionCreateProviderFailure(failure.error, providerFailure)
        assertThat(sessionCreate.params).hasSize(1)
        assertThat(operations.passkeyCreateEntry.startParams).isEmpty()
    }

    @Test
    fun createPasskeyExistingAccountSessionCreateCancellationSettlesAsSystemCancellation(): Unit = runTest(mainDispatcherRule.testDispatcher) {
        val loginId = LoginId("existing-canceled@example.com", LoginId.Type.Email)
        val operations = collectingLoginOperations(
            collectedLoginId = loginId,
            loginResponse = LoginResponse.Success(AccessToken("existing-canceled-access-token"), "existing-canceled-payload")
        )
        val sessionCreate = RecordingSessionCreate(available = true, output = SessionOutput("unused"), failure = CancellationException())
        val flow = createPasskeyFlow(operations, sessionCreate = sessionCreate)

        val controller = flow.start(BoostFlowContext.Empty)
        advanceUntilIdle()

        val reason = assertFlowCanceled(controller.whenSettled())

        assertThat(reason).isInstanceOf(Reason.SystemError::class.java)
        assertThat(sessionCreate.params).hasSize(1)
        assertThat(operations.passkeyCreateEntry.startParams).isEmpty()
    }

    @Test
    fun createPasskeyDelegatesToLoginFlowWhenPasskeyAuthIsRequired(): Unit = runTest(mainDispatcherRule.testDispatcher) {
        val loginId = LoginId("returning@example.com", LoginId.Type.Email)
        val childAccessToken = AccessToken("child-access-token")
        val childLogin = BoostFlow.Response.Login(
            loginId = loginId,
            authMethod = AuthMethod.Passkey,
            accessToken = childAccessToken,
            sessionPayload = "child-session-payload",
            session = null
        )
        val childLoginFlow = RecordingBoostLoginFlow(FlowResult.Success(childLogin))
        val operations = collectingLoginOperations(
            collectedLoginId = loginId,
            loginResponse = LoginResponse.AuthRequired(
                AuthRequirements(
                    targetScore = 1,
                    operations = listOf(OperationRequirement(type = OperationType.PASSKEY_AUTH, score = 1))
                ),
                reason = "passkey auth required"
            )
        )
        var factoryCalls = 0
        val flow = createPasskeyFlow(operations, boostFlowLoginFactory = {
            factoryCalls += 1
            childLoginFlow
        })

        assertThat(factoryCalls).isEqualTo(0)

        val controller = flow.start(BoostFlowContext.Empty)
        advanceUntilIdle()

        val result = controller.whenSettled()

        assertThat(result).isInstanceOf(FlowResult.Success::class.java)
        val response = (result as FlowResult.Success).success
        assertThat(response).isInstanceOf(BoostFlow.Response.Login::class.java)
        response as BoostFlow.Response.Login
        assertThat(response.loginId).isEqualTo(loginId)
        assertThat(response.authMethod).isEqualTo(AuthMethod.Passkey)
        assertThat(response.accessToken).isSameInstanceAs(childAccessToken)
        assertThat(response.sessionPayload).isEqualTo("child-session-payload")
        assertThat(factoryCalls).isEqualTo(1)
        assertThat(childLoginFlow.startContexts).hasSize(1)
        assertThat(childLoginFlow.startContexts.single().loginId).isEqualTo(loginId)
        assertThat(childLoginFlow.startContexts.single().authRequiredResponse).isInstanceOf(LoginResponse.AuthRequired::class.java)
        assertThat(operations.passkeyCreateEntry.availabilityParams).isEmpty()
        assertThat(operations.passkeyCreateEntry.startParams).isEmpty()
    }

    @Test
    fun createPasskeyCancellationDuringLoginFallbackStartAbortsReturnedController(): Unit = runTest(mainDispatcherRule.testDispatcher) {
        val loginId = LoginId("canceled-fallback@example.com", LoginId.Type.Email)
        val operations = collectingLoginOperations(
            collectedLoginId = loginId,
            loginResponse = LoginResponse.AuthRequired(
                AuthRequirements(
                    targetScore = 1,
                    operations = listOf(OperationRequirement(type = OperationType.PASSKEY_AUTH, score = 1))
                ),
                reason = "passkey auth required"
            )
        )
        lateinit var flow: BoostCreatePasskeyFlowImpl
        val childLoginFlow = RecordingBoostLoginFlow(
            FlowResult.Canceled(Reason.SystemError("unused")),
            onStart = { flow.cancel() }
        )
        flow = createPasskeyFlow(operations, boostFlowLoginFactory = { childLoginFlow })

        val controller = flow.start(BoostFlowContext.Empty)
        advanceUntilIdle()

        val reason = assertFlowCanceled(controller.whenSettled())
        assertThat(reason).isInstanceOf(Reason.SystemError::class.java)
        assertThat(childLoginFlow.startContexts).hasSize(1)
        assertThat(childLoginFlow.abortReasons).containsExactly(reason)
        assertThat(childLoginFlow.settlementCalls).isEqualTo(0)
    }

    @Test
    fun createPasskeyReturnsProofOutcomeOrTerminalNoProofOutcomeFromAttestationPath(): Unit = runTest(mainDispatcherRule.testDispatcher) {
        val loginId = LoginId("new@example.com", LoginId.Type.Email)
        val successfulOps = collectingLoginOperations(
            collectedLoginId = loginId,
            loginResponse = LoginResponse.AccountNotFound("register")
        ).apply {
            passkeyCreateEntry.results.add(OperationResult.Success(AttestationResponse(ProofToken("proof-token"), """{"ownId":true}""")))
        }
        val successfulFlow = createPasskeyFlow(successfulOps)

        val successfulController = successfulFlow.start(BoostFlowContext.Empty)
        advanceUntilIdle()

        val successfulResult = successfulController.whenSettled()
        assertThat(successfulResult).isInstanceOf(FlowResult.Success::class.java)
        val successfulResponse = (successfulResult as FlowResult.Success).success as BoostFlow.Response.CreatePasskey
        assertThat(successfulResponse.loginId).isEqualTo(loginId)
        assertThat(successfulResponse.proofToken).isEqualTo(ProofToken("proof-token"))
        assertThat(successfulResponse.ownIdData).isEqualTo("""{"ownId":true}""")

        val fallbackOps = collectingLoginOperations(
            collectedLoginId = loginId,
            loginResponse = LoginResponse.AccountNotFound("register")
        ).apply {
            passkeyCreateEntry.results.add(
                OperationResult.Failure(
                    PasskeyAttestationOperationFailure.Integration.ProviderFailed(ErrorCode.IntegrationError, "local attestation failed")
                )
            )
        }
        val fallbackFlow = createPasskeyFlow(fallbackOps)

        val fallbackController = fallbackFlow.start(BoostFlowContext.Empty)
        advanceUntilIdle()

        val fallbackResult = fallbackController.whenSettled()
        assertThat(fallbackResult).isInstanceOf(FlowResult.Success::class.java)
        val fallbackResponse = (fallbackResult as FlowResult.Success).success as BoostFlow.Response.CreatePasskey
        assertThat(fallbackResponse.loginId).isEqualTo(loginId)
        assertThat(fallbackResponse.proofToken).isNull()
        assertThat(fallbackResponse.ownIdData).isNull()
    }

    @Test
    fun loginAuthRequiredPathComposesOperationAnalyticsProviderAndStorageInTerminalOrder(): Unit = runTest(mainDispatcherRule.testDispatcher) {
        val events = mutableListOf<String>()
        val loginId = LoginId("boost-login@example.com", LoginId.Type.Email)
        val passkeyAccessToken = AccessToken("passkey-access-token")
        val finalAccessToken = AccessToken("final-access-token")
        val proofToken = ProofToken("login-proof-token")
        val operations = RecordingOwnIdOperation(
            loginIdCollectEntry = recordingEntry(
                OperationType.LOGIN_ID_COLLECT,
                OperationResult.Success(loginId)
            ),
            loginEntry = recordingEntry(
                OperationType.SESSION_CREATION,
                OperationResult.Success(
                    LoginResponse.AuthRequired(
                        AuthRequirements(
                            targetScore = 1,
                            operations = listOf(OperationRequirement(type = OperationType.PASSKEY_AUTH, score = 1))
                        ),
                        reason = "passkey required"
                    )
                ),
                OperationResult.Success(LoginResponse.Success(finalAccessToken, "session-payload"))
            ),
            passkeyAuthEntry = recordingEntry(
                OperationType.PASSKEY_AUTH,
                OperationResult.Success(passkeyAccessToken)
            ),
            passkeyEnrollEntry = recordingEntry(
                OperationType.PASSKEY_ENROLLMENT,
                OperationResult.Success(Unit)
            )
        )
        val repository = FakeUserRepository(events = events)
        val sessionCreate = RecordingSessionCreate(available = true, output = SessionOutput("host-session"), events = events)
        val journey = RecordingUserJourney(events)
        val flow = loginFlow(
            operations,
            userRepository = repository,
            sessionCreate = sessionCreate,
            userJourney = journey
        )

        val controller = flow.start(BoostFlowContext {
            this.proofToken = proofToken
            source = FlowInfo.Source.DEFERRED
            traceParent = "00-00000000000000000000000000000001-0000000000000002-01"
        })
        advanceUntilIdle()

        val result = controller.whenSettled()

        assertThat(result).isInstanceOf(FlowResult.Success::class.java)
        val response = (result as FlowResult.Success).success
        assertThat(response.loginId).isEqualTo(loginId)
        assertThat(response.authMethod).isEqualTo(AuthMethod.Passkey)
        assertThat(response.accessToken).isSameInstanceAs(finalAccessToken)
        assertThat(response.session).isEqualTo("host-session")
        assertThat(operations.loginIdCollectEntry.startParams).hasSize(1)
        assertThat(operations.loginEntry.startParams).hasSize(2)
        assertThat(operations.loginEntry.startParams[0]?.loginId).isEqualTo(loginId)
        assertThat(operations.loginEntry.startParams[1]?.accessToken).isSameInstanceAs(passkeyAccessToken)
        assertThat(operations.passkeyAuthEntry.startParams.single()?.loginId).isEqualTo(loginId)
        val passkeyEnrollParams = operations.passkeyEnrollEntry.startParams.single()
        assertThat(passkeyEnrollParams.accessToken).isSameInstanceAs(finalAccessToken)
        assertThat(passkeyEnrollParams.proofToken).isSameInstanceAs(proofToken)
        assertThat(sessionCreate.params.single().authMethod).isEqualTo(AuthMethod.Passkey)
        val storedUser = repository.setUsers.single()
        assertThat(storedUser.loginId).isEqualTo(loginId)
        assertThat(storedUser.authMethod).isEqualTo(AuthMethod.Passkey)
        assertThat(journey.completedOutcomes.single()).isInstanceOf(UserJourney.Controller.Outcome.LoggedIn::class.java)
        assertThat(events).containsExactly(
            "journey:start:login:DEFERRED:00-00000000000000000000000000000001-0000000000000002-01",
            "journey:operation:start:LoginIdCollect",
            "journey:setUserInfo:boost-login@example.com",
            "journey:operation:complete:LoginIdCollect:success",
            "journey:operation:start:SessionCreation",
            "journey:operation:complete:SessionCreation:success",
            "journey:operation:start:PasskeyAuth",
            "journey:operation:complete:PasskeyAuth:success",
            "journey:operation:start:SessionCreation",
            "journey:operation:complete:SessionCreation:success",
            "journey:operation:start:PasskeyEnrollment",
            "journey:operation:complete:PasskeyEnrollment:success",
            "session:isAvailable:boost-login@example.com:passkey",
            "session:create:boost-login@example.com:passkey",
            "repository:set:boost-login@example.com:passkey",
            "journey:flow:complete:LoggedIn:passkey"
        ).inOrder()
    }

    @Test
    fun loginAuthRequiredVerificationUsesMatchingTypedChannelIds(): Unit = runTest(mainDispatcherRule.testDispatcher) {
        val emailLoginId = LoginId("boost-email@example.com", LoginId.Type.Email)
        val emailOperations = authRequiredVerificationOperations(
            verificationType = OperationType.EMAIL_VERIFICATION,
            verificationAccessToken = AccessToken("email-verification-access-token")
        )
        val emailController = loginFlow(emailOperations).start(
            BoostFlowContext {
                loginId = emailLoginId
                authRequiredResponse = authRequiredVerificationResponse(OperationType.EMAIL_VERIFICATION, "email-requirement-channel-id")
            }
        )
        advanceUntilIdle()

        assertThat(emailController.whenSettled()).isInstanceOf(FlowResult.Success::class.java)
        assertThat(emailOperations.emailVerificationEntry.startParams.single()?.loginId).isEqualTo(emailLoginId)
        assertThat(emailOperations.emailVerificationEntry.startParams.single()?.loginIdHintId).isEqualTo("email-requirement-channel-id")
        assertThat(emailOperations.phoneVerificationEntry.startParams).isEmpty()

        val phoneLoginId = LoginId("+15550101010", LoginId.Type.PhoneNumber)
        val phoneOperations = authRequiredVerificationOperations(
            verificationType = OperationType.PHONE_NUMBER_VERIFICATION,
            verificationAccessToken = AccessToken("phone-verification-access-token")
        )
        val phoneController = loginFlow(phoneOperations).start(
            BoostFlowContext {
                loginId = phoneLoginId
                authRequiredResponse = authRequiredVerificationResponse(OperationType.PHONE_NUMBER_VERIFICATION, "phone-requirement-channel-id")
            }
        )
        advanceUntilIdle()

        assertThat(phoneController.whenSettled()).isInstanceOf(FlowResult.Success::class.java)
        assertThat(phoneOperations.phoneVerificationEntry.startParams.single()?.loginId).isEqualTo(phoneLoginId)
        assertThat(phoneOperations.phoneVerificationEntry.startParams.single()?.loginIdHintId).isEqualTo("phone-requirement-channel-id")
        assertThat(phoneOperations.emailVerificationEntry.startParams).isEmpty()
    }

    @Test
    fun loginAuthRequiredVerificationForUsernameForwardsSelectedChannelIds(): Unit = runTest(mainDispatcherRule.testDispatcher) {
        val username = LoginId("boost-user", LoginId.Type.UserName)
        val emailOperations = authRequiredVerificationOperations(
            verificationType = OperationType.EMAIL_VERIFICATION,
            verificationAccessToken = AccessToken("username-email-verification-access-token")
        )
        val emailController = loginFlow(emailOperations).start(
            BoostFlowContext {
                loginId = username
                authRequiredResponse = authRequiredVerificationResponse(OperationType.EMAIL_VERIFICATION, "selected-email-channel-id")
            }
        )
        advanceUntilIdle()

        assertThat(emailController.whenSettled()).isInstanceOf(FlowResult.Success::class.java)
        assertThat(emailOperations.emailVerificationEntry.startParams.single()?.loginId).isEqualTo(username)
        assertThat(emailOperations.emailVerificationEntry.startParams.single()?.loginIdHintId).isEqualTo("selected-email-channel-id")

        val phoneOperations = authRequiredVerificationOperations(
            verificationType = OperationType.PHONE_NUMBER_VERIFICATION,
            verificationAccessToken = AccessToken("username-phone-verification-access-token")
        )
        val phoneController = loginFlow(phoneOperations).start(
            BoostFlowContext {
                loginId = username
                authRequiredResponse = authRequiredVerificationResponse(OperationType.PHONE_NUMBER_VERIFICATION, "selected-phone-channel-id")
            }
        )
        advanceUntilIdle()

        assertThat(phoneController.whenSettled()).isInstanceOf(FlowResult.Success::class.java)
        assertThat(phoneOperations.phoneVerificationEntry.startParams.single()?.loginId).isEqualTo(username)
        assertThat(phoneOperations.phoneVerificationEntry.startParams.single()?.loginIdHintId).isEqualTo("selected-phone-channel-id")
    }

    @Test
    fun loginAllowedAuthOperationsAbsentOrEmptyPreserveDefaultSelection(): Unit = runTest(mainDispatcherRule.testDispatcher) {
        val username = LoginId("boost-user", LoginId.Type.UserName)
        val requirements = authRequiredResponse(
            OperationType.ID_DOC_VERIFICATION,
            OperationType.EMAIL_VERIFICATION,
            OperationType.PHONE_NUMBER_VERIFICATION
        )
        val contexts = listOf(
            BoostFlowContext {
                loginId = username
                authRequiredResponse = requirements
            },
            BoostFlowContext {
                loginId = username
                authRequiredResponse = requirements
                allowedAuthOperations = emptySet()
            }
        )

        contexts.forEach { context ->
            val operations = authRequiredVerificationOperations(
                verificationType = OperationType.EMAIL_VERIFICATION,
                verificationAccessToken = AccessToken("default-email-verification-access-token")
            )

            val controller = loginFlow(operations).start(context)
            advanceUntilIdle()

            assertThat(controller.whenSettled()).isInstanceOf(FlowResult.Success::class.java)
            assertThat(operations.emailVerificationEntry.startParams).hasSize(1)
            assertThat(operations.phoneVerificationEntry.startParams).isEmpty()
        }
    }

    @Test
    fun loginAllowedAuthOperationsSelectOnlyPermittedUsernameVerification(): Unit = runTest(mainDispatcherRule.testDispatcher) {
        val username = LoginId("boost-user", LoginId.Type.UserName)
        val requirements = authRequiredResponse(
            OperationType.PASSKEY_AUTH,
            OperationType.EMAIL_VERIFICATION,
            OperationType.PHONE_NUMBER_VERIFICATION
        )
        val scenarios = listOf(
            BoostLoginAuthOperation.EMAIL_VERIFICATION to OperationType.EMAIL_VERIFICATION,
            BoostLoginAuthOperation.PHONE_NUMBER_VERIFICATION to OperationType.PHONE_NUMBER_VERIFICATION
        )

        scenarios.forEach { (allowedOperation, expectedOperationType) ->
            val operations = authRequiredVerificationOperations(
                verificationType = expectedOperationType,
                verificationAccessToken = AccessToken("${expectedOperationType.value}-verification-access-token")
            )
            val controller = loginFlow(operations).start(
                BoostFlowContext {
                    loginId = username
                    authRequiredResponse = requirements
                    allowedAuthOperations = setOf(allowedOperation)
                }
            )
            advanceUntilIdle()

            assertThat(controller.whenSettled()).isInstanceOf(FlowResult.Success::class.java)
            assertThat(operations.passkeyAuthEntry.startParams).isEmpty()
            assertThat(operations.passkeyCreateEntry.startParams).isEmpty()
            if (expectedOperationType == OperationType.EMAIL_VERIFICATION) {
                assertThat(operations.emailVerificationEntry.startParams).hasSize(1)
                assertThat(operations.phoneVerificationEntry.startParams).isEmpty()
            } else {
                assertThat(operations.phoneVerificationEntry.startParams).hasSize(1)
                assertThat(operations.emailVerificationEntry.startParams).isEmpty()
            }
        }
    }

    @Test
    fun loginPasskeyAllowancePermitsPasskeyAuthentication(): Unit = runTest(mainDispatcherRule.testDispatcher) {
        val loginId = LoginId("boost-passkey@example.com", LoginId.Type.Email)
        val operations = RecordingOwnIdOperation(
            loginEntry = recordingEntry(
                OperationType.SESSION_CREATION,
                OperationResult.Success(LoginResponse.Success(AccessToken("final-access-token"), "session-payload"))
            ),
            passkeyAuthEntry = recordingEntry(
                OperationType.PASSKEY_AUTH,
                OperationResult.Success(AccessToken("passkey-access-token"))
            )
        )
        val controller = loginFlow(operations).start(
            BoostFlowContext {
                this.loginId = loginId
                authRequiredResponse = authRequiredResponse(
                    OperationType.PASSKEY_AUTH,
                    OperationType.EMAIL_VERIFICATION
                )
                allowedAuthOperations = setOf(BoostLoginAuthOperation.PASSKEY)
            }
        )
        advanceUntilIdle()

        assertThat(controller.whenSettled()).isInstanceOf(FlowResult.Success::class.java)
        assertThat(operations.passkeyAuthEntry.startParams).hasSize(1)
        assertThat(operations.passkeyCreateEntry.startParams).isEmpty()
        assertThat(operations.emailVerificationEntry.startParams).isEmpty()
    }

    @Test
    fun loginPasskeyOnlyAllowanceDoesNotCreatePasskeyWithoutPermittedFallback(): Unit = runTest(mainDispatcherRule.testDispatcher) {
        val loginId = LoginId("boost-passkey-fallback@example.com", LoginId.Type.Email)
        val operations = RecordingOwnIdOperation(
            passkeyCreateEntry = recordingEntry(
                OperationType.PASSKEY_CREATION,
                OperationResult.Success(AttestationResponse(ProofToken("proof-token"), "own-id-data"))
            ),
            emailVerificationEntry = recordingEntry(
                OperationType.EMAIL_VERIFICATION,
                OperationResult.Success(AccessToken("email-verification-access-token"))
            )
        )
        val controller = loginFlow(operations).start(
            BoostFlowContext {
                this.loginId = loginId
                authRequiredResponse = authRequiredResponse(OperationType.EMAIL_VERIFICATION)
                allowedAuthOperations = setOf(BoostLoginAuthOperation.PASSKEY)
            }
        )
        advanceUntilIdle()

        val result = controller.whenSettled()
        assertThat(result).isInstanceOf(FlowResult.Failure::class.java)
        assertThat((result as FlowResult.Failure).failure).isInstanceOf(BoostLoginFlowFailure.InsufficientAuth::class.java)
        assertThat(operations.passkeyCreateEntry.availabilityParams).isEmpty()
        assertThat(operations.passkeyCreateEntry.startParams).isEmpty()
        assertThat(operations.passkeyAuthEntry.startParams).isEmpty()
        assertThat(operations.emailVerificationEntry.startParams).isEmpty()
    }

    @Test
    fun loginExhaustedPasskeyAuthenticationDoesNotCreatePasskeyWithoutFallback(): Unit = runTest(mainDispatcherRule.testDispatcher) {
        for (authAvailable in listOf(false, true)) {
            val operations = RecordingOwnIdOperation(
                passkeyAuthEntry = recordingEntry(
                    OperationType.PASSKEY_AUTH,
                    OperationResult.Failure(
                        PasskeyAssertionOperationFailure.Credential.NoApplicablePasskeys(ErrorCode.NoApplicablePasskeys, "No passkey")
                    ),
                    availability = if (authAvailable) Availability.Available() else Availability.Unavailable("Passkeys unavailable")
                ),
                passkeyCreateEntry = recordingEntry(
                    OperationType.PASSKEY_CREATION,
                    OperationResult.Success(AttestationResponse(ProofToken("proof-token"), "own-id-data"))
                )
            )
            val controller = loginFlow(operations).start(BoostFlowContext {
                loginId = LoginId("exhausted@example.com", LoginId.Type.Email)
                authRequiredResponse = authRequiredResponse(OperationType.PASSKEY_AUTH)
            })
            advanceUntilIdle()

            val result = controller.whenSettled()
            assertThat(result).isInstanceOf(FlowResult.Failure::class.java)
            assertThat((result as FlowResult.Failure).failure).isInstanceOf(BoostLoginFlowFailure.InsufficientAuth::class.java)
            assertThat(operations.passkeyAuthEntry.availabilityParams).hasSize(1)
            assertThat(operations.passkeyAuthEntry.startParams).hasSize(if (authAvailable) 1 else 0)
            assertThat(operations.passkeyCreateEntry.availabilityParams).isEmpty()
            assertThat(operations.passkeyCreateEntry.startParams).isEmpty()
            assertThat(operations.passkeyEnrollEntry.startParams).isEmpty()
        }
    }

    @Test
    fun loginPasskeyCreationPreservesPermittedVerificationAndEnrollment(): Unit = runTest(mainDispatcherRule.testDispatcher) {
        for (serverListsCreation in listOf(false, true)) {
            val proofToken = ProofToken("proof-token")
            val operations = RecordingOwnIdOperation(
                loginEntry = recordingEntry(
                    OperationType.SESSION_CREATION,
                    OperationResult.Success(LoginResponse.Success(AccessToken("final-access-token"), "session-payload"))
                ),
                passkeyCreateEntry = recordingEntry(
                    OperationType.PASSKEY_CREATION,
                    OperationResult.Success(AttestationResponse(proofToken, "own-id-data"))
                ),
                passkeyEnrollEntry = recordingEntry(OperationType.PASSKEY_ENROLLMENT, OperationResult.Success(Unit)),
                emailVerificationEntry = recordingEntry(
                    OperationType.EMAIL_VERIFICATION,
                    OperationResult.Success(AccessToken("email-access-token"))
                )
            )
            val controller = loginFlow(operations).start(BoostFlowContext {
                loginId = LoginId("permitted-fallback@example.com", LoginId.Type.Email)
                authRequiredResponse = if (serverListsCreation) {
                    authRequiredResponse(OperationType.PASSKEY_CREATION, OperationType.EMAIL_VERIFICATION)
                } else {
                    authRequiredResponse(OperationType.EMAIL_VERIFICATION)
                }
                allowedAuthOperations = setOf(BoostLoginAuthOperation.PASSKEY, BoostLoginAuthOperation.EMAIL_VERIFICATION)
            })
            advanceUntilIdle()

            val result = controller.whenSettled()
            assertThat(result).isInstanceOf(FlowResult.Success::class.java)
            assertThat((result as FlowResult.Success).success.authMethod).isEqualTo(AuthMethod.Otp)
            assertThat(operations.passkeyCreateEntry.startParams).hasSize(1)
            assertThat(operations.emailVerificationEntry.startParams).hasSize(1)
            assertThat(operations.passkeyAuthEntry.startParams).isEmpty()
            assertThat(operations.passkeyEnrollEntry.startParams.single().proofToken).isSameInstanceAs(proofToken)
        }
    }

    @Test
    fun loginWithoutMatchingAllowedAuthOperationSettlesInsufficientAuthWithoutStartingExcludedOperations(): Unit =
        runTest(mainDispatcherRule.testDispatcher) {
            val operations = RecordingOwnIdOperation(
                emailVerificationEntry = recordingEntry(
                    OperationType.EMAIL_VERIFICATION,
                    OperationResult.Success(AccessToken("email-verification-access-token"))
                ),
                phoneVerificationEntry = recordingEntry(
                    OperationType.PHONE_NUMBER_VERIFICATION,
                    OperationResult.Success(AccessToken("phone-verification-access-token"))
                )
            )
            val controller = loginFlow(operations).start(
                BoostFlowContext {
                    loginId = LoginId("boost-user", LoginId.Type.UserName)
                    authRequiredResponse = authRequiredResponse(OperationType.PHONE_NUMBER_VERIFICATION)
                    allowedAuthOperations = setOf(BoostLoginAuthOperation.EMAIL_VERIFICATION)
                }
            )
            advanceUntilIdle()

            val result = controller.whenSettled()
            assertThat(result).isInstanceOf(FlowResult.Failure::class.java)
            assertThat((result as FlowResult.Failure).failure).isInstanceOf(BoostLoginFlowFailure.InsufficientAuth::class.java)
            assertThat(operations.passkeyCreateEntry.startParams).isEmpty()
            assertThat(operations.passkeyAuthEntry.startParams).isEmpty()
            assertThat(operations.emailVerificationEntry.startParams).isEmpty()
            assertThat(operations.phoneVerificationEntry.startParams).isEmpty()
        }

    @Test
    fun loginWithOnlyUnsupportedRequirementSettlesInsufficientAuthWhenPasskeysAreNotAllowed(): Unit =
        runTest(mainDispatcherRule.testDispatcher) {
            val operations = RecordingOwnIdOperation()
            val controller = loginFlow(operations).start(
                BoostFlowContext {
                    loginId = LoginId("boost-user", LoginId.Type.UserName)
                    authRequiredResponse = authRequiredResponse(OperationType.ID_DOC_VERIFICATION)
                    allowedAuthOperations = setOf(BoostLoginAuthOperation.EMAIL_VERIFICATION)
                }
            )
            advanceUntilIdle()

            val result = controller.whenSettled()
            assertThat(result).isInstanceOf(FlowResult.Failure::class.java)
            assertThat((result as FlowResult.Failure).failure).isInstanceOf(BoostLoginFlowFailure.InsufficientAuth::class.java)
            assertThat(operations.passkeyCreateEntry.startParams).isEmpty()
            assertThat(operations.passkeyAuthEntry.startParams).isEmpty()
            assertThat(operations.emailVerificationEntry.startParams).isEmpty()
            assertThat(operations.phoneVerificationEntry.startParams).isEmpty()
        }

    @Test
    fun createPasskeyRegistrationPathComposesAttestationAnalyticsAndProofStorageInTerminalOrder(): Unit = runTest(mainDispatcherRule.testDispatcher) {
        val events = mutableListOf<String>()
        val loginId = LoginId("new-boost@example.com", LoginId.Type.Email)
        val proofToken = ProofToken("created-proof-token")
        val operations = RecordingOwnIdOperation(
            loginIdCollectEntry = recordingEntry(
                OperationType.LOGIN_ID_COLLECT,
                OperationResult.Success(loginId)
            ),
            loginEntry = recordingEntry(
                OperationType.SESSION_CREATION,
                OperationResult.Success(LoginResponse.AccountNotFound("register"))
            ),
            passkeyCreateEntry = recordingEntry(
                OperationType.PASSKEY_CREATION,
                OperationResult.Success(AttestationResponse(proofToken, """{"ownId":true}"""))
            )
        )
        val repository = FakeUserRepository(events = events)
        val sessionCreate = RecordingSessionCreate(available = true, output = SessionOutput("unused"), events = events)
        val journey = RecordingUserJourney(events)
        val flow = createPasskeyFlow(
            operations,
            userRepository = repository,
            sessionCreate = sessionCreate,
            userJourney = journey
        )

        val controller = flow.start(BoostFlowContext {
            source = FlowInfo.Source.WIDGET_BUTTON
            traceParent = "00-00000000000000000000000000000003-0000000000000004-01"
        })
        advanceUntilIdle()

        val result = controller.whenSettled()

        assertThat(result).isInstanceOf(FlowResult.Success::class.java)
        val response = (result as FlowResult.Success).success as BoostFlow.Response.CreatePasskey
        assertThat(response.loginId).isEqualTo(loginId)
        assertThat(response.proofToken).isSameInstanceAs(proofToken)
        assertThat(response.ownIdData).isEqualTo("""{"ownId":true}""")
        assertThat(operations.passkeyCreateEntry.startParams.single()?.loginId).isEqualTo(loginId)
        assertThat(sessionCreate.params).isEmpty()
        val storedUser = repository.setUsers.single()
        assertThat(storedUser.loginId).isEqualTo(loginId)
        assertThat(storedUser.authMethod).isEqualTo(AuthMethod.Passkey)
        assertThat(journey.completedOutcomes.single()).isInstanceOf(UserJourney.Controller.Outcome.Registered::class.java)
        assertThat(events).containsExactly(
            "journey:start:create-passkey:WIDGET_BUTTON:00-00000000000000000000000000000003-0000000000000004-01",
            "journey:operation:start:LoginIdCollect",
            "journey:setUserInfo:new-boost@example.com",
            "journey:operation:complete:LoginIdCollect:success",
            "journey:operation:start:SessionCreation",
            "journey:operation:complete:SessionCreation:success",
            "journey:operation:start:PasskeyCreation",
            "journey:operation:complete:PasskeyCreation:success",
            "repository:set:new-boost@example.com:passkey",
            "journey:flow:complete:Registered:null"
        ).inOrder()
    }

    @Test
    fun createPasskeyDelegatedLoginSwitchesAnalyticsAndLeavesTerminalSideEffectsToChildFlow(): Unit = runTest(mainDispatcherRule.testDispatcher) {
        val events = mutableListOf<String>()
        val loginId = LoginId("returning-child@example.com", LoginId.Type.Email)
        val childLogin = BoostFlow.Response.Login(
            loginId = loginId,
            authMethod = AuthMethod.Passkey,
            accessToken = AccessToken("child-access-token"),
            sessionPayload = "child-session-payload",
            session = null
        )
        val childLoginFlow = RecordingBoostLoginFlow(FlowResult.Success(childLogin), events)
        val operations = RecordingOwnIdOperation(
            loginIdCollectEntry = recordingEntry(
                OperationType.LOGIN_ID_COLLECT,
                OperationResult.Success(loginId)
            ),
            loginEntry = recordingEntry(
                OperationType.SESSION_CREATION,
                OperationResult.Success(
                    LoginResponse.AuthRequired(
                        AuthRequirements(
                            targetScore = 1,
                            operations = listOf(OperationRequirement(type = OperationType.PASSKEY_AUTH, score = 1))
                        ),
                        reason = "passkey auth required"
                    )
                )
            )
        )
        val repository = FakeUserRepository(events = events)
        val sessionCreate = RecordingSessionCreate(available = true, output = SessionOutput("unused"), events = events)
        val journey = RecordingUserJourney(events)
        val flow = createPasskeyFlow(
            operations,
            userRepository = repository,
            sessionCreate = sessionCreate,
            userJourney = journey,
            boostFlowLoginFactory = { childLoginFlow }
        )

        val controller = flow.start(BoostFlowContext {
            source = FlowInfo.Source.RECOVERY_PROMPT
            traceParent = "00-00000000000000000000000000000005-0000000000000006-01"
        })
        advanceUntilIdle()

        val result = controller.whenSettled()

        assertThat(result).isInstanceOf(FlowResult.Success::class.java)
        assertThat((result as FlowResult.Success).success).isSameInstanceAs(childLogin)
        val childStartContext = childLoginFlow.startContexts.single()
        assertThat(childStartContext.loginId).isEqualTo(loginId)
        assertThat(childStartContext.authRequiredResponse).isInstanceOf(LoginResponse.AuthRequired::class.java)
        assertThat(operations.passkeyCreateEntry.startParams).isEmpty()
        assertThat(sessionCreate.params).isEmpty()
        assertThat(repository.setUsers).isEmpty()
        assertThat(journey.completedOutcomes).isEmpty()
        assertThat(events).containsExactly(
            "journey:start:create-passkey:RECOVERY_PROMPT:00-00000000000000000000000000000005-0000000000000006-01",
            "journey:operation:start:LoginIdCollect",
            "journey:setUserInfo:returning-child@example.com",
            "journey:operation:complete:LoginIdCollect:success",
            "journey:operation:start:SessionCreation",
            "journey:operation:complete:SessionCreation:success",
            "journey:switchToFlow:login:RECOVERY_PROMPT",
            "childLogin:start:returning-child@example.com"
        ).inOrder()
    }

    @Test
    fun factoriesPreserveOnlyDisplayNameInPasskeyOperationContext(): Unit = runTest(mainDispatcherRule.testDispatcher) {
        for (displayName in listOf(null, "Ada Lovelace")) {
            for (mode in 0..2) {
                val token = accessTokenFor("Email:factory@example.test")
                val contexts = mutableListOf<Context?>()
                val container = DIContainerImpl("boost-factory")
                container.register(InstanceName("boost-factory"))
                container.register<LoginIdValidator>(AcceptingLoginIdValidator)
                container.register<Serialization>(JsonSerialization)
                container.register<Job>(Job())
                container.register<DispatcherProvider>(object : DispatcherProvider {
                    override fun defaultDispatcher(): CoroutineDispatcher = mainDispatcherRule.testDispatcher
                    override fun ioDispatcher(): CoroutineDispatcher = mainDispatcherRule.testDispatcher
                })
                container.setContext {
                    authz = Authz.fromToken(token)
                    accountDisplayName = displayName
                }
                val loginResult = when (mode) {
                    1 -> LoginResponse.AccountNotFound("register")
                    2 -> authRequiredResponse(OperationType.PASSKEY_AUTH, OperationType.EMAIL_VERIFICATION)
                    else -> authRequiredResponse(OperationType.EMAIL_VERIFICATION)
                }
                val loginEntry = recordingEntry<LoginOperation.Params?, LoginResponse, LoginOperationFailure>(
                    OperationType.SESSION_CREATION, OperationResult.Success(loginResult)
                )
                container.registerFactory<LoginOperation> {
                    object : LoginOperation {
                        override val operationType = OperationType.SESSION_CREATION
                        override suspend fun availability(params: Capability.Params?): Availability = Availability.Available()
                        override fun start(params: LoginOperation.Params?) = loginEntry.start(params)
                    }
                }
                val passkeyEntry = recordingEntry<PasskeyAttestationOperation.Params?, AttestationResponse, PasskeyAttestationOperationFailure>(
                    OperationType.PASSKEY_CREATION, OperationResult.Canceled(Reason.UserClose())
                )
                container.registerFactory<PasskeyAttestationOperation> {
                    val scopedContext = getOrNull<Context>()
                    object : PasskeyAttestationOperation {
                        override val operationType = OperationType.PASSKEY_CREATION
                        override suspend fun availability(params: Capability.Params?): Availability {
                            contexts += scopedContext
                            return Availability.Available()
                        }
                        override fun start(params: PasskeyAttestationOperation.Params?) = passkeyEntry.start(params)
                    }
                }
                if (mode == 0) {
                    BoostLoginFlowImpl.create(container).start(BoostFlowContext {})
                } else {
                    BoostCreatePasskeyFlowImpl.create(container).start(BoostFlowContext {})
                }
                advanceUntilIdle()
                assertThat(contexts).isNotEmpty()
                contexts.forEach { context ->
                    assertThat(context?.accountDisplayName).isEqualTo(displayName)
                    assertThat(context?.authz).isNull()
                }
                assertThat(container.getOrNull<Context>()?.accountDisplayName).isEqualTo(displayName)
                assertThat(container.getOrNull<Context>()?.authz).isNotNull()
            }
        }
    }

    @Test
    fun eligiblePersistenceFinishesBeforeSuccessAndJourneySummary(): Unit = runTest(mainDispatcherRule.testDispatcher) {
        for (mode in 0..2) {
            val writeStarted = CompletableDeferred<Unit>()
            val releaseWrite = CompletableDeferred<Unit>()
            val repository = FakeUserRepository(onSet = {
                writeStarted.complete(Unit)
                releaseWrite.await()
            })
            val journey = RecordingUserJourney(mutableListOf())
            val controller = persistenceController(mode, repository, journey)
            val result = async { controller.whenSettled() }
            advanceUntilIdle()
            assertThat(writeStarted.isCompleted).isTrue()
            assertThat(result.isCompleted).isFalse()
            assertThat(journey.completedOutcomes).isEmpty()
            releaseWrite.complete(Unit)
            advanceUntilIdle()
            assertThat(result.await()).isInstanceOf(FlowResult.Success::class.java)
            assertThat(repository.setUsers).hasSize(1)
            assertThat(journey.completedOutcomes).hasSize(1)
        }
    }

    @Test
    fun persistenceFailureRemainsNonfatalButRepositoryCancellationSettlesOnce(): Unit = runTest(mainDispatcherRule.testDispatcher) {
        for (mode in 0..2) {
            for (failure in listOf(IllegalStateException("storage failed"), CancellationException("storage canceled"))) {
                val repository = FakeUserRepository(onSet = { throw failure })
                val journey = RecordingUserJourney(mutableListOf())
                val controller = persistenceController(mode, repository, journey)
                advanceUntilIdle()
                val first = controller.whenSettled()
                if (failure is CancellationException) {
                    assertThat(first).isInstanceOf(FlowResult.Canceled::class.java)
                    val outcome = journey.completedOutcomes.single() as UserJourney.Controller.Outcome.Error
                    assertThat(outcome.errorCode).isEqualTo(ErrorCode.Aborted)
                } else {
                    assertThat(first).isInstanceOf(FlowResult.Success::class.java)
                }
                assertThat(repository.setUsers).hasSize(1)
                assertThat(journey.completedOutcomes).hasSize(1)
                assertThat(controller.whenSettled()).isSameInstanceAs(first)
            }
        }
    }

    @Test
    fun instanceShutdownDuringPersistenceSettlesCancellationOnce(): Unit = runTest(mainDispatcherRule.testDispatcher) {
        for (mode in 0..2) {
            val instanceJob = Job()
            val repository = FakeUserRepository(onSet = { CompletableDeferred<Unit>().await() })
            val journey = RecordingUserJourney(mutableListOf())
            val controller = persistenceController(mode, repository, journey, instanceJob)
            advanceUntilIdle()
            assertThat(repository.setUsers).hasSize(1)
            instanceJob.cancel()
            advanceUntilIdle()
            val first = controller.whenSettled()
            assertThat(first).isInstanceOf(FlowResult.Canceled::class.java)
            assertThat(controller.whenSettled()).isSameInstanceAs(first)
            assertThat(journey.completedOutcomes).hasSize(1)
        }
    }

    private fun TestScope.persistenceController(
        mode: Int, repository: UserRepository, journey: UserJourney, instanceJob: Job = Job()
    ): BoostFlow.Controller<out BoostFlow.Response, *> {
        val loginId = LoginId("persistence@example.test", LoginId.Type.Email)
        val operations = collectingLoginOperations(
            loginId,
            if (mode == 2) LoginResponse.AccountNotFound("register") else LoginResponse.Success(AccessToken("access-token"), "session")
        ).let { ops ->
            if (mode != 2) ops else RecordingOwnIdOperation(
                loginIdCollectEntry = ops.loginIdCollectEntry,
                loginEntry = ops.loginEntry,
                passkeyCreateEntry = recordingEntry(
                    OperationType.PASSKEY_CREATION, OperationResult.Success(AttestationResponse(ProofToken("proof-token"), "{}"))
                )
            )
        }
        return if (mode == 0) {
            loginFlow(operations, userRepository = repository, userJourney = journey, instanceJob = instanceJob).start(BoostFlowContext {})
        } else {
            createPasskeyFlow(operations, userRepository = repository, userJourney = journey, instanceJob = instanceJob).start(BoostFlowContext {})
        }
    }

    private fun TestScope.loginFlow(
        operations: RecordingOwnIdOperation,
        userRepository: UserRepository? = FakeUserRepository(),
        sessionCreate: SessionCreate? = null,
        context: Context? = null,
        userJourney: UserJourney? = null,
        instanceJob: Job = Job()
    ): BoostLoginFlowImpl =
        BoostLoginFlowImpl(
            userRepository = userRepository,
            ownIdOperation = operations,
            userJourney = userJourney,
            sessionCreate = sessionCreate,
            loginIdValidator = AcceptingLoginIdValidator,
            serialization = JsonSerialization,
            defaultDispatcher = StandardTestDispatcher(testScheduler),
            instanceJob = instanceJob,
            context = context,
            logger = null
        )

    private fun TestScope.createPasskeyFlow(
        operations: RecordingOwnIdOperation,
        userRepository: UserRepository? = FakeUserRepository(),
        sessionCreate: SessionCreate? = null,
        context: Context? = null,
        userJourney: UserJourney? = null,
        instanceJob: Job = Job(),
        boostFlowLoginFactory: () -> BoostFlow.Login = {
            loginFlow(
                operations,
                userRepository = userRepository,
                sessionCreate = sessionCreate,
                context = context,
                userJourney = userJourney
            )
        }
    ): BoostCreatePasskeyFlowImpl =
        BoostCreatePasskeyFlowImpl(
            userRepository = userRepository,
            ownIdOperation = operations,
            boostFlowLoginFactory = boostFlowLoginFactory,
            loginIdValidator = AcceptingLoginIdValidator,
            serialization = JsonSerialization,
            userJourney = userJourney,
            sessionCreate = sessionCreate,
            defaultDispatcher = StandardTestDispatcher(testScheduler),
            instanceJob = instanceJob,
            context = context,
            logger = null
        )

    private fun collectingLoginOperations(
        collectedLoginId: LoginId,
        loginResponse: LoginResponse = LoginResponse.Success(AccessToken("collected-access-token"), "session-payload")
    ): RecordingOwnIdOperation =
        RecordingOwnIdOperation(
            loginIdCollectEntry = recordingEntry(
                OperationType.LOGIN_ID_COLLECT,
                OperationResult.Success(collectedLoginId)
            ),
            loginEntry = recordingEntry(
                OperationType.SESSION_CREATION,
                OperationResult.Success(loginResponse)
            )
        )

    private fun authRequiredVerificationOperations(
        verificationType: OperationType,
        verificationAccessToken: AccessToken
    ): RecordingOwnIdOperation =
        RecordingOwnIdOperation(
            loginEntry = recordingEntry(
                OperationType.SESSION_CREATION,
                OperationResult.Success(LoginResponse.Success(AccessToken("${verificationType.value}-final-access-token"), "session-payload"))
            ),
            passkeyCreateEntry = unavailableEntry(OperationType.PASSKEY_CREATION),
            emailVerificationEntry = recordingEntry(
                OperationType.EMAIL_VERIFICATION,
                OperationResult.Success(verificationAccessToken)
            ),
            phoneVerificationEntry = recordingEntry(
                OperationType.PHONE_NUMBER_VERIFICATION,
                OperationResult.Success(verificationAccessToken)
            )
        )

    private fun authRequiredVerificationResponse(verificationType: OperationType, channelId: String): LoginResponse.AuthRequired =
        LoginResponse.AuthRequired(
            AuthRequirements(
                targetScore = 1,
                operations = listOf(
                    OperationRequirement(
                        type = verificationType,
                        score = 1,
                        channels = listOf(OperationChannel(channel = "$channelId-display", id = channelId))
                    )
                )
            ),
            reason = "${verificationType.value} required"
        )

    private fun authRequiredResponse(vararg operationTypes: OperationType): LoginResponse.AuthRequired =
        LoginResponse.AuthRequired(
            AuthRequirements(
                targetScore = 1,
                operations = operationTypes.map { operationType ->
                    OperationRequirement(
                        type = operationType,
                        score = 1,
                        channels = when (operationType) {
                            OperationType.EMAIL_VERIFICATION,
                            OperationType.PHONE_NUMBER_VERIFICATION -> listOf(
                                OperationChannel(
                                    channel = "${operationType.value}-channel",
                                    id = "${operationType.value}-channel-id"
                                )
                            )

                            else -> null
                        }
                    )
                }
            ),
            reason = "Authentication required"
        )

    private fun contextWithLoginId(loginId: LoginId): Context =
        Context.Builder().apply { authz = Authz.start(loginId) }.build("test-context")

    private fun assertLoginSessionCreationFailure(
        result: FlowResult<BoostFlow.Response.Login, BoostLoginFlowFailure>
    ): BoostLoginFlowFailure.SessionCreationFailed {
        assertThat(result).isInstanceOf(FlowResult.Failure::class.java)
        val failure = (result as FlowResult.Failure).failure
        assertThat(failure).isInstanceOf(BoostLoginFlowFailure.SessionCreationFailed::class.java)
        return failure as BoostLoginFlowFailure.SessionCreationFailed
    }

    private fun assertCreatePasskeySessionCreationFailure(
        result: FlowResult<BoostFlow.Response, BoostCreatePasskeyFlowFailure>
    ): BoostCreatePasskeyFlowFailure.SessionCreationFailed {
        assertThat(result).isInstanceOf(FlowResult.Failure::class.java)
        val failure = (result as FlowResult.Failure).failure
        assertThat(failure).isInstanceOf(BoostCreatePasskeyFlowFailure.SessionCreationFailed::class.java)
        return failure as BoostCreatePasskeyFlowFailure.SessionCreationFailed
    }

    private fun assertFlowCanceled(result: FlowResult<*, *>): Reason {
        assertThat(result).isInstanceOf(FlowResult.Canceled::class.java)
        return (result as FlowResult.Canceled).reason
    }

    private fun assertUnderlyingSessionCreateProviderFailure(actual: Throwable?, expected: SessionCreateProviderFailure) {
        assertThat(actual).isInstanceOf(SessionCreateProviderFailure::class.java)
        assertThat(actual === expected || actual?.cause === expected).isTrue()
    }

    private class SessionCreateProviderFailure : RuntimeException()

    private class FakeUserRepository(
        private val initialUser: User? = null,
        private val events: MutableList<String>? = null,
        private val onSet: suspend () -> Unit = {}
    ) : UserRepository {
        var getLastUserCalls = 0
        val setUsers = mutableListOf<User>()

        override suspend fun getLastUser(): User? {
            getLastUserCalls += 1
            return initialUser
        }

        override suspend fun setLastUser(user: User) {
            setUsers += user
            events?.add("repository:set:${user.loginId.id}:${user.authMethod}")
            onSet()
        }

        override suspend fun clearLastUser() = Unit
    }

    private class RecordingBoostLoginFlow(
        private val result: FlowResult<BoostFlow.Response.Login, BoostLoginFlowFailure>,
        private val events: MutableList<String>? = null,
        private val onStart: (() -> Unit)? = null
    ) : BoostFlow.Login {
        val startContexts = mutableListOf<BoostFlowContext>()
        val abortReasons = mutableListOf<Reason>()
        var settlementCalls = 0

        override fun start(context: BoostFlowContext): BoostFlow.Controller<BoostFlow.Response.Login, BoostLoginFlowFailure> {
            startContexts += context
            events?.add("childLogin:start:${context.loginId?.id}")
            onStart?.invoke()
            return object : BoostFlow.Controller<BoostFlow.Response.Login, BoostLoginFlowFailure> {
                override suspend fun whenSettled(): FlowResult<BoostFlow.Response.Login, BoostLoginFlowFailure> {
                    settlementCalls += 1
                    return result
                }

                override fun abort(reason: Reason) {
                    abortReasons += reason
                }
            }
        }
    }

    private class RecordingSessionCreate(
        private val available: Boolean,
        private val output: SessionOutput,
        private val failure: Throwable? = null,
        private val events: MutableList<String>? = null
    ) : SessionCreate {
        val availabilityParams = mutableListOf<Capability.Params?>()
        val params = mutableListOf<SessionCreate.Params>()

        override suspend fun isAvailable(params: Capability.Params?): Boolean {
            availabilityParams += params
            (params as? SessionCreate.Params)?.let {
                events?.add("session:isAvailable:${it.loginId.id}:${it.authMethod}")
            }
            return available
        }

        override suspend fun create(params: SessionCreate.Params): Result<SessionOutput> {
            this.params += params
            events?.add("session:create:${params.loginId.id}:${params.authMethod}")
            return failure?.let { Result.failure(it) } ?: Result.success(output)
        }
    }

    private class RecordingUserJourney(
        private val events: MutableList<String>
    ) : UserJourney {
        val completedOutcomes = mutableListOf<UserJourney.Controller.Outcome>()
        val operationCompletions = mutableListOf<Pair<OperationID, Triple<ErrorCode?, String?, String?>>>()

        override suspend fun startFlow(name: String?, source: FlowInfo.Source, traceParent: String?): UserJourney.Controller {
            events += "journey:start:$name:$source:$traceParent"
            return Controller()
        }

        private inner class Controller : UserJourney.Controller {
            override suspend fun setUserInfo(loginId: LoginId) {
                events += "journey:setUserInfo:${loginId.id}"
            }

            override suspend fun setReferer(referer: String) {
                events += "journey:setReferer:$referer"
            }

            override suspend fun switchToFlow(flowId: String?, name: String?, source: FlowInfo.Source) {
                events += "journey:switchToFlow:$name:$source"
            }

            override suspend fun startOperation(operationId: OperationID) {
                events += "journey:operation:start:${operationId.type}"
            }

            override suspend fun addOperationClick(operationId: OperationID) {
                events += "journey:operation:click:${operationId.type}"
            }

            override suspend fun completeOperation(operationId: OperationID, errorCode: ErrorCode?, source: String?, message: String?) {
                operationCompletions += operationId to Triple(errorCode, source, message)
                events += "journey:operation:complete:${operationId.type}:${errorCode ?: "success"}"
            }

            override fun completeFlow(outcome: UserJourney.Controller.Outcome) {
                completedOutcomes += outcome
                events += when (outcome) {
                    is UserJourney.Controller.Outcome.LoggedIn -> "journey:flow:complete:LoggedIn:${outcome.authMethod}"
                    is UserJourney.Controller.Outcome.Registered -> "journey:flow:complete:Registered:${outcome.authMethod}"
                    is UserJourney.Controller.Outcome.Completed -> "journey:flow:complete:Completed:${outcome.authMethod}"
                    is UserJourney.Controller.Outcome.Error -> "journey:flow:complete:Error:${outcome.errorCode}"
                }
            }
        }
    }

    private sealed interface LoginHint {
        object None : LoginHint
        data class Typed(val loginId: LoginId) : LoginHint
        data class Raw(val rawLoginId: String) : LoginHint

        companion object {
            fun from(context: Context?): LoginHint =
                context?.loginId?.let { Typed(it) } ?: context?.rawLoginId?.let { Raw(it) } ?: None
        }
    }

    private class RecordingOwnIdOperation(
        val loginIdCollectEntry: RecordingOperationEntry<LoginIdCollectOperation.Params?, LoginId, LoginIdCollectOperationFailure> =
            unavailableEntry(OperationType.LOGIN_ID_COLLECT),
        val loginEntry: RecordingOperationEntry<LoginOperation.Params?, LoginResponse, LoginOperationFailure> =
            unavailableEntry(OperationType.SESSION_CREATION),
        val passkeyCreateEntry: RecordingOperationEntry<PasskeyAttestationOperation.Params?, AttestationResponse, PasskeyAttestationOperationFailure> =
            recordingEntry(OperationType.PASSKEY_CREATION),
        val passkeyEnrollEntry: RecordingOperationEntry<PasskeyEnrollOperation.Params, Unit, PasskeyEnrollOperationFailure> =
            unavailableEntry(OperationType.PASSKEY_ENROLLMENT),
        val passkeyAuthEntry: RecordingOperationEntry<PasskeyAssertionOperation.Params?, AccessToken, PasskeyAssertionOperationFailure> =
            unavailableEntry(OperationType.PASSKEY_AUTH),
        val emailVerificationEntry: RecordingOperationEntry<EmailVerificationOperation.Params?, AccessOrProofToken, EmailVerificationOperationFailure> =
            unavailableEntry(OperationType.EMAIL_VERIFICATION),
        val phoneVerificationEntry: RecordingOperationEntry<PhoneVerificationOperation.Params?, AccessOrProofToken, PhoneVerificationOperationFailure> =
            unavailableEntry(OperationType.PHONE_NUMBER_VERIFICATION),
        private val scopedContext: Context? = null,
        private val delayedLoginResults: Map<Int, CompletableDeferred<OperationResult<LoginResponse, LoginOperationFailure>>> = emptyMap(),
        val scopedLoginHints: MutableList<LoginHint> = mutableListOf()
    ) : OwnIdOperation {
        override val loginId: OwnIdOperation.LoginId
            get() = object : OwnIdOperation.LoginId {
                override val collect: OperationEntry<LoginIdCollectOperation.Params?, LoginId, LoginIdCollectOperationFailure> =
                    object : OperationEntry<LoginIdCollectOperation.Params?, LoginId, LoginIdCollectOperationFailure>, DIResolvable {
                        override val operationType: OperationType = loginIdCollectEntry.operationType

                        override suspend fun availability(params: LoginIdCollectOperation.Params?): Availability =
                            loginIdCollectEntry.availability(params)

                        override fun start(params: LoginIdCollectOperation.Params?): OperationController<LoginId, LoginIdCollectOperationFailure> {
                            scopedLoginHints += LoginHint.from(scopedContext)
                            return loginIdCollectEntry.start(params)
                        }

                        override fun canResolve(): Boolean = loginIdCollectEntry.canResolve()
                        override fun getUnsatisfiedDependencies(): List<String>? = loginIdCollectEntry.getUnsatisfiedDependencies()
                    }
            }

        override val verifications: OwnIdOperation.Verifications
            get() = object : OwnIdOperation.Verifications {
                override val email: OperationEntry<EmailVerificationOperation.Params?, AccessOrProofToken, EmailVerificationOperationFailure> =
                    emailVerificationEntry
                override val phone: OperationEntry<PhoneVerificationOperation.Params?, AccessOrProofToken, PhoneVerificationOperationFailure> =
                    phoneVerificationEntry
            }

        override val passkeys: OwnIdOperation.Passkeys
            get() = object : OwnIdOperation.Passkeys {
                override val create: OperationEntry<PasskeyAttestationOperation.Params?, AttestationResponse, PasskeyAttestationOperationFailure> =
                    passkeyCreateEntry
                override val auth: OperationEntry<PasskeyAssertionOperation.Params?, AccessToken, PasskeyAssertionOperationFailure> = passkeyAuthEntry
                override val enroll: OperationEntry<PasskeyEnrollOperation.Params, Unit, PasskeyEnrollOperationFailure> = passkeyEnrollEntry
            }

        override val socialLogin: OwnIdOperation.SocialLogin
            get() = object : OwnIdOperation.SocialLogin {
                override val googleSignIn: OperationEntry<SignInWithGoogleOperation.Params?, AccessTokenWithUserInfo, SignInWithGoogleOperationFailure> =
                    unavailableEntry(OperationType.OIDC_AUTHENTICATION_GOOGLE)
            }

        override val login: OperationEntry<LoginOperation.Params?, LoginResponse, LoginOperationFailure> =
            object : OperationEntry<LoginOperation.Params?, LoginResponse, LoginOperationFailure>, DIResolvable by loginEntry {
                override val operationType = OperationType.SESSION_CREATION
                override suspend fun availability(params: LoginOperation.Params?): Availability = loginEntry.availability(params)
                override fun start(params: LoginOperation.Params?): OperationController<LoginResponse, LoginOperationFailure> {
                    val controller = loginEntry.start(params)
                    val pending = delayedLoginResults[loginEntry.startParams.size] ?: return controller
                    return object : OperationController<LoginResponse, LoginOperationFailure> {
                        override val operationId = controller.operationId
                        override suspend fun whenSettled(): OperationResult<LoginResponse, LoginOperationFailure> =
                            withContext(NonCancellable) { pending.await() }
                        override fun abort(reason: Reason) = controller.abort(reason)
                    }
                }
            }

        override fun withContext(scopeName: String, block: Context.Builder.() -> Unit): OwnIdOperation =
            RecordingOwnIdOperation(
                loginIdCollectEntry = loginIdCollectEntry,
                loginEntry = loginEntry,
                passkeyCreateEntry = passkeyCreateEntry,
                passkeyEnrollEntry = passkeyEnrollEntry,
                passkeyAuthEntry = passkeyAuthEntry,
                emailVerificationEntry = emailVerificationEntry,
                phoneVerificationEntry = phoneVerificationEntry,
                scopedContext = Context.Builder().apply(block).build(scopeName),
                scopedLoginHints = scopedLoginHints,
                delayedLoginResults = delayedLoginResults
            )

        override fun withProviders(scopeName: String, block: OwnIdProvidersRegistrar.() -> Unit): OwnIdOperation = this
    }

}
