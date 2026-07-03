package com.ownid.sdk.capability

import com.ownid.sdk.model.LoginIdConfiguration

/**
 * Provides the active login ID configuration used to classify and validate login ID values.
 *
 * The SDK registers this capability in each instance and updates its server-driven configuration from app config.
 * Calling [setConfiguration] installs an app-owned override for that instance; the override takes precedence until
 * [clearConfiguration] removes it. This provider supplies configuration to login ID models and validators, but it does
 * not imply that login ID values are persisted.
 *
 * Implementations should be safe for concurrent SDK reads and updates. The built-in provider keeps only in-memory
 * state, ignores configurations with no supported types, preserves the first occurrence of duplicated types, and drops
 * validation entries for unsupported types.
 */
public interface LoginIdConfigurationProvider : Capability {
    /**
     * Active login ID configuration.
     *
     * [LoginIdConfiguration.supportedTypes] order is used as type resolution priority. A type-specific regex in
     * [LoginIdConfiguration.validationRegexes] overrides that type's default validation rule.
     */
    public val configuration: LoginIdConfiguration

    /**
     * Updates the server-driven fallback configuration.
     *
     * If an app override is active, the updated server configuration becomes visible only after [clearConfiguration].
     * The built-in provider ignores invalid configurations that contain no supported types and keeps the previous state.
     */
    public fun setServerConfiguration(configuration: LoginIdConfiguration)

    /**
     * Sets an app-owned configuration override.
     *
     * The override remains active until [clearConfiguration]. The built-in provider ignores invalid configurations that
     * contain no supported types and keeps the previous state.
     */
    public fun setConfiguration(configuration: LoginIdConfiguration)

    /** Removes the app-owned override and returns [configuration] to the latest valid server-driven fallback. */
    public fun clearConfiguration()
}
