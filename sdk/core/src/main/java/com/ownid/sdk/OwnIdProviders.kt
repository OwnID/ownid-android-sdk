package com.ownid.sdk

import android.content.Context
import android.graphics.drawable.Drawable
import com.ownid.sdk.dsl.AuthResult
import kotlinx.coroutines.flow.StateFlow

/**
 * Represents different types of providers used for OwnID authentication flow.
 *
 * Providers manage critical components such as session handling and authentication mechanisms, including traditional password-based logins.
 * They also manage account creation and retrieval of branded visuals (logo).
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

    /**
     * The Logo Provider retrieves a logo for branding purposes.
     */
    public interface LogoProvider : OwnIdProvider {
        /**
         * Retrieves a [Drawable] logo as a [StateFlow], which can be emitted from any data source (e.g., remote or local).
         *
         * @param context The current [Context].
         * @param logoUrl An optional URL string for locating the logo.
         *
         * @return A [StateFlow] that emits the [Drawable] logo or null if not found.
         */
        public fun getLogo(context: Context, logoUrl: String?): StateFlow<Drawable?>
    }
}

/**
 * Holds instances of different OwnID providers.
 *
 * @property session Optional session provider.
 * @property account Optional account provider.
 * @property auth List of authentication providers.
 * @property logo Optional logo provider for branding.
 */
public data class OwnIdProviders(
    public var session: OwnIdProvider.SessionProvider? = null,
    public var account: OwnIdProvider.AccountProvider? = null,
    public var auth: List<OwnIdProvider.AuthProvider> = emptyList(),
    public var logo: OwnIdProvider.LogoProvider? = null
)