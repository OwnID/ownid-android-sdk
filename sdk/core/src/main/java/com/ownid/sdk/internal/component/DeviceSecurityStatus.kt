package com.ownid.sdk.internal.component

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import org.json.JSONObject

internal class DeviceSecurityStatus private constructor(
    internal val isDeviceSecured: Boolean,
    internal val isFingerprintHardwarePresent: Boolean,
    internal val isFaceHardwarePresent: Boolean,
    internal val isIrisHardwarePresent: Boolean,
    internal val isStrongBiometricEnabled: Boolean
) {

    companion object {
        internal fun create(context: Context): DeviceSecurityStatus? = runCatching {
            DeviceSecurityStatus(
                isDeviceSecured = isDeviceSecured(context),
                isFingerprintHardwarePresent = isFingerprintHardwarePresent(context),
                isFaceHardwarePresent = isFaceHardwarePresent(context),
                isIrisHardwarePresent = isIrisHardwarePresent(context),
                isStrongBiometricEnabled = isStrongBiometricEnabled(context)
            )
        }.getOrNull()

        /**
         * Checks if the current device is secured with a lock screen credential (i.e. PIN, pattern, or password).
         */
        @RequiresPermission(Manifest.permission.USE_BIOMETRIC)
        internal fun isDeviceSecured(context: Context): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                canAuthenticate(context, BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            } else {
                context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure ?: false
            }

        /**
         * Checks if the current device has a hardware sensor that may be used for fingerprint authentication.
         */
        internal fun isFingerprintHardwarePresent(context: Context): Boolean =
            context.packageManager?.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT) ?: false

        /**
         * Checks if the current device has a hardware sensor that may be used for face authentication.
         */
        internal fun isFaceHardwarePresent(context: Context): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                context.packageManager?.hasSystemFeature(PackageManager.FEATURE_FACE) ?: false

        /**
         * Checks if the current device has a hardware sensor that may be used for iris authentication.
         */
        internal fun isIrisHardwarePresent(context: Context): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                context.packageManager?.hasSystemFeature(PackageManager.FEATURE_IRIS) ?: false

        /**
         * Checks if the current device has a string biometric enabled (hardware available, templates enrolled, user-enabled).
         */
        @RequiresPermission(Manifest.permission.USE_BIOMETRIC)
        internal fun isStrongBiometricEnabled(context: Context): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                canAuthenticate(context, BiometricManager.Authenticators.BIOMETRIC_STRONG)
            } else {
                false
            }

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


    internal fun asJson(): JSONObject = JSONObject().apply {
        put("isDeviceSecured", isDeviceSecured)
        put("isFingerprintHardwarePresent", isFingerprintHardwarePresent)
        put("isFaceHardwarePresent", isFaceHardwarePresent)
        put("isIrisHardwarePresent", isIrisHardwarePresent)
        put("isStrongBiometricEnabled", isStrongBiometricEnabled)
    }
}