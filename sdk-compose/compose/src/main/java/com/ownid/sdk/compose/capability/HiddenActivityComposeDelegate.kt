package com.ownid.sdk.compose.capability

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.text.layoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ownid.sdk.capability.LanguageTagsProvider
import com.ownid.sdk.compose.LocalOwnIdColors
import com.ownid.sdk.compose.OwnIdColors
import com.ownid.sdk.compose.OwnIdOperationUi
import com.ownid.sdk.compose.OwnIdThemeStore
import com.ownid.sdk.compose.OwnIdUiContainerController
import com.ownid.sdk.compose.ProvideOwnIdTheme
import com.ownid.sdk.compose.defaultOwnIdTheme
import com.ownid.sdk.compose.ops.EmailVerificationUiProvider
import com.ownid.sdk.compose.ops.LoginIdCollectUiProvider
import com.ownid.sdk.compose.ops.PhoneVerificationUiProvider
import com.ownid.sdk.compose.ops.impl.EmailVerificationHostUi
import com.ownid.sdk.compose.ops.impl.LoginIdCollectHostUi
import com.ownid.sdk.compose.ops.impl.PhoneVerificationHostUi
import com.ownid.sdk.di.DIContainerResolver
import com.ownid.sdk.di.getOrNull
import com.ownid.sdk.logger.OwnIdLogRouter
import com.ownid.sdk.model.InstanceName
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LanguageTag
import com.ownid.sdk.model.OperationID
import com.ownid.sdk.model.OperationType
import com.ownid.sdk.model.Reason
import com.ownid.sdk.ops.EmailVerificationOperationController
import com.ownid.sdk.ops.LoginIdCollectOperationController
import com.ownid.sdk.ops.OperationRegistry
import com.ownid.sdk.ops.PhoneVerificationOperationController
import com.ownid.sdk.ui.OwnIdHiddenActivity
import com.ownid.sdk.ui.OwnIdHiddenActivity.Companion.closeActivity
import com.ownid.sdk.ui.OwnIdHiddenActivity.Companion.closeActivityWithError
import com.ownid.sdk.ui.capability.EmailVerificationStringsProvider
import com.ownid.sdk.ui.capability.HiddenActivityLauncher
import com.ownid.sdk.ui.capability.LoginIdCollectStringsProvider
import com.ownid.sdk.ui.capability.PhoneVerificationStringsProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Presents SDK-owned OwnID operation UI in a Compose modal bottom sheet inside [OwnIdHiddenActivity].
 *
 * This delegate is used only for SDK-owned presentation. App-hosted presentation goes through
 * [OwnIdOperationUi] and [OwnIdUiContainerController]. The delegate resolves the active operation from
 * [OperationRegistry], renders the matching Compose host, and uses the same operation lifecycle rules as app-hosted UI:
 * operation settlement closes the sheet, while sheet dismissal cancels an unsettled operation. Presentation failures
 * abort the operation with a system error instead of exposing the activity or container as an app contract.
 */
@InternalOwnIdAPI
internal class HiddenActivityComposeDelegate : HiddenActivityLauncher.Delegate {

    internal companion object {
        internal fun createIntent(context: Context, instanceName: InstanceName, operationID: OperationID): Intent =
            OwnIdHiddenActivity.createIntent(context, instanceName)
                .putExtra(KEY_COMPOSE_DELEGATE, true)
                .putExtra(OwnIdHiddenActivity.PREPARE_IME_HANDOFF_BEFORE_LAUNCH_KEY, true)
                .putExtra(OPERATION_TYPE_KEY, operationID.type.name)
                .putExtra(OPERATION_ID_KEY, operationID.id)

        private const val KEY_COMPOSE_DELEGATE = "com.ownid.sdk.key.ui.KEY_COMPOSE_DELEGATE"
        private const val OPERATION_TYPE_KEY = "com.ownid.sdk.key.OPERATION_TYPE_KEY"
        private const val OPERATION_ID_KEY = "com.ownid.sdk.key.OPERATION_ID_KEY"
    }

    override fun isThis(intent: Intent): Boolean = intent.getBooleanExtra(KEY_COMPOSE_DELEGATE, false)

    private var ownIdLogRouter: OwnIdLogRouter? = null

    override fun onCreate(activity: ComponentActivity, instanceResolver: DIContainerResolver) {
        val ownIdLogRouter = instanceResolver.getOrNull<OwnIdLogRouter>()
        this.ownIdLogRouter = ownIdLogRouter
        ownIdLogRouter?.logD(this, "onCreate", "Invoked: ${this.hashCode()}")

        val typeString = activity.intent.getStringExtra(OPERATION_TYPE_KEY) ?: "null"
        val type = runCatching { OperationType.valueOf(typeString) }.getOrNull()
        if (type == null) {
            activity.closeActivityWithError("${this.javaClass.simpleName} onCreate: No operation type found for [$typeString]")
            return
        }

        val idString = activity.intent.getStringExtra(OPERATION_ID_KEY)
        if (idString == null) {
            activity.closeActivityWithError("${this.javaClass.simpleName} onCreate: No operation ID found [$idString]")
            return
        }
        val operationID = OperationID(type, idString)
        val instanceName = instanceResolver.getOrNull<InstanceName>()

        val operationRegistry = instanceResolver.getOrNull<OperationRegistry>()
        if (operationRegistry == null) {
            activity.closeActivityWithError("${this.javaClass.simpleName} onCreate: No operation registry found for $instanceName")
            return
        }

        val themeStore = instanceResolver.getOrNull<OwnIdThemeStore>()
        if (themeStore == null) {
            ownIdLogRouter?.logI(this, "onCreate", "No OwnID theme store found for $instanceName")
        }

        val languageTagsProvider = instanceResolver.getOrNull<LanguageTagsProvider>()

        activity.setContent {
            OperationContent(
                operationID = operationID,
                operationRegistry = operationRegistry,
                instanceResolver = instanceResolver,
                ownIdLogRouter = ownIdLogRouter,
                themeStore = themeStore,
                languageTagsProvider = languageTagsProvider
            )
        }
    }

    override fun onDestroy(activity: ComponentActivity) {
        ownIdLogRouter?.logD(this, "onDestroy", "Invoked")
    }
}

@Composable
@InternalOwnIdAPI
@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
internal fun OperationContent(
    operationID: OperationID,
    operationRegistry: OperationRegistry,
    instanceResolver: DIContainerResolver,
    ownIdLogRouter: OwnIdLogRouter?,
    themeStore: OwnIdThemeStore?,
    languageTagsProvider: LanguageTagsProvider?,
    onCloseActivityForTesting: (() -> Unit)? = null,
    onControllerCreatedForTesting: (OwnIdUiContainerController) -> Unit = {}
) {
    val activity = LocalActivity.current as ComponentActivity
    val closeActivity = remember(activity, onCloseActivityForTesting) {
        onCloseActivityForTesting ?: { activity.closeActivity() }
    }
    val logWarning: (String, String) -> Unit = remember(activity, ownIdLogRouter) {
        { prefix, message -> ownIdLogRouter?.logW(activity, prefix, message) }
    }
    val theme = themeStore?.theme?.collectAsStateWithLifecycle()?.value
    ProvideOwnIdTheme(theme ?: defaultOwnIdTheme()) {
        ProvideSdkLayoutDirection(languageTagsProvider) {
            @OptIn(ExperimentalMaterial3Api::class)
            ModalBottomSheetContainer(
                onControllerCreatedForTesting = onControllerCreatedForTesting
            ) { uiContainerController ->
                LaunchedEffect(uiContainerController, closeActivity) {
                    uiContainerController.awaitClosed()
                    closeActivity() // Rely on DisposableEffect in ModalBottomSheetContainer
                }

                OperationBody(
                    operationID = operationID,
                    operationRegistry = operationRegistry,
                    instanceResolver = instanceResolver,
                    logWarning = logWarning,
                    uiContainerController = uiContainerController
                )
            }
        }
    }
}

@Composable
@InternalOwnIdAPI
@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
internal fun OperationBody(
    operationID: OperationID,
    operationRegistry: OperationRegistry,
    instanceResolver: DIContainerResolver,
    logWarning: (prefix: String, message: String) -> Unit,
    uiContainerController: OwnIdUiContainerController
) {
    val currentLogWarning by rememberUpdatedState(logWarning)
    val operationController = operationRegistry.current.collectAsStateWithLifecycle().value[operationID]
    if (operationController == null) {
        LaunchedEffect(operationID) { uiContainerController.requestDismissWithoutAbort() }
        return
    }

    val operationLifecycle = remember(operationController, uiContainerController) {
        OperationUiLifecycle(operationController, uiContainerController)
    }

    DisposableEffect(operationLifecycle) {
        operationLifecycle.start()
        onDispose {
            operationLifecycle.onHostDisposed()
        }
    }

    if (!operationLifecycle.shouldRender) return

    when (operationController) {
        is LoginIdCollectOperationController -> {
            val uiProvider = remember(instanceResolver) { instanceResolver.getOrNull<LoginIdCollectUiProvider>() }
            val stringsProvider = remember(instanceResolver) { instanceResolver.getOrNull<LoginIdCollectStringsProvider>() }
            when {
                uiProvider == null -> LaunchedEffect(operationController, uiContainerController) {
                    val message = "Login ID collection UI is unavailable because no UI content provider is registered"
                    currentLogWarning("LoginIdCollectUi", message)
                    operationLifecycle.cancel(Reason.SystemError(message))
                    uiContainerController.requestDismissWithoutAbort()
                }

                stringsProvider == null -> LaunchedEffect(operationController, uiContainerController) {
                    val message = "Login ID collection UI is unavailable because required UI strings are missing"
                    currentLogWarning("LoginIdCollectUi", message)
                    operationLifecycle.cancel(Reason.SystemError(message))
                    uiContainerController.requestDismissWithoutAbort()
                }

                else -> LoginIdCollectHostUi(
                    controller = operationController,
                    content = uiProvider.getContent(),
                    stringsProvider = stringsProvider,
                    containerController = uiContainerController
                )
            }
        }

        is EmailVerificationOperationController -> {
            val uiProvider = remember(instanceResolver) { instanceResolver.getOrNull<EmailVerificationUiProvider>() }
            val stringsProvider = remember(instanceResolver) { instanceResolver.getOrNull<EmailVerificationStringsProvider>() }
            when {
                uiProvider == null -> LaunchedEffect(operationController, uiContainerController) {
                    val message = "Email verification UI is unavailable because no UI content provider is registered"
                    currentLogWarning("EmailVerificationUi", message)
                    operationLifecycle.cancel(Reason.SystemError(message))
                    uiContainerController.requestDismissWithoutAbort()
                }

                stringsProvider == null -> LaunchedEffect(operationController, uiContainerController) {
                    val message = "Email verification UI is unavailable because required UI strings are missing"
                    currentLogWarning("EmailVerificationUi", message)
                    operationLifecycle.cancel(Reason.SystemError(message))
                    uiContainerController.requestDismissWithoutAbort()
                }

                else -> EmailVerificationHostUi(
                    controller = operationController,
                    content = uiProvider.getContent(),
                    stringsProvider = stringsProvider,
                    containerController = uiContainerController
                )
            }
        }

        is PhoneVerificationOperationController -> {
            val uiProvider = remember(instanceResolver) { instanceResolver.getOrNull<PhoneVerificationUiProvider>() }
            val stringsProvider = remember(instanceResolver) { instanceResolver.getOrNull<PhoneVerificationStringsProvider>() }
            when {
                uiProvider == null -> LaunchedEffect(operationController, uiContainerController) {
                    val message = "Phone verification UI is unavailable because no UI content provider is registered"
                    currentLogWarning("PhoneVerificationUi", message)
                    operationLifecycle.cancel(Reason.SystemError(message))
                    uiContainerController.requestDismissWithoutAbort()
                }

                stringsProvider == null -> LaunchedEffect(operationController, uiContainerController) {
                    val message = "Phone verification UI is unavailable because required UI strings are missing"
                    currentLogWarning("PhoneVerificationUi", message)
                    operationLifecycle.cancel(Reason.SystemError(message))
                    uiContainerController.requestDismissWithoutAbort()
                }

                else -> PhoneVerificationHostUi(
                    controller = operationController,
                    content = uiProvider.getContent(),
                    stringsProvider = stringsProvider,
                    containerController = uiContainerController
                )
            }
        }

        else -> LaunchedEffect(operationController, uiContainerController) {
            val message = "UI for ${operationController.operationId.type} is not implemented"
            currentLogWarning("HiddenActivityComposeDelegate", message)
            operationLifecycle.cancel(Reason.SystemError(message))
            uiContainerController.requestDismissWithoutAbort()
        }
    }
}

@Composable
@ExperimentalMaterial3Api
@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
internal fun ModalBottomSheetContainer(
    activity: Activity? = LocalActivity.current,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    scope: CoroutineScope = rememberCoroutineScope(),
    colors: OwnIdColors = LocalOwnIdColors.current,
    openedFallbackTimeoutForTesting: Duration = 1_200.milliseconds,
    onControllerCreatedForTesting: (OwnIdUiContainerController) -> Unit = {},
    content: @Composable ColumnScope.(OwnIdUiContainerController) -> Unit
) {
    val currentOnControllerCreated by rememberUpdatedState(onControllerCreatedForTesting)
    var showBottomSheet by remember { mutableStateOf(true) }
    var shouldKeepSheetVisible by remember { mutableStateOf(true) }
    var openedEmitted by remember { mutableStateOf(false) }
    val contentLayoutDirection = LocalLayoutDirection.current

    val uiContainerController: OwnIdUiContainerController = remember {
        lateinit var controller: OwnIdUiContainerController
        controller = OwnIdUiContainerController {
            shouldKeepSheetVisible = false
            scope.launch {
                try {
                    sheetState.hide()
                } finally {
                    showBottomSheet = false
                    controller.markClosed()
                }
            }
        }
        controller
    }

    LaunchedEffect(uiContainerController) {
        currentOnControllerCreated(uiContainerController)
    }

    DisposableEffect(activity, uiContainerController) {
        onDispose {
            if (activity == null || !activity.isChangingConfigurations || activity.isFinishing) {
                shouldKeepSheetVisible = false
                uiContainerController.markClosed()
            }
        }
    }

    LaunchedEffect(sheetState) {
        snapshotFlow {
            Triple(sheetState.currentValue, sheetState.targetValue, sheetState.hasExpandedState)
        }.collect { (current, target, hasExpandedState) ->
            val expandedNow = (target == SheetValue.Expanded && current == SheetValue.Expanded)
            if (expandedNow && !openedEmitted) {
                openedEmitted = true
                // Give the sheet time to finish drawing before focusing inputs
                awaitFrame()
                awaitFrame()
                uiContainerController.markOpened()
            }

            val shouldReconcileVisibleSheet =
                shouldKeepSheetVisible &&
                        showBottomSheet &&
                        !uiContainerController.isClosed &&
                        hasExpandedState &&
                        current == SheetValue.Hidden &&
                        target == SheetValue.Hidden

            if (shouldReconcileVisibleSheet) {
                scope.launch { sheetState.show() }
            }
        }
    }

    // Grace fallback: if for any reason we did not get an Expanded signal, unlock focus after ~1.2s
    LaunchedEffect(uiContainerController, openedFallbackTimeoutForTesting) {
        withTimeoutOrNull(openedFallbackTimeoutForTesting) { uiContainerController.awaitOpened() } ?: run {
            if (!uiContainerController.isClosed) {
                uiContainerController.markOpened()
            }
        }
    }

    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                shouldKeepSheetVisible = false
                showBottomSheet = false
                uiContainerController.markClosed()
            },
            shape = MaterialTheme.shapes.medium.copy(bottomStart = CornerSize(0.0.dp), bottomEnd = CornerSize(0.0.dp)),
            containerColor = colors.surface,
            contentColor = colors.onSurface,
            sheetState = sheetState,
            sheetMaxWidth = 480.dp,
            dragHandle = null
        ) {
            // ModalBottomSheet is dialog-backed; re-provide the caller's layout direction for sheet content.
            CompositionLocalProvider(LocalLayoutDirection provides contentLayoutDirection) {
                content(uiContainerController)
            }
        }
    }
}

@Composable
private fun ProvideSdkLayoutDirection(
    languageTagsProvider: LanguageTagsProvider?,
    content: @Composable () -> Unit
) {
    val languageTags = languageTagsProvider?.languageTagsFlow?.collectAsStateWithLifecycle()?.value
    val layoutDirection = remember(languageTags) {
        val tag = languageTags?.firstOrNull() ?: LanguageTag.DEFAULT
        val locale = Locale.forLanguageTag(tag.tagString)
        if (locale.layoutDirection == View.LAYOUT_DIRECTION_RTL) LayoutDirection.Rtl else LayoutDirection.Ltr
    }

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        content()
    }
}
