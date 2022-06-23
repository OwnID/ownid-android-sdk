package com.ownid.sdk.exception

import android.content.Intent
import java.io.PrintWriter
import java.io.StringWriter

/**
 * General exception used by OwnID SDK.
 *
 * @param message   text message describing reason for exception
 * @param cause     original exception that is wrapped in [OwnIdException]
 */
public open class OwnIdException @JvmOverloads constructor(message: String, cause: Throwable? = null) :
    Exception(message, cause) {

    public companion object {
        private const val KEY_RESULT_ERROR = "com.ownid.sdk.intent.KEY_RESULT_ERROR"

        internal fun unwrapFromIntentOrThrow(resultData: Intent?): OwnIdException =
            resultData?.getSerializableExtra(KEY_RESULT_ERROR)?.let {
                (it as? OwnIdException) ?: OwnIdException("Unknown error: $it")
            } ?: throw OwnIdException("OwnIdException is not set")

        public fun map(message: String, cause: Throwable): OwnIdException {
            return if (cause is OwnIdException) cause else OwnIdException(message, cause)
        }
    }

    internal fun wrapInIntent(): Intent = Intent().putExtra(KEY_RESULT_ERROR, this)

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