package com.ownid.sdk

import android.os.CancellationSignal
import androidx.annotation.MainThread
import com.gigya.android.sdk.session.SessionInfo
import com.ownid.sdk.exception.GigyaException
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.internal.component.OwnIdInternalLogger
import com.ownid.sdk.internal.toGigyaError
import com.ownid.sdk.internal.toGigyaSession
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume

/**
 * A [SessionAdapter] implementation for transforming Gigya session data into a [SessionInfo] object.
 */
@OptIn(InternalOwnIdAPI::class)
private object GigyaSessionAdapter : SessionAdapter<SessionInfo> {
    @Throws
    override fun transformOrThrow(session: String): SessionInfo =
        runCatching {
            JSONObject(session).apply {
                toGigyaSession()?.let { session -> return@runCatching session }
                toGigyaError()?.let { gigyaError ->
                    throw GigyaException(gigyaError, "[${gigyaError.errorCode}] ${gigyaError.localizedMessage}")
                }
            }
            throw OwnIdException("Unexpected data in 'session'")
        }.recoverCatching {
            val error = OwnIdException.map("Gigya session adapter error: ${it.message}", it)
            OwnIdInternalLogger.logW(this, "GigyaSessionAdapter", error.message, error)
            throw error
        }.getOrThrow()
}

/**
 * Starts the OwnID flow using the default [GigyaSessionAdapter] to handle session data.
 *
 * This function initiates the OwnID flow for Gigya integration, using the [GigyaSessionAdapter] to transform the session data
 * received from the OwnID service into a [SessionInfo] object.
 *
 * @param adapter The [SessionAdapter] used to transform the session data. Defaults to [GigyaSessionAdapter].
 * @param cancellationSignal An optional [CancellationSignal] to allow cancellation of the flow.
 * @param callback A callback function that receives the result of the flow as a [FlowResult] object.
 *
 * @throws IllegalArgumentException if called from a thread other than the main thread.
 */
@MainThread
@Throws(IllegalArgumentException::class)
@JvmOverloads
public fun OwnIdGigya.start(
    adapter: SessionAdapter<SessionInfo> = GigyaSessionAdapter,
    cancellationSignal: CancellationSignal? = null,
    callback: (FlowResult<SessionInfo>) -> Unit
) {
    (this as OwnIdInstance).start(adapter, cancellationSignal, callback)
}

/**
 * Starts a suspending OwnId flow function using the [GigyaSessionAdapter] to handle session data and returns the result.
 *
 * This suspending function provides a coroutine-based API for initiating the OwnID flow for Gigya integration.
 * It suspends the coroutine until the flow completes and returns the result as a [FlowResult] object.
 *
 * @param adapter The [SessionAdapter] used to transform the session data. Defaults to [GigyaSessionAdapter].
 *
 * @return The result of the flow as a [FlowResult].
 *
 * @throws IllegalArgumentException if called from a thread other than the main thread.
 */
@MainThread
@Throws(IllegalArgumentException::class)
public suspend fun OwnIdGigya.start(adapter: SessionAdapter<SessionInfo> = GigyaSessionAdapter): FlowResult<SessionInfo> =
    suspendCancellableCoroutine { continuation ->
        val canceller = CancellationSignal()
        continuation.invokeOnCancellation { canceller.cancel() }
        start(adapter, canceller) {
            if (continuation.isActive) continuation.resume(it)
        }
    }