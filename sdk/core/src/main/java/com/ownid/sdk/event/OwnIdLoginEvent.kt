package com.ownid.sdk.event

import com.ownid.sdk.OwnIdInstance
import com.ownid.sdk.OwnIdIntegration
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.viewmodel.OwnIdLoginViewModel

/**
 * Sealed class with events for OwnID login process.
 *
 * This type of events emitted by [OwnIdLoginViewModel] when [OwnIdInstance] has [OwnIdIntegration] component set.
 */
public sealed class OwnIdLoginEvent : OwnIdEvent {

    /**
     * Class representing busy status during OwnID login process.
     *
     * @property isBusy  Set to true if OwnID is busy with waiting or processing data.
     */
    public class Busy(public val isBusy: Boolean) : OwnIdLoginEvent()

    /**
     * Class representing login event in OwnID login process.
     * Event emitted when user successfully completes login with OwnID.
     *
     * @property authType   A string describing the type of authentication that was used during OwnID login process.
     * @property loginData  Optional [LoginData] that returned by identity platform. The exact type is defined per integration.
     * @property authToken  A token that can be used to refresh a session
     */
    public class LoggedIn(public val authType: String, public val loginData: LoginData?, public val authToken: String?) : OwnIdLoginEvent()

    /**
     * Class representing error events in OwnID login process.
     * Event emitted when an error occurs during OwnID login process.
     *
     * @property cause  Holds reason for this error. See [OwnIdException]
     */
    public class Error(public val cause: OwnIdException) : OwnIdLoginEvent()

    override fun toString(): String = "OwnIdLoginEvent.${javaClass.simpleName}"
}