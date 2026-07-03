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
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(InternalOwnIdAPI::class)
// Covers: OPS-110, OPS-180
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

    private class FakePasskeyAssertionApi(
        private val startResult: ApiResult<PasskeyAssertionApi.Controller, PasskeyAssertionStartApiFailure>
    ) : PasskeyAssertionApi {
        override suspend fun start(params: PasskeyAssertionApi.Params?): ApiResult<PasskeyAssertionApi.Controller, PasskeyAssertionStartApiFailure> =
            startResult
    }

    private class FakeAssertionController(
        override val assertionOptions: AssertionOptions,
        private val verifyResult: ApiResult<AccessToken, PasskeyAssertionVerifyApiFailure>
    ) : PasskeyAssertionApi.Controller {
        val verifiedAssertions = mutableListOf<AssertionResult>()
        val canceledReasons = mutableListOf<Reason>()

        override val verify: suspend (assertion: AssertionResult) -> ApiResult<AccessToken, PasskeyAssertionVerifyApiFailure> = { assertion ->
            verifiedAssertions += assertion
            verifyResult
        }

        override val cancel: suspend (reason: Reason) -> ApiResult<Unit, PasskeyAssertionCancelApiFailure> = { reason ->
            canceledReasons += reason
            ApiResult.Success(Unit)
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
