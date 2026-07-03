package com.ownid.sdk.compose

import android.app.Application
import android.os.Looper
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.OwnId
import com.ownid.sdk.compose.ops.EmailVerificationUiProvider
import com.ownid.sdk.compose.ops.LoginIdCollectUiContent
import com.ownid.sdk.compose.ops.LoginIdCollectUiProvider
import com.ownid.sdk.compose.ops.PhoneVerificationUiProvider
import com.ownid.sdk.di.getOrThrow
import com.ownid.sdk.di.register
import com.ownid.sdk.di.remove
import com.ownid.sdk.model.AccessOrProofToken
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.InstanceName
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.OperationID
import com.ownid.sdk.model.OperationType
import com.ownid.sdk.model.Reason
import com.ownid.sdk.model.UiError
import com.ownid.sdk.ops.EmailVerificationOperationFailure
import com.ownid.sdk.ops.LoginIdCollectOperationFailure
import com.ownid.sdk.ops.OperationFailure
import com.ownid.sdk.ops.OperationResult
import com.ownid.sdk.ops.PhoneVerificationOperationFailure
import com.ownid.sdk.ui.capability.EmailVerificationStringsProvider
import com.ownid.sdk.ui.capability.LoginIdCollectStrings
import com.ownid.sdk.ui.capability.LoginIdCollectStringsProvider
import com.ownid.sdk.ui.capability.PhoneVerificationStringsProvider
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@OptIn(InternalOwnIdAPI::class)
// Covers: UI-PRESENT-070, UI-PRESENT-080, UI-PRESENT-090, UI-PRESENT-180
internal class OwnIdOperationUiRuntimeContractTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val app: Application
        get() = RuntimeEnvironment.getApplication()

    private val createdInstances = mutableListOf<InstanceName>()

    @After
    fun tearDown() {
        createdInstances.forEach(OwnId::destroy)
        createdInstances.clear()
    }

    @Test
    fun appHostedContainerOpenAndSettledDismissalReachOperationUiBoundary() {
        val instanceName = createInitializedInstance()
        val stringsProvider = StaticLoginIdCollectStringsProvider(loginStrings("Provided"))
        val providerCalls = mutableListOf<LoginContentCall>()
        val provider = RecordingLoginIdCollectUiProvider(recordingContent(providerCalls))
        OwnId.getInstanceContainer(instanceName)!!.apply {
            register<LoginIdCollectStringsProvider>(stringsProvider)
            register<LoginIdCollectUiProvider>(provider)
        }
        val operationController = RecordingLoginIdCollectOperationController(
            operationId = OperationID(OperationType.LOGIN_ID_COLLECT, "settled-dismissal"),
            initialUiState = loginUiState(loginIdValue = "initial")
        )
        val uiController = OwnIdOperationUiController(instanceName, operationController)
        var closeCalls = 0
        val containerController = OwnIdUiContainerController(closeAction = { closeCalls += 1 })

        composeRule.setContent {
            OwnIdOperationUi(
                operationUiController = uiController,
                uiContainerController = containerController,
                errorTextProvider = { code -> "mapped:${code.value}" }
            )
        }

        composeRule.waitUntil { providerCalls.any { call -> !call.isReadyForInitialFocus } }

        containerController.markOpened()
        composeRule.waitUntil { providerCalls.any { call -> call.isReadyForInitialFocus } }

        composeRule.runOnIdle {
            operationController.complete(OperationResult.Success(LoginId("person@example.com", LoginId.Type.Email)))
        }
        shadowOf(Looper.getMainLooper()).idle()
        composeRule.waitUntil { closeCalls == 1 }

        containerController.markClosed()

        assertThat(operationController.abortReasons).isEmpty()
        val readyCall = providerCalls.last { it.isReadyForInitialFocus }
        assertThat(readyCall.strings.title).isEqualTo("Provided title")
        assertThat(readyCall.mappedErrorText).isNull()
    }

    @Test
    fun missingProviderAbortsWithSystemErrorAndDismissesWithoutUserClose() {
        val missingProvider = createLoginUiCase()
        OwnId.getInstanceContainer(missingProvider.instanceName)!!.apply {
            remove<LoginIdCollectUiProvider>()
            register<LoginIdCollectStringsProvider>(StaticLoginIdCollectStringsProvider(loginStrings("Missing provider")))
        }
        missingProvider.assertSystemAbortAndDismissalWithoutUserClose("Login ID collection UI", "no UI content provider")
    }

    @Test
    fun emailMissingProviderAbortsWithSystemErrorAndDismissesWithoutUserClose() {
        val missingEmailProvider = createEmailUiCase()
        OwnId.getInstanceContainer(missingEmailProvider.instanceName)!!.apply {
            remove<EmailVerificationUiProvider>()
            register<EmailVerificationStringsProvider>(StaticEmailVerificationStringsProvider(emailStrings("Missing email provider")))
        }

        missingEmailProvider.assertSystemAbortAndDismissalWithoutUserClose("Email verification UI", "no UI content provider")
    }

    @Test
    fun phoneMissingProviderAbortsWithSystemErrorAndDismissesWithoutUserClose() {
        val missingPhoneProvider = createPhoneUiCase()
        OwnId.getInstanceContainer(missingPhoneProvider.instanceName)!!.apply {
            remove<PhoneVerificationUiProvider>()
            register<PhoneVerificationStringsProvider>(StaticPhoneVerificationStringsProvider(phoneStrings("Missing phone provider")))
        }

        missingPhoneProvider.assertSystemAbortAndDismissalWithoutUserClose("Phone verification UI", "no UI content provider")
    }

    @Test
    fun missingStringsAbortWithSystemErrorAndDismissesWithoutUserClose() {
        val missingStrings = createLoginUiCase()
        OwnId.getInstanceContainer(missingStrings.instanceName)!!.apply {
            register<LoginIdCollectUiProvider>(RecordingLoginIdCollectUiProvider(recordingContent(mutableListOf())))
            remove<LoginIdCollectStringsProvider>()
        }
        missingStrings.assertSystemAbortAndDismissalWithoutUserClose("Login ID collection UI", "required UI strings")
    }

    @Test
    fun emailMissingStringsAbortsWithSystemErrorAndDismissesWithoutUserClose() {
        val missingEmailStrings = createEmailUiCase()
        OwnId.getInstanceContainer(missingEmailStrings.instanceName)!!.apply {
            register<EmailVerificationUiProvider>(RecordingEmailVerificationUiProvider(emptyEmailContent()))
            remove<EmailVerificationStringsProvider>()
        }

        missingEmailStrings.assertSystemAbortAndDismissalWithoutUserClose("Email verification UI", "required UI strings")
    }

    @Test
    fun phoneMissingStringsAbortsWithSystemErrorAndDismissesWithoutUserClose() {
        val missingPhoneStrings = createPhoneUiCase()
        OwnId.getInstanceContainer(missingPhoneStrings.instanceName)!!.apply {
            register<PhoneVerificationUiProvider>(RecordingPhoneVerificationUiProvider(emptyPhoneContent()))
            remove<PhoneVerificationStringsProvider>()
        }

        missingPhoneStrings.assertSystemAbortAndDismissalWithoutUserClose("Phone verification UI", "required UI strings")
    }

    @Test
    fun missingInstanceAbortsWithSystemErrorAndDismissesWithoutUserClose() {
        val missingInstanceName = InstanceName("operation-ui-missing-instance-${UUID.randomUUID()}")
        val missingInstanceController = RecordingLoginIdCollectOperationController(
            operationId = OperationID(OperationType.LOGIN_ID_COLLECT, "missing-instance"),
            initialUiState = loginUiState(loginIdValue = "missing-instance")
        )
        OperationUiCase(missingInstanceName, missingInstanceController)
            .assertSystemAbortAndDismissalWithoutUserClose("OwnID SDK instance is no longer available")
    }

    @Test
    fun emailMissingInstanceAbortsWithSystemErrorAndDismissesWithoutUserClose() {
        val missingEmailInstance = OperationUiCase(
            instanceName = InstanceName("operation-ui-missing-email-instance-${UUID.randomUUID()}"),
            operationController = RecordingEmailVerificationOperationController(
                OperationID(OperationType.EMAIL_VERIFICATION, "missing-email-instance")
            )
        )

        missingEmailInstance.assertSystemAbortAndDismissalWithoutUserClose("OwnID SDK instance is no longer available")
    }

    @Test
    fun phoneMissingInstanceAbortsWithSystemErrorAndDismissesWithoutUserClose() {
        val missingPhoneInstance = OperationUiCase(
            instanceName = InstanceName("operation-ui-missing-phone-instance-${UUID.randomUUID()}"),
            operationController = RecordingPhoneVerificationOperationController(
                OperationID(OperationType.PHONE_NUMBER_VERIFICATION, "missing-phone-instance")
            )
        )

        missingPhoneInstance.assertSystemAbortAndDismissalWithoutUserClose("OwnID SDK instance is no longer available")
    }

    @Test
    fun explicitContentOverrideBeatsRegisteredProviderAndReceivesCurrentHandoff() {
        val instanceName = createInitializedInstance()
        val strings = loginStrings("Current")
        val providerCalls = mutableListOf<LoginContentCall>()
        val overrideCalls = mutableListOf<LoginContentCall>()
        OwnId.getInstanceContainer(instanceName)!!.apply {
            register<LoginIdCollectStringsProvider>(StaticLoginIdCollectStringsProvider(strings))
            register<LoginIdCollectUiProvider>(RecordingLoginIdCollectUiProvider(recordingContent(providerCalls)))
        }
        val operationController = RecordingLoginIdCollectOperationController(
            operationId = OperationID(OperationType.LOGIN_ID_COLLECT, "override-precedence"),
            initialUiState = loginUiState(
                loginIdValue = "current@example.com",
                error = UiError(ErrorCode.Network, "Default network text")
            )
        )
        val uiController = OwnIdOperationUiController(instanceName, operationController)

        composeRule.setContent {
            OwnIdOperationUi(
                operationUiController = uiController,
                loginIdCollectContent = recordingContent(overrideCalls),
                errorTextProvider = { code -> "override:${code.value}" }
            )
        }

        composeRule.waitUntil { overrideCalls.isNotEmpty() }

        val overrideCall = overrideCalls.last()
        assertThat(providerCalls).isEmpty()
        assertThat(overrideCall.loginIdValue).isEqualTo("current@example.com")
        assertThat(overrideCall.strings).isEqualTo(strings)
        assertThat(overrideCall.mappedErrorText).isEqualTo("override:network")
        assertThat(overrideCall.isReadyForInitialFocus).isTrue()
    }

    @Test
    fun appHostedOperationUiDefaultThemeUsesCallerThemeEvenWhenSdkThemeStoreExists() {
        val instanceName = createInitializedInstance()
        val strings = loginStrings("Themed")
        OwnId.getInstanceContainer(instanceName)!!.apply {
            register<LoginIdCollectStringsProvider>(StaticLoginIdCollectStringsProvider(strings))
            getOrThrow<OwnIdThemeStore>().set(
                OwnIdTheme(
                    colorScheme = lightColorScheme(primary = Color(0xFF334455)),
                    shapes = Shapes(),
                    typography = Typography(),
                    colors = OwnIdDefaultColorsLight.copy(primary = Color(0xFF445566))
                )
            )
        }
        val operationController = RecordingLoginIdCollectOperationController(
            operationId = OperationID(OperationType.LOGIN_ID_COLLECT, "theme-inheritance"),
            initialUiState = loginUiState(loginIdValue = "theme@example.com")
        )
        val uiController = OwnIdOperationUiController(instanceName, operationController)
        val probes = mutableListOf<ThemeProbe>()
        val appColorScheme = lightColorScheme(
            primary = Color(0xFF123456),
            onPrimary = Color(0xFFABCDEF),
            surface = Color(0xFFFAFAFA),
            onSurface = Color(0xFF111111)
        )

        composeRule.setContent {
            MaterialTheme(colorScheme = appColorScheme) {
                OwnIdOperationUi(
                    operationUiController = uiController,
                    loginIdCollectContent = themeProbeContent(probes)
                )
            }
        }

        composeRule.waitUntil { probes.isNotEmpty() }

        assertThat(probes.last()).isEqualTo(
            ThemeProbe(
                materialPrimary = Color(0xFF123456),
                ownIdPrimary = Color(0xFF123456)
            )
        )
    }

    @Test
    fun appHostedOperationUiUpdatesVisibleTextFromActiveStringsProvider() {
        val instanceName = createInitializedInstance()
        val stringsProvider = MutableLoginIdCollectStringsProvider(loginStrings("Initial"))
        OwnId.getInstanceContainer(instanceName)!!.apply {
            register<LoginIdCollectStringsProvider>(stringsProvider)
            register<LoginIdCollectUiProvider>(LoginIdCollectUiProvider.Default)
        }
        val operationController = RecordingLoginIdCollectOperationController(
            operationId = OperationID(OperationType.LOGIN_ID_COLLECT, "strings-update"),
            initialUiState = loginUiState(loginIdValue = "locale@example.com")
        )
        val uiController = OwnIdOperationUiController(instanceName, operationController)

        composeRule.setContent {
            OwnIdOperationUi(operationUiController = uiController)
        }

        composeRule.onNodeWithText("Initial title").assertExists()

        composeRule.runOnIdle {
            stringsProvider.update(loginStrings("Updated"))
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Updated title").assertExists()
        composeRule.onNodeWithText("Initial title").assertDoesNotExist()
        assertThat(stringsProvider.requests.single().loginIdTypes).containsExactly(LoginId.Type.Email)
    }

    @Test
    fun appHostedOperationUiExplicitThemeOverridesCallerMaterialThemeAtRuntime() {
        val instanceName = createInitializedInstance()
        val strings = loginStrings("Explicit theme")
        OwnId.getInstanceContainer(instanceName)!!.register<LoginIdCollectStringsProvider>(StaticLoginIdCollectStringsProvider(strings))
        val operationController = RecordingLoginIdCollectOperationController(
            operationId = OperationID(OperationType.LOGIN_ID_COLLECT, "explicit-theme"),
            initialUiState = loginUiState(loginIdValue = "explicit-theme@example.com")
        )
        val uiController = OwnIdOperationUiController(instanceName, operationController)
        val probes = mutableListOf<ThemeProbe>()
        val appColorScheme = lightColorScheme(primary = Color(0xFF111111))
        val explicitColorScheme = lightColorScheme(
            primary = Color(0xFF654321),
            onPrimary = Color(0xFFFFFFFF),
            surface = Color(0xFFF7F7F7),
            onSurface = Color(0xFF010101)
        )
        val explicitTheme = OwnIdTheme(
            colorScheme = explicitColorScheme,
            shapes = Shapes(),
            typography = Typography(),
            colors = OwnIdDefaultColorsLight.copy(primary = Color(0xFF765432))
        )

        composeRule.setContent {
            MaterialTheme(colorScheme = appColorScheme) {
                OwnIdOperationUi(
                    operationUiController = uiController,
                    theme = explicitTheme,
                    loginIdCollectContent = themeProbeContent(probes)
                )
            }
        }

        composeRule.waitUntil { probes.isNotEmpty() }

        assertThat(probes.last()).isEqualTo(
            ThemeProbe(
                materialPrimary = Color(0xFF654321),
                ownIdPrimary = Color(0xFF765432)
            )
        )
    }

    @Test
    fun unsetContentOverrideFallsBackToRegisteredProvider() {
        val instanceName = createInitializedInstance()
        val strings = loginStrings("Current")
        val providerCalls = mutableListOf<LoginContentCall>()
        OwnId.getInstanceContainer(instanceName)!!.apply {
            register<LoginIdCollectStringsProvider>(StaticLoginIdCollectStringsProvider(strings))
            register<LoginIdCollectUiProvider>(RecordingLoginIdCollectUiProvider(recordingContent(providerCalls)))
        }
        val fallbackController = RecordingLoginIdCollectOperationController(
            operationId = OperationID(OperationType.LOGIN_ID_COLLECT, "provider-fallback"),
            initialUiState = loginUiState(loginIdValue = "fallback@example.com")
        )
        val fallbackUiController = OwnIdOperationUiController(instanceName, fallbackController)

        composeRule.setContent {
            OwnIdOperationUi(operationUiController = fallbackUiController)
        }

        composeRule.waitUntil { providerCalls.isNotEmpty() }

        val providerCall = providerCalls.last()
        assertThat(providerCall.loginIdValue).isEqualTo("fallback@example.com")
        assertThat(providerCall.strings).isEqualTo(strings)
    }

    private fun createLoginUiCase(): OperationUiCase<LoginId, LoginIdCollectOperationFailure> {
        val instanceName = createInitializedInstance()
        val operationController = RecordingLoginIdCollectOperationController(
            operationId = OperationID(OperationType.LOGIN_ID_COLLECT, "missing-dependency-${UUID.randomUUID()}"),
            initialUiState = loginUiState(loginIdValue = "missing@example.com")
        )
        return OperationUiCase(instanceName, operationController)
    }

    private fun createEmailUiCase(): OperationUiCase<AccessOrProofToken, EmailVerificationOperationFailure> =
        OperationUiCase(
            instanceName = createInitializedInstance(),
            operationController = RecordingEmailVerificationOperationController(
                OperationID(OperationType.EMAIL_VERIFICATION, "missing-email-dependency-${UUID.randomUUID()}")
            )
        )

    private fun createPhoneUiCase(): OperationUiCase<AccessOrProofToken, PhoneVerificationOperationFailure> =
        OperationUiCase(
            instanceName = createInitializedInstance(),
            operationController = RecordingPhoneVerificationOperationController(
                OperationID(OperationType.PHONE_NUMBER_VERIFICATION, "missing-phone-dependency-${UUID.randomUUID()}")
            )
        )

    private fun <Success, Failure : OperationFailure> OperationUiCase<Success, Failure>.assertSystemAbortAndDismissalWithoutUserClose(
        vararg expectedMessageFragments: String
    ) {
        var closeCalls = 0
        val closeReasons = mutableListOf<Reason?>()
        val containerController = OwnIdUiContainerController(closeAction = { closeCalls += 1 })
        containerController.addClosedHandler { reason -> closeReasons += reason }
        val uiController = OwnIdOperationUiController(instanceName, operationController)

        composeRule.setContent {
            OwnIdOperationUi(
                operationUiController = uiController,
                uiContainerController = containerController
            )
        }

        composeRule.waitUntil { operationController.abortReasons.isNotEmpty() && closeCalls == 1 }

        containerController.markClosed()

        val reason = operationController.abortReasons.single()
        assertThat(reason).isInstanceOf(Reason.SystemError::class.java)
        expectedMessageFragments.forEach { fragment ->
            assertThat((reason as Reason.SystemError).details).contains(fragment)
        }
        assertThat(closeReasons).containsExactly(null)
    }

    private fun createInitializedInstance(): InstanceName {
        val instanceName = InstanceName("operation-ui-runtime-${UUID.randomUUID()}")
        createdInstances += instanceName
        OwnId.initialize(instanceName) {
            applicationContext = app
            appId = "App123"
        }
        return instanceName
    }

    private fun recordingContent(calls: MutableList<LoginContentCall>): LoginIdCollectUiContent =
        { uiState, strings, _, _, errorTextProvider, isReadyForInitialFocus ->
            SideEffect {
                calls += LoginContentCall(
                    loginIdValue = uiState.loginIdValue,
                    strings = strings,
                    mappedErrorText = uiState.error?.let { error -> errorTextProvider?.invoke(error.errorCode) },
                    isReadyForInitialFocus = isReadyForInitialFocus
                )
            }
        }

    private fun themeProbeContent(probes: MutableList<ThemeProbe>): LoginIdCollectUiContent =
        { _, _, _, _, _, _ ->
            val materialPrimary = MaterialTheme.colorScheme.primary
            val ownIdPrimary = LocalOwnIdColors.current.primary
            SideEffect {
                probes += ThemeProbe(materialPrimary = materialPrimary, ownIdPrimary = ownIdPrimary)
            }
        }

    private data class OperationUiCase<Success, Failure : OperationFailure>(
        val instanceName: InstanceName,
        val operationController: RecordingOperationController<Success, Failure>
    )

    private data class ThemeProbe(val materialPrimary: Color, val ownIdPrimary: Color)

    private data class LoginContentCall(
        val loginIdValue: String,
        val strings: LoginIdCollectStrings,
        val mappedErrorText: String?,
        val isReadyForInitialFocus: Boolean
    )
}
