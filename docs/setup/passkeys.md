# Passkey Setup

OwnID uses Android [Credential Manager](https://developer.android.com/identity/credential-manager) for passkey creation and authentication. Credential Manager passkeys work on Android 9 (API level 28) and higher.

To keep passkey behavior aligned with the latest Android platform fixes, add the latest stable AndroidX Credentials artifacts to your app:

```kotlin
dependencies {
    implementation("androidx.credentials:credentials:<latest-version>")
    implementation("androidx.credentials:credentials-play-services-auth:<latest-version>")
}
```

Use the latest stable version from the [AndroidX Credentials release notes](https://developer.android.com/jetpack/androidx/releases/credentials).

## Digital Asset Links

Passkeys require your app and relying party domain to be associated with [Digital Asset Links](https://developers.google.com/digital-asset-links). Host an `assetlinks.json` file at:

```text
https://<relying-party-domain>/.well-known/assetlinks.json
```

The file must be publicly available over HTTPS, return `HTTP 200`, use a JSON content type, avoid redirects, and allow automated retrieval of `/.well-known/assetlinks.json`.

```json
[
  {
    "relation": [
      "delegate_permission/common.handle_all_urls",
      "delegate_permission/common.get_login_creds"
    ],
    "target": {
      "namespace": "android_app",
      "package_name": "<android_application_id>",
      "sha256_cert_fingerprints": [
        "<sha256_signing_certificate_fingerprint>"
      ]
    }
  }
]
```

Add an entry for each application ID and signing certificate variant that should use passkeys, such as debug, release, or product flavor builds.

See Android's [Credential Manager prerequisites](https://developer.android.com/identity/credential-manager/prerequisites) for the platform requirements and validation steps.

## Verify the Association

> [!WARNING]
> The association file must be publicly accessible in every environment where passkeys are tested or used. The endpoint must not require a password, Basic Auth, SSO, cookies, VPN access, mTLS, an IP allowlist, or an interactive WAF or bot-protection challenge.

Verify the association file on the exact HTTPS host used as the relying party domain:

```text
https://<relying-party-domain>/.well-known/assetlinks.json
```

Confirm that the endpoint:

- Returns `HTTP 200` with `Content-Type: application/json`
- Does not redirect
- Returns valid JSON rather than an HTML login or error page
- Is accessible from a public network without authentication or VPN access
- Allows automated retrieval and is not blocked by `robots.txt`

Confirm that the Android application ID and certificate fingerprints match the installed build. Validate the hosted file with Google's [Statement List Generator and Tester](https://developers.google.com/digital-asset-links/tools/generator), selecting **Credential Sharing** as the relation type.

## Allow Time for Updates

The association file may be cached by your hosting provider, CDN, and Google verification services. Updating the origin file does not guarantee that devices receive the new version immediately.

Inspect the response headers:

```bash
curl -sS -D - -o /dev/null \
  https://<relying-party-domain>/.well-known/assetlinks.json
```

Review `Cache-Control`, `Age`, `Expires`, `ETag`, and `Last-Modified`. Avoid long cache lifetimes, such as one week, if you expect to update the file. A long `max-age` can keep an earlier version valid after the origin file has changed.

Digital Asset Links determines statement validity from the underlying data TTL, subject to implementation limits. The [Digital Asset Links API](https://developers.google.com/digital-asset-links/reference/rest/v1/statements/list) returns `maxAge`, which indicates how much longer its result should be considered valid.

After an update, first confirm that the public endpoint returns the new file and review its current cache headers.
