package com.ownid.sdk.internal.feature.webflow

import androidx.annotation.RestrictTo
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.JsonSerializable

/**
 *  A wrapper interface for functions provided by the customer.
 *  This allows for consistent handling and processing of various custom operations.
 */
@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface OwnIdFlowWrapper<out R : JsonSerializable> {

    /**
     * Invokes the wrapped function with the provided payload.
     *
     * @param payload The payload associated with the function.
     * @return The result of the function invocation.
     */
    public suspend fun invoke(payload: OwnIdFlowPayload): R?
}