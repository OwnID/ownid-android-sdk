package com.ownid.sdk.event

import com.ownid.sdk.exception.OwnIdException

/**
 * Sealed class with events for OwnID login flow
 */
public sealed class OwnIdLoginEvent : OwnIdEvent {
    /**
     * Class representing busy status during OwnID login flow.
     *
     * @property isBusy  Set to true if OwnID is busy with waiting or processing data.
     */
    public class Busy(public val isBusy: Boolean) : OwnIdLoginEvent()

    /**
     * Object representing login event in OwnID login flow.
     * Event triggered when user successfully completes login with OwnID.
     *
     * @property authType   A string describing the type of authentication that was used during OwnID flow
     */
    public class LoggedIn(public val authType: String) : OwnIdLoginEvent()

    /**
     * Class representing error events in OwnID login flow.
     * Event triggered when an error occurs during OwnID login flow.
     *
     * @property cause  Holds reason for this error. See [OwnIdException]
     */
    public class Error(public val cause: OwnIdException) : OwnIdLoginEvent()

    override fun toString(): String = "OwnIdLoginEvent.${javaClass.simpleName}"
}