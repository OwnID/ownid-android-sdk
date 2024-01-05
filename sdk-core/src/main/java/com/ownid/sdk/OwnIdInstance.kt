package com.ownid.sdk

import com.ownid.sdk.event.LoginData
import com.ownid.sdk.exception.OwnIdException

/**
 * Top level integration independent interface for OwnID SDK instance.
 */
public interface OwnIdInstance {

    /**
     * Instance of [OwnIdCore].
     */
    public val ownIdCore: OwnIdCore

    /**
     * Complete OwnID Registration flow and register new user. User password will be generated automatically.
     *
     * @param loginId        User Login ID.
     * @param params         [RegistrationParameters] Additional parameters for registration. Depend on integration.
     * @param ownIdResponse  [OwnIdResponse] from OwnID Register flow.
     * @param callback       [OwnIdCallback] with optional [LoginData] value of Registration flow result or with [OwnIdException] cause value if Registration flow failed.
     */
    public fun register(loginId: String, params: RegistrationParameters?, ownIdResponse: OwnIdResponse, callback: OwnIdCallback<LoginData?>)

    /**
     * Complete OwnID Login flow.
     *
     * @param ownIdResponse  [OwnIdResponse] from OwnID Login flow.
     * @param callback       [OwnIdCallback] with optional [LoginData] value of Login flow result or with [OwnIdException] cause if Login failed.
     */
    public fun login(ownIdResponse: OwnIdResponse, callback: OwnIdCallback<LoginData?>)
}

/**
 * Creates new instance of [OwnIdWebViewBridge] that uses this instance of OwnID.
 */
public fun OwnIdInstance.createWebViewBridge(): OwnIdWebViewBridge = ownIdCore.createWebViewBridge()