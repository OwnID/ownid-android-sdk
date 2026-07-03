package com.ownid.sdk.ops.impl

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.OwnIdConfigurationImpl
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
import com.ownid.sdk.di.getOrThrow
import com.ownid.sdk.di.impl.DIContainerImpl
import com.ownid.sdk.di.injectInstanceDefaults
import com.ownid.sdk.model.AccessOrProofToken
import com.ownid.sdk.model.ChallengeId
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.InstanceName
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.LoginIdConfiguration
import com.ownid.sdk.model.OperationType
import com.ownid.sdk.model.Reason
import com.ownid.sdk.ops.EmailVerificationOperation
import com.ownid.sdk.ops.EmailVerificationOperationFailure
import com.ownid.sdk.ops.LoginIdCollectOperationFailure
import com.ownid.sdk.ops.OperationController
import com.ownid.sdk.ops.PhoneVerificationOperation
import com.ownid.sdk.ops.PhoneVerificationOperationFailure
import com.ownid.sdk.ops.ui.EmailVerificationUi
import com.ownid.sdk.ops.ui.LoginIdCollectUi
import com.ownid.sdk.ops.ui.PhoneVerificationUi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(InternalOwnIdAPI::class)
// Covers: UI-PRESENT-010, UI-PRESENT-020
internal class CoreUiOperationStartupContractTest {

    @Test
    fun defaultCoreLoginIdCollectUiFailureSettlesAsIntegrationUiFailure() = runTest {
        val registry = OperationRegistryImpl()
        val operation = loginIdCollectOperation(ui = defaultCoreLoginIdCollectUi(), registry = registry)

        val controller = operation.start(null)
        drainOperations()

        val failure = controller.whenSettled().assertFailureResult()
        assertThat(failure).isInstanceOf(LoginIdCollectOperationFailure.Integration.Ui::class.java)
        assertThat(failure.errorCode).isEqualTo(ErrorCode.IntegrationError)
        assertThat(failure.message).contains("No UI implementation registered for LoginIdCollectUi")
        assertThat((failure as LoginIdCollectOperationFailure.Integration.Ui).error).isInstanceOf(IllegalStateException::class.java)
        assertThat(registry.current.value).isEmpty()
    }

    @Test
    fun defaultCoreEmailVerificationUiFailureSettlesAsIntegrationUiFailure() = runTest {
        val registry = OperationRegistryImpl()
        val operation = emailVerificationOperation(ui = defaultCoreEmailVerificationUi(), registry = registry)

        val controller = operation.start(EmailVerificationOperation.Params(loginId = emailLoginId))
        drainOperations()

        val failure = controller.whenSettled().assertFailureResult()
        assertThat(failure).isInstanceOf(EmailVerificationOperationFailure.Integration.Ui::class.java)
        assertThat(failure.errorCode).isEqualTo(ErrorCode.IntegrationError)
        assertThat(failure.message).contains("No UI implementation registered for EmailVerificationUi")
        assertThat((failure as EmailVerificationOperationFailure.Integration.Ui).error).isInstanceOf(IllegalStateException::class.java)
        assertThat(registry.current.value).isEmpty()
    }

    @Test
    fun defaultCorePhoneVerificationUiFailureSettlesAsIntegrationUiFailure() = runTest {
        val registry = OperationRegistryImpl()
        val operation = phoneVerificationOperation(ui = defaultCorePhoneVerificationUi(), registry = registry)

        val controller = operation.start(PhoneVerificationOperation.Params(loginId = phoneLoginId))
        drainOperations()

        val failure = controller.whenSettled().assertFailureResult()
        assertThat(failure).isInstanceOf(PhoneVerificationOperationFailure.Integration.Ui::class.java)
        assertThat(failure.errorCode).isEqualTo(ErrorCode.IntegrationError)
        assertThat(failure.message).contains("No UI implementation registered for PhoneVerificationUi")
        assertThat((failure as PhoneVerificationOperationFailure.Integration.Ui).error).isInstanceOf(IllegalStateException::class.java)
        assertThat(registry.current.value).isEmpty()
    }

    @Test
    fun loginIdCollectUiStartupErrorSettlesOnce() = runTest {
        val registry = OperationRegistryImpl()
        val ui = FailingLoginIdCollectUi(
            StartupFailure(IllegalStateException("first login failure"), "first login details"),
            StartupFailure(IllegalArgumentException("second login failure"), "second login details")
        )
        val operation = loginIdCollectOperation(ui = ui, registry = registry)

        val controller = operation.start(null)
        drainOperations()
        val firstResult = controller.whenSettled()

        controller.abort(Reason.UserClose("after startup failure"))
        drainOperations()

        assertThat(ui.startCount).isEqualTo(1)
        assertThat(controller.whenSettled()).isSameInstanceAs(firstResult)
        val failure = firstResult.assertFailureResult() as LoginIdCollectOperationFailure.Integration.Ui
        assertThat(failure.message).isEqualTo("first login details")
        assertThat(failure.error).hasMessageThat().isEqualTo("first login failure")
        assertThat(registry.current.value).isEmpty()
    }

    @Test
    fun emailVerificationUiStartupErrorSettlesOnceBeforeLaterStartResult() = runTest {
        val registry = OperationRegistryImpl()
        val ui = FailingEmailVerificationUi(
            StartupFailure(IllegalStateException("first email failure"), "first email details"),
            StartupFailure(IllegalArgumentException("second email failure"), "second email details")
        )
        val api = EmailVerificationApiStub(ApiResult.Success(EmailControllerStub()))
        val operation = emailVerificationOperation(ui = ui, api = api, registry = registry)

        val controller = operation.start(EmailVerificationOperation.Params(loginId = emailLoginId))
        drainOperations()
        val firstResult = controller.whenSettled()

        controller.abort(Reason.UserClose("after startup failure"))
        drainOperations()

        assertThat(ui.startCount).isEqualTo(1)
        assertThat(api.startCalls).isAtMost(1)
        assertThat(controller.whenSettled()).isSameInstanceAs(firstResult)
        val failure = firstResult.assertFailureResult() as EmailVerificationOperationFailure.Integration.Ui
        assertThat(failure.message).isEqualTo("first email details")
        assertThat(failure.error).hasMessageThat().isEqualTo("first email failure")
        assertThat(registry.current.value).isEmpty()
    }

    @Test
    fun phoneVerificationUiStartupErrorSettlesOnceBeforeLaterStartResult() = runTest {
        val registry = OperationRegistryImpl()
        val ui = FailingPhoneVerificationUi(
            StartupFailure(IllegalStateException("first phone failure"), "first phone details"),
            StartupFailure(IllegalArgumentException("second phone failure"), "second phone details")
        )
        val api = PhoneVerificationApiStub(ApiResult.Success(PhoneControllerStub()))
        val operation = phoneVerificationOperation(ui = ui, api = api, registry = registry)

        val controller = operation.start(PhoneVerificationOperation.Params(loginId = phoneLoginId))
        drainOperations()
        val firstResult = controller.whenSettled()

        controller.abort(Reason.UserClose("after startup failure"))
        drainOperations()

        assertThat(ui.startCount).isEqualTo(1)
        assertThat(api.startCalls).isAtMost(1)
        assertThat(controller.whenSettled()).isSameInstanceAs(firstResult)
        val failure = firstResult.assertFailureResult() as PhoneVerificationOperationFailure.Integration.Ui
        assertThat(failure.message).isEqualTo("first phone details")
        assertThat(failure.error).hasMessageThat().isEqualTo("first phone failure")
        assertThat(registry.current.value).isEmpty()
    }

    private fun TestScope.loginIdCollectOperation(
        ui: LoginIdCollectUi,
        registry: OperationRegistryImpl = OperationRegistryImpl()
    ): LoginIdCollectOperationImpl =
        LoginIdCollectOperationImpl(
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

    private fun TestScope.emailVerificationOperation(
        ui: EmailVerificationUi,
        api: EmailVerificationApi = EmailVerificationApiStub(ApiResult.Success(EmailControllerStub())),
        registry: OperationRegistryImpl = OperationRegistryImpl()
    ): EmailVerificationOperationImpl =
        EmailVerificationOperationImpl(
            operationType = OperationType.EMAIL_VERIFICATION,
            operationRegistry = registry,
            ui = ui,
            errorStringsProvider = null,
            api = api,
            defaultDispatcher = operationTestDispatcher(),
            instanceJob = Job(),
            context = null,
            loginIdValidator = TestLoginIdValidator(),
            unsatisfiedDependencies = null,
            logger = null
        )

    private fun TestScope.phoneVerificationOperation(
        ui: PhoneVerificationUi,
        api: PhoneVerificationApi = PhoneVerificationApiStub(ApiResult.Success(PhoneControllerStub())),
        registry: OperationRegistryImpl = OperationRegistryImpl()
    ): PhoneVerificationOperationImpl =
        PhoneVerificationOperationImpl(
            operationType = OperationType.PHONE_NUMBER_VERIFICATION,
            operationRegistry = registry,
            ui = ui,
            errorStringsProvider = null,
            api = api,
            defaultDispatcher = operationTestDispatcher(),
            instanceJob = Job(),
            context = null,
            loginIdValidator = TestLoginIdValidator(),
            unsatisfiedDependencies = null,
            logger = null
        )

    private fun defaultCoreLoginIdCollectUi(): LoginIdCollectUi =
        defaultCoreContainer("login-default").getOrThrow()

    private fun defaultCoreEmailVerificationUi(): EmailVerificationUi =
        defaultCoreContainer("email-default").getOrThrow()

    private fun defaultCorePhoneVerificationUi(): PhoneVerificationUi =
        defaultCoreContainer("phone-default").getOrThrow()

    private fun defaultCoreContainer(scopeName: String): DIContainerImpl =
        DIContainerImpl(scopeName = scopeName).also { container ->
            container.injectInstanceDefaults(InstanceName(scopeName), OwnIdConfigurationImpl(appId = "app123"))
        }

    private data class StartupFailure(val error: Throwable?, val details: String?)

    private class FailingLoginIdCollectUi(private vararg val failures: StartupFailure) : LoginIdCollectUi {
        var startCount = 0

        override fun start(
            controller: OperationController<LoginId, LoginIdCollectOperationFailure>,
            onStartError: (error: Throwable?, details: String?) -> Unit
        ) {
            startCount++
            failures.forEach { failure -> onStartError(failure.error, failure.details) }
        }
    }

    private class FailingEmailVerificationUi(private vararg val failures: StartupFailure) : EmailVerificationUi {
        var startCount = 0

        override fun start(
            controller: OperationController<AccessOrProofToken, EmailVerificationOperationFailure>,
            onStartError: (error: Throwable?, details: String?) -> Unit
        ) {
            startCount++
            failures.forEach { failure -> onStartError(failure.error, failure.details) }
        }
    }

    private class FailingPhoneVerificationUi(private vararg val failures: StartupFailure) : PhoneVerificationUi {
        var startCount = 0

        override fun start(
            controller: OperationController<AccessOrProofToken, PhoneVerificationOperationFailure>,
            onStartError: (error: Throwable?, details: String?) -> Unit
        ) {
            startCount++
            failures.forEach { failure -> onStartError(failure.error, failure.details) }
        }
    }

    private class EmailVerificationApiStub(
        private val startResult: ApiResult<EmailVerificationApi.Controller, EmailVerificationStartApiFailure>
    ) : EmailVerificationApi {
        var startCalls = 0

        override suspend fun start(params: EmailVerificationApi.Params?): ApiResult<EmailVerificationApi.Controller, EmailVerificationStartApiFailure> {
            startCalls++
            return startResult
        }
    }

    private class PhoneVerificationApiStub(
        private val startResult: ApiResult<PhoneVerificationApi.Controller, PhoneVerificationStartApiFailure>
    ) : PhoneVerificationApi {
        var startCalls = 0

        override suspend fun start(params: PhoneVerificationApi.Params?): ApiResult<PhoneVerificationApi.Controller, PhoneVerificationStartApiFailure> {
            startCalls++
            return startResult
        }
    }

    private class EmailControllerStub : EmailVerificationApi.Controller {
        override val challenge = otpChallenge(ChallengeId("email-challenge"))
        override val completeWithCode: suspend (code: String) -> ApiResult<AccessOrProofToken, EmailVerificationCompleteApiFailure> =
            { ApiResult.Success(proofToken) }
        override val resend: suspend () -> ApiResult<Unit, EmailVerificationResendApiFailure> = { ApiResult.Success(Unit) }
        override val cancel: suspend (reason: Reason) -> ApiResult<Unit, EmailVerificationCancelApiFailure> = { ApiResult.Success(Unit) }
    }

    private class PhoneControllerStub : PhoneVerificationApi.Controller {
        override val challenge = otpChallenge(ChallengeId("phone-challenge"))
        override val completeWithCode: suspend (code: String) -> ApiResult<AccessOrProofToken, PhoneVerificationCompleteApiFailure> =
            { ApiResult.Success(proofToken) }
        override val resend: suspend () -> ApiResult<Unit, PhoneVerificationResendApiFailure> = { ApiResult.Success(Unit) }
        override val cancel: suspend (reason: Reason) -> ApiResult<Unit, PhoneVerificationCancelApiFailure> = { ApiResult.Success(Unit) }
    }
}
