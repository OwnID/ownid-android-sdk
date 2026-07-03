package com.ownid.sdk.model

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.flow.FlowFailure
import com.ownid.sdk.flow.FlowResult
import com.ownid.sdk.flow.errorOrNull
import com.ownid.sdk.flow.fold
import com.ownid.sdk.flow.getOrNull
import com.ownid.sdk.flow.map
import com.ownid.sdk.flow.onCanceled
import com.ownid.sdk.flow.onError
import com.ownid.sdk.flow.onSuccess
import com.ownid.sdk.flow.reasonOrNull
import com.ownid.sdk.ops.OperationFailure
import com.ownid.sdk.ops.OperationResult
import com.ownid.sdk.ops.errorOrNull
import com.ownid.sdk.ops.fold
import com.ownid.sdk.ops.getOrNull
import com.ownid.sdk.ops.map
import com.ownid.sdk.ops.onCanceled
import com.ownid.sdk.ops.onError
import com.ownid.sdk.ops.onSuccess
import com.ownid.sdk.ops.reasonOrNull
import org.junit.Test

// Covers: MODEL-080, MODEL-090, MODEL-100, MODEL-120, LOC-040
internal class OperationErrorValueContractTest {

    @Test
    fun availabilityCallbacksRunOnlyForMatchingSubtypeAndReturnOriginalValue() {
        val available = Availability.Available()
        val unavailable = Availability.Unavailable("missing provider")
        var availableCalls = 0
        val unavailableMessages = mutableListOf<String>()

        assertThat(available.onAvailable { availableCalls += 1 }).isSameInstanceAs(available)
        assertThat(available.onUnavailable { unavailableMessages += it }).isSameInstanceAs(available)
        assertThat(unavailable.onAvailable { availableCalls += 10 }).isSameInstanceAs(unavailable)
        assertThat(unavailable.onUnavailable { unavailableMessages += it }).isSameInstanceAs(unavailable)

        assertThat(availableCalls).isEqualTo(1)
        assertThat(unavailableMessages).containsExactly("missing provider")
    }

    @Test
    fun reasonsExposeStableCategoriesAndDiagnosticDetails() {
        assertThat(Reason.Timeout().toString()).isEqualTo("timeout")
        assertThat(Reason.UserClose().toString()).isEqualTo("userClose")
        assertThat(Reason.UserClose("").toString()).isEqualTo("userClose")
        assertThat(Reason.MoveToOtherChallenge().toString()).isEqualTo("moveToOtherChallenge")
        assertThat(Reason.SystemError().toString()).isEqualTo("systemError")
        assertThat(Reason.Unknown().toString()).isEqualTo("unknown")
        assertThat(Reason.AlreadyExists().toString()).isEqualTo("alreadyExists")

        val userClose = Reason.UserClose("dismissed")
        val systemError = Reason.SystemError("boom")
        val unknown = Reason.Unknown("future")

        assertThat(userClose.details).isEqualTo("dismissed")
        assertThat(userClose.toString()).contains("userClose")
        assertThat(userClose.toString()).contains("dismissed")
        assertThat(systemError.details).isEqualTo("boom")
        assertThat(systemError.toString()).contains("systemError")
        assertThat(systemError.toString()).contains("boom")
        assertThat(unknown.details).isEqualTo("future")
        assertThat(unknown.toString()).contains("unknown")
        assertThat(unknown.toString()).contains("future")
    }

    @Test
    fun operationResultHelpersKeepSuccessCanceledAndFailureValuesDistinct() {
        val success: OperationResult<String, TestOperationFailure> = OperationResult.Success("payload")
        val reason = Reason.UserClose("dismissed")
        val canceled: OperationResult<String, TestOperationFailure> = OperationResult.Canceled(reason)
        val failurePayload = TestOperationFailure(ErrorCode.Network, "offline")
        val failure: OperationResult<String, TestOperationFailure> = OperationResult.Failure(failurePayload)
        val calls = mutableListOf<String>()
        val canceledReasons = mutableListOf<Reason>()

        success.onSuccess { calls += "success:$it" }.onCanceled { canceledReasons += it }.onError { calls += "failure:${it.message}" }
        canceled.onSuccess { calls += "unexpected:$it" }.onCanceled { canceledReasons += it }.onError { calls += "unexpected:${it.message}" }
        failure.onSuccess { calls += "unexpected:$it" }.onCanceled { canceledReasons += it }.onError { calls += "failure:${it.message}" }

        assertThat(calls).containsExactly("success:payload", "failure:offline").inOrder()
        assertThat(canceledReasons).containsExactly(reason)
        assertThat(success.fold(onSuccess = { it }, onCanceled = { "unexpected" }, onError = { "unexpected" })).isEqualTo("payload")
        val foldedCanceled: Any = canceled.fold(onSuccess = { "unexpected" }, onCanceled = { it }, onError = { "unexpected" })
        assertThat(foldedCanceled).isSameInstanceAs(reason)
        assertThat(failure.fold(onSuccess = { "unexpected" }, onCanceled = { "unexpected" }, onError = { it.message })).isEqualTo("offline")
        assertThat(success.getOrNull()).isEqualTo("payload")
        assertThat(success.reasonOrNull()).isNull()
        assertThat(success.errorOrNull()).isNull()
        assertThat(canceled.getOrNull()).isNull()
        assertThat(canceled.reasonOrNull()).isSameInstanceAs(reason)
        assertThat(canceled.errorOrNull()).isNull()
        assertThat(failure.getOrNull()).isNull()
        assertThat(failure.reasonOrNull()).isNull()
        assertThat(failure.errorOrNull()).isSameInstanceAs(failurePayload)
        assertThat(success.map { it.length }.getOrNull()).isEqualTo(7)
        assertThat(canceled.map { it.length }).isSameInstanceAs(canceled)
        assertThat(failure.map { it.length }).isSameInstanceAs(failure)
    }

    @Test
    fun flowResultHelpersKeepSuccessCanceledAndFailureValuesDistinct() {
        val success: FlowResult<String, TestFlowFailure> = FlowResult.Success("payload")
        val reason = Reason.Timeout()
        val canceled: FlowResult<String, TestFlowFailure> = FlowResult.Canceled(reason)
        val failurePayload = TestFlowFailure(ErrorCode.Forbidden, "denied")
        val failure: FlowResult<String, TestFlowFailure> = FlowResult.Failure(failurePayload)
        val calls = mutableListOf<String>()

        success.onSuccess { calls += "success:$it" }.onCanceled { calls += "canceled:$it" }.onError { calls += "failure:${it.message}" }
        canceled.onSuccess { calls += "unexpected:$it" }.onCanceled { calls += "canceled:$it" }.onError { calls += "unexpected:${it.message}" }
        failure.onSuccess { calls += "unexpected:$it" }.onCanceled { calls += "unexpected:$it" }.onError { calls += "failure:${it.message}" }

        assertThat(calls).containsExactly("success:payload", "canceled:timeout", "failure:denied").inOrder()
        assertThat(success.fold(onSuccess = { it }, onCanceled = { "unexpected" }, onError = { "unexpected" })).isEqualTo("payload")
        assertThat(canceled.fold(onSuccess = { "unexpected" }, onCanceled = { it.toString() }, onError = { "unexpected" })).isEqualTo("timeout")
        assertThat(failure.fold(onSuccess = { "unexpected" }, onCanceled = { "unexpected" }, onError = { it.message })).isEqualTo("denied")
        assertThat(success.getOrNull()).isEqualTo("payload")
        assertThat(success.reasonOrNull()).isNull()
        assertThat(success.errorOrNull()).isNull()
        assertThat(canceled.getOrNull()).isNull()
        assertThat(canceled.reasonOrNull()).isSameInstanceAs(reason)
        assertThat(canceled.errorOrNull()).isNull()
        assertThat(failure.getOrNull()).isNull()
        assertThat(failure.reasonOrNull()).isNull()
        assertThat(failure.errorOrNull()).isSameInstanceAs(failurePayload)
        assertThat(success.map { it.length }.getOrNull()).isEqualTo(7)
        assertThat(canceled.map { it.length }).isSameInstanceAs(canceled)
        assertThat(failure.map { it.length }).isSameInstanceAs(failure)
    }

    @Test
    fun operationTypesExposeOwnIdValuesAndOperationIdsCompareByTypeAndId() {
        val expectedValues = mapOf(
            OperationType.LOGIN_ID_COLLECT to "LoginIdCollect",
            OperationType.EMAIL_VERIFICATION to "EmailVerification",
            OperationType.EMAIL_ENROLLMENT to "EmailEnrollment",
            OperationType.PHONE_NUMBER_VERIFICATION to "PhoneNumberVerification",
            OperationType.PHONE_NUMBER_ENROLLMENT to "PhoneNumberEnrollment",
            OperationType.PASSKEY_CREATION to "PasskeyCreation",
            OperationType.PASSKEY_AUTH to "PasskeyAuth",
            OperationType.PASSKEY_ENROLLMENT to "PasskeyEnrollment",
            OperationType.SESSION_CREATION to "SessionCreation",
            OperationType.DEFERRED_AUTHENTICATION to "DeferredAuthentication",
            OperationType.EXTERNAL_AUTHENTICATION to "ExternalAuthentication",
            OperationType.PROFILE_COLLECTION to "ProfileCollection",
            OperationType.PASSWORD_AUTHENTICATION to "PasswordAuthentication",
            OperationType.OIDC_AUTHENTICATION_APPLE to "OidcAuthenticationApple",
            OperationType.OIDC_AUTHENTICATION_GOOGLE to "OidcAuthenticationGoogle",
            OperationType.REGISTRATION to "Registration",
            OperationType.PROFILE_UPDATE to "ProfileUpdate",
            OperationType.SESSION_MANAGEMENT to "SessionManagement",
            OperationType.WEB_BRIDGE to "WebBridge",
            OperationType.FACE_KEY_VERIFICATION to "FaceKeyVerification",
            OperationType.FACE_KEY_CREATION to "FaceKeyCreation",
            OperationType.FACE_KEY_ENROLLMENT to "FaceKeyEnrollment"
        )

        assertThat(OperationType.values().associateWith(OperationType::value)).containsExactlyEntriesIn(expectedValues)
        expectedValues.forEach { (type, value) ->
            assertThat(type.toString()).isEqualTo(value)
        }

        val operationId = OperationID(OperationType.PASSKEY_AUTH, "operation-1")
        val sameOperationId = OperationID(OperationType.PASSKEY_AUTH, "operation-1")

        assertThat(operationId).isEqualTo(sameOperationId)
        assertThat(operationId.hashCode()).isEqualTo(sameOperationId.hashCode())
        assertThat(operationId).isNotEqualTo(OperationID(OperationType.PASSKEY_CREATION, "operation-1"))
        assertThat(operationId).isNotEqualTo(OperationID(OperationType.PASSKEY_AUTH, "operation-2"))
        assertThat(operationId.toString()).isEqualTo("PasskeyAuth:operation-1")
    }

    @Test
    fun operationRequirementKeepsScoreChannelsAndDiagnosticText() {
        val channel = OperationChannel("person@example.com", "email-channel")
        val requirement = OperationRequirement(OperationType.EMAIL_VERIFICATION, score = -1, channels = listOf(channel))

        assertThat(requirement.type).isEqualTo(OperationType.EMAIL_VERIFICATION)
        assertThat(requirement.score).isEqualTo(-1)
        assertThat(requirement.channels).containsExactly(channel)
        assertThat(requirement.toString()).isEqualTo("EmailVerification(score=-1, channels=[OperationChannel(channel='*', id='email-channel')])")
        assertThat(requirement.toString()).doesNotContain("person@example.com")

        val noChannels = OperationRequirement(OperationType.PASSKEY_AUTH, score = 2)
        assertThat(noChannels.toString()).isEqualTo("PasskeyAuth(score=2, channels=[null])")
    }

    @Test
    fun authRequirementsUseSourceDeclaredScoreAchievabilityRule() {
        assertThat(AuthRequirements(targetScore = 0, operations = emptyList()).isTargetScoreAchievable()).isTrue()
        assertThat(AuthRequirements(targetScore = -1, operations = emptyList()).isTargetScoreAchievable()).isTrue()
        assertThat(AuthRequirements(targetScore = 2, operations = emptyList()).isTargetScoreAchievable()).isFalse()

        val requirements = AuthRequirements(
            targetScore = 3,
            operations = listOf(
                OperationRequirement(OperationType.PASSKEY_AUTH, score = 1),
                OperationRequirement(OperationType.EMAIL_VERIFICATION, score = 2)
            )
        )

        assertThat(requirements.isTargetScoreAchievable()).isTrue()
        assertThat(requirements.toString()).isEqualTo(
            "AuthRequirements(targetScore=3, operations=[PasskeyAuth(score=1, channels=[null]), EmailVerification(score=2, channels=[null])])"
        )
    }

    @Test
    fun errorCodesExposeStableValuesAndDefaultStringsResolveByCode() {
        val expectedValues = mapOf(
            ErrorCode.Aborted to "aborted",
            ErrorCode.CancelNotSupported to "cancel_not_supported",
            ErrorCode.DeviceNotSupported to "device_not_supported",
            ErrorCode.DomElementNotFound to "dom_element_not_found",
            ErrorCode.EmptyLoginId to "empty_login_id",
            ErrorCode.Forbidden to "forbidden",
            ErrorCode.IntegrationError to "integration_error",
            ErrorCode.InvalidArgument to "invalid_argument",
            ErrorCode.InvalidChallenge to "invalid_challenge",
            ErrorCode.LoginIdTypeNotSupported to "login_id_type_not_supported",
            ErrorCode.LoginIdValidationFailed to "login_id_validation_failed",
            ErrorCode.LoginWithPasswordFailed to "login_with_password_failed",
            ErrorCode.MaximumAttemptsReached to "maximum_attempts_reached",
            ErrorCode.MaximumChallengesReached to "maximum_challenges_reached",
            ErrorCode.MaximumResendAttemptsReached to "maximum_resend_attempts_reached",
            ErrorCode.MissingCapabilityProvider to "missing_capability_provider",
            ErrorCode.MissingChannel to "missing_channel",
            ErrorCode.Network to "network",
            ErrorCode.NoApplicablePasskeys to "no_applicable_passkeys",
            ErrorCode.NotificationBlocked to "notification_blocked",
            ErrorCode.OidcFailed to "oidc_failed",
            ErrorCode.PasskeyAlreadyRegistered to "passkey_already_registered",
            ErrorCode.PasskeyNotCreated to "passkey_not_created",
            ErrorCode.PasskeysNotSupported to "passkeys_not_supported",
            ErrorCode.ScreensNotReady to "screens_not_ready",
            ErrorCode.SessionNotEstablished to "session_not_established",
            ErrorCode.Timeout to "timeout",
            ErrorCode.Unauthorized to "unauthorized",
            ErrorCode.Unknown to "unknown",
            ErrorCode.UserBlocked to "user_blocked",
            ErrorCode.UserChanged to "user_changed",
            ErrorCode.UserNotFound to "user_not_found",
            ErrorCode.VerificationCodeWrong to "verification_code_wrong",
            ErrorCode.WidgetAlreadyExists to "widget_already_exists"
        )

        assertThat(ErrorCode.values().associateWith(ErrorCode::value)).containsExactlyEntriesIn(expectedValues)
        expectedValues.forEach { (code, value) ->
            assertThat(code.toString()).isEqualTo(value)
            assertThat(ErrorStrings.DEFAULT.getString(code)).isNotEmpty()
            assertThat(code.resolveLocalizedMessage(ErrorStrings.DEFAULT)).isEqualTo(ErrorStrings.DEFAULT.getString(code))
        }
    }

    @Test
    fun uiErrorAndErrorStringsCompareByDisplayedValues() {
        val first = UiError(ErrorCode.Network, "Offline")
        val same = UiError(ErrorCode.Network, "Offline")

        assertThat(first).isEqualTo(same)
        assertThat(first.hashCode()).isEqualTo(same.hashCode())
        assertThat(first).isNotEqualTo(UiError(ErrorCode.Unknown, "Offline"))
        assertThat(first).isNotEqualTo(UiError(ErrorCode.Network, "Different"))
        assertThat(first.toString()).isEqualTo("UiError(errorCode=network, localizedMessage=Offline)")
        assertThat(ErrorCode.Network.toUiError(ErrorStrings.DEFAULT)).isEqualTo(UiError(ErrorCode.Network, ErrorStrings.DEFAULT.network))

        val custom = errorStrings("!")
        val sameCustom = errorStrings("!")
        assertThat(custom).isEqualTo(sameCustom)
        assertThat(custom.hashCode()).isEqualTo(sameCustom.hashCode())
        assertThat(custom).isNotEqualTo(errorStrings("?"))
        assertThat(custom.getString(ErrorCode.Aborted)).isEqualTo("aborted!")
        assertThat(custom.getString(ErrorCode.WidgetAlreadyExists)).isEqualTo("widgetAlreadyExists!")
    }

    private fun errorStrings(suffix: String): ErrorStrings = ErrorStrings(
        aborted = "aborted$suffix",
        cancelNotSupported = "cancelNotSupported$suffix",
        deviceNotSupported = "deviceNotSupported$suffix",
        domElementNotFound = "domElementNotFound$suffix",
        emptyLoginId = "emptyLoginId$suffix",
        forbidden = "forbidden$suffix",
        integrationError = "integrationError$suffix",
        invalidArgument = "invalidArgument$suffix",
        invalidChallenge = "invalidChallenge$suffix",
        loginIdTypeNotSupported = "loginIdTypeNotSupported$suffix",
        loginIdValidationFailed = "loginIdValidationFailed$suffix",
        loginWithPasswordFailed = "loginWithPasswordFailed$suffix",
        maximumAttemptsReached = "maximumAttemptsReached$suffix",
        maximumChallengesReached = "maximumChallengesReached$suffix",
        maximumResendAttemptsReached = "maximumResendAttemptsReached$suffix",
        missingCapabilityProvider = "missingCapabilityProvider$suffix",
        missingChannel = "missingChannel$suffix",
        network = "network$suffix",
        noApplicablePasskeys = "noApplicablePasskeys$suffix",
        notificationBlocked = "notificationBlocked$suffix",
        oidcFailed = "oidcFailed$suffix",
        passkeyAlreadyRegistered = "passkeyAlreadyRegistered$suffix",
        passkeyNotCreated = "passkeyNotCreated$suffix",
        passkeysNotSupported = "passkeysNotSupported$suffix",
        screensNotReady = "screensNotReady$suffix",
        sessionNotEstablished = "sessionNotEstablished$suffix",
        timeout = "timeout$suffix",
        unauthorized = "unauthorized$suffix",
        unknown = "unknown$suffix",
        userBlocked = "userBlocked$suffix",
        userChanged = "userChanged$suffix",
        userNotFound = "userNotFound$suffix",
        verificationCodeWrong = "verificationCodeWrong$suffix",
        widgetAlreadyExists = "widgetAlreadyExists$suffix"
    )

    private data class TestOperationFailure(override val errorCode: ErrorCode, override val message: String) : OperationFailure

    private data class TestFlowFailure(override val errorCode: ErrorCode, override val message: String) : FlowFailure
}
