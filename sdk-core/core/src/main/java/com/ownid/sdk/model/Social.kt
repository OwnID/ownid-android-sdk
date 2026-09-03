package com.ownid.sdk.model

import kotlinx.serialization.Serializable

/**
 * Supported social identity providers.
 *
 * [wireValue] is the stable OwnID provider string used in SDK API requests and responses. Enum constant names are
 * Kotlin names; use [wireValue] when the OwnID string is required. Invalid names passed to [valueOf] throw
 * [IllegalArgumentException].
 */
public enum class SocialProvider(public val wireValue: String) {
    GOOGLE("Google"),

    APPLE("Apple")
}

/**
 * OAuth response type requested during a social sign-in attempt.
 *
 * When this value is passed to SDK OIDC APIs, [CODE] maps to the OwnID `code` request value and [ID_TOKEN] maps to
 * `id_token`. Use the completion method that matches the requested response type.
 */
public enum class OauthResponseType {
    CODE,

    ID_TOKEN
}

/**
 * Server-issued challenge for a social sign-in attempt.
 *
 * This model is [Serializable] with the public `challengeId`, `timeout`, `clientId`, and `challengeUrl` fields. The SDK
 * keeps [clientId] and [challengeUrl] as supplied; [timeout] is normalized by [Timeout].
 *
 * @property challengeId Unique challenge identifier.
 * @property timeout Timeout for the challenge.
 * @property clientId OAuth client ID for the social provider.
 * @property challengeUrl URL to navigate to in order to complete the challenge, when provided.
 */
@Serializable
public class SocialChallenge(
    public val challengeId: ChallengeId,
    public val timeout: Timeout,
    public val clientId: String,
    public val challengeUrl: String? = null
) {
    override fun toString(): String = "SocialChallenge(challengeId='*', timeout=$timeout, clientId=$clientId, challengeUrl=$challengeUrl)"

    override fun equals(other: Any?): Boolean =
        this === other || (
                other is SocialChallenge &&
                        challengeId == other.challengeId &&
                        timeout == other.timeout &&
                        clientId == other.clientId &&
                        challengeUrl == other.challengeUrl
                )

    override fun hashCode(): Int {
        var result = challengeId.hashCode()
        result = 31 * result + timeout.hashCode()
        result = 31 * result + clientId.hashCode()
        result = 31 * result + (challengeUrl?.hashCode() ?: 0)
        return result
    }
}

/**
 * An access token bundled with the user's login ID, profile info, and the social [provider].
 *
 * The SDK returns this model after a successful social sign-in attempt. The app owns the resulting token and profile
 * data and decides how to exchange or persist them at its authentication boundary. [toString] redacts [userInfo] and
 * relies on [AccessToken] to redact the token value.
 *
 * @property accessToken Signed authentication token issued after successful social sign-in.
 * @property loginId Login identifier associated with the authenticated user.
 * @property userInfo User information returned by the social provider as string key-value pairs.
 * @property provider Social provider that issued the authenticated result.
 */
public class AccessTokenWithUserInfo(
    public val accessToken: AccessToken,
    public val loginId: LoginId,
    public val userInfo: Map<String, String>,
    public val provider: SocialProvider
) {
    override fun toString(): String = "AccessTokenWithUserInfo(accessToken=$accessToken, loginId=$loginId, userInfo='*', provider=$provider)"

    override fun equals(other: Any?): Boolean =
        this === other || (
                other is AccessTokenWithUserInfo &&
                        accessToken.token == other.accessToken.token &&
                        loginId == other.loginId &&
                        userInfo == other.userInfo &&
                        provider == other.provider
                )

    override fun hashCode(): Int {
        var result = accessToken.token.hashCode()
        result = 31 * result + loginId.hashCode()
        result = 31 * result + userInfo.hashCode()
        result = 31 * result + provider.hashCode()
        return result
    }
}
