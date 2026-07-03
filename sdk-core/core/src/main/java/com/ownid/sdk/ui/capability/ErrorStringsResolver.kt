package com.ownid.sdk.ui.capability

import com.ownid.sdk.capability.DispatcherProvider
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.ErrorStrings
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Keeps the latest resolved error strings for mapping error codes to user-visible messages.
 *
 * The resolver subscribes to [ErrorStringsProvider] when one is available and stores each non-null emission. Lookups use
 * that latest stored value; when no value has been emitted, they use the fallback strings supplied for the lookup.
 */
internal class ErrorStringsResolver(
    errorStringsProvider: ErrorStringsProvider?,
    dispatcherProvider: DispatcherProvider,
    instanceJob: Job
) : CoroutineScope by CoroutineScope(dispatcherProvider.defaultDispatcher() + SupervisorJob(instanceJob) + CoroutineName("OwnId.ErrorStringsResolver")) {

    @Volatile
    private var currentErrorStrings: ErrorStrings? = null

    init {
        errorStringsProvider
            ?.getStrings(ErrorStringsParams())
            ?.onEach { strings -> if (strings != null) currentErrorStrings = strings }
            ?.launchIn(this)
    }

    fun toLocalizedMessage(errorCode: ErrorCode, fallbackErrorStrings: ErrorStrings): String {
        val errorStrings = currentErrorStrings ?: fallbackErrorStrings
        return errorCode.resolveLocalizedMessage(errorStrings)
    }
}
