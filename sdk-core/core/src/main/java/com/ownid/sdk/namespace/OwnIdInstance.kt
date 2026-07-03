package com.ownid.sdk.namespace

import com.ownid.sdk.OwnId
import com.ownid.sdk.OwnIdConfiguration
import com.ownid.sdk.capability.LocalInfo
import com.ownid.sdk.di.DIContainer
import com.ownid.sdk.di.getOrThrow
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.ui.webbridge.WebBridge

/**
 * Configured OwnID SDK instance.
 *
 * Obtain an instance with [OwnId.instance] after initialization and use it as the entry point to scoped OwnID
 * namespaces such as flows, headless integrations, and WebBridge.
 *
 * Instance handles are bound to the SDK scope they were obtained from.
 * After [OwnId.destroy] or same-name reinitialization, previously returned handles and namespaces derived
 * from them are invalid and should be reacquired from [OwnId.instance].
 *
 * [withContext] and [withProviders] return [OwnIdInstance] views bound to child scopes and leave this instance's
 * current scope unchanged. [setContext], [clearContext], and [setProviders] update this instance's current scope in
 * place and return this instance view. [withProviders] and [setProviders] return the current instance unchanged when
 * the registrar does not register providers.
 */
public interface OwnIdInstance : OwnIdNamespace<OwnIdInstance>, ContextOverride<OwnIdInstance>, ProvidersOverride<OwnIdInstance> {

    public val configuration: OwnIdConfiguration

    public val localInfo: LocalInfo

    /** Flow namespace for running composite auth flows. */
    public val flows: OwnIdFlows

    /** Headless namespace for UI-less integrations. */
    public val headless: OwnIdHeadless

    /** WebBridge namespace for creating scoped [WebBridge] instances and configuring their default plugins. */
    public val webBridge: OwnIdWebBridge
}

/**
 * Full instance namespace visible only to SDK modules.
 *
 * This is an internal SDK module contract, not a public app integration contract. It exposes Core-owned direct API and
 * operation namespaces to modules that intentionally opt in to OwnID internals.
 */
@InternalOwnIdAPI
public interface OwnIdFullInstance : OwnIdInstance {
    public val api: OwnIdApi

    public val ops: OwnIdOperation
}

@InternalOwnIdAPI
internal val DIContainer.instanceNamespace: OwnIdFullInstance
    get() = object : OwnIdFullInstance,
        OwnIdNamespaceSupport<OwnIdInstance>,
        ContextOverrideSupport<OwnIdInstance>,
        ProvidersOverrideSupport<OwnIdInstance> {

        override val currentNamespace: OwnIdInstance
            get() = this

        override val container: DIContainer
            get() = this@instanceNamespace

        override fun rebind(container: DIContainer): OwnIdInstance = container.instanceNamespace

        public override val configuration: OwnIdConfiguration
            get() = getOrThrow<OwnIdConfiguration>()

        public override val localInfo: LocalInfo
            get() = getOrThrow<LocalInfo>()

        public override val api: OwnIdApi = apiNamespace

        public override val ops: OwnIdOperation = opsNamespace

        override val flows: OwnIdFlows = flowsNamespace

        override val headless: OwnIdHeadless = headlessNamespace

        override val webBridge: OwnIdWebBridge = webBridgeNamespace
    }
