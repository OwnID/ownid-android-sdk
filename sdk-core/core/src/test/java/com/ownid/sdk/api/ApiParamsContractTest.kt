package com.ownid.sdk.api

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.OauthResponseType
import com.ownid.sdk.model.ProofToken
import com.ownid.sdk.model.SocialProvider
import com.ownid.sdk.model.VerificationMethod
import org.junit.Test

// Covers: API-210
internal class ApiParamsContractTest {

    @Test
    fun authParamsStoreDefaultAndExplicitValues() {
        val loginId = LoginId("user@example.com", LoginId.Type.Email)
        val accessToken = AccessToken("access-token")

        assertThat(DiscoverApi.Params().loginId).isNull()
        assertThat(DiscoverApi.Params(loginId).loginId).isEqualTo(loginId)

        assertThat(LoginApi.Params().accessToken).isNull()
        assertThat(LoginApi.Params(accessToken).accessToken).isEqualTo(accessToken)
    }

    @Test
    fun enrollParamsRequireProofTokenAndKeepOptionalAccessToken() {
        val proofToken = ProofToken("proof-token")
        val accessToken = AccessToken("access-token")

        val passkeyDefaults = PasskeyEnrollApi.Params(proofToken)
        assertThat(passkeyDefaults.proofToken).isEqualTo(proofToken)
        assertThat(passkeyDefaults.accessToken).isNull()
        assertThat(PasskeyEnrollApi.Params(proofToken, accessToken).accessToken).isEqualTo(accessToken)

        val emailDefaults = EmailEnrollApi.Params(proofToken)
        assertThat(emailDefaults.proofToken).isEqualTo(proofToken)
        assertThat(emailDefaults.accessToken).isNull()
        assertThat(EmailEnrollApi.Params(proofToken, accessToken).accessToken).isEqualTo(accessToken)

        val phoneDefaults = PhoneEnrollApi.Params(proofToken)
        assertThat(phoneDefaults.proofToken).isEqualTo(proofToken)
        assertThat(phoneDefaults.accessToken).isNull()
        assertThat(PhoneEnrollApi.Params(proofToken, accessToken).accessToken).isEqualTo(accessToken)
    }

    @Test
    fun oidcParamsDefaultToGoogleIdTokenSemanticsWithoutMaterializingProvider() {
        val defaults = OidcApi.Params()

        assertThatObject(defaults.provider).isNull()
        assertThat(defaults.oauthResponseType).isEqualTo(OauthResponseType.ID_TOKEN)
        assertThat(defaults.accessToken).isNull()

        val accessToken = AccessToken("access-token")
        val explicit = OidcApi.Params(
            provider = SocialProvider.APPLE,
            oauthResponseType = OauthResponseType.CODE,
            accessToken = accessToken
        )

        assertThatObject(explicit.provider).isEqualTo(SocialProvider.APPLE)
        assertThat(explicit.oauthResponseType).isEqualTo(OauthResponseType.CODE)
        assertThat(explicit.accessToken).isEqualTo(accessToken)
    }

    @Test
    fun passkeyParamsKeepNullableContextResolvedInputsAndExplicitValues() {
        val loginId = LoginId("user@example.com", LoginId.Type.Email)
        val accessToken = AccessToken("access-token")

        val assertionDefaults = PasskeyAssertionApi.Params()
        assertThat(assertionDefaults.loginId).isNull()
        assertThat(assertionDefaults.accessToken).isNull()

        val assertionExplicit = PasskeyAssertionApi.Params(loginId = loginId, accessToken = accessToken)
        assertThat(assertionExplicit.loginId).isEqualTo(loginId)
        assertThat(assertionExplicit.accessToken).isEqualTo(accessToken)

        val attestationDefaults = PasskeyAttestationApi.Params()
        assertThat(attestationDefaults.loginId).isNull()
        assertThat(attestationDefaults.accountDisplayName).isNull()
        assertThat(attestationDefaults.accessToken).isNull()

        val attestationExplicit = PasskeyAttestationApi.Params(
            loginId = loginId,
            accountDisplayName = "User Example",
            accessToken = accessToken
        )
        assertThat(attestationExplicit.loginId).isEqualTo(loginId)
        assertThat(attestationExplicit.accountDisplayName).isEqualTo("User Example")
        assertThat(attestationExplicit.accessToken).isEqualTo(accessToken)
    }

    @Test
    fun verificationParamsKeepAllOptionalStartInputs() {
        val loginId = LoginId("user@example.com", LoginId.Type.Email)
        val accessToken = AccessToken("access-token")
        val methods = linkedSetOf(VerificationMethod.OTP, VerificationMethod.MAGIC_LINK)

        val emailDefaults = EmailVerificationApi.Params()
        assertThat(emailDefaults.loginId).isNull()
        assertThat(emailDefaults.loginIdHintId).isNull()
        assertThat(emailDefaults.accessToken).isNull()
        assertThat(emailDefaults.verificationMethods).isNull()
        assertThat(emailDefaults.magicLinkRedirectUrl).isNull()

        val emailExplicit = EmailVerificationApi.Params(
            loginId = loginId,
            loginIdHintId = "email-hint",
            accessToken = accessToken,
            verificationMethods = methods,
            magicLinkRedirectUrl = "https://app.example.com/email"
        )
        assertThat(emailExplicit.loginId).isEqualTo(loginId)
        assertThat(emailExplicit.loginIdHintId).isEqualTo("email-hint")
        assertThat(emailExplicit.accessToken).isEqualTo(accessToken)
        assertThat(emailExplicit.verificationMethods).isEqualTo(methods)
        assertThat(emailExplicit.magicLinkRedirectUrl).isEqualTo("https://app.example.com/email")

        val phoneDefaults = PhoneVerificationApi.Params()
        assertThat(phoneDefaults.loginId).isNull()
        assertThat(phoneDefaults.loginIdHintId).isNull()
        assertThat(phoneDefaults.accessToken).isNull()
        assertThat(phoneDefaults.verificationMethods).isNull()
        assertThat(phoneDefaults.magicLinkRedirectUrl).isNull()

        val phoneLoginId = LoginId("+15551234567", LoginId.Type.PhoneNumber)
        val phoneExplicit = PhoneVerificationApi.Params(
            loginId = phoneLoginId,
            loginIdHintId = "phone-hint",
            accessToken = accessToken,
            verificationMethods = methods,
            magicLinkRedirectUrl = "https://app.example.com/phone"
        )
        assertThat(phoneExplicit.loginId).isEqualTo(phoneLoginId)
        assertThat(phoneExplicit.loginIdHintId).isEqualTo("phone-hint")
        assertThat(phoneExplicit.accessToken).isEqualTo(accessToken)
        assertThat(phoneExplicit.verificationMethods).isEqualTo(methods)
        assertThat(phoneExplicit.magicLinkRedirectUrl).isEqualTo("https://app.example.com/phone")
    }

    private fun assertThatObject(actual: Any?) = assertThat(actual)
}
