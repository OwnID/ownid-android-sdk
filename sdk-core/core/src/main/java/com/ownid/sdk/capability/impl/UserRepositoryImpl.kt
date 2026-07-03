package com.ownid.sdk.capability.impl

import com.ownid.sdk.capability.Serialization
import com.ownid.sdk.capability.Storage
import com.ownid.sdk.capability.UserRepository
import com.ownid.sdk.model.User

/**
 * Storage-backed [UserRepository] for the SDK's last-user record.
 *
 * The repository serializes the complete [User] value into SDK-owned storage and returns `null` when no record is
 * present. It does not add encryption, retention policy, or cross-app synchronization beyond the configured
 * [Storage] capability.
 */
internal class UserRepositoryImpl(
    private val storage: Storage,
    private val serialization: Serialization
) : UserRepository {

    private companion object {
        private const val KEY_LAST_USER = "LAST_USER"
    }

    @Throws
    override suspend fun getLastUser(): User? {
        val json = storage.getString(KEY_LAST_USER) ?: return null
        return serialization.decodeFromString(User.serializer(), json)
    }

    @Throws
    override suspend fun setLastUser(user: User) {
        storage.putString(KEY_LAST_USER, serialization.encodeToString(User.serializer(), user))
    }

    @Throws
    override suspend fun clearLastUser() {
        storage.remove(KEY_LAST_USER)
    }
}
