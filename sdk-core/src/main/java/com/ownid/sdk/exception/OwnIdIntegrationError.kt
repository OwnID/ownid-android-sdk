package com.ownid.sdk.exception

import com.ownid.sdk.InternalOwnIdAPI

/**
 * General error for wrapping Identity Management System errors OwnID integrates with.
 * The exact errors are defined per integration.
 *
 * @param message       Text message describing reason for error.
 * @param cause         Original exception that is wrapped in [OwnIdIntegrationError]
 */
@OptIn(InternalOwnIdAPI::class)
public open class OwnIdIntegrationError(message: String, cause: Throwable? = null) : OwnIdException(message, cause)