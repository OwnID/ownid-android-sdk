package com.ownid.sdk.exception

/**
 * Exception with server error message.
 *
 * @param message   text message describing reason for exception
 */
public class ServerError(message: String) : OwnIdException(message)