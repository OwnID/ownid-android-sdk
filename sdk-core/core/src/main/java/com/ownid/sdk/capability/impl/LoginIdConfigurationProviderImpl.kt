package com.ownid.sdk.capability.impl

import com.ownid.sdk.capability.LoginIdConfigurationProvider
import com.ownid.sdk.model.LoginIdConfiguration
import java.util.concurrent.atomic.AtomicReference

/**
 * In-memory [LoginIdConfigurationProvider] implementation for an OwnID instance.
 *
 * The provider stores a server-driven fallback configuration and an optional app-owned override. Reads return the
 * override when present, otherwise the fallback. The initial fallback is [LoginIdConfiguration.DEFAULT_EMAIL].
 *
 * All methods are synchronous and use an [AtomicReference], so callers do not need a specific coroutine dispatcher.
 * Invalid updates with no supported types are ignored. Valid updates are normalized by preserving the first occurrence
 * of each supported type and dropping validation regexes for types not in the supported list.
 */
internal class LoginIdConfigurationProviderImpl : LoginIdConfigurationProvider {

    private data class State(
        val autoConfiguration: LoginIdConfiguration,
        val forcedConfiguration: LoginIdConfiguration?
    )

    private val state: AtomicReference<State> = AtomicReference(
        State(
            autoConfiguration = LoginIdConfiguration.DEFAULT_EMAIL,
            forcedConfiguration = null
        )
    )

    override val configuration: LoginIdConfiguration
        get() = state.get().forcedConfiguration ?: state.get().autoConfiguration

    override fun setServerConfiguration(configuration: LoginIdConfiguration) {
        val normalized = configuration.normalizedOrNull() ?: return
        while (true) {
            val current = state.get()
            val next = current.copy(autoConfiguration = normalized)
            if (next == current) return
            if (state.compareAndSet(current, next)) return
        }
    }

    override fun setConfiguration(configuration: LoginIdConfiguration) {
        val normalized = configuration.normalizedOrNull() ?: return
        while (true) {
            val current = state.get()
            val next = current.copy(forcedConfiguration = normalized)
            if (next == current) return
            if (state.compareAndSet(current, next)) return
        }
    }

    override fun clearConfiguration() {
        while (true) {
            val current = state.get()
            if (current.forcedConfiguration == null) return
            val next = current.copy(forcedConfiguration = null)
            if (state.compareAndSet(current, next)) return
        }
    }

    private fun LoginIdConfiguration.normalizedOrNull(): LoginIdConfiguration? {
        val normalizedTypes = supportedTypes.distinct().takeIf { it.isNotEmpty() } ?: return null
        val allowedTypes = normalizedTypes.toSet()
        return LoginIdConfiguration(
            supportedTypes = normalizedTypes,
            validationRegexes = validationRegexes.filterKeys(allowedTypes::contains)
        )
    }
}
