![logo](docs/logo.svg)
<br>
<br>
[![OwnID Core Android SDK version](https://img.shields.io/maven-central/v/com.ownid.android-sdk/core?label=Core%20Android%20SDK)](https://search.maven.org/artifact/com.ownid.android-sdk/core) [![OwnID Compose Android SDK version](https://img.shields.io/maven-central/v/com.ownid.android-sdk/compose?label=Compose%20Android%20SDK)](https://search.maven.org/artifact/com.ownid.android-sdk/compose) [![OwnID Gigya Android SDK version](https://img.shields.io/maven-central/v/com.ownid.android-sdk/gigya?label=Gigya%20Android%20SDK)](https://search.maven.org/artifact/com.ownid.android-sdk/gigya) [![OwnID Redirect Android SDK version](https://img.shields.io/maven-central/v/com.ownid.android-sdk/redirect?label=Redirect%20Android%20SDK)](https://search.maven.org/artifact/com.ownid.android-sdk/redirect)

## OwnID Android SDK

The [OwnID](https://www.ownid.com/) Android SDK is a client library that offers a secure and passwordless login alternative for your Android applications. By leveraging [Passkeys](https://www.passkeys.com/), it replaces conventional passwords and enhances authentication methods. Packaged as an Android library (.aar), this SDK enables users to seamlessly execute registration and login flows within their native Android applications.

### Key components of the OwnID Android SDK:

- **OwnID Core** - Provides fundamental functionalities such as SDK configuration, UI widgets, interaction with the Android system, and returning OwnID flow results to the Android application. It also provide two flow variants:
   + **Native Flow** - utilizes native OwnID UI widgets and native UI.
   + **Elite Flow** - provides a powerful and flexible framework for integrating and customizing authentication processes within your applications.

- **OwnID Integration Component** - An optional extension of the Core SDK, designed for seamless integration with identity platforms on the native side. When present, it executes the actual registration and login processes into the identity platform.

### To integrate OwnID with your identity platform, you have three pathways:

- **[Direct Integration](docs/sdk-direct-integration.md)** - Handle OwnID Response data directly without using the Integration component.

- **[Custom Integration](docs/sdk-custom-integration.md)** - Develop your OwnID Integration component tailored to your identity platform.

- **Prebuilt Integration** - Utilize the existing OwnID SDK with a prebuilt Integration component. Options include:

   - **[OwnID Gigya](docs/sdk-gigya.md)** - Expands Core SDK functionality by offering a prebuilt Gigya Integration, supporting Email/Password-based [Gigya Authentication](https://github.com/SAP/gigya-android-sdk). It also includes the [OwnID WebView Bridge](docs/sdk-webbridge.md), enabling native Passkeys functionality for Gigya Web Screen-Sets with OwnID Web SDK.

### Additional Components:

- **[OwnID Compose](docs/sdk-compose.md)** - Extends OwnID Core SDK by providing an [Android Compose](https://developer.android.com/jetpack/compose) wrapper for OwnID UI widgets.

- **[OwnID WebView Bridge](docs/sdk-webbridge.md)** - A Core SDK component that introduces native Passkeys functionality to the OwnID Web SDK when running within an [Android WebView](https://developer.android.com/reference/android/webkit/WebView).

- **[OwnID Redirect](docs/sdk-redirect.md)** - Help Android app that use WebView or CustomTab with OwnID WebApp to redirect back from browser to native app.

### Advanced Configuration

Explore advanced configuration options in OwnID Core Android SDK by referring to the [Advanced Configuration](docs/sdk-advanced-configuration.md) documentation.

## Demo applications

This repository hosts various OwnID Demo applications, each showcasing integration scenarios:

- **Direct Handling of OwnID Response**: `demo-integration` module exemplifies the integration process by directly handling OwnID Response.

- **Gigya Integration Demos**:

   - `demo-gigya` module provides an example of Gigya integration using Kotlin.
   - `demo-gigya-compose` module provides an example  of Gigya integration with Android Compose.
   - `demo-gigya-java` module provides an example of Gigya integration using Java.

- **Gigya Web Screen-Sets with WebView Bridge Demo**: `demo-gigya-screens` module.

- **Redirect Demo for Gigya Web Screen-Sets Integration**: `demo-redirect` module.

The `demo-common` module contains common code for all demo applications.

You can run these demo apps on a physical Android device or an emulator.

## Supported Languages

The OwnID SDK has built-in support for multiple languages. The SDK loads translations in runtime and selects the best language available. The list of currently supported languages can be found [here](https://i18n.prod.ownid.com/langs.json).

The SDK will also make the RTL adjustments if needed. If the user's mobile device uses a language that is not supported, the SDK displays the UI in English.

## Data Safety

The OwnID SDK collects data and information about events inside the SDK using Log Data. This Log Data does not include any personal data that can be used to identify the user such as username, email, and password. It does include general information like the device Internet Protocol (“IP”) address, device model, operating system version, time and date of events, and other statistics.

Log Data is sent to the OwnID server using an encrypted process so it can be used to collect OwnID service statistics and improve service quality. OwnID does not share Log Data with any third party services.

## Feedback

We'd love to hear from you! If you have any questions or suggestions, feel free to reach out by creating a GitHub issue.

## License

```
Copyright 2023 OwnID INC.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```