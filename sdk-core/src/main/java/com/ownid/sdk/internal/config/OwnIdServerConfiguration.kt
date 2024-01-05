package com.ownid.sdk.internal.config

import android.net.Uri
import androidx.annotation.RestrictTo
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.internal.OwnIdInternalLogger
import com.ownid.sdk.internal.events.LogItem
import okhttp3.HttpUrl
import org.json.JSONObject

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class OwnIdServerConfiguration internal constructor(
    @JvmField internal val logLevel: LogItem.Level,
    @JvmField internal val redirectUrl: String?,
    @JvmField internal val androidSettings: AndroidSettings,
    @JvmField internal val passkeysAutofillEnabled: Boolean,
    @JvmField internal val enableRegistrationFromLogin: Boolean,
    @JvmField internal val supportedLocales: Set<String>,
    @JvmField internal val loginId: LoginId,
    @JvmField internal val origin: Set<String>,
    @JvmField internal val phoneCodes: List<PhoneCode>,
    @JvmField internal val serverUrl: HttpUrl
) {

    @InternalOwnIdAPI
    internal class AndroidSettings private constructor(
        @JvmField internal val packageName: String,
        @JvmField internal val certificateHashes: Set<String>,
        @JvmField internal val redirectUrlOverride: String?
    ) {
        internal companion object {
            internal fun fromResponse(response: JSONObject): AndroidSettings = response.optJSONObject("androidSettings")?.run {
                val packageName = optString("packageName")

                val certificateHashes = optJSONArray("certificateHashes")?.let { jsonArray ->
                    List(jsonArray.length()) { jsonArray.optString(it) }.mapNotNull { hash ->
                        val cleanHash = hash.filterNot { it == ':' }.uppercase().filter { it in "0123456789ABCDEF" }
                        if (cleanHash.length % 2 != 0 || (cleanHash.length / 2 != 20 && cleanHash.length / 2 != 32)) {
                            OwnIdInternalLogger.logE(this@Companion, "AndroidSettings", "Invalid SHA1 or SHA256 hash")
                            null
                        } else hash
                    }

                } ?: emptyList()

                var redirectUrlOverride: String? = null
                if (has("redirectUrlOverride")) {
                    val uri = Uri.parse(optString("redirectUrlOverride")).normalizeScheme()
                    if (uri.isAbsolute) redirectUrlOverride = uri.toString()
                    else OwnIdInternalLogger.logE(this@Companion, "AndroidSettings", "'redirectUrlOverride' must contain an explicit scheme: '$uri'")
                }

                AndroidSettings(packageName, certificateHashes.toSet(), redirectUrlOverride)

            } ?: AndroidSettings("", emptySet(), null)
        }
    }

    @InternalOwnIdAPI
    internal class LoginId private constructor(@JvmField internal val type: Type, @JvmField internal val regex: Regex) {

        @InternalOwnIdAPI
        internal enum class Type { Email, PhoneNumber, UserName }

        internal companion object {
            internal fun fromResponse(response: JSONObject): LoginId {
                val loginIdJson = response.optJSONObject("loginId") ?: JSONObject("""{"type":"email"}""")
                val typeString = loginIdJson.getString("type")
                val type = Type.entries.firstOrNull { it.name.equals(typeString, ignoreCase = true) } ?: run {
                    OwnIdInternalLogger.logE(this, "LoginId", "No supported LoginId.Type found: $typeString")
                    Type.Email
                }
                return LoginId(type, loginIdJson.optString("regex").ifBlank { ".*" }.toRegex())
            }
        }
    }

    @InternalOwnIdAPI
    internal data class PhoneCode(
        @JvmField internal val code: String,
        @JvmField internal val name: String,
        @JvmField internal val dialCode: String,
        @JvmField internal val emoji: String
    ) {

        internal companion object {
            internal fun fromResponse(response: JSONObject): PhoneCode = PhoneCode(
                response.optString("code"),
                response.optString("name"),
                response.optString("dialCode"),
                response.optString("emoji")
            )
        }

        internal fun toCompactString(): String = "$emoji  $dialCode"
        override fun toString(): String = "$emoji   $name   $dialCode"
    }

    @InternalOwnIdAPI
    internal fun isFidoPossible(): Boolean = androidSettings.packageName.isNotBlank() && androidSettings.certificateHashes.isNotEmpty()

    @InternalOwnIdAPI
    internal fun redirectUri(): String? = androidSettings.redirectUrlOverride ?: redirectUrl
}