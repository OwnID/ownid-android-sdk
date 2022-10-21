package com.ownid.sdk

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.annotation.VisibleForTesting
import com.ownid.sdk.Configuration.Companion.createFromAssetFile
import com.ownid.sdk.Configuration.Companion.createFromJson
import com.ownid.sdk.internal.OwnIdActivity
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.util.*

/**
 * Class-holder for OwnID configuration parameters. Use [createFromAssetFile] or [createFromJson] methods to create instance.
 * Example of full JSON configuration:
 * ```
 * {
 *  "app_id": "gephu5k2dnff2v",
 *  "env": "dev", // optional: "dev", "staging", "uat". If absent - production
 *  "redirection_uri": "com.ownid.demo:/",  // optional. If absent - ${packageName}://ownid/redirect/
 *  "enable_logging": false, // optional, default - false
 * }
 *```
 *
 * @param version           SDK version string.
 * @param userAgent         User Agent string used in network connection to OwnID servers.
 * @param serverUrl         base OwnID server address. Only "https" schema supported.
 * @param redirectionUri    a [Uri] to be used as redirection back from Custom Tab (or standalone Browser)
 * to [OwnIdActivity]; Can be custom Uri schema (like "com.ownid.demo:/android") or "https" Url.
 * @param baseLocaleUri     base OwnID server address for text localization
 *
 * @throws JSONException            on Json parsing error.
 * @throws IllegalArgumentException if any parameter is empty or blank, or [serverUrl] is not "https", not `*.ownid.com`.
 */
@androidx.annotation.OptIn(InternalOwnIdAPI::class)
public class Configuration @VisibleForTesting constructor(
    @InternalOwnIdAPI @JvmField @JvmSynthetic internal val version: String,
    @InternalOwnIdAPI @JvmField public val userAgent: String,
    @InternalOwnIdAPI @JvmField public val serverUrl: HttpUrl,
    @InternalOwnIdAPI @JvmField @JvmSynthetic internal val redirectionUri: Uri,
    @InternalOwnIdAPI @JvmField @JvmSynthetic internal val baseLocaleUri: HttpUrl,
    @InternalOwnIdAPI @JvmField @JvmSynthetic internal val cacheDir: File
) {
    public companion object {
        @InternalOwnIdAPI
        @get:JvmSynthetic
        @get:JvmName("getXAPIVersion")
        public val X_API_VERSION: String = "1"

        private const val SUFFIX_OWNID: String = "ownid"
        private const val SUFFIX_STATUS_FINAL: String = "status/final"
        private const val SUFFIX_EVENTS: String = "events"
        private const val LOCALE_LIST_FILE_NAME: String = "langs.json"
        private const val LOCALE_FILE_NAME: String = "mobile-sdk.json"

        private const val KEY_APP_ID: String = "app_id"
        private const val KEY_ENV: String = "env"
        private const val KEY_REDIRECTION_URI: String = "redirection_uri"
        private const val KEY_ENABLE_LOGGING: String = "enable_logging"

        private const val VERSIONS_PATH = "com/ownid/sdk"

        /**
         * Create [Configuration] by parsing JSON configuration file [configurationFileName]
         * Example of full JSON configuration:
         * ```
         * {
         *  "app_id": "gephu5k2dnff2v",
         *  "env": "dev", // optional: "dev", "staging", "uat". If absent - production
         *  "redirection_uri": "com.ownid.demo:/", // optional. If absent - ${packageName}://ownid/redirect/
         *  "enable_logging": false, // optional, default - false
         * }
         *```
         * @throws JSONException            on Json parsing error.
         * @throws IllegalArgumentException if mandatory parameters are empty, blank, or doesn't meat requirements. See documentation.
         */
        @JvmStatic
        @Throws(JSONException::class, IllegalArgumentException::class)
        public fun createFromAssetFile(
            context: Context, configurationFileName: String, product: String
        ): Configuration = runCatching {
            logD("Configuration.createFromAssetFile: $configurationFileName, product: $product")
            val configJsonString = getFileFromAssets(context.applicationContext, configurationFileName).decodeToString()
            JSONObject(configJsonString).toConfiguration(product, context.applicationContext)
        }.onFailure { logE("Configuration.createFromAssetFile", it) }.getOrThrow()

        /**
         * Create [Configuration] by parsing JSON string
         * Example of full JSON configuration string:
         * ```
         * {
         *  "app_id": "gephu5k2dnff2v",
         *  "env": "dev", // optional: "dev", "staging", "uat". If absent - production
         *  "redirection_uri": "com.ownid.demo:/", // optional. If absent - ${packageName}://ownid/redirect/
         *  "enable_logging": false, // optional, default - false
         * }
         *```
         * @throws JSONException            on Json parsing error.
         * @throws IllegalArgumentException if mandatory parameters are empty, blank, or doesn't meat requirements. See documentation.
         */
        @JvmStatic
        @Throws(JSONException::class, IllegalArgumentException::class)
        public fun createFromJson(
            context: Context, configJsonString: String, product: String
        ): Configuration = runCatching {
            logD("Configuration.createFromJson; product: $product")
            JSONObject(configJsonString).toConfiguration(product, context.applicationContext)
        }.onFailure { logE("Configuration.createFromJson", it) }.getOrThrow()

        @JvmStatic
        @JvmSynthetic
        @InternalOwnIdAPI
        @VisibleForTesting
        public fun getFileFromAssets(context: Context, fileName: String): ByteArray {
            context.assets.open(fileName).use { inputStream ->
                val fileBytes = ByteArray(inputStream.available())
                inputStream.read(fileBytes)
                fileBytes.isNotEmpty() || throw IllegalArgumentException("$fileName is empty")
                return fileBytes
            }
        }

        @JvmStatic
        @JvmSynthetic
        @InternalOwnIdAPI
        @VisibleForTesting
        public fun getVersionsFromAssets(context: Context): List<Pair<String, String>> {
            return context.assets.list(VERSIONS_PATH)?.map { fileName ->
                val properties = context.assets.open("$VERSIONS_PATH/$fileName")
                    .use { Properties().apply { load(it) } }
                Pair(properties.getProperty("name"), properties.getProperty("version"))
            } ?: throw IllegalArgumentException("No property files found in assets folder '$VERSIONS_PATH'.")
        }

        @JvmStatic
        @JvmSynthetic
        @InternalOwnIdAPI
        @VisibleForTesting
        public fun createUserAgent(
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

        @JvmStatic
        @JvmSynthetic
        @InternalOwnIdAPI
        @VisibleForTesting
        public fun createVersion(product: String, productModules: List<Pair<String, String>>): String {
            val productVersion = productModules.firstOrNull { it.first == product }?.second
            val productString = productVersion?.let { "$product/$it" } ?: product

            val coreVersion = productModules.firstOrNull { it.first == OwnIdCore.PRODUCT_NAME }?.second
                ?: throw IllegalArgumentException("No core version found.")

            return "$productString ${OwnIdCore.PRODUCT_NAME}/$coreVersion"
        }

        @JvmStatic
        @JvmSynthetic
        @InternalOwnIdAPI
        @VisibleForTesting
        public fun JSONObject.toConfiguration(product: String, context: Context): Configuration {
            OwnIdLogger.enabled = optBoolean(KEY_ENABLE_LOGGING)

            val productModules = getVersionsFromAssets(context)
            val userAgent = createUserAgent(product, productModules, context.packageName)
            val version = createVersion(product, productModules)

            val appId = optString(KEY_APP_ID)
            require(appId.matches("^[A-Za-z0-9]+$".toRegex())) { "Bad or empty App Id ($appId)" }

            val serverUrl = when (val env = optString(KEY_ENV)) {
                "dev", "staging", "uat" -> "https://$appId.server.$env.ownid.com"
                "" -> "https://$appId.server.ownid.com"
                else -> throw IllegalArgumentException("Unknown environment: $env")
            }.toHttpUrl()

            val redirectionUri =
                if (has(KEY_REDIRECTION_URI)) Uri.parse(optString(KEY_REDIRECTION_URI)).normalizeScheme()
                else {
                    val scheme = context.packageName
                    require(scheme.matches("^[a-zA-Z][a-zA-Z0-9.+-]+$".toRegex())) {
                        "Application package name ($scheme) cannot be used as URI scheme: https://datatracker.ietf.org/doc/html/rfc3986#section-3.1"
                    }
                    Uri.parse("$scheme://ownid/redirect/")
                }

            val baseLocaleUri = when (val env = optString(KEY_ENV)) {
                "dev", "staging", "uat" -> "https://i18n.dev.ownid.com"
                "" -> "https://i18n.prod.ownid.com"
                else -> throw IllegalArgumentException("Unknown environment: $env")
            }.toHttpUrl()

            return Configuration(version, userAgent, serverUrl, redirectionUri, baseLocaleUri, context.cacheDir)
        }
    }

    init {
        runCatching {
            require(userAgent.isNotBlank()) { "User agent cannot be empty" }

            require(serverUrl.isHttps) { "Server url: only https supported" }
            require("ownid.com".equals(serverUrl.topPrivateDomain(), true)) { "Server url: Not *.ownid.com url" }

            require(redirectionUri.isAbsolute) { "Redirection URI must contain an explicit scheme" }
        }.onFailure { logE("init", it) }.getOrThrow()
    }

    @get:JvmSynthetic
    @InternalOwnIdAPI
    internal val ownIdUrl: HttpUrl = serverUrl.newBuilder().addEncodedPathSegments(SUFFIX_OWNID).build()

    @get:JvmSynthetic
    @InternalOwnIdAPI
    internal val ownIdEventsUrl: HttpUrl = serverUrl.newBuilder().addEncodedPathSegments(SUFFIX_EVENTS).build()

    @get:JvmSynthetic
    @InternalOwnIdAPI
    internal val ownIdStatusUrl: HttpUrl = ownIdUrl.newBuilder().addEncodedPathSegments(SUFFIX_STATUS_FINAL).build()

    @get:JvmSynthetic
    @InternalOwnIdAPI
    internal val ownIdLocaleListUrl: HttpUrl = baseLocaleUri.newBuilder().addPathSegment(LOCALE_LIST_FILE_NAME).build()

    @JvmSynthetic
    @InternalOwnIdAPI
    internal fun getLocaleUrl(serverLocaleTag: String): HttpUrl =
        baseLocaleUri.newBuilder().addPathSegment(serverLocaleTag).addPathSegment(LOCALE_FILE_NAME).build()
}