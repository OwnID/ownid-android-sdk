package com.ownid.sdk

import com.ownid.sdk.dsl.AuthResult
import com.ownid.sdk.dsl.providers
import com.ownid.sdk.dsl.start

/**
 * Represents different types of providers used for OwnID authentication flow.
 *
 * Providers manage critical components such as session handling and authentication mechanisms, including traditional password-based logins.
 * They allow developers to define how users are authenticated, how sessions are maintained and how accounts are managed within the application.
 *
 * Define providers globally using [OwnId.providers] and override them for specific flows if required using [OwnId.start].
 */
public sealed interface OwnIdProvider {

    /**
     * The Session Provider is responsible for creating user sessions.
     */
    public interface SessionProvider : OwnIdProvider {
        /**
         * Implement function to create a user session using the provided data and return a [AuthResult] indicating whether the session creation was successful or not.
         *
         * @param loginId The user's login ID.
         * @param session Raw session data received from the OwnID.
         * @param authToken OwnID authentication token associated with the session.
         * @param authMethod Type of authentication used for the session (optional).
         *
         * @return [AuthResult] with the result of the session creation operation.
         */
        public suspend fun create(loginId: String, session: String, authToken: String, authMethod: AuthMethod?): AuthResult
    }

    /**
     * The Account Provider manages account creation.
     */
    public interface AccountProvider : OwnIdProvider {
        /**
         * Implement function to registers a new account with the given loginId and profile information.
         *
         * Set `ownIdData` to the user profile if available.
         *
         * @param loginId The user's login ID.
         * @param profile Raw profile data received from the OwnID.
         * @param ownIdData Optional data associated with the user.
         * @param authToken OwnID authentication token associated with the session (optional).
         *
         * @return [AuthResult] with the result of the account registration operation.
         */
        public suspend fun register(loginId: String, profile: String, ownIdData: String?, authToken: String?): AuthResult
    }

    /**
     * The Authentication Provider manages various authentication mechanisms.
     */
    public sealed interface AuthProvider : OwnIdProvider {
        /**
         * Provides password-based authentication functionality.
         */
        public interface Password : AuthProvider {
            /**
             * Implement function to authenticates user with the given loginId and password.
             *
             * @param loginId The user's login ID.
             * @param password The user's password.
             *
             * @return [AuthResult] with the result of the authentication operation.
             */
            public suspend fun authenticate(loginId: String, password: String): AuthResult
        }
    }
}

/**
 * Holds instances of different OwnID providers.
 *
 * @property session Optional session provider.
 * @property account Optional account provider.
 * @property auth List of authentication providers.
 */
public class OwnIdProviders(
    public var session: OwnIdProvider.SessionProvider? = null,
    public var account: OwnIdProvider.AccountProvider? = null,
    public var auth: List<OwnIdProvider.AuthProvider> = emptyList()
)