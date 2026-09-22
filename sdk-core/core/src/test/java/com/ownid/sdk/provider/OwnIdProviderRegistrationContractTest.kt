package com.ownid.sdk.provider

import android.content.Context
import android.content.ContextWrapper
import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.capability.Capability
import com.ownid.sdk.capability.SignInWithGoogle
import com.ownid.sdk.capability.SignInWithSocial
import com.ownid.sdk.capability.UiContextProvider
import com.ownid.sdk.di.MissingDependencyException
import com.ownid.sdk.di.getOrThrow
import com.ownid.sdk.di.impl.DIContainerImpl
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.AuthMethod
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LoginId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test

@OptIn(InternalOwnIdAPI::class)
// Covers: CFG-140, CFG-150, CFG-160, CFG-170, CFG-180, CFG-270
internal class OwnIdProviderRegistrationContractTest {

    @Test
    fun providerBuildersRequireMandatoryHandlersBeforeCreatingProviders() {
        assertThrows(IllegalArgumentException::class.java) {
            SessionCreateBuilder().build()
        }
        assertThrows(IllegalArgumentException::class.java) {
            PasswordAuthenticateBuilder().build()
        }
        assertThrows(IllegalArgumentException::class.java) {
            SignInWithGoogleBuilder().build()
        }
    }

    @Test
    fun sessionCreateBuilderCreatesProviderWithTypedAvailabilityAndCallback() = runTest {
        val matchingParams = sessionCreateParams(LoginId.Type.Email)
        val nonMatchingParams = sessionCreateParams(LoginId.Type.PhoneNumber)
        val provider = SessionCreateBuilder().apply {
            isAvailable { params -> params.loginId.type == LoginId.Type.Email }
            create { params -> Result.success(SessionOutput("session:${params.loginId.id}")) }
        }.build()

        assertThat(provider.isAvailable(null)).isTrue()
        assertThat(provider.isAvailable(matchingParams)).isTrue()
        assertThat(provider.isAvailable(nonMatchingParams)).isFalse()
        assertThat(provider.isAvailable(UnsupportedParams)).isFalse()
        assertThat(provider.create(matchingParams).getOrThrow().session).isEqualTo("session:user@example.com")
    }

    @Test
    fun passwordAuthenticateBuilderCreatesProviderWithTypedAvailabilityAndCallback() = runTest {
        val matchingParams = passwordAuthenticateParams(password = "correct")
        val nonMatchingParams = passwordAuthenticateParams(password = "wrong")
        val provider = PasswordAuthenticateBuilder().apply {
            isAvailable { params -> params.password == "correct" }
            authenticate { params -> Result.success(SessionOutput("password:${params.loginId.id}")) }
        }.build()

        assertThat(provider.isAvailable(null)).isTrue()
        assertThat(provider.isAvailable(matchingParams)).isTrue()
        assertThat(provider.isAvailable(nonMatchingParams)).isFalse()
        assertThat(provider.isAvailable(UnsupportedParams)).isFalse()
        assertThat(provider.authenticate(matchingParams).getOrThrow().session).isEqualTo("password:user@example.com")
    }

    @Test
    fun signInWithGoogleBuilderCreatesProviderWithTypedAvailabilityAndSignInCallback() = runTest {
        val matchingParams = signInWithSocialParams(webClientId = "web-client")
        val nonMatchingParams = signInWithSocialParams(webClientId = "other-client")
        val signOutParams = SignInWithGoogle.SignOutParams(TestContext)
        var cancelCount = 0
        var signOutContext: Context? = null
        val provider = SignInWithGoogleBuilder().apply {
            isAvailable { params -> params.webClientId == "web-client" }
            signIn { activity, params ->
                assertThat(activity).isSameInstanceAs(TestActivity)
                SignInWithSocial.Result.Success(id = "google:${params.webClientId}", idToken = "id-token")
            }
            cancel { cancelCount += 1 }
            signOut { params ->
                signOutContext = params.context
                null
            }
        }.build()

        assertThat(provider.isAvailable(null)).isTrue()
        assertThat(provider.isAvailable(matchingParams)).isTrue()
        assertThat(provider.isAvailable(nonMatchingParams)).isFalse()
        assertThat(provider.isAvailable(UnsupportedParams)).isFalse()
        assertThat((provider.signIn(TestActivity, matchingParams) as SignInWithSocial.Result.Success).id).isEqualTo("google:web-client")
        provider.cancel()
        assertThat(cancelCount).isEqualTo(1)
        assertThat(provider.signOut(signOutParams)).isNull()
        assertThat(signOutContext).isSameInstanceAs(signOutParams.context)
    }

    @Test
    fun withProvidersCreatesChildScopeAndLeavesParentProvidersUnchanged() = runTest {
        val parent = DIContainerImpl(scopeName = "parent")
        val parentProvider = sessionCreateProvider()

        parent.register(SessionCreate::class.java, parentProvider)

        val unchanged = parent.withProviders(scopeName = "empty-child") {
        }
        val child = parent.withProviders(scopeName = "child") {
            sessionCreate { create { Result.success(SessionOutput("first")) } }
            sessionCreate { create { Result.success(SessionOutput("second")) } }
            sessionCreate { create { Result.success(SessionOutput("third")) } }
        }

        assertThat(unchanged).isSameInstanceAs(parent)
        assertThat(parent.getOrThrow<SessionCreate>()).isSameInstanceAs(parentProvider)
        assertThat(child.getOrThrow<SessionCreate>().create(sessionCreateParams(LoginId.Type.Email)).getOrThrow().session).isEqualTo("third")
    }

    @Test
    fun withProvidersUsesLastRegisteredSignInWithGoogleProviderInChildScope() = runTest {
        val parent = DIContainerImpl(scopeName = "parent")

        val child = parent.withProviders(scopeName = "child") {
            signInWithGoogle { signIn { _, _ -> SignInWithSocial.Result.Success(id = "first", idToken = "first-token") } }
            signInWithGoogle { signIn { _, _ -> SignInWithSocial.Result.Success(id = "second", idToken = "second-token") } }
        }

        val result = child.getOrThrow<SignInWithGoogle>().signIn(TestActivity, signInWithSocialParams(webClientId = "web-client"))

        assertThat((result as SignInWithSocial.Result.Success).id).isEqualTo("second")
    }

    @Test
    fun setProvidersUpdatesCurrentScopeAndPreservesUndeclaredProviderTypes() = runTest {
        val container = DIContainerImpl(scopeName = "providers")
        val retainedPasswordProvider = passwordAuthenticateProvider("retained")

        container.register(PasswordAuthenticate::class.java, retainedPasswordProvider)

        val unchanged = container.setProviders {
        }
        val changed = container.setProviders {
            sessionCreate { create { Result.success(SessionOutput("first")) } }
            sessionCreate { create { Result.success(SessionOutput("second")) } }
        }

        assertThat(unchanged).isSameInstanceAs(container)
        assertThat(changed).isSameInstanceAs(container)
        assertThat(container.getOrThrow<SessionCreate>().create(sessionCreateParams(LoginId.Type.Email)).getOrThrow().session).isEqualTo("second")
        assertThat(container.getOrThrow<PasswordAuthenticate>()).isSameInstanceAs(retainedPasswordProvider)
    }

    @Test
    fun customUiContextProviderIsScopedWithoutEagerResolutionOrParentFallback() {
        val parent = DIContainerImpl(scopeName = "parent")
        val parentProvider = UiContextProvider { UiContextProvider.Result.Success(TestActivity) }
        val error = IllegalStateException("app could not select a host")
        var resolutions = 0
        val childProvider = UiContextProvider {
            resolutions += 1
            UiContextProvider.Result.Failure(error)
        }
        val siblingProvider = UiContextProvider { UiContextProvider.Result.Success(TestActivity) }
        var lookupAfterRegistration: UiContextProvider? = null

        parent.register(UiContextProvider::class.java, parentProvider)
        val child = parent.withProviders(scopeName = "child") {
            uiContext(childProvider)
            lookupAfterRegistration = getOrNull(UiContextProvider::class.java)
        }
        val sibling = parent.withProviders(scopeName = "sibling") { uiContext(siblingProvider) }

        assertThat(child).isNotSameInstanceAs(parent)
        assertThat(parent.getOrThrow<UiContextProvider>()).isSameInstanceAs(parentProvider)
        assertThat(sibling.getOrThrow<UiContextProvider>()).isSameInstanceAs(siblingProvider)
        assertThat(lookupAfterRegistration).isSameInstanceAs(parentProvider)
        assertThat(child.getOrThrow<UiContextProvider>()).isSameInstanceAs(childProvider)
        assertThat(resolutions).isEqualTo(0)

        val failure = child.getOrThrow<UiContextProvider>().current() as UiContextProvider.Result.Failure
        assertThat(failure.error).isSameInstanceAs(error)
        assertThat(resolutions).isEqualTo(1)
    }

    @Test
    fun setProvidersPreservesOmittedUiContextAndUsesLastRegisteredCustomProvider() {
        val container = DIContainerImpl(scopeName = "providers")
        val original = UiContextProvider { UiContextProvider.Result.Success(TestActivity) }
        val first = UiContextProvider { UiContextProvider.Result.Success(TestActivity) }
        val last = UiContextProvider { UiContextProvider.Result.Success(TestActivity) }
        var lookupAfterRegistration: UiContextProvider? = null

        container.register(UiContextProvider::class.java, original)
        assertThat(container.setProviders { sessionCreate { create { Result.success(SessionOutput("created")) } } })
            .isSameInstanceAs(container)
        assertThat(container.getOrThrow<UiContextProvider>()).isSameInstanceAs(original)

        assertThat(container.setProviders {
            uiContext(first)
            lookupAfterRegistration = getOrNull(UiContextProvider::class.java)
            uiContext(last)
        }).isSameInstanceAs(container)

        assertThat(lookupAfterRegistration).isSameInstanceAs(original)
        assertThat(container.getOrThrow<UiContextProvider>()).isSameInstanceAs(last)
        assertThat(container.getOrThrow<SessionCreate>()).isNotNull()
    }

    @Test
    fun registrarLookupSeesParentScopeDependenciesButNotProvidersRegisteredInSameWithProvidersBlock() = runTest {
        val parent = DIContainerImpl(scopeName = "parent")
        val parentSessionProvider = sessionCreateProvider()
        val parentPasswordProvider = passwordAuthenticateProvider("parent")
        var sessionFromGetOrNull: SessionCreate? = null
        var passwordFromGetOrThrow: PasswordAuthenticate? = null
        var googleBeforeRegistration: SignInWithGoogle? = null
        var googleAfterRegistration: SignInWithGoogle? = null

        parent.register(SessionCreate::class.java, parentSessionProvider)
        parent.register(PasswordAuthenticate::class.java, parentPasswordProvider)

        val child = parent.withProviders(scopeName = "child") {
            sessionFromGetOrNull = getOrNull(SessionCreate::class.java)
            passwordFromGetOrThrow = getOrThrow(PasswordAuthenticate::class.java)
            googleBeforeRegistration = getOrNull(SignInWithGoogle::class.java)

            signInWithGoogle { signIn { _, _ -> SignInWithSocial.Result.Success(id = "google", idToken = "id-token") } }

            googleAfterRegistration = getOrNull(SignInWithGoogle::class.java)
            assertThrows(MissingDependencyException::class.java) {
                getOrThrow(SignInWithGoogle::class.java)
            }
        }

        assertThat(sessionFromGetOrNull).isSameInstanceAs(parentSessionProvider)
        assertThat(passwordFromGetOrThrow).isSameInstanceAs(parentPasswordProvider)
        assertThat(googleBeforeRegistration).isNull()
        assertThat(googleAfterRegistration).isNull()
        assertThat(child.getOrThrow<SignInWithGoogle>()).isNotNull()
    }

    @Test
    fun registrarLookupSeesCurrentScopeDependenciesButNotProvidersRegisteredLaterInSetProvidersBlock() = runTest {
        val container = DIContainerImpl(scopeName = "current")
        val currentPasswordProvider = passwordAuthenticateProvider("current")
        var passwordFromGetOrNull: PasswordAuthenticate? = null
        var passwordFromGetOrThrow: PasswordAuthenticate? = null
        var sessionBeforeRegistration: SessionCreate? = null
        var sessionAfterRegistration: SessionCreate? = null

        container.register(PasswordAuthenticate::class.java, currentPasswordProvider)

        container.setProviders {
            passwordFromGetOrNull = getOrNull(PasswordAuthenticate::class.java)
            passwordFromGetOrThrow = getOrThrow(PasswordAuthenticate::class.java)
            sessionBeforeRegistration = getOrNull(SessionCreate::class.java)

            sessionCreate { create { Result.success(SessionOutput("created")) } }

            sessionAfterRegistration = getOrNull(SessionCreate::class.java)
            assertThrows(MissingDependencyException::class.java) {
                getOrThrow(SessionCreate::class.java)
            }
        }

        assertThat(passwordFromGetOrNull).isSameInstanceAs(currentPasswordProvider)
        assertThat(passwordFromGetOrThrow).isSameInstanceAs(currentPasswordProvider)
        assertThat(sessionBeforeRegistration).isNull()
        assertThat(sessionAfterRegistration).isNull()
        assertThat(container.getOrThrow<SessionCreate>().create(sessionCreateParams(LoginId.Type.Email)).getOrThrow().session).isEqualTo("created")
    }

    private fun sessionCreateProvider(): SessionCreate = object : SessionCreate {
        override suspend fun create(params: SessionCreate.Params): Result<SessionOutput> =
            Result.success(SessionOutput("parent"))
    }

    private fun passwordAuthenticateProvider(label: String): PasswordAuthenticate = object : PasswordAuthenticate {
        override suspend fun authenticate(params: PasswordAuthenticate.Params): Result<SessionOutput> =
            Result.success(SessionOutput(label))
    }

    private fun sessionCreateParams(type: LoginId.Type): SessionCreate.Params =
        SessionCreate.Params(
            loginId = LoginId(if (type == LoginId.Type.Email) "user@example.com" else "+15551234567", type),
            accessToken = AccessToken("access-token"),
            authMethod = AuthMethod.Passkey,
            sessionPayload = "payload"
        )

    private fun passwordAuthenticateParams(password: String): PasswordAuthenticate.Params =
        PasswordAuthenticate.Params(
            loginId = LoginId("user@example.com", LoginId.Type.Email),
            password = password
        )

    private fun signInWithSocialParams(webClientId: String): SignInWithSocial.Params =
        SignInWithSocial.Params(
            webClientId = webClientId,
            nonce = "nonce"
        )

    private object UnsupportedParams : Capability.Params

    private object TestContext : ContextWrapper(null)

    private val TestActivity: android.app.Activity = io.mockk.mockk()
}
