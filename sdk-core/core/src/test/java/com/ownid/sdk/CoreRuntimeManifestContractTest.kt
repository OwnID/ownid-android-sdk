package com.ownid.sdk

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.view.WindowManager
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.Properties

@RunWith(RobolectricTestRunner::class)
// Covers: MANIFEST-RUNTIME-010
internal class CoreRuntimeManifestContractTest {

    private val app: Application
        get() = RuntimeEnvironment.getApplication()

    @Test
    fun coreRequiredPermissionsAreVisibleThroughRuntimePackageInfo() {
        val packageInfo = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)

        assertWithMessage("Core manifest permissions should be visible after manifest merging")
            .that(packageInfo.requestedPermissions?.toList().orEmpty())
            .containsAtLeast(
                Manifest.permission.INTERNET,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.USE_BIOMETRIC
            )
    }

    @Test
    @Suppress("DEPRECATION")
    fun hiddenActivityDeclarationThemeAndWindowFlagsAreRuntimePackageVisible() {
        val hiddenActivity = app.packageManager
            .getPackageInfo(app.packageName, PackageManager.GET_ACTIVITIES)
            .activities
            .orEmpty()
            .single { it.name == HIDDEN_ACTIVITY_CLASS }

        assertThat(hiddenActivity.enabled).isTrue()
        assertThat(hiddenActivity.exported).isFalse()
        assertThat(hiddenActivity.theme).isEqualTo(R.style.OwnIdTheme_Hidden)
        assertThat(hiddenActivity.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST)
            .isEqualTo(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val hiddenTheme = app.resources.newTheme().apply { applyStyle(hiddenActivity.theme, true) }
        hiddenTheme.obtainStyledAttributes(
            intArrayOf(
                android.R.attr.windowNoTitle,
                android.R.attr.windowIsTranslucent,
                android.R.attr.windowBackground,
                android.R.attr.statusBarColor
            )
        ).use { attrs ->
            assertThat(attrs.getBoolean(0, false)).isTrue()
            assertThat(attrs.getBoolean(1, false)).isTrue()
            assertThat(attrs.getResourceId(2, 0)).isEqualTo(android.R.color.transparent)
            assertThat(attrs.getColor(3, -1)).isEqualTo(0)
        }
    }

    @Test
    fun generatedCoreModuleMetadataIsVisibleThroughRuntimeAssets() {
        assertThat(app.assets.list("com/ownid/sdk").orEmpty().toList()).contains("core.properties")

        val moduleProperties = Properties().apply {
            app.assets.open("com/ownid/sdk/core.properties").use { load(it) }
        }

        assertThat(moduleProperties.getProperty("name")).isEqualTo("OwnIDCore")
        assertThat(moduleProperties.getProperty("version")).matches("""\d+\.\d+\.\d+(?:-SNAPSHOT)?""")
    }

    private fun android.content.res.TypedArray.use(block: (android.content.res.TypedArray) -> Unit) {
        try {
            block(this)
        } finally {
            recycle()
        }
    }

    private companion object {
        private const val HIDDEN_ACTIVITY_CLASS = "com.ownid.sdk.ui.OwnIdHiddenActivity"
    }
}
