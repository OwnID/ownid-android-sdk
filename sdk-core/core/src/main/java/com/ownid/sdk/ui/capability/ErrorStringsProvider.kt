package com.ownid.sdk.ui.capability

import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.ErrorStrings

/**
 * Provides resolved error messages keyed by [ErrorCode].
 *
 * Default SDK providers read error keys from server locale data using the corresponding [ErrorCode] values and use
 * [ErrorStrings.DEFAULT] for any missing key.
 */
public interface ErrorStringsProvider : StringsProvider<ErrorStrings, ErrorStringsParams> {

    /** Repository that supplies complete error strings by applying embedded defaults. */
    public interface EmbeddedRepository : StringsProvider.EmbeddedRepository<ErrorStrings>

    /** Repository that reads raw error string keys from server locale data. */
    public interface ServerRepository : StringsProvider.ServerRepository
}

/**
 * Parameters for error string resolution.
 *
 * Error strings do not require per-call inputs.
 */
public class ErrorStringsParams : StringsProvider.Params
