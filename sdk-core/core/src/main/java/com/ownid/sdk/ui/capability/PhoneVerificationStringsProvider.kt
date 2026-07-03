package com.ownid.sdk.ui.capability

/**
 * Provides resolved strings for the phone verification UI.
 *
 * Default SDK providers read phone verification keys from server locale data and use [PhoneVerificationStrings.DEFAULT]
 * for any missing key.
 */
public interface PhoneVerificationStringsProvider : StringsProvider<PhoneVerificationStrings, PhoneVerificationStringsParams> {

    /** Repository that supplies complete phone verification strings by applying embedded defaults. */
    public interface EmbeddedRepository : StringsProvider.EmbeddedRepository<PhoneVerificationStrings>

    /** Repository that reads raw phone verification string keys from server locale data. */
    public interface ServerRepository : StringsProvider.ServerRepository
}

/**
 * Parameters for phone verification string resolution.
 *
 * Phone verification strings do not require per-call inputs.
 */
public class PhoneVerificationStringsParams : StringsProvider.Params

/** Complete UI strings for phone verification. */
public class PhoneVerificationStrings(
    /** Title text for the phone verification view. */
    public val title: String,
    /** Instructional message for the phone verification view. May include `%CODE_LENGTH%` and `%LOGIN_ID%`. */
    public val message: String,
    /** Description text for the verification step. */
    public val description: String,
    /** Label for the resend-code action. */
    public val resend: String,
    /** Label for the cancel action. */
    public val cancel: String,
    /** Label for the "not you?" link. */
    public val notYou: String
) : StringsProvider.Data {

    public companion object {
        /** Embedded English fallback strings used when server locale data is unavailable or incomplete. */
        public val DEFAULT: PhoneVerificationStrings = PhoneVerificationStrings(
            title = "Verify Your Phone Number",
            message = "We have sent you a %CODE_LENGTH%-digit code to\n%LOGIN_ID%",
            description = "Enter the code",
            resend = "Resend SMS",
            cancel = "Cancel",
            notYou = "Not you?",
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PhoneVerificationStrings) return false

        if (title != other.title) return false
        if (message != other.message) return false
        if (description != other.description) return false
        if (resend != other.resend) return false
        if (cancel != other.cancel) return false
        if (notYou != other.notYou) return false

        return true
    }

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + message.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + resend.hashCode()
        result = 31 * result + cancel.hashCode()
        result = 31 * result + notYou.hashCode()
        return result
    }
}
