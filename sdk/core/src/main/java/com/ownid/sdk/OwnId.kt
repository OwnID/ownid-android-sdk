package com.ownid.sdk

import android.content.Context
import androidx.annotation.GuardedBy
import androidx.annotation.MainThread
import com.ownid.sdk.internal.component.OwnIdInternalLogger
import com.ownid.sdk.internal.feature.webbridge.OwnIdWebViewBridgeImpl
import org.json.JSONException

/**
 * Single access point to all OwnID SDK instances.
 * Acts as a target for extension methods provided by OwnID SDKs.
 *
 * It holds all created instances of OwnID SDKs.
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

    @Deprecated("Deprecated since 3.4.0", ReplaceWith("OwnId.instance"))
    @JvmStatic
    @JvmOverloads
    @OptIn(InternalOwnIdAPI::class)
    @Throws(IllegalStateException::class)
    @Suppress("UNCHECKED_CAST")
    public fun <T : OwnIdInstance> getInstanceOrThrow(instanceName: InstanceName = InstanceName.DEFAULT): T =
        checkNotNull(synchronized(instanceLock) { INSTANCES[instanceName] as? T }) { "No OwnID instances available [$instanceName]" }

    @Deprecated("Deprecated since 3.4.0", ReplaceWith("OwnId.instance"))
    @JvmStatic
    @JvmOverloads
    @OptIn(InternalOwnIdAPI::class)
    @Suppress("UNCHECKED_CAST")
    public fun <T : OwnIdInstance> getInstanceOrNull(instanceName: InstanceName = InstanceName.DEFAULT): T? =
        synchronized(instanceLock) { INSTANCES[instanceName] as? T }

    @Deprecated("Deprecated since 3.4.0", ReplaceWith("OwnId.instance"))
    @JvmStatic
    @OptIn(InternalOwnIdAPI::class)
    @Throws(IllegalStateException::class)
    @Suppress("UNCHECKED_CAST")
    public fun <T : OwnIdInstance> firstInstanceOrThrow(): T =
        checkNotNull(synchronized(instanceLock) { INSTANCES.values.firstOrNull() as? T }) { "No OwnID instances available" }

    @JvmStatic
    @OptIn(InternalOwnIdAPI::class)
    @Suppress("UNCHECKED_CAST")
    public fun <T : OwnIdInstance> firstInstanceOrNull(): T? =
        synchronized(instanceLock) { INSTANCES.values.firstOrNull() as? T }

    /**
     * Provides access to OwnID instance to interact with the OwnID.
     *
     * @throws IllegalStateException if no OwnID instance available.
     */
    @JvmStatic
    @OptIn(InternalOwnIdAPI::class)
    public val instance: OwnIdInstance
        @Throws(IllegalStateException::class)
        get() = checkNotNull(synchronized(instanceLock) { INSTANCES.values.firstOrNull() }) { "No OwnID instances available" }

    /**
     * Creates an instance of OwnID.
     *
     * If an instance for [instanceName] already exist, it will be returned without creation of a new one.
     *
     * Must be called on Android Main thread.
     *
     * @param context                       Android [Context].
     * @param configurationAssetFileName    Asset file name with [Configuration] in JSON format.
     * @param productName                   An SDK [ProductName].
     * @param instanceName                  An [InstanceName] of OwnID.
     * @param ownIdInstance                 A function that creates an instance of OwnID with optional [OwnIdIntegration] component.
     *
     * @throws IllegalArgumentException     On JSON parsing error or required parameters are empty, blank or contain wrong data.
     * @throws IllegalStateException        If called on non-Main thread.
     *
     * @return [OwnIdInstance] instance.
     */
    @JvmStatic
    @MainThread
    @InternalOwnIdAPI
    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    public fun createFromFile(
        context: Context,
        configurationAssetFileName: String,
        productName: ProductName,
        instanceName: InstanceName,
        ownIdInstance: (OwnIdCore) -> OwnIdInstance
    ): OwnIdInstance = synchronized(instanceLock) {

        getInstanceOrNull<OwnIdInstance>(instanceName)?.let { return@synchronized it }

        val configuration = try {
            Configuration.createFromAssetFile(context, configurationAssetFileName, productName)
        } catch (cause: JSONException) {
            throw IllegalArgumentException("Json parsing error in: $configurationAssetFileName", cause)
        }

        val ownIdCore = OwnIdCoreImpl.createInstance(context, instanceName, configuration)

        ownIdInstance.invoke(ownIdCore).also {
            OwnIdInternalLogger.logI(this, "createInstanceFromFile", "Instance created ($configurationAssetFileName) || ${ownIdCore.configuration.userAgent}")
            putInstance(it)
        }
    }

    /**
     * Creates an instance of OwnID.
     *
     * If an instance for [instanceName] already exist, it will be returned without creation of a new one.
     *
     * Must be called on Android Main thread.
     *
     * @param context                       Android [Context].
     * @param configurationJson             String with [Configuration] in JSON format.
     * @param productName                   An SDK [ProductName].
     * @param instanceName                  An [InstanceName] of OwnID.
     * @param ownIdInstance                 A function that creates an instance of OwnID with optional [OwnIdIntegration] component.
     *
     * @throws IllegalArgumentException     On JSON parsing error or required parameters are empty, blank or contain wrong data.
     * @throws IllegalStateException        If called on non-Main thread.
     *
     * @return [OwnIdInstance] instance.
     */
    @JvmStatic
    @MainThread
    @InternalOwnIdAPI
    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    public fun createFromJson(
        context: Context,
        configurationJson: String,
        productName: ProductName,
        instanceName: InstanceName,
        ownIdInstance: (OwnIdCore) -> OwnIdInstance
    ): OwnIdInstance = synchronized(instanceLock) {

        getInstanceOrNull<OwnIdInstance>(instanceName)?.let { return@synchronized it }

        val configuration = try {
            Configuration.createFromJson(context, configurationJson, productName)
        } catch (cause: JSONException) {
            throw IllegalArgumentException("Json parsing error", cause)
        }

        val ownIdCore = OwnIdCoreImpl.createInstance(context, instanceName, configuration)

        ownIdInstance.invoke(ownIdCore).also {
            OwnIdInternalLogger.logI(this, "createInstanceFromJson", "Instance created || ${ownIdCore.configuration.userAgent}")
            putInstance(it)
        }
    }

    /**
     * Creates an instance of OwnID with optional [OwnIdIntegration] component.
     *
     * If an instance for [instanceName] already exist, it will be returned without creation of a new one.
     *
     * Must be called on Android Main thread.
     *
     * @param context                       Android [Context].
     * @param configurationAssetFileName    Asset file name with [Configuration] in JSON format.
     * @param productName                   An SDK [ProductName].
     * @param instanceName                  An optional [InstanceName] of OwnID. Default: [InstanceName.DEFAULT].
     * @param ownIdIntegration              An optional function that creates an instance of [OwnIdIntegration] component.
     *
     * @throws IllegalArgumentException     On JSON parsing error or required parameters are empty, blank or contain wrong data.
     * @throws IllegalStateException        If called on non-Main thread.
     *
     * @return [OwnIdInstance] instance.
     */
    @JvmStatic
    @MainThread
    @JvmOverloads
    @OptIn(InternalOwnIdAPI::class)
    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    public fun createInstanceFromFile(
        context: Context,
        configurationAssetFileName: String,
        productName: ProductName,
        instanceName: InstanceName = InstanceName.DEFAULT,
        ownIdIntegration: ((OwnIdCore) -> OwnIdIntegration)? = null
    ): OwnIdInstance = createFromFile(context, configurationAssetFileName, productName, instanceName) { ownIdCore ->
        object : OwnIdInstance {
            override val ownIdCore: OwnIdCore = ownIdCore
            override val ownIdIntegration: OwnIdIntegration? = ownIdIntegration?.invoke(ownIdCore)
        }
    }

    /**
     * Creates an instance of OwnID with optional [OwnIdIntegration] component.
     *
     * If an instance for [instanceName] already exist, it will be returned without creation of a new one.
     *
     * Must be called on Android Main thread.
     *
     * @param context                       Android [Context].
     * @param configurationJson             String with [Configuration] in JSON format.
     * @param productName                   An SDK [ProductName].
     * @param instanceName                  An optional [InstanceName] of OwnID. Default: [InstanceName.DEFAULT].
     * @param ownIdIntegration              An optional function that creates an instance of [OwnIdIntegration] component.
     *
     * @throws IllegalArgumentException     On JSON parsing error or required parameters are empty, blank or contain wrong data.
     * @throws IllegalStateException        If called on non-Main thread.
     *
     * @return [OwnIdInstance] instance.
     */
    @JvmStatic
    @MainThread
    @JvmOverloads
    @OptIn(InternalOwnIdAPI::class)
    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    public fun createInstanceFromJson(
        context: Context,
        configurationJson: String,
        productName: ProductName,
        instanceName: InstanceName = InstanceName.DEFAULT,
        ownIdIntegration: ((OwnIdCore) -> OwnIdIntegration)? = null
    ): OwnIdInstance = createFromJson(context, configurationJson, productName, instanceName) { ownIdCore ->
        object : OwnIdInstance {
            override val ownIdCore: OwnIdCore = ownIdCore
            override val ownIdIntegration: OwnIdIntegration? = ownIdIntegration?.invoke(ownIdCore)
        }
    }

    /**
     * Creates a new instance of [OwnIdWebViewBridge]. Ensure an OwnID instance is created before calling this function.
     *
     * The bridge enables communication between the OwnID Web SDK running in a WebView and the OwnID Android SDK.
     *
     * You can customize the functionalities exposed to the Web SDK by specifying the namespaces to include or exclude.
     *
     * **Namespaces:**
     * * Namespaces are functional plugins that encapsulate sets of related features accessible by the Web SDK.
     * * Available namespaces are defined in [OwnIdWebViewBridge.Namespace].
     * * By default, all namespaces are included.
     *
     * @param includeNamespaces An optional list of namespaces to include in the bridge. If `null`, all namespaces are included.
     * @param excludeNamespaces An optional list of namespaces to exclude from the bridge. Excluded namespaces take precedence over included ones.
     *
     * @return A new instance of [OwnIdWebViewBridge] configured with the specified namespaces.
     */
    @JvmStatic
    @JvmOverloads
    @OptIn(InternalOwnIdAPI::class)
    public fun createWebViewBridge(
        includeNamespaces: List<OwnIdWebViewBridge.Namespace>? = null,
        excludeNamespaces: List<OwnIdWebViewBridge.Namespace>? = null
    ): OwnIdWebViewBridge =
        OwnIdWebViewBridgeImpl(includeNamespaces, excludeNamespaces)
}