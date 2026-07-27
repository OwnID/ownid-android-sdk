# Enable Passkeys

Use this reference for every Android host app that integrates OwnID SDK v4. It
covers Android passkey prerequisites: app dependencies, app identities, signing
certificates, Digital Asset Links, hosted file checks, and platform availability.

Source docs:

- `../../../../README.md#enable-passkeys`

Platform source of truth:

- Android Credential Manager:
  `https://developer.android.com/identity/credential-manager`
- AndroidX Credentials releases:
  `https://developer.android.com/jetpack/androidx/releases/credentials`
- Credential Manager prerequisites:
  `https://developer.android.com/identity/credential-manager/prerequisites`
- Digital Asset Links:
  `https://developers.google.com/digital-asset-links`

## Contents

- [Baseline Requirement](#baseline-requirement)
- [Inputs To Collect First](#inputs-to-collect-first)
- [Host App Changes](#host-app-changes)
- [Digital Asset Links](#digital-asset-links)
- [Credential Manager Reality](#credential-manager-reality)
- [What To Verify Externally](#what-to-verify-externally)

## Baseline Requirement

Complete Android passkey setup as baseline SDK integration work, independent of
the first OwnID feature or UI surface being integrated. Every integration needs
a known relying-party domain association plan.

## Inputs To Collect First

Confirm these values from the host app, OwnID tenant configuration, and
release/signing owner:

- OwnID `appId`, environment (`Prod` by default, `Uat` only when specified),
  and region used by the app.
- Relying-party domain used by OwnID passkey requests for that tenant.
- Final Android `applicationId` for each build variant that should use
  passkeys, including UAT flavor suffixes such as `.uat`.
- SHA-256 signing certificate fingerprint for each of those variants. Debug,
  internal, upload, app-signing, and release certificates can differ.
- Host app dependency-management style for AndroidX artifacts.
- Minimum supported device behavior. OwnID SDK may support a lower app min SDK;
  Android platform passkeys through Credential Manager require Android 9/API 28
  or higher.

The relying-party domain is the domain in the passkey challenge. It is not
automatically the OwnID `appId`, API `rootUrl`, API host, or marketing
website. Ask the tenant/backend owner when it is not explicit.

## Host App Changes

Add Credential Manager dependencies in the app module that owns the OwnID SDK
integration. Follow the app's existing version catalog, Gradle platform, or
dependency declaration style.

```kotlin
dependencies {
    implementation("androidx.credentials:credentials:<latest-stable-version>")
    implementation("androidx.credentials:credentials-play-services-auth:<latest-stable-version>")
}
```

Use the current stable AndroidX Credentials version unless the host app already
pins a compatible version through central dependency management. The OwnID README
links to AndroidX Credentials release notes as the version source of truth.

Android passkey setup uses app dependencies and hosted Digital Asset Links. No
OwnID-specific manifest permission, intent filter, activity, service, or
metadata entry is required for passkey association. The association is made by
the hosted Digital Asset Links file matching the app's package name and signing
certificate.

## Digital Asset Links

The relying-party domain must host:

```text
https://<relying-party-domain>/.well-known/assetlinks.json
```

Generate the `assetlinks.json` content from the values collected in intake and
give the developer the exact file content plus the required hosting path. Mark
hosting as external unless the repo or deployment system owns that host.

Minimum statement shape:

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

Add one statement for each app identity that uses passkeys:

- each final `applicationId`;
- each signing certificate fingerprint used for installed builds;
- each environment/flavor that points at the same relying-party domain.

The file must be public over HTTPS, return HTTP 200, use a JSON content type,
avoid redirects, and allow automated retrieval of
`/.well-known/assetlinks.json`. The package name and SHA-256 fingerprint must
match the installed APK exactly. A correct debug entry does not validate a
release build, and a release entry does not validate a debug or flavored build
with a different `applicationId`.

When multiple variants share the relying-party domain, use one JSON array with
one statement per final app identity. If a fingerprint is missing, ask for it
instead of leaving an ambiguous entry.

Treat domain hosting, signing certificates, Play signing, and OwnID Console
settings as explicit external work. If the app repository cannot own the hosted
`assetlinks.json`, report the required external change.

## Credential Manager Reality

OwnID passkey operations delegate creation and authentication to Android
Credential Manager. Treat these as platform outcomes:

- Devices below Android 9/API 28 cannot run platform passkeys.
- A device can be API 28+ and still fail availability because the credential
  service, Google Play services bridge, domain association, tenant config, or
  available credentials are not ready.
- Users can close the platform UI or have no applicable credentials.
- Availability diagnostics are for integration logs, not raw end-user copy.

For app-owned passkey actions, check availability before starting the action.
For high-level flows and Boost widgets, let the flow or widget perform operation
selection and availability handling. When availability fails, report the
passkey readiness gap in the integration work and handle the user-facing
outcome in the feature that invokes passkeys. This reference does not define
session handling or app authentication behavior.

## What To Verify Externally

For each app identity/environment that should support passkeys, verify:

- OwnID tenant passkey relying-party domain is known and matches the hosted
  `assetlinks.json` domain.
- `https://<relying-party-domain>/.well-known/assetlinks.json` is reachable as
  public HTTPS with HTTP 200, JSON content type, and no redirect.
- The host allows automated retrieval of `/.well-known/assetlinks.json`.
- The JSON includes `delegate_permission/common.get_login_creds` and
  `delegate_permission/common.handle_all_urls`.
- The JSON package name equals the final installed `applicationId`.
- The JSON SHA-256 fingerprint equals the certificate that signed the installed
  build.
- The generated `assetlinks.json` content has been handed off or committed only
  to the repository/location that owns the relying-party domain.
- The app has AndroidX Credentials dependencies in the app module that owns the
  OwnID SDK integration.
- The integration has an explicit handling plan for unavailable, canceled,
  no-credential, and error outcomes where passkeys are invoked.

DemoBase and DemoAdvanced provide working Credential Manager dependency and
variant-specific `applicationId`/signing patterns. Complete the corresponding
hosted `assetlinks.json` from the selected tenant's relying-party domain and
the app identities collected during intake.
