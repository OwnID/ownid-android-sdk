package com.ownid.sdk.dsl

import android.content.Context
import android.graphics.drawable.Drawable
import com.ownid.sdk.AuthMethod
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.JsonSerializable
import com.ownid.sdk.OwnId
import com.ownid.sdk.OwnIdProvider
import com.ownid.sdk.OwnIdProviders
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

@DslMarker
public annotation class OwnIdProviderDsl

/**
 * Builder class for configuring [OwnIdProviders].
 *
 * Define session, account, authentication, and logo providers to be used during the OwnID flow.
 *
 * See [OwnIdProvider]
 */
@OwnIdStartDsl
@OwnIdProviderDsl
public class OwnIdProvidersBuilder internal constructor(
    private val existingProviders: OwnIdProviders? = null
) {
    private var sessionProvider: OwnIdProvider.SessionProvider? = null
    private var accountProvider: OwnIdProvider.AccountProvider? = null
    private val authProviders = mutableListOf<OwnIdProvider.AuthProvider>()
    private var logoProvider: OwnIdProvider.LogoProvider? = null

    /**
     * Configures the session provider using a [SessionProviderBuilder].
     *
     * @param block Lambda function to configure the session provider.
     */
    public fun session(block: SessionProviderBuilder.() -> Unit) {
        sessionProvider = SessionProviderBuilder().apply(block).build()
    }

    /**
     * Configures the account provider using an [AccountProviderBuilder].
     *
     * @param block Lambda function to configure the account provider.
     */
    public fun account(block: AccountProviderBuilder .() -> Unit) {
        accountProvider = AccountProviderBuilder().apply(block).build()
    }

    /**
     * Configures the authentication providers using an [AuthProvidersBuilder].
     *
     * @param block Lambda function to configure the authentication providers.
     */
    public fun auth(block: AuthProvidersBuilder.() -> Unit) {
        authProviders.add(AuthProvidersBuilder().apply(block).build())
    }

    /**
     * Configures the logo provider:
     *
     * Example usage:
     * ```
     * OwnId.providers {
     *     logo { context: Context, logoUrl: String? ->
     *         // return some StateFlow<Drawable?>
     *     }
     * }
     * ```
     *
     * @param block A lambda that receives [Context] and an optional logoUrl, and must return a [StateFlow] of [Drawable?].
     */
    public fun logo(block: (context: Context, logoUrl: String?) -> StateFlow<Drawable?>) {
        logoProvider = object : OwnIdProvider.LogoProvider {
            override fun getLogo(context: Context, logoUrl: String?): StateFlow<Drawable?> = block(context, logoUrl)
        }
    }

    /**
     * Builds the [OwnIdProviders] instance.
     *
     * @return The [OwnIdProviders] instance.
     */
    public fun build(): OwnIdProviders = OwnIdProviders(
        session = sessionProvider ?: existingProviders?.session,
        account = accountProvider ?: existingProviders?.account,
        auth = if (authProviders.isEmpty() && !existingProviders?.auth.isNullOrEmpty()) existingProviders!!.auth else authProviders,
        logo = logoProvider ?: existingProviders?.logo
    )
}


/**
 * Configures global OwnID [OwnIdProvider] using an [OwnIdProvidersBuilder].
 *
 * These providers will be used for all OwnID flows unless overridden using [OwnId.start].
 */
public fun OwnId.providers(block: OwnIdProvidersBuilder .() -> Unit) {
    providers = OwnIdProvidersBuilder(providers).apply(block).build()
}

/**
 * Builder class for configuring the session provider.
 *
 * This builder allows you to define how sessions are created.
 */
@OwnIdProviderDsl
public class SessionProviderBuilder {
    private var createSession: (suspend (loginId: String, session: String, authToken: String, authMethod: AuthMethod?) -> AuthResult)? =
        null

    /**
     * Defines how sessions are created.
     *
     * Implement function to create a user session using the provided data and return a [AuthResult] indicating whether the session creation was successful or not.
     *
     * @param loginId The user's login identifier.
     * @param session Raw session data received from the OwnID.
     * @param authToken OwnID authentication token associated with the session.
     * @param authMethod Type of authentication used for the session (optional).
     *
     * @return [AuthResult] with the result of the session creation operation.
     */
    public fun create(block: suspend (loginId: String, session: String, authToken: String, authMethod: AuthMethod?) -> AuthResult) {
        createSession = block
    }

    /**
     * Builds the [SessionProviderBuilder] instance.
     *
     * @return The [SessionProviderBuilder] instance.
     */
    public fun build(): OwnIdProvider.SessionProvider {
        requireNotNull(createSession) { "create block must be provided" }

        return object : OwnIdProvider.SessionProvider {
            override suspend fun create(loginId: String, session: String, authToken: String, authMethod: AuthMethod?): AuthResult =
                createSession!!(loginId, session, authToken, authMethod)
        }
    }
}

/**
 * Builder class for configuring the account provider.
 *
 * This builder allows you to define how accounts are created.
 */
@OwnIdProviderDsl
public class AccountProviderBuilder {
    private var registerAccount: (suspend (loginId: String, profile: String, ownIdData: String?, authToken: String?) -> AuthResult)? = null

    /**
     * Defines how accounts are created.
     *
     * Implement function to registers a new account with the given loginId and profile information.
     *
     * Set `ownIdData` to the user profile if available.
     *
     * @param loginId The user's login identifier.
     * @param profile Raw profile data received from the OwnID.
     * @param ownIdData Optional data associated with the user.
     * @param authToken OwnID authentication token associated with the session (optional).
     *
     * @return [AuthResult] with the result of the account registration operation.
     */
    public fun register(block: suspend (loginId: String, profile: String, ownIdData: String?, authToken: String?) -> AuthResult) {
        registerAccount = block
    }

    /**
     * Builds the [AccountProviderBuilder] instance.
     *
     * @return The [AccountProviderBuilder] instance.
     */
    public fun build(): OwnIdProvider.AccountProvider {
        requireNotNull(registerAccount) { "register block must be provided" }

        return object : OwnIdProvider.AccountProvider {
            override suspend fun register(loginId: String, profile: String, ownIdData: String?, authToken: String?): AuthResult =
                registerAccount!!(loginId, profile, ownIdData, authToken)
        }
    }
}

/**
 * Builder class for configuring the authentication providers.
 *
 * This builder allows you to define various authentication mechanisms.
 */
@OwnIdProviderDsl
public class AuthProvidersBuilder {
    private var password: OwnIdProvider.AuthProvider.Password? = null

    /**
     * Configures the password authentication provider using a [PasswordProviderBuilder].
     *
     * @param block Lambda function to configure the password authentication provider.
     */
    public fun password(block: PasswordProviderBuilder.() -> Unit) {
        password = PasswordProviderBuilder().apply(block).build()
    }

    /**
     * Builds the [AuthProvidersBuilder] instance.
     *
     * @return The [AuthProvidersBuilder] instance.
     */
    public fun build(): OwnIdProvider.AuthProvider {
        return password ?: throw IllegalStateException("No authentication method provided")
    }
}

/**
 * Builder class for configuring the password authentication provider.
 */
@OwnIdProviderDsl
public class PasswordProviderBuilder {
    private var authenticate: (suspend (loginId: String, password: String) -> AuthResult)? = null

    /**
     * Defines how password authentication is performed.
     *
     * Implement function to authenticates user with the given loginId and password.
     *
     * @param loginId The user's login identifier.
     * @param password The user's password.
     *
     * @return [AuthResult] with the result of the authentication operation.
     */
    public fun authenticate(block: suspend (loginId: String, password: String) -> AuthResult) {
        authenticate = block
    }

    /**
     * Builds the [PasswordProviderBuilder] instance.
     *
     * @return The [PasswordProviderBuilder] instance.
     */
    public fun build(): OwnIdProvider.AuthProvider.Password {
        requireNotNull(authenticate) { "authenticate block must be provided" }

        return object : OwnIdProvider.AuthProvider.Password {
            override suspend fun authenticate(loginId: String, password: String): AuthResult = authenticate!!(loginId, password)
        }
    }
}

/**
 * Represents the result of an authentication operation.
 *
 * @property status Status of the authentication operation.
 */
public sealed class AuthResult(private val status: String) : JsonSerializable {
    public class Fail(public val reason: String?) : AuthResult("fail")
    public class LoggedIn : AuthResult("logged-in")

    @InternalOwnIdAPI
    public override fun toJson(): String = JSONObject().apply {
        put("status", status)
        when (this@AuthResult) {
            is Fail -> if (reason != null) put("reason", reason)
            is LoggedIn -> Unit
        }
    }.toString()
}