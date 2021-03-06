![logo](docs/logo.svg)
<br>
<br>
[![OwnID Core-Android SDK version](https://img.shields.io/maven-central/v/com.ownid.android-sdk/core?label=Core-Android%20SDK)](https://docs.ownid.com) [![OwnID Gigya-Android SDK version](https://img.shields.io/maven-central/v/com.ownid.android-sdk/gigya?label=Gigya-Android%20SDK)](https://docs.ownid.com)

## OwnID Android SDK

The [OwnID](https://ownid.com/) Android SDK is a client library written in Kotlin that provides a passwordless login alternative for your Android application by using cryptographic keys to replace the traditional password. Built as an Android library (.aar), the SDK allows the user to perform Registration and Login flows in a native Android application.

The OwnID Android SDK consists of a Core module along with modules that are specific to an integration. The Core module provides core functionality like setting up an OwnID configuration, performing network calls to the OwnID server, interacting with a browser, handling a redirect URI, and checking and returning results to the Android application.

The OwnID Android SDK is built with Android API version 32 and Java 8+, and supports the minimum API version 23.

For more details about using this SDK, see the module documentation for your identity platform:

- OwnID Gigya-Android SDK 

## Demo applications

This repository contains OwnID Demo application sources for different types of identity platforms:
 - Gigya integration demo (`demo-gigya` module in Kotlin, `demo-gigya-java` module in Java).

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