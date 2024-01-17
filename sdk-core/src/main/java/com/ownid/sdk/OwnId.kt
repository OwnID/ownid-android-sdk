package com.ownid.sdk

import android.content.Context
import androidx.annotation.GuardedBy
import androidx.annotation.MainThread
import org.json.JSONException

/**
 * Single access point to all OwnID SDK instances.
 * Acts as a target for extension methods provided by OwnID SDKs.
 *
 * It holds all created instances of OwnID SDKs.
 * Most applications don't need to directly interact with its methods.
 */
public object OwnId {

    @InternalOwnIdAPI
    public val instanceLock: Any = Any()

    @JvmStatic
    @GuardedBy("instanceLock")
    private val INSTANCES: MutableMap<InstanceName, OwnIdInstance> = HashMap()

    @JvmStatic
    @OptIn(InternalOwnIdAPI::class)
    public fun putInstance(ownIdInstance: OwnIdInstance): Unit =
        synchronized(instanceLock) { INSTANCES[ownIdInstance.ownIdCore.instanceName] = ownIdInstance }

    @JvmStatic
    @OptIn(InternalOwnIdAPI::class)
    @Throws(ClassCastException::class)
    @Suppress("UNCHECKED_CAST")
    public fun <T : OwnIdInstance> getInstanceOrThrow(instanceName: InstanceName): T =
        synchronized(instanceLock) { INSTANCES[instanceName] as T }

    @JvmStatic
    @OptIn(InternalOwnIdAPI::class)
    @Suppress("UNCHECKED_CAST")
    public fun <T : OwnIdInstance> getInstanceOrNull(instanceName: InstanceName): T? =
        synchronized(instanceLock) { INSTANCES[instanceName] as? T }

    @JvmStatic
    @OptIn(InternalOwnIdAPI::class)
    @Suppress("UNCHECKED_CAST")
    public fun <T : OwnIdInstance> getInstanceOrNull(): T? =
        synchronized(instanceLock) { INSTANCES.firstNotNullOfOrNull { it.value } as? T }

    /**
     * Creates an instance of OwnID.
     *
     * If instance for [instanceName] already exist it will be returned without creation a new one.
     *
     * Must be called on Android Main thread.
     *
     * @param context                       Android [Context].
     * @param configurationAssetFileName    Asset file name with [Configuration] in JSON format.
     * @param productName                   An SDK [ProductName].
     * @param instanceName                  An [InstanceName] of OwnID.
     * @param createInstance                A function that creates an instance of OwnID using [OwnIdCore].
     *
     * @throws IllegalArgumentException     On JSON parsing error or required parameters are empty, blank or contain wrong data.
     * @throws IllegalStateException        If called on non-Main thread.
     *
     * @return [OwnIdInstance] instance.
     */
    @JvmStatic
    @MainThread
    @OptIn(InternalOwnIdAPI::class)
    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    public fun createInstanceFromFile(
        context: Context,
        configurationAssetFileName: String,
        productName: ProductName,
        instanceName: InstanceName,
        createInstance: (OwnIdCore) -> OwnIdInstance
    ): OwnIdInstance = synchronized(instanceLock) {

        getInstanceOrNull<OwnIdInstance>(instanceName)?.let { return@synchronized it }

        val configuration = try {
            Configuration.createFromAssetFile(context, configurationAssetFileName, productName)
        } catch (cause: JSONException) {
            throw IllegalArgumentException("Json parsing error in: $configurationAssetFileName", cause)
        }

        val ownIdCore = OwnIdCoreImpl.createInstance(context, instanceName, configuration)

        createInstance.invoke(ownIdCore).also { putInstance(it) }
    }

    /**
     * Creates an instance of OwnID.
     *
     * If instance for [instanceName] already exist it will be returned without creation a new one.
     *
     * Must be called on Android Main thread.
     *
     * @param context                       Android [Context].
     * @param configurationJson             String with [Configuration] in JSON format.
     * @param productName                   An SDK [ProductName].
     * @param instanceName                  An [InstanceName] of OwnID.
     * @param createInstance                A function that creates an instance of OwnID using [OwnIdCore].
     *
     * @throws IllegalArgumentException     On JSON parsing error or required parameters are empty, blank or contain wrong data.
     * @throws IllegalStateException        If called on non-Main thread.
     *
     * @return [OwnIdInstance] instance.
     */
    @JvmStatic
    @OptIn(InternalOwnIdAPI::class)
    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    public fun createInstanceFromJson(
        context: Context,
        configurationJson: String,
        productName: ProductName,
        instanceName: InstanceName,
        createInstance: (OwnIdCore) -> OwnIdInstance
    ): OwnIdInstance = synchronized(instanceLock) {

        getInstanceOrNull<OwnIdInstance>(instanceName)?.let { return@synchronized it }

        val configuration = try {
            Configuration.createFromJson(context, configurationJson, productName)
        } catch (cause: JSONException) {
            throw IllegalArgumentException("Json parsing error", cause)
        }

        val ownIdCore = OwnIdCoreImpl.createInstance(context, instanceName, configuration)

        createInstance.invoke(ownIdCore).also { putInstance(it) }
    }
}