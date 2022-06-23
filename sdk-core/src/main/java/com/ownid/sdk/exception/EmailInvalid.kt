package com.ownid.sdk.exception

/**
 * Exception during OwnID flow when user email address is badly formatted.
 */
public class EmailInvalid : OwnIdException("The email address is badly formatted")