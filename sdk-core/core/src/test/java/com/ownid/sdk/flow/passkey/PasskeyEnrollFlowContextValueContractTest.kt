package com.ownid.sdk.flow.passkey

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.OperationType
import com.ownid.sdk.model.ProofToken
import org.junit.Test

// Covers: FLOW-160
internal class PasskeyEnrollFlowContextValueContractTest {

    @Test
    fun toStringListsOnlyConfiguredAccessProofAndHeadlessValues() {
        val allValues = PasskeyEnrollFlowContext {
            accessToken = AccessToken("1234567890abcdefghij")
            proofToken = ProofToken("abcdefghij1234567890")
            headless = true
        }

        assertThat(allValues.toString()).isEqualTo(
            "PasskeyEnrollFlowContext(" +
                    "accessToken=AccessToken(token=12345678..[4]...cdefghij), " +
                    "proofToken=ProofToken(token=abcdefgh..[4]...34567890), " +
                    "headless=true" +
                    ")"
        )

        val proofOnly = PasskeyEnrollFlowContext {
            proofToken = ProofToken("proof-token")
        }

        assertThat(proofOnly.toString()).isEqualTo("PasskeyEnrollFlowContext(proofToken=ProofToken(token=proof-token))")
        assertThat(proofOnly.toString()).doesNotContain("accessToken=")
        assertThat(proofOnly.toString()).doesNotContain("headless=")

        val empty = PasskeyEnrollFlowContext {}

        assertThat(empty.toString()).isEqualTo("PasskeyEnrollFlowContext()")
    }

    @Test
    fun responseToStringRedactsLoginId() {
        val response = PasskeyEnrollFlow.Response(LoginId("person@example.com", LoginId.Type.Email))

        assertThat(response.toString()).isEqualTo(
            "PasskeyEnrollFlowResponse(loginId=LoginId(id='p****n@example.com', type=Email))"
        )
        assertThat(response.toString()).doesNotContain("person@example.com")
    }

    @Test
    fun failureToStringKeepsStableCategoryCodeAndMessage() {
        assertThat(
            PasskeyEnrollFlowFailure.Input.MissingAccessToken(
                errorCode = ErrorCode.InvalidArgument,
                message = "missing token"
            ).toString()
        ).isEqualTo("Input.MissingAccessToken(errorCode=invalid_argument, message=missing token)")
        assertThat(
            PasskeyEnrollFlowFailure.Input.UnresolvedLoginId(
                errorCode = ErrorCode.InvalidArgument,
                message = "bad login ID"
            ).toString()
        ).isEqualTo("Input.UnresolvedLoginId(errorCode=invalid_argument, message=bad login ID)")
        assertThat(
            PasskeyEnrollFlowFailure.OperationFailed(
                operationType = OperationType.PASSKEY_CREATION,
                errorCode = ErrorCode.PasskeyNotCreated,
                message = "creation failed"
            ).toString()
        ).isEqualTo("OperationFailed(errorCode=passkey_not_created, message=creation failed)")
        assertThat(
            PasskeyEnrollFlowFailure.Unexpected(
                errorCode = ErrorCode.Unknown,
                message = "invariant"
            ).toString()
        ).isEqualTo("Unexpected(errorCode=unknown, message=invariant)")
    }
}
