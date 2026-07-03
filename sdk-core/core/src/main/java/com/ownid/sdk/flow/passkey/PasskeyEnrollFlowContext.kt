@file:JvmName("OwnIdPasskeyEnrollFlowContexts")

package com.ownid.sdk.flow.passkey

import com.ownid.sdk.capability.Capability
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.Context
import com.ownid.sdk.model.ProofToken

@DslMarker
internal annotation class OwnIdPasskeyEnrollFlowContextDsl

/**
 * Creates a [PasskeyEnrollFlowContext] using a DSL builder.
 *
 * Set [PasskeyEnrollFlowContext.Builder.accessToken] when the access token is not already available from the current
 * [Context]. Set [PasskeyEnrollFlowContext.Builder.proofToken] to skip local passkey attestation and enroll an
 * already-created passkey proof. [PasskeyEnrollFlowContext.Builder.headless] is reserved and currently has no observable
 * effect; it does not hide platform passkey UI when attestation is required.
 */
@Suppress("NOTHING_TO_INLINE")
@JvmSynthetic
public inline fun PasskeyEnrollFlowContext(noinline block: PasskeyEnrollFlowContext.Builder.() -> Unit): PasskeyEnrollFlowContext =
    PasskeyEnrollFlowContext.Builder().apply(block).build()

/**
 * Immutable configuration for the passkey enrollment flow.
 *
 * Carries the access token, proof token, and headless flag. Public contexts are immutable; use the
 * [PasskeyEnrollFlowContext] DSL function or [Builder] to create a new value for each enrollment attempt.
 *
 * Values are per-run inputs. The context does not create an app session, store tokens, or validate token pairing at
 * build time. Missing or unusable values are reported by [PasskeyEnrollFlow.availability] or by the terminal
 * [PasskeyEnrollFlowFailure] from [PasskeyEnrollFlow.start].
 *
 * If [proofToken] is absent, the flow starts passkey attestation before enrollment. If [proofToken] is present, the
 * flow starts enrollment directly. When the flow runs attestation itself, the attestation proof token is consumed
 * internally and attestation `ownIdData` is not returned by [PasskeyEnrollFlow.Response].
 *
 * Build it via the [PasskeyEnrollFlowContext] DSL function or [Builder].
 */
public class PasskeyEnrollFlowContext private constructor(
    private val values: Map<Key<*>, Any?>
) : Capability.Params {
    internal interface Key<T : Any>

    private object AccessTokenKey : Key<AccessToken>
    private object ProofTokenKey : Key<ProofToken>
    private object HeadLessKey : Key<Boolean>
    private object TraceParentKey : Key<String>

    /** Access token proving the user is already authenticated; falls back to the current [Context] when `null`. */
    public val accessToken: AccessToken?
        get() = values[AccessTokenKey] as? AccessToken

    /** Proof token from a prior attestation or verification step; when set, the flow skips local passkey attestation. */
    public val proofToken: ProofToken?
        get() = values[ProofTokenKey] as? ProofToken

    /**
     * Reserved for future use and currently has no observable effect.
     *
     * This does not suppress passkey attestation UI when no proof token is provided.
     */
    public val headless: Boolean?
        get() = values[HeadLessKey] as? Boolean

    internal val traceParent: String?
        get() = values[TraceParentKey] as? String

    internal fun toBuilder(): Builder = Builder().apply {
        accessToken = this@PasskeyEnrollFlowContext.accessToken
        proofToken = this@PasskeyEnrollFlowContext.proofToken
        headless = this@PasskeyEnrollFlowContext.headless
        traceParent = this@PasskeyEnrollFlowContext.traceParent
    }

    /** DSL builder for [PasskeyEnrollFlowContext]. All properties are optional. */
    @OwnIdPasskeyEnrollFlowContextDsl
    public class Builder {
        /** Access token proving the user is already authenticated; falls back to the current [Context] when `null`. */
        public var accessToken: AccessToken? = null

        /** Proof token from a prior attestation or verification step; when set, the flow skips local passkey attestation. */
        public var proofToken: ProofToken? = null

        /**
         * Reserved for future use and currently has no observable effect.
         *
         * This does not suppress passkey attestation UI when no proof token is provided.
         */
        public var headless: Boolean? = null
        internal var traceParent: String? = null

        /** Creates a [PasskeyEnrollFlowContext] from the builder's current state. */
        public fun build(): PasskeyEnrollFlowContext = PasskeyEnrollFlowContext(
            buildMap {
                put(AccessTokenKey, accessToken)
                put(ProofTokenKey, proofToken)
                put(HeadLessKey, headless)
                put(TraceParentKey, traceParent)
            }
        )
    }

    internal fun copy(block: Builder.() -> Unit = {}): PasskeyEnrollFlowContext = toBuilder().apply(block).build()

    override fun toString(): String {
        val entries = buildList {
            accessToken?.let { add("accessToken=$it") }
            proofToken?.let { add("proofToken=$it") }
            headless?.let { add("headless=$it") }
        }.joinToString()
        return "PasskeyEnrollFlowContext($entries)"
    }
}
