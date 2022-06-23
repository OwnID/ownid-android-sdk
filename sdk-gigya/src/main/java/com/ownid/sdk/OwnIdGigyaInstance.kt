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
 * @param gigya                         Instance of [Gigya]
 * @param configurationAssetFileName    (optional) Asset file name with [Configuration] in JSON format (default: "ownIdGigyaSdkConfig.json").
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
public fun <A : GigyaAccount> OwnId.createGigyaInstance(
    context: Context,
    gigya: Gigya<A>,
    configurationAssetFileName: String = OwnIdGigya.DEFAULT_CONFIGURATION_FILE,
    instanceName: InstanceName = OwnIdGigya.DEFAULT_INSTANCE_NAME
): OwnIdGigya =
    OwnIdGigyaFactory.createInstance(context, gigya, configurationAssetFileName, instanceName)

/**
 * Creates an instance of OwnID Gigya.
 *
 * If instance for [instanceName] already exist it will be returned without creation a new one.
 *
 * @param context                       Android [Context]
 * @param gigya                         Instance of [Gigya]
 * @param configurationJson             String with [Configuration] in JSON format
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
public fun <A : GigyaAccount> OwnId.createGigyaInstanceFromJson(
    context: Context,
    gigya: Gigya<A>,
    configurationJson: String,
    instanceName: InstanceName = OwnIdGigya.DEFAULT_INSTANCE_NAME
): OwnIdGigya =
    OwnIdGigyaFactory.createInstanceFromJson(context, gigya, configurationJson, instanceName)