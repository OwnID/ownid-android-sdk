package com.ownid.sdk.ops.impl

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.api.ApiResult
import com.ownid.sdk.api.DiscoverApi
import com.ownid.sdk.api.DiscoverApiFailure
import com.ownid.sdk.api.LoginApi
import com.ownid.sdk.api.LoginApiFailure
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LoginResponse
import com.ownid.sdk.model.OperationType
import com.ownid.sdk.ops.LoginOperation
import com.ownid.sdk.ops.LoginOperationFailure
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(InternalOwnIdAPI::class)
// Covers: OPS-080
internal class LoginOperationImplTest {

    @Test
    fun startWithAccessTokenCompletesFromLoginApiAndDoesNotDiscover() = runTest {
        val loginApi = FakeLoginApi(ApiResult.Success(LoginResponse.Success(accessToken, sessionPayload = """{"session":true}""")))
        val discoverApi = FakeDiscoverApi(ApiResult.Success(LoginResponse.AccountNotFound("unused")))
        val operation = loginOperation(loginApi = loginApi, discoverApi = discoverApi)

        val controller = operation.start(LoginOperation.Params(loginId = emailLoginId, accessToken = accessToken))
        drainOperations()

        assertThat(controller.whenSettled().assertSuccessResult()).isInstanceOf(LoginResponse.Success::class.java)
        assertThat(loginApi.starts.map { it.accessToken }).containsExactly(accessToken)
        assertThat(discoverApi.starts).isEmpty()
    }

    @Test
    fun discoverInvalidLoginIdFailureMapsToTerminalInputFailure() = runTest {
        val apiFailure = DiscoverApiFailure.BadRequest.InvalidLoginId(
            errorCode = ErrorCode.LoginIdValidationFailed,
            message = "invalid login id",
            loginId = emailLoginId,
            regex = ".*"
        )
        val operation = loginOperation(discoverApi = FakeDiscoverApi(ApiResult.Failure(apiFailure)))

        val controller = operation.start(LoginOperation.Params(loginId = emailLoginId))
        drainOperations()

        val failure = controller.whenSettled().assertFailureResult()
        assertThat(failure).isInstanceOf(LoginOperationFailure.Input.InvalidLoginId::class.java)
        assertThat((failure as LoginOperationFailure.Input.InvalidLoginId).apiFailure).isSameInstanceAs(apiFailure)
    }

    private fun TestScope.loginOperation(
        loginApi: LoginApi = FakeLoginApi(ApiResult.Success(LoginResponse.Success(AccessToken("login-token"), "{}"))),
        discoverApi: DiscoverApi = FakeDiscoverApi(ApiResult.Success(LoginResponse.AccountNotFound("not found")))
    ): LoginOperationImpl =
        LoginOperationImpl(
            operationType = OperationType.SESSION_CREATION,
            operationRegistry = OperationRegistryImpl(),
            loginIdValidator = TestLoginIdValidator(),
            loginApi = loginApi,
            discoverApi = discoverApi,
            defaultDispatcher = operationTestDispatcher(),
            instanceJob = Job(),
            context = null,
            unsatisfiedDependencies = null,
            logger = null
        )

    private class FakeLoginApi(private val result: ApiResult<LoginResponse, LoginApiFailure>) : LoginApi {
        val starts = mutableListOf<LoginApi.Params>()

        override suspend fun start(params: LoginApi.Params?): ApiResult<LoginResponse, LoginApiFailure> {
            starts += requireNotNull(params)
            return result
        }
    }

    private class FakeDiscoverApi(private val result: ApiResult<LoginResponse, DiscoverApiFailure>) : DiscoverApi {
        val starts = mutableListOf<DiscoverApi.Params>()

        override suspend fun start(params: DiscoverApi.Params?): ApiResult<LoginResponse, DiscoverApiFailure> {
            starts += requireNotNull(params)
            return result
        }
    }
}
