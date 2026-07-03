package com.ownid.sdk.namespace

import androidx.annotation.CheckResult
import com.ownid.sdk.OwnId
import com.ownid.sdk.di.DIContainer
import com.ownid.sdk.model.Context
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.clearContext
import com.ownid.sdk.model.setContext
import com.ownid.sdk.model.withContext
import com.ownid.sdk.provider.OwnIdProvidersRegistrar
import com.ownid.sdk.provider.setProviders
import com.ownid.sdk.provider.withProviders

/**
 * Base contract for OwnID namespace handles that can return scoped views.
 *
 * Namespace handles are bound to the SDK scope they were obtained from.
 * After [OwnId.destroy] or same-name reinitialization of the owning instance, reacquire the namespace from [OwnId] or
 * the current [OwnIdInstance].
 *
 * Use [withContext] to return the same namespace type bound to a child scope with a [Context] containing a login ID,
 * access token, or account display name. Use [withProviders] to return the same namespace type bound to a child scope
 * with provider overrides.
 *
 * Scoped calls leave the current namespace unchanged. [withProviders] returns the current namespace when
 * [ProvidersScope.withProviders] receives an empty registrar.
 */
public interface OwnIdNamespace<Self> : ContextScope<Self>, ProvidersScope<Self>

/**
 * Contract for namespaces that can return a context-scoped view.
 */
public interface ContextScope<Self> {
    /**
     * Returns this namespace scoped with a [Context] built from [block].
     *
     * Use this to create a child scope for a specific login ID, access token, or account display name without changing
     * the current namespace or owning instance.
     * The returned namespace stays bound to that child scope.
     *
     * OwnId does not handle failures thrown from [block].
     *
     * @param scopeName Name for the returned child scope; defaults to "Context".
     * @param block Builder that sets [Context] fields: `authz` via [Authz.start] or [Authz.fromToken], and/or `accountDisplayName`.
     *
     * @return The same namespace type scoped for the returned child scope.
     */
    @CheckResult
    public fun withContext(scopeName: String = "Context", block: Context.Builder.() -> Unit): Self
}

/**
 * Contract for namespaces that can update [Context] in place.
 *
 * Unlike [ContextScope.withContext], these methods do not create a child scope.
 */
public interface ContextOverride<Self> {
    /**
     * Updates the current scope's [Context].
     *
     * Merge semantics:
     * - Fields assigned in [block] replace existing values in the current scope.
     * - Fields not assigned in [block] keep their existing values.
     * - Assigning `null` clears the field in the resulting context.
     *
     * OwnId does not handle failures thrown from [block].
     *
     * @param block Builder that sets [Context] fields: `authz` via [Authz.start] or [Authz.fromToken], and/or `accountDisplayName`.
     *
     * @return The namespace for the updated current scope.
     */
    public fun setContext(block: Context.Builder.() -> Unit): Self

    /**
     * Clears the current scope's [Context].
     *
     * @return The namespace for the updated current scope.
     */
    public fun clearContext(): Self
}

/**
 * Contract for namespaces that can return a provider-scoped view.
 */
public interface ProvidersScope<Self> {
    /**
     * Returns this namespace scoped with providers from [block].
     *
     * Use this to create a child scope with custom provider behavior without changing the current namespace or owning
     * instance.
     * The returned namespace stays bound to that child scope.
     *
     * If [block] does not register providers, returns the current namespace unchanged.
     *
     * OwnId does not handle failures thrown from [block].
     *
     * @param scopeName Name for the returned child scope; defaults to "Providers".
     * @param block Registrar to supply providers for the child scope.
     *
     * @return The same namespace type scoped for the returned child scope, or the current namespace unchanged when
     * empty.
     */
    @CheckResult
    public fun withProviders(scopeName: String = "Providers", block: OwnIdProvidersRegistrar.() -> Unit): Self
}

/**
 * Contract for namespaces that can update providers in place.
 *
 * Unlike [ProvidersScope.withProviders], these methods do not create a child scope.
 */
public interface ProvidersOverride<Self> {
    /**
     * Updates providers in the current scope.
     *
     * Merge semantics:
     * - Providers declared in [block] replace providers of the same type in the current scope.
     * - Existing providers of supported types that are not declared in [block] remain unchanged.
     *
     * If [block] does not register providers, returns the current namespace unchanged.
     *
     * OwnId does not handle failures thrown from [block].
     *
     * @param block Registrar to supply providers for the current scope.
     *
     * @return The namespace for the updated current scope.
     */
    public fun setProviders(block: OwnIdProvidersRegistrar.() -> Unit): Self
}


@InternalOwnIdAPI
internal interface OwnIdNamespaceSupport<Self> : OwnIdNamespace<Self>, ContextScopeSupport<Self>, ProvidersScopeSupport<Self>

@InternalOwnIdAPI
internal interface ContextScopeSupport<Self> : ContextScope<Self> {
    val container: DIContainer

    fun rebind(container: DIContainer): Self

    override fun withContext(scopeName: String, block: Context.Builder.() -> Unit): Self =
        rebind(container.withContext(scopeName, block))
}

@InternalOwnIdAPI
internal interface ContextOverrideSupport<Self> : ContextOverride<Self> {
    val container: DIContainer

    val currentNamespace: Self

    override fun setContext(block: Context.Builder.() -> Unit): Self =
        currentNamespace.also { container.setContext(block) }

    override fun clearContext(): Self =
        currentNamespace.also { container.clearContext() }
}

@InternalOwnIdAPI
internal interface ProvidersScopeSupport<Self> : ProvidersScope<Self> {
    val container: DIContainer

    fun rebind(container: DIContainer): Self

    @Suppress("UNCHECKED_CAST")
    override fun withProviders(scopeName: String, block: OwnIdProvidersRegistrar.() -> Unit): Self {
        val scopedContainer = container.withProviders(scopeName, block)
        if (scopedContainer === container) return this as Self

        return rebind(scopedContainer)
    }
}

@InternalOwnIdAPI
internal interface ProvidersOverrideSupport<Self> : ProvidersOverride<Self> {
    val container: DIContainer

    val currentNamespace: Self

    override fun setProviders(block: OwnIdProvidersRegistrar.() -> Unit): Self =
        currentNamespace.also { container.setProviders(block) }
}
