# OwnID Android SDK WebView Bridge - WebView Integration

The OwnID Android SDK WebView Bridge, an integral part of the OwnID Core Android SDK, empowers the OwnID Web SDK to leverage the native capabilities of the OwnID Android SDK. 

The bridge facilitates the injection of JavaScript code, enabling communication between the OwnID Web SDK and the native OwnID Android SDK.

To get more information about the OwnID Android SDK, please refer to the [OwnID Android SDK](../README.md) documentation.

## Table of contents
* [Before You Begin](#before-you-begin)
* [WebView Bridge components](#webview-bridge-components)
* [Adding WebView Bridge](#adding-webview-bridge)
   + [1. Utilizing Prebuilt Integration-specific WebView Bridge](#1-utilizing-prebuilt-integration-specific-webview-bridge)
   + [2. Manual Integration of WebView Bridge](#2-manual-integration-of-webview-bridge)

## Before You Begin

Before incorporating the OwnID Android SDK WebView Bridge into your Android application, ensure that you have already integrated the OwnID Android SDK. Detailed step-by-step instructions can be found in the [OwnID Android SDK](../README.md) documentation.

Additionally, make sure you have successfully integrated the [OwnID Web SDK](https://docs.ownid.com) into your WebView.

## WebView Bridge components

The OwnID Android SDK WebView Bridge comprises the following components:

 - **Native Passkey Support**. 
   
   Ensure that you enable passkey authentication in your Android application by following the steps outlined in the **Enable Passkey Authentication** section of the OwnID documentation.

## Adding WebView Bridge

You have two primary options for integrating the OwnID WebView Bridge into your application:

### 1. Utilizing Prebuilt Integration-specific WebView Bridge

Currently OwnID SDK provides prebuilt WebView Bridge for [OwnID Gigya Android SDK](sdk-gigya.md#add-ownid-webview-bridge) for seamless integration with Gigya Web Screen-Sets with OwnID Web SDK.

### 2. Manual Integration of WebView Bridge

To manually integrate the OwnID WebView Bridge into your WebView, follow these steps:

1. Create an instance of the OwnID Android SDK, for example `ownIdInstance`. Detailed instructions can be found in the [OwnID Android SDK](../README.md).

1. Inject the OwnID WebView Bridge into your [WebView](https://developer.android.com/reference/android/webkit/WebView) by invoking `ownIdInstance.createWebViewBridge().injectInto(webView)`. This is typically done during the creation of the WebView and before loading its content. Refer to the in-code documentation for the `injectInto` method for additional configuration options.
