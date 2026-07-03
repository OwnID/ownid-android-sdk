package com.ownid.sdk.provider

import androidx.annotation.MainThread
import com.ownid.sdk.OwnId
import com.ownid.sdk.capability.Capability
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.AuthMethod
import com.ownid.sdk.model.LoginId

/**
 * App-owned provider that creates a host session after OwnID authentication succeeds.
 *
 * Register via [OwnId.setProviders] or [OwnId.withProviders].
 *
 * OwnID invokes [isAvailable] and [create] on the main thread.
 *
 * The app owns session creation, token exchange, persistence, and the meaning of failures returned from [create]. If the
 * app's session layer uses blocking work, dispatch that work away from the main thread inside the callback and return only
 * after the app-owned work completes.
 */
public interface SessionCreate : Capability {

    /**
     * Parameters for [SessionCreate].
     *
     * @property loginId Authenticated login ID.
     * @property accessToken OwnID access token associated with this authentication. Treat it as sensitive and avoid
     * logging or persisting it unless your session boundary requires it.
     * @property authMethod Authentication method associated with the OwnID authentication result.
     * @property sessionPayload Server-provided host session payload. Structured values remain JSON text. If the server
     * returns a plain string, this property contains that string value. Treat it as sensitive session material.
     */
    public class Params(
        public val loginId: LoginId,
        public val accessToken: AccessToken,
        public val authMethod: AuthMethod,
        public val sessionPayload: String
    ) : Capability.Params

    /**
     * Returns whether session creation can run for the provided parameters.
     *
     * OwnID invokes this availability check with [Params] on the main thread before [create]. A `null` value is a
     * general readiness check without session-specific data. The default implementation returns `true`; implementations
     * that require [Params] should return `false` for `null` or unsupported parameter types.
     *
     * @param params Optional session creation parameters. `null` means no parameter-specific check is requested.
     */
    @MainThread
    public override suspend fun isAvailable(params: Capability.Params?): Boolean = super.isAvailable(params)

    /**
     * Creates host session output for an authenticated user.
     *
     * Use [Params.accessToken] and [Params.sessionPayload] at the app's authentication boundary to create or refresh the
     * host session. Return [Result.success] with [SessionOutput] when session creation succeeds, or [Result.failure]
     * when it fails. OwnID does not retry this callback or persist the returned [SessionOutput]. OwnID calls this method
     * on the main thread and does not move app-owned work to a background dispatcher for you.
     *
     * @param params Session creation parameters.
     */
    @MainThread
    public suspend fun create(params: Params): Result<SessionOutput>
}
