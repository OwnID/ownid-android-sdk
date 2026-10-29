package com.ownid.sdk.ops.impl

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.api.ApiResult
import com.ownid.sdk.api.PasskeyAttestationApi
import com.ownid.sdk.api.PasskeyAttestationCancelApiFailure
import com.ownid.sdk.api.PasskeyAttestationStartApiFailure
import com.ownid.sdk.api.PasskeyAttestationVerifyApiFailure
import com.ownid.sdk.capability.Capability
import com.ownid.sdk.capability.Passkey
import com.ownid.sdk.model.AttestationOptions
import com.ownid.sdk.model.AttestationResponse
import com.ownid.sdk.model.AttestationResult
import com.ownid.sdk.model.Authz
import com.ownid.sdk.model.Availability
import com.ownid.sdk.model.Context
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
    fun unavailableUiFailsBeforeCreatingAChallengeWithOrWithoutPreflight() = runTest {
        for (checkPreflight in listOf(false, true)) {
            val apiController = FakeAttestationController(
                defaultAttestationOptions,
                verifyResult = ApiResult.Success(AttestationResponse(proofToken = proofToken, ownIdData = "{}"))
            )
            val api = FakePasskeyAttestationApi(ApiResult.Success(apiController))
            val ui = FakePasskeyAttestationUi(Passkey.Result.Success(attestationResult()))
            val registry = OperationRegistryImpl()
            val operation = passkeyAttestationOperation(ui, api, registry)
            val params = PasskeyAttestationOperation.Params(loginId = emailLoginId)
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
            assertThat(failure).isInstanceOf(PasskeyAttestationOperationFailure.Integration.ProviderFailed::class.java)
            assertThat(failure.errorCode).isEqualTo(ErrorCode.PasskeyNotCreated)
            assertThat(ui.availabilityParams.single()).isSameInstanceAs(params)
            assertThat(api.params).isEmpty()
            assertThat(ui.requests).isEmpty()
            assertThat(apiController.verifiedAttestations).isEmpty()
            assertThat(apiController.canceledReasons).isEmpty()
            assertThat(registry.current.value).isEmpty()
        }
    }

    @Test
    fun readinessReceivesOriginalNullableParamsWhileApiUsesContext() = runTest {
        for (params in listOf(null, PasskeyAttestationOperation.Params())) {
            val apiController = FakeAttestationController(
                defaultAttestationOptions,
                verifyResult = ApiResult.Success(AttestationResponse(proofToken = proofToken, ownIdData = "{}"))
            )
            val api = FakePasskeyAttestationApi(ApiResult.Success(apiController))
            val ui = FakePasskeyAttestationUi(Passkey.Result.Success(attestationResult()))
            val operation = passkeyAttestationOperation(
                ui = ui,
                api = api,
                context = Context.Builder().apply {
                    authz = Authz.fromToken(accessToken)
                    accountDisplayName = "  Context Attestation User  "
                }.build("passkey-readiness")
            )
            assertThat(operation.availability(params)).isInstanceOf(Availability.Available::class.java)
            val controller = operation.start(params)
            drainOperations()
            controller.whenSettled().assertSuccessResult()

            assertThat(ui.availabilityParams).hasSize(2)
            ui.availabilityParams.forEach { assertThat(it).isSameInstanceAs(params) }
            assertThat(api.params.single()?.accessToken).isEqualTo(accessToken)
            assertThat(api.params.single()?.accountDisplayName).isEqualTo("  Context Attestation User  ")
        }
    }

    @Test
    fun successPassesApiOptionsToCredentialUiAndVerifiesReturnedAttestation() = runTest {
        val options = attestationOptions()
        val attestation = attestationResult()
        val expected = AttestationResponse(proofToken = proofToken, ownIdData = """{"ownId":true}""")
        val apiController = FakeAttestationController(options, verifyResult = ApiResult.Success(expected))
        val ui = FakePasskeyAttestationUi(Passkey.Result.Success(attestation))
        val api = FakePasskeyAttestationApi(ApiResult.Success(apiController))
        val operation = passkeyAttestationOperation(ui = ui, api = api)

        val controller = operation.start(PasskeyAttestationOperation.Params(loginId = emailLoginId, isAutoSelectAllowed = false))
        drainOperations()

        assertThat(controller.whenSettled().assertSuccessResult()).isSameInstanceAs(expected)
        assertThat(api.params.single()?.accountDisplayName).isNull()
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
        registry: OperationRegistryImpl = OperationRegistryImpl(),
        context: Context? = null
    ): PasskeyAttestationOperationImpl =
        PasskeyAttestationOperationImpl(
            operationType = OperationType.PASSKEY_CREATION,
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
        val params = mutableListOf<PasskeyAttestationApi.Params?>()
        override suspend fun start(params: PasskeyAttestationApi.Params?): ApiResult<PasskeyAttestationApi.Controller, PasskeyAttestationStartApiFailure> {
            this.params += params
            return startResult
        }
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
        var available = true
        val availabilityParams = mutableListOf<Capability.Params?>()
        override suspend fun isAvailable(params: Capability.Params?): Boolean {
            availabilityParams += params
            return available
        }

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
