package com.ownid.sdk.capability.impl

import android.app.Application
import android.app.KeyguardManager
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricManager
import android.os.Build
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow.extract
import org.robolectric.shadows.ShadowBiometricManager
import org.robolectric.shadows.ShadowBuild

@RunWith(RobolectricTestRunner::class)
// Covers: INFO-RUNTIME-010, INFO-RUNTIME-020
internal class LocalInfoImplRuntimeContractTest {

    private val app: Application
        get() = RuntimeEnvironment.getApplication()

    @Test
    fun reportsRuntimePackageModuleDebugUserAgentAndCorrelationMetadata() {
        setRuntimeAppVersion()
        app.applicationInfo.flags = app.applicationInfo.flags or ApplicationInfo.FLAG_DEBUGGABLE
        ShadowBuild.setVersionRelease("14")
        ShadowBuild.setModel("Pixel (Fold)")

        val localInfo = LocalInfoImpl(app)

        val coreModule = localInfo.modules.single { it.first == "OwnIDCore" }
        assertThat(coreModule.second).matches("""\d+\.\d+\.\d+(?:-SNAPSHOT)?""")
        assertThat(localInfo.packageName).isEqualTo(app.packageName)
        assertThat(localInfo.appVersion).isEqualTo("7.8.9")
        assertThat(localInfo.isDebuggable).isTrue()
        assertThat(localInfo.userAgent).isEqualTo(
            "OwnIDCore/${coreModule.second} (Linux; Android 14; Pixel Fold) ${app.packageName}"
        )
        assertThat(localInfo.userAgent).doesNotContain("Pixel (Fold)")
        assertThat(localInfo.correlationId).matches("""[A-Za-z0-9_-]{22}""")
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.O_MR1])
    fun systemFidoGateReportsUnavailableBelowAndroidP() {
        val localInfo = LocalInfoImpl(app)

        assertThat(localInfo.isSystemFidoCapable).isFalse()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun systemFidoGateReportsAvailableFromAndroidP() {
        val localInfo = LocalInfoImpl(app)

        assertThat(localInfo.isSystemFidoCapable).isTrue()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    fun packageFeatureDiagnosticsFollowRobolectricPackageManagerState() {
        val packageManager = shadowOf(app.packageManager)
        packageManager.setSystemFeature(PackageManager.FEATURE_FINGERPRINT, true)
        packageManager.setSystemFeature(PackageManager.FEATURE_FACE, true)
        packageManager.setSystemFeature(PackageManager.FEATURE_IRIS, false)

        val localInfo = LocalInfoImpl(app)

        assertThat(localInfo.isFingerprintHardwarePresent).isTrue()
        assertThat(localInfo.isFaceHardwarePresent).isTrue()
        assertThat(localInfo.isIrisHardwarePresent).isFalse()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun faceAndIrisDiagnosticsStayDisabledBeforeAndroidQEvenWhenFeaturesExist() {
        val packageManager = shadowOf(app.packageManager)
        packageManager.setSystemFeature(PackageManager.FEATURE_FACE, true)
        packageManager.setSystemFeature(PackageManager.FEATURE_IRIS, true)

        val localInfo = LocalInfoImpl(app)

        assertThat(localInfo.isFaceHardwarePresent).isFalse()
        assertThat(localInfo.isIrisHardwarePresent).isFalse()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    @Suppress("UsePropertyAccessSyntax")
    fun deviceSecureDiagnosticUsesKeyguardBeforeAndroidR() {
        val keyguardManager = app.getSystemService(KeyguardManager::class.java)
        shadowOf(keyguardManager).setIsDeviceSecure(false)
        val unsecuredInfo = LocalInfoImpl(app)

        shadowOf(keyguardManager).setIsDeviceSecure(true)
        val securedInfo = LocalInfoImpl(app)

        assertThat(unsecuredInfo.isDeviceSecured).isFalse()
        assertThat(securedInfo.isDeviceSecured).isTrue()
        assertThat(unsecuredInfo.isStrongBiometricEnabled).isFalse()
        assertThat(securedInfo.isStrongBiometricEnabled).isFalse()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.R])
    fun biometricDiagnosticsUseCurrentBiometricManagerSnapshotFromAndroidR() {
        val biometricManager = app.getSystemService(BiometricManager::class.java)
        extract<ShadowBiometricManager>(biometricManager).setCanAuthenticate(false)
        val unavailableInfo = LocalInfoImpl(app)

        extract<ShadowBiometricManager>(biometricManager).setCanAuthenticate(true)
        val availableInfo = LocalInfoImpl(app)

        assertThat(unavailableInfo.isDeviceSecured).isFalse()
        assertThat(unavailableInfo.isStrongBiometricEnabled).isFalse()
        assertThat(availableInfo.isDeviceSecured).isTrue()
        assertThat(availableInfo.isStrongBiometricEnabled).isTrue()
    }

    private fun setRuntimeAppVersion() {
        shadowOf(app.packageManager)
            .getInternalMutablePackageInfo(app.packageName)
            .versionName = "7.8.9"
    }
}
