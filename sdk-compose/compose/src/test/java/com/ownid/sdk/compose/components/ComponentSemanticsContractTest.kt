package com.ownid.sdk.compose.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.ui.capability.BoostWidgetStrings
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
// Covers: UI-PRESENT-140, UI-PRESENT-280
internal class ComponentSemanticsContractTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun iconButtonExposesButtonRoleLabelAndEnabledState() {
        var clicks = 0

        composeRule.setContent {
            OwnIdIconButton(
                onClick = { clicks += 1 },
                contentDescription = "Continue with OwnID",
                modifier = Modifier.testTag(ICON_BUTTON_TAG)
            )
        }

        composeRule.onNodeWithTag(ICON_BUTTON_TAG)
            .assertContentDescriptionEquals("Continue with OwnID")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(hasClickAction())
            .assertIsEnabled()
            .performClick()

        composeRule.runOnIdle {
            assertThat(clicks).isEqualTo(1)
        }
    }

    @Test
    fun iconButtonDisabledStateBlocksSemanticClick() {
        var clicks = 0

        composeRule.setContent {
            OwnIdIconButton(
                onClick = { clicks += 1 },
                contentDescription = "Continue with OwnID",
                enabled = false,
                modifier = Modifier.testTag(ICON_BUTTON_TAG)
            )
        }

        composeRule.onNodeWithTag(ICON_BUTTON_TAG)
            .assertContentDescriptionEquals("Continue with OwnID")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertIsNotEnabled()

        composeRule.runOnIdle {
            assertThat(clicks).isEqualTo(0)
        }
    }

    @Test
    fun spinnerExposesIndeterminateProgressSemantics() {
        composeRule.setContent {
            OwnIdSpinner(modifier = Modifier.testTag(SPINNER_TAG))
        }

        composeRule.onNodeWithTag(SPINNER_TAG)
            .assert(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
    }

    @Test
    fun checkmarkIsDecorativeByDefault() {
        composeRule.setContent {
            OwnIdCheckmark(modifier = Modifier.testTag(CHECKMARK_TAG))
        }

        composeRule.onNodeWithTag(CHECKMARK_TAG)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
            .assert(hasClickAction().not())
    }

    @Test
    fun textButtonsExposeButtonRoleLabelAndDisabledState() {
        var primaryClicks = 0
        var textClicks = 0

        composeRule.setContent {
            androidx.compose.foundation.layout.Column {
                OwnIdButton(
                    text = "Continue",
                    onClick = { primaryClicks += 1 },
                    modifier = Modifier.testTag(PRIMARY_BUTTON_TAG)
                )
                OwnIdTextButton(
                    text = "Not you?",
                    onClick = { textClicks += 1 },
                    enabled = false,
                    modifier = Modifier.testTag(TEXT_BUTTON_TAG)
                )
            }
        }

        composeRule.onNodeWithText("Continue")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(hasClickAction())
            .assertIsEnabled()
            .performClick()

        composeRule.onNodeWithText("Not you?")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertIsNotEnabled()

        composeRule.runOnIdle {
            assertThat(primaryClicks).isEqualTo(1)
            assertThat(textClicks).isEqualTo(0)
        }
    }

    @Test
    fun boostButtonUsesWidgetLabelAndBusyDisabledState() {
        var clicks = 0

        composeRule.setContent {
            OwnIdBoostButton(
                onClick = { clicks += 1 },
                isBusy = true,
                widgetStrings = boostWidgetStrings(),
                orText = null
            )
        }

        composeRule.onNodeWithContentDescription(BOOST_BUTTON_LABEL)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertIsNotEnabled()

        composeRule.runOnIdle {
            assertThat(clicks).isEqualTo(0)
        }
    }

    @Test
    fun boostButtonPreservesButtonSemanticsUnderInjectedFontScale() {
        var clicks = 0

        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1.0f, fontScale = 1.8f)) {
                OwnIdBoostButton(
                    onClick = { clicks += 1 },
                    isBusy = false,
                    widgetStrings = boostWidgetStrings(),
                    orText = null
                )
            }
        }

        composeRule.onNodeWithContentDescription(BOOST_BUTTON_LABEL)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertIsEnabled()
            .performClick()

        composeRule.runOnIdle {
            assertThat(clicks).isEqualTo(1)
        }
    }

    private fun boostWidgetStrings(): BoostWidgetStrings =
        BoostWidgetStrings(
            skipPassword = BOOST_BUTTON_LABEL,
            or = "or"
        )

    private companion object {
        private const val BOOST_BUTTON_LABEL = "Continue with OwnID"
        private const val ICON_BUTTON_TAG = "ownid-icon-button"
        private const val SPINNER_TAG = "ownid-spinner"
        private const val CHECKMARK_TAG = "ownid-checkmark"
        private const val PRIMARY_BUTTON_TAG = "ownid-primary-button"
        private const val TEXT_BUTTON_TAG = "ownid-text-button"
    }
}
