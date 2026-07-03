package com.ownid.sdk.provider

import androidx.annotation.MainThread
import com.ownid.sdk.OwnId
import com.ownid.sdk.capability.Capability
import com.ownid.sdk.model.LoginId

/**
 * App-owned provider that authenticates a user with login ID and password.
 *
 * Register via [OwnId.setProviders] or [OwnId.withProviders].
 *
 * OwnID invokes [isAvailable] and [authenticate] on the main thread.
 *
 * The app owns password verification, session creation, and the meaning of failures returned from [authenticate]. If the
 * app's authentication layer uses blocking work, dispatch that work away from the main thread inside the callback and
 * return only after the app-owned work completes.
 */
public interface PasswordAuthenticate : Capability {

    /**
     * Parameters for [PasswordAuthenticate].
     *
     * @property loginId User login ID.
     * @property password User password. Treat it as sensitive credential material and never log it.
     */
    public class Params(
        public val loginId: LoginId,
        public val password: String
    ) : Capability.Params

    /**
     * Returns whether password authentication can run for the provided parameters.
     *
     * OwnID invokes this availability check with [Params] on the main thread before [authenticate]. A `null` value is a
     * general readiness check without credential data. The default implementation returns `true`; implementations that
     * require [Params] should return `false` for `null` or unsupported parameter types.
     *
     * @param params Optional password authentication parameters. `null` means no parameter-specific check is requested.
     */
    @MainThread
    public override suspend fun isAvailable(params: Capability.Params?): Boolean = super.isAvailable(params)

    /**
     * Verifies credentials against the host authentication system.
     *
     * Verify credentials at the app's authentication boundary and return [Result.success] with [SessionOutput] when
     * authentication succeeds. Return [Result.failure] for invalid credentials or integration failures. OwnID does not
     * retry this callback or persist the returned [SessionOutput]. OwnID calls this method on the main thread and does not
     * move app-owned work to a background dispatcher for you.
     *
     * @param params Password authentication parameters.
     */
    @MainThread
    public suspend fun authenticate(params: Params): Result<SessionOutput>
}
