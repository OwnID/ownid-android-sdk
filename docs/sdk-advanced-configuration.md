# OwnID Android SDK - Advanced Configuration

The OwnID Android SDK offers multiple configuration options:

## Table of contents

* [Before You Begin](#before-you-begin)
* [Logging Events](#logging-events)
* [OwnID Environment](#ownid-environment)
* [OwnID SDK Language](#ownid-sdk-language)
* [Redirection URI Alternatives](#redirection-uri-alternatives)
* [Provide Login ID to OwnID](#provide-login-id-to-ownid)
* [Button UI customization](#button-ui-customization)
* [Custom view](#custom-view)
* [Auto Backup rules](#auto-backup-rules)

## Before You Begin

The configuration options listed here are part of OwnID Code Android SDK. Check [documentation](/README.md) to be sure that it's available to the type of integration you use.

## Logging Events

OwnID SDK has a Logger that is used to log its events. The default implementation, `OwnIdLogger.DefaultLogger()`, directs logs to `android.util.Log`. To use a custom Logger, implement the `OwnIdLogger.Logger` interface, then specify your custom logger class instance and/or custom tag using the `init` method:

```kotlin
OwnIdLogger.set("Custom-Tag", CustomLogger())
```

By default, logging is **turned off**. Enable logging with `OwnIdLogger.enabled = true`.

Logging can also be activated from SDKs configuration file (like `assets/ownIdSdkConfig.json`) by including the optional `enableLogging` parameter:

```json
{
  "appId": "...",
  "enableLogging": true
}
```

> [!IMPORTANT]
> It is strongly advised to disable logging in production builds.

## OwnID Environment

By default, OwnID operates in the production environment based on the specified `appId` in the configuration. However, you have the flexibility to set a different environment. Available options include `uat` and `staging`. To specify a non-production environment, use the `env` key in the configuration JSON:

```json
{
  "appId": "...",
  "env": "uat"
}
```

## OwnID SDK Language

By default, the OwnID SDK utilizes the language TAGs list (well-formed [IETF BCP 47 language tag](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Accept-Language)), based on the device locales set by the user in the Android system. However, you can customize this behavior by manually setting the OwnID SDK language TAGs list. There are two methods to achieve this:

1. **Set language TAGs list Provider** - Define a function that returns the language TAGs list for the OwnID SDK.

   <details open>
   <summary>Kotlin</summary>

   ```kotlin
   OwnIdLoginViewModel.setLanguageTagsProvider { listOf("en") }
   // or
   OwnIdRegisterViewModel.setLanguageTagsProvider { listOf("en") }
   ```
   </details>

   <details>
   <summary>Java</summary>

   ```java
   OwnIdLoginViewModel.setLanguageTagsProvider(() -> Collections.singletonList("en"));
   // or
   OwnIdRegisterViewModel.setLanguageTagsProvider(() -> Collections.singletonList("en"));
   ```
   </details>

2. **Set language TAGs list directly** - Manually specify the language TAGs list:

   <details open>
   <summary>Kotlin</summary>

   ```kotlin
   OwnIdLoginViewModel.setLanguageTags(listOf("en"))
   // or
   OwnIdRegisterViewModel.setLanguageTags(listOf("en"))
   ```
   </details>

   <details>
   <summary>Java</summary>

   ```java
   OwnIdLoginViewModel.setLanguageTags(Collections.singletonList("en"));
   // or
   OwnIdRegisterViewModel.setLanguageTags(Collections.singletonList("en"));
   ```
   </details>

> [!NOTE]
> In case both methods are utilized, the SDK follows this priority:
> 
> 1. The list from the Provider takes precedence if it's not empty.
> 1. Then, the directly specified language TAGs list is used if not empty.
> 1. Finally, the list from device locales is employed.

## Redirection URI Alternatives

> [!IMPORTANT]
> Redirection URI is required only if the OwnID flow involves the OwnID Web App.

The redirection URI determines where users lands once they are done using their browser to interact with the OwnID Web App. To capture this redirect, the OwnID SDK must be registered with the Android OS as a handler for the URI. By default, the OwnID SDK uses the redirection URI as `{applicationId}://ownid/redirect/`, where `applicationId` represents the [Android application Id](https://developer.android.com/studio/build/configure-app-module#set-application-id).

```xml
<activity
    android:name="com.ownid.sdk.internal.nativeflow.steps.webapp.OwnIdWebAppRedirectActivity"
    android:exported="true"
    android:launchMode="singleTask">

    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data
            android:host="ownid"
            android:path="/redirect/"
            android:scheme="${applicationId}" />
    </intent-filter>
</activity>
```

The OwnID SDK assumes that the `applicationId` matches the value returned by [`Context.getPackageName()`](https://developer.android.com/reference/kotlin/android/content/Context#getpackagename). If the `applicationId` cannot be used as a URI scheme ([see requirements](https://datatracker.ietf.org/doc/html/rfc3986#section-3.1)), an error message will appear in the OwnID SDK logs (if logging is enabled): `Application package name (applicationId) cannot be used as URI scheme: https://datatracker.ietf.org/doc/html/rfc3986#section-3.1`.  In this case, use custom redirection uri.

If you prefer a custom redirection URI, add an intent-filter for `OwnIdWebAppRedirectActivity` with the required custom URL to your `AndroidManifest.xml` file:

```xml
<activity
    android:name="com.ownid.sdk.internal.nativeflow.steps.webapp.OwnIdWebAppRedirectActivity"
    android:exported="true"
    android:launchMode="singleTask"
    tools:node="replace">

    <intent-filter>
        <action android:name="android.intent.action.VIEW"/>
        <category android:name="android.intent.category.DEFAULT"/>
        <category android:name="android.intent.category.BROWSABLE"/>
        <data
                android:host="myhost"
                android:scheme="com.myapp.demo" />
    </intent-filter>
</activity>
```

Specify this custom URL in the OwnID configuration with the key `redirectUrl`:

```json
{
   "appId": "...",
   "redirectUrl": "com.myapp.demo://myhost"
}
```

In some cases, HTTPS redirection URIs might be needed. For enhanced security, configure the HTTPS redirection URI as a verified [Android App Link](https://developer.android.com/training/app-links/index.html):

```xml
<activity
    android:name="com.ownid.sdk.internal.nativeflow.steps.webapp.OwnIdWebAppRedirectActivity"
    android:exported="true"
    android:launchMode="singleTask"
    tools:node="replace">

    <intent-filter android:autoVerify="true">
        <action android:name="android.intent.action.VIEW"/>
        <category android:name="android.intent.category.DEFAULT"/>
        <category android:name="android.intent.category.BROWSABLE"/>
        <data android:scheme="https"
              android:host="demo.myapp.com"
              android:path="/ownid"/>
    </intent-filter>
</activity>
```

## Provide Login ID to OwnID

If you opt not to use the `loginIdEditText` view attribute to identify your Login ID `EditText` widget, you have four code-based options to provide the Login ID to the OwnID SDK:

1. Set Login ID value itself:

    ```kotlin
    OwnIdButton.setLoginId(loginId: String?)
    ```

2. Set Login ID provider:

    ```kotlin
    OwnIdButton.setLoginIdProvider(loginIdProvider: (() -> String)?)
    ```

3. Set Login ID EditText View:

    ```kotlin
    OwnIdButton.setLoginIdView(loginIdView: EditText?)
    ```

4. Set Login ID EditText View ID:

    ```kotlin
    OwnIdButton.setLoginIdViewId(@IdRes loginIdViewId: Int)
    ```

> [!NOTE]
> 
> In case multiple methods of specifying the Login ID are used, the SDK prioritizes as follows (from highest to lowest):
> 
> 1. Values from the `setLoginId` method take precedence.
> 1. Values from `setLoginIdProvider` follow.
> 1. Values from `setLoginIdView` come next.
> 1. Values from `setLoginIdViewId` are considered.
> 1. Finally, the value from the `loginIdEditText` attribute is used.

## Button UI customization

### Side-by-side button

The `OwnIdButton` view provides parameters for widget positioning at `start` (default) or `end` of password input field, show/hide "or" text and spinner, "or" text appearance and configurable color parameters: background color (default value `#FFFFFF`, default value-night: `#2A3743`), border color (default value `#D0D0D0`, default value-night: `#2A3743`) and icon color (default value `#0070F2`, default value-night: `#2E8FFF`), spinner indicator color (default value `#ADADAD`, default value-night: `#BDBDBD`) and spinner track color (default value `#DFDFDF`, default value-night: `#717171`).

Customize these attributes through view attributes or a defined style:

#### Via View Attributes:

```xml
<com.ownid.sdk.view.OwnIdButton
    app:backgroundColor="@color/com_ownid_sdk_widgets_button_color_background"
    app:iconColor="@color/com_ownid_sdk_widgets_button_color_icon"
    app:borderColor="@color/com_ownid_sdk_widgets_button_color_border"
    app:orTextAppearance="@style/OwnIdButton.OrTextAppearance.Default"
    app:spinnerIndicatorColor="@color/com_ownid_sdk_widgets_button_color_spinner_indicator"
    app:spinnerTrackColor="@color/com_ownid_sdk_widgets_button_color_spinner_track"
    app:showOr="true"
    app:widgetPosition="start"
    app:showSpinner="true" />
```

#### Via Style Attribute:

Define a style:

```xml
<resources>
    <style name="OwnIdButton.OrTextAppearance.Custom" parent="@style/OwnIdButton.OrTextAppearance.Default">
        <item name="android:textColor">@color/com_ownid_sdk_widgets_button_color_text</item>
    </style>

    <style name="OwnIdButton.Custom" parent="OwnIdButton.Default">
        <item name="widgetPosition">start</item>
        <item name="showOr">true</item>
        <item name="orTextAppearance">@style/OwnIdButton.OrTextAppearance.Default</item>
        <item name="backgroundColor">@color/com_ownid_sdk_widgets_button_color_background</item>
        <item name="borderColor">@color/com_ownid_sdk_widgets_button_color_border</item>
        <item name="iconColor">@color/com_ownid_sdk_widgets_button_color_icon</item>
        <item name="showSpinner">true</item>
        <item name="spinnerIndicatorColor">@color/com_ownid_sdk_widgets_button_color_spinner_indicator</item>
        <item name="spinnerTrackColor">@color/com_ownid_sdk_widgets_button_color_spinner_track</item>
        <item name="tooltipTextAppearance">@style/OwnIdButton.TooltipTextAppearance.Default</item>
        <item name="tooltipBackgroundColor">@color/com_ownid_sdk_widgets_button_color_tooltip_background</item>
        <item name="tooltipBorderColor">@color/com_ownid_sdk_widgets_button_color_tooltip_border</item>
        <item name="tooltipPosition">none</item>
    </style>
</resources>
```

and then set it in view attribute:

```xml
<com.ownid.sdk.view.OwnIdButton
    style="@style/OwnIdButton.Custom" />
```

### Password replacing button

The `OwnIdAuthButton` view offers parameters for text appearance and configurable color parameters: background color (default value `#0070F2`, default value-night: `#3771DF`), spinner indicator color (default value `#FFFFFF`, default value-night: `#FFFFFF`), ripple color (default value `#60FFFFFF`, default value-night: `#60FFFFFF`) and spinner track color (default value `#80FFFFFF`, default value-night: `#80FFFFFF`). 

Customize these attributes through view attributes or a defined style:

#### Via View Attributes:

```xml
<com.ownid.sdk.view.OwnIdAuthButton
    app:textAppearance="@style/OwnIdAuthButton.TextAppearance.Custom"
    app:shapeAppearance="@style/OwnIdAuthButton.ShapeAppearance.Custom"
    app:backgroundTint="@color/com_ownid_sdk_widgets_button_auth_color_background"
    app:spinnerIndicatorColor="@color/com_ownid_sdk_widgets_button_auth_color_spinner_indicator"
    app:spinnerTrackColor="@color/com_ownid_sdk_widgets_button_auth_color_spinner_track"
    app:rippleColor="@color/com_ownid_sdk_widgets_button_auth_color_ripple" />
```

#### Via Style Attribute:

Define a style:

```xml
<resources>
    <style name="OwnIdAuthButton.TextAppearance.Custom" parent="@style/OwnIdAuthButton.TextAppearance.Default">
        <item name="android:textColor">@color/com_ownid_sdk_widgets_button_auth_color_text</item>
        <item name="android:textStyle">bold</item>
        <item name="android:textSize">16sp</item>
        <item name="android:letterSpacing">0.0</item>
    </style>

    <style name="OwnIdAuthButton.ShapeAppearance.Custom" parent="@style/OwnIdAuthButton.ShapeAppearance.Default">
        <item name="cornerFamily">rounded</item>
        <item name="cornerSize">@dimen/com_ownid_sdk_widgets_button_auth_corner_radius</item>
    </style>

    <style name="OwnIdAuthButton.Custom" parent="@style/OwnIdAuthButton.Default">
        <item name="textAppearance">@style/OwnIdAuthButton.TextAppearance.Custom</item>
        <item name="shapeAppearance">@style/OwnIdAuthButton.ShapeAppearance.Custom</item>
        <item name="backgroundTint">@color/com_ownid_sdk_widgets_button_auth_color_background</item>
        <item name="spinnerIndicatorColor">@color/com_ownid_sdk_widgets_button_auth_color_spinner_indicator</item>
        <item name="spinnerTrackColor">@color/com_ownid_sdk_widgets_button_auth_color_spinner_track</item>
        <item name="rippleColor">@color/com_ownid_sdk_widgets_button_auth_color_ripple</item>
    </style>
</resources>
```

and then set it in view attribute:

```xml
<com.ownid.sdk.view.OwnIdAuthButton
    style="@style/OwnIdAuthButton.Custom" />
```

## Custom view

To incorporate OwnID Login/Registration functionality into any [Android View](https://developer.android.com/reference/android/view/View), use the `attachToView` method from `OwnIdLoginViewModel` and `OwnIdRegisterViewModel`. This enables integration with OwnID for the specified view:

```kotlin
OwnIdLoginViewModel.attachToView(
    view: View, // An instance of OwnIdButton, OwnIdAuthButton, or any View.
    owner: LifecycleOwner? = view.findViewTreeLifecycleOwner(), // (optional) A LifecycleOwner for view.
    loginIdProvider: (() -> String)? = null, // (optional) A function that returns user's Login ID as a [String]. If set, then for OwnIdButton, OwnIdAuthButton it will be used as loginIdProvider, for other view types it will be used to get user's Login ID.
    loginType: OwnIdLoginType = OwnIdLoginType.Standard, // (optional) A type of login [OwnIdLoginType].
    onOwnIdResponse: (Boolean) -> Unit = {} //(optional) A function that will be called when OwnID has OwnIdResponse. Use it to change view UI.
)
```

## Auto Backup rules

[Auto Backup for Apps](https://developer.android.com/identity/data/autobackup) automatically backs up a user's data from apps that target and run on Android 6.0 (API level 23) or higher. For OwnID Android SDK it's recomenred to exclude SDK's data from backup.

You can do this by adding folowink parameters to you backup configuration:

In `backup_rules.xml` (on Android 11 and lower): 
```
<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>
    <exclude 
        domain="file"
        path="./datastore/ownid/" />
</full-backup-content>
```

In `data_extraction_rules.xml` (on Android 12 or higher):
```
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <exclude 
            domain="file"
            path="./datastore/ownid/" />
    </cloud-backup>
    <device-transfer>
        <exclude 
            domain="file"
            path="./datastore/ownid/" />
    </device-transfer>
</data-extraction-rules>
```