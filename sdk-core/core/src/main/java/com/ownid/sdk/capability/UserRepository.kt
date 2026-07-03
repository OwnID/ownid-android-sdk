package com.ownid.sdk.capability

import com.ownid.sdk.model.User

/**
 * Persists the last authenticated [User] (login ID and auth method).
 *
 * The default repository stores the record in SDK-owned storage scoped by the configured storage file name. The SDK
 * treats the value as returning-user state only; it may contain PII, encryption is not added by this capability, and
 * apps that replace it own equivalent protection and retention behavior.
 */
public interface UserRepository : Capability {
    /**
     * Returns the last authenticated user, or null if none is stored.
     *
     * @return The last [User], or null.
     *
     * @throws Exception on storage read failure.
     */
    @Throws
    public suspend fun getLastUser(): User?

    /**
     * Stores [user] as the last authenticated user.
     *
     * @throws Exception on storage write failure.
     */
    @Throws
    public suspend fun setLastUser(user: User)

    /**
     * Removes the stored last-user record.
     *
     * @throws Exception on storage write failure.
     */
    @Throws
    public suspend fun clearLastUser()
}
