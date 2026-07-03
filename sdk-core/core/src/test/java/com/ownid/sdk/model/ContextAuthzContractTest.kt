package com.ownid.sdk.model

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.capability.LoginIdValidationException
import com.ownid.sdk.capability.LoginIdValidator
import com.ownid.sdk.di.getOrThrow
import com.ownid.sdk.di.impl.DIContainerImpl
import com.ownid.sdk.provider.SessionCreate
import com.ownid.sdk.provider.setProviders
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertThrows
import org.junit.Test

@OptIn(InternalOwnIdAPI::class)
// Covers: CFG-110, CFG-120, CFG-130, CTX-010, CTX-020
internal class ContextAuthzContractTest {

    @Test
    fun authzFactoriesStoreRawTypedAndTokenInputs() {
        val rawContext = Context.Builder().apply {
            authz = Authz.start("  user@example.com  ")
        }.build(scopeName = "Raw")

        assertThat(rawContext.rawLoginId).isEqualTo("  user@example.com  ")
        assertThat(rawContext.loginId).isNull()
        assertThat(rawContext.accessToken).isNull()

        val typedLoginId = LoginId("user@example.com", LoginId.Type.Email)
        val typedContext = Context.Builder().apply {
            authz = Authz.start(typedLoginId)
        }.build(scopeName = "Typed")

        assertThat(typedContext.loginId).isSameInstanceAs(typedLoginId)
        assertThat(typedContext.rawLoginId).isNull()
        assertThat(typedContext.accessToken).isNull()

        val typedFromRawContext = Context.Builder().apply {
            authz = Authz.start("user-name", LoginId.Type.UserName)
        }.build(scopeName = "TypedFromRaw")

        assertThat(typedFromRawContext.loginId).isEqualTo(LoginId("user-name", LoginId.Type.UserName))
        assertThat(typedFromRawContext.rawLoginId).isNull()

        val accessToken = AccessToken("token-value")
        val tokenContext = Context.Builder().apply {
            authz = Authz.fromToken(accessToken)
        }.build(scopeName = "Token")

        assertThat(tokenContext.accessToken).isSameInstanceAs(accessToken)
        assertThat(tokenContext.loginId).isNull()
        assertThat(tokenContext.rawLoginId).isNull()

        val tokenStringContext = Context.Builder().apply {
            authz = Authz.fromToken("string-token")
        }.build(scopeName = "TokenString")

        assertThat(tokenStringContext.accessToken).isEqualTo(AccessToken("string-token"))
    }

    @Test
    fun accountDisplayNameIsStoredAndEmittedWithContextPayload() {
        val context = Context.Builder().apply {
            authz = Authz.start(LoginId("user@example.com", LoginId.Type.Email))
            accountDisplayName = "Ada Lovelace"
        }.build(scopeName = "Display")

        assertThat(context.accountDisplayName).isEqualTo("Ada Lovelace")
        assertThat(context.toWebBridgePayload()).isEqualTo(
            buildJsonObject {
                put("authz", buildJsonObject {
                    put("loginId", JsonPrimitive("user@example.com"))
                })
                put("accountDisplayName", JsonPrimitive("Ada Lovelace"))
            }
        )
    }

    @Test
    fun builderBuildsSnapshotsAndToBuilderDoesNotMutateSource() {
        val builder = Context.Builder().apply {
            authz = Authz.start("first@example.com")
            accountDisplayName = "First"
        }
        val first = builder.build(scopeName = "First")

        builder.authz = Authz.fromToken("second-token")
        builder.accountDisplayName = "Second"
        val second = builder.build(scopeName = "Second")

        assertThat(first.rawLoginId).isEqualTo("first@example.com")
        assertThat(first.accessToken).isNull()
        assertThat(first.accountDisplayName).isEqualTo("First")
        assertThat(second.rawLoginId).isNull()
        assertThat(second.accessToken).isEqualTo(AccessToken("second-token"))
        assertThat(second.accountDisplayName).isEqualTo("Second")

        val copiedBuilder = first.toBuilder()
        copiedBuilder.accountDisplayName = null
        val copied = copiedBuilder.build(scopeName = "Copied")

        assertThat(first.accountDisplayName).isEqualTo("First")
        assertThat(copied.accountDisplayName).isNull()
        assertThat(copied.rawLoginId).isEqualTo("first@example.com")
    }

    @Test
    fun withContextCreatesChildScopeFromBlockOnlyAndLeavesParentContextUnchanged() {
        val parent = DIContainerImpl(scopeName = "parent")
        parent.setContext {
            authz = Authz.start("parent@example.com")
            accountDisplayName = "Parent"
        }

        val child = parent.withContext(scopeName = "child") {
            accountDisplayName = "Child"
        }

        val parentContext = parent.getOrThrow<Context>()
        val childContext = child.getOrThrow<Context>()

        assertThat(parentContext.rawLoginId).isEqualTo("parent@example.com")
        assertThat(parentContext.accountDisplayName).isEqualTo("Parent")
        assertThat(childContext.rawLoginId).isNull()
        assertThat(childContext.accountDisplayName).isEqualTo("Child")
    }

    @Test
    fun setContextMergesCurrentScopeFieldsAndAllowsExplicitNullClears() {
        val container = DIContainerImpl(scopeName = "context")

        val unchanged = container.setContext {
            authz = Authz.start("first@example.com")
            accountDisplayName = "First"
        }
        val renamed = container.setContext {
            accountDisplayName = "Renamed"
        }
        val authzCleared = container.setContext {
            authz = null
        }

        assertThat(unchanged).isSameInstanceAs(container)
        assertThat(renamed).isSameInstanceAs(container)
        assertThat(authzCleared).isSameInstanceAs(container)

        val context = container.getOrThrow<Context>()
        assertThat(context.rawLoginId).isNull()
        assertThat(context.accountDisplayName).isEqualTo("Renamed")
    }

    @Test
    fun clearContextRemovesCurrentScopeContextOnly() {
        val container = DIContainerImpl(scopeName = "context")
        val providerScope = container.setProviders {
            sessionCreate { create { error("Provider should not be invoked") } }
        }
        val providerBeforeClear = container.getOrThrow<SessionCreate>()
        val child = container.withContext(scopeName = "child") {
            authz = Authz.start("child@example.com")
        }

        container.setContext {
            authz = Authz.start("parent@example.com")
        }

        val cleared = container.clearContext()

        assertThat(cleared).isSameInstanceAs(container)
        assertThat(providerScope).isSameInstanceAs(container)
        assertThat(container.getOrNull(Context::class.java)).isNull()
        assertThat(container.getOrThrow<SessionCreate>()).isSameInstanceAs(providerBeforeClear)
        assertThat(child.getOrThrow<Context>().rawLoginId).isEqualTo("child@example.com")
    }

    @Test
    fun loginIdResolutionHandlesNonRawAuthzWithoutValidator() {
        val typedLoginId = LoginId("user@example.com", LoginId.Type.Email)
        val typedContext = Context.Builder().apply {
            authz = Authz.start(typedLoginId)
        }.build(scopeName = "Typed")
        val tokenContext = Context.Builder().apply {
            authz = Authz.fromToken("access-token")
        }.build(scopeName = "Token")
        val emptyContext = Context.Builder().build(scopeName = "Empty")

        assertThat(typedContext.loginId(loginIdValidator = null)).isSameInstanceAs(typedLoginId)
        assertThat(tokenContext.loginId(loginIdValidator = null)).isNull()
        assertThat(emptyContext.loginId(loginIdValidator = null)).isNull()
    }

    @Test
    fun rawLoginIdResolutionUsesValidatorAndMapsFailures() {
        val rawContext = Context.Builder().apply {
            authz = Authz.start("person@example.com")
        }.build(scopeName = "Raw")

        assertThat(
            rawContext.loginId(
                TestLoginIdValidator(determine = { raw ->
                    assertThat(raw).isEqualTo("person@example.com")
                    LoginId.Type.Email
                })
            )
        ).isEqualTo(LoginId("person@example.com", LoginId.Type.Email))

        val missingValidator = assertThrows(LoginIdResolutionException.MissingValidator::class.java) {
            rawContext.loginId(loginIdValidator = null)
        }

        assertThat(missingValidator.errorCode).isEqualTo(ErrorCode.MissingCapabilityProvider)
        assertThat(missingValidator.message).isEqualTo("Context.loginId(): LoginIdValidator required")

        val unsupported = assertThrows(LoginIdResolutionException.TypeNotSupported::class.java) {
            rawContext.loginId(
                TestLoginIdValidator(determine = {
                    throw LoginIdValidationException.TypeNotSupported("unsupported")
                })
            )
        }

        assertThat(unsupported.errorCode).isEqualTo(ErrorCode.LoginIdTypeNotSupported)
        assertThat(unsupported.message).isEqualTo("unsupported")

        val rejectedLoginId = LoginId("bad", LoginId.Type.Email)
        val validationFailed = assertThrows(LoginIdResolutionException.ValidationFailed::class.java) {
            rawContext.loginId(
                TestLoginIdValidator(determine = {
                    throw LoginIdValidationException.ValidationFailed("invalid", rejectedLoginId, "email-regex")
                })
            )
        }

        assertThat(validationFailed.errorCode).isEqualTo(ErrorCode.LoginIdValidationFailed)
        assertThat(validationFailed.message).isEqualTo("invalid")
        assertThat(validationFailed.loginId).isSameInstanceAs(rejectedLoginId)
        assertThat(validationFailed.regex).isEqualTo("email-regex")
    }

    private class TestLoginIdValidator(
        private val determine: (String) -> LoginId.Type = { error("Unexpected determineLoginIdType call") },
        private val validate: (LoginId) -> LoginId = { it }
    ) : LoginIdValidator {
        override fun determineLoginIdType(loginId: String): LoginId.Type = determine(loginId)

        override fun validate(loginId: LoginId): LoginId = validate.invoke(loginId)
    }
}
