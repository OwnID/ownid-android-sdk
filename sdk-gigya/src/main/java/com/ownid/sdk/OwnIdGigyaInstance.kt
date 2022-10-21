@file:JvmName("OwnIdGigyaInstance")

package com.ownid.sdk

import android.content.Context
import com.gigya.android.sdk.Gigya
import com.gigya.android.sdk.account.models.GigyaAccount
import org.json.JSONException

/**
 * Returns default instance of OwnID Gigya.
 *
 * @throws IllegalStateException  if no default OwnID Gigya instance found.
 * @throws ClassCastException     if instance of OwnID with name [OwnIdGigya.DEFAULT_INSTANCE_NAME] is not of type [OwnIdGigya].
 *
 * @return [OwnIdGigya] instance
 */
@Suppress("unused")
public val OwnId.gigya: OwnIdGigya
    @Throws(IllegalStateException::class)
    get() = OwnIdGigyaFactory.getDefault()

/**
 * Returns instance of OwnID Gigya with [instanceName].
 *
 * @throws IllegalStateException  if no OwnID Gigya instance with [instanceName] found.
 * @throws ClassCastException     if instance of OwnID with [instanceName] is not of type [OwnIdGigya].
 *
 * @return [OwnIdGigya] instance
 */
@Suppress("unused")
@Throws(IllegalStateException::class)
public fun OwnId.gigya(instanceName: InstanceName): OwnIdGigya = OwnIdGigyaFactory.getInstance(instanceName)

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
@JvmOverloads
@Suppress("unused")
@Throws(JSONException::class, IllegalArgumentException::class)
public fun OwnId.createGigyaInstance(
    context: Context,
    configurationAssetFileName: String = OwnIdGigya.DEFAULT_CONFIGURATION_FILE_NAME,
    gigya: Gigya<out GigyaAccount> = Gigya.getInstance(),
    instanceName: InstanceName = OwnIdGigya.DEFAULT_INSTANCE_NAME
): OwnIdGigya =
    OwnIdGigyaFactory.createInstance(context, configurationAssetFileName, gigya, instanceName)

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
@JvmOverloads
@Suppress("unused")
@Throws(JSONException::class, IllegalArgumentException::class)
public fun OwnId.createGigyaInstanceFromJson(
    context: Context,
    configurationJson: String,
    gigya: Gigya<out GigyaAccount> = Gigya.getInstance(),
    instanceName: InstanceName = OwnIdGigya.DEFAULT_INSTANCE_NAME
): OwnIdGigya =
    OwnIdGigyaFactory.createInstanceFromJson(context, configurationJson, gigya, instanceName)