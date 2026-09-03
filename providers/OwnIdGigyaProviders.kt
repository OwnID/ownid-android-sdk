package com.ownid.sdk.provider

import com.gigya.android.sdk.Gigya
import com.gigya.android.sdk.GigyaLoginCallback
import com.gigya.android.sdk.account.models.GigyaAccount
import com.gigya.android.sdk.api.GigyaApiResponse
import com.gigya.android.sdk.network.GigyaError
import com.gigya.android.sdk.session.SessionInfo
import com.ownid.sdk.OwnId
import com.ownid.sdk.capability.Serialization
import com.ownid.sdk.logger.OwnIdLogRouter
import com.ownid.sdk.model.InternalOwnIdAPI
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Gigya-backed failure returned by [gigyaProviders] provider callbacks.
 *
 * The registered provider callback returns this type through [Result.failure]; the helper does not throw it directly.
 *
 * @property gigyaError Raw Gigya error when available; otherwise `null`.
 */
public class GigyaException internal constructor(
    public val gigyaError: GigyaError?,
    message: String?,
    cause: Throwable? = null
) : Throwable(message, cause)

/**
 * Registers Gigya-backed [SessionCreate.create] and [PasswordAuthenticate.authenticate] handlers on this registrar.
 *
 * Call this source-only helper inside an OwnID providers block such as [OwnId.setProviders] or
 * [OwnId.withProviders]. It depends on the target that compiles it having the Gigya Android SDK on its classpath. It is
 * not packaged into the SDK artifacts and is not a separate product.
 *
 * Treat `sessionPayload`, Gigya session token/secret values, and passwords passed through this helper as sensitive.
 *
 * [SessionCreate.create] behavior:
 * - Treats `sessionPayload` as required OwnID-provided JSON text and expects a top-level JSON object.
 * - `sessionInfo` requires non-empty JSON-string `sessionSecret` and `sessionToken` values and accepts an optional
 *   positive expiration from `expires_in` or `expirationTime`.
 * - Valid input returns success with a session containing non-empty credentials; invalid, malformed, or error payloads
 *   return failure.
 *
 * [PasswordAuthenticate.authenticate] behavior:
 * - Authenticates the login ID and password through SAP Customer Data Cloud.
 * - Returns success with the resulting session or failure with [GigyaException].
 * - Cancellation remains cancellation and may leave authentication in progress.
 *
 * Helper and Gigya failures are returned as [Result.failure] with [GigyaException]. OwnID invokes these handlers on the
 * main thread through the provider contracts; keep additional work suspending rather than blocking that thread.
 *
 * @param gigya Gigya core instance to use.
 * @return This registrar for chaining.
 */
public fun <A : GigyaAccount> OwnIdProvidersRegistrar.gigyaProviders(gigya: Gigya<A>): OwnIdProvidersRegistrar = apply {

    @OptIn(InternalOwnIdAPI::class)
    val logger = getOrNull(OwnIdLogRouter::class.java)

    @OptIn(InternalOwnIdAPI::class)
    sessionCreate {
        fun JsonObject.requireNonEmptyString(key: String): String {
            val primitive = this[key] as? JsonPrimitive
            if (primitive == null || !primitive.isString || primitive.content.isEmpty()) {
                throw IllegalArgumentException("Missing, non-string, or empty $key")
            }
            return primitive.content
        }

        create { params ->
            runCatching {
                val serialization = getOrThrow(Serialization::class.java)
                val jsonObject = serialization.parseToJsonElement(params.sessionPayload).jsonObject

                val sessionInfoElement = jsonObject["sessionInfo"]
                if (sessionInfoElement != null) {
                    val sessionInfo = sessionInfoElement as? JsonObject ?: throw IllegalArgumentException("Invalid sessionInfo")
                    val token = sessionInfo.requireNonEmptyString("sessionToken")
                    val secret = sessionInfo.requireNonEmptyString("sessionSecret")
                    val expirationSeconds = sessionInfo["expires_in"]?.jsonPrimitive?.longOrNull?.takeIf { it > 0L }
                        ?: sessionInfo["expirationTime"]?.jsonPrimitive?.longOrNull?.takeIf { it > 0L }
                        ?: 0L

                    gigya.setSession(SessionInfo(secret, token, expirationSeconds))
                    val activeSession: SessionInfo = gigya.session ?: throw IllegalStateException("No active Gigya session after assignment")
                    val activeToken: String? = activeSession.sessionToken
                    val activeSecret: String? = activeSession.sessionSecret
                    if (activeToken.isNullOrEmpty() || activeSecret.isNullOrEmpty()) {
                        throw IllegalStateException("Gigya session has empty credentials after assignment")
                    }
                    return@create Result.success(SessionOutput(session = activeSession))
                }

                jsonObject["errorJson"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() }?.let { errorJson ->
                    val gigyaError = GigyaError.fromResponse(GigyaApiResponse(errorJson))
                    logger?.logW(this, "sessionCreate", "[${gigyaError.errorCode}] ${gigyaError.localizedMessage}")
                    val gigyaException = GigyaException(gigyaError, "sessionCreate: [${gigyaError.errorCode}] ${gigyaError.localizedMessage}")
                    return@create Result.failure(gigyaException)
                }

                logger?.logW(this, "sessionCreate", "Unexpected data in sessionPayload")
                val gigyaException = GigyaException(null, "sessionCreate: Unexpected data in sessionPayload")
                return@create Result.failure(gigyaException)
            }.getOrElse { error ->
                logger?.logW(this, "sessionCreate", "Gigya sessionCreate failed: ${error.message}", error)
                return@create Result.failure(GigyaException(null, "sessionCreate error: ${error.message}", error))
            }
        }
    }

    passwordAuthenticate {
        authenticate { params ->
            suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation {
                    // Gigya login API does not expose cancellation.
                }

                gigya.login(params.loginId.id, params.password, object : GigyaLoginCallback<A>() {
                    override fun onSuccess(account: A) {
                        if (!continuation.isActive) return
                        continuation.resume(Result.success(SessionOutput(session = account.sessionInfo)))
                    }

                    override fun onError(error: GigyaError) {
                        if (!continuation.isActive) return
                        continuation.resume(Result.failure(GigyaException(error, error.localizedMessage)))
                    }
                })
            }
        }
    }
}
