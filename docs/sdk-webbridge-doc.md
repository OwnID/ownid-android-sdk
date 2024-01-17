# OwnID Android SDK WebView Bridge - WebView Integration
The OwnID Android SDK WebView Bridge is a part of OwnID Core Android SDK that enables OwnID Web SDK to use native capabilities of the OwnID Android SDK.
OwnID WebView Bridge injects JavaScript code that can be used by OwnID Web SDK to communicate with native OwnID Android SDK.

To get more information about the OwnID Android SDK, please refer to the [OwnID Android SDK](../README.md) documentation.

## Table of contents
* [Before You Begin](#before-you-begin)
* [Adding WebView Bridge](#adding-webview-bridge)
* [Manual integration of the OwnID WebView Bridge](#manual-integration-of-the-ownid-webview-bridge)

---

## Before You Begin
Before incorporating OwnID Android SDK WebView Bridge into your Android application, ensure that you have already incorporated the OwnID Android SDK. You can find step-by-step instructions in the [OwnID Android SDK](../README.md) documentation.

Additionally, make sure you have integrated the [OwnID Web SDK is added into WebView](https://docs.ownid.com).

## Adding WebView Bridge
You have two primary options for integrating the OwnID WebView Bridge into your application:
- Utilize pre-built integration-specific OwnID WebView Bridge provided by the OwnID SDKs:
  + [OwnID Gigya Android SDK](sdk-gigya-doc.md) for seamless integration with Gigya Screen-Sets.
- Use manual integration of the OwnID WebView Bridge tailored to your identity platform.

## Manual integration of the OwnID WebView Bridge
To add the OwnID WebView Bridge to your WebView, follow these steps:

1. Create an instance of OwnID Android SDK: `ownIdInstance`. You can find step-by-step instructions in the [OwnID Android SDK](../README.md) documentation.
2. Inject OwnID WebView Bridge into a [WebView](https://developer.android.com/reference/android/webkit/WebView) by invoking `ownIdInstance.createWebViewBridge().injectInto(webView)` typically when the is created and before loading WebView content. For additional configuration options check in-code documentation for `injectInto` method.