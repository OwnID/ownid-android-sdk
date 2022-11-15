package com.ownid.sdk

import androidx.annotation.GuardedBy

/**
 * Single access point to all OwnID SDKs from Kotlin.
 * Acts as a target for extension methods provided by OwnID SDKs.
 *
 * It holds all created instances of OwnID SDKs.
 * Most applications don't need to directly interact with its methods.
 */
public object OwnId {

    @InternalOwnIdAPI
    public val ownIdLock: Any = Any()

    @JvmStatic
    @GuardedBy("ownIdLock")
    private val INSTANCES: MutableMap<InstanceName, OwnIdInstance> = mutableMapOf()

    @JvmStatic
    public fun putInstance(ownId: OwnIdInstance): Unit =
        synchronized(ownIdLock) { INSTANCES[ownId.instanceName] = ownId }

    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    public fun <T : OwnIdInstance> getInstanceOrThrow(instanceName: InstanceName): T =
        synchronized(ownIdLock) { INSTANCES[instanceName] as T }

    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    public fun <T : OwnIdInstance> getInstanceOrNull(instanceName: InstanceName): T? =
        synchronized(ownIdLock) { INSTANCES[instanceName] as? T }
}