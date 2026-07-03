package com.ownid.sdk.ui.capability

/**
 * Provides resolved strings for the email verification UI.
 *
 * Default SDK providers read email verification keys from server locale data and use [EmailVerificationStrings.DEFAULT]
 * for any missing key.
 */
public interface EmailVerificationStringsProvider : StringsProvider<EmailVerificationStrings, EmailVerificationStringsParams> {

    /** Repository that supplies complete email verification strings by applying embedded defaults. */
    public interface EmbeddedRepository : StringsProvider.EmbeddedRepository<EmailVerificationStrings>

    /** Repository that reads raw email verification string keys from server locale data. */
    public interface ServerRepository : StringsProvider.ServerRepository
}

/**
 * Parameters for email verification string resolution.
 *
 * Email verification strings do not require per-call inputs.
 */
public class EmailVerificationStringsParams : StringsProvider.Params

/** Complete UI strings for email verification. */
public class EmailVerificationStrings(
    /** Title text for the email verification view. */
    public val title: String,
    /** Instructional message for the email verification view. May include `%CODE_LENGTH%` and `%LOGIN_ID%`. */
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
        public val DEFAULT: EmailVerificationStrings = EmailVerificationStrings(
            title = "Verify Your Email",
            message = "An email with a %CODE_LENGTH%-digit code was just sent to\n%LOGIN_ID%",
            description = "Enter the code",
            resend = "Resend email",
            cancel = "Cancel",
            notYou = "Not you?",
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EmailVerificationStrings) return false

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
