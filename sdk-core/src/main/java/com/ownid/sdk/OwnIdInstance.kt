package com.ownid.sdk

import com.ownid.sdk.event.OwnIdLoginEvent
import com.ownid.sdk.event.OwnIdLoginFlow
import com.ownid.sdk.event.OwnIdRegisterEvent
import com.ownid.sdk.event.OwnIdRegisterFlow
import com.ownid.sdk.viewmodel.OwnIdLoginViewModel
import com.ownid.sdk.viewmodel.OwnIdRegisterViewModel

/**
 * Top level interface for OwnID SDK instance.
 */
public interface OwnIdInstance {

    /**
     * Instance of [OwnIdCore].
     */
    public val ownIdCore: OwnIdCore

    /**
     * Instance of [OwnIdIntegration] - an optional component that contains integration functionality for this [OwnIdInstance].
     *
     * If a component is available, it will be used to do the actual login and/or registration within the identity platform.
     * Also, the [OwnIdLoginViewModel] will emit [OwnIdLoginEvent] events and [OwnIdRegisterViewModel] will emit [OwnIdRegisterEvent] events.
     *
     * If the component is not available, the [OwnIdLoginViewModel] will emit [OwnIdLoginFlow] events and [OwnIdRegisterViewModel] will emit [OwnIdRegisterFlow] events.
     * In that case, it's a developer responsibility to do login and/or registration within the identity platform.
     */
    public val ownIdIntegration: OwnIdIntegration?
}

/**
 * Creates new instance of [OwnIdWebViewBridge] that uses this instance of OwnID.
 */
public fun OwnIdInstance.createWebViewBridge(): OwnIdWebViewBridge = ownIdCore.createWebViewBridge()