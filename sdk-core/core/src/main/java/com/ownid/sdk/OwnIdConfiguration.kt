package com.ownid.sdk

import android.content.Context
import com.ownid.sdk.capability.Serialization
import com.ownid.sdk.model.InternalOwnIdAPI
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException

/**
 * Configuration that identifies which OwnID tenant and region the SDK targets.
 *
 * Use this to point an SDK instance at your OwnID tenant and data-residency region. [appId] is required, [env]
 * defaults to [Env.Prod], [region] defaults to [Region.US], and [rootUrl] optionally overrides the default OwnID server root URL.
 *
 * Set [env] to match the tenant you intend to use, for example [Env.Uat] for pre-production testing. Set [region] to
 * where the tenant was created so requests resolve to the correct regional endpoints and respect data-residency.
 */
public interface OwnIdConfiguration {
    /** Environment selector for OwnID backend routing. */
    public enum class Env {
        Prod,
        Uat
    }

    /** Data-residency region selector for OwnID backend routing. */
    public enum class Region {
        US,
        EU
    }

    /** OwnID application ID from the OwnID Console; must be non-empty and alphanumeric. */
    public val appId: String

    /** Target environment. Defaults to [Env.Prod]. */
    public val env: Env

    /** Data-residency region. Defaults to [Region.US]. */
    public val region: Region

    /** Custom HTTPS root URL for OwnID servers; must not contain backslashes. */
    public val rootUrl: String?
}

internal enum class InternalEnv { Prod, Uat, Dev }

private object EnvCaseInsensitiveSerializer : KSerializer<OwnIdConfiguration.Env> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("OwnIdConfiguration.Env", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: OwnIdConfiguration.Env) = encoder.encodeString(value.name)
    override fun deserialize(decoder: Decoder): OwnIdConfiguration.Env {
        val raw = decoder.decodeString()
        return OwnIdConfiguration.Env.values().firstOrNull { it.name.equals(raw, ignoreCase = true) }
            ?: throw SerializationException("Invalid value '$raw' for 'env'")
    }
}

private object RegionCaseInsensitiveSerializer : KSerializer<OwnIdConfiguration.Region> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("OwnIdConfiguration.Region", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: OwnIdConfiguration.Region) = encoder.encodeString(value.name)
    override fun deserialize(decoder: Decoder): OwnIdConfiguration.Region {
        val raw = decoder.decodeString()
        return OwnIdConfiguration.Region.values().firstOrNull { it.name.equals(raw, ignoreCase = true) }
            ?: throw SerializationException("Invalid value '$raw' for 'region'")
    }
}

@Serializable
@OptIn(ExperimentalSerializationApi::class)
internal class OwnIdConfigurationImpl(
    @JsonNames("appId", "appID")
    @SerialName("appId") override val appId: String,
    @Serializable(with = EnvCaseInsensitiveSerializer::class)
    @SerialName("env") override val env: OwnIdConfiguration.Env = OwnIdConfiguration.Env.Prod,
    @Serializable(with = RegionCaseInsensitiveSerializer::class)
    @SerialName("region") override val region: OwnIdConfiguration.Region = OwnIdConfiguration.Region.US,
    @JsonNames("rootUrl", "rootURL")
    @SerialName("rootUrl") override var rootUrl: String? = null
) : OwnIdConfiguration {

    internal var internalEnv: InternalEnv? = null

    init {
        require(appId.isNotEmpty() && appId.matches("^[A-Za-z0-9]+$".toRegex())) { "App ID must be alphanumeric and non-empty" }

        rootUrl?.let {
            require('\\' !in it) { "Root URL must not contain backslashes" }
            val url = it.toHttpUrl()
            require(url.isHttps) { "Root URL must be HTTPS" }
            rootUrl = url.newBuilder().query(null).fragment(null).build().toString()
        }
    }
}

@DslMarker
public annotation class OwnIdConfigurationDsl

/**
 * Programmatic builder for [OwnIdConfiguration].
 *
 * Use this when you prefer to set values directly in code. [applicationContext] and [appId] are required; [env]
 * defaults to [OwnIdConfiguration.Env.Prod], [region] defaults to [OwnIdConfiguration.Region.US], [rootUrl] is
 * optional and must be HTTPS, and [languages] optionally overrides the root SDK language tags during initialization.
 * Missing required values or invalid values make [OwnId.initialize] log the build failure and leave any current named
 * instance unchanged.
 */
@OwnIdConfigurationDsl
public class OwnIdConfigurationBuilder internal constructor() {
    private var _applicationContext: Context? = null
    private var _appId: String? = null

    /**
     * Android context used to install process-wide SDK services.
     *
     * Pass any [Context]; the SDK stores [Context.getApplicationContext]. Required.
     */
    public var applicationContext: Context
        get() = requireApplicationContext()
        set(value) {
            _applicationContext = value
        }

    /** Your OwnID application ID from the OwnID Console. Required. */
    public var appId: String
        get() = requireAppId()
        set(value) {
            _appId = value
        }

    /** Target environment. Defaults to [OwnIdConfiguration.Env.Prod]. */
    public var env: OwnIdConfiguration.Env = OwnIdConfiguration.Env.Prod

    /** Data-residency region (US or EU). Defaults to [OwnIdConfiguration.Region.US]. */
    public var region: OwnIdConfiguration.Region = OwnIdConfiguration.Region.US

    /** Custom HTTPS root URL for OwnID servers; must not contain backslashes. */
    public var rootUrl: String? = null

    /**
     * Optional root SDK language override applied during initialization.
     *
     * When set to a non-empty list, these language codes with optional regions replace automatic language selection for
     * the process until [OwnId.setLanguage] is called or the process restarts. An empty list restores automatic language
     * selection. Leaving this `null` keeps the current root language mode unchanged.
     */
    public var languages: List<String>? = null

    internal fun build(): OwnIdConfiguration {
        requireApplicationContext()

        return OwnIdConfigurationImpl(requireAppId(), env, region, rootUrl)
    }

    private fun requireApplicationContext(): Context = requireNotNull(_applicationContext) { "Application context must be provided" }

    private fun requireAppId(): String = requireNotNull(_appId) { "App ID must be provided" }
}

/**
 * Builder that creates [OwnIdConfiguration] from a JSON string.
 *
 * Use this when configuration is provided at runtime. Empty JSON, malformed JSON, or invalid values make
 * [OwnId.initializeFromJson] log the build failure and leave any current named instance unchanged.
 *
 * The JSON object accepts "appId" or "appID", "env", "region", and "rootUrl" or "rootURL". Values
 * for [OwnIdConfiguration.Env] and [OwnIdConfiguration.Region] are decoded case-insensitively.
 *
 * The optional "languages" key accepts an array of language codes with optional regions, such as "en" or "en-US". A
 * non-empty array sets an explicit root language override, an empty array restores automatic language selection, and
 * omitting the key keeps the current root language mode unchanged.
 *
 * Unknown keys are ignored.
 */
@OwnIdConfigurationDsl
@OptIn(InternalOwnIdAPI::class)
public class OwnIdJsonConfigurationBuilder internal constructor(
    private val serialization: Serialization
) {
    private var _applicationContext: Context? = null
    private var _json: String? = null

    /**
     * Android context used to install process-wide SDK services.
     *
     * Pass any [Context]; the SDK stores [Context.getApplicationContext]. Required.
     */
    public var applicationContext: Context
        get() = requireApplicationContext()
        set(value) {
            _applicationContext = value
        }
    internal var languages: List<String>? = null

    /** JSON string containing the configuration. */
    public var json: String
        get() = requireJson()
        set(value) {
            _json = value
        }

    internal fun build(): OwnIdConfiguration {
        requireApplicationContext()
        val rawJson = requireJson()

        val jsonElement = serialization.parseToJsonElement(rawJson)
        val jsonObject = jsonElement as? JsonObject
            ?: return serialization.decodeFromJsonElement(OwnIdConfigurationImpl.serializer(), jsonElement)
        var normalizedJson = JsonObject(jsonObject.toMutableMap().apply {
            listOf("appId", "appID", "rootUrl", "rootURL", "env", "region", "languages").forEach { key ->
                if (this[key] is JsonNull) remove(key)
            }
            if ("appId" !in this && "appID" in this) this["appId"] = getValue("appID")
            if ("rootUrl" !in this && "rootURL" in this) this["rootUrl"] = getValue("rootURL")
            remove("appID")
            remove("rootURL")
        })
        normalizedJson["appId"]?.let { appId ->
            if (appId !is JsonPrimitive || !appId.isString) {
                throw SerializationException("Expected 'appId' to be a string")
            }
        }
        val (env, parsedLanguages) = normalizedJson.extractConfigurationMetadata()
        languages = parsedLanguages
        val isDev = env.equals("dev", ignoreCase = true)
        if (isDev) {
            normalizedJson = JsonObject(normalizedJson.toMutableMap().apply { remove("env") })
        }
        return serialization.decodeFromJsonElement(OwnIdConfigurationImpl.serializer(), normalizedJson).apply {
            if (isDev) internalEnv = InternalEnv.Dev
        }
    }

    private fun requireApplicationContext(): Context = requireNotNull(_applicationContext) { "Application context must be provided" }

    private fun requireJson(): String = requireNotNull(_json) { "JSON must be provided" }
}

/**
 * Builder that creates [OwnIdConfiguration] from a configuration file.
 *
 * Use this to keep configuration with the app binary, for example per-flavor assets. Missing, empty, invalid, or
 * unreadable files make [OwnId.initializeFromFile] log the build failure and leave any current named instance
 * unchanged.
 *
 * The default file name is "OwnIdConfig.json" under the app assets directory.
 *
 * The file must contain a JSON object with "appId" or "appID" for the app ID. It accepts "env", "region", and
 * "rootUrl" or "rootURL"; values for [OwnIdConfiguration.Env] and [OwnIdConfiguration.Region] are decoded
 * case-insensitively, and unknown keys are ignored. The optional "languages" key accepts an array of language codes with
 * optional regions, such as "en" or "en-US". A non-empty languages array sets an explicit root language override, an
 * empty array restores automatic language selection, and omitting the key keeps the current root language mode unchanged.
 */
@OwnIdConfigurationDsl
public class OwnIdFileConfigurationBuilder internal constructor(
    private val serialization: Serialization
) {
    private var _applicationContext: Context? = null

    /**
     * Android context used to read app assets and install process-wide SDK services.
     *
     * Pass any [Context]; the SDK stores [Context.getApplicationContext]. Required.
     */
    public var applicationContext: Context
        get() = requireApplicationContext()
        set(value) {
            _applicationContext = value
        }
    internal var languages: List<String>? = null

    /** Asset file name under the app assets directory. Defaults to "OwnIdConfig.json". */
    public var fileName: String = "OwnIdConfig.json"

    internal fun build(): OwnIdConfiguration {
        val applicationContext = requireApplicationContext()

        val builder = OwnIdJsonConfigurationBuilder(serialization).apply {
            this.applicationContext = applicationContext
            json = applicationContext.readAssetFileBytes(this@OwnIdFileConfigurationBuilder.fileName).decodeToString()
        }
        return builder.build().also { languages = builder.languages }
    }

    private fun requireApplicationContext(): Context = requireNotNull(_applicationContext) { "Application context must be provided" }
}

@Throws(IOException::class, IllegalArgumentException::class)
internal fun Context.readAssetFileBytes(fileName: String): ByteArray {
    assets.open(fileName).use { inputStream ->
        val bytes = inputStream.readBytes()
        if (bytes.isEmpty()) throw IllegalArgumentException("Asset file '$fileName' is empty.")
        return bytes
    }
}

internal fun OwnIdConfiguration.Region.toStringSuffix(): String = when (this) {
    OwnIdConfiguration.Region.US -> ""
    OwnIdConfiguration.Region.EU -> "-eu"
}

internal fun OwnIdConfiguration.env(): InternalEnv = (this as? OwnIdConfigurationImpl)?.internalEnv ?: when (env) {
    OwnIdConfiguration.Env.Prod -> InternalEnv.Prod
    OwnIdConfiguration.Env.Uat -> InternalEnv.Uat
}

internal fun OwnIdConfiguration.toStringPrefix(): String = when (env()) {
    InternalEnv.Prod -> ""
    InternalEnv.Uat -> ".uat"
    InternalEnv.Dev -> ".dev"
}

/**
 * Canonical OwnID app host sent in X-OwnID-AppUrl so backend can resolve the app even when rootUrl routes requests
 * elsewhere.
 */
internal fun OwnIdConfiguration.appUrlHeaderValue(): String =
    "$appId.server${toStringPrefix()}.ownid${region.toStringSuffix()}.com"

internal fun OwnIdConfiguration.instanceIdentity(): String =
    "${env()}_${region}_$appId"

internal fun OwnIdConfiguration.storageFileName(): String =
    "${env().name.lowercase()}_${region.name.lowercase()}_${appId}"

private fun JsonObject?.extractConfigurationMetadata(): Pair<String?, List<String>?> {
    val env = this?.get("env")?.jsonPrimitive?.contentOrNull
    val languages = this?.get("languages")?.let { rawLanguages ->
        (rawLanguages as? JsonArray)?.mapIndexed { index, element ->
            if (element is JsonPrimitive && element.isString) {
                element.content
            } else {
                throw SerializationException("Expected 'languages[$index]' to be a string")
            }
        } ?: throw SerializationException("Expected 'languages' to be an array of strings")
    }

    return env to languages
}
