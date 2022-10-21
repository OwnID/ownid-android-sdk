package com.ownid.sdk.internal

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Browser
import androidx.annotation.Px
import androidx.annotation.RequiresApi
import androidx.appcompat.content.res.AppCompatResources
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.core.os.ConfigurationCompat
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.R
import com.ownid.sdk.logD

/**
 * Helper class for launching URI in native app, Custom Tab, or standalone web browser.
 */
@InternalOwnIdAPI
internal object BrowserHelper {

    /**
     * Open [uri] in native app if present in system and [skipNativeApp] is false, otherwise
     *
     * if default browser is set and supports Custom Tabs, open [uri] in Custom Tab, otherwise
     *
     * if default browser is set, open [uri] in it as a standalone browser, otherwise
     *
     * if multiple browsers are available and no default is set, show browser choose dialog
     * and open [uri] in selected as standalone browser.
     *
     * @param context           Android [Context] to get Intent.
     * @param uri               address to be open.
     * @param skipNativeApp     request to skip launch in native app if present.
     *
     * @throws ActivityNotFoundException if no browser or native app found in system.
     */
    @JvmStatic
    @JvmSynthetic
    @Throws(ActivityNotFoundException::class)
    internal fun launchUri(context: Context, uri: Uri, skipNativeApp: Boolean = false) {
        logD("launchUri: $uri, skippNativeApp: $skipNativeApp")

        if (skipNativeApp.not()) {
            val isOpenedInNativeApp = launchNativeUri(context, uri)
            if (isOpenedInNativeApp) return
        }
        val packageName = getDefaultCustomTabPackageName(context)
        if (packageName == null) {
            openInExternalBrowser(context, uri)
        } else {
            openInCustomTab(context, uri)
        }
    }

    /**
     * Open [uri] in standalone browser in new task.
     *
     * @param context   Android [Context]  to get color, icon and Intent.
     * @param uri       address to be open.
     *
     * @throws ActivityNotFoundException if no browser found in system.
     */
    @JvmStatic
    @Throws(ActivityNotFoundException::class)
    private fun openInExternalBrowser(context: Context, uri: Uri) {
        val languageTags = ConfigurationCompat.getLocales(context.resources.configuration).toLanguageTags()
        val intent = Intent(Intent.ACTION_VIEW, uri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            .putExtra(Intent.EXTRA_REFERRER, Uri.parse("android-app://${context.packageName}"))
            .putExtra(Browser.EXTRA_HEADERS, Bundle().apply { putString("Accept-Language", languageTags) })
        context.startActivity(intent)
    }

    /**
     * Open [uri] in Custom Tab.
     *
     * Custom Tab toolbar color will be set to OwnID default color #354A5F.
     * Tab close button will be set to back arrow (if supported by browser).
     *
     * If there is no default browser set in system, show browser choose dialog and open [uri] in
     * selected standalone browser.
     *
     * @param context   Android [Context] to get color, icon and Intent.
     * @param uri       address to be open in Custom Tab.
     *
     * @throws ActivityNotFoundException if no browser found in system.
     */
    @SuppressLint("UseCompatLoadingForDrawables")
    @JvmStatic
    @Throws(ActivityNotFoundException::class)
    private fun openInCustomTab(context: Context, uri: Uri) {
        val customTabColorSchemeParams = CustomTabColorSchemeParams.Builder()
            .setToolbarColor(ContextCompat.getColor(context, R.color.com_ownid_sdk_color_gray))
            .build()

        val customTabsIntent = CustomTabsIntent.Builder()
            .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
            .setColorScheme(CustomTabsIntent.COLOR_SCHEME_LIGHT)
            .setDefaultColorSchemeParams(customTabColorSchemeParams)
            .apply {
                AppCompatResources.getDrawable(context, R.drawable.com_ownid_sdk_ic_arrow_back)
                    ?.let { setCloseButtonIcon(it.toBitmap()) }
            }
            .build()

        customTabsIntent.intent.putExtra(Intent.EXTRA_REFERRER, Uri.parse("android-app://${context.packageName}"))
        val languageTags = ConfigurationCompat.getLocales(context.resources.configuration).toLanguageTags()
        customTabsIntent.intent.putExtra(Browser.EXTRA_HEADERS, Bundle().apply { putString("Accept-Language", languageTags) })
        customTabsIntent.launchUrl(context, uri)
    }

    // Copy from androidx.core:core-ktx
    private fun Drawable.toBitmap(@Px width: Int = intrinsicWidth, @Px height: Int = intrinsicHeight): Bitmap {
        val oldLeft = bounds.left
        val oldTop = bounds.top
        val oldRight = bounds.right
        val oldBottom = bounds.bottom

        val bitmap = Bitmap.createBitmap(width, height, Config.ARGB_8888)
        setBounds(0, 0, width, height)
        draw(Canvas(bitmap))

        setBounds(oldLeft, oldTop, oldRight, oldBottom)
        return bitmap
    }

    /**
     * Goes through all apps that handle https VIEW intent and have a warmup service.
     * Selects the default app set as the web browser in the system. If there is no default, returns null.
     *
     * @param context [Context] to use for accessing [PackageManager].
     *
     * @return The recommended package name when connecting to components related to custom tabs.
     * Null if there this no recommendation or no browser available.
     */
    @JvmStatic
    private fun getDefaultCustomTabPackageName(context: Context): String? {
        val pm = context.packageManager

        // Get all Apps that resolve a generic https url
        val activityIntent = Intent()
            .setAction(Intent.ACTION_VIEW)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setData(Uri.fromParts("https", "", null))

        // Get default VIEW intent handler
        val defaultViewHandlerInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.resolveActivity(activityIntent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.resolveActivity(activityIntent, 0)
        }

        var defaultViewHandlerPackageName: String? = null
        if (defaultViewHandlerInfo != null) {
            defaultViewHandlerPackageName = defaultViewHandlerInfo.activityInfo.packageName
        }

        // Get all apps that can handle VIEW intents and filter only with Custom Tabs support
        val resolvedActivityList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(activityIntent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(activityIntent, 0)
        }

        val packagesSupportingCustomTabs: MutableList<String?> = ArrayList()
        for (info in resolvedActivityList) {
            val serviceIntent = Intent()
                .setAction("android.support.customtabs.action.CustomTabsService")
                .setPackage(info.activityInfo.packageName)

            val resolvedService = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.resolveService(serviceIntent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.resolveService(serviceIntent, 0)
            }
            if (resolvedService != null) {
                packagesSupportingCustomTabs.add(info.activityInfo.packageName)
            }
        }

        // Select default VIEW intent handler if it supports Custom Tabs
        return if (defaultViewHandlerPackageName.isNullOrBlank().not()
            && packagesSupportingCustomTabs.contains(defaultViewHandlerPackageName)
        )
            defaultViewHandlerPackageName
        else
            null
    }

    @JvmStatic
    private fun launchNativeUri(context: Context, uri: Uri): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            launchNativeApi30(context, uri)
        else
            launchNativeBeforeApi30(context, uri)
    }

    @JvmStatic
    @RequiresApi(api = Build.VERSION_CODES.R)
    private fun launchNativeApi30(context: Context, uri: Uri): Boolean {
        val nativeAppIntent = Intent(Intent.ACTION_VIEW, uri)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER)
        return try {
            context.startActivity(nativeAppIntent)
            true
        } catch (ex: ActivityNotFoundException) {
            false
        }
    }

    @JvmStatic
    private fun launchNativeBeforeApi30(context: Context, uri: Uri): Boolean {
        val pm = context.packageManager

        // Get all Apps that resolve a generic https url
        val browserActivityIntent = Intent()
            .setAction(Intent.ACTION_VIEW)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setData(Uri.fromParts("https", "", null))
        val genericResolvedList: Set<String> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                extractPackageNames(pm.queryIntentActivities(browserActivityIntent, PackageManager.ResolveInfoFlags.of(0)))
            } else {
                @Suppress("DEPRECATION")
                extractPackageNames(pm.queryIntentActivities(browserActivityIntent, 0))
            }

        // Get all apps that resolve the specific Url
        val specializedActivityIntent = Intent(Intent.ACTION_VIEW, uri)
            .addCategory(Intent.CATEGORY_BROWSABLE)
        val resolvedSpecializedList =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                extractPackageNames(pm.queryIntentActivities(specializedActivityIntent, PackageManager.ResolveInfoFlags.of(0)))
            } else {
                @Suppress("DEPRECATION")
                extractPackageNames(pm.queryIntentActivities(specializedActivityIntent, 0))
            }

        // Keep only the Urls that resolve the specific, but not the generic urls
        resolvedSpecializedList.removeAll(genericResolvedList)

        // If the list is empty, no native app handlers were found
        if (resolvedSpecializedList.isEmpty()) {
            return false
        }

        // Native handlers are found. Launch the Intent
        specializedActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(specializedActivityIntent)
        return true
    }

    @JvmStatic
    private fun extractPackageNames(resolveInfos: List<ResolveInfo>): MutableSet<String> {
        val packageNameSet: MutableSet<String> = HashSet()
        for (ri in resolveInfos) {
            packageNameSet.add(ri.activityInfo.packageName)
        }
        return packageNameSet
    }
}