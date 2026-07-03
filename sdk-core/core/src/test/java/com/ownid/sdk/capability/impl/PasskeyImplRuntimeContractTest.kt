package com.ownid.sdk.capability.impl

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.os.CancellationSignal
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CreateCredentialRequest
import androidx.credentials.CreateCredentialResponse
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PrepareGetCredentialResponse
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.credentials.exceptions.domerrors.ConstraintError
import androidx.credentials.exceptions.domerrors.NotAllowedError
import androidx.credentials.exceptions.publickeycredential.CreatePublicKeyCredentialDomException
import androidx.credentials.exceptions.publickeycredential.GetPublicKeyCredentialDomException
import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.OwnId
import com.ownid.sdk.api.ApiResult
import com.ownid.sdk.api.PasskeyAssertionApi
import com.ownid.sdk.api.PasskeyAssertionCancelApiFailure
import com.ownid.sdk.api.PasskeyAssertionStartApiFailure
import com.ownid.sdk.api.PasskeyAssertionVerifyApiFailure
import com.ownid.sdk.api.PasskeyAttestationApi
import com.ownid.sdk.api.PasskeyAttestationCancelApiFailure
import com.ownid.sdk.api.PasskeyAttestationStartApiFailure
import com.ownid.sdk.api.PasskeyAttestationVerifyApiFailure
import com.ownid.sdk.capability.DispatcherProvider
import com.ownid.sdk.capability.LocalInfo
import com.ownid.sdk.capability.Passkey
import com.ownid.sdk.capability.PasskeyDiagnostics
import com.ownid.sdk.di.register
import com.ownid.sdk.di.MissingDependencyException
import com.ownid.sdk.di.getOrNull
import com.ownid.sdk.di.getOrThrow
import com.ownid.sdk.flow.MainDispatcherRule
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.AssertionOptions
import com.ownid.sdk.model.AssertionResult
import com.ownid.sdk.model.AttestationResponse
import com.ownid.sdk.model.AttestationOptions
import com.ownid.sdk.model.AttestationResult
import com.ownid.sdk.model.Availability
import com.ownid.sdk.model.InstanceName
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.Reason
import com.ownid.sdk.ops.OperationResult
import com.ownid.sdk.ops.PasskeyAssertionOperation
import com.ownid.sdk.ops.PasskeyAssertionOperationFailure
import com.ownid.sdk.ops.PasskeyAttestationOperation
import com.ownid.sdk.ops.PasskeyAttestationOperationFailure
import com.ownid.sdk.ops.impl.assertionOptions
import com.ownid.sdk.ops.impl.assertionResult
import com.ownid.sdk.ops.impl.attestationOptions
import com.ownid.sdk.ops.impl.attestationResult
import com.ownid.sdk.ops.ui.PasskeyAssertionUi
import com.ownid.sdk.ops.ui.PasskeyAttestationUi
import com.ownid.sdk.ui.webbridge.WebBridge
import com.ownid.sdk.ui.webbridge.WebBridgePasskeyPlugin
import com.ownid.sdk.ui.webbridge.impl.WebBridgePluginFactoryStoreImpl
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.Executor
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class, InternalOwnIdAPI::class)
// Covers: PASSKEY-CAPABILITY-010
internal class PasskeyImplRuntimeContractTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val app: Application
        get() = RuntimeEnvironment.getApplication()

    private val serialization = SerializationImpl()
    private val instancesToDestroy = mutableListOf<InstanceName>()

    @After
    fun tearDown() {
        instancesToDestroy.forEach(OwnId::destroy)
        instancesToDestroy.clear()
    }

    // Covers: PASSKEY-CAPABILITY-040
    @Test
    @Config(sdk = [Build.VERSION_CODES.O_MR1])
    fun apiLevel27DefaultRuntimeInitializesWithoutPasskeyOrWebBridgeFidoSupport(): Unit = runTest(mainDispatcherRule.testDispatcher) {
        val instanceName = uniqueInstanceName()

        OwnId.initialize(instanceName) {
            applicationContext = app
            appId = "Api27PasskeyRuntimeApp123"
        }

        val container = checkNotNull(OwnId.getInstanceContainer(instanceName))
        assertThat(OwnId.instance(instanceName).configuration.appId).isEqualTo("Api27PasskeyRuntimeApp123")
        assertThat(container.getOrThrow<LocalInfo>().isSystemFidoCapable).isFalse()
        assertThat(container.getOrNull<Passkey>()).isNull()
        assertThat(container.getOrNull<PasskeyDiagnostics>()).isNull()

        val pluginFactoryStore = container.getOrThrow<WebBridgePluginFactoryStoreImpl>()
        assertThat(pluginFactoryStore.has(WebBridgePasskeyPlugin.KEY)).isFalse()
        val bridge = container.getOrThrow<WebBridge>()
        assertThat(bridge.plugins.get(WebBridgePasskeyPlugin.KEY)).isNull()
        assertThat(bridge.plugins.snapshot().map { it.key }).doesNotContain(WebBridgePasskeyPlugin.KEY)

        val attestationUiResult = container.getOrThrow<PasskeyAttestationUi>()
            .createCredential(attestationOptions(), isAutoSelectAllowed = true)
        val assertionUiResult = container.getOrThrow<PasskeyAssertionUi>()
            .getCredential(assertionOptions())
        assertMissingPasskeyFailure(attestationUiResult.assertFailure().error)
        assertMissingPasskeyFailure(assertionUiResult.assertFailure().error)

        val attestationOperation = container.getOrThrow<PasskeyAttestationOperation>()
        val assertionOperation = container.getOrThrow<PasskeyAssertionOperation>()
        assertUnavailableBecausePasskeyIsMissing(attestationOperation.availability(PasskeyAttestationOperation.Params()))
        assertUnavailableBecausePasskeyIsMissing(assertionOperation.availability(PasskeyAssertionOperation.Params()))

        val operationScope = container.createScope("api27-passkey-operation-start").apply {
            register<DispatcherProvider>(
                object : DispatcherProvider {
                    override fun defaultDispatcher() = mainDispatcherRule.testDispatcher
                    override fun ioDispatcher() = mainDispatcherRule.testDispatcher
                }
            )
            register<PasskeyAttestationApi>(FakePasskeyAttestationApi())
            register<PasskeyAssertionApi>(FakePasskeyAssertionApi())
        }

        val attestationController = operationScope.getOrThrow<PasskeyAttestationOperation>()
            .start(PasskeyAttestationOperation.Params(loginId = testLoginId))
        val assertionController = operationScope.getOrThrow<PasskeyAssertionOperation>()
            .start(PasskeyAssertionOperation.Params(loginId = testLoginId))
        repeat(8) { runCurrent() }

        val attestationFailure = withTimeout(5.seconds) {
            attestationController.whenSettled().assertFailureResult()
        }
        val assertionFailure = withTimeout(5.seconds) {
            assertionController.whenSettled().assertFailureResult()
        }
        assertMissingPasskeyProviderFailure(attestationFailure)
        assertMissingPasskeyProviderFailure(assertionFailure)
    }

    @Test
    fun apiLevelBelowPasskeyRuntimeGateFailsBeforeCredentialManagerIsRequested(): Unit = runTest(mainDispatcherRule.testDispatcher) {
        var credentialManagerRequested = false
        val diagnostics = RecordingDiagnostics()
        val passkey = passkey(
            credentialManager = RecordingCredentialManager(),
            diagnostics = diagnostics,
            apiLevel = Build.VERSION_CODES.O_MR1,
            onCredentialManagerRequested = { credentialManagerRequested = true }
        )

        val result = passkey.createCredential(app, attestationOptions())

        val failure = result.assertFailure()
        assertThat(failure.error).isInstanceOf(UnsupportedOperationException::class.java)
        assertThat(failure.error).hasMessageThat().contains("Android API ${Build.VERSION_CODES.O_MR1} doesn't support passkeys")
        assertThat(credentialManagerRequested).isFalse()
        assertThat(diagnostics.rpIds).isEmpty()
    }

    @Test
    fun createCredentialBuildsPublicKeyRequestAndDecodesSuccessfulAttestation(): Unit = runTest(mainDispatcherRule.testDispatcher) {
        val options = attestationOptions()
        val expected = attestationResult()
        val credentialManager = RecordingCredentialManager(
            createHandler = { _, request ->
                assertThat(request).isInstanceOf(CreatePublicKeyCredentialRequest::class.java)
                CreatePublicKeyCredentialResponse(serialization.json.encodeToString(expected))
            }
        )
        val passkey = passkey(credentialManager)

        val result = passkey.createCredential(app, options, isAutoSelectAllowed = false)

        assertThat(encoded(result.assertSuccess())).isEqualTo(encoded(expected))
        val recorded = credentialManager.createRequests.single()
        assertThat(recorded.context).isSameInstanceAs(app)
        val request = recorded.request as CreatePublicKeyCredentialRequest
        assertThat(jsonElement(request.requestJson)).isEqualTo(jsonElement(options))
        assertThat(request.clientDataHash).isNull()
        assertThat(request.origin).isNull()
        assertThat(request.preferImmediatelyAvailableCredentials).isTrue()
        assertThat(request.isAutoSelectAllowed).isFalse()
    }

    @Test
    fun getCredentialBuildsPublicKeyRequestWithAutoSelectAndDecodesSuccessfulAssertion(): Unit = runTest(mainDispatcherRule.testDispatcher) {
        val options = assertionOptions()
        val expected = assertionResult()
        val credentialManager = RecordingCredentialManager(
            getHandler = { _, _ ->
                GetCredentialResponse(PublicKeyCredential(serialization.json.encodeToString(expected)))
            }
        )
        val passkey = passkey(credentialManager)

        val result = passkey.getCredential(app, options)

        assertThat(encoded(result.assertSuccess())).isEqualTo(encoded(expected))
        val recorded = credentialManager.getRequests.single()
        assertThat(recorded.context).isSameInstanceAs(app)
        assertThat(recorded.request.preferImmediatelyAvailableCredentials).isTrue()
        assertThat(recorded.request.origin).isNull()
        val option = recorded.request.credentialOptions.single()
        assertThat(option).isInstanceOf(GetPublicKeyCredentialOption::class.java)
        val publicKeyOption = option as GetPublicKeyCredentialOption
        assertThat(jsonElement(publicKeyOption.requestJson)).isEqualTo(jsonElement(options))
        assertThat(publicKeyOption.clientDataHash).isNull()
        assertThat(publicKeyOption.isAutoSelectAllowed).isTrue()
    }

    @Test
    fun secondRequestFailsImmediatelyWhileCreateCredentialIsInProgress(): Unit = runTest(mainDispatcherRule.testDispatcher) {
        val firstRequestStarted = CompletableDeferred<Unit>()
        val credentialManager = RecordingCredentialManager(
            createHandler = { _, _ ->
                firstRequestStarted.complete(Unit)
                awaitCancellation()
            }
        )
        val passkey = passkey(credentialManager)
        val first = launch { passkey.createCredential(app, attestationOptions()) }
        runCurrent()
        firstRequestStarted.await()

        val second = passkey.getCredential(app, assertionOptions())

        val failure = second.assertFailure()
        assertThat(failure.error).isInstanceOf(IllegalStateException::class.java)
        assertThat(failure.error).hasMessageThat().contains("Another passkey request is already in progress")
        assertThat(credentialManager.createRequests).hasSize(1)
        assertThat(credentialManager.getRequests).isEmpty()

        first.cancel()
    }

    @Test
    fun cancellationExceptionsMapToCanceledResultWithoutDiagnostics(): Unit = runTest(mainDispatcherRule.testDispatcher) {
        val createDiagnostics = RecordingDiagnostics()
        val createResult = passkey(
            credentialManager = RecordingCredentialManager(
                createHandler = { _, _ -> throw CreateCredentialCancellationException("create canceled") }
            ),
            diagnostics = createDiagnostics
        ).createCredential(app, attestationOptions())

        val getDiagnostics = RecordingDiagnostics()
        val getResult = passkey(
            credentialManager = RecordingCredentialManager(
                getHandler = { _, _ -> throw GetCredentialCancellationException("get canceled") }
            ),
            diagnostics = getDiagnostics
        ).getCredential(app, assertionOptions())

        assertUserClose(createResult.assertCanceled().reason)
        assertUserClose(getResult.assertCanceled().reason)
        assertThat(createDiagnostics.rpIds).isEmpty()
        assertThat(getDiagnostics.rpIds).isEmpty()
    }

    @Test
    fun noCredentialMapsToDedicatedFailureWithoutDiagnostics(): Unit = runTest(mainDispatcherRule.testDispatcher) {
        val diagnostics = RecordingDiagnostics()
        val passkey = passkey(
            credentialManager = RecordingCredentialManager(
                getHandler = { _, _ -> throw NoCredentialException("none") }
            ),
            diagnostics = diagnostics
        )

        val result = passkey.getCredential(app, assertionOptions())

        assertThat(result.assertFailure().error).isInstanceOf(Passkey.NoCredentialError::class.java)
        assertThat(diagnostics.rpIds).isEmpty()
    }

    @Test
    fun notAllowedDomErrorsMapToCanceledWhileOtherDomErrorsFailAndTriggerDiagnostics(): Unit = runTest(mainDispatcherRule.testDispatcher) {
        val createCanceledDiagnostics = RecordingDiagnostics()
        val createCanceled = passkey(
            credentialManager = RecordingCredentialManager(
                createHandler = { _, _ -> throw CreatePublicKeyCredentialDomException(NotAllowedError(), "not allowed") }
            ),
            diagnostics = createCanceledDiagnostics
        ).createCredential(app, attestationOptions())

        val getCanceledDiagnostics = RecordingDiagnostics()
        val getCanceled = passkey(
            credentialManager = RecordingCredentialManager(
                getHandler = { _, _ -> throw GetPublicKeyCredentialDomException(NotAllowedError(), "not allowed") }
            ),
            diagnostics = getCanceledDiagnostics
        ).getCredential(app, assertionOptions())

        val createFailedDiagnostics = RecordingDiagnostics()
        val createOptions = attestationOptions()
        val createFailureCause = CreatePublicKeyCredentialDomException(ConstraintError(), "constraint")
        val createFailed = passkey(
            credentialManager = RecordingCredentialManager(createHandler = { _, _ -> throw createFailureCause }),
            diagnostics = createFailedDiagnostics
        ).createCredential(app, createOptions)

        val getFailedDiagnostics = RecordingDiagnostics()
        val getOptions = assertionOptions()
        val getFailureCause = GetPublicKeyCredentialDomException(ConstraintError(), "constraint")
        val getFailed = passkey(
            credentialManager = RecordingCredentialManager(getHandler = { _, _ -> throw getFailureCause }),
            diagnostics = getFailedDiagnostics
        ).getCredential(app, getOptions)

        assertUserClose(createCanceled.assertCanceled().reason)
        assertUserClose(getCanceled.assertCanceled().reason)
        assertThat(createCanceledDiagnostics.rpIds).isEmpty()
        assertThat(getCanceledDiagnostics.rpIds).isEmpty()
        assertThat(createFailed.assertFailure().error).isSameInstanceAs(createFailureCause)
        assertThat(getFailed.assertFailure().error).isSameInstanceAs(getFailureCause)
        assertThat(createFailedDiagnostics.rpIds).containsExactly(createOptions.rp.id)
        assertThat(getFailedDiagnostics.rpIds).containsExactly(getOptions.rpId)
    }

    @Test
    fun unexpectedCredentialManagerFailuresTriggerDiagnosticsForTheRequestRpId(): Unit = runTest(mainDispatcherRule.testDispatcher) {
        val createDiagnostics = RecordingDiagnostics()
        val createOptions = attestationOptions()
        val createCause = IllegalStateException("create failed")
        val createResult = passkey(
            credentialManager = RecordingCredentialManager(createHandler = { _, _ -> throw createCause }),
            diagnostics = createDiagnostics
        ).createCredential(app, createOptions)

        val getDiagnostics = RecordingDiagnostics()
        val getOptions = assertionOptions()
        val getCause = IllegalArgumentException("get failed")
        val getResult = passkey(
            credentialManager = RecordingCredentialManager(getHandler = { _, _ -> throw getCause }),
            diagnostics = getDiagnostics
        ).getCredential(app, getOptions)

        assertThat(createResult.assertFailure().error).isSameInstanceAs(createCause)
        assertThat(getResult.assertFailure().error).isSameInstanceAs(getCause)
        assertThat(createDiagnostics.rpIds).containsExactly(createOptions.rp.id)
        assertThat(getDiagnostics.rpIds).containsExactly(getOptions.rpId)
    }

    private fun passkey(
        credentialManager: CredentialManager,
        diagnostics: RecordingDiagnostics = RecordingDiagnostics(),
        apiLevel: Int = Build.VERSION_CODES.P,
        onCredentialManagerRequested: () -> Unit = {}
    ): PasskeyImpl =
        PasskeyImpl(
            serialization = serialization,
            logger = null,
            credentialManagerProvider = {
                onCredentialManagerRequested()
                credentialManager
            },
            passkeyDiagnosticsProvider = { diagnostics },
            apiLevelProvider = { apiLevel }
        )

    private fun uniqueInstanceName(): InstanceName =
        InstanceName("passkey-api27-runtime-${UUID.randomUUID()}").also { instancesToDestroy += it }

    private fun <T> Passkey.Result<T>.assertSuccess(): T {
        assertThat(this).isInstanceOf(Passkey.Result.Success::class.java)
        @Suppress("UNCHECKED_CAST")
        return (this as Passkey.Result.Success<T>).data
    }

    private fun encoded(result: AttestationResult): String = serialization.json.encodeToString(result)

    private fun encoded(result: AssertionResult): String = serialization.json.encodeToString(result)

    private fun jsonElement(optionsJson: String) = serialization.json.parseToJsonElement(optionsJson)

    private fun jsonElement(options: AttestationOptions) = jsonElement(serialization.json.encodeToString(options))

    private fun jsonElement(options: AssertionOptions) = jsonElement(serialization.json.encodeToString(options))

    private fun Passkey.Result<*>.assertFailure(): Passkey.Result.Fail {
        assertThat(this).isInstanceOf(Passkey.Result.Fail::class.java)
        return this as Passkey.Result.Fail
    }

    private fun Passkey.Result<*>.assertCanceled(): Passkey.Result.Canceled {
        assertThat(this).isInstanceOf(Passkey.Result.Canceled::class.java)
        return this as Passkey.Result.Canceled
    }

    private fun assertMissingPasskeyFailure(error: Throwable) {
        assertThat(error).isInstanceOf(MissingDependencyException::class.java)
        assertThat((error as MissingDependencyException).dependencyName).isEqualTo(Passkey::class.java.name)
    }

    private fun assertUnavailableBecausePasskeyIsMissing(availability: Availability) {
        assertThat(availability).isInstanceOf(Availability.Unavailable::class.java)
        assertThat((availability as Availability.Unavailable).message).contains(Passkey::class.java.name)
    }

    private fun OperationResult<*, *>.assertFailureResult(): Any {
        assertThat(this).isInstanceOf(OperationResult.Failure::class.java)
        return (this as OperationResult.Failure<*>).failure
    }

    private fun assertMissingPasskeyProviderFailure(failure: Any) {
        val error = when (failure) {
            is PasskeyAttestationOperationFailure.Integration.ProviderFailed -> failure.error
            is PasskeyAssertionOperationFailure.Integration.ProviderFailed -> failure.error
            else -> error("Unexpected passkey operation failure: $failure")
        }
        assertMissingPasskeyFailure(checkNotNull(error))
    }

    private fun assertUserClose(reason: Reason) {
        assertThat(reason).isInstanceOf(Reason.UserClose::class.java)
        assertThat((reason as Reason.UserClose).details).isEqualTo("User canceled authorization")
    }

    private class RecordingDiagnostics : PasskeyDiagnostics {
        val rpIds = mutableListOf<String>()

        override fun verify(rpId: String) {
            rpIds += rpId
        }
    }

    private data class CreateRequest(val context: Context, val request: CreateCredentialRequest)
    private data class GetRequest(val context: Context, val request: GetCredentialRequest)

    private val testLoginId = LoginId("api27-passkey@example.test", LoginId.Type.Email)

    private class FakePasskeyAttestationApi : PasskeyAttestationApi {
        override suspend fun start(params: PasskeyAttestationApi.Params?): ApiResult<PasskeyAttestationApi.Controller, PasskeyAttestationStartApiFailure> =
            ApiResult.Success(FakeController)

        private object FakeController : PasskeyAttestationApi.Controller {
            override val attestationOptions: AttestationOptions = attestationOptions()
            override val verify: suspend (AttestationResult) -> ApiResult<AttestationResponse, PasskeyAttestationVerifyApiFailure> =
                { error("Unexpected passkey attestation verification") }
            override val cancel: suspend (Reason) -> ApiResult<Unit, PasskeyAttestationCancelApiFailure> =
                { ApiResult.Success(Unit) }
        }
    }

    private class FakePasskeyAssertionApi : PasskeyAssertionApi {
        override suspend fun start(params: PasskeyAssertionApi.Params?): ApiResult<PasskeyAssertionApi.Controller, PasskeyAssertionStartApiFailure> =
            ApiResult.Success(FakeController)

        private object FakeController : PasskeyAssertionApi.Controller {
            override val assertionOptions: AssertionOptions = assertionOptions()
            override val verify: suspend (AssertionResult) -> ApiResult<AccessToken, PasskeyAssertionVerifyApiFailure> =
                { error("Unexpected passkey assertion verification") }
            override val cancel: suspend (Reason) -> ApiResult<Unit, PasskeyAssertionCancelApiFailure> =
                { ApiResult.Success(Unit) }
        }
    }

    private class RecordingCredentialManager(
        private val createHandler: suspend (Context, CreateCredentialRequest) -> CreateCredentialResponse = { _, _ ->
            error("No create handler configured")
        },
        private val getHandler: suspend (Context, GetCredentialRequest) -> GetCredentialResponse = { _, _ ->
            error("No get handler configured")
        }
    ) : CredentialManager {
        val createRequests = mutableListOf<CreateRequest>()
        val getRequests = mutableListOf<GetRequest>()

        override suspend fun createCredential(context: Context, request: CreateCredentialRequest): CreateCredentialResponse {
            createRequests += CreateRequest(context, request)
            return createHandler(context, request)
        }

        override suspend fun getCredential(context: Context, request: GetCredentialRequest): GetCredentialResponse {
            getRequests += GetRequest(context, request)
            return getHandler(context, request)
        }

        override suspend fun getCredential(
            context: Context,
            pendingGetCredentialHandle: PrepareGetCredentialResponse.PendingGetCredentialHandle
        ): GetCredentialResponse = error("Unexpected pending get credential request")

        override suspend fun prepareGetCredential(request: GetCredentialRequest): PrepareGetCredentialResponse =
            error("Unexpected prepare get credential request")

        override suspend fun clearCredentialState(request: ClearCredentialStateRequest): Unit =
            error("Unexpected clear credential state request")

        override fun getCredentialAsync(
            context: Context,
            request: GetCredentialRequest,
            cancellationSignal: CancellationSignal?,
            executor: Executor,
            callback: CredentialManagerCallback<GetCredentialResponse, GetCredentialException>
        ): Unit = error("Unexpected async get credential request")

        override fun getCredentialAsync(
            context: Context,
            pendingGetCredentialHandle: PrepareGetCredentialResponse.PendingGetCredentialHandle,
            cancellationSignal: CancellationSignal?,
            executor: Executor,
            callback: CredentialManagerCallback<GetCredentialResponse, GetCredentialException>
        ): Unit = error("Unexpected async pending get credential request")

        override fun prepareGetCredentialAsync(
            request: GetCredentialRequest,
            cancellationSignal: CancellationSignal?,
            executor: Executor,
            callback: CredentialManagerCallback<PrepareGetCredentialResponse, GetCredentialException>
        ): Unit = error("Unexpected async prepare get credential request")

        override fun createCredentialAsync(
            context: Context,
            request: CreateCredentialRequest,
            cancellationSignal: CancellationSignal?,
            executor: Executor,
            callback: CredentialManagerCallback<CreateCredentialResponse, CreateCredentialException>
        ): Unit = error("Unexpected async create credential request")

        override fun clearCredentialStateAsync(
            request: ClearCredentialStateRequest,
            cancellationSignal: CancellationSignal?,
            executor: Executor,
            callback: CredentialManagerCallback<Void?, ClearCredentialException>
        ): Unit = error("Unexpected async clear credential state request")

        override fun createSettingsPendingIntent(): PendingIntent =
            error("Unexpected settings pending intent request")
    }
}
