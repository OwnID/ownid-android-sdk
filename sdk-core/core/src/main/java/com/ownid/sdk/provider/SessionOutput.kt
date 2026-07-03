package com.ownid.sdk.provider

/**
 * Host session payload returned from provider callbacks.
 *
 * @property session Host-defined session object. May be `null`.
 */
public class SessionOutput(
    public val session: Any?
)
