@file:JvmName("OwnIdFactory")

package com.ownid.sdk

import com.ownid.sdk.internal.feature.webbridge.OwnIdWebViewBridgeImpl

/**
 * Creates a new instance of [OwnIdWebViewBridge] associated with this OwnID instance.
 *
 * The bridge enables communication between the OwnID Web SDK running in a WebView and the OwnID Android SDK.
 *
 * You can customize the functionalities exposed to the Web SDK by specifying the namespaces to include or exclude.
 *
 * **Namespaces:**
 * * Namespaces are functional plugins that encapsulate sets of related features accessible by the Web SDK.
 * * Available namespaces are defined in [OwnIdWebViewBridge.Namespace].
 * * By default, all namespaces are included.
 *
 * @param includeNamespaces An optional list of namespaces to include in the bridge. If `null`, all namespaces are included.
 * @param excludeNamespaces An optional list of namespaces to exclude from the bridge. Excluded namespaces take precedence over included ones.
 *
 * @return A new instance of [OwnIdWebViewBridge] configured with the specified namespaces.
 */
@OptIn(InternalOwnIdAPI::class)
public fun OwnIdInstance.createWebViewBridge(
    includeNamespaces: List<OwnIdWebViewBridge.Namespace>? = null,
    excludeNamespaces: List<OwnIdWebViewBridge.Namespace>? = null
): OwnIdWebViewBridge =
    OwnIdWebViewBridgeImpl(ownIdCore.instanceName, includeNamespaces, excludeNamespaces)