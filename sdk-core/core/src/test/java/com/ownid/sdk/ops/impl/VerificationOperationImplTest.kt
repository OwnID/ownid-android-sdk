package com.ownid.sdk.ops.impl

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.api.ApiResult
import com.ownid.sdk.api.EmailVerificationApi
import com.ownid.sdk.api.EmailVerificationCancelApiFailure
import com.ownid.sdk.api.EmailVerificationCompleteApiFailure
import com.ownid.sdk.api.EmailVerificationResendApiFailure
import com.ownid.sdk.api.EmailVerificationStartApiFailure
import com.ownid.sdk.api.PhoneVerificationApi
import com.ownid.sdk.api.PhoneVerificationCancelApiFailure
import com.ownid.sdk.api.PhoneVerificationCompleteApiFailure
import com.ownid.sdk.api.PhoneVerificationResendApiFailure
import com.ownid.sdk.api.PhoneVerificationStartApiFailure
import com.ownid.sdk.model.AccessOrProofToken
import com.ownid.sdk.model.ChallengeId
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.OperationChannel
import com.ownid.sdk.model.OperationType
import com.ownid.sdk.model.Reason
import com.ownid.sdk.ops.EmailVerificationOperation
import com.ownid.sdk.ops.EmailVerificationOperationFailure
import com.ownid.sdk.ops.OperationController
import com.ownid.sdk.ops.PhoneVerificationOperation
import com.ownid.sdk.ops.PhoneVerificationOperationFailure
import com.ownid.sdk.ops.ui.EmailVerificationUi
import com.ownid.sdk.ops.ui.PhoneVerificationUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class, InternalOwnIdAPI::class)
// Covers: OPS-100, OPS-140, OPS-150, OPS-170
internal class VerificationOperationImplTest {

    @Test
    fun verificationOperationsAcceptMatchingTypedTargetsWithoutHintAndExposeApiChallengeChannel(): Unit = runTest {
        val emailApi = FakeEmailVerificationApi(
            ApiResult.Success(
                FakeEmailController(
                    challengeChannel = OperationChannel(channel = "api-email-channel@example.test", id = "api-email-channel-id")
                )
            )
        )
        val emailOperation = emailVerificationOperation(api = emailApi)

        val emailController = emailOperation.start(EmailVerificationOperation.Params(loginId = emailLoginId))
        drainOperations()

        val emailActive = emailController.state.value as EmailVerificationOperation.State.Active
        assertThat(emailApi.startParams.single()?.loginId).isEqualTo(emailLoginId)
        assertThat(emailApi.startParams.single()?.loginIdHintId).isNull()
        assertThat(emailActive.uiState.challenge.channel)
            .isEqualTo(OperationChannel(channel = "api-email-channel@example.test", id = "api-email-channel-id"))
        emailController.abort(Reason.UserClose())
        drainOperations()

        val phoneApi = FakePhoneVerificationApi(
            ApiResult.Success(
                FakePhoneController(
                    challengeChannel = OperationChannel(channel = "+1 *** 0100", id = "api-phone-channel-id")
                )
            )
        )
        val phoneOperation = phoneVerificationOperation(api = phoneApi)

        val phoneController = phoneOperation.start(PhoneVerificationOperation.Params(loginId = phoneLoginId))
        drainOperations()

        val phoneActive = phoneController.state.value as PhoneVerificationOperation.State.Active
        assertThat(phoneApi.startParams.single()?.loginId).isEqualTo(phoneLoginId)
        assertThat(phoneApi.startParams.single()?.loginIdHintId).isNull()
        assertThat(phoneActive.uiState.challenge.channel)
            .isEqualTo(OperationChannel(channel = "+1 *** 0100", id = "api-phone-channel-id"))
        phoneController.abort(Reason.UserClose())
        drainOperations()
    }

    @Test
    fun verificationOperationsForwardUsernameHintAndExposeApiChallengeChannel(): Unit = runTest {
        val username = LoginId("person", LoginId.Type.UserName)
        val emailApi = FakeEmailVerificationApi(
            ApiResult.Success(
                FakeEmailController(
                    challengeChannel = OperationChannel(channel = "selected-email@example.test", id = "returned-email-channel-id")
                )
            )
        )
        val emailOperation = emailVerificationOperation(api = emailApi)

        val emailController = emailOperation.start(EmailVerificationOperation.Params(loginId = username, loginIdHintId = "selected-email-hint-id"))
        drainOperations()

        val emailActive = emailController.state.value as EmailVerificationOperation.State.Active
        assertThat(emailApi.startParams.single()?.loginId).isEqualTo(username)
        assertThat(emailApi.startParams.single()?.loginIdHintId).isEqualTo("selected-email-hint-id")
        assertThat(emailActive.uiState.challenge.channel)
            .isEqualTo(OperationChannel(channel = "selected-email@example.test", id = "returned-email-channel-id"))
        emailController.abort(Reason.UserClose())
        drainOperations()

        val phoneApi = FakePhoneVerificationApi(
            ApiResult.Success(
                FakePhoneController(
                    challengeChannel = OperationChannel(channel = "+1 *** 0101", id = "returned-phone-channel-id")
                )
            )
        )
        val phoneOperation = phoneVerificationOperation(api = phoneApi)

        val phoneController = phoneOperation.start(PhoneVerificationOperation.Params(loginId = username, loginIdHintId = "selected-phone-hint-id"))
        drainOperations()

        val phoneActive = phoneController.state.value as PhoneVerificationOperation.State.Active
        assertThat(phoneApi.startParams.single()?.loginId).isEqualTo(username)
        assertThat(phoneApi.startParams.single()?.loginIdHintId).isEqualTo("selected-phone-hint-id")
        assertThat(phoneActive.uiState.challenge.channel)
            .isEqualTo(OperationChannel(channel = "+1 *** 0101", id = "returned-phone-channel-id"))
        phoneController.abort(Reason.UserClose())
        drainOperations()
    }

    @Test
    fun verificationOperationsRejectUsernameWithoutNonblankHint(): Unit = runTest {
        val username = LoginId("person", LoginId.Type.UserName)
        val emailOperation = emailVerificationOperation(api = FakeEmailVerificationApi(ApiResult.Success(FakeEmailController())))

        assertThat(emailOperation.isAvailable(EmailVerificationOperation.Params(loginId = username))).isFalse()
        assertThat(emailOperation.isAvailable(EmailVerificationOperation.Params(loginId = username, loginIdHintId = "  "))).isFalse()

        val emailController = emailOperation.start(EmailVerificationOperation.Params(loginId = username))
        drainOperations()
        val emailFailure = emailController.whenSettled().assertFailureResult()
        assertThat(emailFailure).isInstanceOf(EmailVerificationOperationFailure.Input.UnsupportedLoginIdType::class.java)

        val phoneOperation = phoneVerificationOperation(api = FakePhoneVerificationApi(ApiResult.Success(FakePhoneController())))

        assertThat(phoneOperation.isAvailable(PhoneVerificationOperation.Params(loginId = username))).isFalse()
        assertThat(phoneOperation.isAvailable(PhoneVerificationOperation.Params(loginId = username, loginIdHintId = "  "))).isFalse()

        val phoneController = phoneOperation.start(PhoneVerificationOperation.Params(loginId = username, loginIdHintId = ""))
        drainOperations()
        val phoneFailure = phoneController.whenSettled().assertFailureResult()
        assertThat(phoneFailure).isInstanceOf(PhoneVerificationOperationFailure.Input.UnsupportedLoginIdType::class.java)
    }

    @Test
    fun emailWrongOtpKeepsOperationActiveWithUiError() = runTest {
        val apiController = FakeEmailController(
            completeResult = ApiResult.Failure(
                EmailVerificationCompleteApiFailure.BadRequest.WrongCode(
                    errorCode = ErrorCode.VerificationCodeWrong,
                    message = "wrong code",
                    challengeId = ChallengeId("email-challenge")
                )
            )
        )
        val operation = emailVerificationOperation(api = FakeEmailVerificationApi(ApiResult.Success(apiController)))

        val controller = operation.start(EmailVerificationOperation.Params(loginId = emailLoginId))
        drainOperations()
        val active = controller.state.value as EmailVerificationOperation.State.Active

        active.uiState.onCodeEntered("000000")
        drainOperations()

        val afterWrongCode = controller.state.value
        assertThat(afterWrongCode).isInstanceOf(EmailVerificationOperation.State.Active::class.java)
        assertThatObject((afterWrongCode as EmailVerificationOperation.State.Active).uiState.error?.errorCode).isEqualTo(ErrorCode.VerificationCodeWrong)

        controller.abort(Reason.UserClose())
        drainOperations()
    }

    @Test
    fun phoneMaximumAttemptsOtpFailureSettlesTerminalFailure() = runTest {
        val apiFailure = PhoneVerificationCompleteApiFailure.BadRequest.MaximumAttemptsReached(
            errorCode = ErrorCode.MaximumAttemptsReached,
            message = "maximum attempts",
            challengeId = ChallengeId("phone-challenge")
        )
        val operation = phoneVerificationOperation(api = FakePhoneVerificationApi(ApiResult.Success(FakePhoneController(ApiResult.Failure(apiFailure)))))

        val controller = operation.start(PhoneVerificationOperation.Params(loginId = phoneLoginId))
        drainOperations()
        val active = controller.state.value as PhoneVerificationOperation.State.Active

        active.uiState.onCodeEntered("000000")
        drainOperations()
        assertThat(controller.state.value).isInstanceOf(PhoneVerificationOperation.State.Active::class.java)

        advanceTimeBy(1.seconds)
        runCurrent()
        drainOperations()

        val failure = controller.whenSettled().assertFailureResult()
        assertThat(failure).isInstanceOf(PhoneVerificationOperationFailure.Challenge.MaximumAttemptsReached::class.java)
        assertThat((failure as PhoneVerificationOperationFailure.Challenge.MaximumAttemptsReached).apiFailure).isSameInstanceAs(apiFailure)
    }

    @Test
    fun verificationStartApiFailuresSettleBeforeActiveOtpState() = runTest {
        val emailApiFailure = EmailVerificationStartApiFailure.BadRequest.InvalidArgument(ErrorCode.InvalidArgument, "bad email start")
        val emailController = emailVerificationOperation(api = FakeEmailVerificationApi(ApiResult.Failure(emailApiFailure)))
            .start(EmailVerificationOperation.Params(loginId = emailLoginId))
        drainOperations()

        assertThat(emailController.state.value).isInstanceOf(EmailVerificationOperation.State.Completed::class.java)
        val emailFailure = emailController.whenSettled().assertFailureResult()
        assertThat(emailFailure).isInstanceOf(EmailVerificationOperationFailure.Input.InvalidRequest::class.java)
        assertThat((emailFailure as EmailVerificationOperationFailure.Input.InvalidRequest).apiFailure).isSameInstanceAs(emailApiFailure)

        val phoneApiFailure = PhoneVerificationStartApiFailure.BadRequest.InvalidArgument(ErrorCode.InvalidArgument, "bad phone start")
        val phoneController = phoneVerificationOperation(api = FakePhoneVerificationApi(ApiResult.Failure(phoneApiFailure)))
            .start(PhoneVerificationOperation.Params(loginId = phoneLoginId))
        drainOperations()

        assertThat(phoneController.state.value).isInstanceOf(PhoneVerificationOperation.State.Completed::class.java)
        val phoneFailure = phoneController.whenSettled().assertFailureResult()
        assertThat(phoneFailure).isInstanceOf(PhoneVerificationOperationFailure.Input.InvalidRequest::class.java)
        assertThat((phoneFailure as PhoneVerificationOperationFailure.Input.InvalidRequest).apiFailure).isSameInstanceAs(phoneApiFailure)
    }

    @Test
    fun verificationResendSuccessKeepsActiveAndClearsBusyError() = runTest {
        val emailController = FakeEmailController(
            completeResult = ApiResult.Failure(
                EmailVerificationCompleteApiFailure.BadRequest.WrongCode(
                    errorCode = ErrorCode.VerificationCodeWrong,
                    message = "wrong email code",
                    challengeId = ChallengeId("email-challenge")
                )
            ),
            resendResults = listOf(ApiResult.Success(Unit))
        )
        val emailOperationController = emailVerificationOperation(api = FakeEmailVerificationApi(ApiResult.Success(emailController)))
            .start(EmailVerificationOperation.Params(loginId = emailLoginId))
        drainOperations()

        val emailActive = emailOperationController.state.value as EmailVerificationOperation.State.Active
        emailActive.uiState.onCodeEntered("000000")
        drainOperations()
        val emailAfterWrongCode = emailOperationController.state.value as EmailVerificationOperation.State.Active
        assertThatObject(emailAfterWrongCode.uiState.error?.errorCode).isEqualTo(ErrorCode.VerificationCodeWrong)

        emailAfterWrongCode.uiState.onResend()
        drainOperations()

        val emailAfterResend = emailOperationController.state.value as EmailVerificationOperation.State.Active
        assertThat(emailController.resendCalls).isEqualTo(1)
        assertThat(emailAfterResend.uiState.busy).isFalse()
        assertThat(emailAfterResend.uiState.error).isNull()
        emailOperationController.abort(Reason.UserClose())
        drainOperations()

        val phoneController = FakePhoneController(
            completeResult = ApiResult.Failure(
                PhoneVerificationCompleteApiFailure.BadRequest.WrongCode(
                    errorCode = ErrorCode.VerificationCodeWrong,
                    message = "wrong phone code",
                    challengeId = ChallengeId("phone-challenge")
                )
            ),
            resendResults = listOf(ApiResult.Success(Unit))
        )
        val phoneOperationController = phoneVerificationOperation(api = FakePhoneVerificationApi(ApiResult.Success(phoneController)))
            .start(PhoneVerificationOperation.Params(loginId = phoneLoginId))
        drainOperations()

        val phoneActive = phoneOperationController.state.value as PhoneVerificationOperation.State.Active
        phoneActive.uiState.onCodeEntered("000000")
        drainOperations()
        val phoneAfterWrongCode = phoneOperationController.state.value as PhoneVerificationOperation.State.Active
        assertThatObject(phoneAfterWrongCode.uiState.error?.errorCode).isEqualTo(ErrorCode.VerificationCodeWrong)

        phoneAfterWrongCode.uiState.onResend()
        drainOperations()

        val phoneAfterResend = phoneOperationController.state.value as PhoneVerificationOperation.State.Active
        assertThat(phoneController.resendCalls).isEqualTo(1)
        assertThat(phoneAfterResend.uiState.busy).isFalse()
        assertThat(phoneAfterResend.uiState.error).isNull()
        phoneOperationController.abort(Reason.UserClose())
        drainOperations()
    }

    @Test
    fun verificationMaximumResendKeepsActiveWithResendDisabledAndVisibleError() = runTest {
        val emailApiFailure = EmailVerificationResendApiFailure.BadRequest.MaximumResendAttemptsReached(
            errorCode = ErrorCode.MaximumResendAttemptsReached,
            message = "email resend limit",
            challengeId = ChallengeId("email-challenge")
        )
        val emailOperationController = emailVerificationOperation(
            api = FakeEmailVerificationApi(ApiResult.Success(FakeEmailController(resendResults = listOf(ApiResult.Failure(emailApiFailure)))))
        ).start(EmailVerificationOperation.Params(loginId = emailLoginId))
        drainOperations()

        val emailActive = emailOperationController.state.value as EmailVerificationOperation.State.Active
        emailActive.uiState.onResend()
        drainOperations()

        val emailAfterResend = emailOperationController.state.value as EmailVerificationOperation.State.Active
        assertThat(emailAfterResend.uiState.busy).isFalse()
        assertThat(emailAfterResend.uiState.challenge.resendPolicy.allow).isFalse()
        assertThatObject(emailAfterResend.uiState.error?.errorCode).isEqualTo(ErrorCode.MaximumResendAttemptsReached)
        emailOperationController.abort(Reason.UserClose())
        drainOperations()

        val phoneApiFailure = PhoneVerificationResendApiFailure.BadRequest.MaximumResendAttemptsReached(
            errorCode = ErrorCode.MaximumResendAttemptsReached,
            message = "phone resend limit",
            challengeId = ChallengeId("phone-challenge")
        )
        val phoneOperationController = phoneVerificationOperation(
            api = FakePhoneVerificationApi(ApiResult.Success(FakePhoneController(resendResults = listOf(ApiResult.Failure(phoneApiFailure)))))
        ).start(PhoneVerificationOperation.Params(loginId = phoneLoginId))
        drainOperations()

        val phoneActive = phoneOperationController.state.value as PhoneVerificationOperation.State.Active
        phoneActive.uiState.onResend()
        drainOperations()

        val phoneAfterResend = phoneOperationController.state.value as PhoneVerificationOperation.State.Active
        assertThat(phoneAfterResend.uiState.busy).isFalse()
        assertThat(phoneAfterResend.uiState.challenge.resendPolicy.allow).isFalse()
        assertThatObject(phoneAfterResend.uiState.error?.errorCode).isEqualTo(ErrorCode.MaximumResendAttemptsReached)
        phoneOperationController.abort(Reason.UserClose())
        drainOperations()
    }

    @Test
    fun verificationOtherResendFailuresDisableCallbacksAndSettleAfterDelay() = runTest {
        val emailApiFailure = EmailVerificationResendApiFailure.BadRequest.InvalidChallenge(
            errorCode = ErrorCode.InvalidChallenge,
            message = "email challenge expired",
            challengeId = ChallengeId("email-challenge")
        )
        val emailApiController = FakeEmailController(resendResults = listOf(ApiResult.Failure(emailApiFailure)))
        val emailOperationController = emailVerificationOperation(api = FakeEmailVerificationApi(ApiResult.Success(emailApiController)))
            .start(EmailVerificationOperation.Params(loginId = emailLoginId))
        drainOperations()

        val emailActive = emailOperationController.state.value as EmailVerificationOperation.State.Active
        emailActive.uiState.onResend()
        drainOperations()

        val emailAfterFailure = emailOperationController.state.value as EmailVerificationOperation.State.Active
        assertThatObject(emailAfterFailure.uiState.error?.errorCode).isEqualTo(ErrorCode.Unknown)
        emailAfterFailure.uiState.onCodeEntered("123456")
        emailAfterFailure.uiState.onResend()
        emailAfterFailure.uiState.onCancel()
        emailAfterFailure.uiState.onNotYou()
        drainOperations()
        assertThat(emailApiController.completeCalls).isEqualTo(0)
        assertThat(emailApiController.resendCalls).isEqualTo(1)
        assertThat(emailApiController.cancelCalls).isEqualTo(0)
        assertThat(emailOperationController.state.value).isInstanceOf(EmailVerificationOperation.State.Active::class.java)

        advanceTimeBy(1.seconds)
        runCurrent()
        drainOperations()

        val emailFailure = emailOperationController.whenSettled().assertFailureResult()
        assertThat(emailFailure).isInstanceOf(EmailVerificationOperationFailure.Challenge.Invalid::class.java)
        assertThat((emailFailure as EmailVerificationOperationFailure.Challenge.Invalid).apiFailure).isSameInstanceAs(emailApiFailure)

        val phoneApiFailure = PhoneVerificationResendApiFailure.BadRequest.InvalidChallenge(
            errorCode = ErrorCode.InvalidChallenge,
            message = "phone challenge expired",
            challengeId = ChallengeId("phone-challenge")
        )
        val phoneApiController = FakePhoneController(resendResults = listOf(ApiResult.Failure(phoneApiFailure)))
        val phoneOperationController = phoneVerificationOperation(api = FakePhoneVerificationApi(ApiResult.Success(phoneApiController)))
            .start(PhoneVerificationOperation.Params(loginId = phoneLoginId))
        drainOperations()

        val phoneActive = phoneOperationController.state.value as PhoneVerificationOperation.State.Active
        phoneActive.uiState.onResend()
        drainOperations()

        val phoneAfterFailure = phoneOperationController.state.value as PhoneVerificationOperation.State.Active
        assertThatObject(phoneAfterFailure.uiState.error?.errorCode).isEqualTo(ErrorCode.Unknown)
        phoneAfterFailure.uiState.onCodeEntered("123456")
        phoneAfterFailure.uiState.onResend()
        phoneAfterFailure.uiState.onCancel()
        phoneAfterFailure.uiState.onNotYou()
        drainOperations()
        assertThat(phoneApiController.completeCalls).isEqualTo(0)
        assertThat(phoneApiController.resendCalls).isEqualTo(1)
        assertThat(phoneApiController.cancelCalls).isEqualTo(0)
        assertThat(phoneOperationController.state.value).isInstanceOf(PhoneVerificationOperation.State.Active::class.java)

        advanceTimeBy(1.seconds)
        runCurrent()
        drainOperations()

        val phoneFailure = phoneOperationController.whenSettled().assertFailureResult()
        assertThat(phoneFailure).isInstanceOf(PhoneVerificationOperationFailure.Challenge.Invalid::class.java)
        assertThat((phoneFailure as PhoneVerificationOperationFailure.Challenge.Invalid).apiFailure).isSameInstanceAs(phoneApiFailure)
    }

    private fun TestScope.emailVerificationOperation(api: EmailVerificationApi): EmailVerificationOperationImpl =
        EmailVerificationOperationImpl(
            operationType = OperationType.EMAIL_VERIFICATION,
            operationRegistry = OperationRegistryImpl(),
            ui = NoopEmailVerificationUi,
            errorStringsProvider = null,
            api = api,
            defaultDispatcher = operationTestDispatcher(),
            instanceJob = Job(),
            context = null,
            loginIdValidator = TestLoginIdValidator(),
            unsatisfiedDependencies = null,
            logger = null
        )

    private fun TestScope.phoneVerificationOperation(api: PhoneVerificationApi): PhoneVerificationOperationImpl =
        PhoneVerificationOperationImpl(
            operationType = OperationType.PHONE_NUMBER_VERIFICATION,
            operationRegistry = OperationRegistryImpl(),
            ui = NoopPhoneVerificationUi,
            errorStringsProvider = null,
            api = api,
            defaultDispatcher = operationTestDispatcher(),
            instanceJob = Job(),
            context = null,
            loginIdValidator = TestLoginIdValidator(),
            unsatisfiedDependencies = null,
            logger = null
        )

    private object NoopEmailVerificationUi : EmailVerificationUi {
        override fun start(
            controller: OperationController<AccessOrProofToken, EmailVerificationOperationFailure>,
            onStartError: (error: Throwable?, details: String?) -> Unit
        ): Unit = Unit
    }

    private object NoopPhoneVerificationUi : PhoneVerificationUi {
        override fun start(
            controller: OperationController<AccessOrProofToken, PhoneVerificationOperationFailure>,
            onStartError: (error: Throwable?, details: String?) -> Unit
        ): Unit = Unit
    }

    private class FakeEmailVerificationApi(
        private val startResult: ApiResult<EmailVerificationApi.Controller, EmailVerificationStartApiFailure>
    ) : EmailVerificationApi {
        val startParams = mutableListOf<EmailVerificationApi.Params?>()

        override suspend fun start(params: EmailVerificationApi.Params?): ApiResult<EmailVerificationApi.Controller, EmailVerificationStartApiFailure> {
            startParams += params
            return startResult
        }
    }

    private class FakePhoneVerificationApi(
        private val startResult: ApiResult<PhoneVerificationApi.Controller, PhoneVerificationStartApiFailure>
    ) : PhoneVerificationApi {
        val startParams = mutableListOf<PhoneVerificationApi.Params?>()

        override suspend fun start(params: PhoneVerificationApi.Params?): ApiResult<PhoneVerificationApi.Controller, PhoneVerificationStartApiFailure> {
            startParams += params
            return startResult
        }
    }

    private class FakeEmailController(
        private val completeResult: ApiResult<AccessOrProofToken, EmailVerificationCompleteApiFailure> = ApiResult.Success(proofToken),
        private val resendResults: List<ApiResult<Unit, EmailVerificationResendApiFailure>> = listOf(ApiResult.Success(Unit)),
        challengeChannel: OperationChannel = OperationChannel(channel = "user@example.test", id = "channel-id")
    ) : EmailVerificationApi.Controller {
        init {
            require(resendResults.isNotEmpty())
        }

        var completeCalls = 0
            private set
        var resendCalls = 0
            private set
        var cancelCalls = 0
            private set

        override val challenge = otpChallenge(ChallengeId("email-challenge"), challengeChannel)
        override val completeWithCode: suspend (code: String) -> ApiResult<AccessOrProofToken, EmailVerificationCompleteApiFailure> = {
            completeCalls += 1
            completeResult
        }
        override val resend: suspend () -> ApiResult<Unit, EmailVerificationResendApiFailure> = {
            val nextResultIndex = resendCalls
            resendCalls += 1
            resendResults.getOrElse(nextResultIndex) { resendResults.last() }
        }
        override val cancel: suspend (reason: Reason) -> ApiResult<Unit, EmailVerificationCancelApiFailure> = {
            cancelCalls += 1
            ApiResult.Success(Unit)
        }
    }

    private class FakePhoneController(
        private val completeResult: ApiResult<AccessOrProofToken, PhoneVerificationCompleteApiFailure> = ApiResult.Success(proofToken),
        private val resendResults: List<ApiResult<Unit, PhoneVerificationResendApiFailure>> = listOf(ApiResult.Success(Unit)),
        challengeChannel: OperationChannel = OperationChannel(channel = "user@example.test", id = "channel-id")
    ) : PhoneVerificationApi.Controller {
        init {
            require(resendResults.isNotEmpty())
        }

        var completeCalls = 0
            private set
        var resendCalls = 0
            private set
        var cancelCalls = 0
            private set

        override val challenge = otpChallenge(ChallengeId("phone-challenge"), challengeChannel)
        override val completeWithCode: suspend (code: String) -> ApiResult<AccessOrProofToken, PhoneVerificationCompleteApiFailure> = {
            completeCalls += 1
            completeResult
        }
        override val resend: suspend () -> ApiResult<Unit, PhoneVerificationResendApiFailure> = {
            val nextResultIndex = resendCalls
            resendCalls += 1
            resendResults.getOrElse(nextResultIndex) { resendResults.last() }
        }
        override val cancel: suspend (reason: Reason) -> ApiResult<Unit, PhoneVerificationCancelApiFailure> = {
            cancelCalls += 1
            ApiResult.Success(Unit)
        }
    }

    private fun assertThatObject(actual: Any?) = assertThat(actual)
}
