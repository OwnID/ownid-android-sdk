# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Please add these rules to your existing keep rules in order to suppress warnings.
# This is generated automatically by the Android Gradle plugin.
-keep class com.gigya.android.sdk.** { *; }

-dontwarn com.android.volley.Cache$Entry
-dontwarn com.android.volley.Cache
-dontwarn com.android.volley.DefaultRetryPolicy
-dontwarn com.android.volley.NetworkResponse
-dontwarn com.android.volley.NoConnectionError
-dontwarn com.android.volley.ParseError
-dontwarn com.android.volley.Request
-dontwarn com.android.volley.RequestQueue$RequestFilter
-dontwarn com.android.volley.RequestQueue
-dontwarn com.android.volley.Response$ErrorListener
-dontwarn com.android.volley.Response$Listener
-dontwarn com.android.volley.Response
-dontwarn com.android.volley.RetryPolicy
-dontwarn com.android.volley.VolleyError
-dontwarn com.android.volley.VolleyLog
-dontwarn com.android.volley.toolbox.HttpHeaderParser
-dontwarn com.android.volley.toolbox.Volley
-dontwarn com.google.firebase.iid.FirebaseInstanceId
-dontwarn com.google.firebase.iid.InstanceIdResult
-dontwarn com.google.firebase.messaging.FirebaseMessagingService
-dontwarn com.google.firebase.messaging.RemoteMessage
-dontwarn okhttp3.logging.HttpLoggingInterceptor$Level
-dontwarn okhttp3.logging.HttpLoggingInterceptor$Logger
-dontwarn okhttp3.logging.HttpLoggingInterceptor