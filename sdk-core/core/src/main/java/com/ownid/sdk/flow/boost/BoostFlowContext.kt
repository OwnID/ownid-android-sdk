@file:JvmName("OwnIdBoostFlowContexts")

package com.ownid.sdk.flow.boost

import androidx.annotation.RestrictTo
import com.ownid.sdk.analytics.FlowInfo
import com.ownid.sdk.flow.boost.BoostFlowContext.Companion.Empty
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.AuthMethod
import com.ownid.sdk.model.Context
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.LoginResponse
import com.ownid.sdk.model.OperationRequirement
import com.ownid.sdk.model.OperationType
import com.ownid.sdk.model.ProofToken
import com.ownid.sdk.model.shorten

@DslMarker
internal annotation class OwnIdBoostFlowContextDsl

/**
 * Creates a [BoostFlowContext] using a DSL builder.
 *
 * Use this function when a flow needs caller-supplied login-ID hints or last-user behavior:
 *
 * ```
 * val context = BoostFlowContext {
 *     loginId("user@example.com", LoginId.Type.Email)
 *     ignoreLastUser = true
 * }
 * ```
 */
@Suppress("NOTHING_TO_INLINE")
@JvmSynthetic
public inline fun BoostFlowContext(noinline block: BoostFlowContext.Builder.() -> Unit): BoostFlowContext =
    BoostFlowContext.Builder().apply(block).build()

/**
 * Immutable configuration for a Boost authentication flow.
 *
 * Carries optional login-ID hints and behavioral flags that customize flow execution. A built context does not change;
 * use [BoostFlowContext] or [Builder] to create a new value when a run needs different inputs.
 *
 * Boost uses an available access token before login-ID hints. When an access token is available from the Boost context
 * or current [Context], the flow resolves the login ID from that token and continues token-first.
 *
 * If no access token is available, login-ID hints use this precedence:
 * - [loginId] from this context when set.
 * - Raw input supplied through [Builder.loginId] when no type is provided.
 * - Typed login ID, then raw login ID, from the current [Context].
 * - The stored last user, unless [ignoreLastUser] is `true`.
 *
 * Typed login IDs are used as concrete identifiers. Raw login IDs are text hints that OwnID can classify later.
 *
 * Build it via the [BoostFlowContext] DSL function or [Builder], or use [Empty] for default behavior.
 */
@OptIn(InternalOwnIdAPI::class)
public class BoostFlowContext private constructor(
    private val values: Map<Key<*>, Any>
) {
    private interface Key<T : Any>

    private object LoginIdKey : Key<LoginId>
    private object RawLoginIdKey : Key<String>
    private object AccessTokenKey : Key<AccessToken>
    private object IgnoreLastUserKey : Key<Boolean>
    private object SourceKey : Key<FlowInfo.Source>
    private object ProofTokenKey : Key<ProofToken>
    private object OwnIdDataKey : Key<String>
    private object SessionPayloadKey : Key<String>
    private object AuthMethodKey : Key<AuthMethod>
    private object TraceParentKey : Key<String>
    private object AuthRequiredResponseKey : Key<LoginResponse.AuthRequired>
    private object RequestedOpsKey : Key<Map<OperationType, Boolean>>

    /**
     * Typed login identifier hint for the flow.
     *
     * Set this when the app already knows both the user's identifier and type. When this value is `null`, no access
     * token is available, and no raw login ID was supplied, the flow may use the current [Context] login ID or the
     * stored last user when [ignoreLastUser] is not `true`.
     */
    public val loginId: LoginId?
        get() = values[LoginIdKey] as? LoginId

    internal val rawLoginId: String?
        get() = values[RawLoginIdKey] as? String

    internal val accessToken: AccessToken?
        get() = values[AccessTokenKey] as? AccessToken

    /**
     * Set to `true` when this flow should not suggest the stored last user. Explicit login IDs
     * supplied by the app or by the current [Context] can still pre-fill login-ID collection. Access-token inputs still
     * take precedence over login-ID hints.
     */
    public val ignoreLastUser: Boolean?
        get() = values[IgnoreLastUserKey] as? Boolean

    internal val source: FlowInfo.Source?
        get() = values[SourceKey] as? FlowInfo.Source

    internal val proofToken: ProofToken?
        get() = values[ProofTokenKey] as? ProofToken

    internal val ownIdData: String?
        get() = values[OwnIdDataKey] as? String

    internal val sessionPayload: String?
        get() = values[SessionPayloadKey] as? String

    internal val authMethod: AuthMethod?
        get() = values[AuthMethodKey] as? AuthMethod

    internal val traceParent: String?
        get() = values[TraceParentKey] as? String

    internal val authRequiredResponse: LoginResponse.AuthRequired?
        get() = values[AuthRequiredResponseKey] as? LoginResponse.AuthRequired

    internal val requestedOps: Map<OperationType, Boolean>?
        @Suppress("UNCHECKED_CAST")
        get() = values[RequestedOpsKey] as? Map<OperationType, Boolean>

    public companion object {
        /** A context with no flow-context overrides. */
        public val Empty: BoostFlowContext = Builder().build()
    }

    internal fun toBuilder(): Builder = Builder().apply {
        loginId = this@BoostFlowContext.loginId
        rawLoginId = this@BoostFlowContext.rawLoginId
        accessToken = this@BoostFlowContext.accessToken
        ignoreLastUser = this@BoostFlowContext.ignoreLastUser
        source = this@BoostFlowContext.source
        proofToken = this@BoostFlowContext.proofToken
        ownIdData = this@BoostFlowContext.ownIdData
        sessionPayload = this@BoostFlowContext.sessionPayload
        authMethod = this@BoostFlowContext.authMethod
        traceParent = this@BoostFlowContext.traceParent
        authRequiredResponse = this@BoostFlowContext.authRequiredResponse
        requestedOps = this@BoostFlowContext.requestedOps
    }

    /** DSL builder for [BoostFlowContext]. All properties are optional; unset values use flow defaults. */
    @OwnIdBoostFlowContextDsl
    public class Builder {
        /**
         * Typed login identifier hint for the flow.
         *
         * Set this when the app already knows both the user's identifier and type. Assigning a non-null value clears
         * any raw login-ID hint previously supplied through [loginId].
         */
        public var loginId: LoginId? = null
            set(value) {
                field = value
                if (value != null) rawLoginId = null
            }

        internal var rawLoginId: String? = null

        /**
         * Sets the login ID from app input.
         *
         * Pass [type] when the app knows whether the value is an email, phone number, or username. Leave [type] `null`
         * when the app only has the text value and wants OwnID to classify it later. A typed value takes precedence
         * over a raw value. Calling this function clears any previously assigned [loginId] when [type] is `null`, and
         * clears the raw value when [type] is provided.
         */
        public fun loginId(id: String, type: LoginId.Type? = null) {
            if (type == null) {
                loginId = null
                rawLoginId = id
            } else {
                loginId = LoginId(id, type)
                rawLoginId = null
            }
        }

        internal var accessToken: AccessToken? = null

        /**
         * When `true`, ignores the stored last user.
         *
         * Explicit typed or raw login-ID values supplied on this builder, and values from the current [Context],
         * can still pre-fill login-ID collection. Access-token inputs still take precedence over login-ID hints.
         */
        public var ignoreLastUser: Boolean? = null

        /**
         * Internal source label for flow-level telemetry.
         *
         * Intended for SDK-owned integrations. Leave `null` to use the default explicit source.
         */
        @InternalOwnIdAPI
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public var source: FlowInfo.Source? = null

        internal var proofToken: ProofToken? = null
        internal var ownIdData: String? = null
        internal var sessionPayload: String? = null
        internal var authMethod: AuthMethod? = null
        internal var traceParent: String? = null
        internal var authRequiredResponse: LoginResponse.AuthRequired? = null
        internal var requestedOps: Map<OperationType, Boolean>? = null

        internal fun addSucceedOperation(operationType: OperationType) {
            val current = requestedOps.orEmpty()
            if (!current.containsKey(operationType)) {
                requestedOps = current + (operationType to true)
            }
        }

        internal fun addFailedOperation(operationType: OperationType) {
            val current = requestedOps.orEmpty()
            if (!current.containsKey(operationType)) {
                requestedOps = current + (operationType to false)
            }
        }

        /** Creates a [BoostFlowContext] from the builder's current state. */
        public fun build(): BoostFlowContext {
            val map = buildMap {
                loginId?.let { put(LoginIdKey, it) }
                if (loginId == null) rawLoginId?.let { put(RawLoginIdKey, it) }
                accessToken?.let { put(AccessTokenKey, it) }
                ignoreLastUser?.let { put(IgnoreLastUserKey, it) }
                source?.let { put(SourceKey, it) }
                proofToken?.let { put(ProofTokenKey, it) }
                ownIdData?.let { put(OwnIdDataKey, it) }
                sessionPayload?.let { put(SessionPayloadKey, it) }
                authMethod?.let { put(AuthMethodKey, it) }
                traceParent?.let { put(TraceParentKey, it) }
                authRequiredResponse?.let { put(AuthRequiredResponseKey, it) }
                requestedOps?.let { put(RequestedOpsKey, it) }
            }
            return BoostFlowContext(map)
        }
    }

    internal fun copy(block: Builder.() -> Unit = {}): BoostFlowContext = toBuilder().apply(block).build()

    internal fun isTargetScoreAchieved(): Boolean {
        val recommendation = authRequiredResponse?.authRequirements ?: return false
        val succeededScore = requestedOps
            ?.filterValues { it }
            ?.keys
            ?.sumOf { succeededType -> recommendation.operations.firstOrNull { it.type == succeededType }?.score ?: 0 }
            ?: 0
        return succeededScore >= recommendation.targetScore
    }

    internal fun getNextOperationType(): List<OperationType>? {
        val recommendation = authRequiredResponse?.authRequirements ?: return null
        val passedTypes = requestedOps?.keys ?: emptySet()
        val currentLoginId = loginId
        return recommendation.operations
            .filter { it.type !in passedTypes }
            .filter { it.isSelectableBoostAuthOperation(currentLoginId) }
            .map { it.type }
            .ifEmpty { null }
    }

    override fun toString(): String {
        val entries = buildList {
            loginId?.let { add("loginId" to it) }
            rawLoginId?.let { add("rawLoginId" to it.shorten()) }
            accessToken?.let { add("accessToken" to it) }
            ignoreLastUser?.let { add("ignoreLastUser" to it) }
            source?.let { add("source" to it) }
            proofToken?.let { add("proofToken" to it) }
            ownIdData?.let { add("ownIdData" to it.shorten()) }
            sessionPayload?.let { add("sessionPayload" to it.shorten()) }
            authMethod?.let { add("authMethod" to it) }
            traceParent?.let { add("traceParent" to it) }
            authRequiredResponse?.let { add("authRequiredResponse" to it) }
            requestedOps?.let { add("requestedOps" to it) }
        }
        val content = entries.joinToString(", ") { (key, value) -> "$key=$value" }
        return "FlowContext($content)"
    }
}

private fun OperationRequirement.isSelectableBoostAuthOperation(loginId: LoginId?): Boolean = when (type) {
    OperationType.PASSKEY_CREATION -> true
    OperationType.PASSKEY_AUTH -> true
    OperationType.EMAIL_VERIFICATION -> supports(loginId, LoginId.Type.Email)
    OperationType.PHONE_NUMBER_VERIFICATION -> supports(loginId, LoginId.Type.PhoneNumber)
    else -> false
}

private fun OperationRequirement.supports(loginId: LoginId?, targetType: LoginId.Type): Boolean =
    loginId == null || loginId.type == targetType || (loginId.type == LoginId.Type.UserName && channels?.isNotEmpty() == true)
