package com.ownid.sdk.capability

import java.io.IOException

/**
 * Typed key-value storage capability for SDK-owned persisted data.
 *
 * Values are stored by key as String, Boolean, number, or Double values. Get methods return the stored value, or the
 * supplied default when the key is absent. The default implementation also returns the supplied default after an
 * [IOException] read failure.
 *
 * Put and remove methods may throw [IOException] on write failures.
 */
public interface Storage : Capability {

    /**
     * Returns the stored String, or [default] if the key is absent or cannot be read.
     *
     * @param default Value returned when the key is absent or the default implementation catches an [IOException] read
     * failure. Defaults to null.
     */
    public suspend fun getString(key: String, default: String? = null): String?

    /**
     * Persists a String value under [key].
     *
     * @throws IOException on write failure.
     */
    @Throws(IOException::class)
    public suspend fun putString(key: String, value: String)

    /**
     * Returns the stored Boolean, or [default] if the key is absent or cannot be read.
     *
     * @param default Value returned when the key is absent or the default implementation catches an [IOException] read
     * failure. Defaults to null.
     */
    public suspend fun getBoolean(key: String, default: Boolean? = null): Boolean?

    /**
     * Persists a Boolean value under [key].
     *
     * @throws IOException on write failure.
     */
    @Throws(IOException::class)
    public suspend fun putBoolean(key: String, value: Boolean)

    /**
     * Returns the stored number, or [default] if the key is absent or cannot be read.
     *
     * @param default Value returned when the key is absent or the default implementation catches an [IOException] read
     * failure. Defaults to null.
     */
    public suspend fun getNumber(key: String, default: Long? = null): Long?

    /**
     * Persists a number value under [key].
     *
     * @throws IOException on write failure.
     */
    @Throws(IOException::class)
    public suspend fun putNumber(key: String, value: Long)

    /**
     * Returns the stored Double, or [default] if the key is absent or cannot be read.
     *
     * @param default Value returned when the key is absent or the default implementation catches an [IOException] read
     * failure. Defaults to null.
     */
    public suspend fun getDouble(key: String, default: Double? = null): Double?

    /**
     * Persists a Double value under [key].
     *
     * @throws IOException on write failure.
     */
    @Throws(IOException::class)
    public suspend fun putDouble(key: String, value: Double)

    /**
     * Removes the value stored under [key].
     *
     * @throws IOException on write failure.
     */
    @Throws(IOException::class)
    public suspend fun remove(key: String)
}
