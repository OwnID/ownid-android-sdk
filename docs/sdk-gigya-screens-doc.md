# OwnID Gigya-Screen-Sets Android

The OwnID Gigya-Screen-Sets Android SDK integrates with an Android app that uses [Gigya Screen-Sets](https://github.com/SAP/gigya-android-sdk/tree/main/sdk-core#using-screen-sets) for authentication. If your app uses native Email/Password-based [Gigya Authentication](https://github.com/SAP/gigya-android-sdk) without Screen-Sets, use the **[OwnID Gigya-Android SDK](docs/sdk-gigya-doc.md)** instead.

The OwnID Gigya-Screen-Sets Android SDK is a client library written in Java that provides a simple way to add the "Skip Password" feature to the registration and login screens of your native application. The SDK is packaged as an Android library (.aar) that is readily available from the Maven Central repository. For more general information about OwnID SDKs, see [OwnID Android SDK](../README.md).

## Before You Begin
Before incorporating OwnID into your Android app, you need to create an OwnID application and integrate it with your Gigya project. For details, see [OwnID-Gigya Integration Basics](gigya-integration-basics.md).

You should also ensure you have done everything to [integrate Gigya's service into your Android project](https://github.com/SAP/gigya-android-sdk).

## Add Dependency to Gradle File
The OwnID Gigya-Screen-Sets Android SDK is available from the Maven Central repository. As long as your app's `build.gradle` file includes `mavenCentral()` as a repository, you can include the OwnID SDK by adding the following to the Gradle file (the latest version is: [![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.ownid.android-sdk/gigya-screen-sets/badge.svg)](https://github.com/OwnID/ownid-android-sdk)):
```groovy
implementation "com.ownid.android-sdk:gigya-screen-sets:<latest version>"
```
The OwnID Gigya-Screen-Sets Android SDK is built with Android API version 32 and supports the minimum API version 23.

### Understanding Redirection URI 
The redirection URI determines where the user lands once they are done using their browser to interact with the OwnID web app. Because it needs to capture this redirect, the OwnID SDK must be registered with the Android OS as a handler of the URI. By default OwnID SDK use redirection URI as `{applicationId}://ownid/redirect/` whereas the `applicationId` represent the [Android application Id](https://developer.android.com/studio/build/configure-app-module#set-application-id).
```xml
<activity
    android:name="com.ownid.sdk.internal.OwnIdRedirectActivity"
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
By doing so OwnID SDK assumes that the `applicationId` will match the value returned by [Context.getPackageName()](https://developer.android.com/reference/kotlin/android/content/Context#getpackagename). If the `applicationId` cannot be used as URI scheme ([see requirements](https://datatracker.ietf.org/doc/html/rfc3986#section-3.1)) an error will be thrown `IllegalArgumentException: Application package name (applicationId) cannot be used as URI scheme: https://datatracker.ietf.org/doc/html/rfc3986#section-3.1`. In this case, use custom redirection uri.

### Redirection URI Alternatives
If you want to avoid using a default redirection URI, you can add an intent-filter for OwnIdRedirectActivity with required custom url to your AndroidManifest.xml file:

```xml
<activity
    android:name="com.ownid.sdk.internal.OwnIdRedirectActivity"
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

In other cases, you might need to use an HTTPS redirection URI instead of a custom scheme. It is highly recommended that you secure HTTPS redirects by configuring the redirection URI as a verified [Android App Links](https://developer.android.com/training/app-links/index.html).

```xml
<activity
    android:name="com.ownid.sdk.internal.OwnIdRedirectActivity"
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