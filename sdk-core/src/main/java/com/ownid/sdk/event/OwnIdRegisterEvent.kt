package com.ownid.sdk.event

import com.ownid.sdk.OwnIdInstance
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
     * Event triggered when user successfully completes OwnID registration process. The OwnID SDK is waiting for user to enter
     * additional required data to finish registration flow, see [OwnIdInstance.register].
     *
     * @property loginId    May contain user Login ID that was used in OwnID registration flow. Will be empty if no Login ID was set.
     * @property authType   A string describing the type of authentication that was used during OwnID flow
     */
    public class ReadyToRegister(public val loginId: String, public val authType: String) : OwnIdRegisterEvent()

    /**
     * Object representing undo event in OwnID registration flow.
     * Event triggered when user select "Undo" option in ready-to-register state.
     */
    public object Undo : OwnIdRegisterEvent()

    /**
     * Class representing login event in OwnID registration flow.
     * Event triggered when user successfully completes registration with OwnID and is logged in with OwnID.
     *
     * @property authType   A string describing the type of authentication that was used during OwnID flow.
     * @property loginData  Optional [LoginData] that returned by identity management system. The exact type is defined per integration.
     */
    public class LoggedIn(public val authType: String, public val loginData: LoginData?) : OwnIdRegisterEvent()

    /**
     * Class representing error events in OwnID registration flow.
     * Event triggered when an error occurs during OwnID registration flow.
     *
     * @property cause  Holds reason for this error. See [OwnIdException]
     */
    public class Error(public val cause: OwnIdException) : OwnIdRegisterEvent()

    override fun toString(): String = "OwnIdRegisterEvent.${javaClass.simpleName}"
}