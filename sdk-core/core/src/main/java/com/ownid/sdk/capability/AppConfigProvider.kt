package com.ownid.sdk.capability

import com.ownid.sdk.model.AppConfig
import kotlinx.coroutines.flow.Flow

/**
 * Internal boundary for runtime [AppConfig].
 *
 * The provider starts a remote fetch during instance bootstrap and waits only for a bounded bootstrap window before
 * resolving a usable configuration. Fallback order is the last stored configuration for the same app/environment,
 * then [AppConfig.DEFAULT].
 *
 * The applied payload is the SDK module boundary for runtime rules, UI asset configuration, and diagnostics thresholds.
 * This is not an app-developer integration surface.
 */
internal interface AppConfigProvider : Capability {

    /**
     * Returns the active [AppConfig], suspending only until bootstrap produces a value.
     *
     * A cold-start caller may wait for the first network attempt, the bootstrap timeout, or an immediate fetch failure.
     * The returned value is always non-null. Network success after fallback may update [config] later.
     *
     * @return The current, stored, or default runtime configuration.
     */
    suspend fun getOrFetchConfig(): AppConfig

    /**
     * Hot stream of applied non-null [AppConfig] values.
     *
     * The stream emits once bootstrap applies a fresh, stored, or default configuration, and emits again when a later
     * successful fetch applies a changed value.
     */
    val config: Flow<AppConfig>
}
