package com.ownid.sdk.flow.passkey.impl

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.analytics.FlowInfo
import com.ownid.sdk.analytics.UserJourney
import com.ownid.sdk.flow.AcceptingLoginIdValidator
import com.ownid.sdk.flow.FlowResult
import com.ownid.sdk.flow.JsonSerialization
import com.ownid.sdk.flow.MainDispatcherRule
import com.ownid.sdk.flow.RecordingOperationEntry
import com.ownid.sdk.flow.RecordingUserJourney
import com.ownid.sdk.flow.accessTokenFor
import com.ownid.sdk.flow.passkey.PasskeyEnrollFlow
import com.ownid.sdk.flow.passkey.PasskeyEnrollFlowContext
import com.ownid.sdk.flow.passkey.PasskeyEnrollFlowFailure
import com.ownid.sdk.flow.recordingEntry
import com.ownid.sdk.flow.unavailableEntry
import com.ownid.sdk.model.AccessOrProofToken
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.AccessTokenWithUserInfo
import com.ownid.sdk.model.AttestationResponse
import com.ownid.sdk.model.Availability
import com.ownid.sdk.model.Context
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.LoginResponse
import com.ownid.sdk.model.OperationType
import com.ownid.sdk.model.ProofToken
import com.ownid.sdk.model.Reason
import com.ownid.sdk.namespace.OperationEntry
import com.ownid.sdk.namespace.OwnIdOperation
import com.ownid.sdk.ops.EmailVerificationOperation
import com.ownid.sdk.ops.EmailVerificationOperationFailure
import com.ownid.sdk.ops.LoginIdCollectOperation
import com.ownid.sdk.ops.LoginIdCollectOperationFailure
import com.ownid.sdk.ops.LoginOperation
import com.ownid.sdk.ops.LoginOperationFailure
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class, InternalOwnIdAPI::class)
// Covers: FLOW-050, FLOW-060, FLOW-070, FLOW-190, FLOW-ORCH-040
internal class PasskeyEnrollFlowFakeDrivenContractTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun availabilityAndStartFailWhenAccessTokenIsMissing(): Unit = runTest(mainDispatcherRule.testDispatcher) {
        val operations = RecordingOwnIdOperation()
        val flow = passkeyEnrollFlow(operations)

        val availability = flow.availability(PasskeyEnrollFlowContext { proofToken = ProofToken("proof-token") })
        val controller = flow.start(PasskeyEnrollFlowContext { proofToken = ProofToken("proof-token") })
        advanceUntilIdle()

        val result = controller.whenSettled()

        assertThat(availability).isInstanceOf(Availability.Unavailable::class.java)
        assertThat((availability as Availability.Unavailable).message).contains("AccessToken is required")
        assertThat(result).isInstanceOf(FlowResult.Failure::class.java)
        assertThat((result as FlowResult.Failure).failure).isInstanceOf(PasskeyEnrollFlowFailure.Input.MissingAccessToken::class.java)
        assertThat(operations.passkeyCreateEntry.availabilityParams).isEmpty()
        assertThat(operations.passkeyCreateEntry.startParams).isEmpty()
        assertThat(operations.passkeyEnrollEntry.availabilityParams).isEmpty()
        assertThat(operations.passkeyEnrollEntry.startParams).isEmpty()
    }

    @Test
    fun proofTokenPathSkipsLocalAttestationAndForwardsHeadlessToEnroll(): Unit = runTest(mainDispatcherRule.testDispatcher) {
        val accessToken = accessTokenFor("UserName:proof")
        val proofToken = ProofToken("proof-token")
        val operations = RecordingOwnIdOperation(
            passkeyEnrollEntry = recordingEntry(
                OperationType.PASSKEY_ENROLLMENT,
                OperationResult.Success(Unit)
            )
        )
        val flow = passkeyEnrollFlow(operations)
        val context = PasskeyEnrollFlowContext {
            this.accessToken = accessToken
            this.proofToken = proofToken
            headless = true
        }

        val availability = flow.availability(context)
        val controller = flow.start(context)
        advanceUntilIdle()

        val result = controller.whenSettled()

        assertThat(availability).isInstanceOf(Availability.Available::class.java)
        assertThat(operations.passkeyCreateEntry.availabilityParams).isEmpty()
        assertThat(operations.passkeyCreateEntry.startParams).isEmpty()
        assertThat(operations.passkeyEnrollEntry.availabilityParams).hasSize(2)
        assertThat(operations.passkeyEnrollEntry.availabilityParams.map { it.proofToken }).containsExactly(proofToken, proofToken).inOrder()
        assertThat(operations.passkeyEnrollEntry.availabilityParams.map { it.accessToken }).containsExactly(accessToken, accessToken).inOrder()
        assertThat(operations.passkeyEnrollEntry.availabilityParams.map { it.headless }).containsExactly(true, true).inOrder()
        assertThat(operations.passkeyEnrollEntry.startParams.single().proofToken).isSameInstanceAs(proofToken)
        assertThat(operations.passkeyEnrollEntry.startParams.single().accessToken).isSameInstanceAs(accessToken)
        assertThat(operations.passkeyEnrollEntry.startParams.single().headless).isTrue()
        assertThat(result).isInstanceOf(FlowResult.Success::class.java)
        assertThat((result as FlowResult.Success).success.loginId).isEqualTo(LoginId("proof", LoginId.Type.UserName))
    }

    @Test
    fun headlessProofTokenEnrollSuccessRecordsJourneyMetadataUserOperationAndCompletedOutcome(): Unit = runTest(mainDispatcherRule.testDispatcher) {
        val accessToken = accessTokenFor("UserName:journey-success")
        val proofToken = ProofToken("proof-token")
        val userJourney = RecordingUserJourney()
        val operations = RecordingOwnIdOperation(
            passkeyEnrollEntry = recordingEntry(
                OperationType.PASSKEY_ENROLLMENT,
                OperationResult.Success(Unit)
            )
        )
        val flow = passkeyEnrollFlow(operations, userJourney)
        val context = headlessProofTokenContext(accessToken, proofToken)

        val controller = flow.start(context)
        advanceUntilIdle()
        val result = controller.whenSettled()
        val enrollOperationId = operations.passkeyEnrollEntry.startedOperationIds.single()

        assertThat(result).isInstanceOf(FlowResult.Success::class.java)
        assertThat((result as FlowResult.Success).success.loginId).isEqualTo(LoginId("journey-success", LoginId.Type.UserName))
        assertThat(operations.passkeyEnrollEntry.startParams.single().traceParent).isEqualTo(TRACE_PARENT)
        assertThat(userJourney.startedFlows)
            .containsExactly(RecordingUserJourney.StartedFlow("headless-passkey-enroll", FlowInfo.Source.EXPLICIT, TRACE_PARENT))
        assertThat(userJourney.userInfos).containsExactly(LoginId("journey-success", LoginId.Type.UserName))
        assertThat(userJourney.startedOperations).containsExactly(enrollOperationId)
        assertThat(userJourney.completedOperations)
            .containsExactly(RecordingUserJourney.CompletedOperation(enrollOperationId, null, null, null))
        assertThat(userJourney.completedOutcomes.single()).isInstanceOf(UserJourney.Controller.Outcome.Completed::class.java)
    }

    @Test
    fun headlessProofTokenEnrollCancelRecordsJourneyOperationAndAbortedOutcomeWithoutChangingSettlement(): Unit = runTest(mainDispatcherRule.testDispatcher) {
        val accessToken = accessTokenFor("UserName:journey-cancel")
        val proofToken = ProofToken("proof-token")
        val reason = Reason.UserClose("passkey enroll closed")
        val userJourney = RecordingUserJourney()
        val operations = RecordingOwnIdOperation(
            passkeyEnrollEntry = recordingEntry(
                OperationType.PASSKEY_ENROLLMENT,
                OperationResult.Canceled(reason)
            )
        )
        val flow = passkeyEnrollFlow(operations, userJourney)

        val controller = flow.start(headlessProofTokenContext(accessToken, proofToken))
        advanceUntilIdle()
        val result = controller.whenSettled()
        val enrollOperationId = operations.passkeyEnrollEntry.startedOperationIds.single()

        assertThat(result).isInstanceOf(FlowResult.Canceled::class.java)
        assertThat((result as FlowResult.Canceled).reason).isSameInstanceAs(reason)
        assertThat(userJourney.startedOperations).containsExactly(enrollOperationId)
        val operationCompletion = userJourney.completedOperations.single()
        assertThat(operationCompletion.operationId).isEqualTo(enrollOperationId)
        assertThat(operationCompletion.errorCode).isEqualTo(ErrorCode.Aborted)
        assertThat(operationCompletion.source).isEqualTo("PasskeyEnrollFlowImpl.handleEnrollOpResult.canceled")
        assertThat(operationCompletion.message).contains("Canceled with reason: $reason")
        val outcome = userJourney.completedOutcomes.single()
        assertThat(outcome).isInstanceOf(UserJourney.Controller.Outcome.Error::class.java)
        outcome as UserJourney.Controller.Outcome.Error
        assertThat(outcome.errorCode).isEqualTo(ErrorCode.Aborted)
        assertThat(outcome.source).isEqualTo("PasskeyEnrollFlowImpl.completeFlow.canceled")
    }

    @Test
    fun headlessProofTokenEnrollFailureRecordsJourneyOperationAndFailedOutcomeWithoutChangingSettlement(): Unit = runTest(mainDispatcherRule.testDispatcher) {
        val accessToken = accessTokenFor("UserName:journey-failure")
        val proofToken = ProofToken("proof-token")
        val operationFailure = PasskeyEnrollOperationFailure.Integration.ProviderFailed(ErrorCode.Unknown, "server enroll failed")
        val userJourney = RecordingUserJourney()
        val operations = RecordingOwnIdOperation(
            passkeyEnrollEntry = recordingEntry(
                OperationType.PASSKEY_ENROLLMENT,
                OperationResult.Failure(operationFailure)
            )
        )
        val flow = passkeyEnrollFlow(operations, userJourney)

        val controller = flow.start(headlessProofTokenContext(accessToken, proofToken))
        advanceUntilIdle()
        val result = controller.whenSettled()
        val enrollOperationId = operations.passkeyEnrollEntry.startedOperationIds.single()

        assertThat(result).isInstanceOf(FlowResult.Failure::class.java)
        assertThat(userJourney.startedOperations).containsExactly(enrollOperationId)
        assertThat(userJourney.completedOperations)
            .containsExactly(
                RecordingUserJourney.CompletedOperation(
                    operationId = enrollOperationId,
                    errorCode = ErrorCode.Unknown,
                    source = "PasskeyEnrollFlowImpl.handleEnrollOpResult.failure",
                    message = "server enroll failed"
                )
            )
        val outcome = userJourney.completedOutcomes.single()
        assertThat(outcome).isInstanceOf(UserJourney.Controller.Outcome.Error::class.java)
        outcome as UserJourney.Controller.Outcome.Error
        assertThat(outcome.errorCode).isEqualTo(ErrorCode.Unknown)
        assertThat(outcome.source).isEqualTo("PasskeyEnrollFlowImpl.completeFlow.failure")
        assertThat(outcome.message).isEqualTo("Passkey enroll failed: server enroll failed")
    }

    @Test
    fun missingProofTokenPathRunsLocalAttestationBeforeEnrollAndDoesNotSuppressItForHeadless(): Unit = runTest(mainDispatcherRule.testDispatcher) {
        val accessToken = accessTokenFor("UserName:token")
        val attestedProofToken = ProofToken("attested-proof-token")
        val operations = RecordingOwnIdOperation(
            passkeyCreateEntry = recordingEntry(
                OperationType.PASSKEY_CREATION,
                OperationResult.Success(AttestationResponse(attestedProofToken, """{"ownId":true}"""))
            ),
            passkeyEnrollEntry = recordingEntry(
                OperationType.PASSKEY_ENROLLMENT,
                OperationResult.Success(Unit)
            )
        )
        val flow = passkeyEnrollFlow(operations)
        val context = PasskeyEnrollFlowContext {
            this.accessToken = accessToken
            headless = true
        }

        val availability = flow.availability(context)
        val controller = flow.start(context)
        advanceUntilIdle()

        val result = controller.whenSettled()

        assertThat(availability).isInstanceOf(Availability.Available::class.java)
        assertThat(operations.passkeyCreateEntry.availabilityParams).hasSize(2)
        assertThat(operations.passkeyCreateEntry.availabilityParams.map { it?.accessToken }).containsExactly(accessToken, accessToken).inOrder()
        assertThat(operations.passkeyCreateEntry.availabilityParams.map { it?.loginId }).containsExactly(null, null).inOrder()
        assertThat(operations.passkeyCreateEntry.startParams.single()?.accessToken).isSameInstanceAs(accessToken)
        assertThat(operations.passkeyCreateEntry.startParams.single()?.loginId).isNull()
        assertThat(operations.passkeyEnrollEntry.availabilityParams).hasSize(1)
        assertThat(operations.passkeyEnrollEntry.availabilityParams.single().proofToken).isSameInstanceAs(attestedProofToken)
        assertThat(operations.passkeyEnrollEntry.availabilityParams.single().accessToken).isSameInstanceAs(accessToken)
        assertThat(operations.passkeyEnrollEntry.availabilityParams.single().headless).isTrue()
        assertThat(operations.passkeyEnrollEntry.startParams.single().proofToken).isSameInstanceAs(attestedProofToken)
        assertThat(operations.passkeyEnrollEntry.startParams.single().accessToken).isSameInstanceAs(accessToken)
        assertThat(operations.passkeyEnrollEntry.startParams.single().headless).isTrue()
        assertThat(result).isInstanceOf(FlowResult.Success::class.java)
        assertThat((result as FlowResult.Success).success.loginId).isEqualTo(LoginId("token", LoginId.Type.UserName))
    }

    @Test
    fun localAttestationOperationFailureMapsToOperationFailedAndLeavesNoActiveController(): Unit = runTest(mainDispatcherRule.testDispatcher) {
        val accessToken = accessTokenFor("UserName:attestation-failure")
        val operationFailure = PasskeyAttestationOperationFailure.Integration.ProviderFailed(
            ErrorCode.PasskeyNotCreated,
            "local attestation failed"
        )
        val operations = RecordingOwnIdOperation(
            passkeyCreateEntry = recordingEntry(
                OperationType.PASSKEY_CREATION,
                OperationResult.Failure(operationFailure)
            ),
            passkeyEnrollEntry = recordingEntry(OperationType.PASSKEY_ENROLLMENT)
        )
        val flow = passkeyEnrollFlow(operations)
        val context = PasskeyEnrollFlowContext {
            this.accessToken = accessToken
        }

        val availability = flow.availability(context)
        val controller = flow.start(context)
        advanceUntilIdle()

        val firstResult = controller.whenSettled()
        val failure = assertOperationFailed(firstResult)

        assertThat(availability).isInstanceOf(Availability.Available::class.java)
        assertThat(failure.operationType).isEqualTo(OperationType.PASSKEY_CREATION)
        assertThat(failure.errorCode).isEqualTo(operationFailure.errorCode)
        assertThat(failure.message).contains("Passkey attestation failed")
        assertThat(failure.operationFailure).isSameInstanceAs(operationFailure)
        assertThat(operations.passkeyCreateEntry.availabilityParams).hasSize(2)
        assertThat(operations.passkeyCreateEntry.startParams).hasSize(1)
        assertThat(failure.operationId).isEqualTo(operations.passkeyCreateEntry.startedOperationIds.single())
        assertThat(operations.passkeyEnrollEntry.availabilityParams).isEmpty()
        assertThat(operations.passkeyEnrollEntry.startParams).isEmpty()
        assertThat(controller.whenSettled()).isSameInstanceAs(firstResult)

        controller.abort(Reason.UserClose())
        advanceUntilIdle()

        assertThat(operations.passkeyCreateEntry.abortReasons).isEmpty()
        assertThat(operations.passkeyEnrollEntry.abortReasons).isEmpty()
        assertThat(controller.whenSettled()).isSameInstanceAs(firstResult)
    }

    @Test
    fun enrollOperationFailureAfterLocalAttestationMapsToOperationFailedAndLeavesNoActiveController(): Unit = runTest(mainDispatcherRule.testDispatcher) {
        val accessToken = accessTokenFor("UserName:enroll-failure")
        val attestedProofToken = ProofToken("attested-proof-token")
        val operationFailure = PasskeyEnrollOperationFailure.Access.Forbidden(ErrorCode.Forbidden, "server enroll failed")
        val operations = RecordingOwnIdOperation(
            passkeyCreateEntry = recordingEntry(
                OperationType.PASSKEY_CREATION,
                OperationResult.Success(AttestationResponse(attestedProofToken, """{"ownId":true}"""))
            ),
            passkeyEnrollEntry = recordingEntry(
                OperationType.PASSKEY_ENROLLMENT,
                OperationResult.Failure(operationFailure)
            )
        )
        val flow = passkeyEnrollFlow(operations)
        val context = PasskeyEnrollFlowContext {
            this.accessToken = accessToken
            headless = true
        }

        val availability = flow.availability(context)
        val controller = flow.start(context)
        advanceUntilIdle()

        val firstResult = controller.whenSettled()
        val failure = assertOperationFailed(firstResult)

        assertThat(availability).isInstanceOf(Availability.Available::class.java)
        assertThat(operations.passkeyCreateEntry.startParams).hasSize(1)
        assertThat(operations.passkeyCreateEntry.startParams.single()?.accessToken).isSameInstanceAs(accessToken)
        assertThat(operations.passkeyEnrollEntry.startParams).hasSize(1)
        assertThat(operations.passkeyEnrollEntry.startParams.single().proofToken).isSameInstanceAs(attestedProofToken)
        assertThat(operations.passkeyEnrollEntry.startParams.single().accessToken).isSameInstanceAs(accessToken)
        assertThat(operations.passkeyEnrollEntry.startParams.single().headless).isTrue()
        assertThat(failure.operationType).isEqualTo(OperationType.PASSKEY_ENROLLMENT)
        assertThat(failure.errorCode).isEqualTo(operationFailure.errorCode)
        assertThat(failure.message).contains("Passkey enroll failed")
        assertThat(failure.operationFailure).isSameInstanceAs(operationFailure)
        assertThat(failure.operationId).isEqualTo(operations.passkeyEnrollEntry.startedOperationIds.single())
        assertThat(controller.whenSettled()).isSameInstanceAs(firstResult)

        controller.abort(Reason.UserClose())
        advanceUntilIdle()

        assertThat(operations.passkeyCreateEntry.abortReasons).isEmpty()
        assertThat(operations.passkeyEnrollEntry.abortReasons).isEmpty()
        assertThat(controller.whenSettled()).isSameInstanceAs(firstResult)
    }

    private fun assertOperationFailed(
        result: FlowResult<PasskeyEnrollFlow.Response, PasskeyEnrollFlowFailure>
    ): PasskeyEnrollFlowFailure.OperationFailed {
        assertThat(result).isInstanceOf(FlowResult.Failure::class.java)
        val failure = (result as FlowResult.Failure).failure
        assertThat(failure).isInstanceOf(PasskeyEnrollFlowFailure.OperationFailed::class.java)
        return failure as PasskeyEnrollFlowFailure.OperationFailed
    }

    private fun headlessProofTokenContext(accessToken: AccessToken, proofToken: ProofToken): PasskeyEnrollFlowContext =
        PasskeyEnrollFlowContext {
            this.accessToken = accessToken
            this.proofToken = proofToken
            headless = true
            traceParent = TRACE_PARENT
        }

    private fun TestScope.passkeyEnrollFlow(
        operations: RecordingOwnIdOperation,
        userJourney: UserJourney? = null,
        context: Context? = null
    ): PasskeyEnrollFlowImpl =
        PasskeyEnrollFlowImpl(
            ownIdOperation = operations,
            serialization = JsonSerialization,
            loginIdValidator = AcceptingLoginIdValidator,
            userJourney = userJourney,
            defaultDispatcher = StandardTestDispatcher(testScheduler),
            instanceJob = Job(),
            context = context,
            logger = null
        )

    private class RecordingOwnIdOperation(
        val passkeyCreateEntry: RecordingOperationEntry<PasskeyAttestationOperation.Params?, AttestationResponse, PasskeyAttestationOperationFailure> =
            recordingEntry(OperationType.PASSKEY_CREATION),
        val passkeyEnrollEntry: RecordingOperationEntry<PasskeyEnrollOperation.Params, Unit, PasskeyEnrollOperationFailure> =
            recordingEntry(OperationType.PASSKEY_ENROLLMENT)
    ) : OwnIdOperation {
        override val loginId: OwnIdOperation.LoginId
            get() = object : OwnIdOperation.LoginId {
                override val collect: OperationEntry<LoginIdCollectOperation.Params?, LoginId, LoginIdCollectOperationFailure> =
                    unavailableEntry(OperationType.LOGIN_ID_COLLECT)
            }

        override val verifications: OwnIdOperation.Verifications
            get() = object : OwnIdOperation.Verifications {
                override val email: OperationEntry<EmailVerificationOperation.Params?, AccessOrProofToken, EmailVerificationOperationFailure> =
                    unavailableEntry(OperationType.EMAIL_VERIFICATION)
                override val phone: OperationEntry<PhoneVerificationOperation.Params?, AccessOrProofToken, PhoneVerificationOperationFailure> =
                    unavailableEntry(OperationType.PHONE_NUMBER_VERIFICATION)
            }

        override val passkeys: OwnIdOperation.Passkeys
            get() = object : OwnIdOperation.Passkeys {
                override val create: OperationEntry<PasskeyAttestationOperation.Params?, AttestationResponse, PasskeyAttestationOperationFailure> =
                    passkeyCreateEntry
                override val auth: OperationEntry<PasskeyAssertionOperation.Params?, AccessToken, PasskeyAssertionOperationFailure> =
                    unavailableEntry(OperationType.PASSKEY_AUTH)
                override val enroll: OperationEntry<PasskeyEnrollOperation.Params, Unit, PasskeyEnrollOperationFailure> = passkeyEnrollEntry
            }

        override val socialLogin: OwnIdOperation.SocialLogin
            get() = object : OwnIdOperation.SocialLogin {
                override val signInWithGoogle: OperationEntry<SignInWithGoogleOperation.Params?, AccessTokenWithUserInfo, SignInWithGoogleOperationFailure> =
                    unavailableEntry(OperationType.OIDC_AUTHENTICATION_GOOGLE)
            }

        override val login: OperationEntry<LoginOperation.Params?, LoginResponse, LoginOperationFailure> =
            unavailableEntry(OperationType.SESSION_CREATION)

        override fun withContext(scopeName: String, block: Context.Builder.() -> Unit): OwnIdOperation = this
        override fun withProviders(scopeName: String, block: OwnIdProvidersRegistrar.() -> Unit): OwnIdOperation = this
    }

    private companion object {
        private const val TRACE_PARENT = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-00"
    }
}
