package com.ownid.sdk.provider

import android.app.Activity
import android.content.ContextWrapper
import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.capability.SignInWithGoogle
import com.ownid.sdk.capability.SignInWithSocial
import com.ownid.sdk.di.impl.DIContainerImpl
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.AuthMethod
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.Reason
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(InternalOwnIdAPI::class)
// Covers: CFG-190, CFG-200, CFG-210, CFG-220
internal class OwnIdProviderResultContractTest {

    @Test
    fun providerParameterAndOutputWrappersPreserveInputs() {
        val loginId = LoginId("  user@example.com  ", LoginId.Type.Email)
        val accessToken = AccessToken("access-token")
        val sessionParams = SessionCreate.Params(
            loginId = loginId,
            accessToken = accessToken,
            authMethod = AuthMethod.SocialGoogle,
            sessionPayload = """{"session":"payload"}"""
        )
        val passwordParams = PasswordAuthenticate.Params(
            loginId = loginId,
            password = "  raw password  "
        )
        val socialParams = SignInWithSocial.Params(
            webClientId = "web-client",
            nonce = "nonce"
        )
        val sessionOutput = SessionOutput(session = "host-session")
        val nullSessionOutput = SessionOutput(session = null)

        assertThat(sessionParams.loginId).isSameInstanceAs(loginId)
        assertThat(sessionParams.accessToken).isSameInstanceAs(accessToken)
        assertThat(sessionParams.authMethod).isSameInstanceAs(AuthMethod.SocialGoogle)
        assertThat(sessionParams.sessionPayload).isEqualTo("""{"session":"payload"}""")
        assertThat(passwordParams.loginId).isSameInstanceAs(loginId)
        assertThat(passwordParams.password).isEqualTo("  raw password  ")
        assertThat(socialParams.webClientId).isEqualTo("web-client")
        assertThat(socialParams.nonce).isEqualTo("nonce")
        assertThat(sessionOutput.session).isEqualTo("host-session")
        assertThat(nullSessionOutput.session).isNull()
    }

    @Test
    fun sessionAndPasswordProvidersReturnSuccessAndFailureResultsUnmodified() = runTest {
        val sessionOutput = SessionOutput(session = "created-session")
        val sessionFailure = IllegalStateException("session provider failed")
        val passwordOutput = SessionOutput(session = "password-session")
        val passwordFailure = IllegalArgumentException("password provider failed")

        val successfulSessionProvider = SessionCreateBuilder().apply {
            create { Result.success(sessionOutput) }
        }.build()
        val failingSessionProvider = SessionCreateBuilder().apply {
            create { Result.failure(sessionFailure) }
        }.build()
        val successfulPasswordProvider = PasswordAuthenticateBuilder().apply {
            authenticate { Result.success(passwordOutput) }
        }.build()
        val failingPasswordProvider = PasswordAuthenticateBuilder().apply {
            authenticate { Result.failure(passwordFailure) }
        }.build()

        assertThat(successfulSessionProvider.create(sessionCreateParams()).getOrThrow()).isSameInstanceAs(sessionOutput)
        assertThat(failingSessionProvider.create(sessionCreateParams()).exceptionOrNull()).isSameInstanceAs(sessionFailure)
        assertThat(successfulPasswordProvider.authenticate(passwordAuthenticateParams()).getOrThrow()).isSameInstanceAs(passwordOutput)
        assertThat(failingPasswordProvider.authenticate(passwordAuthenticateParams()).exceptionOrNull()).isSameInstanceAs(passwordFailure)
    }

    @Test
    fun socialResultWrappersPreserveSuccessCancelAndFailurePayloads() {
        val failure = IllegalStateException("social provider failed")
        val reason = Reason.UserClose("dismissed")

        val success = SignInWithSocial.Result.Success(id = "provider-user-id", idToken = "id-token")
        val canceled = SignInWithSocial.Result.Canceled(reason = reason)
        val fail = SignInWithSocial.Result.Fail(error = failure)

        assertThat(success.id).isEqualTo("provider-user-id")
        assertThat(success.idToken).isEqualTo("id-token")
        assertThat(canceled.reason).isSameInstanceAs(reason)
        assertThat(fail.error).isSameInstanceAs(failure)
    }

    @Test
    fun googleBuilderDefaultHandlersKeepCancelNoOpAndSignOutSuccessShape() = runTest {
        val defaultProvider = SignInWithGoogleBuilder().apply {
            signIn { _, params -> SignInWithSocial.Result.Success(id = "google:${params.webClientId}", idToken = "id-token") }
        }.build()
        val signInParams = signInWithSocialParams()
        val signOutParams = SignInWithGoogle.SignOutParams(TestContext)

        val result = defaultProvider.signIn(TestActivity, signInParams)
        defaultProvider.cancel()

        assertThat((result as SignInWithSocial.Result.Success).id).isEqualTo("google:web-client")
        assertThat(defaultProvider.signOut(signOutParams)).isNull()
    }

    @Test
    fun googleBuilderSignOutReturnsConfiguredFailureUnmodified() = runTest {
        val signOutFailure = IllegalStateException("local sign-out failed")
        val provider = SignInWithGoogleBuilder().apply {
            signIn { _, _ -> SignInWithSocial.Result.Success(id = "google", idToken = "id-token") }
            signOut { signOutFailure }
        }.build()
        val signOutParams = SignInWithGoogle.SignOutParams(TestContext)

        assertThat(provider.signOut(signOutParams)).isSameInstanceAs(signOutFailure)
    }

    @Test
    fun providerRegistrarKeepsAndroidGoogleRegistrationSeparateFromGenericSocialRegistration() = runTest {
        val container = DIContainerImpl(scopeName = "social")

        val child = container.withProviders(scopeName = "google-child") {
            signInWithGoogle {
                signIn { _, _ -> SignInWithSocial.Result.Success(id = "google", idToken = "id-token") }
            }
        }

        assertThat(child.getOrNull(SignInWithGoogle::class.java)).isNotNull()
        assertThat(child.getOrNull(SignInWithSocial::class.java)).isNull()
    }

    private fun sessionCreateParams(): SessionCreate.Params =
        SessionCreate.Params(
            loginId = LoginId("user@example.com", LoginId.Type.Email),
            accessToken = AccessToken("access-token"),
            authMethod = AuthMethod.Passkey,
            sessionPayload = "payload"
        )

    private fun passwordAuthenticateParams(): PasswordAuthenticate.Params =
        PasswordAuthenticate.Params(
            loginId = LoginId("user@example.com", LoginId.Type.Email),
            password = "password"
        )

    private fun signInWithSocialParams(): SignInWithSocial.Params =
        SignInWithSocial.Params(
            webClientId = "web-client",
            nonce = "nonce"
        )

    private object TestContext : ContextWrapper(null)

    private val TestActivity: Activity = mockk()
}
