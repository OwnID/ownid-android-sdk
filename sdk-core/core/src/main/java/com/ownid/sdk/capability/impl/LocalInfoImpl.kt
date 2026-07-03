package com.ownid.sdk.capability.impl

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import com.ownid.sdk.capability.LocalInfo
import com.ownid.sdk.model.encodeToBase64UrlSafe
import com.ownid.sdk.model.runCatchingExceptions
import com.ownid.sdk.model.secureRandom
import java.util.Properties

internal class LocalInfoImpl(context: Context) : LocalInfo {

    override val modules: List<Pair<String, String>> = context.readModulesFromAssets()

    override val packageName: String = context.packageName

    override val appVersion: String = runCatchingExceptions { context.packageManager.getPackageInfo(packageName, 0).versionName ?: "" }
        .getOrDefault("")

    override val userAgent: String = createUserAgent(modules, packageName)

    override val correlationId: String = secureRandom().encodeToBase64UrlSafe()

    override val isDebuggable: Boolean = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    override val isSystemFidoCapable: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    override val isDeviceSecured: Boolean? = isDeviceSecured(context)

    override val isFingerprintHardwarePresent: Boolean? = isFingerprintHardwarePresent(context)

    override val isFaceHardwarePresent: Boolean? = isFaceHardwarePresent(context)

    override val isIrisHardwarePresent: Boolean? = isIrisHardwarePresent(context)

    override val isStrongBiometricEnabled: Boolean? = isStrongBiometricEnabled(context)

    internal fun Context.readModulesFromAssets(versionsPath: String = "com/ownid/sdk"): List<Pair<String, String>> = runCatchingExceptions {
        assets.list(versionsPath).orEmpty()
            .filter { it.endsWith(".properties") }
            .mapNotNull { fileName -> loadPropertiesFromFile("$versionsPath/$fileName") }
    }.getOrElse { emptyList() }

    internal fun Context.loadPropertiesFromFile(filePath: String): Pair<String, String>? = runCatchingExceptions {
        assets.open(filePath).use {
            Properties().apply { load(it) }.parseVersion()
        }
    }.getOrNull()

    internal fun Properties.parseVersion(): Pair<String, String>? {
        val name = getProperty("name") ?: return null
        val version = getProperty("version") ?: return null
        return name to version
    }

    internal fun createUserAgent(modules: List<Pair<String, String>>, packageName: String): String {
        val (coreModules, otherModules) = modules.partition { it.first.equals("OwnIDCore", true) }

        val core = coreModules.firstOrNull()?.let { "${it.first}/${it.second}" } ?: "OwnIDCore/0.0.0"
        val systemInfo = "(Linux; Android ${Build.VERSION.RELEASE}; ${Build.MODEL.filterNot { it == '(' || it == ')' }})"
        val other = otherModules.joinToString(separator = " ") { "${it.first}/${it.second}" }

        return listOf(core, systemInfo, other, packageName)
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }

    /**
     * Checks if the current device is secured with a lock screen credential (i.e. PIN, pattern, or password).
     */
    @RequiresPermission(Manifest.permission.USE_BIOMETRIC)
    private fun isDeviceSecured(context: Context): Boolean? = runCatchingExceptions {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            canAuthenticate(context, BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        } else {
            context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure ?: false
        }
    }.getOrNull()

    /**
     * Checks if the current device has a hardware sensor that may be used for fingerprint authentication.
     */
    private fun isFingerprintHardwarePresent(context: Context): Boolean? = runCatchingExceptions {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
    }.getOrNull()

    /**
     * Checks if the current device has a hardware sensor that may be used for face authentication.
     */
    private fun isFaceHardwarePresent(context: Context): Boolean? = runCatchingExceptions {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && context.packageManager.hasSystemFeature(PackageManager.FEATURE_FACE)
    }.getOrNull()

    /**
     * Checks if the current device has a hardware sensor that may be used for iris authentication.
     */
    private fun isIrisHardwarePresent(context: Context): Boolean? = runCatchingExceptions {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && context.packageManager.hasSystemFeature(PackageManager.FEATURE_IRIS)
    }.getOrNull()

    /**
     * Checks if the current device has a strong biometric enabled (hardware available, templates enrolled, user-enabled).
     */
    @RequiresPermission(Manifest.permission.USE_BIOMETRIC)
    private fun isStrongBiometricEnabled(context: Context): Boolean? = runCatchingExceptions {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            canAuthenticate(context, BiometricManager.Authenticators.BIOMETRIC_STRONG)
        } else {
            false
        }
    }.getOrNull()

    /**
     * Determine if any of the provided authenticators can be used. In other words, determine if BiometricPrompt can be expected
     * to be shown (hardware available, templates enrolled, user-enabled).
     * For biometric authenticators, determine if the device can currently authenticate with at least the requested strength.
     * Invoking this API with BiometricManager.Authenticators.DEVICE_CREDENTIAL can be used to determine if the user has a PIN/Pattern/Password set up.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    @RequiresPermission(Manifest.permission.USE_BIOMETRIC)
    private fun canAuthenticate(context: Context, authenticators: Int): Boolean =
        context.getSystemService(BiometricManager::class.java)?.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
}
