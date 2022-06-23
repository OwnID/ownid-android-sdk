package com.ownid.sdk.event

import com.ownid.sdk.exception.OwnIdException

/**
 * Sealed class with events for OwnID registration flow
 */
public sealed class OwnIdRegisterEvent : OwnIdEvent {

    /**
     * Class representing busy status during OwnID registration flow.
     *
     * @property isBusy  Set to true if OwnID is busy with waiting or processing data.
     */
    public class Busy(public val isBusy: Boolean) : OwnIdRegisterEvent()

    /**
     * Class representing ready-to-register state during OwnID registration flow.
     * Event triggered when user successfully completes OwnID registration process in web browser. The OwnID SDK is waiting for
     * user to enter name and email to finish registration flow.
     *
     * @property loginId  May contain user login id that was used in OwnID Web App. Will be empty if no login id was set.
     */
    public class ReadyToRegister(public val loginId: String) : OwnIdRegisterEvent()

    /**
     * Object representing undo event in OwnID registration flow.
     * Event triggered when user select "Undo" option in ready-to-register state.
     */
    public object Undo : OwnIdRegisterEvent()

    /**
     * Object representing login event in OwnID registration flow.
     * Event triggered when user successfully completes registration with OwnID and is logged in with OwnID.
     */
    public object LoggedIn : OwnIdRegisterEvent()

    /**
     * Class representing error events in OwnID registration flow.
     * Event triggered when an error occurs during OwnID registration flow.
     *
     * @property cause  Holds reason for this error. See [OwnIdException]
     */
    public class Error(public val cause: OwnIdException) : OwnIdRegisterEvent()

    override fun toString(): String = javaClass.simpleName
}