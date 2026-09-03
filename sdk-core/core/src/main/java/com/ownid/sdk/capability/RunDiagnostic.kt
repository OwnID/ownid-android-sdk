package com.ownid.sdk.capability

import com.ownid.sdk.logger.ServerLogger
import kotlinx.serialization.Serializable

/** Process-wide capability that rotates and optionally reports the previous OwnID-initialized run. */
internal interface RunDiagnostic : Capability {

    /**
     * On the first invocation, rotates the persisted run record and reports a valid previous run only if that invocation supplies
     * [serverLogger]; later invocations are ignored.
     */
    fun reportPreviousRun(serverLogger: ServerLogger?)

    @Serializable
    class PreviousRun(
        val correlationId: String,
        val processId: Int,
        val exit: Exit? = null
    ) {
        @Serializable
        class Exit(
            val processName: String,
            val reasonCode: Int,
            val timestamp: Long,
            val statusCode: Int,
            val importanceCode: Int
        )
    }
}
