package com.ownid.sdk

import com.ownid.sdk.event.LoginData
import com.ownid.sdk.event.OwnIdLoginEvent
import com.ownid.sdk.event.OwnIdRegisterEvent
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.viewmodel.OwnIdLoginViewModel
import com.ownid.sdk.viewmodel.OwnIdRegisterViewModel

/**
 * An OwnID SDK component that contains integration functionality.
 *
 * If a component is available to [OwnIdInstance] it will be used to do the actual login and/or registration within the identity platform.
 *
 * Also, the [OwnIdLoginViewModel] will emit [OwnIdLoginEvent] events and [OwnIdRegisterViewModel] will emit [OwnIdRegisterEvent] events.
 */
public interface OwnIdIntegration {

    /**
     * Complete OwnID Registration process and register new user.
     *
     * @param loginId        User Login ID.
     * @param params         [RegistrationParameters] Additional parameters for registration. Depend on integration.
     * @param ownIdResponse  [OwnIdResponse] from OwnID Register flow.
     * @param callback       [OwnIdCallback] with optional [LoginData] value of Registration process result or with [OwnIdException] cause if Registration failed.
     */
    public fun register(loginId: String, params: RegistrationParameters?, ownIdResponse: OwnIdResponse, callback: OwnIdCallback<LoginData?>)

    /**
     * Complete OwnID Login process.
     *
     * @param ownIdResponse  [OwnIdResponse] from OwnID Login flow.
     * @param callback       [OwnIdCallback] with optional [LoginData] value of Login process result or with [OwnIdException] cause if Login failed.
     */
    public fun login(ownIdResponse: OwnIdResponse, callback: OwnIdCallback<LoginData?>)
}

/**
 * Top level interface for additional registration parameters.
 *
 * The exact type is defined per integration.
 * Usually it contains parameters that are mandatory for registration.
 */
public interface RegistrationParameters