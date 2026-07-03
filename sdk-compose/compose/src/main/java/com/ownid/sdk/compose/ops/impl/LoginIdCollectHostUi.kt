package com.ownid.sdk.compose.ops.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ownid.sdk.compose.LocalOwnIdColors
import com.ownid.sdk.compose.OwnIdUiContainerController
import com.ownid.sdk.compose.components.OwnIdSpinner
import com.ownid.sdk.compose.ops.LoginIdCollectUiContent
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.ops.LoginIdCollectOperation
import com.ownid.sdk.ops.LoginIdCollectOperationController
import com.ownid.sdk.ui.capability.LoginIdCollectStringsParams
import com.ownid.sdk.ui.capability.LoginIdCollectStringsProvider

@Composable
@InternalOwnIdAPI
internal fun LoginIdCollectHostUi(
    controller: LoginIdCollectOperationController,
    content: LoginIdCollectUiContent,
    stringsProvider: LoginIdCollectStringsProvider,
    modifier: Modifier = Modifier,
    containerController: OwnIdUiContainerController? = null,
    errorTextProvider: ((ErrorCode) -> String)? = null
) {
    val operationState by controller.state.collectAsStateWithLifecycle(initialValue = null)
    val uiState = (operationState as? LoginIdCollectOperation.State.Active)?.uiState

    val loginIdTypes = uiState?.collectableLoginIdTypes
    val stringsFlow = remember(controller, stringsProvider, loginIdTypes) {
        loginIdTypes?.let { stringsProvider.getStrings(LoginIdCollectStringsParams(it)) }
    }
    val uiStrings = stringsFlow?.collectAsStateWithLifecycle(initialValue = null)?.value

    val isReadyForInitialFocus by produceState(initialValue = containerController == null, containerController) {
        containerController?.awaitOpened()
        value = true
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 196.dp)
    ) {
        if (uiState == null || uiStrings == null) {
            OwnIdSpinner(modifier = Modifier.align(Alignment.Center))
        } else {
            val colors = LocalOwnIdColors.current
            content(uiState, uiStrings, modifier, colors, errorTextProvider, isReadyForInitialFocus)
        }
    }
}
