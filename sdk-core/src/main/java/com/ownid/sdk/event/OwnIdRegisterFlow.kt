package com.ownid.sdk.event

import com.ownid.sdk.OwnIdInstance
import com.ownid.sdk.OwnIdIntegration
import com.ownid.sdk.OwnIdPayload
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.viewmodel.OwnIdRegisterViewModel

/**
 * Sealed class with events for OwnID registration flow.
 *
 * This type of events emitted by [OwnIdRegisterViewModel] when no [OwnIdIntegration] component set in [OwnIdInstance].
 */
public sealed class OwnIdRegisterFlow : OwnIdEvent {

    /**
     * Class representing busy status during OwnID registration flow.
     *
     * @property isBusy  Set to true if OwnID is busy with waiting or processing data.
     */
    public class Busy(public val isBusy: Boolean) : OwnIdRegisterFlow()

    /**
     * Class representing OwnID response event for OwnID registration flow.
     *
     * This event emitted at the end of the successful OwnID registration flow.
     *
     * Use event data to do registration or login within the identity platform.
     *
     * @property loginId    User Login ID that was used in OwnID flow.
     * @property payload    [OwnIdPayload] with the result of OwnID flow. Use it to do registration or login within the identity platform.
     * @property authType   A string describing the type of authentication that was used during OwnID flow.
     */
    public class Response(public val loginId: String, public val payload: OwnIdPayload, public val authType: String) : OwnIdRegisterFlow()

    /**
     * Object representing undo event in OwnID registration flow.
     * Event emitted when user select "Undo" option.
     */
    public object Undo : OwnIdRegisterFlow()

    /**
     * Class representing error events in OwnID registration flow.
     * Event emitted when an error occurs during OwnID registration flow.
     *
     * @property cause  Holds reason for this error. See [OwnIdException]
     */
    public class Error(public val cause: OwnIdException) : OwnIdRegisterFlow()

    override fun toString(): String = "OwnIdRegisterFlow.${javaClass.simpleName}"
}