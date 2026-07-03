package com.ownid.sdk.api.internal

import com.ownid.sdk.analytics.UserJourneySummary
import com.ownid.sdk.api.ApiCapability
import com.ownid.sdk.api.ApiFailure
import com.ownid.sdk.api.ApiResult
import com.ownid.sdk.api.ApiUnexpectedFailure
import com.ownid.sdk.model.ErrorCode

/**
 * Internal journey-event reporting boundary.
 *
 * Reporting success or failure is diagnostic only and must not decide operation or flow results. The caller owns the
 * journey summary and any fallback behavior. Coroutine cancellation propagates to the caller.
 */
internal interface EventsApi : ApiCapability {

    /** Caller-owned journey summary and optional trace context for the reporting call. */
    class Params(val userJourney: UserJourneySummary, val traceParent: String? = null)

    /** Returns success when reporting is accepted, a typed endpoint failure, or an unexpected failure. */
    suspend fun start(params: Params): ApiResult<Unit, EventsApiFailure>
}

internal sealed interface EventsApiFailure : ApiFailure {
    /** Bad request returned by the event-reporting endpoint. */
    class BadRequest(override val errorCode: ErrorCode, override val message: String) : EventsApiFailure

    /** Transport, runtime, unhandled status, or response-shape failure. */
    class Unexpected(errorCode: ErrorCode, message: String, cause: ApiUnexpectedCause) : ApiUnexpectedFailure(errorCode, message, cause), EventsApiFailure
}
