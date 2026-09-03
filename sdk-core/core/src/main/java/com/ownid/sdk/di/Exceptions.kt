package com.ownid.sdk.di

import com.ownid.sdk.model.InstanceName

/**
 * Resolver failure thrown by [DIContainerResolver.getOrThrow] when a registered dependency cannot be created.
 *
 * This is a public low-level SDK error that advanced consumers can catch for diagnostics. Normal integration code
 * should use typed API, operation, and flow failures instead of treating this exception as control flow.
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
 * This is a public low-level SDK error that advanced consumers can catch for diagnostics. Normal integration code
 * should use typed API, operation, and flow failures instead of treating this exception as control flow.
 *
 * [dependencyName] identifies the missing class, [scopeName] identifies the scope where resolution started, and
 * [entryPoint] identifies the originally requested class. If resolving `A` reaches a missing `B`, [dependencyName]
 * identifies `B` while [entryPoint] identifies `A`.
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

internal class IdentityConflictException(conflictingInstanceName: InstanceName) :
    IllegalStateException("Identity is already in use by '$conflictingInstanceName'")
