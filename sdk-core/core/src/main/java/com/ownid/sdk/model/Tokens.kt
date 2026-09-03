package com.ownid.sdk.model

import com.ownid.sdk.capability.LoginIdValidationException
import com.ownid.sdk.capability.LoginIdValidator
import com.ownid.sdk.capability.Serialization
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A token value that can represent authentication or single-operation proof.
 *
 * The value is exactly one of [AccessToken] or [ProofToken]. SDK surfaces use this wrapper when either token kind can be
 * produced by the same interaction. This wrapper has no public serialization contract.
 */
public sealed interface AccessOrProofToken

/**
 * A signed JWT that proves successful authentication.
 *
 * The raw token is app-owned sensitive data. The SDK forwards it to OwnID APIs or app callbacks that explicitly accept
 * an access token; it does not make this model durable storage.
 *
 * This model is publicly serializable as a Kotlinx Serialization object with the [token] property. [toString] returns a
 * shortened value for diagnostics and must not be used when the full token is required.
 */
@Serializable
public class AccessToken(public val token: String) : AccessOrProofToken {
    override fun toString(): String = "AccessToken(token=${token.shorten()})"
    override fun equals(other: Any?): Boolean = this === other || (other is AccessToken && token == other.token)
    override fun hashCode(): Int = token.hashCode()

    @Throws(TokenException::class)
    internal fun getLoginId(serialization: Serialization, loginIdValidator: LoginIdValidator): LoginId {
        val payload = decodeJwtPayload(token)
            ?: throw TokenException(ErrorCode.InvalidArgument, "Invalid JWT payload")

        val sub = runCatching { serialization.parseToJsonElement(payload).jsonObject["sub"]?.jsonPrimitive?.takeIf { it.isString }?.content }.getOrNull()
        if (sub.isNullOrBlank()) {
            throw TokenException(ErrorCode.InvalidArgument, "JWT subject is missing")
        }

        return parseLoginIdFromUrn(sub, loginIdValidator)
    }

    private fun decodeJwtPayload(token: String): String? {
        val unpaddedBase64UrlSegmentRegex = Regex("[A-Za-z0-9_-]+")
        val parts = token.split('.')
        if (parts.size != 3 || parts.any { !unpaddedBase64UrlSegmentRegex.matches(it) }) {
            return null
        }
        val payload = parts[1]
        return runCatching { String(payload.decodeBase64UrlToByteArray(), Charsets.UTF_8) }.getOrNull()
    }

    @Throws(TokenException::class)
    private fun parseLoginIdFromUrn(urn: String, loginIdValidator: LoginIdValidator): LoginId {
        val parts = urn.split(":", limit = 2)

        val explicitType = if (parts.size == 2) {
            LoginId.Type.values().firstOrNull { it.name.equals(parts[0], ignoreCase = true) }
        } else {
            null
        }

        val id = if (explicitType != null) parts[1] else urn
        if (id.isEmpty()) {
            throw TokenException(ErrorCode.InvalidArgument, "Invalid login ID")
        }

        val type = explicitType ?: try {
            loginIdValidator.determineLoginIdType(id)
        } catch (error: LoginIdValidationException) {
            throw TokenException(error.errorCode, error.message, error)
        }

        return try {
            loginIdValidator.validate(LoginId(id = id, type = type))
        } catch (error: LoginIdValidationException) {
            throw TokenException(error.errorCode, error.message, error)
        }
    }
}

internal class TokenException(internal val errorCode: ErrorCode, message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

/**
 * A signed JWT that proves a single successful operation.
 *
 * A proof token is not a full app session token. Pass it only to SDK APIs or backend endpoints that explicitly expect
 * operation proof.
 *
 * The raw token is app-owned sensitive data. This Android model has no public Kotlinx Serialization contract. [toString]
 * returns a shortened value for diagnostics and must not be used when the full token is required.
 */
public class ProofToken(public val token: String) : AccessOrProofToken {
    override fun toString(): String = "ProofToken(token=${token.shorten()})"
    override fun equals(other: Any?): Boolean = this === other || (other is ProofToken && token == other.token)
    override fun hashCode(): Int = token.hashCode()
}
