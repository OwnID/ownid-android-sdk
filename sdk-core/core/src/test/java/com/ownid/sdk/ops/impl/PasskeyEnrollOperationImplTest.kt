package com.ownid.sdk.ops.impl

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.api.ApiResult
import com.ownid.sdk.api.PasskeyEnrollApi
import com.ownid.sdk.api.PasskeyEnrollApiFailure
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.OperationType
import com.ownid.sdk.model.Reason
import com.ownid.sdk.ops.PasskeyEnrollOperation
import com.ownid.sdk.ops.PasskeyEnrollOperationFailure
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(InternalOwnIdAPI::class)
// Covers: OPS-130, OPS-200
internal class PasskeyEnrollOperationImplTest {

    @Test
    fun paramsAccessTokenAndProofTokenAreSentToApiBeforeContextToken() = runTest {
        val paramsToken = AccessToken("params-access-token")
        val api = FakePasskeyEnrollApi(ApiResult.Success(Unit))
        val operation = passkeyEnrollOperation(api = api, context = accessTokenContext(contextAccessToken))

        val controller = operation.start(PasskeyEnrollOperation.Params(proofToken = proofToken, accessToken = paramsToken))
        drainOperations()

        controller.whenSettled().assertSuccessResult()
        val start = api.starts.assertSingle()
        assertThat(start.proofToken).isEqualTo(proofToken)
        assertThat(start.accessToken).isEqualTo(paramsToken)
    }

    @Test
    fun contextAccessTokenIsUsedWhenParamsOmitAccessToken() = runTest {
        val api = FakePasskeyEnrollApi(ApiResult.Success(Unit))
        val operation = passkeyEnrollOperation(api = api, context = accessTokenContext(contextAccessToken))

        val controller = operation.start(PasskeyEnrollOperation.Params(proofToken = proofToken))
        drainOperations()

        controller.whenSettled().assertSuccessResult()
        assertThat(api.starts.assertSingle().accessToken).isEqualTo(contextAccessToken)
    }

    @Test
    fun missingTokensFailBeforeApiCall() = runTest {
        val api = FakePasskeyEnrollApi(ApiResult.Success(Unit))
        val operation = passkeyEnrollOperation(api = api, context = null)

        val controller = operation.start(null)
        drainOperations()

        assertThat(controller.whenSettled().assertFailureResult()).isInstanceOf(PasskeyEnrollOperationFailure.Input.MissingTokens::class.java)
        assertThat(api.starts).isEmpty()
    }

    @Test
    fun backendEnrollFailureAfterProofSuccessSettlesTypedFailureAndClearsRegistry() = runTest {
        val apiFailure = PasskeyEnrollApiFailure.Forbidden(ErrorCode.Forbidden, "passkey enroll rejected")
        val api = FakePasskeyEnrollApi(ApiResult.Failure(apiFailure))
        val registry = OperationRegistryImpl()
        val operation = passkeyEnrollOperation(api = api, registry = registry, context = accessTokenContext(contextAccessToken))

        val controller = operation.start(PasskeyEnrollOperation.Params(proofToken = proofToken))
        drainOperations()

        val firstResult = controller.whenSettled()
        val failure = firstResult.assertFailureResult()

        assertThat(failure).isInstanceOf(PasskeyEnrollOperationFailure.Access.Forbidden::class.java)
        assertThat((failure as PasskeyEnrollOperationFailure.Access.Forbidden).apiFailure).isSameInstanceAs(apiFailure)
        assertThat(api.starts.assertSingle().accessToken).isEqualTo(contextAccessToken)
        assertThat(registry.current.value).isEmpty()
        assertThat(controller.whenSettled()).isSameInstanceAs(firstResult)

        controller.abort(Reason.UserClose())
        drainOperations()

        assertThat(registry.current.value).isEmpty()
        assertThat(controller.whenSettled()).isSameInstanceAs(firstResult)
    }

    private fun TestScope.passkeyEnrollOperation(
        api: PasskeyEnrollApi,
        registry: OperationRegistryImpl = OperationRegistryImpl(),
        context: com.ownid.sdk.model.Context? = null
    ): PasskeyEnrollOperationImpl =
        PasskeyEnrollOperationImpl(
            operationType = OperationType.PASSKEY_ENROLLMENT,
            operationRegistry = registry,
            api = api,
            defaultDispatcher = operationTestDispatcher(),
            instanceJob = Job(),
            context = context,
            unsatisfiedDependencies = null,
            logger = null
        )

    private class FakePasskeyEnrollApi(private val result: ApiResult<Unit, PasskeyEnrollApiFailure>) : PasskeyEnrollApi {
        val starts = mutableListOf<PasskeyEnrollApi.Params>()

        override suspend fun start(params: PasskeyEnrollApi.Params): ApiResult<Unit, PasskeyEnrollApiFailure> {
            starts += params
            return result
        }
    }
}
