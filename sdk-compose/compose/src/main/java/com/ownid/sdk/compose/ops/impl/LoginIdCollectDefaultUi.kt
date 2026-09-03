package com.ownid.sdk.compose.ops.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillNode
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.dropUnlessStarted
import com.ownid.sdk.compose.LocalOwnIdColors
import com.ownid.sdk.compose.OwnIdColors
import com.ownid.sdk.compose.components.OwnIdButton
import com.ownid.sdk.compose.components.OwnIdTextButton
import com.ownid.sdk.compose.components.UserActionThrottler
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.ops.ui.LoginIdCollectUi
import com.ownid.sdk.ui.capability.LoginIdCollectStrings

@Composable
@OptIn(ExperimentalComposeUiApi::class)
internal fun LoginIdCollectDefaultUi(
    uiState: LoginIdCollectUi.UiState,
    strings: LoginIdCollectStrings,
    modifier: Modifier = Modifier,
    colors: OwnIdColors = LocalOwnIdColors.current,
    errorTextProvider: ((ErrorCode) -> String)? = null,
    isReadyForInitialFocus: Boolean = true
) {
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
            text = strings.message,
            modifier = Modifier.padding(top = 24.dp),
            color = colors.onSurfaceVariant,
            fontSize = 18.sp,
            textAlign = TextAlign.Center
        )

        val onLoginIdChange by rememberUpdatedState(uiState.onLoginIdChange)
        val onContinue by rememberUpdatedState(uiState.onContinue)
        val onCancel by rememberUpdatedState(uiState.onCancel)
        val loginIdValue by rememberUpdatedState(uiState.loginIdValue)
        val continueThrottler = remember { UserActionThrottler() }
        val submitLoginId = dropUnlessStarted { continueThrottler.processAction { onContinue() } }

        var currentIdTextField by rememberSaveable(stateSaver = TextFieldValue.Saver) {
            mutableStateOf(TextFieldValue(uiState.loginIdValue, selection = TextRange(uiState.loginIdValue.length)))
        }
        val focusRequester = remember { FocusRequester() }

        LaunchedEffect(uiState.loginIdValue) {
            if (currentIdTextField.text != uiState.loginIdValue) {
                currentIdTextField = TextFieldValue(text = uiState.loginIdValue, selection = TextRange(uiState.loginIdValue.length))
            }
        }

        LaunchedEffect(isReadyForInitialFocus) {
            if (!isReadyForInitialFocus) return@LaunchedEffect
            focusRequester.requestFocus()
        }

        val autofillTree = LocalAutofillTree.current
        val autofill = LocalAutofill.current
        var isAutofillFieldFocused by remember { mutableStateOf(false) }
        var latestAutofillBounds by remember { mutableStateOf<Rect?>(null) }
        val autofillNode = remember(uiState.collectableLoginIdTypes) {
            AutofillNode(
                autofillTypes = uiState.collectableLoginIdTypes.map { type ->
                    when (type) {
                        LoginId.Type.Email -> AutofillType.EmailAddress
                        LoginId.Type.PhoneNumber -> AutofillType.PhoneNumber
                        LoginId.Type.UserName -> AutofillType.Username
                        else -> null
                    }
                }.filterNotNull(),
                boundingBox = latestAutofillBounds,
                onFill = { filledText ->
                    currentIdTextField = TextFieldValue(text = filledText, selection = TextRange(filledText.length))
                    if (loginIdValue != filledText) {
                        onLoginIdChange(filledText)
                    }
                }
            )
        }
        val activeAutofill = autofill?.takeIf { isAutofillFieldFocused && latestAutofillBounds != null }
        DisposableEffect(autofillTree, autofillNode, activeAutofill) {
            autofillTree += autofillNode
            activeAutofill?.requestAutofillForNode(autofillNode)
            onDispose {
                activeAutofill?.cancelAutofillForNode(autofillNode)
                autofillTree.children.remove(autofillNode.id)
            }
        }

        val keyboard = LocalSoftwareKeyboardController.current
        val keyboardOptions = remember(uiState.collectableLoginIdTypes) {
            KeyboardOptions.Default.copy(
                keyboardType = when {
                    uiState.collectableLoginIdTypes.size != 1 -> KeyboardType.Text
                    uiState.collectableLoginIdTypes.first() == LoginId.Type.Email -> KeyboardType.Email
                    uiState.collectableLoginIdTypes.first() == LoginId.Type.PhoneNumber -> KeyboardType.Phone
                    else -> KeyboardType.Text
                },
                imeAction = ImeAction.Done,
                autoCorrectEnabled = false,
                capitalization = KeyboardCapitalization.None
            )
        }

        val localizedErrorMessage by remember(uiState.error, errorTextProvider, strings) {
            derivedStateOf {
                uiState.error?.let { error ->
                    if (error.errorCode == ErrorCode.LoginIdValidationFailed) strings.error
                    else errorTextProvider?.invoke(error.errorCode) ?: error.localizedMessage
                }
            }
        }
        val isError = localizedErrorMessage != null

        OutlinedTextField(
            value = currentIdTextField,
            onValueChange = { textField ->
                currentIdTextField = textField
                if (loginIdValue != textField.text) {
                    onLoginIdChange(textField.text)
                }
            },
            modifier = Modifier
                .padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 8.dp)
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onGloballyPositioned { coordinates ->
                    coordinates.boundsInWindow().let { bounds ->
                        autofillNode.boundingBox = bounds
                        latestAutofillBounds = bounds
                    }
                }
                .onFocusChanged { focusState ->
                    isAutofillFieldFocused = focusState.isFocused
                },
            keyboardOptions = keyboardOptions,
            textStyle = LocalTextStyle.current.copy(
                textAlign = TextAlign.Left,
                textDirection = TextDirection.Ltr
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    autofill?.cancelAutofillForNode(autofillNode)
                    keyboard?.hide()
                    submitLoginId()
                }
            ),
            placeholder = { Text(text = strings.placeholder) },
            supportingText = {
                Text(
                    text = localizedErrorMessage ?: " ",
                    modifier = if (isError) {
                        Modifier
                    } else {
                        Modifier
                            .alpha(0f)
                            .clearAndSetSemantics { invisibleToUser() }
                    }
                )
            },
            isError = isError,
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = colors.onSurface,
                unfocusedTextColor = colors.onSurface,
                disabledTextColor = colors.onSurface.copy(alpha = 0.38f),
                errorTextColor = colors.onSurface,
                focusedContainerColor = colors.fieldBackground,
                unfocusedContainerColor = colors.fieldBackground,
                disabledContainerColor = colors.fieldBackground,
                errorContainerColor = colors.fieldBackground,
                cursorColor = colors.primary,
                errorCursorColor = colors.error,
                focusedBorderColor = colors.primary,
                unfocusedBorderColor = colors.onSurfaceVariant,
                disabledBorderColor = colors.onSurfaceVariant.copy(alpha = 0.12f),
                errorBorderColor = colors.error,
                errorSupportingTextColor = colors.error
            )
        )

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

            OwnIdButton(
                text = strings.cta,
                onClick = submitLoginId,
                modifier = Modifier.padding(end = 8.dp),
                colors = colors
            )
        }
    }
}
