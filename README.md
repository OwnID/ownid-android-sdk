![logo](docs/logo.svg)
<br>
<br>
[![OwnID Core Android SDK version](https://img.shields.io/maven-central/v/com.ownid.android-sdk/core?label=Core%20Android%20SDK)](https://search.maven.org/artifact/com.ownid.android-sdk/core) [![OwnID Compose Android SDK version](https://img.shields.io/maven-central/v/com.ownid.android-sdk/compose?label=Compose%20Android%20SDK)](https://search.maven.org/artifact/com.ownid.android-sdk/compose) [![OwnID Gigya Android SDK version](https://img.shields.io/maven-central/v/com.ownid.android-sdk/gigya?label=Gigya%20Android%20SDK)](https://search.maven.org/artifact/com.ownid.android-sdk/gigya) [![OwnID Redirect Android SDK version](https://img.shields.io/maven-central/v/com.ownid.android-sdk/redirect?label=Redirect%20Android%20SDK)](https://search.maven.org/artifact/com.ownid.android-sdk/redirect)

## OwnID Android SDK

The [OwnID](https://ownid.com/) Android SDK is a client library written in Kotlin that provides a passwordless login alternative for your Android application by using [Passkeys](https://www.passkeys.com/) to replace the traditional password. Built as an Android library (.aar), the SDK allows the user to perform Registration and Login flows in a native Android application.

The OwnID Android SDK consists of a [Core](docs/sdk-core-doc.md) module along with modules that are specific to an identity platform like Firebase or Gigya. The Core module provides core functionality like setting up an OwnID configuration, performing network calls to the OwnID server, interacting with Android system, and checking and returning results to the Android application. 

The following modules extend the Core module for a specific identify management system:
- **[OwnID Gigya Android SDK](docs/sdk-gigya-doc.md)** - Extends Core SDK functionality by providing integration with Email/Password-based [Gigya Authentication](https://github.com/SAP/gigya-android-sdk).

- **[OwnID Redirect Android SDK](docs/sdk-redirect-doc.md)** - Help Android app that use WebView or CustomTab to redirect back from browser to native app.

The OwnID Android SDK is built with Android API version 34 and Java 8+, and supports the minimum API version 23.
For details on using the SDK, see the module documentation for your identity platform.

## Other identity platforms

You can use OwnID Core Android SDK to gain all of the benefits of OwnID with your identity platform. Check **[OwnID Core Android SDK - Custom Integration](docs/sdk-core-doc.md)** for detailed steps.

## WebView Integration

You can use OwnID Android SDK WebView Bridge to seamlessly integrate the native capabilities of the OwnID Android SDK into the OwnID WebSDK. Check **[OwnID Android SDK WebView Bridge](docs/sdk-webbridge-doc.md)** for detailed steps.

## Compose Integration

OwnID Compose Android SDK extends OwnID Core SDK and provides [Android Compose](https://developer.android.com/jetpack/compose) wrapper for OwnID UI widgets. Check documentation for details **[OwnID Compose Android SDK](docs/sdk-compose-doc.md)**.

## Demo applications

This repository contains OwnID Demo application sources for different types of integrations:
 - Gigya integration demo (`demo-gigya` module in Kotlin, `demo-gigya-compose` module for Android Compose, `demo-gigya-java` module in Java).
 - Gigya Screen Sets integration demo (`demo-gigya-screens` module in Kotlin).
 - Redirect demo for Gigya Screen Sets integration  (`demo-redirect` module in Kotlin).
 - Custom integration demo - `demo-integration`.

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