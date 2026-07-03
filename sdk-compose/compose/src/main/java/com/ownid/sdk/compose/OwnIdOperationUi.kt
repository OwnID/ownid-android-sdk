package com.ownid.sdk.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ownid.sdk.OwnId
import com.ownid.sdk.compose.capability.OperationUiLifecycle
import com.ownid.sdk.compose.ops.EmailVerificationUiContent
import com.ownid.sdk.compose.ops.EmailVerificationUiProvider
import com.ownid.sdk.compose.ops.LoginIdCollectUiContent
import com.ownid.sdk.compose.ops.LoginIdCollectUiProvider
import com.ownid.sdk.compose.ops.PhoneVerificationUiContent
import com.ownid.sdk.compose.ops.PhoneVerificationUiProvider
import com.ownid.sdk.compose.ops.impl.EmailVerificationHostUi
import com.ownid.sdk.compose.ops.impl.LoginIdCollectHostUi
import com.ownid.sdk.compose.ops.impl.PhoneVerificationHostUi
import com.ownid.sdk.di.DIContainerResolver
import com.ownid.sdk.di.getOrNull
import com.ownid.sdk.logger.OwnIdLogRouter
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.Reason
import com.ownid.sdk.ops.EmailVerificationOperationController
import com.ownid.sdk.ops.LoginIdCollectOperationController
import com.ownid.sdk.ops.OperationResult
import com.ownid.sdk.ops.PhoneVerificationOperationController
import com.ownid.sdk.ui.capability.EmailVerificationStringsProvider
import com.ownid.sdk.ui.capability.LoginIdCollectStringsProvider
import com.ownid.sdk.ui.capability.PhoneVerificationStringsProvider

/**
 * Renders one app-hosted OwnID operation in your Compose hierarchy.
 *
 * Supported operations are login ID collection, email verification, and phone verification.
 *
 * Apply `appHostedComponent` to a supported operation, call `start`, keep the returned
 * [OwnIdOperationUiController] in UI state, and pass it to this composable. The composable uses matching content
 * overrides when provided; otherwise it uses content registered for the OwnID instance that created the controller.
 * The app observes the terminal [OperationResult] through
 * [OwnIdOperationUiController.whenSettled]; this composable does not invoke app success, failure, or cancellation
 * callbacks directly.
 *
 * Lifecycle is controlled in one of two modes:
 *
 * - Embedded mode: omit [uiContainerController] when this composable is the operation's visible container. Removing it
 *   from composition aborts an unsettled operation with [Reason.UserClose].
 * - App-container mode: pass [uiContainerController] when the operation is inside an app-owned sheet, dialog, or
 *   overlay. Use the same controller for the whole presentation cycle. The app starts and reports container
 *   dismissal, while this composable keeps the operation and container lifecycle aligned. Removing this composable
 *   before [OwnIdUiContainerController.markClosed] does not cancel the operation; the operation waits for the container
 *   close signal or for operation settlement.
 *
 * After the operation settles, this composable renders no operation content. If the SDK instance is no longer
 * available, required strings or content are missing, or the operation type is unsupported, the operation is aborted
 * with [Reason.SystemError].
 *
 * Custom content overrides own rendering, focus behavior, visible busy indicators, error display, and wiring user
 * actions to the supplied operation UI state callbacks. Operation validation, resend/cancel/"not you" handling, abort,
 * timeout, and final settlement remain owned by the SDK operation controller.
 *
 * @param operationUiController Controller returned after starting an app-hosted operation with `appHostedComponent`.
 * @param modifier Modifier passed to the built-in operation content or to the matching custom content override. It is
 *   not applied to this composable's loading wrapper.
 * @param uiContainerController Optional bridge for an app-owned sheet, dialog, or overlay. Create a new controller for
 *   each presentation cycle, provide a close action that starts dismissal, pass that same controller to the presented
 *   container, then call [OwnIdUiContainerController.markOpened] and [OwnIdUiContainerController.markClosed] from the
 *   container. Do not replace the controller while the current container is still open, and do not use composition
 *   removal as a substitute for [OwnIdUiContainerController.markClosed].
 * @param theme OwnID theme used for this host. Defaults to the theme captured at the call site.
 * @param errorTextProvider Optional provider from SDK-reported [ErrorCode] values to text shown by built-in operation
 *   content and custom content that chooses to use the provider. When omitted, built-in content shows the operation
 *   `UiError.localizedMessage`.
 * @param loginIdCollectContent Optional content override for login ID collection. When `null`, the registered provider
 *   for the controller's OwnID instance is used.
 * @param emailVerificationContent Optional content override for email verification. When `null`, the registered
 *   provider for the controller's OwnID instance is used.
 * @param phoneVerificationContent Optional content override for phone verification. When `null`, the registered
 *   provider for the controller's OwnID instance is used.
 */
@Composable
@OptIn(InternalOwnIdAPI::class)
public fun OwnIdOperationUi(
    operationUiController: OwnIdOperationUiController<*, *>,
    modifier: Modifier = Modifier,
    uiContainerController: OwnIdUiContainerController? = null,
    theme: OwnIdTheme = OwnIdTheme.capture(),
    errorTextProvider: ((ErrorCode) -> String)? = null,
    loginIdCollectContent: LoginIdCollectUiContent? = null,
    emailVerificationContent: EmailVerificationUiContent? = null,
    phoneVerificationContent: PhoneVerificationUiContent? = null
) {
    val instanceContainer by OwnId.getInstanceContainerStateFlow(operationUiController.instanceName).collectAsStateWithLifecycle()
    val operationController = operationUiController.operationControllerBase
    val operationLifecycle = remember(operationController, uiContainerController) {
        OperationUiLifecycle(operationController, uiContainerController)
    }

    DisposableEffect(operationLifecycle) {
        operationLifecycle.start()
        onDispose {
            operationLifecycle.onHostDisposed()
        }
    }

    LaunchedEffect(operationLifecycle, instanceContainer != null) {
        if (instanceContainer == null) {
            operationLifecycle.cancelMissingInstance()
        }
    }

    if (!operationLifecycle.shouldRender) return
    val instanceResolver = instanceContainer ?: return

    ProvideOwnIdTheme(theme) {
        when (operationController) {
            is LoginIdCollectOperationController ->
                LoginIdCollectOperationUi(
                    instanceResolver = instanceResolver,
                    controller = operationController,
                    modifier = modifier,
                    errorTextProvider = errorTextProvider,
                    uiContainerController = uiContainerController,
                    loginIdCollectContent = loginIdCollectContent,
                    abortOperation = operationLifecycle::cancel
                )

            is EmailVerificationOperationController ->
                EmailVerificationOperationUi(
                    instanceResolver = instanceResolver,
                    controller = operationController,
                    modifier = modifier,
                    errorTextProvider = errorTextProvider,
                    uiContainerController = uiContainerController,
                    verificationContent = emailVerificationContent,
                    abortOperation = operationLifecycle::cancel
                )

            is PhoneVerificationOperationController ->
                PhoneVerificationOperationUi(
                    instanceResolver = instanceResolver,
                    controller = operationController,
                    modifier = modifier,
                    errorTextProvider = errorTextProvider,
                    uiContainerController = uiContainerController,
                    verificationContent = phoneVerificationContent,
                    abortOperation = operationLifecycle::cancel
                )

            else -> {
                LaunchedEffect(operationController, uiContainerController) {
                    val ownIdLogRouter = instanceResolver.getOrNull<OwnIdLogRouter>()
                    val message = "Unsupported operation type for app-hosted UI in OwnIdOperationUi: ${operationController.operationId.type}"
                    ownIdLogRouter?.logW(instanceResolver, "OwnIdOperationUi", message)
                    operationLifecycle.cancel(Reason.SystemError(message))
                    uiContainerController?.requestDismissWithoutAbort()
                }
            }
        }
    }
}

@Composable
@InternalOwnIdAPI
private fun LoginIdCollectOperationUi(
    instanceResolver: DIContainerResolver,
    controller: LoginIdCollectOperationController,
    modifier: Modifier = Modifier,
    errorTextProvider: ((ErrorCode) -> String)? = null,
    uiContainerController: OwnIdUiContainerController? = null,
    loginIdCollectContent: LoginIdCollectUiContent? = null,
    abortOperation: (Reason) -> Unit
) {
    val uiProvider = remember(instanceResolver) { instanceResolver.getOrNull<LoginIdCollectUiProvider>() }
    val stringsProvider = remember(instanceResolver) { instanceResolver.getOrNull<LoginIdCollectStringsProvider>() }
    val content = remember(loginIdCollectContent, uiProvider) { loginIdCollectContent ?: uiProvider?.getContent() }
    val missingDetails = when {
        content == null -> "Login ID collection UI is unavailable because no UI content provider is registered"
        stringsProvider == null -> "Login ID collection UI is unavailable because required UI strings are missing"
        else -> null
    }

    if (missingDetails != null) {
        LaunchedEffect(controller, missingDetails) {
            val ownIdLogRouter = instanceResolver.getOrNull<OwnIdLogRouter>()
            ownIdLogRouter?.logW(instanceResolver, "LoginIdCollectOperationUi", missingDetails)
            abortOperation(Reason.SystemError(missingDetails))
            uiContainerController?.requestDismissWithoutAbort()
        }
        return
    }

    LoginIdCollectHostUi(
        controller = controller,
        content = content!!,
        stringsProvider = stringsProvider!!,
        modifier = modifier,
        containerController = uiContainerController,
        errorTextProvider = errorTextProvider
    )
}

@Composable
@InternalOwnIdAPI
private fun EmailVerificationOperationUi(
    instanceResolver: DIContainerResolver,
    controller: EmailVerificationOperationController,
    modifier: Modifier = Modifier,
    errorTextProvider: ((ErrorCode) -> String)? = null,
    uiContainerController: OwnIdUiContainerController? = null,
    verificationContent: EmailVerificationUiContent? = null,
    abortOperation: (Reason) -> Unit
) {
    val uiProvider = remember(instanceResolver) { instanceResolver.getOrNull<EmailVerificationUiProvider>() }
    val stringsProvider = remember(instanceResolver) { instanceResolver.getOrNull<EmailVerificationStringsProvider>() }
    val content = remember(verificationContent, uiProvider) { verificationContent ?: uiProvider?.getContent() }
    val missingDetails = when {
        content == null -> "Email verification UI is unavailable because no UI content provider is registered"
        stringsProvider == null -> "Email verification UI is unavailable because required UI strings are missing"
        else -> null
    }

    if (missingDetails != null) {
        LaunchedEffect(controller, missingDetails) {
            val ownIdLogRouter = instanceResolver.getOrNull<OwnIdLogRouter>()
            ownIdLogRouter?.logW(instanceResolver, "EmailVerificationOperationUi", missingDetails)
            abortOperation(Reason.SystemError(missingDetails))
            uiContainerController?.requestDismissWithoutAbort()
        }
        return
    }

    EmailVerificationHostUi(
        controller = controller,
        content = content!!,
        stringsProvider = stringsProvider!!,
        modifier = modifier,
        containerController = uiContainerController,
        errorTextProvider = errorTextProvider
    )
}

@Composable
@InternalOwnIdAPI
private fun PhoneVerificationOperationUi(
    instanceResolver: DIContainerResolver,
    controller: PhoneVerificationOperationController,
    modifier: Modifier = Modifier,
    errorTextProvider: ((ErrorCode) -> String)? = null,
    uiContainerController: OwnIdUiContainerController? = null,
    verificationContent: PhoneVerificationUiContent? = null,
    abortOperation: (Reason) -> Unit
) {
    val uiProvider = remember(instanceResolver) { instanceResolver.getOrNull<PhoneVerificationUiProvider>() }
    val stringsProvider = remember(instanceResolver) { instanceResolver.getOrNull<PhoneVerificationStringsProvider>() }
    val content = remember(verificationContent, uiProvider) { verificationContent ?: uiProvider?.getContent() }
    val missingDetails = when {
        content == null -> "Phone verification UI is unavailable because no UI content provider is registered"
        stringsProvider == null -> "Phone verification UI is unavailable because required UI strings are missing"
        else -> null
    }

    if (missingDetails != null) {
        LaunchedEffect(controller, missingDetails) {
            val ownIdLogRouter = instanceResolver.getOrNull<OwnIdLogRouter>()
            ownIdLogRouter?.logW(instanceResolver, "PhoneVerificationOperationUi", missingDetails)
            abortOperation(Reason.SystemError(missingDetails))
            uiContainerController?.requestDismissWithoutAbort()
        }
        return
    }

    PhoneVerificationHostUi(
        controller = controller,
        content = content!!,
        stringsProvider = stringsProvider!!,
        modifier = modifier,
        containerController = uiContainerController,
        errorTextProvider = errorTextProvider
    )
}
