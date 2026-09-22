package com.ownid.sdk.ops.impl

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.api.ApiResult
import com.ownid.sdk.api.PasskeyAssertionApi
import com.ownid.sdk.api.PasskeyAssertionCancelApiFailure
import com.ownid.sdk.api.PasskeyAssertionStartApiFailure
import com.ownid.sdk.api.PasskeyAssertionVerifyApiFailure
import com.ownid.sdk.capability.Passkey
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.AssertionOptions
import com.ownid.sdk.model.AssertionResult
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.OperationType
import com.ownid.sdk.model.Reason
import com.ownid.sdk.ops.PasskeyAssertionOperation
import com.ownid.sdk.ops.PasskeyAssertionOperationFailure
import com.ownid.sdk.ops.ui.PasskeyAssertionUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(InternalOwnIdAPI::class)
// Covers: OPS-110, OPS-180, OPS-220
internal class PasskeyAssertionOperationImplTest {

    @Test
    fun successPassesApiOptionsToCredentialUiAndVerifiesReturnedAssertion() = runTest {
        val options = assertionOptions()
        val assertion = assertionResult()
        val expectedToken = AccessToken("verified-access-token")
        val apiController = FakeAssertionController(options, verifyResult = ApiResult.Success(expectedToken))
        val ui = FakePasskeyAssertionUi(Passkey.Result.Success(assertion))
        val operation = passkeyAssertionOperation(ui = ui, api = FakePasskeyAssertionApi(ApiResult.Success(apiController)))

        val controller = operation.start(PasskeyAssertionOperation.Params(loginId = emailLoginId))
        drainOperations()

        assertThat(controller.whenSettled().assertSuccessResult()).isEqualTo(expectedToken)
        assertThat(ui.options).containsExactly(options)
        assertThat(apiController.verifiedAssertions).containsExactly(assertion)
        assertThat(apiController.canceledReasons).isEmpty()
    }

    @Test
    fun credentialCancellationCancelsServerChallengeAndSettlesCanceled() = runTest {
        val cancelReason = Reason.UserClose()
        val apiController = FakeAssertionController(assertionOptions(), verifyResult = ApiResult.Success(accessToken))
        val operation = passkeyAssertionOperation(
            ui = FakePasskeyAssertionUi(Passkey.Result.Canceled(cancelReason)),
            api = FakePasskeyAssertionApi(ApiResult.Success(apiController))
        )

        val controller = operation.start(PasskeyAssertionOperation.Params(loginId = emailLoginId))
        drainOperations()

        assertThat(controller.whenSettled().assertCanceledResult()).isSameInstanceAs(cancelReason)
        assertThat(apiController.canceledReasons).containsExactly(cancelReason)
        assertThat(apiController.verifiedAssertions).isEmpty()
    }

    @Test
    fun noCredentialFailureCancelsChallengeAndSettlesDedicatedFailureWithCleanup() = runTest {
        val error = Passkey.NoCredentialError()

        assertCredentialFailure(error) { failure ->
            assertThat(failure).isInstanceOf(PasskeyAssertionOperationFailure.Credential.NoApplicablePasskeys::class.java)
            val noApplicablePasskeys = failure as PasskeyAssertionOperationFailure.Credential.NoApplicablePasskeys
            assertThat(noApplicablePasskeys.errorCode).isEqualTo(ErrorCode.NoApplicablePasskeys)
            assertThat(noApplicablePasskeys.error).isSameInstanceAs(error)
        }
    }

    @Test
    fun genericCredentialFailureCancelsChallengeAndSettlesProviderFailureWithCleanup() = runTest {
        val error = IllegalStateException("credential provider failed")

        assertCredentialFailure(error) { failure ->
            assertThat(failure).isInstanceOf(PasskeyAssertionOperationFailure.Integration.ProviderFailed::class.java)
            val providerFailed = failure as PasskeyAssertionOperationFailure.Integration.ProviderFailed
            assertThat(providerFailed.errorCode).isEqualTo(ErrorCode.IntegrationError)
            assertThat(providerFailed.error).isSameInstanceAs(error)
        }
    }

    @Test
    fun credentialFailureKeepsProviderFailureWhenServerCancelReturnsFailure() = runTest {
        val error = IllegalStateException("credential provider failed")

        assertCredentialFailure(
            error = error,
            cancelResult = ApiResult.Failure(
                PasskeyAssertionCancelApiFailure.BadRequest.InvalidArgument(ErrorCode.InvalidArgument, "assertion cancel rejected")
            )
        ) { failure ->
            assertThat(failure).isInstanceOf(PasskeyAssertionOperationFailure.Integration.ProviderFailed::class.java)
            assertThat((failure as PasskeyAssertionOperationFailure.Integration.ProviderFailed).error).isSameInstanceAs(error)
        }
    }

    @Test
    fun credentialFailureKeepsProviderFailureWhenServerCancelThrowsCancellationException() = runTest {
        val error = IllegalStateException("credential provider failed")

        assertCredentialFailure(
            error = error,
            cancelError = CancellationException("assertion cancel canceled")
        ) { failure ->
            assertThat(failure).isInstanceOf(PasskeyAssertionOperationFailure.Integration.ProviderFailed::class.java)
            assertThat((failure as PasskeyAssertionOperationFailure.Integration.ProviderFailed).error).isSameInstanceAs(error)
        }
    }

    @Test
    fun backendVerifyFailureAfterCredentialSuccessSettlesTypedFailureAndClearsRegistry() = runTest {
        val assertion = assertionResult()
        val apiFailure = PasskeyAssertionVerifyApiFailure.Forbidden(ErrorCode.Forbidden, "assertion verify rejected")
        val apiController = FakeAssertionController(assertionOptions(), verifyResult = ApiResult.Failure(apiFailure))
        val registry = OperationRegistryImpl()
        val operation = passkeyAssertionOperation(
            registry = registry,
            ui = FakePasskeyAssertionUi(Passkey.Result.Success(assertion)),
            api = FakePasskeyAssertionApi(ApiResult.Success(apiController))
        )

        val controller = operation.start(PasskeyAssertionOperation.Params(loginId = emailLoginId))
        drainOperations()

        val firstResult = controller.whenSettled()
        val failure = firstResult.assertFailureResult()

        assertThat(failure).isInstanceOf(PasskeyAssertionOperationFailure.Access.Forbidden::class.java)
        assertThat((failure as PasskeyAssertionOperationFailure.Access.Forbidden).apiFailure).isSameInstanceAs(apiFailure)
        assertThat(apiController.verifiedAssertions).containsExactly(assertion)
        assertThat(apiController.canceledReasons).isEmpty()
        assertThat(registry.current.value).isEmpty()
        assertThat(controller.whenSettled()).isSameInstanceAs(firstResult)

        controller.abort(Reason.UserClose())
        drainOperations()

        assertThat(registry.current.value).isEmpty()
        assertThat(controller.whenSettled()).isSameInstanceAs(firstResult)
    }

    private fun TestScope.passkeyAssertionOperation(
        ui: PasskeyAssertionUi,
        api: PasskeyAssertionApi,
        registry: OperationRegistryImpl = OperationRegistryImpl()
    ): PasskeyAssertionOperationImpl =
        PasskeyAssertionOperationImpl(
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

    private suspend fun TestScope.assertCredentialFailure(
        error: Throwable,
        cancelResult: ApiResult<Unit, PasskeyAssertionCancelApiFailure> = ApiResult.Success(Unit),
        cancelError: Throwable? = null,
        assertFailure: (PasskeyAssertionOperationFailure) -> Unit
    ) {
        val apiController = FakeAssertionController(
            assertionOptions(),
            verifyResult = ApiResult.Success(accessToken),
            cancelResult = cancelResult,
            cancelError = cancelError
        )
        val registry = OperationRegistryImpl()
        val operation = passkeyAssertionOperation(
            registry = registry,
            ui = FakePasskeyAssertionUi(Passkey.Result.Fail(error)),
            api = FakePasskeyAssertionApi(ApiResult.Success(apiController))
        )

        val controller = operation.start(PasskeyAssertionOperation.Params(loginId = emailLoginId))
        drainOperations()

        val firstResult = controller.whenSettled()
        assertFailure(firstResult.assertFailureResult())
        assertThat(apiController.canceledReasons).hasSize(1)
        assertThat(apiController.canceledReasons.single()).isInstanceOf(Reason.SystemError::class.java)
        assertThat(apiController.verifiedAssertions).isEmpty()
        assertThat(registry.current.value).isEmpty()
        assertThat(controller.whenSettled()).isSameInstanceAs(firstResult)

        controller.abort(Reason.UserClose())
        drainOperations()

        assertThat(apiController.canceledReasons).hasSize(1)
        assertThat(registry.current.value).isEmpty()
        assertThat(controller.whenSettled()).isSameInstanceAs(firstResult)
    }

    private class FakePasskeyAssertionApi(
        private val startResult: ApiResult<PasskeyAssertionApi.Controller, PasskeyAssertionStartApiFailure>
    ) : PasskeyAssertionApi {
        override suspend fun start(params: PasskeyAssertionApi.Params?): ApiResult<PasskeyAssertionApi.Controller, PasskeyAssertionStartApiFailure> =
            startResult
    }

    private class FakeAssertionController(
        override val assertionOptions: AssertionOptions,
        private val verifyResult: ApiResult<AccessToken, PasskeyAssertionVerifyApiFailure>,
        private val cancelResult: ApiResult<Unit, PasskeyAssertionCancelApiFailure> = ApiResult.Success(Unit),
        private val cancelError: Throwable? = null
    ) : PasskeyAssertionApi.Controller {
        val verifiedAssertions = mutableListOf<AssertionResult>()
        val canceledReasons = mutableListOf<Reason>()

        override val verify: suspend (assertion: AssertionResult) -> ApiResult<AccessToken, PasskeyAssertionVerifyApiFailure> = { assertion ->
            verifiedAssertions += assertion
            verifyResult
        }

        override val cancel: suspend (reason: Reason) -> ApiResult<Unit, PasskeyAssertionCancelApiFailure> = { reason ->
            canceledReasons += reason
            cancelError?.let { throw it }
            cancelResult
        }
    }

    private class FakePasskeyAssertionUi(
        private val result: Passkey.Result<AssertionResult>
    ) : PasskeyAssertionUi {
        val options = mutableListOf<AssertionOptions>()

        override suspend fun getCredential(assertionOptions: AssertionOptions): Passkey.Result<AssertionResult> {
            options += assertionOptions
            return result
        }
    }
}
