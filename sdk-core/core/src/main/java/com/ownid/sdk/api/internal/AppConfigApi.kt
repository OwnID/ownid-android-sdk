package com.ownid.sdk.api.internal

import com.ownid.sdk.api.ApiCapability
import com.ownid.sdk.api.ApiFailure
import com.ownid.sdk.api.ApiResult
import com.ownid.sdk.api.ApiUnexpectedFailure
import com.ownid.sdk.capability.AppConfigProvider
import com.ownid.sdk.model.AppConfig
import com.ownid.sdk.model.ErrorCode

/**
 * Internal app-configuration fetch boundary.
 *
 * This contract performs one network fetch and maps the endpoint response. [AppConfigProvider]
 * owns fallback, storage, retry, and stream emission semantics. Coroutine cancellation propagates to the caller.
 */
internal interface AppConfigApi : ApiCapability {

    /** Optional trace context for correlating the fetch with the caller's work. */
    class Params(
        internal val traceParent: String? = null
    )

    /** Returns the mapped configuration, a typed endpoint failure, or an unexpected failure. */
    suspend fun start(params: Params? = null): ApiResult<AppConfig, AppConfigApiFailure>
}

internal sealed interface AppConfigApiFailure : ApiFailure {
    /** Bad request returned by the app-configuration endpoint. */
    class BadRequest(override val errorCode: ErrorCode, override val message: String) : AppConfigApiFailure

    /** Transport, runtime, unhandled status, or response-shape failure. */
    class Unexpected(errorCode: ErrorCode, message: String, cause: ApiUnexpectedCause) : ApiUnexpectedFailure(errorCode, message, cause), AppConfigApiFailure
}
