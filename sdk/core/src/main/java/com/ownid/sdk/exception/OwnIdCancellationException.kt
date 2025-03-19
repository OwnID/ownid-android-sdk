package com.ownid.sdk.exception

import com.ownid.sdk.InternalOwnIdAPI

/**
 * General Cancellation exception that occurs when user cancelled some OwnID flow.
 */
@OptIn(InternalOwnIdAPI::class)
public class OwnIdCancellationException @InternalOwnIdAPI constructor(message: String, cause: Throwable? = null) :
    OwnIdException(message, cause)