package com.ownid.sdk.ui.webbridge.impl.plugins

import android.content.Context
import android.net.Uri
import android.webkit.WebView
import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.capability.LocalInfo
import com.ownid.sdk.capability.Passkey
import com.ownid.sdk.capability.UiContextProvider
import com.ownid.sdk.capability.impl.SerializationImpl
import com.ownid.sdk.model.AssertionOptions
import com.ownid.sdk.model.AssertionResult
import com.ownid.sdk.model.AttestationOptions
import com.ownid.sdk.model.AttestationResult
import com.ownid.sdk.model.AuthenticatorAttachment
import com.ownid.sdk.model.CredentialType
import com.ownid.sdk.model.Reason
import com.ownid.sdk.model.UserVerification
import com.ownid.sdk.ui.webbridge.WebBridgePlugin
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
// Covers: WB-BRIDGE-230
internal class WebBridgePasskeyPluginHandleContractTest {

    private val serialization = SerializationImpl()

    @Test
    fun isAvailableReturnsSystemFidoCapabilityWithoutCallingPasskey(): Unit = runTest {
        val passkey = RecordingPasskey()
        val plugin = plugin(passkey = passkey, localInfo = TestLocalInfo(isSystemFidoCapable = false))

        val result = plugin.handle(message(action = "isAvailable"))

        assertThat(result.success!!.jsonPrimitive.boolean).isFalse()
        assertThat(result.error).isNull()
        assertThat(passkey.getRequests).isEmpty()
        assertThat(passkey.createRequests).isEmpty()
    }

    @Test
    fun getBuildsAssertionRequestAndReturnsCredentialPayload(): Unit = runTest {
        val passkey = RecordingPasskey(
            getResult = Passkey.Result.Success(
                AssertionResult(
                    id = "credential-id",
                    type = CredentialType.PUBLIC_KEY,
                    response = AssertionResult.AuthenticatorResponse(
                        clientDataJSON = "client-data",
                        authenticatorData = "authenticator-data",
                        signature = "signature",
                        userHandle = "user-handle"
                    ),
                    authenticatorAttachment = AuthenticatorAttachment.PLATFORM
                )
            )
        )
        val plugin = plugin(passkey = passkey)

        val result = plugin.handle(
            message(
                action = "get",
                params = """{"context":"challenge-context","relyingPartyId":"example.test","credsIds":["cred-one","cred-two"]}"""
            )
        )

        val request = passkey.getRequests.single()
        assertThat(request.context).isSameInstanceAs(RuntimeEnvironment.getApplication())
        assertThat(request.options.rpId).isEqualTo("example.test")
        assertThat(request.options.allowCredentials!!.map { it.id }).containsExactly("cred-one", "cred-two").inOrder()
        assertThatObject(request.options.userVerification).isSameInstanceAs(UserVerification.REQUIRED)
        val success = result.success!!.jsonObject
        assertThat(success["credentialId"]!!.jsonPrimitive.content).isEqualTo("credential-id")
        assertThat(success["authenticatorData"]!!.jsonPrimitive.content).isEqualTo("authenticator-data")
        assertThat(success["clientDataJSON"]!!.jsonPrimitive.content).isEqualTo("client-data")
        assertThat(success["signature"]!!.jsonPrimitive.content).isEqualTo("signature")
        assertThat(success["userHandle"]!!.jsonPrimitive.content).isEqualTo("user-handle")
        assertThat(success["authenticatorAttachment"]!!.jsonPrimitive.content).isEqualTo("platform")
    }

    @Test
    fun getMapsNoCredentialAndCancellationToNormalizedBridgeErrors(): Unit = runTest {
        val noCredential = plugin(passkey = RecordingPasskey(getResult = Passkey.Result.Fail(Passkey.NoCredentialError())))
            .handle(message(action = "get", params = """{"context":"challenge-context","relyingPartyId":"example.test"}"""))
        val canceled = plugin(passkey = RecordingPasskey(getResult = Passkey.Result.Canceled(Reason.UserClose("dismissed"))))
            .handle(message(action = "get", params = """{"context":"challenge-context","relyingPartyId":"example.test"}"""))
        val noCredentialError = requireNotNull(noCredential.error)
        val canceledError = requireNotNull(canceled.error)

        assertThat(noCredentialError["type"]!!.jsonPrimitive.content).isEqualTo("TYPE_NO_CREDENTIAL")
        assertThat(noCredentialError["message"]!!.jsonPrimitive.content).isEqualTo("No credentials available")
        assertThat(canceledError["type"]!!.jsonPrimitive.content).isEqualTo("ABORTED")
        assertThat(canceledError["message"]!!.jsonPrimitive.content).isEqualTo("userClose: dismissed")
    }

    @Test
    fun createOwnIdFlowBuildsAttestationRequestAndReturnsCompactPayload(): Unit = runTest {
        val passkey = RecordingPasskey(createResult = Passkey.Result.Success(attestationResult("new-credential-id")))
        val plugin = plugin(passkey = passkey)

        val result = plugin.handle(
            message(
                action = "create",
                params = """
                    {
                      "context":"create-context",
                      "relyingPartyId":"example.test",
                      "relyingPartyName":"Example",
                      "userName":"person@example.test",
                      "userDisplayName":"Person",
                      "credId":"existing-credential"
                    }
                """.trimIndent()
            )
        )

        val request = passkey.createRequests.single()
        assertThat(request.context).isSameInstanceAs(RuntimeEnvironment.getApplication())
        assertThat(request.options.rp.id).isEqualTo("example.test")
        assertThat(request.options.rp.name).isEqualTo("Example")
        assertThat(request.options.user.name).isEqualTo("person@example.test")
        assertThat(request.options.user.displayName).isEqualTo("Person")
        assertThat(request.options.excludeCredentials!!.single().id).isEqualTo("existing-credential")
        assertThat(request.isAutoSelectAllowed).isFalse()
        val success = result.success!!.jsonObject
        assertThat(success["credentialId"]!!.jsonPrimitive.content).isEqualTo("new-credential-id")
        assertThat(success["clientDataJSON"]!!.jsonPrimitive.content).isEqualTo("client-data")
        assertThat(success["attestationObject"]!!.jsonPrimitive.content).isEqualTo("attestation-object")
        assertThat(success["id"]).isNull()
    }

    @Test
    fun createEnrollmentOptionsReturnsWebAuthnStylePayload(): Unit = runTest {
        val passkey = RecordingPasskey(createResult = Passkey.Result.Success(attestationResult("enrolled-credential-id")))
        val plugin = plugin(passkey = passkey)

        val result = plugin.handle(
            message(
                action = "create",
                params = """
                    {
                      "challenge":"web-authn-challenge",
                      "rp":{"id":"example.test","name":"Example"},
                      "user":{"id":"user-id","name":"person@example.test","displayName":"Person"},
                      "pubKeyCredParams":[{"type":"public-key","alg":-7}],
                      "excludeCredentials":[{"type":"public-key","id":"old-credential"}]
                    }
                """.trimIndent()
            )
        )

        val request = passkey.createRequests.single()
        assertThat(request.options.challenge.value).isEqualTo("web-authn-challenge")
        assertThat(request.options.excludeCredentials!!.single().id).isEqualTo("old-credential")
        val success = result.success!!.jsonObject
        assertThat(success["id"]!!.jsonPrimitive.content).isEqualTo("enrolled-credential-id")
        assertThat(success["rawId"]!!.jsonPrimitive.content).isEqualTo("enrolled-credential-id")
        assertThat(success["type"]!!.jsonPrimitive.content).isEqualTo("public-key")
        assertThat(success["response"]!!.jsonObject["clientDataJSON"]!!.jsonPrimitive.content).isEqualTo("client-data")
        assertThat(success["response"]!!.jsonObject["attestationObject"]!!.jsonPrimitive.content).isEqualTo("attestation-object")
        assertThat(success["authenticatorAttachment"]!!.jsonPrimitive.content).isEqualTo("platform")
    }

    @Test
    fun createMapsProviderFailureAndCancellationToBridgeErrors(): Unit = runTest {
        val failed = plugin(passkey = RecordingPasskey(createResult = Passkey.Result.Fail(IllegalStateException("provider unavailable"))))
            .handle(message(action = "create", params = enrollmentParams))
        val canceled = plugin(passkey = RecordingPasskey(createResult = Passkey.Result.Canceled(Reason.SystemError("platform canceled"))))
            .handle(message(action = "create", params = enrollmentParams))
        val failedError = requireNotNull(failed.error)
        val canceledError = requireNotNull(canceled.error)

        assertThat(failedError["message"]!!.jsonPrimitive.contentOrNull).isEqualTo("provider unavailable")
        assertThat(failedError["type"]).isNull()
        assertThat(canceledError["type"]!!.jsonPrimitive.content).isEqualTo("ABORTED")
        assertThat(canceledError["message"]!!.jsonPrimitive.content).isEqualTo("systemError: platform canceled")
    }

    private fun plugin(
        passkey: RecordingPasskey,
        localInfo: LocalInfo = TestLocalInfo(isSystemFidoCapable = true)
    ): WebBridgePasskeyPluginImpl =
        WebBridgePasskeyPluginImpl(
            uiContextProvider = { UiContextProvider { RuntimeEnvironment.getApplication() } },
            localInfo = localInfo,
            passkey = passkey,
            serialization = serialization
        )

    private fun message(action: String, params: String? = null): WebBridgePlugin.Message =
        WebBridgePlugin.Message(
            webView = WebView(RuntimeEnvironment.getApplication()),
            sourceOrigin = Uri.parse("https://example.test"),
            isMainFrame = true,
            allowedOriginRules = setOf("https://example.test"),
            payload = WebBridgePlugin.Message.Payload(
                pluginId = "FIDO",
                action = action,
                callbackPath = "ownid.callback",
                params = params
            )
        )

    private fun attestationResult(id: String): AttestationResult =
        AttestationResult(
            id = id,
            type = CredentialType.PUBLIC_KEY,
            response = AttestationResult.AuthenticatorResponse(
                clientDataJSON = "client-data",
                attestationObject = "attestation-object",
                transports = emptyList()
            ),
            authenticatorAttachment = AuthenticatorAttachment.PLATFORM
        )

    private data class GetRequest(val context: Context, val options: AssertionOptions)

    private data class CreateRequest(val context: Context, val options: AttestationOptions, val isAutoSelectAllowed: Boolean)

    private class RecordingPasskey(
        private val getResult: Passkey.Result<AssertionResult> = Passkey.Result.Fail(IllegalStateException("unexpected get")),
        private val createResult: Passkey.Result<AttestationResult> = Passkey.Result.Fail(IllegalStateException("unexpected create"))
    ) : Passkey {
        val getRequests = mutableListOf<GetRequest>()
        val createRequests = mutableListOf<CreateRequest>()

        override suspend fun getCredential(context: Context, assertionOptions: AssertionOptions): Passkey.Result<AssertionResult> {
            getRequests += GetRequest(context, assertionOptions)
            return getResult
        }

        override suspend fun createCredential(
            context: Context,
            attestationOptions: AttestationOptions,
            isAutoSelectAllowed: Boolean
        ): Passkey.Result<AttestationResult> {
            createRequests += CreateRequest(context, attestationOptions, isAutoSelectAllowed)
            return createResult
        }
    }

    private class TestLocalInfo(
        override val isSystemFidoCapable: Boolean
    ) : LocalInfo {
        override val modules: List<Pair<String, String>> = listOf("core" to "test")
        override val packageName: String = "com.ownid.webbridge.test"
        override val appVersion: String = "1"
        override val userAgent: String = "OwnID WebBridge Test"
        override val correlationId: String = "correlation-id"
        override val isDebuggable: Boolean = true
        override val isDeviceSecured: Boolean = true
        override val isFingerprintHardwarePresent: Boolean = false
        override val isFaceHardwarePresent: Boolean = false
        override val isIrisHardwarePresent: Boolean = false
        override val isStrongBiometricEnabled: Boolean = false
    }

    private companion object {
        private val enrollmentParams = """
            {
              "challenge":"web-authn-challenge",
              "rp":{"id":"example.test","name":"Example"},
              "user":{"id":"user-id","name":"person@example.test","displayName":"Person"},
              "pubKeyCredParams":[{"type":"public-key","alg":-7}]
            }
        """.trimIndent()
    }

    private fun assertThatObject(actual: Any?) = assertThat(actual)
}
