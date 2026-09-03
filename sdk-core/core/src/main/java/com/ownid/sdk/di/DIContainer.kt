package com.ownid.sdk.di

import androidx.annotation.RestrictTo
import com.ownid.sdk.model.InternalOwnIdAPI

/**
 * Resolves dependencies visible from one SDK-internal scope.
 *
 * Lookup starts in the bound scope and walks parent scopes. When a parent binding is a factory, the factory receives
 * the original requester so child-scope overrides remain visible during nested resolution. Resolver calls are
 * synchronous, do not switch dispatchers, and do not own cancellation for resolved objects or factory-created work.
 */
@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface DIContainerResolver {
    /** Scope name used in diagnostic traces and resolution errors. */
    public val scopeName: String

    /** Returns true when [clazz] and its declared dependencies are registered without a declared cycle. */
    public fun canResolve(clazz: Class<*>): Boolean

    /** Returns missing or cyclic declared dependency traces for [clazz], or null when none are found. */
    public fun getUnsatisfiedDependencies(clazz: Class<*>): List<String>?

    /**
     * Resolves the dependency for [clazz] or throws.
     *
     * @throws MissingDependencyException if [clazz] or one of its factory dependencies is not registered in the scope
     * tree. For a requested `A` whose factory reaches a missing `B`, the error identifies `B` as the missing dependency
     * and retains `A` as its entry point.
     * @throws DependencyResolutionException if resolution fails (e.g. cycle or factory error).
     */
    @Throws(MissingDependencyException::class, DependencyResolutionException::class)
    public fun <T : Any> getOrThrow(clazz: Class<T>): T

    /** Resolves [clazz], or returns null after suppressing missing-dependency and resolution failures. */
    public fun <T : Any> getOrNull(clazz: Class<T>): T?

    /** Returns matching visible bindings across the scope tree, omitting entries that fail best-effort resolution. */
    public fun <T : Any> getAllInstancesOf(clazz: Class<T>): List<T>
}

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Throws(MissingDependencyException::class, DependencyResolutionException::class)
public inline fun <reified T : Any> DIContainerResolver.getOrThrow(): T = getOrThrow(T::class.java)

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public inline fun <reified T : Any> DIContainerResolver.getOrNull(): T? = getOrNull(T::class.java)

/**
 * Registers dependencies into one SDK-internal scope.
 *
 * Registrations replace bindings for the same class in this scope only. Child scopes inherit visible parent bindings,
 * while parent and sibling scopes are not mutated. Factory bindings are invoked for each resolution; register an
 * instance when shared identity or lifecycle ownership is required.
 */
@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface DIContainerRegistrar {
    /** Creates a child scope with the given [scopeName], inheriting this container's registrations. */
    public fun createScope(scopeName: String): DIContainer

    /** Registers a pre-built [instance] for the given [clazz]. */
    public fun <T : Any> register(clazz: Class<T>, instance: T)

    /** Registers a non-memoized [factory] for [clazz] with declared [dependencies] for resolvability checks. */
    public fun <T : Any> registerFactory(clazz: Class<T>, dependencies: List<Class<*>>, factory: DIContainerResolver.() -> T)

    /** Removes the registration for [clazz] from this scope. */
    public fun <T : Any> remove(clazz: Class<T>)
}

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public inline fun <reified T : Any> DIContainerRegistrar.register(instance: T): Unit = register(T::class.java, instance)

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public inline fun <reified T : Any> DIContainerRegistrar.registerFactory(
    vararg dependencies: Class<*> = emptyArray(), noinline factory: DIContainerResolver.() -> T
): Unit = registerFactory(T::class.java, dependencies.toList(), factory)

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public inline fun <reified T : Any> DIContainerRegistrar.remove(): Unit = remove(T::class.java)

/** Internal SDK module container that can both resolve and register scoped dependencies. */
@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface DIContainer : DIContainerResolver, DIContainerRegistrar
