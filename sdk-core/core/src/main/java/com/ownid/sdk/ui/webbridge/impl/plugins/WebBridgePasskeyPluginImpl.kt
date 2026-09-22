package com.ownid.sdk.ui.webbridge.impl.plugins

import com.ownid.sdk.capability.LocalInfo
import com.ownid.sdk.capability.Passkey
import com.ownid.sdk.capability.Serialization
import com.ownid.sdk.capability.UiContextProvider
import com.ownid.sdk.di.DIContainerResolver
import com.ownid.sdk.di.getOrThrow
import com.ownid.sdk.model.AssertionOptions
import com.ownid.sdk.model.AttestationConveyancePreference
import com.ownid.sdk.model.AttestationOptions
import com.ownid.sdk.model.AuthenticatorAttachment
import com.ownid.sdk.model.ChallengeId
import com.ownid.sdk.model.CredentialType
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.KeyAlgorithmType
import com.ownid.sdk.model.PublicKeyCredentialDescriptor
import com.ownid.sdk.model.ResidentKey
import com.ownid.sdk.model.Timeout
import com.ownid.sdk.model.UserVerification
import com.ownid.sdk.model.encodeToBase64UrlSafe
import com.ownid.sdk.model.secureRandom
import com.ownid.sdk.ui.webbridge.WebBridgePasskeyPlugin
import com.ownid.sdk.ui.webbridge.WebBridgePlugin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Bridge-scoped FIDO plugin backed by the Android passkey capability.
 *
 * This is an internal WebBridge contract for hosted pages, not a stable app-developer API. It exposes availability,
 * assertion, and creation actions under the `FIDO` namespace. `get` expects a hosted-page challenge context and
 * relying-party ID, optionally limited by `credsIds` or `credId`, then calls [Passkey.getCredential]. `create` accepts
 * either the OwnID flow shape, selected by the presence of `context`, or the WebAuthn enrollment-options shape, then
 * calls [Passkey.createCredential] with auto-select disabled. Both actions use an Activity selected by [UiContextProvider].
 *
 * Successful assertions return authenticator data, client data, signature, user handle, optional authenticator
 * attachment, and credential ID. Successful creation returns the compact OwnID flow payload or the WebAuthn-style
 * enrollment payload. Passkey-reported cancellation maps to bridge error type `ABORTED`; missing assertion credentials
 * map to `TYPE_NO_CREDENTIAL`; host-resolution failures, malformed parameters, and other provider failures return a
 * bridge error message. The plugin returns WebBridge payloads only and does not persist assertion or attestation results.
 */
internal class WebBridgePasskeyPluginImpl(
    private val uiContextProvider: () -> UiContextProvider,
    private val localInfo: LocalInfo,
    private val passkey: Passkey,
    private val serialization: Serialization
) : WebBridgePasskeyPlugin {

    @InternalOwnIdAPI
    internal companion object {
        internal fun create(resolver: DIContainerResolver): WebBridgePasskeyPlugin = resolver.run {
            WebBridgePasskeyPluginImpl(
                uiContextProvider = { getOrThrow<UiContextProvider>() },
                localInfo = getOrThrow<LocalInfo>(),
                passkey = getOrThrow<Passkey>(),
                serialization = getOrThrow<Serialization>()
            )
        }
    }

    override suspend fun handle(message: WebBridgePlugin.Message): WebBridgePlugin.Result {
        if (key.id.equals(message.payload.pluginId, ignoreCase = true).not()) {
            return WebBridgePlugin.Result(
                error = buildJsonObject { put("message", "Wrong plugin ID: ${message.payload.pluginId}") }
            )
        }

        return when (message.payload.action.uppercase()) {
            "ISAVAILABLE" -> WebBridgePlugin.Result(success = JsonPrimitive(localInfo.isSystemFidoCapable))
            "GET" -> handleGet(message)
            "CREATE" -> handleCreate(message)
            else -> WebBridgePlugin.Result(error = buildJsonObject { put("message", "Unknown action: ${message.payload.action}") })
        }
    }

    private suspend fun handleGet(message: WebBridgePlugin.Message): WebBridgePlugin.Result = runCatching {
        val paramsJson = message.payload.params?.let { serialization.parseToJsonElement(it).jsonObject } ?: JsonObject(emptyMap())

        val challenge = paramsJson["context"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("WebBridgePasskeyPlugin: 'context' cannot be empty")

        val rpId = paramsJson["relyingPartyId"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("WebBridgePasskeyPlugin: 'relyingPartyId' cannot be empty")

        val credIds = paramsJson.extractCredentialIds()
            .map { credId -> PublicKeyCredentialDescriptor(type = CredentialType.PUBLIC_KEY, id = credId) }
            .ifEmpty { null }

        val assertionOptions = AssertionOptions(
            challenge = ChallengeId(challenge.encodeToByteArray().encodeToBase64UrlSafe()),
            rpId = rpId,
            allowCredentials = credIds,
            userVerification = UserVerification.REQUIRED,
            timeout = Timeout(2 * 60 * 1000L)
        )

        val provider = uiContextProvider.invoke()
        val resolution = withContext(Dispatchers.Main.immediate) { provider.current() }
        val activity = when (resolution) {
            is UiContextProvider.Result.Success -> resolution.activity
            is UiContextProvider.Result.Failure ->
                return@runCatching WebBridgePlugin.Result(error = buildJsonObject { put("message", resolution.error.message) })
        }
        when (val result = passkey.getCredential(activity, assertionOptions)) {
            is Passkey.Result.Fail -> when (result.error) {
                is Passkey.NoCredentialError ->
                    WebBridgePlugin.Result(error = buildJsonObject { put("type", "TYPE_NO_CREDENTIAL"); put("message", result.error.message) })

                else ->
                    WebBridgePlugin.Result(error = buildJsonObject { put("message", result.error.message) })
            }

            is Passkey.Result.Canceled ->
                WebBridgePlugin.Result(error = buildJsonObject { put("type", "ABORTED"); put("message", result.reason.toString()) })

            is Passkey.Result.Success ->
                WebBridgePlugin.Result(success = buildJsonObject {
                    put("authenticatorData", result.data.response.authenticatorData)
                    put("clientDataJSON", result.data.response.clientDataJSON)
                    put("signature", result.data.response.signature)
                    put("userHandle", result.data.response.userHandle)
                    result.data.authenticatorAttachment?.let { put("authenticatorAttachment", it.value) }
                    put("credentialId", result.data.id)
                })
        }
    }.fold(
        onSuccess = { it },
        onFailure = { error ->
            if (error is CancellationException) throw error
            WebBridgePlugin.Result(error = buildJsonObject { put("message", error.message) })
        }
    )

    private suspend fun handleCreate(message: WebBridgePlugin.Message): WebBridgePlugin.Result = runCatching {
        val paramsJson = message.payload.params?.let { serialization.parseToJsonElement(it).jsonObject } ?: JsonObject(emptyMap())

        val isOwnIdFlow = paramsJson.containsKey("context")
        val attestationOptions = if (isOwnIdFlow) {
            createAttestationOptionsFromFlowOptions(paramsJson)
        } else {
            createAttestationOptionsFromEnrollmentOptions(paramsJson)
        }

        val provider = uiContextProvider.invoke()
        val resolution = withContext(Dispatchers.Main.immediate) { provider.current() }
        val activity = when (resolution) {
            is UiContextProvider.Result.Success -> resolution.activity
            is UiContextProvider.Result.Failure ->
                return@runCatching WebBridgePlugin.Result(error = buildJsonObject { put("message", resolution.error.message) })
        }
        when (val result = passkey.createCredential(activity, attestationOptions, isAutoSelectAllowed = false)) {
            is Passkey.Result.Fail ->
                WebBridgePlugin.Result(error = buildJsonObject { put("message", result.error.message) })

            is Passkey.Result.Canceled ->
                WebBridgePlugin.Result(error = buildJsonObject { put("type", "ABORTED"); put("message", result.reason.toString()) })

            is Passkey.Result.Success -> {
                val response = if (isOwnIdFlow) {
                    buildJsonObject {
                        put("attestationObject", result.data.response.attestationObject)
                        put("clientDataJSON", result.data.response.clientDataJSON)
                        put("credentialId", result.data.id)
                    }
                } else {
                    buildJsonObject {
                        put("id", result.data.id)
                        put("rawId", result.data.id)
                        put("type", result.data.type.value)
                        put("response", buildJsonObject {
                            put("clientDataJSON", result.data.response.clientDataJSON)
                            put("attestationObject", result.data.response.attestationObject)
                        })
                        result.data.authenticatorAttachment?.let {
                            put("authenticatorAttachment", it.value)
                        }
                    }
                }
                WebBridgePlugin.Result(success = response)
            }
        }
    }.fold(
        onSuccess = { it },
        onFailure = { error ->
            if (error is CancellationException) throw error
            WebBridgePlugin.Result(error = buildJsonObject { put("message", error.message) })
        }
    )

    private fun createAttestationOptionsFromFlowOptions(paramsJson: JsonObject): AttestationOptions {
        val context = paramsJson["context"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("WebBridgePasskeyPlugin: 'context' cannot be empty")

        val rpId = paramsJson["relyingPartyId"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("WebBridgePasskeyPlugin: 'relyingPartyId' cannot be empty")

        val rpName = paramsJson["relyingPartyName"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("WebBridgePasskeyPlugin: 'relyingPartyName' cannot be empty")

        val userName = paramsJson["userName"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("WebBridgePasskeyPlugin: 'userName' cannot be empty")

        val userDisplayName = paramsJson["userDisplayName"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("WebBridgePasskeyPlugin: 'userDisplayName' cannot be empty")

        val credIds = paramsJson.extractCredentialIds()
            .map { credId -> PublicKeyCredentialDescriptor(type = CredentialType.PUBLIC_KEY, id = credId) }
            .ifEmpty { null }

        return AttestationOptions(
            rp = AttestationOptions.RelayingParty(id = rpId, name = rpName),
            user = AttestationOptions.User(
                id = secureRandom(32).encodeToBase64UrlSafe(),
                name = userName,
                displayName = userDisplayName
            ),
            challenge = ChallengeId(context.encodeToByteArray().encodeToBase64UrlSafe()),
            pubKeyCredParams = listOf(
                AttestationOptions.PubKeyCredParams(type = CredentialType.PUBLIC_KEY, alg = KeyAlgorithmType.ES256),
                AttestationOptions.PubKeyCredParams(type = CredentialType.PUBLIC_KEY, alg = KeyAlgorithmType.RS256)
            ),
            attestation = AttestationConveyancePreference.NONE,
            authenticatorSelection = AttestationOptions.AuthenticatorSelection(
                authenticatorAttachment = AuthenticatorAttachment.PLATFORM,
                userVerification = UserVerification.REQUIRED,
                residentKey = ResidentKey.PREFERRED
            ),
            timeout = Timeout(2 * 60 * 1000L),
            excludeCredentials = credIds
        )
    }

    private fun createAttestationOptionsFromEnrollmentOptions(paramsJson: JsonObject): AttestationOptions {
        val challenge = paramsJson["challenge"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("WebBridgePasskeyPlugin: 'challenge' is required")

        val rpObject = paramsJson["rp"]?.jsonObject ?: throw IllegalArgumentException("WebBridgePasskeyPlugin: 'rp' is required")

        val rp = AttestationOptions.RelayingParty(
            id = rpObject["id"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("WebBridgePasskeyPlugin: 'rp.id' is required"),
            name = rpObject["name"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("WebBridgePasskeyPlugin: 'rp.name' is required")
        )

        val userObject = paramsJson["user"]?.jsonObject
            ?: throw IllegalArgumentException("WebBridgePasskeyPlugin: 'user' is required")

        val userId = userObject["id"]?.jsonPrimitive?.contentOrNull ?: secureRandom(32).encodeToBase64UrlSafe()

        val user = AttestationOptions.User(
            id = userId,
            name = userObject["name"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("WebBridgePasskeyPlugin: 'user.name' is required"),
            displayName = userObject["displayName"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("WebBridgePasskeyPlugin: 'user.displayName' is required")
        )

        val pubKeyCredParamsArray = paramsJson["pubKeyCredParams"]?.jsonArray
            ?: throw IllegalArgumentException("WebBridgePasskeyPlugin: 'pubKeyCredParams' is required")

        val pubKeyCredParams = pubKeyCredParamsArray.mapNotNull { element ->
            val param = element.jsonObject
            val alg = param["alg"]?.jsonPrimitive?.intOrNull
                ?: throw IllegalArgumentException("WebBridgePasskeyPlugin: 'alg' is required in pubKeyCredParams")

            val keyAlgorithm = when (alg) {
                -7 -> KeyAlgorithmType.ES256
                -257 -> KeyAlgorithmType.RS256
                else -> return@mapNotNull null
            }
            AttestationOptions.PubKeyCredParams(type = CredentialType.PUBLIC_KEY, alg = keyAlgorithm)
        }
        if (pubKeyCredParams.isEmpty()) {
            throw IllegalArgumentException("WebBridgePasskeyPlugin: Unsupported algorithm in pubKeyCredParams")
        }

        val attestation = paramsJson["attestation"]?.jsonPrimitive?.contentOrNull?.let { value ->
            AttestationConveyancePreference.values().find { it.value == value }
        } ?: AttestationConveyancePreference.NONE

        val authenticatorSelectionObject = paramsJson["authenticatorSelection"]?.jsonObject
        val authenticatorSelection = if (authenticatorSelectionObject != null) {
            AttestationOptions.AuthenticatorSelection(
                authenticatorAttachment = authenticatorSelectionObject["authenticatorAttachment"]?.jsonPrimitive?.contentOrNull
                    ?.let { value -> AuthenticatorAttachment.values().find { it.value == value } }
                    ?: AuthenticatorAttachment.PLATFORM,
                userVerification = authenticatorSelectionObject["userVerification"]?.jsonPrimitive?.contentOrNull
                    ?.let { value -> UserVerification.values().find { it.value == value } }
                    ?: UserVerification.REQUIRED,
                residentKey = authenticatorSelectionObject["residentKey"]?.jsonPrimitive?.contentOrNull
                    ?.let { value -> ResidentKey.values().find { it.value == value } }
                    ?: ResidentKey.PREFERRED
            )
        } else {
            AttestationOptions.AuthenticatorSelection(
                authenticatorAttachment = AuthenticatorAttachment.PLATFORM,
                userVerification = UserVerification.REQUIRED,
                residentKey = ResidentKey.PREFERRED
            )
        }

        val timeout = paramsJson["timeout"]?.jsonPrimitive?.longOrNull ?: (2 * 60 * 1_000)

        val excludeCredentialsArray = paramsJson["excludeCredentials"]?.jsonArray
        val excludeCredentials = excludeCredentialsArray?.takeIf { it.isNotEmpty() }?.map { element ->
            val descriptor = element.jsonObject
            val credId = descriptor["id"]?.jsonPrimitive?.contentOrNull
                ?: throw IllegalArgumentException("WebBridgePasskeyPlugin: 'id' is required in excludeCredentials")
            val rawType = descriptor["type"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
                ?: throw IllegalArgumentException("WebBridgePasskeyPlugin: 'excludeCredentials.type' is required")
            val type = CredentialType.values().find { it.value == rawType }
                ?: throw IllegalArgumentException("WebBridgePasskeyPlugin: Unsupported credential type '$rawType'")
            PublicKeyCredentialDescriptor(type = type, id = credId)
        }

        return AttestationOptions(
            rp = rp,
            user = user,
            challenge = ChallengeId(challenge),
            pubKeyCredParams = pubKeyCredParams,
            attestation = attestation,
            authenticatorSelection = authenticatorSelection,
            timeout = Timeout(timeout),
            excludeCredentials = excludeCredentials
        )
    }

    private fun JsonObject.extractCredentialIds(): List<String> {
        val credsIds = this["credsIds"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }?.filter { it.isNotBlank() } ?: emptyList()
        val credId = this["credId"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        return credsIds.ifEmpty { listOfNotNull(credId) }
    }
}
