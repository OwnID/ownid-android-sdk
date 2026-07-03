package com.ownid.sdk.api.impl.enroll

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.api.ApiResult
import com.ownid.sdk.api.EmailEnrollApi
import com.ownid.sdk.api.EmailEnrollApiFailure
import com.ownid.sdk.api.PasskeyEnrollApi
import com.ownid.sdk.api.PasskeyEnrollApiFailure
import com.ownid.sdk.api.PhoneEnrollApi
import com.ownid.sdk.api.PhoneEnrollApiFailure
import com.ownid.sdk.api.impl.ApiCallInterceptor
import com.ownid.sdk.api.impl.FixedApiBaseUrl
import com.ownid.sdk.api.impl.RecordingNetwork
import com.ownid.sdk.api.impl.TestDispatcherProvider
import com.ownid.sdk.api.impl.apiSuccess
import com.ownid.sdk.api.impl.assertJsonPostRequest
import com.ownid.sdk.api.impl.assertUnitSuccess
import com.ownid.sdk.api.impl.failureValue
import com.ownid.sdk.api.impl.jsonBody
import com.ownid.sdk.api.impl.jsonString
import com.ownid.sdk.capability.Network
import com.ownid.sdk.capability.impl.SerializationImpl
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.Authz
import com.ownid.sdk.model.Context
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.ProofToken
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
// Covers: API-060
internal class EnrollDirectApiContractTest {

    private val serialization = SerializationImpl()
    private val baseUrl = "https://auth.example.test/api/".toHttpUrl()
    private val traceParent = "00-11111111111111111111111111111111-2222222222222222-01"

    @Test
    fun enrollApisUseParamsProofTokenAndParamsAccessTokenBeforeContextAccessToken(): Unit = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val context = context(Authz.fromToken("context-access-token"))

        val emailNetwork = RecordingNetwork().apply { enqueue { noContent(it) } }
        emailApi(emailNetwork, dispatcher, context).start(
            EmailEnrollApi.Params(
                proofToken = ProofToken("email-proof-token"),
                accessToken = AccessToken("email-param-access-token"),
                traceParent = traceParent
            )
        ).assertUnitSuccess()
        assertEnrollRequest(
            request = emailNetwork.requests.single(),
            url = "https://auth.example.test/api/verifications/email/enroll",
            proofToken = "email-proof-token",
            authorization = "Bearer email-param-access-token"
        )

        val phoneNetwork = RecordingNetwork().apply { enqueue { noContent(it) } }
        phoneApi(phoneNetwork, dispatcher, context).start(
            PhoneEnrollApi.Params(
                proofToken = ProofToken("phone-proof-token"),
                accessToken = AccessToken("phone-param-access-token"),
                traceParent = traceParent
            )
        ).assertUnitSuccess()
        assertEnrollRequest(
            request = phoneNetwork.requests.single(),
            url = "https://auth.example.test/api/verifications/phone/enroll",
            proofToken = "phone-proof-token",
            authorization = "Bearer phone-param-access-token"
        )

        val passkeyNetwork = RecordingNetwork().apply { enqueue { noContent(it) } }
        passkeyApi(passkeyNetwork, dispatcher, context).start(
            PasskeyEnrollApi.Params(
                proofToken = ProofToken("passkey-proof-token"),
                accessToken = AccessToken("passkey-param-access-token"),
                traceParent = traceParent
            )
        ).assertUnitSuccess()
        assertEnrollRequest(
            request = passkeyNetwork.requests.single(),
            url = "https://auth.example.test/api/passkeys/attestation/enroll",
            proofToken = "passkey-proof-token",
            authorization = "Bearer passkey-param-access-token"
        )
    }

    @Test
    fun enrollApisUseContextAccessTokenWhenParamsOmitAccessToken(): Unit = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val context = context(Authz.fromToken("context-access-token"))

        val emailNetwork = RecordingNetwork().apply { enqueue { noContent(it) } }
        emailApi(emailNetwork, dispatcher, context).start(
            EmailEnrollApi.Params(proofToken = ProofToken("email-proof-token"), traceParent = traceParent)
        ).assertUnitSuccess()
        assertEnrollRequest(
            request = emailNetwork.requests.single(),
            url = "https://auth.example.test/api/verifications/email/enroll",
            proofToken = "email-proof-token",
            authorization = "Bearer context-access-token"
        )

        val phoneNetwork = RecordingNetwork().apply { enqueue { noContent(it) } }
        phoneApi(phoneNetwork, dispatcher, context).start(
            PhoneEnrollApi.Params(proofToken = ProofToken("phone-proof-token"), traceParent = traceParent)
        ).assertUnitSuccess()
        assertEnrollRequest(
            request = phoneNetwork.requests.single(),
            url = "https://auth.example.test/api/verifications/phone/enroll",
            proofToken = "phone-proof-token",
            authorization = "Bearer context-access-token"
        )

        val passkeyNetwork = RecordingNetwork().apply { enqueue { noContent(it) } }
        passkeyApi(passkeyNetwork, dispatcher, context).start(
            PasskeyEnrollApi.Params(proofToken = ProofToken("passkey-proof-token"), traceParent = traceParent)
        ).assertUnitSuccess()
        assertEnrollRequest(
            request = passkeyNetwork.requests.single(),
            url = "https://auth.example.test/api/passkeys/attestation/enroll",
            proofToken = "passkey-proof-token",
            authorization = "Bearer context-access-token"
        )
    }

    @Test
    fun enrollApisReturnInvalidArgumentWithoutEffectiveAccessTokenBeforeNetworkOrInterceptors(): Unit = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)

        val emailNetwork = RecordingNetwork()
        val emailInterceptor = RecordingApiCallInterceptor()
        val emailFailure = emailApi(emailNetwork, dispatcher, interceptor = emailInterceptor)
            .start(EmailEnrollApi.Params(proofToken = ProofToken("email-proof-token"), traceParent = traceParent))
            .failureValue()
        assertThat(emailFailure).isInstanceOf(EmailEnrollApiFailure.BadRequest.InvalidArgument::class.java)
        val emailInvalidArgument = emailFailure as EmailEnrollApiFailure.BadRequest.InvalidArgument
        assertThat(emailInvalidArgument.errorCode).isEqualTo(ErrorCode.InvalidArgument)
        assertNoRequestExecution(emailNetwork, emailInterceptor)

        val phoneNetwork = RecordingNetwork()
        val phoneInterceptor = RecordingApiCallInterceptor()
        val phoneFailure = phoneApi(phoneNetwork, dispatcher, interceptor = phoneInterceptor)
            .start(PhoneEnrollApi.Params(proofToken = ProofToken("phone-proof-token"), traceParent = traceParent))
            .failureValue()
        assertThat(phoneFailure).isInstanceOf(PhoneEnrollApiFailure.BadRequest.InvalidArgument::class.java)
        val phoneInvalidArgument = phoneFailure as PhoneEnrollApiFailure.BadRequest.InvalidArgument
        assertThat(phoneInvalidArgument.errorCode).isEqualTo(ErrorCode.InvalidArgument)
        assertNoRequestExecution(phoneNetwork, phoneInterceptor)

        val passkeyNetwork = RecordingNetwork()
        val passkeyInterceptor = RecordingApiCallInterceptor()
        val passkeyFailure = passkeyApi(passkeyNetwork, dispatcher, interceptor = passkeyInterceptor)
            .start(PasskeyEnrollApi.Params(proofToken = ProofToken("passkey-proof-token"), traceParent = traceParent))
            .failureValue()
        assertThat(passkeyFailure).isInstanceOf(PasskeyEnrollApiFailure.BadRequest.InvalidArgument::class.java)
        val passkeyInvalidArgument = passkeyFailure as PasskeyEnrollApiFailure.BadRequest.InvalidArgument
        assertThat(passkeyInvalidArgument.errorCode).isEqualTo(ErrorCode.InvalidArgument)
        assertNoRequestExecution(passkeyNetwork, passkeyInterceptor)
    }

    @Test
    fun callerCancellationDuringEnrollApisPropagatesWithoutServerCancel(): Unit = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val context = context(Authz.fromToken("context-access-token"))

        val emailNetwork = RecordingNetwork().apply {
            enqueue {
                awaitCancellation()
            }
        }
        val emailJob = launch {
            emailApi(emailNetwork, dispatcher, context).start(EmailEnrollApi.Params(proofToken = ProofToken("email-proof-token"), traceParent = traceParent))
        }

        runCurrent()
        assertThat(emailNetwork.requestPaths()).containsExactly("/api/verifications/email/enroll")
        emailJob.cancelAndJoin()
        assertThat(emailJob.isCancelled).isTrue()
        assertThat(emailNetwork.requestPaths()).containsExactly("/api/verifications/email/enroll")

        val phoneNetwork = RecordingNetwork().apply {
            enqueue {
                awaitCancellation()
            }
        }
        val phoneJob = launch {
            phoneApi(phoneNetwork, dispatcher, context).start(PhoneEnrollApi.Params(proofToken = ProofToken("phone-proof-token"), traceParent = traceParent))
        }

        runCurrent()
        assertThat(phoneNetwork.requestPaths()).containsExactly("/api/verifications/phone/enroll")
        phoneJob.cancelAndJoin()
        assertThat(phoneJob.isCancelled).isTrue()
        assertThat(phoneNetwork.requestPaths()).containsExactly("/api/verifications/phone/enroll")

        val passkeyNetwork = RecordingNetwork().apply {
            enqueue {
                awaitCancellation()
            }
        }
        val passkeyJob = launch {
            passkeyApi(passkeyNetwork, dispatcher, context).start(
                PasskeyEnrollApi.Params(proofToken = ProofToken("passkey-proof-token"), traceParent = traceParent)
            )
        }

        runCurrent()
        assertThat(passkeyNetwork.requestPaths()).containsExactly("/api/passkeys/attestation/enroll")
        passkeyJob.cancelAndJoin()
        assertThat(passkeyJob.isCancelled).isTrue()
        assertThat(passkeyNetwork.requestPaths()).containsExactly("/api/passkeys/attestation/enroll")
    }

    private fun emailApi(
        network: Network,
        dispatcher: CoroutineDispatcher,
        context: Context? = null,
        interceptor: ApiCallInterceptor? = null
    ): EmailEnrollApi =
        EmailEnrollApiImpl(
            apiBaseUrl = FixedApiBaseUrl(baseUrl),
            network = network,
            serialization = serialization,
            dispatcherProvider = TestDispatcherProvider(dispatcher),
            context = context,
            interceptor = interceptor
        )

    private fun phoneApi(
        network: Network,
        dispatcher: CoroutineDispatcher,
        context: Context? = null,
        interceptor: ApiCallInterceptor? = null
    ): PhoneEnrollApi =
        PhoneEnrollApiImpl(
            apiBaseUrl = FixedApiBaseUrl(baseUrl),
            network = network,
            serialization = serialization,
            dispatcherProvider = TestDispatcherProvider(dispatcher),
            context = context,
            interceptor = interceptor
        )

    private fun passkeyApi(
        network: Network,
        dispatcher: CoroutineDispatcher,
        context: Context? = null,
        interceptor: ApiCallInterceptor? = null
    ): PasskeyEnrollApi =
        PasskeyEnrollApiImpl(
            apiBaseUrl = FixedApiBaseUrl(baseUrl),
            network = network,
            serialization = serialization,
            dispatcherProvider = TestDispatcherProvider(dispatcher),
            context = context,
            interceptor = interceptor
        )

    private fun assertEnrollRequest(request: Request, url: String, proofToken: String, authorization: String) {
        assertJsonPostRequest(request, url, authorization, traceParent)

        val body = request.jsonBody(serialization)
        assertThat(body.keys).containsExactly("proofToken")
        assertThat(body.jsonString("proofToken")).isEqualTo(proofToken)
    }

    private fun context(authz: Authz): Context = Context.Builder().apply { this.authz = authz }.build("EnrollDirectApiContractTest")

    private fun noContent(request: Request): Network.Response.Success =
        apiSuccess(request.url, code = 204, body = "")

    private fun assertNoRequestExecution(network: RecordingNetwork, interceptor: RecordingApiCallInterceptor) {
        assertThat(network.requests).isEmpty()
        assertThat(interceptor.requestCount).isEqualTo(0)
        assertThat(interceptor.responseCount).isEqualTo(0)
    }

    private class RecordingApiCallInterceptor : ApiCallInterceptor {
        var requestCount: Int = 0
            private set
        var responseCount: Int = 0
            private set

        override suspend fun interceptRequest(request: Request): Request {
            requestCount += 1
            return request
        }

        override suspend fun <ApiSuccess, ApiFailure> onResponse(
            request: Request,
            response: ApiResult<ApiSuccess, ApiFailure>
        ): ApiResult<ApiSuccess, ApiFailure> {
            responseCount += 1
            return response
        }
    }
}
