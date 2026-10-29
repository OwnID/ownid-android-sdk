package com.ownid.sdk.ops.impl

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.api.ApiResult
import com.ownid.sdk.api.PasskeyAssertionApi
import com.ownid.sdk.api.PasskeyAssertionCancelApiFailure
import com.ownid.sdk.api.PasskeyAssertionStartApiFailure
import com.ownid.sdk.api.PasskeyAssertionVerifyApiFailure
import com.ownid.sdk.capability.Capability
import com.ownid.sdk.capability.Passkey
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.AssertionOptions
import com.ownid.sdk.model.AssertionResult
import com.ownid.sdk.model.Authz
import com.ownid.sdk.model.Availability
import com.ownid.sdk.model.Context
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
    fun unavailableUiFailsBeforeCreatingAChallengeWithOrWithoutPreflight() = runTest {
        for (checkPreflight in listOf(false, true)) {
            val apiController = FakeAssertionController(assertionOptions(), verifyResult = ApiResult.Success(accessToken))
            val api = FakePasskeyAssertionApi(ApiResult.Success(apiController))
            val ui = FakePasskeyAssertionUi(Passkey.Result.Success(assertionResult()))
            val registry = OperationRegistryImpl()
            val operation = passkeyAssertionOperation(ui, api, registry)
            val params = PasskeyAssertionOperation.Params(loginId = emailLoginId)
            if (checkPreflight) {
                assertThat(operation.availability(params)).isInstanceOf(Availability.Available::class.java)
            }
            ui.available = false
            if (checkPreflight) {
                assertThat(operation.availability(params)).isInstanceOf(Availability.Unavailable::class.java)
            }
            ui.availabilityParams.clear()

            val controller = operation.start(params)
            drainOperations()

            val failure = controller.whenSettled().assertFailureResult()
            assertThat(failure).isInstanceOf(PasskeyAssertionOperationFailure.Integration.ProviderFailed::class.java)
            assertThat(failure.errorCode).isEqualTo(ErrorCode.IntegrationError)
            assertThat(ui.availabilityParams.single()).isSameInstanceAs(params)
            assertThat(api.params).isEmpty()
            assertThat(ui.options).isEmpty()
            assertThat(apiController.verifiedAssertions).isEmpty()
            assertThat(apiController.canceledReasons).isEmpty()
            assertThat(registry.current.value).isEmpty()
        }
    }

    @Test
    fun readinessReceivesOriginalNullableParamsWhileApiUsesContext() = runTest {
        for (params in listOf(null, PasskeyAssertionOperation.Params())) {
            val apiController = FakeAssertionController(assertionOptions(), verifyResult = ApiResult.Success(accessToken))
            val api = FakePasskeyAssertionApi(ApiResult.Success(apiController))
            val ui = FakePasskeyAssertionUi(Passkey.Result.Success(assertionResult()))
            val operation = passkeyAssertionOperation(
                ui = ui,
                api = api,
                context = Context.Builder().apply { authz = Authz.fromToken(accessToken) }.build("passkey-readiness")
            )
            assertThat(operation.availability(params)).isInstanceOf(Availability.Available::class.java)
            val controller = operation.start(params)
            drainOperations()
            controller.whenSettled().assertSuccessResult()

            assertThat(ui.availabilityParams).hasSize(2)
            ui.availabilityParams.forEach { assertThat(it).isSameInstanceAs(params) }
            assertThat(api.params.single()?.accessToken).isEqualTo(accessToken)
        }
    }

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
        registry: OperationRegistryImpl = OperationRegistryImpl(),
        context: Context? = null
    ): PasskeyAssertionOperationImpl =
        PasskeyAssertionOperationImpl(
            operationType = OperationType.PASSKEY_AUTH,
            operationRegistry = registry,
            ui = ui,
            api = api,
            defaultDispatcher = operationTestDispatcher(),
            instanceJob = Job(),
            context = context,
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
        val params = mutableListOf<PasskeyAssertionApi.Params?>()
        override suspend fun start(params: PasskeyAssertionApi.Params?): ApiResult<PasskeyAssertionApi.Controller, PasskeyAssertionStartApiFailure> {
            this.params += params
            return startResult
        }
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
        var available = true
        val availabilityParams = mutableListOf<Capability.Params?>()
        override suspend fun isAvailable(params: Capability.Params?): Boolean {
            availabilityParams += params
            return available
        }

        val options = mutableListOf<AssertionOptions>()

        override suspend fun getCredential(assertionOptions: AssertionOptions): Passkey.Result<AssertionResult> {
            options += assertionOptions
            return result
        }
    }
}
