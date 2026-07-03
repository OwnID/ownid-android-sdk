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
 * - When `sessionInfo` exists, reads `sessionSecret`, `sessionToken`, and an optional positive expiration from
 *   `expires_in` or `expirationTime`, sets Gigya session, and returns success with
 *   `SessionOutput(session = gigya.session)`.
 * - When non-empty `errorJson` exists, maps it with `GigyaError.fromResponse(...)` and returns failure.
 * - Missing session fields, malformed JSON, Gigya error mapping failures, or any other payload shape return failure.
 * - Does not cancel in-progress payload parsing or Gigya session assignment.
 *
 * [PasswordAuthenticate.authenticate] behavior:
 * - Delegates to `gigya.login`.
 * - Waits for the Gigya callback result.
 * - Cancellation does not cancel the underlying Gigya request because the API does not expose a cancellation handle.
 * - Late callback results are ignored when the coroutine is no longer active.
 * - Returns success with `SessionOutput(session = account.sessionInfo)` on callback success.
 * - Returns failure with [GigyaException] on callback error.
 *
 * Helper and Gigya failures are returned as [Result.failure] with [GigyaException]. Password-authentication coroutine
 * cancellation remains cancellation. OwnID invokes these handlers on the main thread through the provider contracts;
 * keep additional work suspending rather than blocking that thread.
 *
 * @param gigya Gigya core instance to use.
 * @return This registrar for chaining.
 */
public fun <A : GigyaAccount> OwnIdProvidersRegistrar.gigyaProviders(gigya: Gigya<A>): OwnIdProvidersRegistrar = apply {

    @OptIn(InternalOwnIdAPI::class)
    val logger = getOrNull(OwnIdLogRouter::class.java)

    @OptIn(InternalOwnIdAPI::class)
    sessionCreate {
        create { params ->
            runCatching {
                val serialization = getOrThrow(Serialization::class.java)
                val jsonObject = serialization.parseToJsonElement(params.sessionPayload).jsonObject

                jsonObject["sessionInfo"]?.jsonObject?.let { sessionInfo ->
                    val secret = sessionInfo["sessionSecret"]?.jsonPrimitive?.content
                        ?: throw IllegalArgumentException("Missing sessionSecret")
                    val token = sessionInfo["sessionToken"]?.jsonPrimitive?.content
                        ?: throw IllegalArgumentException("Missing sessionToken")
                    val expirationSeconds = sessionInfo["expires_in"]?.jsonPrimitive?.longOrNull?.takeIf { it > 0L }
                        ?: sessionInfo["expirationTime"]?.jsonPrimitive?.longOrNull?.takeIf { it > 0L }
                        ?: 0L

                    gigya.setSession(SessionInfo(secret, token, expirationSeconds))
                    return@create Result.success(SessionOutput(session = gigya.session))
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
