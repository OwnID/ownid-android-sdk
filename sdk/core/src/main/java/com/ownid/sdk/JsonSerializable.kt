package com.ownid.sdk

/**
 * An interface for objects that can be serialized to JSON.
 *
 * Implementations should provide the actual serialization logic within the `toJson()` function.
 */
public interface JsonSerializable {

    public fun toJson(): String
}