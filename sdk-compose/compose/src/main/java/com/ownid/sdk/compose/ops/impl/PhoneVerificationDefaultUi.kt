package com.ownid.sdk.compose.ops.impl

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ownid.sdk.compose.LocalOwnIdColors
import com.ownid.sdk.compose.OwnIdColors
import com.ownid.sdk.compose.components.OtpCodeEditor
import com.ownid.sdk.compose.components.OwnIdSpinner
import com.ownid.sdk.compose.components.OwnIdTextButton
import com.ownid.sdk.compose.components.UserActionThrottler
import com.ownid.sdk.compose.components.normalizeDigits
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.ops.ui.PhoneVerificationUi
import com.ownid.sdk.ui.capability.PhoneVerificationStrings
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun PhoneVerificationDefaultUi(
    uiState: PhoneVerificationUi.UiState,
    strings: PhoneVerificationStrings,
    modifier: Modifier = Modifier,
    colors: OwnIdColors = LocalOwnIdColors.current,
    errorTextProvider: ((ErrorCode) -> String)? = null,
    isReadyForInitialFocus: Boolean = true
) {
    val challenge = uiState.challenge
    val otpLength = remember(challenge) { challenge.methods.otp?.length ?: 4 }
    val resendPolicy = remember(challenge) { challenge.resendPolicy }
    val recipient = remember(challenge) { challenge.channel.channel }

    val onEntered by rememberUpdatedState(uiState.onCodeEntered)
    val onResend by rememberUpdatedState(uiState.onResend)
    val onCancel by rememberUpdatedState(uiState.onCancel)
    val onNotYou by rememberUpdatedState(uiState.onNotYou)
    val otpSubmitThrottler = remember(challenge.challengeId) { UserActionThrottler() }

    var currentField by rememberSaveable(challenge.challengeId, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }

    var showError by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.error) {
        showError = uiState.error != null
        if (uiState.error != null) currentField = TextFieldValue("")
    }

    LaunchedEffect(currentField.text, otpLength) {
        if (currentField.text.length == otpLength && !uiState.busy) {
            otpSubmitThrottler.processAction { onEntered(currentField.text) }
        }
    }

    Column(
        modifier = modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = strings.title,
            modifier = Modifier.padding(top = 8.dp),
            color = colors.onSurface,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            text = strings.message
                .replace("%CODE_LENGTH%", otpLength.toString())
                .replace("%LOGIN_ID%", recipient),
            modifier = Modifier.padding(top = 24.dp),
            color = colors.onSurfaceVariant,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )

        Text(
            text = strings.description,
            modifier = Modifier.padding(top = 16.dp),
            color = colors.onSurface,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )

        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(isReadyForInitialFocus) {
            if (!isReadyForInitialFocus) return@LaunchedEffect
            focusRequester.requestFocus()
        }
        OtpCodeEditor(
            field = currentField,
            onNewField = { newValue ->
                if (uiState.busy.not()) {
                    val normalized = newValue.text.normalizeDigits(otpLength)
                    if (normalized.length < otpLength) showError = false
                    currentField = TextFieldValue(text = normalized, selection = TextRange(normalized.length))
                }
            },
            codeLength = otpLength,
            showError = showError,
            colors = colors,
            contentDescription = strings.description,
            modifier = Modifier
                .padding(top = 16.dp)
                .focusRequester(focusRequester),
        )

        val localizedErrorMessage by remember(uiState.error, errorTextProvider) {
            derivedStateOf {
                uiState.error?.let { error -> errorTextProvider?.invoke(error.errorCode) ?: error.localizedMessage } ?: ""
            }
        }
        val shouldShowError = uiState.busy.not() && showError && uiState.error != null

        Box(
            modifier = Modifier
                .padding(top = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            OwnIdSpinner(
                modifier = Modifier
                    .alpha(if (uiState.busy) 1f else 0f)
                    .semantics { if (!uiState.busy) invisibleToUser() }
                    .size(32.dp),
                colors = colors
            )

            Text(
                text = localizedErrorMessage,
                modifier = Modifier
                    .alpha(if (shouldShowError) 1f else 0f)
                    .then(
                        if (shouldShowError) {
                            Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                        } else {
                            Modifier.clearAndSetSemantics { invisibleToUser() }
                        }
                    ),
                color = colors.error,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )

            var resendAvailableAtMillis by rememberSaveable(challenge.challengeId) {
                mutableLongStateOf(SystemClock.elapsedRealtime() + TimeUnit.SECONDS.toMillis(resendPolicy.debounce.toLong()))
            }
            var isResendAvailable by remember(challenge.challengeId) { mutableStateOf(false) }
            LaunchedEffect(challenge.challengeId, resendAvailableAtMillis, resendPolicy.allow) {
                if (!resendPolicy.allow) {
                    isResendAvailable = false
                    return@LaunchedEffect
                }

                val remainingWaitMillis = resendAvailableAtMillis - SystemClock.elapsedRealtime()
                if (remainingWaitMillis <= 0L) {
                    isResendAvailable = true
                } else {
                    isResendAvailable = false
                    delay(remainingWaitMillis.milliseconds)
                    isResendAvailable = true
                }
            }

            val canResend by remember(uiState.busy, uiState.error, resendPolicy) {
                derivedStateOf { !uiState.busy && uiState.error?.errorCode != ErrorCode.MaximumResendAttemptsReached && resendPolicy.allow }
            }
            val resendVisible = canResend && isResendAvailable && !showError
            OwnIdTextButton(
                text = strings.resend,
                onClick = {
                    if (resendVisible) {
                        isResendAvailable = false
                        resendAvailableAtMillis = SystemClock.elapsedRealtime() + TimeUnit.SECONDS.toMillis(resendPolicy.debounce.toLong())
                        onResend()
                    }
                },
                modifier = Modifier
                    .alpha(if (resendVisible) 1f else 0f)
                    .semantics { if (!resendVisible) invisibleToUser() },
                enabled = resendVisible,
                colors = colors
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OwnIdTextButton(
                text = strings.cancel,
                onClick = onCancel,
                modifier = Modifier.padding(start = 8.dp),
                colors = colors
            )

            OwnIdTextButton(
                text = strings.notYou,
                onClick = { if (!uiState.busy) onNotYou() },
                modifier = Modifier.padding(end = 8.dp),
                enabled = !uiState.busy,
                colors = colors
            )
        }
    }
}
