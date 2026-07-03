package com.ownid.sdk.compose.components

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.dropUnlessStarted
import com.ownid.sdk.compose.LocalOwnIdColors
import com.ownid.sdk.compose.OwnIdColors

internal class UserActionThrottler(
    private val throttleDelayMs: Long = 300L,
    private val uptimeMillis: () -> Long = { SystemClock.uptimeMillis() }
) {
    private var lastActionTimeMs: Long? = null

    internal fun processAction(action: () -> Unit) {
        val currentTime = uptimeMillis()
        val lastActionTimeMs = lastActionTimeMs
        if (lastActionTimeMs == null || currentTime - lastActionTimeMs >= throttleDelayMs) {
            this.lastActionTimeMs = currentTime
            action()
        }
    }
}

/**
 * Internal text-only action button that follows the current OwnID color palette.
 *
 * The caller owns the enabled state and action. Disabled buttons do not invoke [onClick]; enabled clicks are
 * lifecycle-aware and ignore rapid repeat taps. The visible [text] is also the control's accessibility label.
 */
@Composable
internal fun OwnIdTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: OwnIdColors = LocalOwnIdColors.current
) {
    val throttleOnClick = remember { UserActionThrottler() }

    TextButton(
        onClick = dropUnlessStarted { throttleOnClick.processAction { onClick() } },
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors().copy(
            containerColor = Color.Transparent,
            contentColor = colors.primary,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = colors.primary.copy(alpha = 0.38f)
        )
    ) {
        Text(text = text)
    }
}

/**
 * Internal one-time-passcode editor used by OwnID verification UI.
 *
 * The caller owns the text state and validation state. [contentDescription] supplies the field's accessibility
 * label because the visual code slots are not enough context for assistive technology. [codeLength] controls the
 * expected visual width; the editor keeps a stable minimum width so empty and filled states occupy the same space.
 */
@Composable
internal fun OtpCodeEditor(
    field: TextFieldValue,
    onNewField: (TextFieldValue) -> Unit,
    codeLength: Int,
    showError: Boolean,
    colors: OwnIdColors,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val fontScale = density.fontScale
    val textStyle = LocalTextStyle.current.copy(
        color = colors.onSurface,
        fontSize = 38.sp,
        letterSpacing = 16.sp,
        textAlign = TextAlign.Left,
        textDirection = TextDirection.Ltr,
        lineHeight = 38.sp
    )

    val codeLengthString = remember(codeLength) { "8".repeat(codeLength) }
    val textLayoutResult = remember(codeLength, textStyle, fontScale) {
        textMeasurer.measure(
            text = buildAnnotatedString { append(codeLengthString) },
            style = textStyle
        )
    }

    val measuredWidthDp = with(LocalDensity.current) { textLayoutResult.size.width.toDp() }
    val finalMinWidth = measuredWidthDp + 28.dp

    BasicTextField(
        value = field,
        onValueChange = onNewField,
        modifier = modifier
            .widthIn(min = finalMinWidth)
            .width(IntrinsicSize.Min)
            .semantics { this.contentDescription = contentDescription },
        textStyle = textStyle,
        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
        singleLine = true,
        cursorBrush = SolidColor(colors.onSurface),
        decorationBox = { innerTextField ->
            Column(
                modifier = Modifier
                    .clip(shape = MaterialTheme.shapes.extraSmall)
                    .background(color = colors.fieldBackground)
                    .padding(top = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(modifier = Modifier.padding(start = 4.dp)) {
                        if (field.text.isEmpty()) { // Fix for layout not to jump when code is empty
                            Text(
                                text = "\u200B".repeat(codeLength),
                                style = LocalTextStyle.current.copy(
                                    fontSize = 38.sp,
                                    letterSpacing = 16.sp,
                                    lineHeight = 38.sp
                                ),
                                color = Color.Transparent
                            )
                        }
                        innerTextField()
                    }
                }
                Spacer(
                    Modifier
                        .height(2.dp)
                        .fillMaxWidth()
                        .background(if (showError) colors.error else colors.primary)
                )
            }
        }
    )
}

/**
 * Internal primary action button that follows the current OwnID color palette.
 *
 * The caller owns the enabled state and action. Disabled buttons do not invoke [onClick]; enabled clicks are
 * lifecycle-aware and ignore rapid repeat taps. The visible [text] is also the control's accessibility label.
 */
@Composable
internal fun OwnIdButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: OwnIdColors = LocalOwnIdColors.current
) {
    val throttleOnClick = remember { UserActionThrottler() }

    Button(
        onClick = dropUnlessStarted { throttleOnClick.processAction { onClick() } },
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors().copy(
            containerColor = colors.primary,
            contentColor = colors.onPrimary,
            disabledContainerColor = colors.primary.copy(alpha = 0.12f),
            disabledContentColor = colors.onPrimary.copy(alpha = 0.38f)
        )
    ) {
        Text(text = text)
    }
}
