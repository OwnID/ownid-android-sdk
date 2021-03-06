package com.ownid.sdk

/**
 * Single access point to all OwnID SDKs from Kotlin.
 * Acts as a target for extension methods provided by OwnID SDKs.
 *
 * It holds all created instances of OwnID SDKs.
 * Most applications don't need to directly interact with its methods.
 */
public object OwnId {

    @JvmStatic
    @InternalOwnIdAPI
    private val INSTANCES: MutableMap<InstanceName, OwnIdInstance> = mutableMapOf()

    @JvmStatic
    @Synchronized
    @OptIn(InternalOwnIdAPI::class)
    public fun putInstance(ownId: OwnIdInstance) {
        INSTANCES[ownId.instanceName] = ownId
    }

    @JvmStatic
    @Synchronized
    @OptIn(InternalOwnIdAPI::class)
    @Suppress("UNCHECKED_CAST")
    public fun <T : OwnIdInstance> getInstanceOrThrow(instanceName: InstanceName): T = INSTANCES[instanceName] as T

    @JvmStatic
    @Synchronized
    @OptIn(InternalOwnIdAPI::class)
    @Suppress("UNCHECKED_CAST")
    public fun <T : OwnIdInstance> getInstanceOrNull(instanceName: InstanceName): T? = INSTANCES[instanceName] as? T
}