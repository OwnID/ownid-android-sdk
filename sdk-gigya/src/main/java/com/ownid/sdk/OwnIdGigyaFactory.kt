package com.ownid.sdk

import android.content.Context
import com.gigya.android.sdk.Gigya
import com.gigya.android.sdk.account.models.GigyaAccount
import com.ownid.sdk.internal.LocaleService
import com.ownid.sdk.internal.OwnIdGigyaImpl
import org.json.JSONException

/**
 *  Factory to get OwnID Gigya instance in Java.
 */
public object OwnIdGigyaFactory {

    /**
     * Returns default instance of OwnID Gigya.
     *
     * @throws IllegalStateException  if no default OwnID Gigya instance found.
     * @throws ClassCastException     if instance of OwnID with name [OwnIdGigya.DEFAULT_INSTANCE_NAME] is not of type [OwnIdGigya].
     *
     * @return [OwnIdGigya] instance
     */
    @JvmStatic
    @Suppress("unused")
    @Throws(IllegalStateException::class)
    public fun getDefault(): OwnIdGigya = getInstance(OwnIdGigya.DEFAULT_INSTANCE_NAME)

    /**
     * Returns instance of OwnID OwnIdGigya with [instanceName].
     *
     * @throws IllegalStateException  if no OwnID Gigya instance with [instanceName] found.
     * @throws ClassCastException     if instance of OwnID with [instanceName] is not of type [OwnIdGigya].
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
     * @param context                       Android [Context]
     * @param configurationAssetFileName    (optional) Asset file name with [Configuration] in JSON format (default: "ownIdGigyaSdkConfig.json").
     * @param gigya                         (optional) Instance of [Gigya] (default: will be used Gigya instance returned by `Gigya.getInstance()`. Important: Gigya account type must be already set via `Gigya.getInstance()`)
     * @param instanceName                  (optional) Custom [InstanceName] (default [OwnIdGigya.DEFAULT_INSTANCE_NAME]).
     *
     * @throws JSONException                on [Configuration] Json parsing error.
     * @throws IllegalArgumentException     if [Configuration] required parameters are empty, blank or contains wrong data.
     *
     * @return [OwnIdGigya] instance
     */
    @JvmStatic
    @JvmOverloads
    @androidx.annotation.OptIn(InternalOwnIdAPI::class)
    @Throws(IllegalArgumentException::class)
    public fun createInstance(
        context: Context,
        configurationAssetFileName: String = OwnIdGigya.DEFAULT_CONFIGURATION_FILE_NAME,
        gigya: Gigya<out GigyaAccount> = Gigya.getInstance(),
        instanceName: InstanceName = OwnIdGigya.DEFAULT_INSTANCE_NAME
    ): OwnIdGigya = synchronized(OwnId.ownIdLock) {
        OwnId.getInstanceOrNull<OwnIdGigya>(instanceName)?.let { return it }

        val configuration = try {
            Configuration.createFromAssetFile(context, configurationAssetFileName, OwnIdGigya.PRODUCT_NAME)
        } catch (cause: JSONException) {
            throw IllegalArgumentException("Json parsing error in: $configurationAssetFileName", cause)
        }

        val ownIdGigya = OwnIdGigyaImpl(instanceName, configuration, gigya)

        OwnId.putInstance(ownIdGigya)
        logD("Instance created from: $configurationAssetFileName || ${configuration.userAgent}", ownIdGigya)

        LocaleService.createInstance(context, ownIdGigya)

        ownIdGigya
    }

    /**
     * Creates an instance of OwnID Gigya.
     *
     * If instance for [instanceName] already exist it will be returned without creation a new one.
     *
     * @param context                       Android [Context]
     * @param configurationJson             String with [Configuration] in JSON format
     * @param gigya                         (optional) Instance of [Gigya] (default: will be used Gigya instance returned by `Gigya.getInstance()`. Important: Gigya account type must be already set via `Gigya.getInstance()`)
     * @param instanceName                  (optional) Custom [InstanceName] (default [OwnIdGigya.DEFAULT_INSTANCE_NAME]).
     *
     * @throws JSONException                on [Configuration] Json parsing error.
     * @throws IllegalArgumentException     if [Configuration] required parameters are empty, blank or contains wrong data.
     *
     * @return [OwnIdGigya] instance
     */
    @JvmStatic
    @JvmOverloads
    @androidx.annotation.OptIn(InternalOwnIdAPI::class)
    @Throws(IllegalArgumentException::class)
    public fun createInstanceFromJson(
        context: Context,
        configurationJson: String,
        gigya: Gigya<out GigyaAccount> = Gigya.getInstance(),
        instanceName: InstanceName = OwnIdGigya.DEFAULT_INSTANCE_NAME
    ): OwnIdGigya = synchronized(OwnId.ownIdLock) {
        OwnId.getInstanceOrNull<OwnIdGigya>(instanceName)?.let { return it }

        val configuration = try {
            Configuration.createFromJson(context, configurationJson, OwnIdGigya.PRODUCT_NAME)
        } catch (cause: JSONException) {
            throw IllegalArgumentException("Json parsing error", cause)
        }

        val ownIdGigya = OwnIdGigyaImpl(instanceName, configuration, gigya)

        OwnId.putInstance(ownIdGigya)
        logD("Instance created from JSON || ${configuration.userAgent}", ownIdGigya)

        LocaleService.createInstance(context, ownIdGigya)

        ownIdGigya
    }
}