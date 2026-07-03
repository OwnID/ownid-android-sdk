package com.ownid.sdk.ui.webbridge.impl.plugins

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.capability.LocalInfo
import com.ownid.sdk.capability.LoginIdValidator
import com.ownid.sdk.capability.Passkey
import com.ownid.sdk.capability.Serialization
import com.ownid.sdk.capability.SignInWithGoogle
import com.ownid.sdk.capability.SignInWithSocial
import com.ownid.sdk.capability.UiContextProvider
import com.ownid.sdk.capability.UserRepository
import com.ownid.sdk.model.AssertionOptions
import com.ownid.sdk.model.AssertionResult
import com.ownid.sdk.model.AttestationOptions
import com.ownid.sdk.model.AttestationResult
import com.ownid.sdk.model.Authz
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.User
import com.ownid.sdk.ops.WebBridgeOperationEventWrapper
import com.ownid.sdk.provider.PasswordAuthenticate
import com.ownid.sdk.provider.SessionCreate
import com.ownid.sdk.provider.SessionOutput
import com.ownid.sdk.ui.webbridge.WebBridgeContextPlugin
import com.ownid.sdk.ui.webbridge.WebBridgeElitePlugin
import com.ownid.sdk.ui.webbridge.WebBridgeMetadataPlugin
import com.ownid.sdk.ui.webbridge.WebBridgePasskeyPlugin
import com.ownid.sdk.ui.webbridge.WebBridgePlugin
import com.ownid.sdk.ui.webbridge.WebBridgeSocialPlugin
import com.ownid.sdk.ui.webbridge.WebBridgeUserRepositoryPlugin
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.Test

@OptIn(InternalOwnIdAPI::class)
// Covers: WB-130, WB-140, WB-150
internal class WebBridgePluginInjectionDataContractTest {

    @Test
    fun builtInPluginInterfacesExposeStableNamespacesAndDefaultActions() {
        assertThat(WebBridgePasskeyPlugin.KEY.id).isEqualTo("FIDO")
        assertThat(WebBridgeUserRepositoryPlugin.KEY.id).isEqualTo("STORAGE")
        assertThat(WebBridgeSocialPlugin.KEY.id).isEqualTo("SOCIAL")
        assertThat(WebBridgeMetadataPlugin.KEY.id).isEqualTo("METADATA")
        assertThat(WebBridgeContextPlugin.KEY.id).isEqualTo("CONTEXT")
        assertThat(WebBridgeElitePlugin.KEY.id).isEqualTo("FLOW")

        assertThat(TestPasskeyPlugin.actions).containsExactly("isAvailable", "create", "get").inOrder()
        assertThat(TestUserRepositoryPlugin.actions).containsExactly("setLastUser", "getLastUser").inOrder()
        assertThat(TestMetadataPlugin.actions).containsExactly("get")
        assertThat(TestContextPlugin.actions).containsExactly("get")
    }

    @Test
    fun builtInPluginImplementationsAdvertiseAvailableCapabilitiesOnly() {
        val context = com.ownid.sdk.model.Context.Builder().apply {
            authz = Authz.start(LoginId("webbridge@example.test", LoginId.Type.Email))
            accountDisplayName = "WebBridge User"
        }.build(scopeName = "WebBridgePluginInjectionDataContractTest")
        val elitePlugin = WebBridgeElitePluginImpl(
            serialization = TestSerialization,
            loginIdValidator = TestLoginIdValidator,
            sessionCreate = null,
            passwordAuthenticate = null,
            logger = null
        )
        elitePlugin.addEventWrappers(listOf(TestEventWrapper("onNativeAction"), TestEventWrapper("onFinish")))

        val cases = listOf(
            WebBridgePasskeyPluginImpl(
                uiContextProvider = { UiContextProvider { error("Unexpected UI context request") } },
                localInfo = TestLocalInfo,
                passkey = TestPasskey,
                serialization = TestSerialization
            ) to ("FIDO" to listOf("isAvailable", "create", "get")),
            WebBridgeUserRepositoryPluginImpl(
                userRepository = TestUserRepository,
                loginIdValidator = TestLoginIdValidator,
                serialization = TestSerialization
            ) to ("STORAGE" to listOf("setLastUser", "getLastUser")),
            WebBridgeMetadataPluginImpl(localInfo = TestLocalInfo) to ("METADATA" to listOf("get")),
            WebBridgeContextPluginImpl(context = context) to ("CONTEXT" to listOf("get")),
            WebBridgeSocialPluginImpl(
                uiContextProvider = { UiContextProvider { error("Unexpected UI context request") } },
                signInWithGoogle = TestSignInWithGoogle,
                serialization = TestSerialization
            ) to ("SOCIAL" to listOf("Google")),
            elitePlugin to ("FLOW" to listOf("onNativeAction", "onFinish"))
        )

        assertThat(cases.map { it.first.getInjectionData() }).containsExactlyElementsIn(cases.map { it.second }).inOrder()
        assertThat(WebBridgeContextPluginImpl(context = null).getInjectionData()).isNull()
        assertThat(
            WebBridgeSocialPluginImpl(
                uiContextProvider = { UiContextProvider { error("Unexpected UI context request") } },
                signInWithGoogle = null,
                serialization = TestSerialization
            ).getInjectionData()
        ).isNull()
    }

    @Test
    fun elitePluginAdvertisesProviderAndOperationWrapperActionsInRegistrationOrder() {
        val elitePlugin = WebBridgeElitePluginImpl(
            serialization = TestSerialization,
            loginIdValidator = TestLoginIdValidator,
            sessionCreate = TestSessionCreate,
            passwordAuthenticate = TestPasswordAuthenticate,
            logger = null
        )

        assertThat(elitePlugin.actions).containsExactly("session_create", "auth_password_authenticate").inOrder()
        assertThat(elitePlugin.getInjectionData()).isEqualTo("FLOW" to listOf("session_create", "auth_password_authenticate"))

        elitePlugin.addEventWrappers(listOf(TestEventWrapper("onError"), TestEventWrapper("onClose")))

        assertThat(elitePlugin.actions).containsExactly("session_create", "auth_password_authenticate", "onError", "onClose").inOrder()
        assertThat(elitePlugin.getInjectionData())
            .isEqualTo("FLOW" to listOf("session_create", "auth_password_authenticate", "onError", "onClose"))
    }

    private object TestSerialization : Serialization {
        override val json: Json = Json
    }

    private object TestLocalInfo : LocalInfo {
        override val modules: List<Pair<String, String>> = listOf("core" to "test")
        override val packageName: String = "com.ownid.webbridge.test"
        override val appVersion: String = "1"
        override val userAgent: String = "OwnID WebBridge Test"
        override val correlationId: String = "correlation-webbridge-test"
        override val isDebuggable: Boolean = true
        override val isSystemFidoCapable: Boolean = true
        override val isDeviceSecured: Boolean = true
        override val isFingerprintHardwarePresent: Boolean = false
        override val isFaceHardwarePresent: Boolean = false
        override val isIrisHardwarePresent: Boolean = false
        override val isStrongBiometricEnabled: Boolean = false
    }

    private object TestLoginIdValidator : LoginIdValidator {
        override fun determineLoginIdType(loginId: String): LoginId.Type = LoginId.Type.Email
        override fun validate(loginId: LoginId): LoginId = loginId
    }

    private object TestUserRepository : UserRepository {
        override suspend fun getLastUser(): User = error("Unexpected repository read")
        override suspend fun setLastUser(user: User): Unit = error("Unexpected repository write")
        override suspend fun clearLastUser(): Unit = error("Unexpected repository clear")
    }

    private object TestPasskey : Passkey {
        override suspend fun getCredential(
            context: android.content.Context,
            assertionOptions: AssertionOptions
        ): Passkey.Result<AssertionResult> = error("Unexpected passkey assertion")

        override suspend fun createCredential(
            context: android.content.Context,
            attestationOptions: AttestationOptions,
            isAutoSelectAllowed: Boolean
        ): Passkey.Result<AttestationResult> = error("Unexpected passkey attestation")
    }

    private object TestSignInWithGoogle : SignInWithGoogle {
        override suspend fun signIn(params: SignInWithSocial.Params): SignInWithSocial.Result = error("Unexpected social sign-in")
        override suspend fun cancel(): Unit = error("Unexpected social cancel")
        override suspend fun signOut(params: SignInWithGoogle.SignOutParams): Throwable = error("Unexpected social sign-out")
    }

    private object TestSessionCreate : SessionCreate {
        override suspend fun create(params: SessionCreate.Params): Result<SessionOutput> = error("Unexpected session create")
    }

    private object TestPasswordAuthenticate : PasswordAuthenticate {
        override suspend fun authenticate(params: PasswordAuthenticate.Params): Result<SessionOutput> = error("Unexpected password authenticate")
    }

    private class TestEventWrapper(
        override val webBridgePluginAction: String
    ) : WebBridgeOperationEventWrapper {
        override val isTerminal: Boolean = true
        override suspend fun runWrappedFunction(params: String?, serialization: Serialization): JsonElement = JsonObject(emptyMap())
    }

    private object TestPasskeyPlugin : WebBridgePasskeyPlugin {
        override suspend fun handle(message: WebBridgePlugin.Message): WebBridgePlugin.Result = WebBridgePlugin.Result()
    }

    private object TestUserRepositoryPlugin : WebBridgeUserRepositoryPlugin {
        override suspend fun handle(message: WebBridgePlugin.Message): WebBridgePlugin.Result = WebBridgePlugin.Result()
    }

    private object TestMetadataPlugin : WebBridgeMetadataPlugin {
        override suspend fun handle(message: WebBridgePlugin.Message): WebBridgePlugin.Result = WebBridgePlugin.Result()
    }

    private object TestContextPlugin : WebBridgeContextPlugin {
        override suspend fun handle(message: WebBridgePlugin.Message): WebBridgePlugin.Result = WebBridgePlugin.Result()
    }
}
