package com.ownid.sdk.model

/**
 * Server-issued challenge for a verification operation (OTP or magic link).
 *
 * Challenges returned by OwnID APIs are normalized while they are mapped into this model: [timeout] is zero or greater,
 * [ResendPolicy.debounce] is at least one second, and [Methods.Otp.length] is at least four digits. Direct construction
 * keeps the supplied values except for normalization performed by nested value types such as [Timeout].
 *
 * [toString] includes [channel]; [OperationChannel] redacts its channel value.
 *
 * @property challengeId Unique challenge identifier.
 * @property resendPolicy Policy controlling whether and how often codes can be resent.
 * @property timeout Timeout for the challenge.
 * @property attempts Maximum number of verification attempts allowed.
 * @property methods Available verification methods for this challenge.
 * @property channel Channel through which the challenge can be completed.
 */
public class VerificationChallenge(
    public val challengeId: ChallengeId,
    public val resendPolicy: ResendPolicy,
    public val timeout: Timeout,
    public val attempts: Int,
    public val methods: Methods,
    public val channel: OperationChannel
) {
    /**
     * Policy controlling whether and how often the verification code can be resent.
     *
     * The SDK API mapping normalizes [debounce] to at least one second. Direct construction keeps [allow], [attempts],
     * and [debounce] as supplied.
     *
     * @property allow Whether resend is currently allowed.
     * @property attempts Maximum number of resend attempts.
     * @property debounce Seconds to wait before another resend is allowed.
     */
    public class ResendPolicy(public val allow: Boolean, public val attempts: Int, public val debounce: Int) {
        override fun toString(): String = "ResendPolicy(allow=$allow, attempts=$attempts, debounce=$debounce)"

        override fun equals(other: Any?): Boolean =
            this === other || (other is ResendPolicy && allow == other.allow && attempts == other.attempts && debounce == other.debounce)

        override fun hashCode(): Int = ((allow.hashCode() * 31 + attempts) * 31) + debounce
    }

    /**
     * Available verification methods for this challenge.
     *
     * A usable challenge is expected to include at least one non-null method, but direct construction does not enforce
     * that requirement.
     */
    public class Methods(
        public val otp: Otp?,
        public val magicLink: MagicLink?
    ) {

        /**
         * OTP method configuration with the expected code [length].
         *
         * The SDK API mapping normalizes [length] to at least four. Direct construction keeps the supplied value.
         */
        public class Otp(public val length: Int) {
            override fun toString(): String = "Otp(length=$length)"
            override fun equals(other: Any?): Boolean = this === other || (other is Otp && length == other.length)
            override fun hashCode(): Int = length
        }

        public class MagicLink {
            override fun toString(): String = "MagicLink"
            override fun equals(other: Any?): Boolean = this === other || other is MagicLink
            override fun hashCode(): Int = javaClass.hashCode()
        }

        override fun toString(): String = "Methods(otp=$otp, magicLink=$magicLink)"
        override fun equals(other: Any?): Boolean = this === other || (other is Methods && otp == other.otp && magicLink == other.magicLink)
        override fun hashCode(): Int = 31 * (otp?.hashCode() ?: 0) + (magicLink?.hashCode() ?: 0)
    }

    override fun toString(): String =
        "VerificationChallenge(challengeId=$challengeId, timeout=$timeout, attempts=$attempts, methods=$methods, channel=$channel)"

    override fun equals(other: Any?): Boolean =
        this === other || (other is VerificationChallenge && challengeId == other.challengeId && resendPolicy == other.resendPolicy && timeout == other.timeout && attempts == other.attempts && methods == other.methods && channel == other.channel)

    override fun hashCode(): Int {
        var result = challengeId.hashCode()
        result = 31 * result + resendPolicy.hashCode()
        result = 31 * result + timeout.hashCode()
        result = 31 * result + attempts
        result = 31 * result + methods.hashCode()
        result = 31 * result + channel.hashCode()
        return result
    }
}

/**
 * The type of verification delivery method.
 *
 * [value] is the stable OwnID method string used in API payloads. Enum constant names are Kotlin names; [toString]
 * returns [value]. Invalid names passed to [valueOf] throw [IllegalArgumentException].
 */
public enum class VerificationMethod(public val value: String) {
    MAGIC_LINK("MagicLink"),

    OTP("Otp");

    override fun toString(): String = value
}
