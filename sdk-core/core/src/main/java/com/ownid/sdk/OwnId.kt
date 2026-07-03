package com.ownid.sdk

import androidx.annotation.CheckResult
import androidx.annotation.RestrictTo
import com.ownid.sdk.OwnId.destroy
import com.ownid.sdk.OwnId.flows
import com.ownid.sdk.OwnId.getInstanceContainer
import com.ownid.sdk.OwnId.headless
import com.ownid.sdk.OwnId.initialize
import com.ownid.sdk.OwnId.initializeFromFile
import com.ownid.sdk.OwnId.initializeFromJson
import com.ownid.sdk.OwnId.setLanguage
import com.ownid.sdk.OwnId.webBridge
import com.ownid.sdk.OwnId.withContext
import com.ownid.sdk.OwnId.withProviders
import com.ownid.sdk.capability.LanguageTagsProvider
import com.ownid.sdk.capability.Serialization
import com.ownid.sdk.di.DIContainer
import com.ownid.sdk.di.OwnIdRootDIContainer
import com.ownid.sdk.di.getOrNull
import com.ownid.sdk.di.getOrThrow
import com.ownid.sdk.di.register
import com.ownid.sdk.logger.OwnIdLogger
import com.ownid.sdk.logger.OwnIdLoggerBuilder
import com.ownid.sdk.model.Context
import com.ownid.sdk.model.InstanceName
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.namespace.OwnIdFlows
import com.ownid.sdk.namespace.OwnIdHeadless
import com.ownid.sdk.namespace.OwnIdInstance
import com.ownid.sdk.namespace.OwnIdWebBridge
import com.ownid.sdk.provider.OwnIdProvidersRegistrar
import com.ownid.sdk.ui.webbridge.WebBridge
import kotlinx.coroutines.flow.StateFlow

/**
 * Main entry point for OwnID Core SDK instance lifecycle and namespace access.
 *
 * Manages named SDK instances, exposes default-instance namespaces via [flows], [headless], and [webBridge], and provides configuration
 * helpers for logging, language tags, context, and providers. This entry point is shipped by the `com.ownid.android-sdk:core` artifact.
 *
 * Initialize an instance from programmatic values, a JSON string, or an app asset JSON file before using instance-bound namespaces.
 * Initialization builders require an Android [android.content.Context]; the SDK stores its application context. Successful
 * initialization creates or replaces the named instance, stops work owned by the previous same-name instance, and requires callers to
 * reacquire instance and namespace handles. These entry-point calls are synchronous and do not expose a cancellation callback.
 * Configuration build failures are logged and leave the current named instance unchanged.
 *
 * Logging and explicit language tags are process-wide SDK settings. Configure logging before initialization when you need startup or
 * initial HTTP setup logs.
 */
@OptIn(InternalOwnIdAPI::class)
public object OwnId {

    /**
     * Configures SDK-wide logging behavior.
     *
     * The most recent call replaces the previous logger. If you never call this, the SDK stays silent and uses a
     * temporary [OwnIdLogger.Default] only for configuration build failures.
     *
     * Configure logging before [initialize], [initializeFromJson], or [initializeFromFile] if you need logs from
     * configuration, instance creation, or initial HTTP setup.
     *
     * @param block Builder to configure an SDK-wide [OwnIdLogger].
     */
    public fun logger(block: OwnIdLoggerBuilder.() -> Unit) {
        OwnIdRootDIContainer.register<OwnIdLogger>(OwnIdLoggerBuilder().apply(block).build())
    }

    /**
     * Creates or replaces an OwnID instance from programmatic configuration.
     *
     * A successful call installs SDK runtime services if needed, applies the optional language override from the builder, and creates the
     * named instance. The builder must provide a [android.content.Context]; the SDK stores [android.content.Context.getApplicationContext],
     * not an activity context. If an instance with the same name already exists, it is replaced and its instance-scoped work is stopped;
     * fetch new [OwnIdInstance] and namespace handles after replacement.
     *
     * If the builder is invalid, the call logs the error and leaves the current named instance unchanged.
     *
     * Example:
     *
     * ```kotlin
     * OwnId.initialize {
     *     applicationContext = applicationContext
     *     appId = "<OWNID_APP_ID>"
     * }
     * ```
     *
     * @param instanceName Logical name for the SDK instance; defaults to [InstanceName.Default].
     * @param block Builder for [OwnIdConfiguration].
     *
     * @see OwnIdConfigurationBuilder
     */
    public fun initialize(
        instanceName: InstanceName = InstanceName.Default,
        block: OwnIdConfigurationBuilder.() -> Unit
    ) {
        initializeWithBuilder(
            instanceName = instanceName,
            builder = OwnIdConfigurationBuilder().apply(block),
            buildName = "OwnId.initialize",
            build = { build() },
            applicationContext = { applicationContext },
            languages = { languages }
        )
    }

    /**
     * Creates or replaces an OwnID instance from JSON configuration.
     *
     * A successful call installs SDK runtime services if needed, applies the optional "languages" value from JSON, and
     * creates the named instance. If an instance with the same name already exists, it is replaced and its
     * instance-scoped work is stopped; fetch new [OwnIdInstance] and namespace handles after replacement.
     *
     * Invalid or malformed JSON is logged and leaves the current named instance unchanged.
     *
     * Example:
     *
     * ```kotlin
     * OwnId.initializeFromJson {
     *     applicationContext = applicationContext
     *     json = BuildConfig.OWNID_CONFIG_JSON
     * }
     * ```
     *
     * @param instanceName Logical name for the SDK instance; defaults to [InstanceName.Default].
     * @param block Builder for JSON-backed configuration.
     *
     * @see OwnIdJsonConfigurationBuilder
     */
    public fun initializeFromJson(
        instanceName: InstanceName = InstanceName.Default,
        block: OwnIdJsonConfigurationBuilder.() -> Unit
    ) {
        val serialization = OwnIdRootDIContainer.getOrThrow<Serialization>()
        initializeWithBuilder(
            instanceName = instanceName,
            builder = OwnIdJsonConfigurationBuilder(serialization).apply(block),
            buildName = "OwnId.initializeFromJson",
            build = { build() },
            applicationContext = { applicationContext },
            languages = { languages }
        )
    }

    /**
     * Creates or replaces an OwnID instance from file configuration.
     *
     * The default asset name is "OwnIdConfig.json" under the app assets directory.
     *
     * A successful call installs SDK runtime services if needed, applies the optional "languages" value from the file, and
     * creates the named instance. If an instance with the same name already exists, it is replaced and its
     * instance-scoped work is stopped; fetch new [OwnIdInstance] and namespace handles after replacement.
     *
     * Missing, empty, or invalid files are logged and leave the current named instance unchanged.
     *
     * Example:
     *
     * ```kotlin
     * OwnId.initializeFromFile {
     *     applicationContext = applicationContext
     *     fileName = "OwnIdConfig.json"
     * }
     * ```
     *
     * @param instanceName Logical name for the SDK instance; defaults to [InstanceName.Default].
     * @param block Builder for file-backed configuration.
     *
     * @see OwnIdFileConfigurationBuilder
     */
    public fun initializeFromFile(
        instanceName: InstanceName = InstanceName.Default,
        block: OwnIdFileConfigurationBuilder.() -> Unit
    ) {
        val serialization = OwnIdRootDIContainer.getOrThrow<Serialization>()
        initializeWithBuilder(
            instanceName = instanceName,
            builder = OwnIdFileConfigurationBuilder(serialization).apply(block),
            buildName = "OwnId.initializeFromFile",
            build = { build() },
            applicationContext = { applicationContext },
            languages = { languages }
        )
    }

    /**
     * Destroys the OwnID instance for [instanceName].
     *
     * This call is idempotent: missing instances are ignored. Destroying an existing instance removes its SDK instance
     * state and stops instance-scoped work.
     *
     * Previously returned [OwnIdInstance] handles and namespaces derived from them are invalid after this call.
     * To continue working with the same logical instance name, fetch a new handle after reinitializing it.
     *
     * @param instanceName Logical name of the instance to destroy; defaults to [InstanceName.Default].
     */
    public fun destroy(instanceName: InstanceName = InstanceName.Default): Unit =
        OwnIdRootDIContainer.destroyInstanceContainer(instanceName)

    /**
     * Returns the [OwnIdInstance] for the given name.
     *
     * Use this when instance presence is required.
     *
     * The returned handle is bound to the current SDK instance scope for that name.
     * If that named instance is later destroyed or reinitialized, fetch a new handle instead of reusing the old one.
     *
     * @param instanceName Logical name of the instance to fetch; defaults to [InstanceName.Default].
     *
     * @return The instance.
     *
     * @throws IllegalStateException if the named instance is missing.
     */
    @Throws(IllegalStateException::class)
    public fun instance(instanceName: InstanceName = InstanceName.Default): OwnIdInstance =
        checkNotNull(OwnIdRootDIContainer.getInstanceNamespace(instanceName)) {
            "No OwnID instance with name $instanceName. Call OwnId.initialize(...) first."
        }

    /**
     * Creates a child scope and registers a new [Context] in that scope.
     *
     * The child scope keeps the current scope's providers and configuration available.
     * The returned handle stays bound to that child scope; it does not update the default instance in place.
     *
     * The [Context] registered in the child scope is built only from [block].
     *
     * Calls [block] directly. Exceptions thrown from [block] are not handled by OwnId.
     *
     * @param scopeName Name for the child scope; defaults to "Context".
     * @param block Builder that sets [Context] fields: `authz` via [Authz.start] or [Authz.fromToken], and/or `accountDisplayName`.
     *
     * @return The scoped [OwnIdInstance].
     *
     * @throws IllegalStateException if the default instance is missing.
     */
    @Throws(IllegalStateException::class)
    @CheckResult
    public fun withContext(scopeName: String = "Context", block: Context.Builder.() -> Unit): OwnIdInstance =
        instance().withContext(scopeName, block)

    /**
     * Updates the [Context] on the default instance in place.
     *
     * Unlike [withContext], this does not create a child scope.
     *
     * Merge semantics:
     * - Fields assigned in [block] replace existing values in the current scope.
     * - Fields not assigned in [block] keep their existing values.
     * - Assigning `null` clears the field in the resulting context.
     *
     * Calls [block] directly. Exceptions thrown from [block] are not handled by OwnId.
     *
     * @param block Builder that sets [Context] fields: `authz` via [Authz.start] or [Authz.fromToken], and/or `accountDisplayName`.
     *
     * @return The updated [OwnIdInstance].
     *
     * @throws IllegalStateException if the default instance is missing.
     */
    @Throws(IllegalStateException::class)
    public fun setContext(block: Context.Builder.() -> Unit): OwnIdInstance =
        instance().setContext(block)

    /**
     * Clears the [Context] from the default instance.
     *
     * This mutates the default instance scope in place and does not create a child scope. Clearing a missing [Context]
     * is a no-op after the default instance has been created.
     *
     * @return The updated [OwnIdInstance].
     *
     * @throws IllegalStateException if the default instance is missing.
     */
    @Throws(IllegalStateException::class)
    public fun clearContext(): OwnIdInstance =
        instance().clearContext()

    /**
     * Creates a child scope and registers providers from [block] in that scope.
     *
     * The child scope keeps the current scope's providers and configuration available.
     * The returned handle stays bound to that child scope; it does not update the default instance in place.
     *
     * Providers declared in [block] replace providers of the same type only in that child scope.
     *
     * If [block] does not register providers, returns the current instance unchanged. Providers and callbacks are owned
     * by the app and stay available to SDK work that uses the returned scoped handle.
     *
     * Calls [block] directly. Exceptions thrown from [block] are not handled by OwnId.
     *
     * @param scopeName Name for the child scope; defaults to "Providers".
     * @param block Closure that registers providers via [OwnIdProvidersRegistrar].
     *
     * @return The scoped [OwnIdInstance].
     *
     * @throws IllegalStateException if the default instance is missing.
     */
    @Throws(IllegalStateException::class)
    @CheckResult
    public fun withProviders(scopeName: String = "Providers", block: OwnIdProvidersRegistrar.() -> Unit): OwnIdInstance =
        instance().withProviders(scopeName, block)

    /**
     * Updates providers in the current scope in place.
     *
     * Unlike [withProviders], this does not create a child scope.
     *
     * Merge semantics:
     * - Providers declared in [block] replace providers of the same type in the current scope.
     * - Existing providers of supported types that are not declared in [block] remain unchanged.
     *
     * If [block] does not register providers, returns the current instance unchanged. Registered providers replace
     * same-type providers for SDK work that uses the current default instance after this call.
     *
     * Calls [block] directly. Exceptions thrown from [block] are not handled by OwnId.
     *
     * @param block Closure that registers providers via [OwnIdProvidersRegistrar].
     *
     * @return The updated [OwnIdInstance].
     *
     * @throws IllegalStateException if the default instance is missing.
     */
    @Throws(IllegalStateException::class)
    public fun setProviders(block: OwnIdProvidersRegistrar.() -> Unit): OwnIdInstance =
        instance().setProviders(block)

    /**
     * Sets the active language tags for the root SDK language provider.
     *
     * The provided tags replace Android configuration-based language tracking globally for the current process.
     *
     * Passing an empty list restores Android configuration-based language tracking. Passing a non-empty list makes
     * language tags change only through [setLanguage] calls until another empty list is passed or the process restarts.
     *
     * The root [LanguageTagsProvider] is installed during successful initialization. Calling this before initialization
     * is a no-op; language tags supplied by initialization builders are applied during initialization.
     *
     * @param tags A list of BCP 47 language tags (for example, `["en-US", "fr-FR"]`).
     */
    public fun setLanguage(tags: List<String>) {
        OwnIdRootDIContainer.getOrNull<LanguageTagsProvider>()?.setLanguageTags(tags)
    }

    /**
     * Returns [OwnIdFlows] for the default instance.
     *
     * The returned namespace is bound to the current default instance.
     * Fetch it again after [destroy] or same-name reinitialization instead of reusing the old handle.
     *
     * @throws IllegalStateException if the default instance is missing.
     *
     * @see OwnIdFlows
     */
    @get:Throws(IllegalStateException::class)
    public val flows: OwnIdFlows
        get() = instance().flows

    /**
     * Returns [OwnIdHeadless] for the default instance.
     *
     * The returned namespace is bound to the current default instance.
     * Fetch it again after [destroy] or same-name reinitialization instead of reusing the old handle.
     *
     * @throws IllegalStateException if the default instance is missing.
     *
     * @see OwnIdHeadless
     */
    @get:Throws(IllegalStateException::class)
    public val headless: OwnIdHeadless
        get() = instance().headless

    /**
     * Returns [OwnIdWebBridge] for the default instance.
     *
     * Use this namespace to configure [OwnIdWebBridge.defaultPluginFactories] for future bridge instances or to create
     * a fresh [WebBridge] for a WebView session.
     *
     * The returned namespace is bound to the current default instance.
     * Fetch it again after [destroy] or same-name reinitialization instead of reusing the old handle.
     *
     * @throws IllegalStateException if the default instance is missing.
     *
     * @see OwnIdWebBridge
     */
    @get:Throws(IllegalStateException::class)
    public val webBridge: OwnIdWebBridge
        get() = instance().webBridge

    private fun <T> initializeWithBuilder(
        instanceName: InstanceName,
        builder: T,
        buildName: String,
        build: T.() -> OwnIdConfiguration,
        applicationContext: T.() -> android.content.Context,
        languages: T.() -> List<String>?
    ) {
        val configuration = runCatching { builder.build() }
            .onFailure {
                val logger = getLogger() ?: OwnIdLogger.Default
                logger.log(OwnIdLogger.Level.Error, buildName, "Configuration creation failed: ${it.message}", it)
            }
            .getOrNull() ?: return

        OwnIdRootDIContainer.injectRootDefaults(builder.applicationContext())
        builder.languages()?.let { setLanguage(it) }

        OwnIdRootDIContainer.initializeInstanceContainer(instanceName, configuration)
    }

    /**
     * Returns the current DI container for [instanceName], or `null` if that instance has not been created.
     *
     * This is an internal SDK module contract, not a public app integration contract. Use it for one-time access to SDK
     * dependencies scoped to a named instance. The returned container is a lifecycle-bound view; do not keep it across
     * instance replacement or destruction.
     */
    @InternalOwnIdAPI
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun getInstanceContainer(instanceName: InstanceName = InstanceName.Default): DIContainer? =
        OwnIdRootDIContainer.getInstanceContainer(instanceName)

    /**
     * Returns a hot current-value stream for the instance container of [instanceName].
     *
     * This is an internal SDK module contract, not a public app integration contract. Use it when a module should
     * rebind to instance creation, replacement, or destruction instead of keeping a stale container view from
     * [getInstanceContainer]. The stream's current value is `null` while the instance is unavailable and updates when
     * that named instance is created, replaced, or destroyed.
     */
    @InternalOwnIdAPI
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun getInstanceContainerStateFlow(instanceName: InstanceName = InstanceName.Default): StateFlow<DIContainer?> =
        OwnIdRootDIContainer.getInstanceContainerStateFlow(instanceName)

    /**
     * Returns the currently configured SDK-wide logger, or `null` if logging was not configured.
     *
     * This is an internal SDK module contract, not a public app integration contract. Internal SDK code can use this
     * for one-off logging in places where an instance-scoped log router is not available.
     */
    @InternalOwnIdAPI
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun getLogger(): OwnIdLogger? =
        OwnIdRootDIContainer.getOrNull()
}
