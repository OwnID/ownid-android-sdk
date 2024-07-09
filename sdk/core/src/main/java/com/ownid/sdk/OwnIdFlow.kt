package com.ownid.sdk

import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import androidx.annotation.MainThread
import com.ownid.sdk.FlowResult.OnLogin
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.internal.component.OwnIdInternalLogger
import com.ownid.sdk.internal.component.events.Metadata
import com.ownid.sdk.internal.component.events.Metric
import com.ownid.sdk.internal.feature.webflow.OwnIdFlowFeature
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Represents the result of the OwnID flow.
 * This sealed class encapsulates the possible outcomes of the flow, providing different data depending on the result.
 *
 * @param T The type of the session object returned in the [OnLogin] result.
 */
public sealed class FlowResult<out T> {
    /**
     * Indicates that the user account was not found during the flow.
     *
     * @property loginId The login identifier used during the flow.
     * @property ownIdData An optional string containing additional OwnID Data.
     * @property authToken An optional OwnID authentication token.
     */
    public class OnAccountNotFound(public val loginId: String, public val ownIdData: String?, public val authToken: String?) : FlowResult<Nothing>()

    /**
     * Indicates successful login and provides the authenticated session.
     *
     * @property loginId The login identifier used for authentication.
     * @property session The authenticated session object of type [T].
     * @property authToken An OwnID authentication token.
     */
    public class OnLogin<T>(public val loginId: String, public val session: T, public val authToken: String) : FlowResult<T>()

    /**
     * Indicates that an error occurred during the OwnID flow.
     *
     * @property cause The [OwnIdException] representing the error that occurred.
     */
    public class OnError(public val cause: OwnIdException) : FlowResult<Nothing>()

    /**
     * Indicates that the OwnId flow was closed by the user.
     */
    public object OnClose : FlowResult<Nothing>()
}

/**
 * An interface for transforming session data into a specific type [T].
 * Implemented per integration.
 *
 * @param T The type to which the session data will be transformed.
 */
public interface SessionAdapter<out T> {
    /**
     * Transforms the given session string into an object of type [T] or throws an exception if transformation fails.
     *
     * @param session The session data as a String.
     * @return The transformed session object of type [T].
     * @throws [Exception] if an error occurs during execution.
     */
    @Throws
    public fun transformOrThrow(session: String): T
}

/**
 * Starts the OwnId flow using the provided [SessionAdapter] to handle session data.
 *
 * This function initiates the OwnId flow, which involves launching an Activity with WebView to interact with the OwnId.
 * The provided [SessionAdapter] is used to transform the session data received from the OwnId into a specific session type [T].
 *
 * @param T The type to which the session data will be transformed.
 * @param adapter The [SessionAdapter] used to transform the session data.
 * @param cancellationSignal An optional [CancellationSignal] to allow cancellation of the flow.
 * @param callback A callback function that receives the result of the flow as a [FlowResult] object.
 *
 * @throws IllegalArgumentException if called from a thread other than the main thread.
 */
@MainThread
@OptIn(InternalOwnIdAPI::class)
@Throws(IllegalArgumentException::class)
public fun <T> OwnIdInstance.start(
    adapter: SessionAdapter<T>,
    cancellationSignal: CancellationSignal? = null,
    callback: (FlowResult<T>) -> Unit
) {
    check(Looper.getMainLooper().isCurrentThread) { "Only main thread allowed" }

    runCatching {
        val ownIdCoreImpl = ownIdCore as OwnIdCoreImpl

        val resultReceiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle) {
                OwnIdInternalLogger.logD(this@start, "Start.onReceiveResult", "Invoked (resultCode: $resultCode)")

                if (cancellationSignal?.isCanceled == true) {
                    OwnIdInternalLogger.logD(this@start, "Start.onReceiveResult", "Flow request canceled")
                    ownIdCoreImpl.eventsService.sendMetric(Metric.Category.General, Metric.EventType.Track, "Flow Canceled")
                    return
                }

                val result = OwnIdFlowFeature.decodeResult(resultCode, resultData, adapter)

                if (result is FlowResult.OnError) {
                    OwnIdInternalLogger.logE(this@start, "decodeResult.getOrElse", result.cause.message, result.cause)
                    val metadata = Metadata(resultType = Metadata.ResultType.Failure(result::class.java.simpleName))
                    ownIdCoreImpl.eventsService.sendMetric(
                        Metric.Category.General, Metric.EventType.Error, "Flow Ended", metadata = metadata, errorMessage = result.cause.message
                    )
                } else {
                    val metadata = Metadata(resultType = Metadata.ResultType.Success(result::class.java.simpleName))
                    ownIdCoreImpl.eventsService.sendMetric(
                        Metric.Category.General, Metric.EventType.Track, "Flow Ended", metadata = metadata
                    )
                }

                callback.invoke(result)
            }
        }

        ownIdCoreImpl.eventsService.sendMetric(Metric.Category.General, Metric.EventType.Track, "Flow Started")

        val context = ownIdCoreImpl.applicationContext

        context.startActivity(OwnIdFlowFeature.createIntent(context, ownIdCore.instanceName, resultReceiver))

        cancellationSignal?.setOnCancelListener {
            OwnIdInternalLogger.logD(this, "Start", "Flow Request Canceled")
            ownIdCoreImpl.eventsService.sendMetric(Metric.Category.General, Metric.EventType.Track, "Flow Request Canceled")
            OwnIdFlowFeature.sendCloseRequest(context)
        }
    }.onFailure { cause ->
        val error = OwnIdException.map("Start launch failed: ${cause.message}", cause)
        OwnIdInternalLogger.logE(this, "Start", error.message, cause)

        if (cancellationSignal?.isCanceled == false) {
            callback.invoke(FlowResult.OnError(error))
        }
    }
}

/**
 * Starts a suspending OwnId flow function using the provided [SessionAdapter] and returns the result.
 *
 * Function provides a coroutine-based API for initiating the OwnId flow.
 * It suspends the coroutine until the flow completes and returns the result as a [FlowResult] object.
 *
 * @param T The type to which the session data will be transformed.
 * @param adapter The [SessionAdapter] used to transform the session data.
 * @return The result of the flow as a [FlowResult] object.
 *
 * @throws IllegalArgumentException if called from a thread other than the main thread.
 */
@MainThread
@Throws(IllegalArgumentException::class)
public suspend fun <T> OwnIdInstance.start(adapter: SessionAdapter<T>): FlowResult<T> =
    suspendCancellableCoroutine { continuation ->
        val canceller = CancellationSignal()
        continuation.invokeOnCancellation { canceller.cancel() }
        start(adapter, canceller) {
            if (continuation.isActive) continuation.resume(it)
        }
    }
