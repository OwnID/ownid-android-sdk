package com.ownid.sdk.internal.feature.webflow

import androidx.annotation.VisibleForTesting
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.JsonSerializable
import com.ownid.sdk.OwnIdProvider
import com.ownid.sdk.OwnIdProviders
import com.ownid.sdk.dsl.AuthResult


@InternalOwnIdAPI
internal class AccountProviderWrapper(
    @get:VisibleForTesting
    internal val provider: OwnIdProvider.AccountProvider,
) : OwnIdFlowWrapper<AuthResult> {
    override suspend fun invoke(payload: OwnIdFlowPayload): AuthResult {
        payload as AccountRegisterEvent.Payload
        return provider.register(payload.loginId, payload.rawProfile, payload.ownIdData, payload.authToken)
    }
}

@InternalOwnIdAPI
internal class SessionProviderWrapper(
    @get:VisibleForTesting
    internal val provider: OwnIdProvider.SessionProvider
) : OwnIdFlowWrapper<AuthResult> {
    override suspend fun invoke(payload: OwnIdFlowPayload): AuthResult {
        payload as SessionCreateEvent.Payload
        return provider.create(payload.loginId, payload.rawSession, payload.authToken, payload.authMethod)
    }
}

@InternalOwnIdAPI
internal class AuthPasswordWrapper(
    @get:VisibleForTesting
    internal val provider: OwnIdProvider.AuthProvider.Password
) : OwnIdFlowWrapper<AuthResult> {
    override suspend fun invoke(payload: OwnIdFlowPayload): AuthResult {
        payload as AuthPasswordEvent.Payload
        return provider.authenticate(payload.loginId, payload.password)
    }
}

@InternalOwnIdAPI
internal fun OwnIdProviders.toWrappers(): List<OwnIdFlowWrapper<JsonSerializable>> = buildList {
    session?.let { provider -> add(SessionProviderWrapper(provider)) }
    account?.let { provider -> add(AccountProviderWrapper(provider)) }
    auth.forEach { provider ->
        when (provider) {
            is OwnIdProvider.AuthProvider.Password -> add(AuthPasswordWrapper(provider))
        }
    }
}