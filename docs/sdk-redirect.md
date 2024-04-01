# OwnID Redirect Android

The OwnID Redirect Android SDK help Android app that use WebView or CustomTab to redirect back from browser to native app.

## Add Dependency to Gradle File
The OwnID Redirect Android SDK is available from the Maven Central repository. As long as your app's `build.gradle` file includes `mavenCentral()` as a repository, you can include the OwnID SDK by adding the following to the Gradle file (the latest version is: [![Maven Central](https://img.shields.io/maven-central/v/com.ownid.android-sdk/redirect?label=Redirect%20Android%20SDK)](https://search.maven.org/artifact/com.ownid.android-sdk/redirect)):
```groovy
implementation "com.ownid.android-sdk:redirect:<latest version>"
```
The OwnID Redirect Android SDK is built with Android API version 33 and supports the minimum API version 23.

### Understanding Redirection URI 
The redirection URI determines where the user lands once they are done using their browser to interact with the OwnID Web app. Because it needs to capture this redirect, the OwnID SDK must be registered with the Android OS as a handler of the URI. By default OwnID SDK uses redirection URI as `{applicationId}://ownid/redirect/` whereas the `applicationId` represents the [Android application Id](https://developer.android.com/studio/build/configure-app-module#set-application-id).
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
