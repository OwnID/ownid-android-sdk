package com.ownid.sdk.di

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import com.ownid.sdk.OwnIdConfiguration
import com.ownid.sdk.capability.ActivityProvider
import com.ownid.sdk.capability.LanguageTagsProvider
import com.ownid.sdk.capability.LocalInfo
import com.ownid.sdk.capability.RunDiagnostic
import com.ownid.sdk.capability.Serialization
import com.ownid.sdk.capability.impl.ActivityProviderImpl
import com.ownid.sdk.capability.impl.LanguageTagsProviderImpl
import com.ownid.sdk.capability.impl.LocalInfoImpl
import com.ownid.sdk.capability.impl.RunDiagnosticImpl
import com.ownid.sdk.capability.impl.SerializationImpl
import com.ownid.sdk.di.OwnIdRootDIContainer.getInstanceContainerStateFlow
import com.ownid.sdk.di.impl.DIContainerImpl
import com.ownid.sdk.instanceIdentity
import com.ownid.sdk.logger.OwnIdLogRouter
import com.ownid.sdk.logger.OwnIdLogger
import com.ownid.sdk.logger.ServerLogger
import com.ownid.sdk.model.InstanceName
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.namespace.OwnIdFullInstance
import com.ownid.sdk.namespace.instanceNamespace
import com.ownid.sdk.ui.capability.OperationUiContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide root container for OwnID SDK modules.
 *
 * This is an internal SDK module contract, not a public app integration contract. The root owns process-wide
 * capabilities and named instance lifecycles. A named instance is the replacement and destroy boundary for its
 * configuration, scoped dependencies, namespace wrapper, and instance-owned work.
 *
 * Previously returned containers and namespace wrappers are bound to the lifecycle that produced them. Modules that
 * need to survive replacement or destruction should observe [getInstanceContainerStateFlow] and rebind to the current
 * container. Lifecycle mutations are serialized, and the state stream publishes create, replace, and destroy updates
 * for the named instance.
 */
@InternalOwnIdAPI
internal object OwnIdRootDIContainer : DIContainerImpl(scopeName = "ROOT") {
    private class InstanceEntry {
        val containerState = MutableStateFlow<DIContainer?>(null)

        @Volatile
        var namespace: OwnIdFullInstance? = null
    }

    /** Retains lifecycle streams for named instances across create, replace, and destroy transitions. */
    private val instances = mutableMapOf<InstanceName, InstanceEntry>()

    private var rootDefaultsInjected: Boolean = false

    init {
        register<OwnIdLogRouter>(
            OwnIdLogRouter(
                ownIdLoggerProvider = { getOrNull<OwnIdLogger>() },
                serverLoggerProvider = { null }
            )
        )

        register<Serialization>(SerializationImpl())
    }

    internal fun injectRootDefaults(context: Context) {
        synchronized(lock) {
            val rootLogRouter = getOrNull<OwnIdLogRouter>()

            if (rootDefaultsInjected) {
                rootLogRouter?.logI(this, "injectRootDefaults", "Already injected. Ignoring.")
                return
            }

            val isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

            setLogRouter(rootLogRouter, enableTrace = isDebuggable)

            register<Context>(context.applicationContext)

            register<LocalInfo>(LocalInfoImpl(context = context.applicationContext))

            register<ActivityProvider>(ActivityProviderImpl(context = context.applicationContext, logger = rootLogRouter))

            register<LanguageTagsProvider>(LanguageTagsProviderImpl(context = context.applicationContext, logger = rootLogRouter))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                RunDiagnosticImpl.create(this)?.let { register<RunDiagnostic>(it) }
            }

            rootDefaultsInjected = true
        }
    }

    internal fun initializeInstanceContainer(
        instanceName: InstanceName = InstanceName.Default,
        configuration: OwnIdConfiguration,
        languages: List<String>? = null
    ): DIContainer =
        synchronized(lock) {
            val instanceIdentity = configuration.instanceIdentity()
            val conflictingInstanceName = instances.entries.firstOrNull { (existingInstanceName, entry) ->
                existingInstanceName != instanceName && entry.containerState.value?.getOrNull<OwnIdConfiguration>()?.instanceIdentity() == instanceIdentity
            }?.key

            if (conflictingInstanceName != null) {
                throw IdentityConflictException(conflictingInstanceName)
            }

            val entry = getOrCreateInstanceEntry(instanceName)
            val previousContainer = entry.containerState.value
            val container = createScope(scopeName = instanceName.value)
            var published = false

            try {
                container.injectInstanceDefaults(instanceName, configuration)
                val namespace = container.instanceNamespace

                languages?.let { getOrNull<LanguageTagsProvider>()?.setLanguageTags(it) }

                entry.containerState.value = container
                entry.namespace = namespace
                published = true
            } finally {
                if (!published) {
                    container.getOrNull<Job>()?.cancel()
                }
            }

            getOrNull<RunDiagnostic>()?.reportPreviousRun(container.getOrNull<ServerLogger>())
            previousContainer?.getOrNull<Job>()?.cancel()

            val logRouter = container.getOrNull<OwnIdLogRouter>()
            if (container.canResolve(OperationUiContainer::class.java).not()) {
                logRouter?.logI(this, "initializeInstanceContainer", "No UI module injected. OperationUiContainer is missing")
            }

            logRouter?.logD(this, "initializeInstanceContainer", "Instance created: $instanceName")

            container
        }

    internal fun getInstanceContainer(instanceName: InstanceName = InstanceName.Default): DIContainer? =
        synchronized(lock) { instances[instanceName]?.containerState?.value }

    internal fun getInstanceContainerStateFlow(instanceName: InstanceName = InstanceName.Default): StateFlow<DIContainer?> =
        synchronized(lock) { getOrCreateInstanceEntry(instanceName).containerState.asStateFlow() }

    internal fun getInstanceNamespace(instanceName: InstanceName = InstanceName.Default): OwnIdFullInstance? =
        synchronized(lock) { instances[instanceName]?.namespace }

    internal fun destroyInstanceContainer(instanceName: InstanceName = InstanceName.Default): Unit = synchronized(lock) {
        val entry = instances[instanceName] ?: return@synchronized

        val container = entry.containerState.value ?: return@synchronized
        entry.containerState.value = null
        entry.namespace = null
        container.getOrNull<OwnIdLogRouter>()?.logD(this, "destroyInstanceContainer", "Instance destroyed: $instanceName")
        container.getOrNull<Job>()?.cancel()
    }

    private fun getOrCreateInstanceEntry(instanceName: InstanceName): InstanceEntry =
        instances[instanceName] ?: InstanceEntry().also { instances[instanceName] = it }
}
