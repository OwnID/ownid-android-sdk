@file:Suppress("unused")

package com.ownid.sdk.ops.impl

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.capability.LoginIdConfigurationProvider
import com.ownid.sdk.capability.LoginIdValidationException
import com.ownid.sdk.capability.LoginIdValidator
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.AssertionOptions
import com.ownid.sdk.model.AssertionResult
import com.ownid.sdk.model.AttestationOptions
import com.ownid.sdk.model.AttestationResult
import com.ownid.sdk.model.AuthenticatorAttachment
import com.ownid.sdk.model.ChallengeId
import com.ownid.sdk.model.Context
import com.ownid.sdk.model.CredentialType
import com.ownid.sdk.model.KeyAlgorithmType
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.LoginIdConfiguration
import com.ownid.sdk.model.OperationChannel
import com.ownid.sdk.model.ProofToken
import com.ownid.sdk.model.Reason
import com.ownid.sdk.model.Timeout
import com.ownid.sdk.model.VerificationChallenge
import com.ownid.sdk.ops.OperationFailure
import com.ownid.sdk.ops.OperationResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent

internal val emailLoginId = LoginId("person@example.com", LoginId.Type.Email)
internal val phoneLoginId = LoginId("+15555550100", LoginId.Type.PhoneNumber)
internal val accessToken = AccessToken("access-token")
internal val contextAccessToken = AccessToken("context-access-token")
internal val proofToken = ProofToken("proof-token")

internal fun accessTokenContext(token: AccessToken = contextAccessToken): Context =
    Context.Builder().apply { authz = com.ownid.sdk.model.Authz.fromToken(token) }.build("test")

internal fun loginIdContext(loginId: LoginId): Context =
    Context.Builder().apply { authz = com.ownid.sdk.model.Authz.start(loginId) }.build("test")

@OptIn(ExperimentalCoroutinesApi::class)
internal fun TestScope.drainOperations() {
    repeat(8) { runCurrent() }
}

internal fun TestScope.operationTestDispatcher(): TestDispatcher = StandardTestDispatcher(testScheduler)

@Suppress("UNCHECKED_CAST")
internal fun <R, F : OperationFailure> OperationResult<R, F>.assertSuccessResult(): R {
    assertThat(this).isInstanceOf(OperationResult.Success::class.java)
    return (this as OperationResult.Success<R>).success
}

@Suppress("UNCHECKED_CAST")
internal fun <R, F : OperationFailure> OperationResult<R, F>.assertFailureResult(): F {
    assertThat(this).isInstanceOf(OperationResult.Failure::class.java)
    return (this as OperationResult.Failure<F>).failure
}

internal fun <R, F : OperationFailure> OperationResult<R, F>.assertCanceledResult(): Reason {
    assertThat(this).isInstanceOf(OperationResult.Canceled::class.java)
    return (this as OperationResult.Canceled).reason
}

internal fun <T> Iterable<T>.assertSingle(): T {
    assertThat(this).hasSize(1)
    return single()
}

internal class TestLoginIdValidator(
    private val supportedTypes: List<LoginId.Type> = listOf(LoginId.Type.Email, LoginId.Type.PhoneNumber, LoginId.Type.UserName),
    private val invalidValues: Set<String> = emptySet()
) : LoginIdValidator {
    override fun determineLoginIdType(loginId: String): LoginId.Type =
        supportedTypes.firstOrNull { type -> type.classificationRegex.matches(loginId) }
            ?: throw LoginIdValidationException.TypeNotSupported("Unsupported LoginId.Type")

    override fun validate(loginId: LoginId): LoginId {
        if (loginId.type !in supportedTypes) throw LoginIdValidationException.TypeNotSupported("Unsupported LoginId.Type: ${loginId.type}")
        val regex = loginId.type.classificationRegex.pattern
        if (loginId.id in invalidValues || !loginId.type.classificationRegex.matches(loginId.id)) {
            throw LoginIdValidationException.ValidationFailed("Invalid LoginId", loginId, regex)
        }
        return loginId
    }
}

internal class TestLoginIdConfigurationProvider(
    initialConfiguration: LoginIdConfiguration = LoginIdConfiguration.DEFAULT_EMAIL
) : LoginIdConfigurationProvider {
    private var currentConfiguration: LoginIdConfiguration = initialConfiguration

    override val configuration: LoginIdConfiguration
        get() = currentConfiguration

    override fun setServerConfiguration(configuration: LoginIdConfiguration) {
        currentConfiguration = configuration
    }

    override fun setConfiguration(configuration: LoginIdConfiguration) {
        currentConfiguration = configuration
    }

    override fun clearConfiguration(): Unit = Unit
}

internal fun assertionOptions(challenge: ChallengeId = ChallengeId("assertion-challenge")): AssertionOptions =
    AssertionOptions(challenge = challenge, rpId = "example.com", timeout = Timeout(60_000))

internal fun assertionResult(id: String = "credential-id"): AssertionResult =
    AssertionResult(
        id = id,
        type = CredentialType.PUBLIC_KEY,
        response = AssertionResult.AuthenticatorResponse(
            clientDataJSON = "client-data",
            authenticatorData = "authenticator-data",
            signature = "signature"
        ),
        authenticatorAttachment = AuthenticatorAttachment.PLATFORM
    )

internal fun attestationOptions(challenge: ChallengeId = ChallengeId("attestation-challenge")): AttestationOptions =
    AttestationOptions(
        rp = AttestationOptions.RelayingParty(id = "example.com", name = "Example"),
        user = AttestationOptions.User(id = "user-id", name = "person@example.com", displayName = "Person"),
        challenge = challenge,
        pubKeyCredParams = listOf(AttestationOptions.PubKeyCredParams(CredentialType.PUBLIC_KEY, KeyAlgorithmType.ES256)),
        timeout = Timeout(60_000)
    )

internal fun attestationResult(id: String = "new-credential-id"): AttestationResult =
    AttestationResult(
        id = id,
        type = CredentialType.PUBLIC_KEY,
        response = AttestationResult.AuthenticatorResponse(
            clientDataJSON = "client-data",
            attestationObject = "attestation-object",
            transports = emptyList()
        ),
        authenticatorAttachment = AuthenticatorAttachment.PLATFORM
    )

internal fun otpChallenge(
    challengeId: ChallengeId = ChallengeId("verification-challenge"),
    channel: OperationChannel = OperationChannel(channel = "user@example.test", id = "channel-id")
): VerificationChallenge =
    VerificationChallenge(
        challengeId = challengeId,
        resendPolicy = VerificationChallenge.ResendPolicy(allow = true, attempts = 3, debounce = 1),
        timeout = Timeout(60_000),
        attempts = 3,
        methods = VerificationChallenge.Methods(otp = VerificationChallenge.Methods.Otp(length = 6), magicLink = null),
        channel = channel
    )
