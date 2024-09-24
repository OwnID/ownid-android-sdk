package com.ownid.sdk

import android.webkit.WebView
import androidx.annotation.MainThread
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.ownid.sdk.OwnIdWebViewBridge.Namespace
import com.ownid.sdk.exception.OwnIdException

/**
 * The OwnID WebView Bridge enables bidirectional communication between the OwnID Web SDK running in a WebView and OwnID Android SDK.
 * This bridge injects JavaScript code into the [WebView], allowing the Web SDK to call OwnID Android SDK functions.
 *
 *  **Plugin-based Architecture:**
 *  Organizes functionalities into distinct plugins called [Namespace]s (e.g., FIDO, STORAGE, METADATA), which are then exposed to the OwnID Web SDK.
 *
 */
public interface OwnIdWebViewBridge {

    /**
     * Represents functional plugins for OwnID WebView Bridge.
     */
    public enum class Namespace {
        /**
         * Handles operations related to FIDO authentication.
         */
        FIDO,

        /**
         *  Manages data storage interactions.
         */
        STORAGE,

        /**
         *  Provides access to native SDK metadata.
         */
        METADATA,

        /**
         *  Provides access to OwnID flow.
         */
        FLOW,
    }

    /**
     * Injects the OwnID WebView Bridge into the specified [WebView], enabling communication between the OwnID Web SDK and OwnID Android SDK.
     * This function establishes the communication bridge by injecting a JavaScript object into the [WebView].
     *
     * **Important:** Call this method **before** loading any content into the WebView or reload [WebView] content after successful injection.
     *
     * **Injection Allowed Origin Rules:**
     * These rules determine which origins are allowed to communicate with the injected JavaScript code during the injection process itself.
     * * **Synchronous Injection (`synchronous = true`):**
     *     * If OwnID application configuration is available, allowed origins are derived from the server configuration plus any values provided in the `allowedOriginRules` parameter.
     *     * If OwnID application configuration is unavailable and `allowedOriginRules` is not empty, only values from `allowedOriginRules` are used.
     *     * If both OwnID application configuration and `allowedOriginRules` are unavailable or empty, a wildcard rule (`*`) is used, allowing communication from any origin. **This is less secure and should be avoided if possible.**
     * * **Asynchronous Injection(`synchronous = false` - default):**
     *     * **Always waits** for the OwnID application configuration to become available before proceeding with injection.
     *     * Allowed origins are derived from the server configuration plus any values provided in the `allowedOriginRules` parameter.
     *     * Injection will **fail** if the OwnID application configuration cannot be fetched.
     *
     * **Namespace Allowed Origin Rules:**
     * These rules determine which origins are allowed to call namespace functions after the bridge is injected.
     * * For synchronous injection, if the OwnID application configuration is not available during injection, it will be fetched asynchronously in the background.
     *   Once the configuration is fetched, the allowed origins for namespace calls will be updated to include those from the server configuration plus any provided in `allowedOriginRules`.
     * * **Important:** The initial **Injection Allowed Origin Rules** will not be updated after the configuration is fetched asynchronously.
     *
     * The JavaScript object will be injected in any frame whose origin matches the resulting **Allowed Origin Rules**.
     * Each entry in `allowedOriginRules` must follow the format `SCHEME "://" [ HOSTNAME_PATTERN [ ":" PORT ] ]`.
     * Invalid entries will be ignored.
     * Check the [WebView documentation](https://developer.android.com/reference/androidx/webkit/WebViewCompat#addWebMessageListener(android.webkit.WebView,java.lang.String,java.util.Set%3Cjava.lang.String%3E,androidx.webkit.WebViewCompat.WebMessageListener)) for more details.
     *
     * If the WebView's [findViewTreeLifecycleOwner] returns `null`, you **must** provide a [LifecycleOwner] to manage the bridge's lifecycle.
     *
     * This method must be called on the main thread.
     *
     * @param webView The [WebView] instance to inject the bridge into.
     * @param allowedOriginRules An optional set of allowed origin rules to be used **in addition to** those from the OwnID application configuration.
     * @param owner  Optional [LifecycleOwner] for managing the bridge (required if WebView doesn't have one).
     * @param synchronous Determines whether injection should be performed synchronously or asynchronously. Defaults to `false` (asynchronous). See the "Injection Allowed Origin Rules" section for details on the behavior in each mode.
     * @param onResult Optional callback to receive the injection result.
     *
     * @throws IllegalStateException If called on a non-main thread or if attempting to attach to the same WebView multiple times.
     * @throws IllegalArgumentException If no lifecycle owner is available.
     * @throws OwnIdException If an error occurs during injection, such as:
     *  * Failure to fetch the OwnID application configuration (in asynchronous mode).
     *  * No valid HTTPS URLs are provided in the OwnID application configuration or `allowedOriginRules` (in asynchronous mode).
     *  * The WebView doesn't support `WebViewFeature.WEB_MESSAGE_LISTENER` or `WebViewFeature.DOCUMENT_START_SCRIPT`.
     */
    @MainThread
    public fun injectInto(
        webView: WebView,
        allowedOriginRules: Set<String> = emptySet(),
        owner: LifecycleOwner? = webView.findViewTreeLifecycleOwner(),
        synchronous: Boolean = false,
        onResult: InjectCallback? = null
    )

    /**
     * Callback interface for receiving the result of the `injectInto` operation.
     *
     * @see OwnIdWebViewBridge.injectInto
     */
    public interface InjectCallback {
        /**
         * Called when the injection operation completes.
         *
         * @param error An [OwnIdException] if an error occurred during injection, or `null` if the injection was successful.
         */
        public fun onResult(error: OwnIdException?)
    }
}