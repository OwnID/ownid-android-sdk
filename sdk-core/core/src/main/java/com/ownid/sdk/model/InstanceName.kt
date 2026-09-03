package com.ownid.sdk.model

import com.ownid.sdk.model.InstanceName.Companion.Default

/**
 * Identifies an OwnID SDK instance by name.
 *
 * Use named instances when one app process must keep separate OwnID configurations. Different names cannot use the same
 * app ID, environment, and region at the same time. The [Default] instance is used when no explicit name is provided.
 *
 * Names are compared by exact [value] and [toString] returns the raw value. The SDK does not normalize, trim, or reject
 * blank values, so use a stable non-empty name for app-created instances.
 */
public class InstanceName(public val value: String) {
    public companion object {
        public val Default: InstanceName = InstanceName("DEFAULT")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is InstanceName) return false
        return value == other.value
    }

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value
}
