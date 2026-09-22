package com.ownid.sdk.ops.impl

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.api.ApiResult
import com.ownid.sdk.api.OidcApi
import com.ownid.sdk.api.OidcCancelApiFailure
import com.ownid.sdk.api.OidcCompleteApiFailure
import com.ownid.sdk.api.OidcStartApiFailure
import com.ownid.sdk.capability.SignInWithSocial
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.AccessTokenWithUserInfo
import com.ownid.sdk.model.ChallengeId
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.OperationType
import com.ownid.sdk.model.Reason
import com.ownid.sdk.model.SocialChallenge
import com.ownid.sdk.model.SocialProvider
import com.ownid.sdk.model.Timeout
import com.ownid.sdk.ops.SignInWithGoogleOperation
import com.ownid.sdk.ops.SignInWithGoogleOperationFailure
import com.ownid.sdk.ops.ui.SignInWithGoogleUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(InternalOwnIdAPI::class)
// Covers: OPS-120, OPS-230
internal class SignInWithGoogleOperationImplTest {

    @Test
    fun providerSuccessCompletesOidcChallengeWithIdToken() = runTest {
        val expected = AccessTokenWithUserInfo(
            accessToken = AccessToken("google-access-token"),
            loginId = emailLoginId,
            userInfo = mapOf("sub" to "google-user"),
            provider = SocialProvider.GOOGLE
        )
        val apiController = FakeOidcController(completeResult = ApiResult.Success(expected))
        val api = FakeOidcApi(ApiResult.Success(apiController))
        val ui = FakeSignInWithGoogleUi(SignInWithSocial.Result.Success(id = "google-user", idToken = "id-token"))
        val operation = googleOperation(ui = ui, api = api)

        val controller = operation.start(SignInWithGoogleOperation.Params())
        drainOperations()

        assertThat(controller.whenSettled().assertSuccessResult()).isEqualTo(expected)
        assertThat(api.starts.map { it.provider }).containsExactly(SocialProvider.GOOGLE)
        assertThat(ui.signInRequests).containsExactly("web-client-id" to "google-challenge")
        assertThat(apiController.completedTokens).containsExactly("id-token")
        assertThat(apiController.canceledReasons).isEmpty()
    }

    @Test
    fun providerCancelCancelsOidcChallengeAndSettlesCanceled() = runTest {
        val reason = Reason.UserClose()
        val apiController = FakeOidcController(completeResult = ApiResult.Success(googleToken()))
        val operation = googleOperation(
            ui = FakeSignInWithGoogleUi(SignInWithSocial.Result.Canceled(reason)),
            api = FakeOidcApi(ApiResult.Success(apiController))
        )

        val controller = operation.start(SignInWithGoogleOperation.Params())
        drainOperations()

        assertThat(controller.whenSettled().assertCanceledResult()).isSameInstanceAs(reason)
        assertThat(apiController.canceledReasons).containsExactly(reason)
        assertThat(apiController.completedTokens).isEmpty()
    }

    @Test
    fun providerFailureMapsToIntegrationProviderFailed() = runTest {
        val providerError = IllegalStateException("provider failed")
        val apiController = FakeOidcController(completeResult = ApiResult.Success(googleToken()))
        val operation = googleOperation(
            ui = FakeSignInWithGoogleUi(SignInWithSocial.Result.Fail(providerError)),
            api = FakeOidcApi(ApiResult.Success(apiController))
        )

        val controller = operation.start(SignInWithGoogleOperation.Params())
        drainOperations()

        val failure = controller.whenSettled().assertFailureResult()
        assertThat(failure).isInstanceOf(SignInWithGoogleOperationFailure.Integration.ProviderFailed::class.java)
        assertThat((failure as SignInWithGoogleOperationFailure.Integration.ProviderFailed).error).isSameInstanceAs(providerError)
        assertThat(apiController.canceledReasons).hasSize(1)
    }

    @Test
    fun oidcStartFailureSettlesTypedFailureAndClearsRegistry() = runTest {
        val apiFailure = OidcStartApiFailure.BadRequest.InvalidArgument(ErrorCode.InvalidArgument, "OIDC start rejected")
        val registry = OperationRegistryImpl()
        val operation = googleOperation(
            ui = FakeSignInWithGoogleUi(SignInWithSocial.Result.Success(id = "unused", idToken = "unused")),
            api = FakeOidcApi(ApiResult.Failure(apiFailure)),
            registry = registry
        )

        val controller = operation.start(SignInWithGoogleOperation.Params())
        drainOperations()

        val firstResult = controller.whenSettled()
        val failure = firstResult.assertFailureResult()
        assertThat(failure).isInstanceOf(SignInWithGoogleOperationFailure.Input.InvalidRequest::class.java)
        assertThat((failure as SignInWithGoogleOperationFailure.Input.InvalidRequest).apiFailure).isSameInstanceAs(apiFailure)
        assertThat(registry.current.value).isEmpty()
        assertThat(controller.whenSettled()).isSameInstanceAs(firstResult)
    }

    @Test
    fun oidcCompletionFailureSettlesTypedFailureAndClearsRegistry() = runTest {
        val apiFailure = OidcCompleteApiFailure.Forbidden(ErrorCode.Forbidden, "OIDC completion forbidden")
        val apiController = FakeOidcController(completeResult = ApiResult.Failure(apiFailure))
        val registry = OperationRegistryImpl()
        val operation = googleOperation(
            ui = FakeSignInWithGoogleUi(SignInWithSocial.Result.Success(id = "google-user", idToken = "id-token")),
            api = FakeOidcApi(ApiResult.Success(apiController)),
            registry = registry
        )

        val controller = operation.start(SignInWithGoogleOperation.Params())
        drainOperations()

        val firstResult = controller.whenSettled()
        val failure = firstResult.assertFailureResult()
        assertThat(failure).isInstanceOf(SignInWithGoogleOperationFailure.Access.Forbidden::class.java)
        assertThat((failure as SignInWithGoogleOperationFailure.Access.Forbidden).apiFailure).isSameInstanceAs(apiFailure)
        assertThat(apiController.completedTokens).containsExactly("id-token")
        assertThat(apiController.canceledReasons).isEmpty()
        assertThat(registry.current.value).isEmpty()
        assertThat(controller.whenSettled()).isSameInstanceAs(firstResult)
    }

    @Test
    fun providerCancellationPreservesReasonWhenServerCancelReturnsFailure() = runTest {
        val providerReason = Reason.UserClose("provider closed")
        val apiController = FakeOidcController(
            completeResult = ApiResult.Success(googleToken()),
            cancelResult = ApiResult.Failure(OidcCancelApiFailure.Forbidden(ErrorCode.Forbidden, "OIDC cancel forbidden"))
        )
        val registry = OperationRegistryImpl()
        val operation = googleOperation(
            ui = FakeSignInWithGoogleUi(SignInWithSocial.Result.Canceled(providerReason)),
            api = FakeOidcApi(ApiResult.Success(apiController)),
            registry = registry
        )

        val controller = operation.start(SignInWithGoogleOperation.Params())
        drainOperations()

        val firstResult = controller.whenSettled()
        assertThat(firstResult.assertCanceledResult()).isSameInstanceAs(providerReason)
        assertThat(apiController.canceledReasons).containsExactly(providerReason)
        assertThat(registry.current.value).isEmpty()
        assertThat(controller.whenSettled()).isSameInstanceAs(firstResult)

        controller.abort(Reason.Unknown("late abort"))
        drainOperations()
        assertThat(apiController.canceledReasons).containsExactly(providerReason)
        assertThat(controller.whenSettled()).isSameInstanceAs(firstResult)
    }

    @Test
    fun oidcCompletionCancellationSettlesCanceledWhenServerCancelIsAlsoCanceled() = runTest {
        val apiController = FakeOidcController(
            completeResult = ApiResult.Success(googleToken()),
            completeError = CancellationException("completion canceled"),
            cancelError = CancellationException("cancel canceled")
        )
        val registry = OperationRegistryImpl()
        val operation = googleOperation(
            ui = FakeSignInWithGoogleUi(SignInWithSocial.Result.Success(id = "google-user", idToken = "id-token")),
            api = FakeOidcApi(ApiResult.Success(apiController)),
            registry = registry
        )

        val controller = operation.start(SignInWithGoogleOperation.Params())
        drainOperations()

        val firstResult = controller.whenSettled()
        val reason = firstResult.assertCanceledResult()
        assertThat(reason).isInstanceOf(Reason.SystemError::class.java)
        assertThat(apiController.completedTokens).containsExactly("id-token")
        assertThat(apiController.canceledReasons.single()).isSameInstanceAs(reason)
        assertThat(registry.current.value).isEmpty()
        assertThat(controller.whenSettled()).isSameInstanceAs(firstResult)
    }

    private fun TestScope.googleOperation(
        ui: SignInWithGoogleUi,
        api: OidcApi,
        registry: OperationRegistryImpl = OperationRegistryImpl()
    ): SignInWithGoogleOperationImpl =
        SignInWithGoogleOperationImpl(
            operationType = OperationType.OIDC_AUTHENTICATION_GOOGLE,
            operationRegistry = registry,
            ui = ui,
            api = api,
            defaultDispatcher = operationTestDispatcher(),
            instanceJob = Job(),
            context = null,
            unsatisfiedDependencies = null,
            logger = null
        )

    private fun googleToken(): AccessTokenWithUserInfo =
        AccessTokenWithUserInfo(
            accessToken = AccessToken("google-access-token"),
            loginId = LoginId("person@example.com", LoginId.Type.Email),
            userInfo = emptyMap(),
            provider = SocialProvider.GOOGLE
        )

    private class FakeOidcApi(
        private val startResult: ApiResult<OidcApi.Controller, OidcStartApiFailure>
    ) : OidcApi {
        val starts = mutableListOf<OidcApi.Params>()

        override suspend fun start(params: OidcApi.Params?): ApiResult<OidcApi.Controller, OidcStartApiFailure> {
            starts += requireNotNull(params)
            return startResult
        }
    }

    private class FakeOidcController(
        private val completeResult: ApiResult<AccessTokenWithUserInfo, OidcCompleteApiFailure>,
        private val cancelResult: ApiResult<Unit, OidcCancelApiFailure> = ApiResult.Success(Unit),
        private val completeError: Throwable? = null,
        private val cancelError: Throwable? = null
    ) : OidcApi.Controller {
        override val challenge = SocialChallenge(
            challengeId = ChallengeId("google-challenge"),
            timeout = Timeout(60_000),
            clientId = "web-client-id"
        )
        val completedTokens = mutableListOf<String>()
        val canceledReasons = mutableListOf<Reason>()

        override val completeWithToken: suspend (idToken: String) -> ApiResult<AccessTokenWithUserInfo, OidcCompleteApiFailure> = { idToken ->
            completedTokens += idToken
            completeError?.let { throw it }
            completeResult
        }
        override val completeWithCode: suspend (code: String) -> ApiResult<AccessTokenWithUserInfo, OidcCompleteApiFailure> = {
            error("Google operation should complete with ID token")
        }
        override val cancel: suspend (reason: Reason) -> ApiResult<Unit, OidcCancelApiFailure> = { reason ->
            canceledReasons += reason
            cancelError?.let { throw it }
            cancelResult
        }
    }

    private class FakeSignInWithGoogleUi(private val result: SignInWithSocial.Result) : SignInWithGoogleUi {
        val signInRequests = mutableListOf<Pair<String, String?>>()
        var cancelCount = 0

        override suspend fun signIn(webClientId: String, nonce: String?): SignInWithSocial.Result {
            signInRequests += webClientId to nonce
            return result
        }

        override suspend fun cancel() {
            cancelCount++
        }
    }
}
