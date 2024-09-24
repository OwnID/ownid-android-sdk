package com.ownid.sdk

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.annotation.VisibleForTesting
import com.ownid.sdk.Configuration.Companion.createFromAssetFile
import com.ownid.sdk.Configuration.Companion.createFromJson
import com.ownid.sdk.internal.asHexUpper
import com.ownid.sdk.internal.component.OwnIdInternalLogger
import com.ownid.sdk.internal.component.config.OwnIdServerConfiguration
import com.ownid.sdk.internal.feature.nativeflow.steps.webapp.OwnIdWebAppActivity
import com.ownid.sdk.internal.toSHA256Bytes
import org.json.JSONException
import org.json.JSONObject
import java.util.Properties

/**
 * Class-holder for OwnID configuration parameters. Use [createFromAssetFile] or [createFromJson] methods to create instance.
 * Example of full JSON configuration:
 * ```
 * {
 *  "appId": "gephu5k2dnff2v",
 *  "env": "dev", // optional: "dev", "staging", "uat". Any other value or no value (default) - production
 *  "redirectUrl": "com.ownid.demo:/",  // optional. No value (default) - ${packageName}://ownid/redirect/
 *  "enableLogging": false, // optional, No value (default) - false
 * }
 *```
 *
 * @param appId             OwnID application ID from OwnID Console.
 * @param env               OwnID application environment.
 * @param redirectUrl       an [Uri] to be used as redirection back from Custom Tab (or standalone Browser) to [OwnIdWebAppActivity]. Can be custom Uri schema (like "com.ownid.demo:/android") or "https" Url.
 * @param version           OwnID SDK version string.
 * @param userAgent         User Agent string used in network connection to OwnID servers.
 * @param packageName       Name of application's package that runs OwnID SDK.
 * @param certificateHashes Set of certificates SHA256 and SHA1 hashes that used to sign application that runs OwnID SDK.
 */
public class Configuration @VisibleForTesting @InternalOwnIdAPI constructor(
    @JvmField public val appId: String,
    @JvmField public val env: String,
    @JvmField public val redirectUrl: String,
    @JvmField public val version: String,
    @JvmField public val userAgent: String,
    @JvmField public val packageName: String,
    @JvmField public val certificateHashes: Set<String>
) {

    /**
     * String constants for [Configuration] parameters. Use them as a keys to set required values.
     *
     * - ```"appId"```: OwnID application ID from OwnID Console.
     * - ```"env"```: OwnID application environment.
     * - ```"redirectUrl"```: an [Uri] to be used as redirection back from Custom Tab (or standalone Browser). Can be overwritten by ```"redirectUrlAndroid"```.
     * - ```"redirectUrlAndroid"```: an [Uri] to be used as redirection back from Custom Tab (or standalone Browser). Overrides ```"redirectUrl"``` parameter.
     * - ```"enableLogging"```: Enabled OwnID SDK logs
     */
    public object KEY {
        public const val APP_ID: String = "appId"
        public const val ENV: String = "env"
        public const val REDIRECT_URL: String = "redirectUrl"
        public const val REDIRECT_URL_ANDROID: String = "redirectUrlAndroid"
        public const val ENABLE_LOGGING: String = "enableLogging"
    }

    @JvmSynthetic
    @InternalOwnIdAPI
    internal lateinit var server: OwnIdServerConfiguration
        private set

    @JvmSynthetic
    @InternalOwnIdAPI
    internal var isServerConfigurationSet: Boolean = false
        private set

    @JvmSynthetic
    @Synchronized
    @InternalOwnIdAPI
    internal fun setServerConfiguration(value: OwnIdServerConfiguration) {
        server = value
        isServerConfigurationSet = true
    }

    @JvmSynthetic
    @InternalOwnIdAPI
    internal fun getRedirectUri(): String = if (isServerConfigurationSet) server.redirectUri() ?: redirectUrl else redirectUrl

    @JvmSynthetic
    @InternalOwnIdAPI
    internal fun isFidoPossible(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && isServerConfigurationSet
            && server.isFidoPossible()
            && packageName == server.androidSettings.packageName
            && server.androidSettings.certificateHashes.any { certificateHashes.contains(it) }

    @JvmSynthetic
    @InternalOwnIdAPI
    internal fun verify() {
        if (isServerConfigurationSet.not()) {
            OwnIdInternalLogger.logW(this, "verify", "Server configuration is not set")
            return
        }

        if (server.androidSettings.packageName.isBlank() || packageName != server.androidSettings.packageName) {
            val msg = "PackageName mismatch. Configured '${server.androidSettings.packageName}' but is '$packageName'. FIDO disabled"
            OwnIdInternalLogger.logW(this, "verify", "PackageName mismatch", errorMessage = msg)
            return
        }

        val serverHashes = server.androidSettings.certificateHashes
        if (serverHashes.isEmpty() || serverHashes.any { certificateHashes.contains(it) }.not()) {
            val msg = "Certificate hash mismatch. Configured [${serverHashes.joinToString()}], but is [${certificateHashes.joinToString()}]. FIDO disabled"
            OwnIdInternalLogger.logW(this, "verify", "Certificate hash mismatch", errorMessage = msg)
            return
        }
    }

    public companion object {

        /**
         * Create [Configuration] by parsing JSON configuration file [configurationFileName]
         * Example of full JSON configuration:
         * ```
         * {
         *  "appId": "gephu5k2dnff2v",
         *  "env": "dev", // optional: "dev", "staging", "uat". Any other value or no value (default) - production
         *  "redirectUrl": "com.ownid.demo:/",  // optional. No value (default) - ${packageName}://ownid/redirect/
         *  "enableLogging": false, // optional, No value (default) - false
         * }
         *```
         * @param context                   Android [Context]
         * @param configurationFileName     Asset file name with configuration JSON
         * @param product                   Unique product name string
         *
         * @throws JSONException            on Json parsing error.
         * @throws IllegalArgumentException if mandatory parameters are empty, blank, or doesn't meat requirements. See documentation.
         */
        @OptIn(InternalOwnIdAPI::class)
        @Throws(JSONException::class, IllegalArgumentException::class)
        public fun createFromAssetFile(context: Context, configurationFileName: String, product: String): Configuration {
            OwnIdInternalLogger.logI(this, "Configuration.createFromAssetFile", "Product: $product (${context.packageName})")
            val configJsonString = getFileFromAssets(context.applicationContext, configurationFileName).decodeToString()
            return JSONObject(configJsonString).toConfiguration(product, context.applicationContext)
        }

        /**
         * Create [Configuration] by parsing JSON string
         * Example of full JSON configuration string:
         * ```
         * {
         *  "appId": "gephu5k2dnff2v",
         *  "env": "dev", // optional: "dev", "staging", "uat". Any other value or no value (default) - production
         *  "redirectUrl": "com.ownid.demo:/",  // optional. No value (default) - ${packageName}://ownid/redirect/
         *  "enableLogging": false, // optional, No value (default) - false
         * }
         *```
         * @param context               Android [Context]
         * @param configJsonString      String with configuration in JSON format
         * @param product               Unique product name string
         *
         * @throws JSONException            on Json parsing error.
         * @throws IllegalArgumentException if mandatory parameters are empty, blank, or doesn't meat requirements. See documentation.
         */
        @OptIn(InternalOwnIdAPI::class)
        @Throws(JSONException::class, IllegalArgumentException::class)
        public fun createFromJson(context: Context, configJsonString: String, product: String): Configuration {
            OwnIdInternalLogger.logI(this, "Configuration.createFromJson", "Product: $product (${context.packageName})")
            return JSONObject(configJsonString).toConfiguration(product, context.applicationContext)
        }

        private const val VERSIONS_PATH = "com/ownid/sdk"

        @InternalOwnIdAPI
        private fun JSONObject.toConfiguration(product: String, context: Context): Configuration {
            OwnIdLogger.enabled = optBoolean(KEY.ENABLE_LOGGING)

            val appId = getString(KEY.APP_ID)
            require(appId.matches("^[A-Za-z0-9]+$".toRegex())) { "Wrong 'appId' value:'$appId'" }

            val envString = optString(KEY.ENV).lowercase()
            val env = if (envString in listOf("dev", "staging", "uat")) "$envString." else ""

            val redirectUri = if (has(KEY.REDIRECT_URL)) {
                Uri.parse(optString(KEY.REDIRECT_URL)).normalizeScheme().also {
                    require(it.isAbsolute) { "Redirect Url must contain an explicit scheme" }
                }
            } else if (context.packageName.matches("^[a-zA-Z][a-zA-Z0-9.+-]+$".toRegex())) {
                Uri.parse("${context.packageName}://ownid/redirect/").normalizeScheme()
            } else {
                val msg = "Application package name (${context.packageName}) cannot be used as URI scheme: https://datatracker.ietf.org/doc/html/rfc3986#section-3.1"
                OwnIdInternalLogger.logW(this, "Configuration", msg)
                Uri.EMPTY
            }

            val productModules = getVersionsFromAssets(context)
            val userAgent = createUserAgent(product, productModules, context.packageName)
            val version = productModules.joinToString(separator = " ") { "${it.first}/${it.second}" }.trim()

            return Configuration(appId, env, redirectUri.toString(), version, userAgent, context.packageName, getCertificateHashes(context))
        }

        @InternalOwnIdAPI
        @VisibleForTesting
        internal fun getFileFromAssets(context: Context, fileName: String): ByteArray {
            context.assets.open(fileName).use { inputStream ->
                val fileBytes = ByteArray(inputStream.available())
                inputStream.read(fileBytes)
                fileBytes.isNotEmpty() || throw IllegalArgumentException("$fileName is empty")
                return fileBytes
            }
        }

        @InternalOwnIdAPI
        @VisibleForTesting
        internal fun getVersionsFromAssets(context: Context): List<Pair<String, String>> {
            return context.assets.list(VERSIONS_PATH)?.map { fileName ->
                val properties = context.assets.open("${VERSIONS_PATH}/$fileName").use { Properties().apply { load(it) } }
                Pair(properties.getProperty("name"), properties.getProperty("version"))
            } ?: throw IllegalArgumentException("No property files found in assets folder '${VERSIONS_PATH}'.")
        }

        @InternalOwnIdAPI
        private fun createUserAgent(
            product: String, productModules: List<Pair<String, String>>, packageName: String
        ): String {
            val productVersion = productModules.firstOrNull { it.first == product }?.second
            val productString = productVersion?.let { "$product/$it" } ?: product

            val coreVersion = productModules.firstOrNull { it.first == OwnIdCore.PRODUCT_NAME }?.second
                ?: throw IllegalArgumentException("No core version found.")

            val platform = productModules.filterNot { it.first in listOf(OwnIdCore.PRODUCT_NAME, product) }
                .joinToString(separator = " ") { "${it.first}/${it.second}" }.trim()

            return "$productString (Linux; Android ${Build.VERSION.RELEASE}; ${Build.MODEL.filterNot { it == '(' || it == ')' }}) " +
                    "${OwnIdCore.PRODUCT_NAME}/$coreVersion ${if (platform.isNotEmpty()) "$platform " else ""}$packageName"
        }

        @Suppress("DEPRECATION")
        @SuppressLint("PackageManagerGetSignatures")
        @InternalOwnIdAPI
        @VisibleForTesting
        internal fun getCertificateHashes(context: Context): Set<String> = runCatching {
            val signatures = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
            } else {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo.run {
                    if (hasMultipleSigners()) apkContentsSigners else signingCertificateHistory
                }
            }
            signatures.map { it.toByteArray().toSHA256Bytes().asHexUpper() }.toSet()
        }.onFailure {
            OwnIdInternalLogger.logW(this, "getCertificateHashes", "Filed to get certificate hashes: ${it.message}", it)
        }.getOrDefault(emptySet())
    }
}