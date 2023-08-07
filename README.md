![logo](logo.svg)
<br>
<br>
[![OwnID Core Android SDK version](https://img.shields.io/maven-central/v/com.ownid.android-sdk/core?label=Core-Android%20SDK)](https://search.maven.org/artifact/com.ownid.android-sdk/core) [![OwnID Compose Android SDK version](https://img.shields.io/maven-central/v/com.ownid.android-sdk/compose?label=Compose%20Android%20SDK)](https://search.maven.org/artifact/com.ownid.android-sdk/compose) [![OwnID Gigya-Android SDK version](https://img.shields.io/maven-central/v/com.ownid.android-sdk/gigya?label=Gigya-Android%20SDK)](https://search.maven.org/artifact/com.ownid.android-sdk/gigya) [![OwnID Gigya-Screen-Sets-Android SDK version](https://img.shields.io/maven-central/v/com.ownid.android-sdk/gigya-screen-sets?label=Gigya-Screen-Sets-Android%20SDK)](https://search.maven.org/artifact/com.ownid.android-sdk/gigya-screen-sets)

## OwnID Android SDK

The [OwnID](https://ownid.com/) Android SDK is a client library written in Kotlin that provides a passwordless login alternative for your Android application by using cryptographic keys to replace the traditional password. Built as an Android library (.aar), the SDK allows the user to perform Registration and Login flows in a native Android application.

The OwnID Android SDK consists of a Core module along with modules that are specific to an integration. The Core module provides core functionality like setting up an OwnID configuration, performing network calls to the OwnID server, interacting with a browser, handling a redirect URI, and checking and returning results to the Android application.

The OwnID Android SDK is built with Android API version 33 and Java 8+, and supports the minimum API version 23.

For more details about using this SDK, see the module documentation for your identity platform:

- **[OwnID Gigya Android SDK](docs/sdk-gigya-doc.md)** - Extends Core SDK functionality by providing integration with Email/Password-based [Gigya Authentication](https://github.com/SAP/gigya-android-sdk).

- **[OwnID Gigya-Screen-Sets Android SDK](docs/sdk-gigya-screens-doc.md)** - For apps that use Gigya Screen-Sets authentication.

- **[OwnID Redirect Android SDK](docs/sdk-redirect-doc.md)** - Help Android app that use WebView or CustomTab to redirect back from browser to native app.

## Compose Integration

OwnID Compose Android SDK extends OwnID Core SDK and provides [Android Compose](https://developer.android.com/jetpack/compose) wrapper for OwnID UI widgets. Check documentation for details **[OwnID Compose Android SDK](./docs/sdk-compose-doc.md)**.

## Demo applications

This repository contains OwnID Demo application sources for different types of identity platforms:
 - Gigya integration demo (`demo-gigya` module in Kotlin, `demo-gigya-compose` module for Android Compose,`demo-gigya-java` module in Java).

The `demo-common` module contains common code for all demo applications.

You can run these demo apps on a physical Android device or an emulator.

## Feedback
We'd love to hear from you! If you have any questions or suggestions, feel free to reach out by creating a GitHub issue.

## License

```
Copyright 2022 OwnID INC.

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