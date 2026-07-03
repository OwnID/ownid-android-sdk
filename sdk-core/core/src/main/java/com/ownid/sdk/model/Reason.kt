package com.ownid.sdk.model

/**
 * Describes why an operation was canceled or abandoned.
 *
 * Reasons are stable categories for cancellation handling and analytics. Some reasons carry optional diagnostic text
 * that is included in [toString] when present and is not intended for exact string matching.
 */
public sealed interface Reason {

    public class Timeout : Reason {
        override fun toString(): String = "timeout"
    }

    public class UserClose(public val details: String? = null) : Reason {
        override fun toString(): String = if (details.isNullOrEmpty()) "userClose" else "userClose: $details"
    }

    public class MoveToOtherChallenge : Reason {
        override fun toString(): String = "moveToOtherChallenge"
    }

    public class SystemError(public val details: String? = null) : Reason {
        override fun toString(): String = if (details.isNullOrEmpty()) "systemError" else "systemError: $details"
    }

    public class Unknown(public val details: String? = null) : Reason {
        override fun toString(): String = if (details.isNullOrEmpty()) "unknown" else "unknown: $details"
    }

    public class AlreadyExists : Reason {
        override fun toString(): String = "alreadyExists"
    }
}
