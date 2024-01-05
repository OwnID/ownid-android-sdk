package com.ownid.sdk.exception

import com.ownid.sdk.InternalOwnIdAPI
import java.io.PrintWriter
import java.io.StringWriter

/**
 * General exception used by OwnID SDK.
 *
 * There are three types of errors that are inherited from [OwnIdException]:
 *  - [OwnIdFlowCanceled] - Error when OwnID flow was canceled by user.
 *  - [OwnIdUserError] - Error that is intended to be reported to the user.
 *  - [OwnIdIntegrationError] - Error that wraps Identity Management System errors OwnID integrates with.
 *
 * @param message   Text message describing reason for exception.
 * @param cause     Original exception that is wrapped in [OwnIdException].
 */
public open class OwnIdException @JvmOverloads @InternalOwnIdAPI constructor(message: String, cause: Throwable? = null) :
    Exception(message, cause) {

    @InternalOwnIdAPI
    public companion object {
        @InternalOwnIdAPI
        public fun map(message: String, cause: Throwable): OwnIdException =
            if (cause is OwnIdException) cause else OwnIdException(message, cause)
    }

    @InternalOwnIdAPI
    public open fun toMap(): Map<String, Any?> = mapOf(
        "className" to javaClass.simpleName,
        "message" to message,
        "cause" to cause?.toMap(),
        "stackTrace" to StringWriter().also { printStackTrace(PrintWriter(it)) }.toString()
    )

    private fun Throwable.toMap(): Map<String, Any?> = mapOf(
        "className" to javaClass.simpleName,
        "message" to message,
        "cause" to cause?.toMap(),
        "stackTrace" to StringWriter().also { printStackTrace(PrintWriter(it)) }.toString()
    )
}