package com.ownid.sdk.ui.capability

/**
 * Provides resolved strings for Boost widgets.
 *
 * Default SDK providers read Boost widget keys from server locale data and use [BoostWidgetStrings.DEFAULT] for any
 * missing key.
 */
public interface BoostWidgetStringsProvider : StringsProvider<BoostWidgetStrings, BoostWidgetStringsParams> {

    /** Repository that supplies complete Boost widget strings by applying embedded defaults. */
    public interface EmbeddedRepository : StringsProvider.EmbeddedRepository<BoostWidgetStrings>

    /** Repository that reads raw Boost widget string keys from server locale data. */
    public interface ServerRepository : StringsProvider.ServerRepository
}

/**
 * Parameters for Boost widget string resolution.
 *
 * Boost widget strings do not require per-call inputs.
 */
public class BoostWidgetStringsParams : StringsProvider.Params

/** Complete UI strings for Boost widgets. */
public class BoostWidgetStrings(
    /** Label for the skip-password button. */
    public val skipPassword: String,
    /** Separator text between the widget and alternative login options. */
    public val or: String
) : StringsProvider.Data {

    public companion object {
        /** Embedded English fallback strings used when server locale data is unavailable or incomplete. */
        public val DEFAULT: BoostWidgetStrings = BoostWidgetStrings(skipPassword = "Skip Password", or = "or")
    }

    override fun equals(other: Any?): Boolean = this === other || (other is BoostWidgetStrings && skipPassword == other.skipPassword && or == other.or)

    override fun hashCode(): Int = skipPassword.hashCode() * 31 + or.hashCode()
}
