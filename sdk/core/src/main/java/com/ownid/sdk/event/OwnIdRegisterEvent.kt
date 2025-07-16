package com.ownid.sdk.event

import com.ownid.sdk.OwnIdInstance
import com.ownid.sdk.OwnIdIntegration
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.viewmodel.OwnIdRegisterViewModel

/**
 * Sealed class with events for OwnID registration process.
 *
 * This type of events emitted by [OwnIdRegisterViewModel] when [OwnIdInstance] has [OwnIdIntegration] component set.
 */
public sealed class OwnIdRegisterEvent : OwnIdEvent {

    /**
     * Class representing busy status during OwnID registration process.
     *
     * @property isBusy  Set to true if OwnID is busy with waiting or processing data.
     */
    public class Busy(public val isBusy: Boolean) : OwnIdRegisterEvent()

    /**
     * Class representing ready-to-register state during OwnID registration process.
     * Event emitted when user successfully completes OwnID registration flow. The OwnID SDK is waiting for user to enter
     * any additional required data to finish registration process, see [OwnIdRegisterViewModel.register].
     *
     * @property loginId    User Login ID that was used in OwnID registration flow.
     * @property authType   A string describing the type of authentication that was used during OwnID flow.
     */
    public class ReadyToRegister(public val loginId: String, public val authType: String) : OwnIdRegisterEvent()

    /**
     * Object representing undo event in OwnID registration process.
     * Event triggered when user select "Undo" option in ready-to-register state.
     */
    public object Undo : OwnIdRegisterEvent()

    /**
     * Class representing login event in OwnID registration process.
     * Event emitted when user successfully completes registration with OwnID and is logged in with OwnID.
     *
     * @property authType   A string describing the type of authentication that was used during OwnID process.
     * @property loginData  Optional [LoginData] that returned by identity platform. The exact type is defined per integration.
     * @property authToken  A token that can be used to refresh a session
     */
    public class LoggedIn(public val authType: String, public val loginData: LoginData?, public val authToken: String?) : OwnIdRegisterEvent()

    /**
     * Class representing error events in OwnID registration process.
     * Event emitted when an error occurs during OwnID registration process.
     *
     * @property cause  Holds reason for this error. See [OwnIdException]
     */
    public class Error(public val cause: OwnIdException) : OwnIdRegisterEvent()

    override fun toString(): String = "OwnIdRegisterEvent.${javaClass.simpleName}"
}