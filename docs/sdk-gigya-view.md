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
   + [Gigya with native views](#gigya-with-native-views)
     * [Implement the Registration Screen](#implement-the-registration-screen)
     * [Implement the Login Screen](#implement-the-login-screen)
     * [Social Login and Account linking](#social-login-and-account-linking)
     * [Tooltip](#tooltip)
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
```

The OwnID Gigya Android SDK is built with Android API version 34 and Java 8+, and supports the minimum API version 23.

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

## Enable passkey authentication

The OwnID SDK uses [Passkeys](https://www.passkeys.com) to authenticate users. 

> [!IMPORTANT]
>
> To enable passkey support for your Android app, associate your app with a website that your app owns using [Digital Asset Links](https://developers.google.com/digital-asset-links) by following this guide: [Add support for Digital Asset Links](https://developer.android.com/training/sign-in/passkeys#add-support-dal).

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

If your application employs native Android views with Gigya, please follow the instructions provided under [Gigya with native views](#gigya-with-native-views).

### Gigya with Web Screen-Sets

If you're running Gigya with Web Screen-Sets and want to utilize the [OwnID Android SDK WebView Bridge](sdk-webbridge.md), then add `OwnId.configureGigyaWebBridge()` **before** initializing Gigya SDK:

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

### Gigya with native views

#### Implement the Registration Screen

Using the OwnID SDK to implement passwordless authentication starts by adding an `OwnIdButton` view to your Registration screen's layout file. Your app then waits for events while the user interacts with OwnID.

> [!NOTE]
>
> When a user registers with OwnID, a random password is generated and set for the user's Gigya account.

**Add OwnID UI**

Add the passwordless authentication to your application's Registration screen by including the `OwnIdButton` view to your Registration screen's layout file:

```xml
<com.ownid.sdk.view.OwnIdButton
    android:id="@+id/own_id_register"
    android:layout_width="wrap_content"
    android:layout_height="0dp"
    app:loginIdEditText="@id/et_fragment_create_email" />
```

![OwnIdButton UI Example](button_view_example.png) ![OwnIdButton Dark UI Example](button_view_example_dark.png)

`OwnIdButton` is an Android [ConstraintLayout](https://developer.android.com/reference/androidx/constraintlayout/widget/ConstraintLayout) view that contains OwnID button - customized [ImageView](https://developer.android.com/reference/android/widget/ImageView) and [TextView](https://developer.android.com/reference/android/widget/TextView) with "or" text. The OwnID button ImageView is always square in size, recommended to use height to not less `40dp`. It's recommended to use [ConstraintLayout](https://developer.android.com/training/constraint-layout) and position `OwnIdButton` to the start on password EditText with top constraint set to the top and bottom to the bottom of Password EditText. If you want to put `OwnIdButton` to the end on password EditText, set attribute `app:widgetPosition="end"` for `OwnIdButton`.

Define the `loginIdEditText` attribute to reference the [EditText](https://developer.android.com/reference/android/widget/EditText) widget that correspond to the Login ID field of your Registration screen. Including these attribute simplifies the way the SDK obtains the user's Login ID. If you want your code to provide the user's Login ID to the SDK instead of using the view attribute, see [Advanced Configuration: Provide Login ID to OwnID](sdk-advanced-configuration.md#provide-login-id-to-ownid).

For additional `OwnIdButton` UI customization see [Advanced Configuration: Button UI customization](sdk-advanced-configuration.md#button-ui-customization).

**Listen to Events from OwnID Register View Model**

Now that you have added the OwnID UI to your screen, you need to listen to registration events that occur when the user interacts with OwnID. First, create an instance of `OwnIdRegisterViewModel` in your Fragment or Activity, passing in an OwnID Gigya instance as the argument:

```kotlin
class MyRegistrationFragment : Fragment() {
   private val ownIdViewModel: OwnIdRegisterViewModel by ownIdViewModel()
}
```

Within that Fragment or Activity, insert code that attaches a `OwnIdButton` view to the `OwnIdRegisterViewModel` and listens to OwnID Register integration events:

```kotlin
class MyRegistrationFragment : Fragment() {
    private val ownIdViewModel: OwnIdRegisterViewModel by ownIdViewModel()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ownIdViewModel.attachToView(view.findViewById(R.id.own_id_register))
        
        ownIdViewModel.integrationEvents.observe(viewLifecycleOwner) { ownIdEvent ->
            when (ownIdEvent) {
                // Event when OwnID is busy processing request
                is OwnIdRegisterEvent.Busy -> { /* (Optional) Show busy status 'ownIdEvent.isBusy' according to your application UI */  }
                
                // Event when user successfully finishes OwnID registration flow
                is OwnIdRegisterEvent.ReadyToRegister -> {
                    // Obtain user's email before calling the register() function.
                    ownIdViewModel.register(email)
                    // or 
                    // val params = mutableMapOf<String, Any>()
                    // ownIdViewModel.register(email, GigyaRegistrationParameters(params))
                }

                // Event when user select "Undo" option in ready-to-register state
                OwnIdRegisterEvent.Undo -> { /* */}

                // Event when OwnID creates Gigya account and logs in user
                is OwnIdRegisterEvent.LoggedIn -> { /* User is logged in with OwnID. Use 'ownIdEvent.authType' to get type of authentication that was used during OwnID flow.*/ }

                // Event when an error happened during OwnID flow 
                is OwnIdRegisterEvent.Error ->
                    when (val cause = ownIdEvent.cause) {
                        is GigyaException -> { /* Handle 'cause.gigyaError' according to your application flow */ }
                        else -> { /* Handle 'cause' according to your application flow  */ }
                    }
            }
        }
    }
}
```

**Calling the register() Function**

> [!IMPORTANT]
>
> Upon receiving the `ReadyToRegister` event, indicating the completion of the OwnID Registration flow, the user is returned to the Registration screen. 
> 
> It's crucial to note that the user account is not yet created within Gigya at this stage.
>
> On the Registration screen, the user can fill in optional or mandatory data and click the "Submit" button or its equivalent.

On the "Submit" button click, invoke the `ownIdViewModel.register(email, GigyaRegistrationParameters(params))` function, passing any necessary data within the `GigyaRegistrationParameters` parameter. This triggers the actual user account creation within Gigya, utilizing the standard Gigya SDK function [`register(String email, String password, Map<String, Object> params, GigyaLoginCallback<T> callback)`](https://github.com/SAP/gigya-android-sdk/tree/main/sdk-core#register-via-email--password). There is no need to directly call this Gigya function, as `OwnIdRegisterViewModel.register()` handles it internally.

You can define custom parameters for the registration request as `Map<String, Object>` and add them to `GigyaRegistrationParameters`. These parameters are also passed to the [Gigya registration call](https://github.com/SAP/gigya-android-sdk/tree/main/sdk-core#register-via-email--password).

In addition, the `OwnIdRegisterViewModel.register()` function set Gigya's `profile.locale` value to the first locale from [OwnID SDK language](sdk-advanced-configuration.md/#ownid-sdk-language) list. You can override this behavior by setting required locale in `GigyaRegistrationParameters` like `GigyaRegistrationParameters(mutableMapOf<String, Any>("profile" to """{"locale":"en"}"""))`.

#### Implement the Login Screen

The process of implementing your Login screen is very similar to the one used to implement the Registration screen - add an OwnId UI to your Login screen. Your app then waits for events while the user interacts with OwnID.

**Add OwnID UI**

Similar to the Registration screen, add the passwordless authentication to your application's Login screen by including one of OwnID button variants:

1. Side-by-side button: The `OwnIdButton` that is located on the side of the password input field.
2. Password replacing button: The `OwnIdAuthButton` that replaces password input field.

You can use any of this buttons based on your requirements. 

1. **Side-by-side button**

    Add the following to your Login screen's layout file:

    ```xml
    <com.ownid.sdk.view.OwnIdButton
        android:id="@+id/own_id_login"
        android:layout_width="wrap_content"
        android:layout_height="0dp"
        app:loginIdEditText="@id/et_fragment_login_email" />
    ```

    ![OwnIdButton UI Example](button_view_example.png) ![OwnIdButton Dark UI Example](button_view_example_dark.png)

    `OwnIdButton` is an Android [ConstraintLayout](https://developer.android.com/reference/androidx/constraintlayout/widget/ConstraintLayout) view that contains OwnID button - customized [ImageView](https://developer.android.com/reference/android/widget/ImageView) and [TextView](https://developer.android.com/reference/android/widget/TextView) with "or" text. The OwnID button ImageView is always square in size, recommended to use height to not less `40dp`. It's recommended to use [ConstraintLayout](https://developer.android.com/training/constraint-layout) and put `OwnIdButton` to the end on password EditText with top constraint set to the top and bottom to the bottom of Password EditText.

    Define the `loginIdEditText` attribute to reference the [EditText](https://developer.android.com/reference/android/widget/EditText) widget that correspond to the Login ID field of your Login screen. Including these attribute simplifies the way the SDK obtains the user's Login ID. If you want your code to provide the user's Login ID to the SDK instead of using the view attribute, see [Advanced Configuration: Provide Login ID to OwnID](sdk-advanced-configuration.md#provide-login-id-to-ownid).

    For additional `OwnIdButton` UI customization see [Advanced Configuration: Button UI customization](sdk-advanced-configuration.md#button-ui-customization).

1. **Password replacing button**

     Add the following to your Login screen's layout file:

    ```xml
    <com.ownid.sdk.view.OwnIdAuthButton
        android:id="@+id/own_id_login"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        app:loginIdEditText="@id/et_fragment_login_email" />
    ```

    ![OwnIdAuthButton UI Example](auth_button_view_example.png) ![OwnIdAuthButton Dark UI Example](auth_button_view_example_dark.png)

    `OwnIdAuthButton` is an Android [ConstraintLayout](https://developer.android.com/reference/androidx/constraintlayout/widget/ConstraintLayout) view that contains OwnID button - customized [MaterialButton](https://developer.android.com/reference/com/google/android/material/button/MaterialButton) and [CircularProgressIndicator](https://developer.android.com/reference/com/google/android/material/progressindicator/CircularProgressIndicator). It's recommended to use [ConstraintLayout](https://developer.android.com/training/constraint-layout) and position `OwnIdAuthButton` below Login ID EditText with start and end constraint set to the start and end of Login ID EditText.

    Define the `loginIdEditText` attribute to reference the [EditText](https://developer.android.com/reference/android/widget/EditText) widget that correspond to the Login ID field of your Registration screen. Including these attribute simplifies the way the SDK obtains the user's Login ID. If you want your code to provide the user's Login ID to the SDK instead of using the view attribute, see [Advanced Configuration: Provide Login ID to OwnID](sdk-advanced-configuration.md#provide-login-id-to-ownid).

    For additional `OwnIdAuthButton` UI customization see [Advanced Configuration: Button UI customization](sdk-advanced-configuration.md#button-ui-customization).

**Listen to Events from OwnID Login View Model**

Now that you have added the OwnID UI to your screen, you need to listen to login events that occur as the user interacts with OwnID. First, create an instance of `OwnIdLoginViewModel` in your Fragment or Activity, passing in an OwnID Gigya instance as the argument:

```kotlin
class MyLoginFragment : Fragment() {
   private val ownIdViewModel: OwnIdLoginViewModel by ownIdViewModel()
}
```

Within that Fragment or Activity, insert code that attaches a `OwnIdButton` or `OwnIdAuthButton` view to the `OwnIdLoginViewModel` and listens to OwnID Login integration events:

```kotlin
class MyLoginFragment : Fragment() {
    private val ownIdViewModel: OwnIdLoginViewModel by ownIdViewModel()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ownIdViewModel.attachToView(view.findViewById(R.id.own_id_login))

        ownIdViewModel.integrationEvents.observe(viewLifecycleOwner) { ownIdEvent ->
            when (ownIdEvent) {
                // Event when OwnID is busy processing request
                is OwnIdLoginEvent.Busy -> { /* (Optional) Show busy status 'ownIdEvent.isBusy' according to your application UI */  }
                
                // Event when OwnID logs in user
                is OwnIdLoginEvent.LoggedIn -> { /* User is logged in with OwnID. Use 'ownIdEvent.authType' to get type of authentication that was used during OwnID flow.*/ }

                // Event when an error happened during OwnID flow 
                is OwnIdLoginEvent.Error ->
                    when (val cause = ownIdEvent.cause) {
                        is GigyaException -> { /* Handle 'cause.gigyaError' according to your application flow */ }
                        else -> { /* Handle 'cause' according to your application flow  */ }
                    }
            }
        }
    }
}
```

#### Social Login and Account linking

If you use Gigya [Social Login](https://github.com/SAP/gigya-android-sdk/tree/main/sdk-core#social-login) feature then you need to handle [Account linking interruption](https://github.com/SAP/gigya-android-sdk/tree/main/sdk-core#interruptions-handling---account-linking-example) case. To let OwnID do account linking add the `OwnIdButton` or `OwnIdAuthButton` to your application's Account linking screen same as for Login screen and pass `OwnIdLoginType.LinkSocialAccount` parameter to `attachToView` method:

```kotlin
class MyLinkSocialFragment : Fragment() {
    private val ownIdViewModel: OwnIdLoginViewModel by ownIdViewModel()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ownIdViewModel.attachToView(view.findViewById(R.id.own_id_login), loginType = OwnIdLoginType.LinkSocialAccount)

        ...
    }
}
```

#### Tooltip

The OwnID SDK's `OwnIdButton` can show a Tooltip with text "Sign In with Fingerprint" / "Register with Fingerprint". The OwnID Tooltip view is attached to `OwnIdButton` view lifecycle. For login the Tooltip appears every time the `OwnIdButton` view is `onResume` state and hides on `onPause` state. For registration the Tooltip appears when Login ID `EditText` view contains valid email address, and follows the same `onResume`/`onPause` state logic.

![OwnID Tooltip UI Example](tooltip_example.png) ![OwnID Tooltip Dark UI Example](tooltip_example_dark.png)

`OwnIdButton` view has parameters to specify tooltip text appearance, tooltip background color (default value `#FFFFFF`, default value-night: `#2A3743`), tooltip border color (default value `#D0D0D0`, default value-night: `#2A3743`) and tooltip position `top`/`bottom`/`start`/`end`/`none` (default `none`). You can change them by setting values in view attributes:

```xml
<com.ownid.sdk.view.OwnIdButton
    app:tooltipTextAppearance="@style/OwnIdButton.TooltipTextAppearance.Default"
    app:tooltipBackgroundColor="@color/com_ownid_sdk_color_tooltip_background"
    app:tooltipBorderColor="@color/com_ownid_sdk_color_tooltip_border"
    app:tooltipPosition="bottom"/>
```

or via `style` attribute. First defile a style:

```xml
<resources>
    <style name="OwnIdButton.TooltipTextAppearance.Default" parent="@style/TextAppearance.AppCompat" />

    <style name="OwnIdButton.Custom" parent="">
        <item name="tooltipTextAppearance">@style/OwnIdButton.TooltipTextAppearance.Default</item>
        <item name="tooltipBackgroundColor">@color/com_ownid_sdk_color_tooltip_background</item>
        <item name="tooltipBorderColor">@color/com_ownid_sdk_color_tooltip_border</item>
        <item name="tooltipPosition">bottom</item>
    </style>
</resources>
```

and then set it in view attribute:

```xml
<com.ownid.sdk.view.OwnIdButton
    style="@style/OwnIdButton.Custom" />
```

## Credential enrollment

The credential enrollment feature enables users to enroll credentials outside of the login/registration flows. When running Gigya with a native view, you can trigger credential enrollment on demand, for example, after the user registers with a password.

To trigger credential enrollment, create an instance of `OwnIdEnrollmentViewModel` and call the `enrollCredential` method:

```kotlin
class UserActivity : AppCompatActivity() { 
    private val ownIdViewModel: OwnIdEnrollmentViewModel by ownIdViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
       
        ownIdViewModel.enrollCredential(
            context = this@UserActivity,
            loginIdProvider = OwnIdGigya.defaultLoginIdProvider(),
            authTokenProvider = OwnIdGigya.defaultAuthTokenProvider()
        )
    }
}
```

The `enrollCredential` method requires a `loginIdProvider` and an `authTokenProvider`, which have default implementations provided by the OwnID Gigya Android SDK via `OwnIdGigya.defaultLoginIdProvider()` and `OwnIdGigya.defaultAuthTokenProvider()`, respectively.

Optionally, to monitor the status of the last credential enrollment request, you can listen to enrollment events from the StateFlow via `OwnIdEnrollmentViewModel.enrollmentResultFlow`:

```kotlin
ownIdViewModel.enrollmentResultFlow
    .filterNotNull()
    .onEach { Log.i("UserActivity", "enrollmentResult: $it") }
    .launchIn(lifecycleScope)
```

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
