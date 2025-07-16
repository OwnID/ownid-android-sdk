# OwnID Gigya Android SDK

The OwnID Android SDK is a client library offering a secure and passwordless login alternative for your Android applications. It leverages [Passkeys](https://www.passkeys.com) to replace conventional passwords, fostering enhanced authentication methods.

The OwnID Gigya Android SDK expands [OwnID Android Core SDK](../README.md) functionality by offering a prebuilt Gigya Integration, supporting Email/Password-based [Gigya Authentication](https://github.com/SAP/gigya-android-sdk).

For more general information about OwnID SDKs, see [OwnID Android SDK](../README.md).

## Table of contents

* [Before You Begin](#before-you-begin)
* [Add Dependency to Gradle File](#add-dependency-to-gradle-file)
* [Enable Java 8 Compatibility in Your Project](#enable-java-8-compatibility-in-your-project)
* [Enable passkey authentication](#enable-passkey-authentication)
* [Create Configuration File](#create-configuration-file)
* [Create Default OwnID Gigya Instance](#create-default-ownid-gigya-instance)
* [Add OwnID UI to application](#add-ownid-ui-to-application)
   + [Gigya with Web Screen-Sets](#gigya-with-web-screen-sets)
   + [Gigya with Compose](#gigya-with-compose)
     * [Implement the Registration Screen](#implement-the-registration-screen)
     * [Implement the Login Screen](#implement-the-login-screen)
     * [Social Login and Account linking](#social-login-and-account-linking)
   + [Gigya with Elite](#gigya-with-elite)
     * [Set Providers](#set-providers)
     * [Start the Elite](#start-the-elite)    
* [Credential enrollment](#credential-enrollment)
* [Creating custom OwnID Gigya Instances](#creating-custom-ownid-gigya-instances)
* [Error and Exception Handling](#error-and-exception-handling)

## Before You Begin

Before incorporating OwnID into your Android app, you need to create an OwnID application in [OwnID Console](https://console.ownid.com) and integrate it with your Gigya project. For details, see [OwnID Gigya Integration Basics](gigya-integration-basics.md).

You should also ensure you have done everything to [integrate Gigya's service into your Android project](https://github.com/SAP/gigya-android-sdk).

## Add Dependency to Gradle File

The OwnID Gigya Android SDK is available from the Maven Central repository. As long as your app's `build.gradle` file includes `mavenCentral()` as a repository, you can include the OwnID SDK by adding the following to the Gradle file (the latest version is: [![Maven Central](https://img.shields.io/maven-central/v/com.ownid.android-sdk/gigya?label=Gigya%20Android%20SDK)](https://search.maven.org/artifact/com.ownid.android-sdk/gigya)):

```groovy
implementation "com.ownid.android-sdk:gigya:<latest version>"
implementation "com.ownid.android-sdk:compose:<latest version>"
```

The OwnID Gigya Android SDK is built with Android API version 35 and Java 8+, and supports the minimum API version 23.

## Enable Java 8 Compatibility in Your Project

The OwnID SDK requires [Java 8 bytecode](https://developer.android.com/studio/write/java8-support). To enable this feature, add the following to your Gradle file:

```groovy
android {
   compileOptions {
      sourceCompatibility JavaVersion.VERSION_1_8
      targetCompatibility JavaVersion.VERSION_1_8
   }
   kotlinOptions {
      jvmTarget = "1.8"
   }
}
```

## Enable Passkey authentication

The OwnID SDK uses [Passkeys](https://www.passkeys.com) to authenticate users. To enable passkey support for your Android app, you need to:

1. Set the Android package name and signing certificate SHA-256 hash for your OwnID application in the [OwnID Console](https://console.ownid.com) in the Integration > Native Apps section.
2. Associate your application with a website that your application owns using [Digital Asset Links](https://developers.google.com/digital-asset-links) by following this guide: [Add support for Digital Asset Links](https://developer.android.com/training/sign-in/passkeys#add-support-dal).

To obtain the SHA-256 hash of your Android app's signing certificate, use the keytool utility from the Java Development Kit (JDK). Run the following command in a terminal, replacing `[keystore_path]` and `[key_alias]` with your actual keystore path and key alias:
```
keytool -list -v -keystore [keystore_path] -alias [key_alias]
```
You may be prompted for the keystore and key passwords.

## Create Configuration File

The OwnID SDK uses a configuration file in your `assets` folder to configure itself.  At a minimum, this JSON configuration file defines the OwnID App Id - the unique identifier of your OwnID application, which you can obtain from the [OwnID Console](https://console.ownid.com). Create `assets/ownIdGigyaSdkConfig.json` and define the `appId` parameter:
```json
{
   "appId": "gephu342dnff2v" // Replace with your App Id
}
```

For additional configuration options, including logging and UI language, see [Advanced Configuration](sdk-advanced-configuration.md).

## Create Default OwnID Gigya Instance

Before adding OwnID UI to your app screens, you need to use an Android Context and instance of Gigya to create a default instance of OwnID Gigya. Most commonly, you create this OwnID Gigya instance using the Android [Application class](https://developer.android.com/reference/kotlin/android/app/Application). For information on initializing and creating an instance of Gigya, refer to the [Gigya documentation](https://github.com/SAP/gigya-android-sdk).

See [complete example](../demo/gigya/src/main/java/com/ownid/demo/gigya/DemoApp.kt)

```kotlin
class MyApplication : Application() {
   override fun onCreate() {
      super.onCreate()
      // Create Gigya instance
      Gigya.setApplication(this)
       
      // Create OwnID Gigya instance
      OwnId.createGigyaInstanceFromFile(this /* Context */)

      // If you use custom account class
      // OwnId.createGigyaInstanceFromFile(this, gigya = Gigya.getInstance(MyAccount::class.java))
   }
}
```

> [!NOTE]
>
> The OwnID SDK automatically reads the `ownIdGigyaSdkConfig.json` configuration file from your `assets` folder and creates a default > instance that is accessible as `OwnId.gigya`. For details about creating a custom OwnID Gigya instance, see [Creating custom OwnID Gigya Instances](#creating-custom-ownid-gigya-instances).

## Add OwnID UI to application

The process of integrating OwnID into your Registration or Login screens varies depending on the type of UI utilized in your application.

If your application utilizes Gigya with Web Screen-Sets, OwnID integration can be achieved through [OwnID WebSDK](https://docs.ownid.com/) and [OwnID Android SDK WebView Bridge](sdk-webbridge.md).

If your application employs native Android Compose with Gigya, please follow the instructions provided under [Gigya with Compose](#gigya-with-compose).

### Gigya with Web Screen-Sets

If you're running Gigya with Web Screen-Sets and want to utilize the [OwnID Android SDK WebView Bridge](sdk-webbridge.md), then add `OwnId.configureGigyaWebBridge()` **before** initializing Gigya SDK:

See [complete example](../demo/gigya-screens/src/main/java/com/ownid/demo/gigya/DemoApp.kt)

```kotlin
class MyApplication : Application() {
   override fun onCreate() {
      super.onCreate()

      // Configure Gigya SDK to use OwnId Gigya WebBridge
      OwnId.configureGigyaWebBridge()

      // Create Gigya instance
      Gigya.setApplication(this)
       
      // Create OwnID Gigya instance
      OwnId.createGigyaInstanceFromFile(this /* Context */)

      // If you use custom account class
      // OwnId.createGigyaInstanceFromFile(this, gigya = Gigya.getInstance(MyAccount::class.java))
   }
}
```

Next, add [OwnID WebSDK](https://docs.ownid.com/) to Gigya Web Screen-Sets.

> [!NOTE]
>
> Add `*.gigya.com` to whitelisted domains in OwnID Console

### Gigya with Compose

#### Implement the Registration Screen

Using the OwnID Compose SDK to implement passwordless authentication starts by adding an `OwnIdRegisterButton` component to your Registration screen. Your app then waits while the user interacts with OwnID.

```kotlin
val ownIdRegisterViewModel = ownIdViewModel<OwnIdRegisterViewModel>()

OwnIdRegisterButton(
    loginId = email.value,
    ownIdRegisterViewModel = ownIdRegisterViewModel,
    onReadyToRegister = { loginId ->
        // (Optional) Set the actual login id that was used in OwnID flow into your registration UI 
        if (loginId.isNotBlank()) email.value = loginId 
    },
    onLogin = { authToken -> /* User is logged in with OwnID. */ },
    onError = { error -> /* Handle 'error' according to your application flow. */ }
)
```

Update your **Create Account** button or equivalent to complete registration with OwnID if the user finished OwnID registration flow:

```kotlin
Button(
    onClick = {
        if (ownIdRegisterViewModel.isReadyToRegister) {
            // Register user with OwnID.
            val params = mutableMapOf<String, Any>()
            ownIdRegisterViewModel.register(email.value, GigyaRegistrationParameters(params))
        } else {
            // Register user with a password.
        }
    }
) {
   Text(text = "Create Account")
}
```

Check [complete example](../demo/gigya/src/main/java/com/ownid/demo/gigya/screen/auth/RegistrationScreen.kt)

![OwnIdButton UI Example](button_view_example.png) ![OwnIdButton Dark UI Example](button_view_example_dark.png)

`OwnIdRegisterButton` component wraps `OwnIdButton` and has the following parameters:
   * `loginId` - Current user login id (e.g., email or phone number).
   * `modifier` - (optional) The modifier to be applied to the `OwnIdRegisterButton`.
   * `ownIdRegisterViewModel` - (optional) An instance of `OwnIdRegisterViewModel`.
   * `onReadyToRegister` - (optional) A function called when the user successfully completes OwnID registration flow.
   * `onLogin` - (optional) A function called when the user successfully completes registration with OwnID and is logged in with OwnID with optional authentication token that can be used to refresh a session.
   * `onResponse` - (optional) A function called at the end of the successful OwnID registration flow with `OwnIdFlowResponse`.
   * `onError` -  (optional) A function called when an error occurs during the OwnID registration process, with `OwnIdException`.
   * `onUndo` - (optional) A function called when the user selects the "Undo" option in the ready-to-register state.
   * `onBusy` - (optional) A function called to notify the busy status during the OwnID registration process.
   * `styleRes` - A style resource reference. Use it to style `OwnIdButton`

For Gigya integration the functions `onReadyToRegister`, `onLogin`, `onError`, `onUndo`, and `onBusy` will be called.

For additional UI customization, see [Button UI customization](sdk-advanced-configuration.md#button-ui-customization).

Invoking the `ownIdViewModel.register(email, GigyaRegistrationParameters(params))` function and passing any necessary data within the `GigyaRegistrationParameters` parameter will triggers the actual user account creation within Gigya, utilizing the standard Gigya SDK function [`register(String email, String password, Map<String, Object> params, GigyaLoginCallback<T> callback)`](https://github.com/SAP/gigya-android-sdk/tree/main/sdk-core#register-via-email--password). There is no need to directly call this Gigya function, as `OwnIdRegisterViewModel.register()` handles it internally.

You can define custom parameters for the registration request as `Map<String, Object>` and add them to `GigyaRegistrationParameters`. These parameters are also passed to the [Gigya registration call](https://github.com/SAP/gigya-android-sdk/tree/main/sdk-core#register-via-email--password).

In addition, the `OwnIdRegisterViewModel.register()` function set Gigya's `profile.locale` value to the first locale from [OwnID SDK language](sdk-advanced-configuration.md/#ownid-sdk-language) list. You can override this behavior by setting required locale in `GigyaRegistrationParameters` like `GigyaRegistrationParameters(mutableMapOf<String, Any>("profile" to """{"locale":"en"}"""))`.

#### Implement the Login Screen

Similar to the Registration screen, add the passwordless authentication to your application's Login screen by including one of OwnID button variants:

1. Side-by-side button: The `OwnIdButton` that is located on the side of the password input field.
2. Password replacing button: The `OwnIdAuthButton` that replaces password input field.

You can use any of this buttons based on your requirements. 

1. **Side-by-side button**

    Add the following to your Login screen's layout file:

    ```kotlin
    OwnIdLoginButton(
        loginIdProvider = { email.value },
        onLogin = { authToken -> /* User is logged in with OwnID. */ },
        onError = { error -> /* Handle 'error' according to your application flow. */ }
    )
    ```
    Check [complete example](../demo/gigya/src/main/java/com/ownid/demo/gigya/screen/auth/LoginScreen.kt#L94)

    ![OwnIdButton UI Example](button_view_example.png) ![OwnIdButton Dark UI Example](button_view_example_dark.png)

    `OwnIdLoginButton` component wraps `OwnIdButton` and has such parameters:
      * `loginIdProvider` - A function returning the current user login id (e.g., email or phone number).
      * `modifier` - (optional) The modifier to be applied to the `OwnIdLoginButton`.
      * `ownIdLoginViewModel` - (optional) An instance of [OwnIdLoginViewModel].
      * `loginType` - (optional) Login type. Default `OwnIdLoginType.Standard`.
      * `onLogin` - (optional) A function called when the user successfully completes login with OwnID with optional authentication token that can be used to refresh a session.
      * `onResponse` - (optional) A function called at the end of the successful OwnID login flow with `OwnIdFlowResponse`.
      * `onError` -  (optional) A function called when an error occurs during the OwnID login process, with `OwnIdException`.
      * `onBusy` - (optional) A function called to notify the busy status during the OwnID login process.
      * `styleRes` - A style resource reference. Use it to style `OwnIdButton`

    For Gigya integration the functions `onLogin`, `onError`, and `onBusy` will be called.  
    
1. **Password replacing button**

     Add the following to your Login screen's layout file:

    ```kotlin 
    OwnIdAuthLoginButton(
        loginIdProvider = { email.value },
        onLogin = { authToken -> /* User is logged in with OwnID. */ },
        onError = { error -> /* Handle 'error' according to your application flow. */ }
    )
    ```
    Check [complete example](../demo/gigya/src/main/java/com/ownid/demo/gigya/screen/auth/LoginScreen.kt#L147)
  
    ![OwnIdAuthButton UI Example](auth_button_view_example.png) ![OwnIdAuthButton Dark UI Example](auth_button_view_example_dark.png)

    `OwnIdAuthLoginButton` component wraps `OwnIdAuthButton` and has such parameters:
      * `loginIdProvider` - A function returning the current user login id (e.g., email or phone number).
      * `modifier` - (optional) The modifier to be applied to the `OwnIdLoginButton`.
      * `ownIdLoginViewModel` - (optional) An instance of [OwnIdLoginViewModel].
      * `loginType` - (optional) Login type. Default `OwnIdLoginType.Standard`.
      * `onLogin` - (optional) A function called when the user successfully completes login with OwnID with optional authentication token that can be used to refresh a session.
      * `onResponse` - (optional) A function called at the end of the successful OwnID login flow with `OwnIdFlowResponse`.
      * `onError` -  (optional) A function called when an error occurs during the OwnID login process, with `OwnIdException`.
      * `onBusy` - (optional) A function called to notify the busy status during the OwnID login process.
      * `styleRes` - A style resource reference. Use it to style `OwnIdAuthButton`

    For Gigya integration the functions `onLogin`, `onError`, and `onBusy` will be called.  

For additional UI customization, see [Button UI customization](sdk-advanced-configuration.md#button-ui-customization).

#### Social Login and Account linking

If you use Gigya [Social Login](https://github.com/SAP/gigya-android-sdk/tree/main/sdk-core#social-login) feature then you need to handle [Account linking interruption](https://github.com/SAP/gigya-android-sdk/tree/main/sdk-core#interruptions-handling---account-linking-example) case. To let OwnID do account linking add the `OwnIdLoginButton` or `OwnIdAuthLoginButton` component to your application's Account linking screen same as for Login screen and pass `OwnIdLoginType.LinkSocialAccount` as `loginType` parameter:

```kotlin
OwnIdLoginButton(
    loginIdProvider = { email.value },
    loginType = OwnIdLoginType.LinkSocialAccount,
    ...
)
```

Check [complete example](../demo/gigya/src/main/java/com/ownid/demo/gigya/screen/auth/ConflictingAccountScreen.kt)

### Gigya with Elite

Elite Flow provides a powerful and flexible framework for integrating and customizing authentication processes within your applications. To implement passwordless authentication using the Elite Flow in OwnID SDK, follow these three steps:

1. Set providers.
1. Start the Elite Flow with event handlers.

#### Set Providers

Providers manage critical components such as session handling and authentication mechanisms, including traditional password-based logins. They allow developers to define how users are authenticated, how sessions are maintained and how accounts are managed within the application. All providers use `suspend` functions.

You can define such providers:
1. **Session Provider**: Manages user session creation.
1. **Account Provider**: Handles account creation.
1. **Authentication Provider**: Manages various authentication mechanisms.
    1. Password-based authentication provider.

OwnID Gigya SDK provides default implementations for the required providers, so you only need to set them up as follows:

```kotlin
import com.ownid.sdk.OwnId
import com.ownid.sdk.getGigyaProviders
import com.ownid.sdk.dsl.providers

OwnId.providers {
    getGigyaProviders(Gigya.getInstance())
}
```

See [complete example](../demo/gigya/src/main/java/com/ownid/demo/gigya/DemoApp.kt#L40)

#### Start the Elite

To start a Elite, call the `start()` function. You can define event handlers for specific actions and responses within the authentication flow. They allow to customize behavior when specific events occur. All event handlers are optional.

```kotlin
OwnId.start {
    events { // All event handlers are optional.
        onNativeAction { name, params ->
            // Called when a native action is requested by other event handlers, such as `onAccountNotFound`.
            // Elite UI is currently closed or will be closed in a moment.
            // Run native actions such as user registration.
        }
        onAccountNotFound { loginId, ownIdData, authToken ->
            // Called when the specified account details do not match any existing accounts.
            // Use it to customize the flow if no account is found.
            // It should return a PageAction to define the next steps in the flow.
            PageAction.<...>
        }
        onFinish { loginId, authMethod, authToken ->
            // Called when the authentication flow successfully completes.
            // Elite UI is currently closed or will be closed in a moment.
            // Define post-authentication actions here, such as session management or navigation.
        }
        onError { cause ->
            // Called when an error occurs during the authentication flow.
            // Elite UI is currently closed or will be closed in a moment.
            // Handle errors gracefully, such as logging or showing a message to the user.
        }
        onClose {
            // Called when the authentication flow is closed, either by the user or automatically.
            // Elite UI is currently closed or will be closed in a moment.
            // Define any cleanup or UI updates needed.
        }
    }
}
```

See [complete example](../demo/gigya/src/main/java/com/ownid/demo/gigya/screen/auth/AuthViewModel.kt#L110)

You can pass additional optional parameters to configure Elite WebView.

```kotlin
OwnId.start(
    options = EliteOptions(
        webView = EliteOptions.WebView(
            baseUrl = "https://mysite.com", // Optional base URL for the WebView content
            html = "<html></html>"          // Optional HTML content to be rendered in the WebView
        )
    )
) {
    events { }
}
```

**Page Actions**

OwnID SDK provides two Page Actions to control the next steps in the Elite flow:

1. `PageAction.Close` - In response to this action the `onClose` event handler will be called.
2. `PageAction.Native.Register(loginId, ownIdData, authToken)` - In response to this action the `onNativeAction` event handler will be called with the action name "register" and parameters containing the `loginId`, `ownIdData`, and `authToken` encoded as a JSON string.

## Credential enrollment

The credential enrollment feature enables users to enroll credentials outside of the login/registration flows. When running Gigya with a native view, you can trigger credential enrollment on demand, for example, after the user registers with a password.

To trigger credential enrollment, create an instance of `OwnIdEnrollmentViewModel` and call the `enrollCredential` method:

```kotlin
val context = LocalContext.current
val ownIdEnrollmentViewModel = ownIdViewModel<OwnIdEnrollmentViewModel>()

ownIdEnrollmentViewModel.enrollCredential(
    context = context,
    loginIdProvider = OwnIdGigya.defaultLoginIdProvider(),
    authTokenProvider = OwnIdGigya.defaultAuthTokenProvider()
)
```

The `enrollCredential` method requires a `loginIdProvider` and an `authTokenProvider`, which have default implementations provided by the OwnID Gigya Android SDK via `OwnIdGigya.defaultLoginIdProvider()` and `OwnIdGigya.defaultAuthTokenProvider()`, respectively.

Optionally, to monitor the status of the last credential enrollment request, you can listen to enrollment events from the StateFlow via `OwnIdEnrollmentViewModel.enrollmentResultFlow`:

```kotlin
ownIdViewModel.enrollmentResultFlow
    .filterNotNull()
    .onEach { Log.i("UserActivity", "enrollmentResult: $it") }
    .launchIn(lifecycleScope)
```

Check [complete example](../demo/gigya/src/main/java/com/ownid/demo/gigya/screen/home/ProfileScreen.kt)

## Creating custom OwnID Gigya Instances

By default, the OwnID SDK creates an instance using the `assets/ownIdGigyaSdkConfig.json` configuration file. To create the custom instance you can use a function that reads a configuration file or use one that accepts a JSON string of configuration options directly. The following sections describe this process.

#### Option 1: Custom Instance Using Configuration File

You can use the `OwnId.createGigyaInstanceFromFile` function to create a custom instance. This function reads from a configuration file when creating the instance. If you would prefer to pass the configuration options using a JSON string, see [Custom Instance Using JSON String](#option-2-custom-instance-using-json-string).

```kotlin
OwnId.createGigyaInstanceFromFile(
    context: Context, // Android context
    configurationAssetFileName: String = OwnIdGigya.DEFAULT_CONFIGURATION_FILE_NAME, // JSON configuration file
    gigya: Gigya<out GigyaAccount> = Gigya.getInstance(), // Gigya instance
    instanceName: InstanceName = OwnIdGigya.DEFAULT_INSTANCE_NAME // Instance name
): OwnIdGigya
```

To get a default OwnID SDK instance, use `OwnId.gigya`. To get an instance with a custom name, use `OwnId.gigya(instanceName)`.

#### Option 2: Custom Instance Using JSON String

You can use the `OwnId.createInstanceFromJson` function to create a custom instance that is configured using a JSON string. If you would to prefer to create a custom instance using a configuration file, see [Custom Instance Using Configuration File](#option-1-custom-instance-using-configuration-file).

```kotlin
OwnId.createGigyaInstanceFromJson(
    context: Context, // Android context
    configurationJson: String, // String with configuration in JSON format
    gigya: Gigya<out GigyaAccount> = Gigya.getInstance(), // Gigya instance
    instanceName: InstanceName = OwnIdGigya.DEFAULT_INSTANCE_NAME // Instance name
): OwnIdGigya
```

To get a default OwnID SDK instance, use `OwnId.gigya`. To get an instance with a custom name, use `OwnId.gigya(instanceName)`.

## Error and Exception Handling

The OwnID SDK provides special classes that you can use to add error and exception handling to your application.

The general `OwnIdException` class represents top-level class for errors and exceptions that may happen in the flow of the OwnID SDK. Check its definition in code [OwnIdException](/sdk/core/src/main/java/com/ownid/sdk/exception/OwnIdException.kt):

In addition, the following classes are special exceptions that can occur in the flow of the OwnID SDK:
* `class OwnIdFlowCanceled(val step: String) : OwnIdException("User canceled OwnID ($step) flow.")` - Exception that occurs when user cancelled OwnID flow. Usually application can ignore this error.

* `class OwnIdUserError(val code: String, val userMessage: String, message: String, cause: Throwable? = null) : OwnIdException(message, cause)` - Error that is intended to be reported to end user. The `userMessage` string is localized based on [OwnID SDK language](sdk-advanced-configuration.md/#ownid-sdk-language) and can be used as an error message for user.

* `class OwnIdIntegrationError(message: String, cause: Throwable? = null) : OwnIdException(message, cause)` - General error for wrapping identity platform errors OwnID integrates with.

    For Gigya integration the `GigyaException` is available as a wrapper for `GigyaError`: 
    * `class GigyaException(val gigyaError: GigyaError, message: String) : OwnIdIntegrationError(message)` - use it to get the original `GigyaError` from Gigya SDK.
