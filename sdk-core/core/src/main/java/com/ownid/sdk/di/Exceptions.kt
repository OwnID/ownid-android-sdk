package com.ownid.sdk.di

/**
 * Resolver failure thrown by [DIContainerResolver.getOrThrow] when a registered dependency cannot be created.
 *
 * This is an internal SDK module-boundary error. Public API, operation, and flow entry points generally convert
 * dependency setup failures into their typed result failure instead of throwing this exception directly.
 *
 * [dependencyName] identifies the requested class, [scopeName] identifies the scope where resolution started, and
 * [entryPoint] carries optional resolution context. The original factory, cycle, or runtime failure is exposed as
 * [cause].
 */
public class DependencyResolutionException internal constructor(
    public val dependencyName: String,
    public val scopeName: String,
    public val entryPoint: String?,
    cause: Throwable?
) : IllegalStateException(
    buildString {
        append("Failed to resolve ")
        append(dependencyName)
        append(" in ")
        append(scopeName)
        if (!entryPoint.isNullOrBlank()) {
            append(" for ")
            append(entryPoint)
        }
        val msg = cause?.message
        if (!msg.isNullOrBlank()) {
            append(": ")
            append(msg)
        }
    },
    cause
)

/**
 * Resolver failure thrown by [DIContainerResolver.getOrThrow] when no dependency exists in the visible scope tree.
 *
 * This is an internal SDK module-boundary error. Public API, operation, and flow entry points generally convert missing
 * dependencies into their typed result failure instead of throwing this exception directly.
 *
 * [dependencyName] identifies the missing class, [scopeName] identifies the scope where resolution started, and
 * [entryPoint] carries optional resolution context.
 */
public class MissingDependencyException internal constructor(
    public val dependencyName: String,
    public val scopeName: String,
    public val entryPoint: String?
) : IllegalStateException(
    buildString {
        append("Missing ")
        append(dependencyName)
        append(" in ")
        append(scopeName)
        if (!entryPoint.isNullOrBlank()) {
            append(" for ")
            append(entryPoint)
        }
    }
)
