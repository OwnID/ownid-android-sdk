package com.ownid.sdk.api.internal

import com.ownid.sdk.api.ApiUnexpectedFailure

/**
 * Internal cause category for [ApiUnexpectedFailure].
 *
 * The category supports SDK diagnostics and server logging. It is not part of the public app-facing recovery contract.
 */
internal sealed interface ApiUnexpectedCause {
    /** Transport failure returned by [com.ownid.sdk.capability.Network] before a usable HTTP response was available. */
    class Network(val failure: com.ownid.sdk.capability.Network.Response.Fail.NetworkError) : ApiUnexpectedCause

    /** HTTP response or successful response body that did not match the endpoint contract handled by the SDK. */
    class ApiContract(val failure: com.ownid.sdk.capability.Network.Response.Fail, val error: Throwable? = null) : ApiUnexpectedCause

    /** Local SDK setup or execution failure outside the HTTP response mapping layer. */
    class Runtime(val error: Throwable) : ApiUnexpectedCause
}
