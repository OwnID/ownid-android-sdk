@file:JvmName("OwnIdGigyaInstance")

package com.ownid.sdk

import android.content.Context
import androidx.annotation.MainThread
import com.gigya.android.sdk.Gigya
import com.gigya.android.sdk.account.models.GigyaAccount
import com.gigya.android.sdk.ui.plugin.IGigyaWebBridge

/**
 * Returns default instance of OwnID Gigya.
 *
 * @throws IllegalStateException  If no default OwnID Gigya instance found.
 * @throws ClassCastException     If instance of OwnID with name [OwnIdGigya.DEFAULT_INSTANCE_NAME] is not of type [OwnIdGigya].
 *
 * @return [OwnIdGigya] instance.
 */
@Suppress("unused")
public val OwnId.gigya: OwnIdGigya
    @Throws(IllegalStateException::class)
    get() = OwnIdGigyaFactory.getDefault()

/**
 * Returns instance of OwnID Gigya with [instanceName].
 *
 * @param instanceName [InstanceName] of OwnID Gigya instance.
 *
 * @throws IllegalStateException  If no OwnID Gigya instance with [instanceName] found.
 * @throws ClassCastException     If instance of OwnID with [instanceName] is not of type [OwnIdGigya].
 *
 * @return [OwnIdGigya] instance.
 */
@Suppress("unused")
@Throws(IllegalStateException::class)
public fun OwnId.gigya(instanceName: InstanceName): OwnIdGigya = OwnIdGigyaFactory.getInstance(instanceName)

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
@MainThread
@JvmOverloads
@Suppress("unused")
@Throws(IllegalArgumentException::class, IllegalStateException::class, ClassCastException::class)
public fun OwnId.createGigyaInstanceFromFile(
    context: Context,
    configurationAssetFileName: String = OwnIdGigya.DEFAULT_CONFIGURATION_FILE_NAME,
    gigya: Gigya<out GigyaAccount> = Gigya.getInstance(),
    instanceName: InstanceName = OwnIdGigya.DEFAULT_INSTANCE_NAME
): OwnIdGigya =
    OwnIdGigyaFactory.createInstanceFromFile(context, configurationAssetFileName, gigya, instanceName)

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
@MainThread
@JvmOverloads
@Suppress("unused")
@Throws(IllegalArgumentException::class, IllegalStateException::class, ClassCastException::class)
public fun OwnId.createGigyaInstanceFromJson(
    context: Context,
    configurationJson: String,
    gigya: Gigya<out GigyaAccount> = Gigya.getInstance(),
    instanceName: InstanceName = OwnIdGigya.DEFAULT_INSTANCE_NAME
): OwnIdGigya =
    OwnIdGigyaFactory.createInstanceFromJson(context, configurationJson, gigya, instanceName)

/**
 * Configure Gigya SDK to use OwnIdGigyaWebBridge.
 */
@Suppress("unused")
public fun OwnId.configureGigyaWebBridge() {
    Gigya.getContainer().bind(IGigyaWebBridge::class.java, OwnIdGigyaWebBridge::class.java, false)
}