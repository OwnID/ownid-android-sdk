package com.ownid.sdk

import android.content.Context
import androidx.annotation.MainThread
import com.gigya.android.sdk.Gigya
import com.gigya.android.sdk.account.models.GigyaAccount
import com.ownid.sdk.internal.OwnIdGigyaImpl
import com.ownid.sdk.internal.OwnIdInternalLogger

/**
 *  Factory to get OwnID Gigya instance in Java.
 */
public object OwnIdGigyaFactory {

    /**
     * Returns default instance of OwnID Gigya.
     *
     * @throws IllegalStateException  If no default OwnID Gigya instance found.
     * @throws ClassCastException     If instance of OwnID with name [OwnIdGigya.DEFAULT_INSTANCE_NAME] is not of type [OwnIdGigya].
     *
     * @return [OwnIdGigya] instance
     */
    @JvmStatic
    @Suppress("unused")
    @Throws(IllegalStateException::class)
    public fun getDefault(): OwnIdGigya = getInstance(OwnIdGigya.DEFAULT_INSTANCE_NAME)

    /**
     * Returns instance of OwnID Gigya with [instanceName].
     *
     * @param instanceName [InstanceName] of OwnID Gigya instance.
     *
     * @throws IllegalStateException  If no OwnID Gigya instance with [instanceName] found.
     * @throws ClassCastException     If instance of OwnID with [instanceName] is not of type [OwnIdGigya].
     *
     * @return [OwnIdGigya] instance
     */
    @JvmStatic
    @Suppress("unused")
    @Throws(IllegalStateException::class)
    public fun getInstance(instanceName: InstanceName): OwnIdGigya = OwnId.getInstanceOrThrow(instanceName)

    /**
     * Creates an instance of OwnID Gigya.
     *
     * If instance for [instanceName] already exist it will be returned without creation a new one.
     *
     * Must be called on Android Main thread.
     *
     * @param context                       Android [Context].
     * @param configurationAssetFileName    (optional) Asset file name with [Configuration] in JSON format (default: [OwnIdGigya.DEFAULT_CONFIGURATION_FILE_NAME]).
     * @param gigya                         (optional) Instance of [Gigya] (default: will be used Gigya instance returned by `Gigya.getInstance()`. Important: Gigya account type must be already set via `Gigya.getInstance()`).
     * @param instanceName                  (optional) Custom [InstanceName] (default [OwnIdGigya.DEFAULT_INSTANCE_NAME]).
     *
     * @throws IllegalArgumentException     On JSON parsing error or required parameters are empty, blank or contain wrong data.
     * @throws IllegalStateException        If called on non-Main thread.
     * @throws ClassCastException           If instance of OwnID with [instanceName] is not of type [OwnIdGigya].
     *
     * @return [OwnIdGigya] instance.
     */
    @JvmStatic
    @MainThread
    @JvmOverloads
    @OptIn(InternalOwnIdAPI::class)
    @Throws(IllegalArgumentException::class, IllegalStateException::class, ClassCastException::class)
    public fun createInstanceFromFile(
        context: Context,
        configurationAssetFileName: String = OwnIdGigya.DEFAULT_CONFIGURATION_FILE_NAME,
        gigya: Gigya<out GigyaAccount> = Gigya.getInstance(),
        instanceName: InstanceName = OwnIdGigya.DEFAULT_INSTANCE_NAME
    ): OwnIdGigya = OwnId.createInstanceFromFile(context, configurationAssetFileName, OwnIdGigya.PRODUCT_NAME, instanceName) { ownIdCore ->
        OwnIdGigyaImpl(ownIdCore, gigya).also {
            OwnIdInternalLogger.logI(this, "createInstance", "Instance created ($configurationAssetFileName) || ${ownIdCore.configuration.userAgent}")
        }
    } as OwnIdGigya

    /**
     * Creates an instance of OwnID Gigya.
     *
     * If instance for [instanceName] already exist it will be returned without creation a new one.
     *
     * Must be called on Android Main thread.
     *
     * @param context                       Android [Context].
     * @param configurationJson             String with [Configuration] in JSON format.
     * @param gigya                         (optional) Instance of [Gigya] (default: will be used Gigya instance returned by `Gigya.getInstance()`. Important: Gigya account type must be already set via `Gigya.getInstance()`).
     * @param instanceName                  (optional) Custom [InstanceName] (default [OwnIdGigya.DEFAULT_INSTANCE_NAME]).
     *
     * @throws IllegalArgumentException     On JSON parsing error or required parameters are empty, blank or contain wrong data.
     * @throws IllegalStateException        If called on non-Main thread.
     * @throws ClassCastException           If instance of OwnID with [instanceName] is not of type [OwnIdGigya].
     *
     * @return [OwnIdGigya] instance.
     */
    @JvmStatic
    @MainThread
    @JvmOverloads
    @OptIn(InternalOwnIdAPI::class)
    @Throws(IllegalArgumentException::class, IllegalStateException::class, ClassCastException::class)
    public fun createInstanceFromJson(
        context: Context,
        configurationJson: String,
        gigya: Gigya<out GigyaAccount> = Gigya.getInstance(),
        instanceName: InstanceName = OwnIdGigya.DEFAULT_INSTANCE_NAME
    ): OwnIdGigya = OwnId.createInstanceFromJson(context, configurationJson, OwnIdGigya.PRODUCT_NAME, instanceName) { ownIdCore ->
        OwnIdGigyaImpl(ownIdCore, gigya).also {
            OwnIdInternalLogger.logI(this, "createInstanceFromJson", "Instance created || ${ownIdCore.configuration.userAgent}")
        }
    } as OwnIdGigya
}