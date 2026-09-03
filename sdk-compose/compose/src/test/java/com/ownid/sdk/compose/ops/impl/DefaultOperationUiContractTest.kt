package com.ownid.sdk.compose.ops.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.Autofill
import androidx.compose.ui.autofill.AutofillNode
import androidx.compose.ui.autofill.AutofillTree
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.compose.assertEditableText
import com.ownid.sdk.compose.assertTextLayoutDirection
import com.ownid.sdk.compose.loginStrings
import com.ownid.sdk.compose.loginUiState
import com.ownid.sdk.model.ChallengeId
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.OperationChannel
import com.ownid.sdk.model.Timeout
import com.ownid.sdk.model.UiError
import com.ownid.sdk.model.VerificationChallenge
import com.ownid.sdk.ops.ui.EmailVerificationUi
import com.ownid.sdk.ops.ui.PhoneVerificationUi
import com.ownid.sdk.ui.capability.EmailVerificationStrings
import com.ownid.sdk.ui.capability.PhoneVerificationStrings
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalComposeUiApi::class)
// Covers: UI-PRESENT-120, UI-PRESENT-210, UI-PRESENT-220, UI-PRESENT-230
internal class DefaultOperationUiContractTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loginIdTypingUpdatesEditableTextAndExposesDoneAction() {
        val uiState = loginUiState(loginIdValue = "")
        val strings = loginStrings("Collect")

        composeRule.setContent {
            LoginIdCollectDefaultUi(
                uiState = uiState,
                strings = strings,
                isReadyForInitialFocus = true
            )
        }

        composeRule.onNode(hasSetTextAction()).assertIsFocused().performTextReplacement("user@example.com")
        composeRule.onNode(hasSetTextAction()).assertEditableText("user@example.com")
        composeRule.onNode(hasSetTextAction()).assert(SemanticsMatcher.keyIsDefined(SemanticsActions.OnImeAction))
    }

    @Test
    fun loginIdAutofillReplacesFocusedNodeWithoutDuplicateRequestsOrLeaks() {
        val requestedNodes = mutableListOf<AutofillNode>()
        val cancelledNodes = mutableListOf<AutofillNode>()
        val autofillTree = AutofillTree()
        val autofill = object : Autofill {
            override fun requestAutofillForNode(autofillNode: AutofillNode) {
                checkNotNull(autofillNode.boundingBox) { "Autofill request must follow field positioning" }
                requestedNodes += autofillNode
            }

            override fun cancelAutofillForNode(autofillNode: AutofillNode) {
                cancelledNodes += autofillNode
            }
        }
        var uiState by mutableStateOf(loginUiState(loginIdValue = ""))
        var showContent by mutableStateOf(true)

        composeRule.setContent {
            CompositionLocalProvider(
                LocalAutofill provides autofill,
                LocalAutofillTree provides autofillTree
            ) {
                if (showContent) {
                    LoginIdCollectDefaultUi(
                        uiState = uiState,
                        strings = loginStrings("Collect"),
                        isReadyForInitialFocus = true
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val emailNode = requestedNodes.single()
        assertThat(cancelledNodes).isEmpty()
        assertThat(emailNode.autofillTypes).containsExactly(AutofillType.EmailAddress)
        assertThat(autofillTree.children.values).containsExactly(emailNode)

        composeRule.runOnIdle {
            uiState = uiState.copy(collectableLoginIdTypes = listOf(LoginId.Type.PhoneNumber))
        }
        composeRule.waitForIdle()

        val phoneNode = requestedNodes.last()
        assertThat(requestedNodes).containsExactly(emailNode, phoneNode).inOrder()
        assertThat(cancelledNodes).containsExactly(emailNode)
        assertThat(phoneNode.autofillTypes).containsExactly(AutofillType.PhoneNumber)
        assertThat(autofillTree.children.values).containsExactly(phoneNode)

        composeRule.runOnIdle {
            showContent = false
        }
        composeRule.waitForIdle()

        assertThat(cancelledNodes).containsExactly(emailNode, phoneNode).inOrder()
        assertThat(autofillTree.children).isEmpty()
    }

    @Test
    fun loginIdTextEntryKeepsLtrTextLayoutInsideRtlComposition() {
        val uiState = loginUiState(loginIdValue = "user@example.com")
        val strings = loginStrings("Collect")

        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                LoginIdCollectDefaultUi(
                    uiState = uiState,
                    strings = strings,
                    isReadyForInitialFocus = false
                )
            }
        }

        composeRule.onNode(hasSetTextAction())
            .assertEditableText("user@example.com")
            .assertTextLayoutDirection(TextDirection.Ltr)
    }

    @Test
    fun loginIdValidationErrorRecoversOnUpdatedStateAndActionsAreExposed() {
        val strings = loginStrings("Collect")
        var uiState by mutableStateOf(loginUiState(loginIdValue = "bad", error = UiError(ErrorCode.LoginIdValidationFailed, "Invalid")))

        composeRule.setContent {
            LoginIdCollectDefaultUi(
                uiState = uiState,
                strings = strings,
                isReadyForInitialFocus = false
            )
        }

        composeRule.onNodeWithText(strings.error).assertExists()

        composeRule.onNode(hasSetTextAction()).performTextInput("@example.com")
        composeRule.onNode(hasSetTextAction()).assertEditableText("bad@example.com")
        composeRule.runOnIdle {
            uiState = uiState.copy(loginIdValue = "bad@example.com", error = null)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(strings.error).assertDoesNotExist()

        composeRule.onNode(hasText(strings.cta) and hasClickAction()).assertExists()
        composeRule.onNode(hasText(strings.cancel) and hasClickAction()).assertExists()
    }

    @Test
    fun loginIdContinueAndCancelActionsInvokeCallbacks() {
        val callbacks = mutableListOf<String>()
        val strings = loginStrings("Collect")
        val uiState = loginUiState(loginIdValue = "user@example.com").copy(
            onContinue = { callbacks += "continue" },
            onCancel = { callbacks += "cancel" }
        )

        composeRule.setContent {
            LoginIdCollectDefaultUi(
                uiState = uiState,
                strings = strings,
                isReadyForInitialFocus = false
            )
        }

        composeRule.onNode(hasText(strings.cta) and hasClickAction()).assertIsEnabled().performClick()
        composeRule.onNode(hasText(strings.cancel) and hasClickAction()).assertIsEnabled().performClick()

        composeRule.runOnIdle {
            assertThat(callbacks).containsExactly("continue", "cancel").inOrder()
        }
    }

    @Test
    fun loginIdUiKeepsEditableFieldInsideConstrainedPhoneWidth() {
        val strings = loginStrings("Collect")

        setConstrainedPhoneContent {
            LoginIdCollectDefaultUi(
                uiState = loginUiState(loginIdValue = "user@example.com"),
                strings = strings,
                isReadyForInitialFocus = false
            )
        }

        composeRule.onNode(hasSetTextAction())
            .assertEditableText("user@example.com")
            .assertBoundsInsideConstrainedPhoneWidth()
    }

    @Test
    fun emailVerificationNormalizesOtpAndSubmitsOnlyAtExpectedLength() {
        val submittedCodes = mutableListOf<String>()
        val strings = emailStrings()

        composeRule.setContent {
            EmailVerificationDefaultUi(
                uiState = EmailVerificationUi.UiState(
                    challenge = challenge(length = 6, channel = "api-returned-email-channel"),
                    onCodeEntered = { code -> submittedCodes += code }
                ),
                strings = strings,
                isReadyForInitialFocus = false
            )
        }

        composeRule.onNodeWithText("Send 6 digits to api-returned-email-channel").assertExists()

        composeRule.onNodeWithContentDescription(strings.description).performTextInput("１a٢ ۳")
        composeRule.onNodeWithContentDescription(strings.description).assertEditableText("123")

        composeRule.runOnIdle {
            assertThat(submittedCodes).isEmpty()
        }

        composeRule.onNodeWithContentDescription(strings.description).performTextInput("-۴5۶")
        composeRule.onNodeWithContentDescription(strings.description).assertEditableText("123456")

        composeRule.waitUntil { submittedCodes.isNotEmpty() }

        assertThat(submittedCodes).containsExactly("123456")
    }

    @Test
    fun emailVerificationOtpEntryKeepsLtrTextLayoutInsideRtlComposition() {
        val strings = emailStrings()

        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                EmailVerificationDefaultUi(
                    uiState = EmailVerificationUi.UiState(
                        challenge = challenge(length = 6, channel = "masked@example.com"),
                        onCodeEntered = {}
                    ),
                    strings = strings,
                    isReadyForInitialFocus = false
                )
            }
        }

        composeRule.onNodeWithContentDescription(strings.description)
            .performTextInput("123")
        composeRule.onNodeWithContentDescription(strings.description)
            .assertEditableText("123")
            .assertTextLayoutDirection(TextDirection.Ltr)
    }

    @Test
    fun emailVerificationOtpFieldStaysInsideConstrainedPhoneWidth() {
        val strings = emailStrings()

        setConstrainedPhoneContent {
            EmailVerificationDefaultUi(
                uiState = EmailVerificationUi.UiState(
                    challenge = challenge(length = 6, channel = "masked@example.com"),
                    onCodeEntered = {}
                ),
                strings = strings,
                isReadyForInitialFocus = false
            )
        }

        composeRule.onNodeWithContentDescription(strings.description)
            .assertEditableText("")
            .assertBoundsInsideConstrainedPhoneWidth()
    }

    @Test
    fun emailVerificationOtpFieldStaysUsableUnderInjectedFontScale() {
        val strings = emailStrings()
        val submittedCodes = mutableListOf<String>()

        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1.0f, fontScale = 1.5f)) {
                EmailVerificationDefaultUi(
                    uiState = EmailVerificationUi.UiState(
                        challenge = challenge(length = 6, channel = "masked@example.com"),
                        onCodeEntered = { code -> submittedCodes += code }
                    ),
                    strings = strings,
                    isReadyForInitialFocus = false
                )
            }
        }

        composeRule.onNodeWithContentDescription(strings.description)
            .assertEditableText("")
            .performTextInput("123456")
        composeRule.onNodeWithContentDescription(strings.description)
            .assertEditableText("123456")
        composeRule.waitUntil { submittedCodes.isNotEmpty() }

        assertThat(submittedCodes).containsExactly("123456")
    }

    @Test
    fun emailVerificationClearsForReentryAndUpdatesMappedErrorText() {
        val submittedCodes = mutableListOf<String>()
        val strings = emailStrings()
        var uiState by mutableStateOf(
            EmailVerificationUi.UiState(
                challenge = challenge(length = 6, channel = "masked@example.com"),
                onCodeEntered = { code -> submittedCodes += code }
            )
        )

        composeRule.setContent {
            EmailVerificationDefaultUi(
                uiState = uiState,
                strings = strings,
                errorTextProvider = { code -> "mapped:${code.value}" },
                isReadyForInitialFocus = false
            )
        }

        composeRule.onNodeWithContentDescription(strings.description).performTextInput("123456")
        composeRule.waitUntil { submittedCodes == listOf("123456") }

        composeRule.runOnIdle {
            uiState = uiState.copy(error = UiError(ErrorCode.VerificationCodeWrong, "Wrong code"))
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription(strings.description).assertIsFocused()
        composeRule.onNodeWithContentDescription(strings.description).assertEditableText("")
        composeRule.onNodeWithText("mapped:verification_code_wrong").assertExists()

        composeRule.onNodeWithContentDescription(strings.description).performTextInput("12")
        composeRule.onNodeWithContentDescription(strings.description).assertEditableText("12")
        composeRule.onNodeWithText("mapped:verification_code_wrong").assertDoesNotExist()

        composeRule.onNodeWithContentDescription(strings.description).performTextInput("3456")
        composeRule.onNodeWithContentDescription(strings.description).assertEditableText("123456")
        composeRule.runOnIdle {
            assertThat(submittedCodes).containsExactly("123456")
        }

        composeRule.runOnIdle {
            uiState = uiState.copy(error = UiError(ErrorCode.Network, "Network error"))
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription(strings.description).assertEditableText("")
        composeRule.onNodeWithText("mapped:network").assertExists()
    }

    @Test
    fun verificationOtpStateResetsWhenChallengeIdChanges() {
        val emailStrings = emailStrings()
        val phoneStrings = phoneStrings()
        var emailChallenge by mutableStateOf(challenge(id = "email-challenge-1", length = 6, channel = "masked@example.com"))
        var phoneChallenge by mutableStateOf(challenge(id = "phone-challenge-1", length = 4, channel = "+1 *** 0100"))

        composeRule.setContent {
            Column {
                EmailVerificationDefaultUi(
                    uiState = EmailVerificationUi.UiState(challenge = emailChallenge),
                    strings = emailStrings,
                    isReadyForInitialFocus = false
                )
                PhoneVerificationDefaultUi(
                    uiState = PhoneVerificationUi.UiState(challenge = phoneChallenge),
                    strings = phoneStrings,
                    isReadyForInitialFocus = false
                )
            }
        }

        composeRule.onNodeWithContentDescription(emailStrings.description).performTextInput("12")
        composeRule.onNodeWithContentDescription(phoneStrings.description).performTextInput("34")
        composeRule.onNodeWithContentDescription(emailStrings.description).assertEditableText("12")
        composeRule.onNodeWithContentDescription(phoneStrings.description).assertEditableText("34")

        composeRule.runOnIdle {
            emailChallenge = challenge(id = "email-challenge-2", length = 6, channel = "masked@example.com")
            phoneChallenge = challenge(id = "phone-challenge-2", length = 4, channel = "+1 *** 0100")
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription(emailStrings.description).assertEditableText("")
        composeRule.onNodeWithContentDescription(phoneStrings.description).assertEditableText("")
    }

    @Test
    fun emailVerificationErrorExposesPoliteLiveRegionAndBusyExposesProgress() {
        val strings = emailStrings()
        var uiState by mutableStateOf(
            EmailVerificationUi.UiState(
                challenge = challenge(length = 6, channel = "masked@example.com"),
                busy = true,
                onCodeEntered = {}
            )
        )

        composeRule.setContent {
            EmailVerificationDefaultUi(
                uiState = uiState,
                strings = strings,
                errorTextProvider = { code -> "mapped:${code.value}" },
                isReadyForInitialFocus = false
            )
        }

        composeRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
            .assertExists()

        composeRule.runOnIdle {
            uiState = uiState.copy(
                busy = false,
                error = UiError(ErrorCode.VerificationCodeWrong, "Wrong code")
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("mapped:verification_code_wrong")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    @Test
    fun emailVerificationResendAndNotYouCallbacksAreGatedByBusyState() {
        val callbacks = mutableListOf<String>()
        val strings = emailStrings()
        var uiState by mutableStateOf(
            EmailVerificationUi.UiState(
                challenge = challenge(length = 6, channel = "masked@example.com", allowResend = true, resendDebounce = 0),
                busy = true,
                onResend = { callbacks += "resend" },
                onNotYou = { callbacks += "not-you" },
                onCancel = { callbacks += "cancel" }
            )
        )

        composeRule.setContent {
            EmailVerificationDefaultUi(
                uiState = uiState,
                strings = strings,
                isReadyForInitialFocus = false
            )
        }

        composeRule.onNodeWithText(strings.resend).assertIsNotEnabled()
        composeRule.onNodeWithText(strings.notYou).assertIsNotEnabled()
        composeRule.runOnIdle { assertThat(callbacks).isEmpty() }

        composeRule.runOnIdle { uiState = uiState.copy(busy = false) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(strings.resend).assertIsEnabled().performClick()
        composeRule.onNodeWithText(strings.notYou).assertIsEnabled().performClick()
        composeRule.onNodeWithText(strings.cancel).performClick()

        composeRule.runOnIdle {
            assertThat(callbacks).containsExactly("resend", "not-you", "cancel").inOrder()
        }
    }

    @Test
    fun phoneVerificationUsesChannelForPlaceholdersAndBlocksBusyInput() {
        val submittedCodes = mutableListOf<String>()
        val strings = phoneStrings()
        var uiState by mutableStateOf(
            PhoneVerificationUi.UiState(
                challenge = challenge(length = 4, channel = "api-returned-phone-channel"),
                busy = true,
                onCodeEntered = { code -> submittedCodes += code }
            )
        )

        composeRule.setContent {
            PhoneVerificationDefaultUi(
                uiState = uiState,
                strings = strings,
                isReadyForInitialFocus = false
            )
        }

        composeRule.onNodeWithText("Text 4 to api-returned-phone-channel").assertExists()

        composeRule.onNodeWithContentDescription(strings.description).performTextInput("۱۲۳۴")
        composeRule.onNodeWithContentDescription(strings.description).assertEditableText("")

        composeRule.runOnIdle {
            assertThat(submittedCodes).isEmpty()
            uiState = uiState.copy(busy = false)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription(strings.description).performTextReplacement("۱۲۳۴")
        composeRule.onNodeWithContentDescription(strings.description).assertEditableText("1234")
        composeRule.waitUntil { submittedCodes.isNotEmpty() }

        assertThat(submittedCodes).containsExactly("1234")
    }

    @Test
    fun phoneVerificationOtpFieldStaysInsideConstrainedPhoneWidth() {
        val strings = phoneStrings()

        setConstrainedPhoneContent {
            PhoneVerificationDefaultUi(
                uiState = PhoneVerificationUi.UiState(
                    challenge = challenge(length = 4, channel = "+1 *** 0100"),
                    onCodeEntered = {}
                ),
                strings = strings,
                isReadyForInitialFocus = false
            )
        }

        composeRule.onNodeWithContentDescription(strings.description)
            .assertEditableText("")
            .assertBoundsInsideConstrainedPhoneWidth()
    }

    private fun setConstrainedPhoneContent(content: @Composable () -> Unit) {
        composeRule.setContent {
            Box(Modifier.requiredWidth(CONSTRAINED_PHONE_WIDTH)) {
                content()
            }
        }
    }

    private fun SemanticsNodeInteraction.assertBoundsInsideConstrainedPhoneWidth(): SemanticsNodeInteraction {
        val bounds = fetchSemanticsNode().boundsInRoot
        assertThat(bounds.left).isAtLeast(0.0f)
        assertThat(bounds.right).isAtMost(CONSTRAINED_PHONE_WIDTH.value + 0.5f)
        return this
    }

    private fun emailStrings(): EmailVerificationStrings =
        EmailVerificationStrings(
            title = "Email title",
            message = "Send %CODE_LENGTH% digits to %LOGIN_ID%",
            description = "Email code",
            resend = "Resend",
            cancel = "Cancel",
            notYou = "Not you?"
        )

    private fun phoneStrings(): PhoneVerificationStrings =
        PhoneVerificationStrings(
            title = "Phone title",
            message = "Text %CODE_LENGTH% to %LOGIN_ID%",
            description = "Phone code",
            resend = "Resend",
            cancel = "Cancel",
            notYou = "Not you?"
        )

    private fun challenge(
        length: Int,
        channel: String,
        allowResend: Boolean = false,
        resendDebounce: Int = 1,
        id: String = "challenge-$length-$channel"
    ): VerificationChallenge =
        VerificationChallenge(
            challengeId = ChallengeId(id),
            resendPolicy = VerificationChallenge.ResendPolicy(allow = allowResend, attempts = if (allowResend) 1 else 0, debounce = resendDebounce),
            timeout = Timeout(60_000),
            attempts = 3,
            methods = VerificationChallenge.Methods(
                otp = VerificationChallenge.Methods.Otp(length = length),
                magicLink = null
            ),
            channel = OperationChannel(channel = channel, id = "channel-$length")
        )

    private companion object {
        private val CONSTRAINED_PHONE_WIDTH = 360.dp
    }
}
