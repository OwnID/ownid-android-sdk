@file:JvmName("OwnIdContextScopes")

package com.ownid.sdk.model

import com.ownid.sdk.OwnId
import com.ownid.sdk.capability.LoginIdValidationException
import com.ownid.sdk.capability.LoginIdValidator
import com.ownid.sdk.capability.appendWithType
import com.ownid.sdk.di.DIContainer
import com.ownid.sdk.di.getOrNull
import com.ownid.sdk.di.impl.DIContainerImpl
import com.ownid.sdk.di.register
import com.ownid.sdk.di.remove
import com.ownid.sdk.model.Authz.Companion.fromToken
import com.ownid.sdk.model.Authz.Companion.start
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Authorization input supplied by the app for SDK surfaces that accept scoped context.
 *
 * Use login-ID authz when the app starts from a user identifier. Use access-token authz when the app already has an
 * OwnID access token. A single [Authz] value represents one of those options, never both.
 *
 * Create instances via [start] and [fromToken]. Raw login-ID authz is stored as a string and may be resolved to a typed
 * [LoginId] later by a consuming surface. Access-token authz stores the supplied [AccessToken] as-is.
 *
 * [Authz] has no public serialization contract. The SDK may forward selected context fields, including authz, to
 * supported SDK surfaces.
 */
public sealed interface Authz {
    public companion object {
        /**
         * Creates login-ID authz from an identifier string.
         *
         * Pass [type] when your app already knows the identifier type. Omit [type] when the app only has a raw value
         * and the SDK should resolve the type later.
         *
         * This method does not validate [id]. Type resolution and any later validation depend on the consuming surface.
         */
        public fun start(id: String, type: LoginId.Type? = null): Authz =
            if (type == null) RawLoginIdAuthz(id) else TypedLoginIdAuthz(LoginId(id, type))

        /**
         * Creates login-ID authz from a typed [LoginId].
         *
         * Use this when your app already has both the identifier value and its type. This method stores [loginId] as-is;
         * additional type checks and validation depend on the consuming surface.
         */
        public fun start(loginId: LoginId): Authz =
            TypedLoginIdAuthz(loginId)

        /**
         * Creates access-token authz from a token string.
         *
         * The value is wrapped as [AccessToken] without validation or persistence.
         */
        public fun fromToken(token: String): Authz =
            FromAccessToken(AccessToken(token))

        /**
         * Creates access-token authz from [AccessToken].
         *
         * The provided token object is stored as-is without validation or persistence.
         */
        public fun fromToken(token: AccessToken): Authz =
            FromAccessToken(token)
    }
}

private class RawLoginIdAuthz(val id: String) : Authz
private class TypedLoginIdAuthz(val loginId: LoginId) : Authz
private class FromAccessToken(val token: AccessToken) : Authz

@DslMarker
internal annotation class OwnIdContextDsl

/**
 * Scoped value of app-supplied context for SDK surfaces.
 *
 * Build this with [Builder] through [OwnId.withContext], [OwnId.setContext], or the equivalent instance/namespace APIs.
 *
 * The context may include one authz value, either login-ID authz or access-token authz, plus presentation values such
 * as an account display name. Raw login-ID authz is stored as provided and may be resolved later by a consuming surface.
 * Typed login-ID authz is preserved as provided. Additional type checks and validation depend on the consuming surface.
 *
 * [Context] itself has no public serialization contract. Consuming SDK surfaces serialize only the payload fields they
 * support.
 *
 * Treat context values as scoped inputs, not durable storage. The SDK keeps them in the active context scope until that
 * scope is cleared, replaced, or discarded, and callers remain responsible for handling identifiers and tokens as
 * sensitive app-owned data.
 */
public class Context private constructor(
    private val values: Map<Key<*>, Any?>
) {
    internal interface Key<T : Any>

    private object ScopeNameKey : Key<String>
    private object AuthzKey : Key<Authz>
    private object AccountDisplayNameKey : Key<String>

    internal val authz: Authz?
        get() = values[AuthzKey] as? Authz

    internal val accountDisplayName: String?
        get() = values[AccountDisplayNameKey] as? String

    /**
     * DSL builder for [Context].
     *
     * Authz is represented by a single [authz] slot, so the context can hold only one authz value. Mutating this builder
     * does not change a [Context] that was already built.
     *
     * The builder does not validate login IDs. Assign `null` through in-place context updates to clear a previously
     * registered value.
     */
    @OwnIdContextDsl
    public class Builder {
        public var authz: Authz? = null

        public var accountDisplayName: String? = null

        public fun build(scopeName: String): Context = Context(
            buildMap {
                put(ScopeNameKey, scopeName)
                put(AuthzKey, authz)
                put(AccountDisplayNameKey, accountDisplayName)
            }
        )
    }

    internal fun toBuilder(): Builder = Builder().apply {
        authz = this@Context.authz
        accountDisplayName = this@Context.accountDisplayName
    }
}

@InternalOwnIdAPI
internal fun DIContainer.withContext(scopeName: String, block: Context.Builder.() -> Unit): DIContainer =
    createScope("${this.scopeName} <- $scopeName").apply {
        register<Context>(Context.Builder().apply(block).build(this.scopeName))
    }

/**
 * Updates the [Context] in the current scope in place.
 *
 * Fields assigned in [block] replace existing values, fields not assigned keep their existing values, and assigning
 * `null` clears that field.
 */
@InternalOwnIdAPI
internal fun DIContainer.setContext(block: Context.Builder.() -> Unit): DIContainer {
    val lockObject = if (this is DIContainerImpl) this.lock else this
    return synchronized(lockObject) {
        val builder = getOrNull<Context>()?.toBuilder() ?: Context.Builder()
        register<Context>(builder.apply(block).build(scopeName))
        this
    }
}

@InternalOwnIdAPI
internal fun DIContainer.clearContext(): DIContainer {
    val lockObject = if (this is DIContainerImpl) this.lock else this
    return synchronized(lockObject) {
        remove<Context>()
        this
    }
}

internal val Context.accessToken: AccessToken?
    get() = (this.authz as? FromAccessToken)?.token

internal val Context.loginId: LoginId?
    get() = (this.authz as? TypedLoginIdAuthz)?.loginId

internal val Context.rawLoginId: String?
    get() = (this.authz as? RawLoginIdAuthz)?.id

/**
 * Resolves this context's authz into a typed [LoginId] when possible.
 *
 * Typed login-ID authz is returned unchanged. Access-token authz and missing authz return `null`. Raw login-ID authz
 * requires [loginIdValidator] and maps validation failures into [LoginIdResolutionException] so consuming layers can
 * expose their own public failure models.
 */
@Throws(LoginIdResolutionException::class)
internal fun Context.loginId(loginIdValidator: LoginIdValidator?): LoginId? =
    when (val authzWrapper = this.authz) {
        is TypedLoginIdAuthz -> authzWrapper.loginId

        is RawLoginIdAuthz -> {
            val validator = loginIdValidator ?: throw LoginIdResolutionException.MissingValidator("Context.loginId(): LoginIdValidator required")
            try {
                validator.appendWithType(authzWrapper.id)
            } catch (error: LoginIdValidationException) {
                throw when (error) {
                    is LoginIdValidationException.TypeNotSupported -> LoginIdResolutionException.TypeNotSupported(error.message)
                    is LoginIdValidationException.ValidationFailed -> LoginIdResolutionException.ValidationFailed(error.message, error.loginId, error.regex)
                }
            }
        }

        is FromAccessToken -> null
        null -> null
    }

/**
 * Failure produced while resolving raw login-ID authz into a concrete [LoginId.Type].
 *
 * Consuming layers map these internal failures to their public failure models.
 */
internal sealed class LoginIdResolutionException(
    internal val errorCode: ErrorCode,
    override val message: String,
    cause: Throwable? = null
) : IllegalArgumentException(message, cause) {
    internal class MissingValidator(message: String) :
        LoginIdResolutionException(ErrorCode.MissingCapabilityProvider, message) {
        override fun toString(): String = "LoginIdResolutionException.MissingValidator(errorCode=$errorCode, message=$message)"
    }

    internal class TypeNotSupported(message: String) :
        LoginIdResolutionException(ErrorCode.LoginIdTypeNotSupported, message) {
        override fun toString(): String = "LoginIdResolutionException.TypeNotSupported(errorCode=$errorCode, message=$message)"
    }

    internal class ValidationFailed(message: String, internal val loginId: LoginId, internal val regex: String) :
        LoginIdResolutionException(ErrorCode.LoginIdValidationFailed, message) {
        override fun toString(): String = "LoginIdResolutionException.ValidationFailed(errorCode=$errorCode, message=$message, loginId=$loginId, regex=$regex)"
    }
}

/**
 * Builds the context payload exposed to WebBridge.
 *
 * Only the supported bridge-facing context fields are emitted. The payload may contain login identifiers or access
 * tokens and must be treated as sensitive by consumers.
 */
internal fun Context.toWebBridgePayload(): JsonObject = buildJsonObject {
    when (val authzWrapper = authz) {
        is RawLoginIdAuthz -> put("authz", buildJsonObject {
            put("loginId", JsonPrimitive(authzWrapper.id))
        })

        is TypedLoginIdAuthz -> put("authz", buildJsonObject {
            put("loginId", JsonPrimitive(authzWrapper.loginId.id))
        })

        is FromAccessToken -> put("authz", buildJsonObject {
            put("accessToken", JsonPrimitive(authzWrapper.token.token))
        })

        null -> Unit
    }

    accountDisplayName?.let { put("accountDisplayName", JsonPrimitive(it)) }
}
