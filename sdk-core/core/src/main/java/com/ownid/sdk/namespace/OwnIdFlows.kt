package com.ownid.sdk.namespace

import com.ownid.sdk.OwnId
import com.ownid.sdk.di.DIContainer
import com.ownid.sdk.flow.boost.BoostCreatePasskeyFlowFailure
import com.ownid.sdk.flow.boost.BoostFlow
import com.ownid.sdk.flow.boost.BoostFlowContext
import com.ownid.sdk.flow.boost.BoostLoginFlowFailure
import com.ownid.sdk.flow.elite.EliteFlow
import com.ownid.sdk.flow.elite.EliteFlowContext
import com.ownid.sdk.model.Context
import com.ownid.sdk.model.InternalOwnIdAPI

/**
 * OwnID authentication flow namespace.
 *
 * Use this namespace for the highest-level OwnID integrations.
 *
 * Namespace handles are bound to the SDK scope they were obtained from.
 * After [OwnId.destroy] or same-name reinitialization, previously returned namespace handles are invalid and should be
 * reacquired from [OwnId.flows] or the current [OwnIdInstance].
 *
 * These flows orchestrate multiple operations for you based on the current [Context]
 * and server requirements. Passing `null`, or using the zero-argument [FlowEntry.start] overload, starts Boost and
 * Elite with their empty flow contexts.
 *
 * Use [withContext] to scope flows with a [Context] containing a login ID or
 * access token and [withProviders] to register providers. Use [FlowEntry.start] to launch the selected flow.
 * Namespace entry properties do not resolve their underlying flow runtime until [FlowEntry.start] is called. Each
 * start call returns a caller-owned flow controller; keep it while the flow is active and use it to await the terminal
 * flow result.
 *
 * Accessed via [OwnId.flows] (default instance) or [OwnIdInstance.flows].
 */
public interface OwnIdFlows : OwnIdNamespace<OwnIdFlows> {

    public val boost: OwnIdBoostFlow

    /**
     * Boost authentication flow group.
     *
     * Use [login] for sign-in screens and [createPasskey] when account creation should include the OwnID
     * create-passkey path. The create-passkey flow can end with either an existing-account login or a create-passkey
     * result.
     */
    public interface OwnIdBoostFlow {
        /** Composite login flow that orchestrates operations based on server authentication requirements. */
        public val login: FlowEntry<BoostFlowContext?, BoostFlow.Controller<BoostFlow.Response.Login, BoostLoginFlowFailure>>

        /** Composite account-creation create-passkey flow that can also return an existing-account login result. */
        public val createPasskey: FlowEntry<BoostFlowContext?, BoostFlow.Controller<BoostFlow.Response, BoostCreatePasskeyFlowFailure>>
    }

    /** Web-based authentication flow using a WebView/WebBridge. */
    public val elite: FlowEntry<EliteFlowContext?, EliteFlow.Controller>

}

@InternalOwnIdAPI
internal val DIContainer.flowsNamespace: OwnIdFlows
    get() = OwnIdFlowsImpl(this)

@InternalOwnIdAPI
private class OwnIdFlowsImpl(
    override val container: DIContainer
) : OwnIdFlows, OwnIdNamespaceSupport<OwnIdFlows> {

    override fun rebind(container: DIContainer): OwnIdFlows = container.flowsNamespace

    override val boost: OwnIdFlows.OwnIdBoostFlow = object : OwnIdFlows.OwnIdBoostFlow {
        override val login: FlowEntry<BoostFlowContext?, BoostFlow.Controller<BoostFlow.Response.Login, BoostLoginFlowFailure>> =
            flowEntry(
                container = container,
                runtimeClass = BoostFlow.Login::class.java,
                start = { context -> start(context ?: BoostFlowContext.Empty) }
            )

        override val createPasskey: FlowEntry<BoostFlowContext?, BoostFlow.Controller<BoostFlow.Response, BoostCreatePasskeyFlowFailure>> =
            flowEntry(
                container = container,
                runtimeClass = BoostFlow.CreatePasskey::class.java,
                start = { context -> start(context ?: BoostFlowContext.Empty) }
            )
    }

    override val elite: FlowEntry<EliteFlowContext?, EliteFlow.Controller> =
        flowEntry(
            container = container,
            runtimeClass = EliteFlow::class.java,
            start = { context -> start(context ?: EliteFlowContext.Empty) }
        )
}
