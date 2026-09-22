package com.ownid.sdk.capability.impl

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.credentials.exceptions.domerrors.NotAllowedError
import androidx.credentials.exceptions.publickeycredential.CreatePublicKeyCredentialDomException
import androidx.credentials.exceptions.publickeycredential.GetPublicKeyCredentialDomException
import com.ownid.sdk.capability.Passkey
import com.ownid.sdk.capability.PasskeyDiagnostics
import com.ownid.sdk.capability.Serialization
import com.ownid.sdk.logger.OwnIdLogRouter
import com.ownid.sdk.model.AssertionOptions
import com.ownid.sdk.model.AssertionResult
import com.ownid.sdk.model.AttestationOptions
import com.ownid.sdk.model.AttestationResult
import com.ownid.sdk.model.AuthenticatorAttachment
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.Reason
import com.ownid.sdk.model.TransportType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.coroutines.cancellation.CancellationException

@OptIn(InternalOwnIdAPI::class)
internal class PasskeyImpl(
    serialization: Serialization,
    private val logger: OwnIdLogRouter?,
    private val credentialManagerProvider: (Context) -> CredentialManager = { context -> CredentialManager.create(context) },
    private val passkeyDiagnosticsProvider: () -> PasskeyDiagnostics?,
    private val apiLevelProvider: () -> Int = { Build.VERSION.SDK_INT }
) : Passkey {

    private val passkeyJson: Json = Json(serialization.json) { encodeDefaults = false }
    private var requestInProgress: Boolean = false

    override suspend fun createCredential(
        activity: Activity,
        attestationOptions: AttestationOptions,
        isAutoSelectAllowed: Boolean
    ): Passkey.Result<AttestationResult> = withContext(Dispatchers.Main.immediate) {
        val apiLevel = apiLevelProvider()
        if (apiLevel < Build.VERSION_CODES.P) {
            return@withContext Passkey.Result.Fail(
                UnsupportedOperationException("Android API $apiLevel doesn't support passkeys")
            )
        }

        if (requestInProgress) {
            return@withContext Passkey.Result.Fail(IllegalStateException("Another passkey request is already in progress"))
        }

        requestInProgress = true
        return@withContext try {
            val credentialManager = credentialManagerProvider(activity)
            val request = CreatePublicKeyCredentialRequest(attestationOptions.toAndroidAttestationOptions(passkeyJson), null, true, null, isAutoSelectAllowed)
            when (val result = credentialManager.createCredential(activity, request)) {
                is CreatePublicKeyCredentialResponse -> Passkey.Result.Success(result.toAttestationResult(passkeyJson))
                else -> Passkey.Result.Fail(
                    IllegalAccessException("CreateCredentialResponse - unsupported result type: ${result.type}")
                )
            }
        } catch (cause: Exception) {
            when (cause) {
                is CreateCredentialCancellationException, is CancellationException ->
                    Passkey.Result.Canceled(Reason.UserClose("User canceled authorization"))

                is CreatePublicKeyCredentialDomException ->
                    if (cause.domError !is NotAllowedError) {
                        logger?.logW(this@PasskeyImpl, "createCredential", "Passkey attestation failed: ${cause.errorMessage ?: cause.message}", cause)
                        Passkey.Result.Fail(cause)
                    } else {
                        Passkey.Result.Canceled(Reason.UserClose("User canceled authorization"))
                    }

                else -> {
                    logger?.logW(this@PasskeyImpl, "createCredential", "Passkey attestation failed: ${cause.message}", cause)
                    Passkey.Result.Fail(cause)
                }
            }.also {
                if (it is Passkey.Result.Fail) {
                    passkeyDiagnosticsProvider()?.verify(attestationOptions.rp.id)
                }
            }
        } finally {
            requestInProgress = false
        }
    }

    override suspend fun getCredential(
        activity: Activity,
        assertionOptions: AssertionOptions
    ): Passkey.Result<AssertionResult> = withContext(Dispatchers.Main.immediate) {
        val apiLevel = apiLevelProvider()
        if (apiLevel < Build.VERSION_CODES.P) {
            return@withContext Passkey.Result.Fail(
                UnsupportedOperationException("Android API $apiLevel doesn't support passkeys")
            )
        }

        if (requestInProgress) {
            return@withContext Passkey.Result.Fail(IllegalStateException("Another passkey request is already in progress"))
        }

        requestInProgress = true
        return@withContext try {
            val credentialManager = credentialManagerProvider(activity)

            val getPublicKeyCredentialOption = GetPublicKeyCredentialOption(assertionOptions.toAndroidAssertionOptions(passkeyJson))
            val result = credentialManager.getCredential(
                activity,
                GetCredentialRequest(listOf(getPublicKeyCredentialOption), preferImmediatelyAvailableCredentials = true)
            )
            when (val credential = result.credential) {
                is PublicKeyCredential -> Passkey.Result.Success(credential.toAssertionResult(passkeyJson))
                else -> Passkey.Result.Fail(
                    IllegalAccessException("GetCredentialResponse - unsupported credential type: ${credential::class.java.name}")
                )
            }
        } catch (cause: Exception) {
            when (cause) {
                is GetCredentialCancellationException, is CancellationException ->
                    Passkey.Result.Canceled(Reason.UserClose("User canceled authorization"))

                is NoCredentialException -> {
                    logger?.logI(this@PasskeyImpl, "getCredential", "No credential available", cause)
                    Passkey.Result.Fail(Passkey.NoCredentialError())
                }

                is GetPublicKeyCredentialDomException ->
                    if (cause.domError !is NotAllowedError) {
                        logger?.logW(this@PasskeyImpl, "getCredential", "Passkey assertion failed: ${cause.errorMessage ?: cause.message}", cause)
                        Passkey.Result.Fail(cause)
                    } else Passkey.Result.Canceled(Reason.UserClose("User canceled authorization"))

                else -> {
                    logger?.logW(this, "getCredential", "Passkey assertion failed: ${cause.message}", cause)
                    Passkey.Result.Fail(cause)
                }
            }.also {
                if (it is Passkey.Result.Fail && it.error !is Passkey.NoCredentialError) {
                    passkeyDiagnosticsProvider()?.verify(assertionOptions.rpId)
                }
            }
        } finally {
            requestInProgress = false
        }
    }

    private fun PublicKeyCredential.toAssertionResult(json: Json): AssertionResult =
        json.decodeFromString<AssertionResult>(sanitizeProviderResult(authenticationResponseJson, "getCredential", sanitizeTransports = false))

    private fun AssertionOptions.toAndroidAssertionOptions(json: Json): String =
        json.encodeToString(this)

    private fun CreatePublicKeyCredentialResponse.toAttestationResult(json: Json): AttestationResult =
        json.decodeFromString<AttestationResult>(sanitizeProviderResult(registrationResponseJson, "createCredential", sanitizeTransports = true))

    private fun sanitizeProviderResult(responseJson: String, operation: String, sanitizeTransports: Boolean): String {
        val root = passkeyJson.parseToJsonElement(responseJson) as? JsonObject ?: return responseJson
        val sanitizedRoot = root.toMutableMap()
        var changed = false

        val attachment = root["authenticatorAttachment"]
        if (attachment is JsonPrimitive && attachment.isString && AuthenticatorAttachment.values().none { it.value == attachment.content }) {
            sanitizedRoot.remove("authenticatorAttachment")
            logger?.logW(this, operation, "Removed unknown provider value at authenticatorAttachment: ${attachment.content}")
            changed = true
        }

        if (sanitizeTransports) {
            val response = root["response"] as? JsonObject
            val transports = response?.get("transports") as? JsonArray
            if (response != null && transports != null) {
                val unknownTransports = mutableListOf<String>()
                val knownTransports = transports.filter { element ->
                    if (element is JsonPrimitive && element.isString && TransportType.values().none { it.value == element.content }) {
                        unknownTransports += element.content
                        false
                    } else {
                        true
                    }
                }
                if (unknownTransports.isNotEmpty()) {
                    sanitizedRoot["response"] = JsonObject(response.toMutableMap().apply { this["transports"] = JsonArray(knownTransports) })
                    logger?.logW(this, operation, "Removed unknown provider values at response.transports: $unknownTransports")
                    changed = true
                }
            }
        }

        return if (changed) JsonObject(sanitizedRoot).toString() else responseJson
    }

    private fun AttestationOptions.toAndroidAttestationOptions(json: Json): String =
        json.encodeToString(this)

    internal companion object {
        fun failingPasskey(error: Throwable): Passkey = object : Passkey {
            override suspend fun createCredential(
                activity: Activity,
                attestationOptions: AttestationOptions,
                isAutoSelectAllowed: Boolean
            ): Passkey.Result<AttestationResult> = Passkey.Result.Fail(error)

            override suspend fun getCredential(
                activity: Activity,
                assertionOptions: AssertionOptions
            ): Passkey.Result<AssertionResult> = Passkey.Result.Fail(error)
        }
    }
}
