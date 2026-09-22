@file:JvmName("OwnIdProviderScopes")

package com.ownid.sdk.provider

import android.app.Activity
import android.os.Looper
import androidx.annotation.MainThread
import com.ownid.sdk.OwnId
import com.ownid.sdk.capability.Capability
import com.ownid.sdk.capability.SignInWithGoogle
import com.ownid.sdk.capability.SignInWithSocial
import com.ownid.sdk.capability.UiContextProvider
import com.ownid.sdk.capability.impl.ActivityUiContextProviderImpl
import com.ownid.sdk.capability.impl.UiContextProviderImpl
import com.ownid.sdk.di.DIContainer
import com.ownid.sdk.di.DIContainerRegistrar
import com.ownid.sdk.di.DIContainerResolver
import com.ownid.sdk.di.DependencyResolutionException
import com.ownid.sdk.di.MissingDependencyException
import com.ownid.sdk.di.OwnIdRootDIContainer
import com.ownid.sdk.di.impl.DIContainerImpl
import com.ownid.sdk.model.InternalOwnIdAPI

@DslMarker
public annotation class OwnIdProvidersDsl

/**
 * Registrar for OwnID provider implementations.
 *
 * Use this registrar only inside [OwnId.setProviders] or [OwnId.withProviders] blocks. [OwnId.withProviders] creates a child scope whose providers
 * override inherited providers of the same type; [OwnId.setProviders] updates the current scope in place.
 *
 * Supported provider types are [SessionCreate], [PasswordAuthenticate], [SignInWithGoogle], and [UiContextProvider]. Registering the same provider
 * type more than once in one block uses the last provider. A block that registers no providers leaves the current scope unchanged.
 *
 * Do not retain this registrar or provider builders after the enclosing provider block returns. OwnID retains the registered provider instances;
 * the app owns custom-provider state, external SDK/session state, and failure mapping.
 */
@OwnIdProvidersDsl
@OptIn(InternalOwnIdAPI::class)
public class OwnIdProvidersRegistrar internal constructor(
    private val resolver: DIContainerResolver
) {
    private var sessionCreate: SessionCreate? = null
    private var passwordAuthenticate: PasswordAuthenticate? = null
    private var signInWithGoogle: SignInWithGoogle? = null
    private var uiContextProvider: UiContextProvider? = null

    /**
     * Returns the dependency of type [clazz] from the current provider scope, or `null` when it is missing.
     *
     * Use this typed lookup to read capabilities already available to the registrar while configuring providers. During
     * [OwnId.withProviders], lookup reads from the parent scope being extended; providers registered later in the same block are not visible during
     * that block.
     */
    public fun <T : Any> getOrNull(clazz: Class<T>): T? = resolver.getOrNull(clazz)

    /**
     * Returns the dependency of type [clazz] from the current provider scope, or throws when it is missing.
     *
     * Use this typed lookup when provider configuration cannot proceed without that dependency. During [OwnId.withProviders], lookup reads from the
     * parent scope being extended; providers registered later in the same block are not visible during that block.
     *
     * @throws MissingDependencyException
     *     When the requested dependency or one of its factory dependencies is not registered in the current provider
     *     scope. For a requested `A` whose factory reaches a missing `B`, the error identifies `B` as the missing
     *     dependency and retains `A` as its entry point.
     * @throws DependencyResolutionException
     *     When dependency resolution fails for another reason, such as a cycle or factory error.
     */
    @Throws(MissingDependencyException::class, DependencyResolutionException::class)
    public fun <T : Any> getOrThrow(clazz: Class<T>): T = resolver.getOrThrow(clazz)

    /**
     * Registers the [SessionCreate] provider for this registrar.
     *
     * If called multiple times, the last provider wins. The [block] must set [SessionCreateBuilder.create]; omitting it throws
     * [IllegalArgumentException] before registration completes.
     *
     * @throws IllegalArgumentException When [block] does not set a session creation handler.
     */
    public fun sessionCreate(block: SessionCreateBuilder.() -> Unit) {
        sessionCreate = SessionCreateBuilder().apply(block).build()
    }

    /**
     * Registers the [PasswordAuthenticate] provider for this registrar.
     *
     * If called multiple times, the last provider wins. The [block] must set [PasswordAuthenticateBuilder.authenticate];
     * omitting it throws [IllegalArgumentException] before registration completes.
     *
     * @throws IllegalArgumentException When [block] does not set a password authentication handler.
     */
    public fun passwordAuthenticate(block: PasswordAuthenticateBuilder.() -> Unit) {
        passwordAuthenticate = PasswordAuthenticateBuilder().apply(block).build()
    }

    /**
     * Registers the [SignInWithGoogle] provider for this registrar.
     *
     * If called multiple times, the last provider wins. The [block] must set [SignInWithGoogleBuilder.signIn]; omitting it throws
     * [IllegalArgumentException] before registration completes.
     *
     * @throws IllegalArgumentException When [block] does not set a Google sign-in handler.
     */
    public fun signInWithGoogle(block: SignInWithGoogleBuilder.() -> Unit) {
        signInWithGoogle = SignInWithGoogleBuilder().apply(block).build()
    }

    /**
     * Registers an SDK-provided [UiContextProvider] bound to [activity].
     *
     * The provider follows this logical Activity through supported configuration replacement. It does not migrate to another Activity during
     * navigation. Resolution during a replacement gap fails immediately. For an Activity that is not an
     * [androidx.activity.ComponentActivity], recovery is best effort: if registration happens after its last state save,
     * the next replacement cannot be followed.
     *
     * If called multiple times, the last UI-context provider wins.
     *
     * @throws IllegalStateException When called off the main thread or when [activity] is not ready to be bound.
     */
    @MainThread
    public fun uiContext(activity: Activity) {
        check(Looper.myLooper() == Looper.getMainLooper()) { "uiContext(activity) must run on the main thread" }
        uiContextProvider = ActivityUiContextProviderImpl(
            activity,
            OwnIdRootDIContainer.getOrThrow(UiContextProvider::class.java) as UiContextProviderImpl
        )
    }

    /**
     * Registers the app-provided [provider] as the [UiContextProvider].
     *
     * The exact instance is stored without invoking it. OwnID calls [UiContextProvider.current] synchronously on the
     * main thread when UI is needed. Its result is authoritative: a failure does not fall back to an inherited
     * provider. If called multiple times, the last UI-context provider wins.
     */
    public fun uiContext(provider: UiContextProvider) {
        uiContextProvider = provider
    }

    internal fun register(registrar: DIContainerRegistrar) {
        sessionCreate?.let { registrar.register(SessionCreate::class.java, it) }
        passwordAuthenticate?.let { registrar.register(PasswordAuthenticate::class.java, it) }
        signInWithGoogle?.let { registrar.register(SignInWithGoogle::class.java, it) }
        uiContextProvider?.let { registrar.register(UiContextProvider::class.java, it) }
    }

    internal fun isEmpty(): Boolean =
        sessionCreate == null && passwordAuthenticate == null && signInWithGoogle == null && uiContextProvider == null
}

/**
 * Builds a [SessionCreate] provider for the current registration block.
 *
 * [create] is required. [isAvailable] is optional and defaults to available. The resulting provider is available for `null` params, delegates typed
 * [SessionCreate.Params] to [isAvailable], and reports unavailable for other parameter types. OwnID invokes these callbacks on the main thread. If a
 * callback uses blocking app-owned work, dispatch that work away from the main thread and return only after it completes.
 *
 * When [isAvailable] returns `false`, OwnID treats this provider as unavailable for the requested parameters. OwnID
 * does not retry session creation or persist returned session values.
 */
@OwnIdProvidersDsl
public class SessionCreateBuilder internal constructor() {
    private var availability: suspend (SessionCreate.Params) -> Boolean = { true }
    private var create: (suspend (SessionCreate.Params) -> Result<SessionOutput>)? = null

    /**
     * Sets the availability check for this provider.
     *
     * If omitted, the provider is available. OwnID invokes this handler on the main thread.
     */
    public fun isAvailable(block: suspend (SessionCreate.Params) -> Boolean) {
        availability = block
    }

    /**
     * Sets the session creation handler.
     *
     * Return [Result.success] with [SessionOutput] when session creation succeeds, or [Result.failure] when it fails.
     * The app owns the failure value. OwnID does not move app-owned work to a background dispatcher for you.
     */
    public fun create(block: suspend (SessionCreate.Params) -> Result<SessionOutput>) {
        create = block
    }

    internal fun build(): SessionCreate = object : SessionCreate {
        private val createHandler = requireNotNull(this@SessionCreateBuilder.create) { "sessionCreate requires a create handler" }

        override suspend fun isAvailable(params: Capability.Params?): Boolean = when (params) {
            null -> true
            is SessionCreate.Params -> availability(params)
            else -> false
        }

        override suspend fun create(params: SessionCreate.Params): Result<SessionOutput> = createHandler(params)
    }
}

/**
 * Builds a [PasswordAuthenticate] provider for the current registration block.
 *
 * [authenticate] is required. [isAvailable] is optional and defaults to available. The resulting provider is available for `null` params, delegates
 * typed [PasswordAuthenticate.Params] to [isAvailable], and reports unavailable for other parameter types. OwnID invokes these callbacks on the main
 * thread. If a callback uses blocking app-owned work, dispatch that work away from the main thread and return only after it completes.
 *
 * When [isAvailable] returns `false`, OwnID treats this provider as unavailable for the requested parameters. OwnID
 * does not retry password authentication or persist returned session values.
 */
@OwnIdProvidersDsl
public class PasswordAuthenticateBuilder internal constructor() {
    private var availability: suspend (PasswordAuthenticate.Params) -> Boolean = { true }
    private var authenticate: (suspend (PasswordAuthenticate.Params) -> Result<SessionOutput>)? = null

    /**
     * Sets the availability check for this provider.
     *
     * If omitted, the provider is available. OwnID invokes this handler on the main thread.
     */
    public fun isAvailable(block: suspend (PasswordAuthenticate.Params) -> Boolean) {
        availability = block
    }

    /**
     * Sets the password authentication handler.
     *
     * Return [Result.success] with [SessionOutput] when authentication succeeds, or [Result.failure] when it fails.
     * The app owns the failure value. OwnID does not move app-owned work to a background dispatcher for you.
     */
    public fun authenticate(block: suspend (PasswordAuthenticate.Params) -> Result<SessionOutput>) {
        authenticate = block
    }

    internal fun build(): PasswordAuthenticate = object : PasswordAuthenticate {
        private val authenticateHandler =
            requireNotNull(this@PasswordAuthenticateBuilder.authenticate) { "passwordAuthenticate requires an authenticate handler" }

        override suspend fun isAvailable(params: Capability.Params?): Boolean = when (params) {
            null -> true
            is PasswordAuthenticate.Params -> availability(params)
            else -> false
        }

        override suspend fun authenticate(params: PasswordAuthenticate.Params): Result<SessionOutput> = authenticateHandler(params)
    }
}

/**
 * Builds a [SignInWithGoogle] provider for the current registration block.
 *
 * [signIn] is required. [isAvailable] defaults to available, [cancel] defaults to a no-op, and [signOut] defaults to success. The resulting provider is
 * available for `null` params, delegates typed [SignInWithSocial.Params] to [isAvailable], and reports unavailable for other parameter types.
 *
 * OwnID invokes [signIn] on the main thread. Other handlers have no main-thread guarantee.
 * The provider owns cancellation support, local provider state, ID-token handling, and failure mapping.
 */
@OwnIdProvidersDsl
public class SignInWithGoogleBuilder internal constructor() {
    private var availability: suspend (SignInWithSocial.Params) -> Boolean = { true }
    private var signIn: (suspend (Activity, SignInWithSocial.Params) -> SignInWithSocial.Result)? = null
    private var cancel: suspend () -> Unit = {}
    private var signOut: suspend (SignInWithGoogle.SignOutParams) -> Throwable? = { null }

    /**
     * Sets the availability check for this provider.
     *
     * If omitted, the provider is available.
     */
    public fun isAvailable(block: suspend (SignInWithSocial.Params) -> Boolean) {
        availability = block
    }

    /**
     * Sets the required Google sign-in handler.
     *
     * OwnID invokes it on the Android main thread with the Activity selected for the request. Do not retain that
     * Activity after the request completes.
     */
    public fun signIn(block: suspend (Activity, SignInWithSocial.Params) -> SignInWithSocial.Result) {
        signIn = block
    }

    /**
     * Sets the cancellation handler.
     *
     * If omitted, cancellation is a no-op.
     */
    public fun cancel(block: suspend () -> Unit) {
        cancel = block
    }

    /**
     * Sets the Google sign-out handler.
     *
     * If omitted, sign-out returns `null`.
     */
    public fun signOut(block: suspend (SignInWithGoogle.SignOutParams) -> Throwable?) {
        signOut = block
    }

    internal fun build(): SignInWithGoogle = object : SignInWithGoogle {
        private val signInHandler = requireNotNull(this@SignInWithGoogleBuilder.signIn) { "signInWithGoogle requires a signIn handler" }
        private val cancelHandler = this@SignInWithGoogleBuilder.cancel
        private val signOutHandler = this@SignInWithGoogleBuilder.signOut

        override suspend fun isAvailable(params: Capability.Params?): Boolean = when (params) {
            null -> true
            is SignInWithSocial.Params -> availability(params)
            else -> false
        }

        override suspend fun signIn(activity: Activity, params: SignInWithSocial.Params): SignInWithSocial.Result = signInHandler(activity, params)

        override suspend fun cancel() {
            cancelHandler()
        }

        override suspend fun signOut(params: SignInWithGoogle.SignOutParams): Throwable? = signOutHandler(params)
    }
}

/**
 * Creates a child scope and registers providers from [block] in that scope.
 *
 * The child scope keeps the current scope's providers and configuration available. Providers declared in [block] replace providers of the same type
 * only in that child scope. If the same provider type is registered more than once in [block], the last provider wins.
 *
 * If [block] does not register providers, returns this container unchanged.
 *
 * Exceptions thrown from [block] propagate to the caller.
 *
 * @param scopeName Name for the child scope.
 * @param block Registration block using [OwnIdProvidersRegistrar].
 *
 * @return A [DIContainer] with the registered providers, or this container if none were defined.
 */
@InternalOwnIdAPI
internal fun DIContainer.withProviders(scopeName: String, block: OwnIdProvidersRegistrar.() -> Unit): DIContainer {
    val providers = OwnIdProvidersRegistrar(this).apply(block)

    if (providers.isEmpty()) return this

    return createScope(scopeName).apply {
        providers.register(this)
    }
}

/**
 * Updates providers in the current scope in place.
 *
 * Unlike [withProviders], this does not create a child scope.
 *
 * Merge semantics:
 * - Providers declared in [block] replace providers of the same type in the current scope.
 * - If the same provider type is registered more than once in [block], the last provider wins.
 * - Existing [SessionCreate], [PasswordAuthenticate], [SignInWithGoogle], and [UiContextProvider] providers not declared in [block] remain unchanged.
 *
 * If [block] does not register providers, this call is a no-op.
 *
 * Exceptions thrown from [block] propagate to the caller.
 *
 * @param block Registration block using [OwnIdProvidersRegistrar].
 *
 * @return This container.
 */
@InternalOwnIdAPI
internal fun DIContainer.setProviders(block: OwnIdProvidersRegistrar.() -> Unit): DIContainer {
    val providers = OwnIdProvidersRegistrar(this).apply(block)

    if (providers.isEmpty()) return this

    val lockObject = if (this is DIContainerImpl) this.lock else this
    return synchronized(lockObject) {
        providers.register(this)
        this
    }
}
