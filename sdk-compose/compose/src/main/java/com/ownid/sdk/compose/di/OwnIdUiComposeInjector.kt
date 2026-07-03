package com.ownid.sdk.compose.di

import androidx.annotation.RestrictTo
import com.ownid.sdk.compose.OwnIdThemeStore
import com.ownid.sdk.compose.capability.HiddenActivityComposeDelegate
import com.ownid.sdk.compose.ops.EmailVerificationUiProvider
import com.ownid.sdk.compose.ops.LoginIdCollectUiProvider
import com.ownid.sdk.compose.ops.PhoneVerificationUiProvider
import com.ownid.sdk.compose.ops.impl.EmailVerificationUiImpl
import com.ownid.sdk.compose.ops.impl.LoginIdCollectUiImpl
import com.ownid.sdk.compose.ops.impl.PhoneVerificationUiImpl
import com.ownid.sdk.di.DIContainer
import com.ownid.sdk.di.OwnIdModuleInjector
import com.ownid.sdk.di.getOrNull
import com.ownid.sdk.di.getOrThrow
import com.ownid.sdk.di.register
import com.ownid.sdk.di.registerFactory
import com.ownid.sdk.logger.OwnIdLogRouter
import com.ownid.sdk.model.InstanceName
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.OperationID
import com.ownid.sdk.ops.ui.EmailVerificationUi
import com.ownid.sdk.ops.ui.LoginIdCollectUi
import com.ownid.sdk.ops.ui.PhoneVerificationUi
import com.ownid.sdk.ui.capability.HiddenActivityLauncher
import com.ownid.sdk.ui.capability.OperationUiContainer

/**
 * Registers optional UI bindings in an OwnID instance container.
 *
 * Core discovers this module when the optional UI artifact is linked into the app. The module is not an app-facing
 * extension point; it supplies OwnID theme, operation UI content, and SDK-owned operation UI presentation. Apps
 * customize operation content through the public UI APIs rather than by calling this injector.
 */
@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class OwnIdUiComposeInjector : OwnIdModuleInjector.Module {

    /**
     * Injects default UI factories into [container].
     *
     * Existing container contents are preserved except for UI capability keys intentionally owned by the optional
     * UI module. Core SDK runtime contracts stay owned by the Core artifact.
     */
    override fun injectIntoInstanceContainer(container: DIContainer) {
        val logger = container.getOrNull<OwnIdLogRouter>()
        logger?.logV(this, "injectIntoInstanceScope", "Start")

        container.apply {
            register(OwnIdThemeStore())

            registerFactory<HiddenActivityComposeDelegate> { HiddenActivityComposeDelegate() }

            registerFactory<OperationUiContainer>(HiddenActivityLauncher::class.java, HiddenActivityComposeDelegate::class.java) {
                object : OperationUiContainer {
                    override fun show(instanceName: InstanceName, operationID: OperationID, onShowError: (error: Throwable?, details: String?) -> Unit) {
                        try {
                            val hiddenActivityLauncher = getOrThrow<HiddenActivityLauncher>()
                            hiddenActivityLauncher.launchActivityForResult(
                                intentProvider = { context -> HiddenActivityComposeDelegate.createIntent(context, instanceName, operationID) },
                                onShowError = onShowError
                            )
                        } catch (error: Exception) {
                            onShowError(error, "Failed to get HiddenActivityLauncher: ${error.message}")
                        }
                    }
                }
            }

            registerFactory<LoginIdCollectUiProvider> { LoginIdCollectUiProvider.Default }

            registerFactory<LoginIdCollectUi>(LoginIdCollectUiProvider::class.java, OperationUiContainer::class.java, InstanceName::class.java) {
                LoginIdCollectUiImpl { operationID, onShowError ->
                    getOrThrow<OperationUiContainer>().show(getOrThrow<InstanceName>(), operationID, onShowError)
                }
            }

            registerFactory<EmailVerificationUiProvider> { EmailVerificationUiProvider.Default }

            registerFactory<EmailVerificationUi>(EmailVerificationUiProvider::class.java, OperationUiContainer::class.java, InstanceName::class.java) {
                EmailVerificationUiImpl { operationID, onShowError ->
                    getOrThrow<OperationUiContainer>().show(getOrThrow<InstanceName>(), operationID, onShowError)
                }
            }

            registerFactory<PhoneVerificationUiProvider> { PhoneVerificationUiProvider.Default }

            registerFactory<PhoneVerificationUi>(PhoneVerificationUiProvider::class.java, OperationUiContainer::class.java, InstanceName::class.java) {
                PhoneVerificationUiImpl { operationID, onShowError ->
                    getOrThrow<OperationUiContainer>().show(getOrThrow<InstanceName>(), operationID, onShowError)
                }
            }
        }

        logger?.logV(this, "injectIntoInstanceScope", "Finish")
    }
}
