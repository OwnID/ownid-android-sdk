package com.ownid.sdk.flow.boost

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.analytics.FlowInfo
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.AuthMethod
import com.ownid.sdk.model.AuthRequirements
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.LoginResponse
import com.ownid.sdk.model.OperationRequirement
import com.ownid.sdk.model.OperationType
import com.ownid.sdk.model.ProofToken
import org.junit.Test

@OptIn(InternalOwnIdAPI::class)
// Covers: FLOW-170, FLOW-180
internal class BoostFlowValueContractTest {

    @Test
    fun contextToStringListsOnlyConfiguredValuesAndShortensRawLoginId() {
        val rawLoginId = "1234567890abcdefghij"
        val context = BoostFlowContext {
            loginId(rawLoginId)
            ignoreLastUser = true
            source = FlowInfo.Source.WIDGET_BUTTON
        }

        assertThat(context.loginId).isNull()
        val diagnostic = context.toString()
        assertThat(diagnostic).isEqualTo(
            "FlowContext(" +
                    "rawLoginId=12345678..[4]...cdefghij, " +
                    "ignoreLastUser=true, " +
                    "source=WIDGET_BUTTON" +
                    ")"
        )
        assertThat(diagnostic).doesNotContain(rawLoginId)
    }

    @Test
    fun contextToStringUsesTypedLoginIdAndOmitsRawLoginId() {
        val context = BoostFlowContext {
            loginId("raw@example.com")
            loginId("person@example.com", LoginId.Type.Email)
            source = FlowInfo.Source.EXPLICIT
        }

        val diagnostic = context.toString()
        assertThat(diagnostic).isEqualTo(
            "FlowContext(loginId=LoginId(id='p****n@example.com', type=Email), source=EXPLICIT)"
        )
        assertThat(diagnostic).doesNotContain("rawLoginId=")
        assertThat(diagnostic).doesNotContain("person@example.com")
    }

    @Test
    fun contextToStringKeepsEmptyContextCompact() {
        assertThat(BoostFlowContext.Empty.toString()).isEqualTo("FlowContext()")
    }

    @Test
    fun contextToStringIncludesOnlyNonEmptyAllowedAuthOperations() {
        val configured = BoostFlowContext {
            allowedAuthOperations = setOf(
                BoostLoginAuthOperation.EMAIL_VERIFICATION,
                BoostLoginAuthOperation.PHONE_NUMBER_VERIFICATION
            )
        }
        val explicitlyEmpty = BoostFlowContext {
            allowedAuthOperations = emptySet()
        }

        assertThat(configured.toString()).contains(
            "allowedAuthOperations=[EMAIL_VERIFICATION, PHONE_NUMBER_VERIFICATION]"
        )
        assertThat(explicitlyEmpty.toString()).doesNotContain("allowedAuthOperations")
    }

    @Test
    fun contextToStringListsAllConfiguredFieldsAndRedactsSensitiveValues() {
        val rawLoginId = "1234567890abcdefghij"
        val secret = "abcdefghij1234567890"
        val context = BoostFlowContext {
            loginId(rawLoginId)
            accessToken = AccessToken(secret)
            ignoreLastUser = true
            source = FlowInfo.Source.WIDGET_BUTTON
            proofToken = ProofToken(secret)
            ownIdData = secret
            sessionPayload = secret
            authMethod = AuthMethod.Passkey
            traceParent = "00-trace-parent"
            authRequiredResponse = LoginResponse.AuthRequired(
                authRequirements = AuthRequirements(
                    targetScore = 10,
                    operations = listOf(OperationRequirement(OperationType.PASSKEY_AUTH, score = 10))
                ),
                reason = "more-auth"
            )
            requestedOps = linkedMapOf(OperationType.PASSKEY_AUTH to true)
        }

        val diagnostic = context.toString()

        assertThat(diagnostic).startsWith("FlowContext(")
        assertThat(diagnostic).endsWith(")")
        assertThat(diagnostic).contains("rawLoginId=12345678..[4]...cdefghij")
        assertThat(diagnostic).contains("accessToken=AccessToken(token=abcdefgh..[4]...34567890)")
        assertThat(diagnostic).contains("ignoreLastUser=true")
        assertThat(diagnostic).contains("source=WIDGET_BUTTON")
        assertThat(diagnostic).contains("proofToken=ProofToken(token=abcdefgh..[4]...34567890)")
        assertThat(diagnostic).contains("ownIdData=abcdefgh..[4]...34567890")
        assertThat(diagnostic).contains("sessionPayload=abcdefgh..[4]...34567890")
        assertThat(diagnostic).contains("authMethod=passkey")
        assertThat(diagnostic).contains("traceParent=00-trace-parent")
        assertThat(diagnostic).contains(
            "authRequiredResponse=AuthRequired(AuthRequirements(targetScore=10, operations=[PasskeyAuth(score=10, channels=[null])]), reason=more-auth)"
        )
        assertThat(diagnostic).contains("requestedOps={PasskeyAuth=true}")
        assertThat(diagnostic).doesNotContain(rawLoginId)
        assertThat(diagnostic).doesNotContain(secret)
    }

    @Test
    fun responseToStringRedactsSensitiveValues() {
        val rawToken = "1234567890abcdefghij"
        val loginResponse = BoostFlow.Response.Login(
            loginId = LoginId("person@example.com", LoginId.Type.Email),
            authMethod = AuthMethod.Otp,
            accessToken = AccessToken(rawToken),
            sessionPayload = """{"session":"secret"}""",
            session = "host-session"
        )
        val createPasskeyResponse = BoostFlow.Response.CreatePasskey(
            loginId = LoginId("person@example.com", LoginId.Type.Email),
            proofToken = ProofToken(rawToken),
            ownIdData = """{"own":"secret"}"""
        )

        assertThat(loginResponse.toString()).isEqualTo(
            "Login(" +
                    "loginId=LoginId(id='p****n@example.com', type=Email), " +
                    "authMethod=otp, " +
                    "accessToken=AccessToken(token=12345678..[4]...cdefghij), " +
                    "sessionPayload='*', " +
                    "session='*'" +
                    ")"
        )
        assertThat(loginResponse.toString()).doesNotContain(rawToken)
        assertThat(loginResponse.toString()).doesNotContain("host-session")
        assertThat(createPasskeyResponse.toString()).isEqualTo(
            "CreatePasskey(" +
                    "loginId=LoginId(id='p****n@example.com', type=Email), " +
                    "proofToken=ProofToken(token=12345678..[4]...cdefghij), " +
                    "ownIdData='*'" +
                    ")"
        )
        assertThat(createPasskeyResponse.toString()).doesNotContain(rawToken)
        assertThat(createPasskeyResponse.toString()).doesNotContain("secret")
    }

    @Test
    fun createPasskeyOwnIdDataForReturnsDataOnlyForMatchingCurrentLoginId() {
        val response = BoostFlow.Response.CreatePasskey(
            loginId = LoginId("person@example.com", LoginId.Type.Email),
            proofToken = ProofToken("proof-token"),
            ownIdData = """{"own":"data"}"""
        )

        assertThat(response.ownIdDataFor("person@example.com")).isEqualTo("""{"own":"data"}""")
        assertThat(response.ownIdDataFor("  person@example.com\n")).isEqualTo("""{"own":"data"}""")
        assertThat(response.ownIdDataFor(null)).isNull()
        assertThat(response.ownIdDataFor("")).isNull()
        assertThat(response.ownIdDataFor("   \n\t")).isNull()
        assertThat(response.ownIdDataFor("other@example.com")).isNull()

        val responseWithoutOwnIdData = BoostFlow.Response.CreatePasskey(
            loginId = LoginId("person@example.com", LoginId.Type.Email),
            proofToken = null,
            ownIdData = null
        )

        assertThat(responseWithoutOwnIdData.ownIdDataFor("person@example.com")).isNull()
    }

    @Test
    fun failureToStringKeepsStableCategoryCodeAndMessage() {
        assertThat(
            BoostLoginFlowFailure.Input.UnresolvedLoginId(
                errorCode = ErrorCode.InvalidArgument,
                message = "bad login ID"
            ).toString()
        ).isEqualTo("Input.UnresolvedLoginId(errorCode=invalid_argument, message=bad login ID)")
        assertThat(
            BoostLoginFlowFailure.Account.Blocked(
                errorCode = ErrorCode.UserBlocked,
                message = "blocked"
            ).toString()
        ).isEqualTo("Account.Blocked(errorCode=user_blocked, message=blocked)")
        assertThat(
            BoostLoginFlowFailure.Account.NotFound(
                errorCode = ErrorCode.UserNotFound,
                message = "missing"
            ).toString()
        ).isEqualTo("Account.NotFound(errorCode=user_not_found, message=missing)")
        assertThat(
            BoostLoginFlowFailure.InsufficientAuth(
                errorCode = ErrorCode.Unknown,
                message = "no operation"
            ).toString()
        ).isEqualTo("InsufficientAuth(errorCode=unknown, message=no operation)")
        assertThat(
            BoostLoginFlowFailure.SessionCreationFailed(
                errorCode = ErrorCode.IntegrationError,
                message = "session rejected"
            ).toString()
        ).isEqualTo("SessionCreationFailed(errorCode=integration_error, message=session rejected)")
        assertThat(
            BoostLoginFlowFailure.OperationFailed(
                operationType = OperationType.PASSKEY_AUTH,
                errorCode = ErrorCode.PasskeysNotSupported,
                message = "unavailable"
            ).toString()
        ).isEqualTo("OperationFailed(errorCode=passkeys_not_supported, message=unavailable)")
        assertThat(
            BoostLoginFlowFailure.Unexpected(
                errorCode = ErrorCode.Unknown,
                message = "invariant"
            ).toString()
        ).isEqualTo("Unexpected(errorCode=unknown, message=invariant)")

        assertThat(
            BoostCreatePasskeyFlowFailure.Input.UnresolvedLoginId(
                errorCode = ErrorCode.InvalidArgument,
                message = "bad login ID"
            ).toString()
        ).isEqualTo("Input.UnresolvedLoginId(errorCode=invalid_argument, message=bad login ID)")
        assertThat(
            BoostCreatePasskeyFlowFailure.Account.Blocked(
                errorCode = ErrorCode.UserBlocked,
                message = "blocked"
            ).toString()
        ).isEqualTo("Account.Blocked(errorCode=user_blocked, message=blocked)")
        assertThat(
            BoostCreatePasskeyFlowFailure.Account.NotFound(
                errorCode = ErrorCode.UserNotFound,
                message = "missing"
            ).toString()
        ).isEqualTo("Account.NotFound(errorCode=user_not_found, message=missing)")
        assertThat(
            BoostCreatePasskeyFlowFailure.InsufficientAuth(
                errorCode = ErrorCode.Unknown,
                message = "no operation"
            ).toString()
        ).isEqualTo("InsufficientAuth(errorCode=unknown, message=no operation)")
        assertThat(
            BoostCreatePasskeyFlowFailure.SessionCreationFailed(
                errorCode = ErrorCode.IntegrationError,
                message = "session rejected"
            ).toString()
        ).isEqualTo("SessionCreationFailed(errorCode=integration_error, message=session rejected)")
        assertThat(
            BoostCreatePasskeyFlowFailure.OperationFailed(
                operationType = OperationType.PASSKEY_CREATION,
                errorCode = ErrorCode.PasskeyNotCreated,
                message = "creation failed"
            ).toString()
        ).isEqualTo("OperationFailed(errorCode=passkey_not_created, message=creation failed)")
        assertThat(
            BoostCreatePasskeyFlowFailure.Unexpected(
                errorCode = ErrorCode.Unknown,
                message = "invariant"
            ).toString()
        ).isEqualTo("Unexpected(errorCode=unknown, message=invariant)")
    }
}
