-keepnames class com.ownid.sdk.** { *; }

-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** {
  *;
}