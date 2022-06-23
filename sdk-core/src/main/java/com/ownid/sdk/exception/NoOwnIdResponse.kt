package com.ownid.sdk.exception

import com.ownid.sdk.internal.OwnIdResponse

/**
 * Exception when OwnID ViewModel does not have [OwnIdResponse] value set.
 */
public class NoOwnIdResponse : OwnIdException("No OwnIdResponse available. Login or Register flow must be run first")