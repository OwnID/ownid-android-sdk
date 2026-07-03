package com.ownid.sdk.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * A login identifier value with its declared [type].
 *
 * The constructor stores [id] and [type] exactly as supplied. It does not trim, normalize, infer, or validate the
 * value. SDK surfaces that consume a login ID validate it against the active [LoginIdConfiguration] and surface
 * unsupported or invalid values through their own input-failure result.
 *
 * Public serialization encodes this value as an object with `id` and `type`; [Type] serializes as its exact enum case
 * name.
 */
@Serializable
public class LoginId(public val id: String, public val type: Type) {

    /**
     * Returns [id] masked for logs and debug text based on [type].
     *
     * Email and phone-number values are masked only when they match the helper's expected shape. Other identifiers are
     * trimmed before masking. Use [id] when the exact value is required.
     */
    public val maskedId: String
        get() = when (type) {
            Type.Email -> id.maskEmail()
            Type.PhoneNumber -> id.maskPhoneNumber()
            else -> id.maskId()
        }

    override fun equals(other: Any?): Boolean = other is LoginId && id == other.id && type == other.type

    override fun hashCode(): Int = 31 * id.hashCode() + type.hashCode()

    override fun toString(): String = "LoginId(id='$maskedId', type=$type)"

    /**
     * Classification of a login identifier.
     *
     * [classificationRegex] is used to infer a type from a raw login ID and as the validation fallback when
     * [LoginIdConfiguration.validationRegexes] has no override for this type. Types without type-specific syntax use a
     * non-blank value check.
     *
     * Serialized values are exact enum case names, such as `Email` or `PhoneNumber`. Decoding an unknown name throws a
     * [SerializationException].
     *
     * @property classificationRegex Default regex used to classify a raw login ID as this type and as the validation
     * fallback when [LoginIdConfiguration.validationRegexes] has no override.
     */
    @Serializable(with = Type.Serializer::class)
    public enum class Type(public val classificationRegex: Regex = "^(?=.*\\S).*$".toRegex()) {
        Email("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$".toRegex()),

        PhoneNumber("^\\+?[1-9]\\d{1,14}$".toRegex()),

        UserName,

        CredentialId,

        Anonymous,

        FaceKeyPersonId;

        public object Serializer : KSerializer<Type> {
            override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LoginId.Type", PrimitiveKind.STRING)

            override fun serialize(encoder: Encoder, value: Type): Unit = encoder.encodeString(value.name)

            override fun deserialize(decoder: Decoder): Type = decoder.decodeString().let { raw ->
                values().firstOrNull { it.name == raw } ?: throw SerializationException("Unknown Type: $raw")
            }
        }
    }
}

/**
 * Describes login-ID types supported by the current OwnID application configuration.
 *
 * [supportedTypes] defines which login-ID types may be used and the priority order for resolving a raw login ID value.
 * [validationRegexes] contains optional validation overrides for supported types. A missing key or `null` value means
 * validation falls back to [LoginId.Type.classificationRegex].
 *
 * The constructor stores values as supplied. SDK configuration providers normalize before use by preserving the first
 * occurrence of each supported type, ignoring empty supported-type lists, and dropping validation entries for
 * unsupported types.
 */
public class LoginIdConfiguration(
    public val supportedTypes: List<LoginId.Type>,
    public val validationRegexes: Map<LoginId.Type, Regex?>
) {
    public companion object {
        public val DEFAULT_EMAIL: LoginIdConfiguration = LoginIdConfiguration(
            supportedTypes = listOf(LoginId.Type.Email),
            validationRegexes = mapOf(LoginId.Type.Email to null)
        )
    }
}

/**
 * Authentication method used during a login or registration flow.
 *
 * Serialized as a canonical string matching [toString]. Decoding accepts canonical values and legacy aliases for some
 * methods; unrecognized strings decode as [Unknown].
 */
@Serializable(with = AuthMethod.Serializer::class)
public sealed class AuthMethod {
    public object Otp : AuthMethod()

    public object Passkey : AuthMethod()

    public object MagicLink : AuthMethod()

    public object Password : AuthMethod()

    public object Deferred : AuthMethod()

    public object SocialGoogle : AuthMethod()

    public object SocialApple : AuthMethod()

    public object FaceKey : AuthMethod()

    public object Immediate : AuthMethod()

    public object Unknown : AuthMethod()

    override fun toString(): String = when (this) {
        Otp -> "otp"
        Passkey -> "passkey"
        MagicLink -> "magic-link"
        Password -> "password"
        Deferred -> "deferred"
        Immediate -> "immediate"
        Unknown -> "unknown"
        SocialGoogle -> "social-google"
        SocialApple -> "social-apple"
        FaceKey -> "facekey"
    }

    public object Serializer : KSerializer<AuthMethod> {
        private val aliases: Map<AuthMethod, List<String>> = mapOf(
            Passkey to listOf("passkey", "biometrics", "desktop-biometrics"), // V3 Compatibility
            Otp to listOf("otp", "email-fallback", "sms-fallback"), // V3 Compatibility
            MagicLink to listOf("magic-link"),
            Password to listOf("password"),
            Deferred to listOf("deferred"),
            SocialGoogle to listOf("social-google"),
            SocialApple to listOf("social-apple"),
            FaceKey to listOf("facekey"),
            Immediate to listOf("immediate"),
            Unknown to listOf("unknown"),
        )

        private val methodToCanonical: Map<AuthMethod, String> = aliases.mapValues { (_, list) -> list.first() }

        private val stringToMethod: Map<String, AuthMethod> = aliases.flatMap { (method, list) -> list.map { it to method } }.toMap()

        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("AuthMethod", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: AuthMethod) {
            encoder.encodeString(methodToCanonical[value] ?: "unknown")
        }

        override fun deserialize(decoder: Decoder): AuthMethod {
            val raw = decoder.decodeString()
            return stringToMethod[raw] ?: Unknown
        }
    }
}

/**
 * An authenticated user identified by [loginId] and the [authMethod] used.
 *
 * Public serialization includes the raw login ID value. Use [LoginId.maskedId] or [LoginId.toString] for logs and
 * debug output.
 */
@Serializable
public class User(public val loginId: LoginId, public val authMethod: AuthMethod)

/**
 * Result returned by a login or discover request.
 *
 * This Android model is not a public `@Serializable` wire model. SDK network calls map backend responses internally
 * before returning this result.
 *
 * [Success] means OwnID authentication succeeded and returned data for app session integration.
 *
 * [NoSession] carries exactly one mutually exclusive outcome when a successful login payload is not returned:
 * - [AuthRequired]
 * - [AccountNotFound]
 * - [AccountBlocked]
 */
public sealed interface LoginResponse {

    /**
     * Successful login carrying the [accessToken] and [sessionPayload] for session integration.
     *
     * Pass [sessionPayload] to your backend as part of session creation. Structured payload values remain JSON text. If
     * the server returns a plain string, this property contains that string value.
     *
     * [toString] redacts [sessionPayload] and uses the redacted [AccessToken.toString] output.
     */
    public class Success(public val accessToken: AccessToken, public val sessionPayload: String) : LoginResponse {
        override fun toString(): String = "Success(accessToken=$accessToken, sessionPayload='*')"
    }

    /**
     * A no-session login outcome.
     *
     * Carries an optional server-provided [reason] for why a successful login payload was not returned.
     */
    public sealed interface NoSession : LoginResponse {
        public val reason: String?
    }

    /**
     * Additional authentication is required before a successful login payload can be returned.
     *
     * Use [authRequirements] to determine the next operations needed to reach the target score.
     */
    public class AuthRequired(public val authRequirements: AuthRequirements, override val reason: String?) : NoSession {
        override fun toString(): String = "AuthRequired($authRequirements, reason=$reason)"
    }

    public class AccountNotFound(override val reason: String?) : NoSession {
        override fun toString(): String = "AccountNotFound(reason=$reason)"
    }

    public class AccountBlocked(override val reason: String?) : NoSession {
        override fun toString(): String = "AccountBlocked(reason=$reason)"
    }
}

/**
 * Describes the operations required to reach the authentication target score.
 *
 * The constructor stores values as supplied and does not enforce non-negative scores or sort [operations].
 *
 * @property targetScore Minimum cumulative score that must be reached.
 * @property operations Recommended operations that can be performed to reach the target score, in server-provided order.
 */
public class AuthRequirements(
    public val targetScore: Int,
    public val operations: List<OperationRequirement>
) {
    override fun toString(): String = "AuthRequirements(targetScore=$targetScore, operations=[${operations.joinToString()}])"

    /** Returns `true` when the sum of all operation scores meets or exceeds the [targetScore]. */
    public fun isTargetScoreAchievable(): Boolean {
        if (targetScore <= 0) return true
        if (operations.isEmpty()) return false
        return operations.sumOf { it.score } >= targetScore
    }
}
