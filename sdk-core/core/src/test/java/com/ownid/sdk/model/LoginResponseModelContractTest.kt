package com.ownid.sdk.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

// Covers: MODEL-070
internal class LoginResponseModelContractTest {

    @Test
    fun authRequirementsKeepPublicValuesOperationOrderAndAchievability() {
        val emailChannel = OperationChannel(channel = "person@example.com", id = "email-channel")
        val emailRequirement = OperationRequirement(
            type = OperationType.EMAIL_VERIFICATION,
            score = 3,
            channels = listOf(emailChannel)
        )
        val passkeyRequirement = OperationRequirement(
            type = OperationType.PASSKEY_AUTH,
            score = 7,
            channels = emptyList()
        )
        val operations = listOf(emailRequirement, passkeyRequirement)
        val requirements = AuthRequirements(
            targetScore = 10,
            operations = operations
        )

        assertThat(requirements.targetScore).isEqualTo(10)
        assertThat(requirements.operations).containsExactlyElementsIn(operations).inOrder()
        assertThat(requirements.operations.map { it.type.value }).containsExactly("EmailVerification", "PasskeyAuth").inOrder()
        assertThat(requirements.operations.map { it.score }).containsExactly(3, 7).inOrder()
        assertThat(requirements.operations[0].channels).containsExactly(emailChannel)
        assertThat(requirements.operations[1].channels).isEmpty()
        assertThat(requirements.isTargetScoreAchievable()).isTrue()
        assertThat(AuthRequirements(targetScore = 11, operations = requirements.operations).isTargetScoreAchievable()).isFalse()
        assertThat(AuthRequirements(targetScore = 0, operations = emptyList()).isTargetScoreAchievable()).isTrue()
        assertThat(AuthRequirements(targetScore = -1, operations = emptyList()).isTargetScoreAchievable()).isTrue()
        assertThat(AuthRequirements(targetScore = 1, operations = emptyList()).isTargetScoreAchievable()).isFalse()
        assertThat(
            AuthRequirements(
                targetScore = Int.MAX_VALUE,
                operations = listOf(
                    OperationRequirement(OperationType.PASSKEY_AUTH, Int.MAX_VALUE),
                    OperationRequirement(OperationType.EMAIL_VERIFICATION, 1)
                )
            ).isTargetScoreAchievable()
        ).isTrue()
        assertThat(
            AuthRequirements(
                targetScore = 10,
                operations = listOf(
                    OperationRequirement(OperationType.PASSKEY_AUTH, 3),
                    OperationRequirement(OperationType.EMAIL_VERIFICATION, -1),
                    OperationRequirement(OperationType.PHONE_NUMBER_VERIFICATION, 7)
                )
            ).isTargetScoreAchievable()
        ).isTrue()
        val requirementsDiagnostic = requirements.toString()
        val expectedRequirementsDiagnostic =
            "AuthRequirements(targetScore=10, operations=[EmailVerification(score=3, channels=[OperationChannel(channel='*', id='email-channel')]), " +
                    "PasskeyAuth(score=7, channels=[])])"

        assertThat(requirementsDiagnostic).isEqualTo(expectedRequirementsDiagnostic)
        assertThat(requirementsDiagnostic).doesNotContain("person@example.com")
    }

    @Test
    fun authRequirementsUseAllFieldsForOrderedStructuralEqualityAndHashing() {
        val requirements = AuthRequirements(
            targetScore = 3,
            operations = listOf(
                OperationRequirement(OperationType.PASSKEY_AUTH, score = 1),
                OperationRequirement(OperationType.EMAIL_VERIFICATION, score = 2)
            )
        )
        val equalRequirements = AuthRequirements(
            targetScore = 3,
            operations = listOf(
                OperationRequirement(OperationType.PASSKEY_AUTH, score = 1),
                OperationRequirement(OperationType.EMAIL_VERIFICATION, score = 2)
            )
        )

        assertEqualAndSameHash(requirements, equalRequirements)
        assertThat(requirements).isNotEqualTo(AuthRequirements(targetScore = 4, operations = requirements.operations))
        assertThat(requirements).isNotEqualTo(AuthRequirements(targetScore = 3, operations = requirements.operations.dropLast(1)))
        assertThat(requirements).isNotEqualTo(AuthRequirements(targetScore = 3, operations = requirements.operations.reversed()))
    }

    @Test
    fun loginResponseVariantsExposePublicValuesAndRedactedDiagnostics() {
        val rawToken = "1234567890ABCDEFGHIJ"
        val accessToken = AccessToken(rawToken)
        val success = LoginResponse.Success(accessToken, sessionPayload = """{"session":"secret"}""")
        val authRequired = LoginResponse.AuthRequired(
            authRequirements = AuthRequirements(
                targetScore = 1,
                operations = listOf(OperationRequirement(OperationType.EMAIL_VERIFICATION, score = 1))
            ),
            reason = "more-auth"
        )
        val accountNotFound = LoginResponse.AccountNotFound("missing")
        val accountBlocked = LoginResponse.AccountBlocked("blocked")

        assertThat(success.accessToken).isEqualTo(accessToken)
        assertThat(success.sessionPayload).isEqualTo("""{"session":"secret"}""")
        val successDiagnostic = success.toString()
        assertThat(successDiagnostic).isEqualTo("Success(accessToken=AccessToken(token=12345678..[4]...CDEFGHIJ), sessionPayload='*')")
        assertThat(successDiagnostic).doesNotContain(rawToken)
        assertThat(successDiagnostic).doesNotContain("""{"session":"secret"}""")
        assertThat(successDiagnostic).doesNotContain("secret")

        assertThat(authRequired).isInstanceOf(LoginResponse.NoSession::class.java)
        assertThat(authRequired.authRequirements.targetScore).isEqualTo(1)
        assertThat(authRequired.reason).isEqualTo("more-auth")
        assertThat(authRequired.toString()).isEqualTo(
            "AuthRequired(AuthRequirements(targetScore=1, operations=[EmailVerification(score=1, channels=[null])]), reason=more-auth)"
        )

        assertThat(accountNotFound).isInstanceOf(LoginResponse.NoSession::class.java)
        assertThat(accountNotFound.reason).isEqualTo("missing")
        assertThat(accountNotFound.toString()).isEqualTo("AccountNotFound(reason=missing)")

        assertThat(accountBlocked).isInstanceOf(LoginResponse.NoSession::class.java)
        assertThat(accountBlocked.reason).isEqualTo("blocked")
        assertThat(accountBlocked.toString()).isEqualTo("AccountBlocked(reason=blocked)")
    }

    @Test
    fun loginResponseVariantsUseAllFieldsAndLeafTypesForStructuralEqualityAndHashing() {
        val success = LoginResponse.Success(AccessToken("access-token"), sessionPayload = "session-payload")
        assertEqualAndSameHash(
            success,
            LoginResponse.Success(AccessToken("access-token"), sessionPayload = "session-payload")
        )
        assertThat(success).isNotEqualTo(LoginResponse.Success(AccessToken("other-token"), sessionPayload = "session-payload"))
        assertThat(success).isNotEqualTo(LoginResponse.Success(AccessToken("access-token"), sessionPayload = "other-payload"))

        val authRequirements = AuthRequirements(
            targetScore = 1,
            operations = listOf(OperationRequirement(OperationType.EMAIL_VERIFICATION, score = 1))
        )
        val authRequired = LoginResponse.AuthRequired(authRequirements, reason = null)
        assertEqualAndSameHash(
            authRequired,
            LoginResponse.AuthRequired(
                AuthRequirements(
                    targetScore = 1,
                    operations = listOf(OperationRequirement(OperationType.EMAIL_VERIFICATION, score = 1))
                ),
                reason = null
            )
        )
        assertThat(authRequired).isNotEqualTo(
            LoginResponse.AuthRequired(AuthRequirements(targetScore = 2, operations = authRequirements.operations), reason = null)
        )
        assertThat(authRequired).isNotEqualTo(LoginResponse.AuthRequired(authRequirements, reason = "more-auth"))

        val accountNotFound = LoginResponse.AccountNotFound("same-reason")
        assertEqualAndSameHash(accountNotFound, LoginResponse.AccountNotFound("same-reason"))
        assertThat(accountNotFound).isNotEqualTo(LoginResponse.AccountNotFound(null))

        val accountBlocked = LoginResponse.AccountBlocked("same-reason")
        assertEqualAndSameHash(accountBlocked, LoginResponse.AccountBlocked("same-reason"))
        assertThat(accountBlocked).isNotEqualTo(LoginResponse.AccountBlocked(null))
        assertThat(accountNotFound).isNotEqualTo(accountBlocked)
    }

    private fun assertEqualAndSameHash(actual: Any, expected: Any) {
        assertThat(actual).isEqualTo(expected)
        assertThat(actual.hashCode()).isEqualTo(expected.hashCode())
    }
}
