package com.ownid.sdk.event

import com.ownid.sdk.OwnIdInstance
import com.ownid.sdk.OwnIdIntegration
import com.ownid.sdk.OwnIdPayload
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.viewmodel.OwnIdLoginViewModel

/**
 * Sealed class with events for OwnID login flow.
 *
 * This type of events emitted by [OwnIdLoginViewModel] when no [OwnIdIntegration] component set in [OwnIdInstance].
 */
public sealed class OwnIdLoginFlow : OwnIdEvent {

    /**
     * Class representing busy status during OwnID login flow.
     *
     * @property isBusy  Set to true if OwnID is busy with waiting or processing data.
     */
    public class Busy(public val isBusy: Boolean) : OwnIdLoginFlow()

    /**
     * Class representing OwnID response event for OwnID login flow.
     *
     * This event emitted at the end of the successful OwnID login flow.
     *
     * Use event data to do login within the identity platform.
     *
     * @property loginId    User Login ID that was used in OwnID flow.
     * @property payload    [OwnIdPayload] with the result of OwnID flow. Use it to do login within the identity platform.
     * @property authType   A string describing the type of authentication that was used during OwnID flow.
     * @property authToken  A token that can be used to refresh a session
     */
    public class Response(
        public val loginId: String,
        public val payload: OwnIdPayload,
        public val authType: String,
        public val authToken: String?
    ) : OwnIdLoginFlow()

    /**
     * Class representing error events for OwnID login flow.
     *
     * Event triggered when an error occurs during OwnID login flow.
     *
     * @property cause  Holds reason for this error. See [OwnIdException]
     */
    public class Error(public val cause: OwnIdException) : OwnIdLoginFlow()

    override fun toString(): String = "OwnIdLoginFlow.${javaClass.simpleName}"
}