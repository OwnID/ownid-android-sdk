package com.ownid.sdk.exception

import android.content.Intent
import android.os.Build
import com.ownid.sdk.InternalOwnIdAPI
import java.io.PrintWriter
import java.io.StringWriter

/**
 * General exception used by OwnID SDK.
 *
 * @param message   text message describing reason for exception
 * @param cause     original exception that is wrapped in [OwnIdException]
 */
public open class OwnIdException @JvmOverloads constructor(message: String, cause: Throwable? = null) : Exception(message, cause) {

    @InternalOwnIdAPI
    @Suppress("DEPRECATION")
    public companion object {
        private const val KEY_RESULT_ERROR = "com.ownid.sdk.intent.KEY_RESULT_ERROR"

        internal fun unwrapFromIntentOrThrow(resultData: Intent?): OwnIdException {
            resultData != null || throw OwnIdException("OwnIdException is not set")

            val value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                resultData!!.getSerializableExtra(KEY_RESULT_ERROR, Throwable::class.java)
            else
                resultData!!.getSerializableExtra(KEY_RESULT_ERROR)

            value != null || throw OwnIdException("OwnIdException is not set")

            return if (value is OwnIdException) value else throw OwnIdException("Unknown error: $value")
        }

        public fun map(message: String, cause: Throwable): OwnIdException {
            return if (cause is OwnIdException) cause else OwnIdException(message, cause)
        }
    }

    @InternalOwnIdAPI
    internal fun wrapInIntent(): Intent = Intent().putExtra(KEY_RESULT_ERROR, this)

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