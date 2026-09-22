package com.ownid.sdk.ops.impl

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.api.ApiResult
import com.ownid.sdk.api.PasskeyAttestationApi
import com.ownid.sdk.api.PasskeyAttestationCancelApiFailure
import com.ownid.sdk.api.PasskeyAttestationStartApiFailure
import com.ownid.sdk.api.PasskeyAttestationVerifyApiFailure
import com.ownid.sdk.capability.Passkey
import com.ownid.sdk.model.AttestationOptions
import com.ownid.sdk.model.AttestationResponse
import com.ownid.sdk.model.AttestationResult
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.OperationType
import com.ownid.sdk.model.Reason
import com.ownid.sdk.ops.PasskeyAttestationOperation
import com.ownid.sdk.ops.PasskeyAttestationOperationFailure
import com.ownid.sdk.ops.ui.PasskeyAttestationUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(InternalOwnIdAPI::class)
// Covers: OPS-160, OPS-190, OPS-240
internal class PasskeyAttestationOperationImplTest {

    @Test
    fun successPassesApiOptionsToCredentialUiAndVerifiesReturnedAttestation() = runTest {
        val options = attestationOptions()
        val attestation = attestationResult()
        val expected = AttestationResponse(proofToken = proofToken, ownIdData = """{"ownId":true}""")
        val apiController = FakeAttestationController(options, verifyResult = ApiResult.Success(expected))
        val ui = FakePasskeyAttestationUi(Passkey.Result.Success(attestation))
        val operation = passkeyAttestationOperation(ui = ui, api = FakePasskeyAttestationApi(ApiResult.Success(apiController)))

        val controller = operation.start(PasskeyAttestationOperation.Params(loginId = emailLoginId, isAutoSelectAllowed = false))
        drainOperations()

        assertThat(controller.whenSettled().assertSuccessResult()).isSameInstanceAs(expected)
        assertThat(ui.requests).containsExactly(options to false)
        assertThat(apiController.verifiedAttestations).containsExactly(attestation)
        assertThat(apiController.canceledReasons).isEmpty()
    }

    @Test
    fun credentialCancellationCancelsServerChallengeAndSettlesCanceled() = runTest {
        val cancelReason = Reason.UserClose()
        val apiController = FakeAttestationController(
            defaultAttestationOptions,
            verifyResult = ApiResult.Success(AttestationResponse(proofToken = proofToken, ownIdData = "{}"))
        )
        val operation = passkeyAttestationOperation(
            ui = FakePasskeyAttestationUi(Passkey.Result.Canceled(cancelReason)),
            api = FakePasskeyAttestationApi(ApiResult.Success(apiController))
        )

        val controller = operation.start(PasskeyAttestationOperation.Params(loginId = emailLoginId))
        drainOperations()

        assertThat(controller.whenSettled().assertCanceledResult()).isSameInstanceAs(cancelReason)
        assertThat(apiController.canceledReasons).containsExactly(cancelReason)
        assertThat(apiController.verifiedAttestations).isEmpty()
    }

    @Test
    fun credentialFailuresCancelChallengeAndSettleProviderFailureWithCleanup() = runTest {
        assertCredentialFailure(Passkey.NoCredentialError())
        assertCredentialFailure(IllegalStateException("credential provider failed"))
    }

    @Test
    fun credentialFailureKeepsProviderFailureWhenServerCancelReturnsFailure() = runTest {
        val error = IllegalStateException("credential provider failed")
        assertCredentialFailure(
            error = error,
            cancelResult = ApiResult.Failure(
                PasskeyAttestationCancelApiFailure.BadRequest.InvalidArgument(ErrorCode.InvalidArgument, "attestation cancel rejected")
            )
        )
    }

    @Test
    fun credentialFailureKeepsProviderFailureWhenServerCancelThrowsCancellationException() = runTest {
        val error = IllegalStateException("credential provider failed")
        assertCredentialFailure(
            error = error,
            cancelError = CancellationException("attestation cancel canceled")
        )
    }

    @Test
    fun backendVerifyFailureAfterCredentialSuccessSettlesTypedFailureAndClearsRegistry() = runTest {
        val attestation = attestationResult()
        val apiFailure = PasskeyAttestationVerifyApiFailure.Unauthorized(ErrorCode.Unauthorized, "attestation verify rejected")
        val apiController = FakeAttestationController(defaultAttestationOptions, verifyResult = ApiResult.Failure(apiFailure))
        val registry = OperationRegistryImpl()
        val operation = passkeyAttestationOperation(
            registry = registry,
            ui = FakePasskeyAttestationUi(Passkey.Result.Success(attestation)),
            api = FakePasskeyAttestationApi(ApiResult.Success(apiController))
        )

        val controller = operation.start(PasskeyAttestationOperation.Params(loginId = emailLoginId))
        drainOperations()

        val firstResult = controller.whenSettled()
        val failure = firstResult.assertFailureResult()

        assertThat(failure).isInstanceOf(PasskeyAttestationOperationFailure.Access.Unauthorized::class.java)
        assertThat((failure as PasskeyAttestationOperationFailure.Access.Unauthorized).apiFailure).isSameInstanceAs(apiFailure)
        assertThat(apiController.verifiedAttestations).containsExactly(attestation)
        assertThat(apiController.canceledReasons).isEmpty()
        assertThat(registry.current.value).isEmpty()
        assertThat(controller.whenSettled()).isSameInstanceAs(firstResult)

        controller.abort(Reason.UserClose())
        drainOperations()

        assertThat(registry.current.value).isEmpty()
        assertThat(controller.whenSettled()).isSameInstanceAs(firstResult)
    }

    private val defaultAttestationOptions = attestationOptions()

    private fun TestScope.passkeyAttestationOperation(
        ui: PasskeyAttestationUi,
        api: PasskeyAttestationApi,
        registry: OperationRegistryImpl = OperationRegistryImpl()
    ): PasskeyAttestationOperationImpl =
        PasskeyAttestationOperationImpl(
            operationType = OperationType.PASSKEY_CREATION,
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
        cancelResult: ApiResult<Unit, PasskeyAttestationCancelApiFailure> = ApiResult.Success(Unit),
        cancelError: Throwable? = null
    ) {
        val apiController = FakeAttestationController(
            defaultAttestationOptions,
            verifyResult = ApiResult.Success(AttestationResponse(proofToken = proofToken, ownIdData = "{}")),
            cancelResult = cancelResult,
            cancelError = cancelError
        )
        val registry = OperationRegistryImpl()
        val operation = passkeyAttestationOperation(
            registry = registry,
            ui = FakePasskeyAttestationUi(Passkey.Result.Fail(error)),
            api = FakePasskeyAttestationApi(ApiResult.Success(apiController))
        )

        val controller = operation.start(PasskeyAttestationOperation.Params(loginId = emailLoginId))
        drainOperations()

        val firstResult = controller.whenSettled()
        val failure = firstResult.assertFailureResult()
        assertThat(failure).isInstanceOf(PasskeyAttestationOperationFailure.Integration.ProviderFailed::class.java)
        val providerFailed = failure as PasskeyAttestationOperationFailure.Integration.ProviderFailed
        assertThat(providerFailed.errorCode).isEqualTo(ErrorCode.PasskeyNotCreated)
        assertThat(providerFailed.error).isSameInstanceAs(error)
        assertThat(apiController.canceledReasons).hasSize(1)
        assertThat(apiController.canceledReasons.single()).isInstanceOf(Reason.SystemError::class.java)
        assertThat(apiController.verifiedAttestations).isEmpty()
        assertThat(registry.current.value).isEmpty()
        assertThat(controller.whenSettled()).isSameInstanceAs(firstResult)

        controller.abort(Reason.UserClose())
        drainOperations()

        assertThat(apiController.canceledReasons).hasSize(1)
        assertThat(registry.current.value).isEmpty()
        assertThat(controller.whenSettled()).isSameInstanceAs(firstResult)
    }

    private class FakePasskeyAttestationApi(
        private val startResult: ApiResult<PasskeyAttestationApi.Controller, PasskeyAttestationStartApiFailure>
    ) : PasskeyAttestationApi {
        override suspend fun start(params: PasskeyAttestationApi.Params?): ApiResult<PasskeyAttestationApi.Controller, PasskeyAttestationStartApiFailure> =
            startResult
    }

    private class FakeAttestationController(
        override val attestationOptions: AttestationOptions,
        private val verifyResult: ApiResult<AttestationResponse, PasskeyAttestationVerifyApiFailure>,
        private val cancelResult: ApiResult<Unit, PasskeyAttestationCancelApiFailure> = ApiResult.Success(Unit),
        private val cancelError: Throwable? = null
    ) : PasskeyAttestationApi.Controller {
        val verifiedAttestations = mutableListOf<AttestationResult>()
        val canceledReasons = mutableListOf<Reason>()

        override val verify: suspend (attestation: AttestationResult) -> ApiResult<AttestationResponse, PasskeyAttestationVerifyApiFailure> = { attestation ->
            verifiedAttestations += attestation
            verifyResult
        }
        override val cancel: suspend (reason: Reason) -> ApiResult<Unit, PasskeyAttestationCancelApiFailure> = { reason ->
            canceledReasons += reason
            cancelError?.let { throw it }
            cancelResult
        }
    }

    private class FakePasskeyAttestationUi(
        private val result: Passkey.Result<AttestationResult>
    ) : PasskeyAttestationUi {
        val requests = mutableListOf<Pair<AttestationOptions, Boolean>>()

        override suspend fun createCredential(
            attestationOptions: AttestationOptions,
            isAutoSelectAllowed: Boolean
        ): Passkey.Result<AttestationResult> {
            requests += attestationOptions to isAutoSelectAllowed
            return result
        }
    }
}
