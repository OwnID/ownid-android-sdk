<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/images/logo.svg">
  <source media="(prefers-color-scheme: light)" srcset="docs/images/logo-dark.svg">
  <img alt="OwnID" src="docs/images/logo-dark.svg" width="260">
</picture>

# OwnID Android SDK

[![OwnID Core Android SDK version](https://img.shields.io/maven-central/v/com.ownid.android-sdk/core?label=OwnID%20Core%20Android%20SDK)](https://central.sonatype.com/artifact/com.ownid.android-sdk/core)
[![OwnID Compose Android SDK version](https://img.shields.io/maven-central/v/com.ownid.android-sdk/compose?label=OwnID%20Compose%20Android%20SDK)](https://central.sonatype.com/artifact/com.ownid.android-sdk/compose)

OwnID Android SDK helps apps add passkey-first authentication, account verification, and passkey enrollment while keeping the app in control of its users, sessions, and identity-provider setup.

## Install

Use the smallest SDK that covers the flow or feature your app needs.

| Add | Provides |
| --- | --- |
| Core SDK<br/>`com.ownid.android-sdk:core` | SDK configuration, providers, Elite Flow, Headless, Passkey Enrollment, WebBridge, and passkey authentication flows. |
| Compose UI SDK<br/>`com.ownid.android-sdk:compose` | Boost widgets, SDK-provided Compose operation UI, app-hosted operation UI, themes, colors, and reusable UI components. Depends on Core. |

### Core SDK

Core SDK requires Android API 23+ and is built with Kotlin 1.8.22.

```kotlin
dependencies {
    implementation("com.ownid.android-sdk:core:<latest-version>")
}
```

### Compose UI SDK

Compose UI SDK requires Android API 23+ and Compose 1.7.8+, and is built with Kotlin 1.9.25.

```kotlin
dependencies {
    implementation("com.ownid.android-sdk:compose:<latest-version>")
}
```

> [!NOTE]
> `com.ownid.android-sdk:compose` depends on `com.ownid.android-sdk:core`. Add `core` separately only when your app intentionally pins the Core artifact version.

## Enable Passkeys

OwnID uses Android [Credential Manager](https://developer.android.com/identity/credential-manager) for passkey creation and authentication. Credential Manager passkeys work on Android 9 (API level 28) and higher.

To keep passkey behavior aligned with the latest Android platform fixes, add the latest stable AndroidX Credentials artifacts to your app:

```kotlin
dependencies {
    implementation("androidx.credentials:credentials:<latest-version>")
    implementation("androidx.credentials:credentials-play-services-auth:<latest-version>")
}
```

Use the latest stable version from the [AndroidX Credentials release notes](https://developer.android.com/jetpack/androidx/releases/credentials).

### Digital Asset Links

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

## Start Here

1. [Install the SDK](#install) for your flow or feature.
2. [Enable Passkeys](#enable-passkeys) as baseline SDK setup.
3. [Configure OwnID](docs/setup/configuration.md) before using the SDK.
4. [Register providers](docs/setup/providers.md) required by the OwnID functionality and identity systems your app uses.
   Implement app-specific providers in your app; copy source-only helpers from [`providers/`](providers/) only when the Providers guide calls for them.

After setup, choose the integration path that matches the screen or user journey you are building.

| App need | Use | SDK artifact | Start with |
| --- | --- | --- | --- |
| Add OwnID to an existing native login screen | Boost Login Widget | Compose UI SDK | [Boost Flow](docs/flows/boost-flow.md) |
| Add create-passkey to account creation | Boost Create Passkey Widget | Compose UI SDK | [Boost Flow](docs/flows/boost-flow.md) |
| Use hosted OwnID authentication UI in the app | Elite Flow | Core SDK | [Elite Flow](docs/flows/elite-flow.md) |
| Build fully custom native authentication UI | Headless | Core SDK | [Headless](docs/flows/headless.md) |
| Add a passkey for a signed-in user | Passkey Enrollment | Core SDK | [Passkey Enrollment](docs/flows/passkey-enrollment.md) |
| Connect OwnID Web SDK inside an app `WebView` | WebBridge | Core SDK | [WebBridge](docs/integration/webbridge.md) |

> [!TIP]
> Migrating from OwnID SDK version 3? Start with [Migration from Version 3 to Version 4](docs/upgrade/v3-to-v4.md).

The full documentation map is in [Documentation](docs/README.md).

## Examples

- [DemoBase](demoBase/) shows standard SDK setup, Boost Flow, Elite Flow, Headless, Passkey Enrollment, and example identity-provider wiring.
- [DemoAdvanced](demoAdvanced/) shows customized Boost widgets, app-hosted operation UI, low-level API and operation scenarios, Headless, and Google provider wiring.

These apps are examples. Use public SDK source, published artifacts, and documentation as the integration contract.

## Version Support

- **v4:** Current version, recommended for all integrations.
- **v3:** Maintenance mode. Receives only critical bug fixes and security updates; no new features.
- **v2:** Obsolete, deprecated, and no longer supported, including security updates.

## Data Safety

See [Data Safety](docs/setup/data-safety.md) for SDK data handling notes.

## Support

For integration help, contact [support@ownid.com](mailto:support@ownid.com).

## License

This SDK is distributed under the [Apache 2.0 license](LICENSE).
