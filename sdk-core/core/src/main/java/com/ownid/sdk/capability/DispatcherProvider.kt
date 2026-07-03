package com.ownid.sdk.capability

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Provides coroutine dispatchers used by SDK operations.
 *
 * This capability is an instance-scoped scheduling contract for SDK internals. It does not move work to the Android
 * main thread; UI-backed capabilities switch to their required UI context themselves.
 *
 * - [defaultDispatcher]: general computation and instance-owned background scopes.
 * - [ioDispatcher]: storage, network, and other blocking or I/O-bound work.
 */
public interface DispatcherProvider : Capability {
    /** Dispatcher for general computation and non-UI background work. */
    public fun defaultDispatcher(): CoroutineDispatcher

    /** Dispatcher for I/O-bound work. The default SDK provider limits parallelism for shared SDK I/O. */
    public fun ioDispatcher(): CoroutineDispatcher
}
