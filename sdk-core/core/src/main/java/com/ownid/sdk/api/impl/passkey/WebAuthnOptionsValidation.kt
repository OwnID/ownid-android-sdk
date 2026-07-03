package com.ownid.sdk.api.impl.passkey

import com.ownid.sdk.api.impl.models.AssertionOptionsResponse
import com.ownid.sdk.api.impl.models.AttestationOptionsResponse
import com.ownid.sdk.model.decodeBase64UrlToByteArray
import com.ownid.sdk.model.encodeToBase64UrlSafe

internal object WebAuthnOptionsValidation {
    private const val MAX_USER_HANDLE_BYTES = 64
    private const val MAX_CREDENTIAL_ID_BYTES = 1023
    private val base64UrlRegex = Regex("^[A-Za-z0-9_-]*={0,2}$")

    fun validateAttestationOptionsResponse(response: AttestationOptionsResponse) {
        decodeBase64Url(response.challenge.value, fieldPath = "attestation.challenge")
        val userId = decodeBase64Url(response.user.id, fieldPath = "attestation.user.id")
        validateSize(userId.size, fieldPath = "attestation.user.id", min = 1, max = MAX_USER_HANDLE_BYTES)

        response.excludeCredentials?.forEachIndexed { index, credential ->
            val credentialId = decodeBase64Url(credential.id, fieldPath = "attestation.excludeCredentials[$index].id")
            validateSize(credentialId.size, fieldPath = "attestation.excludeCredentials[$index].id", min = 0, max = MAX_CREDENTIAL_ID_BYTES)
        }
    }

    fun validateAssertionOptionsResponse(response: AssertionOptionsResponse) {
        decodeBase64Url(response.challenge.value, fieldPath = "assertion.challenge")

        response.allowCredentials?.forEachIndexed { index, credential ->
            val credentialId = decodeBase64Url(credential.id, fieldPath = "assertion.allowCredentials[$index].id")
            validateSize(credentialId.size, fieldPath = "assertion.allowCredentials[$index].id", min = 0, max = MAX_CREDENTIAL_ID_BYTES)
        }
    }

    private fun decodeBase64Url(value: String, fieldPath: String): ByteArray {
        require(base64UrlRegex.matches(value)) { "Invalid WebAuthn options: $fieldPath must be base64url" }

        val normalizedValue = value.trimEnd('=')
        require(normalizedValue.length % 4 != 1) { "Invalid WebAuthn options: $fieldPath has invalid base64url length" }
        require(value.length == normalizedValue.length || value.length % 4 == 0) { "Invalid WebAuthn options: $fieldPath has invalid base64url padding" }

        val decoded = try {
            value.decodeBase64UrlToByteArray()
        } catch (error: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid WebAuthn options: $fieldPath failed to decode base64url", error)
        }

        require(decoded.encodeToBase64UrlSafe() == normalizedValue) { "Invalid WebAuthn options: $fieldPath is not canonical base64url" }
        return decoded
    }

    private fun validateSize(size: Int, fieldPath: String, min: Int, max: Int) {
        require(size in min..max) { "Invalid WebAuthn options: $fieldPath decoded size $size is outside $min..$max bytes" }
    }
}
