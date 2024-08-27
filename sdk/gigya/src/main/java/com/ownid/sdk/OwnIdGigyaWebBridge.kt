package com.ownid.sdk

import android.content.Context
import android.view.View
import android.webkit.WebView
import androidx.annotation.Keep
import com.gigya.android.sdk.Config
import com.gigya.android.sdk.GigyaPluginCallback
import com.gigya.android.sdk.account.IAccountService
import com.gigya.android.sdk.account.models.GigyaAccount
import com.gigya.android.sdk.api.IBusinessApiService
import com.gigya.android.sdk.providers.IProviderFactory
import com.gigya.android.sdk.session.ISessionService
import com.gigya.android.sdk.session.ISessionVerificationService
import com.gigya.android.sdk.ui.plugin.GigyaWebBridge
import com.gigya.android.sdk.ui.plugin.webbridgetmanager.IWebBridgeInterruptionManager

@Keep
public class OwnIdGigyaWebBridge<A : GigyaAccount?>(
    context: Context,
    config: Config,
    sessionService: ISessionService,
    businessApiService: IBusinessApiService<A>,
    accountService: IAccountService<A>,
    sessionVerificationService: ISessionVerificationService,
    providerFactory: IProviderFactory,
    webBridgeInterruptionManager: IWebBridgeInterruptionManager<*>
) : GigyaWebBridge<A>(
    context,
    config,
    sessionService,
    businessApiService,
    accountService,
    sessionVerificationService,
    providerFactory,
    webBridgeInterruptionManager
) {

    override fun attachTo(webView: WebView, pluginCallback: GigyaPluginCallback<A>, progressView: View?) {
        super.attachTo(webView, pluginCallback, progressView)
        OwnId.createWebViewBridge(
            includeNamespaces = listOf(
                OwnIdWebViewBridge.Namespace.FIDO,
                OwnIdWebViewBridge.Namespace.STORAGE,
                OwnIdWebViewBridge.Namespace.METADATA
            )
        )
            .injectInto(webView, setOf("https://www.gigya.com"))
    }
}