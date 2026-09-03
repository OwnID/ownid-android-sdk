package com.ownid.sdk.di.impl

import androidx.annotation.RestrictTo
import com.ownid.sdk.di.DIContainer
import com.ownid.sdk.di.DIContainerResolver
import com.ownid.sdk.di.DependencyResolutionException
import com.ownid.sdk.di.MissingDependencyException
import com.ownid.sdk.logger.OwnIdLogRouter
import com.ownid.sdk.model.InternalOwnIdAPI

/**
 * Internal [DIContainer] implementation for the SDK module graph.
 *
 * The cross-module contract is hierarchical scope resolution: child scopes inherit parent bindings, child
 * registrations override only that child subtree, and factories resolve against the original requester so
 * child-scope overrides are honored. Binding storage is synchronized, nested resolution is tracked per calling thread
 * for cycle detection, and factories execute synchronously without container-owned caching or cancellation.
 */
@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY)
public open class DIContainerImpl internal constructor(
    public override val scopeName: String,
    private val parent: DIContainer? = null
) : DIContainer {

    internal interface Logger {
        val enableTrace: Boolean
        fun trace(container: DIContainer, tag: String, msg: String?)
        fun error(container: DIContainer, tag: String, msg: String?, cause: Throwable?)
    }

    private sealed class InstanceOrFactory(val dependencies: List<Class<*>>) {
        class Instance<T : Any>(val value: T) : InstanceOrFactory(emptyList())
        class Factory<T : Any>(dependencies: List<Class<*>>, val factory: DIContainerResolver.() -> T) : InstanceOrFactory(dependencies)
    }

    private val container = mutableMapOf<Class<*>, InstanceOrFactory>()

    internal val lock = Any()

    @Volatile
    private var logger: Logger? = null

    private companion object {
        private val RESOLUTION_STACK = ThreadLocal<MutableList<Pair<DIContainer, Class<*>>>>()
    }

    internal fun setLogger(logger: Logger?) {
        this.logger = logger
    }

    internal fun setLogRouter(logRouter: OwnIdLogRouter?, enableTrace: Boolean = logger?.enableTrace == true) {
        logger = logRouter?.let { router ->
            object : Logger {
                private val suppressedLogClasses = setOf(
                    "com.ownid.sdk.logger.OwnIdLogRouter",
                    "com.ownid.sdk.logger.OwnIdLogger",
                    "com.ownid.sdk.logger.ServerLogger"
                )

                override val enableTrace: Boolean = enableTrace

                override fun trace(container: DIContainer, tag: String, msg: String?) {
                    val message = msg.orEmpty()
                    if (suppressedLogClasses.any { message.contains(it) }) return
                    router.logV(container, tag, msg)
                }

                override fun error(container: DIContainer, tag: String, msg: String?, cause: Throwable?) {
                    val message = msg.orEmpty()
                    if (suppressedLogClasses.any { message.contains(it) }) return
                    router.logI(container, tag, msg, cause)
                }
            }
        }
    }

    // DIContainerRegistrar

    override fun createScope(scopeName: String): DIContainer =
        DIContainerImpl(scopeName = scopeName, parent = this).also { child ->
            child.setLogger(logger)
        }

    override fun <T : Any> register(clazz: Class<T>, instance: T) {
        var overwritten = false
        synchronized(lock) {
            if (container.containsKey(clazz)) overwritten = true
            container[clazz] = InstanceOrFactory.Instance(instance)
        }
        logTrace("register") { "For ${clazz.name} (overwritten=$overwritten)" }
    }

    override fun <T : Any> registerFactory(clazz: Class<T>, dependencies: List<Class<*>>, factory: DIContainerResolver.() -> T) {
        var overwritten = false
        synchronized(lock) {
            if (container.containsKey(clazz)) overwritten = true
            container[clazz] = InstanceOrFactory.Factory(dependencies, factory)
        }
        logTrace("registerFactory") { "For ${clazz.name}, deps=${dependencies.joinToString { it.name }} (overwritten=$overwritten)" }
    }

    override fun <T : Any> remove(clazz: Class<T>) {
        val removed: Any?
        synchronized(lock) {
            @Suppress("UNCHECKED_CAST")
            removed = container.remove(clazz)
        }
        logTrace("remove") { "For ${clazz.name}, removed=${removed != null}" }
    }

    // DIContainerResolver

    override fun canResolve(clazz: Class<*>): Boolean =
        getUnsatisfiedDependencies(clazz) == null

    override fun getUnsatisfiedDependencies(clazz: Class<*>): List<String>? {
        logTrace("getUnsatisfiedDependencies") { "Checking ${clazz.name}" }

        val traces = LinkedHashSet<String>()
        collectUnsatisfiedTraces(clazz, mutableListOf(), traces, requester = this)
        return traces.takeIf { it.isNotEmpty() }?.toList()
    }

    @Throws(MissingDependencyException::class, DependencyResolutionException::class)
    override fun <T : Any> getOrThrow(clazz: Class<T>): T = getOrThrow(clazz, logMissing = true)

    @Throws(MissingDependencyException::class, DependencyResolutionException::class)
    private fun <T : Any> getOrThrow(clazz: Class<T>, logMissing: Boolean): T {
        val stack = RESOLUTION_STACK.get() ?: mutableListOf<Pair<DIContainer, Class<*>>>().also { RESOLUTION_STACK.set(it) }
        val isRoot = stack.isEmpty()
        val key = this to clazz

        if (stack.contains(key)) {
            val cycleMessage = "Dependency cycle detected: ${(stack + key).joinToString(" -> ") { (c, clz) -> "${c.scopeName}:${clz.name}" }}"
            val entry = stack.firstOrNull()?.second?.name ?: "Direct:get(${clazz.name})"
            val cause = IllegalStateException(cycleMessage)
            logError("getOrThrow", "Cycle while resolving ${clazz.name}", cause)

            throw DependencyResolutionException(dependencyName = clazz.name, scopeName = scopeName, entryPoint = entry, cause = cause)
        }

        stack.add(key)

        try {
            val instance = resolveInstance(clazz, logMissing, requester = this) ?: throw MissingDependencyException(
                dependencyName = clazz.name,
                scopeName = scopeName,
                entryPoint = stack.firstOrNull()?.second?.name ?: "Direct:get(${clazz.name})"
            )


            logTrace("getOrThrow") {
                "Resolved ${clazz.name} -> ${instance::class.java.simpleName}#${instance.hashCode()} depth=${stack.size - 1}"
            }

            return instance
        } catch (missing: MissingDependencyException) {
            if (logMissing) logError("getOrThrow", "Missing dependency ${missing.dependencyName} (depth=${stack.size - 1})", missing)
            throw missing
        } catch (resolution: DependencyResolutionException) {
            logError("getOrThrow", "Resolution failure for ${clazz.name} (depth=${stack.size - 1})", resolution)
            throw resolution
        } catch (cause: Exception) {
            val entry = stack.firstOrNull()?.second?.name ?: "Direct:get(${clazz.name})"
            logError("getOrThrow", "Unexpected error resolving ${clazz.name} (depth=${stack.size - 1})", cause)
            throw DependencyResolutionException(dependencyName = clazz.name, scopeName = scopeName, entryPoint = entry, cause = cause)
        } finally {
            if (stack.isNotEmpty()) stack.removeAt(stack.size - 1)
            if (isRoot) {
                RESOLUTION_STACK.remove()
            }
        }
    }

    override fun <T : Any> getOrNull(clazz: Class<T>): T? = try {
        getOrThrow(clazz, logMissing = false)
    } catch (_: MissingDependencyException) {
        null
    } catch (_: DependencyResolutionException) {
        null
    }

    override fun <T : Any> getAllInstancesOf(clazz: Class<T>): List<T> {
        logTrace("getAllAvailableInstancesOf") { "Collecting ${clazz.name}" }

        val result = mutableListOf<T>()
        val seenClasses = mutableSetOf<Class<*>>()
        val requester = this

        fun collect(container: DIContainer?) {
            when (container) {
                is DIContainerImpl -> {
                    val localKeys = synchronized(container.lock) { container.container.keys.toList() }
                    for (candidateClass in localKeys) {
                        if (!clazz.isAssignableFrom(candidateClass)) continue
                        if (!seenClasses.add(candidateClass)) continue

                        @Suppress("UNCHECKED_CAST")
                        val instance = requester.getOrNull(candidateClass as Class<T>)
                        if (instance != null) result.add(instance)
                    }
                    collect(container.parent)
                }

                null -> Unit
            }
        }

        collect(this)

        logTrace("getAllAvailableInstancesOf") { "Found ${result.size} instances of ${clazz.simpleName}" }
        return result
    }

    // Internal helpers

    private fun <T : Any> resolveInstance(clazz: Class<T>, logMissing: Boolean, requester: DIContainerImpl): T? {
        val localBinding: InstanceOrFactory?
        synchronized(lock) {
            localBinding = container[clazz]
        }

        if (localBinding != null) {
            @Suppress("UNCHECKED_CAST")
            return when (localBinding) {
                is InstanceOrFactory.Instance<*> -> localBinding.value as T
                is InstanceOrFactory.Factory<*> -> localBinding.factory(requester) as T
            }
        }

        val p = parent as? DIContainerImpl ?: return null
        return p.resolveInstance(clazz, logMissing, requester)
    }

    private fun collectUnsatisfiedTraces(clazz: Class<*>, path: MutableList<Class<*>>, traces: MutableSet<String>, requester: DIContainerImpl) {
        if (path.contains(clazz)) {
            traces.add(formatTrace(path + clazz, "cyclic"))
            return
        }

        val localBinding = findBindingInChain(requester, clazz)
        if (localBinding == null) {
            traces.add(formatTrace(path + clazz, "missing"))
            return
        }

        if (localBinding.dependencies.isEmpty()) return

        path.add(clazz)
        for (depClass in localBinding.dependencies) {
            collectUnsatisfiedTraces(depClass, path, traces, requester)
        }
        path.removeAt(path.size - 1)
    }

    private fun formatTrace(path: List<Class<*>>, issue: String): String {
        val labels = path.mapIndexed { index, clazz ->
            if (index == 0) "${scopeName}:${clazz.name}" else clazz.name
        }
        return "${labels.joinToString(" -> ")} [$issue]"
    }

    private fun findBindingInChain(requester: DIContainerImpl, clazz: Class<*>): InstanceOrFactory? {
        var current: DIContainerImpl? = requester
        while (current != null) {
            val currentContainer = current
            val localBinding = synchronized(currentContainer.lock) { currentContainer.container[clazz] }
            if (localBinding != null) return localBinding
            current = currentContainer.parent as? DIContainerImpl
        }
        return null
    }

    private inline fun logTrace(tag: String, crossinline msg: () -> String) {
        val currentLogger = logger
        if (currentLogger != null && currentLogger.enableTrace) {
            currentLogger.trace(this, "$scopeName:$tag", msg())
        }
    }

    private fun logError(tag: String, msg: String?, cause: Throwable?) {
        logger?.error(this, "$scopeName:$tag", msg, cause)
    }
}
