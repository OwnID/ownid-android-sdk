package com.ownid.sdk.api

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.model.ChallengeId
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.LoginId
import org.junit.Test

// Covers: MODEL-180
internal class ApiFailureValueContractTest {

    @Test
    fun failureScopeExposesStableWireValues() {
        assertThat(ApiFailureScope.DATA.value).isEqualTo("data")
        assertThat(ApiFailureScope.CHANNEL.value).isEqualTo("channel")
        assertThat(ApiFailureScope.SESSION.value).isEqualTo("session")
    }

    @Test
    fun loginFailureBadRequestValuesKeepDiagnosticFieldsAndRedactExtraDetailsFromDescription() {
        val loginId = LoginId("user@example.com", LoginId.Type.Email)
        val failure = DiscoverApiFailure.BadRequest.InvalidLoginId(
            errorCode = ErrorCode.LoginIdValidationFailed,
            message = "invalid login id",
            loginId = loginId,
            regex = ".+@example\\.com"
        )

        assertThat(failure.errorCode).isEqualTo(ErrorCode.LoginIdValidationFailed)
        assertThat(failure.message).isEqualTo("invalid login id")
        assertThat(failure.loginId).isEqualTo(loginId)
        assertThat(failure.regex).isEqualTo(".+@example\\.com")
        assertThat(failure.toString()).isEqualTo("BadRequest.InvalidLoginId(errorCode=login_id_validation_failed, message=invalid login id)")
    }

    @Test
    fun dependencyFailuresKeepCapabilityAndScopeButUseStableDescription() {
        val missingProvider = LoginApiFailure.FailedDependency.MissingProvider(
            errorCode = ErrorCode.MissingCapabilityProvider,
            message = "missing provider",
            capability = "sendSms",
            scope = ApiFailureScope.CHANNEL
        )
        val providerFailed = LoginApiFailure.FailedDependency.ProviderFailed(
            errorCode = ErrorCode.IntegrationError,
            message = "provider failed",
            scope = ApiFailureScope.SESSION
        )

        assertThat(missingProvider.capability).isEqualTo("sendSms")
        assertThat(missingProvider.scope).isEqualTo(ApiFailureScope.CHANNEL)
        assertThat(missingProvider.toString()).isEqualTo(
            "FailedDependency.MissingProvider(errorCode=missing_capability_provider, message=missing provider)"
        )

        assertThat(providerFailed.scope).isEqualTo(ApiFailureScope.SESSION)
        assertThat(providerFailed.toString()).isEqualTo("FailedDependency.ProviderFailed(errorCode=integration_error, message=provider failed)")
    }

    @Test
    fun challengeFailuresKeepChallengeIdButUseStableDescription() {
        val challengeId = ChallengeId("challenge-123")

        val invalidChallenge = OidcCompleteApiFailure.BadRequest.InvalidChallenge(
            errorCode = ErrorCode.InvalidChallenge,
            message = "expired challenge",
            challengeId = challengeId
        )
        val maximumAttemptsReached = EmailVerificationCompleteApiFailure.BadRequest.MaximumAttemptsReached(
            errorCode = ErrorCode.MaximumAttemptsReached,
            message = "too many attempts",
            challengeId = challengeId
        )

        assertThat(invalidChallenge.challengeId).isEqualTo(challengeId)
        assertThat(invalidChallenge.toString()).isEqualTo("BadRequest.InvalidChallenge(errorCode=invalid_challenge, message=expired challenge)")

        assertThat(maximumAttemptsReached.challengeId).isEqualTo(challengeId)
        assertThat(maximumAttemptsReached.toString()).isEqualTo(
            "BadRequest.MaximumAttemptsReached(errorCode=maximum_attempts_reached, message=too many attempts)"
        )
    }

    @Test
    fun directFailuresExposeErrorShapeThroughApiFailureInterface() {
        val failures = listOf(
            PasskeyEnrollApiFailure.BadRequest.InvalidArgument(ErrorCode.InvalidArgument, "invalid enroll"),
            PhoneVerificationStartApiFailure.BadRequest.MissingChannel(
                ErrorCode.MissingChannel,
                "missing channel",
                LoginId("+15551234567", LoginId.Type.PhoneNumber)
            ),
            PasskeyAttestationVerifyApiFailure.Unauthorized(ErrorCode.Unauthorized, "unauthorized"),
            OidcStartApiFailure.MaximumChallengesReached(ErrorCode.MaximumChallengesReached, "busy")
        )

        assertThat(failures.map { it.errorCode }).containsExactly(
            ErrorCode.InvalidArgument,
            ErrorCode.MissingChannel,
            ErrorCode.Unauthorized,
            ErrorCode.MaximumChallengesReached
        ).inOrder()
        assertThat(failures.map { it.message }).containsExactly("invalid enroll", "missing channel", "unauthorized", "busy").inOrder()
        assertThat(failures.map { it.toString() }).containsExactly(
            "BadRequest.InvalidArgument(errorCode=invalid_argument, message=invalid enroll)",
            "BadRequest.MissingChannel(errorCode=missing_channel, message=missing channel)",
            "Unauthorized(errorCode=unauthorized, message=unauthorized)",
            "MaximumChallengesReached(errorCode=maximum_challenges_reached, message=busy)"
        ).inOrder()
    }
}
