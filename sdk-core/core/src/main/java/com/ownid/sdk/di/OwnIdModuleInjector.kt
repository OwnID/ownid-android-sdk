package com.ownid.sdk.di

import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.RestrictTo
import com.ownid.sdk.logger.OwnIdLogRouter
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.runCatchingExceptions

/**
 * Discovers optional SDK modules and injects their bindings into an instance container.
 *
 * This is an internal SDK module contract, not a public app integration contract. Modules discovered through
 * SDK-owned metadata may register dependencies for their own artifact boundary. Injection is best-effort: a module
 * that cannot be loaded or injected is logged and skipped so the Core instance remains usable with the bindings that
 * are available.
 */
@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public object OwnIdModuleInjector {

    /**
     * SDK module entry point for registering dependencies into an instance [DIContainer].
     *
     * This is an internal SDK module contract, not a public app integration contract. Implementations own only their
     * module's bindings. They may depend on Core services and may replace module-owned fallback bindings when that is
     * the intended extension point.
     */
    @InternalOwnIdAPI
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public interface Module {
        /** Registers this module's dependencies; failures are isolated by [OwnIdModuleInjector]. */
        public fun injectIntoInstanceContainer(container: DIContainer)
    }

    internal fun injectIntoInstanceContainer(container: DIContainer) {
        val logger = container.getOrNull<OwnIdLogRouter>()

        container.runCatchingExceptions {
            loadModules(context = getOrThrow<Context>(), logger).forEach { module ->
                runCatchingExceptions { module.injectIntoInstanceContainer(container) }
                    .onFailure { logger?.logW(this, "injectIntoInstanceContainer", "Failed for: ${module::class.java}", it) }
            }
        }
    }

    private const val INJECTOR_KEY: String = "com.ownid.sdk.ui.INJECTOR_KEY"

    private fun loadModules(context: Context, logger: OwnIdLogRouter?): List<Module> {
        val packageInfo = runCatchingExceptions {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_META_DATA or PackageManager.GET_SERVICES)
        }.onFailure { logger?.logW(this, "loadModules", "Failed to get package info", it) }
            .getOrNull()

        val serviceClassNames = packageInfo?.services?.mapNotNull { it.metaData?.getString(INJECTOR_KEY) }.orEmpty()

        return serviceClassNames.mapNotNull { injectorClassName ->
            runCatchingExceptions { Class.forName(injectorClassName).getConstructor().newInstance() as Module }
                .onFailure { logger?.logW(this, "loadModules", "Failed to instantiate injector: $injectorClassName", it) }
                .getOrNull()
        }
    }
}
