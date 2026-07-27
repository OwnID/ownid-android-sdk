# Install

Use this reference only to add OwnID Android SDK v4 dependencies to a client
Android app.

## Contents

- [Artifacts](#artifacts)
- [Version And Compatibility](#version-and-compatibility)
- [Apply To The Host App](#apply-to-the-host-app)
- [Review Checks](#review-checks)

## Artifacts

OwnID Android SDK v4 uses Maven group `com.ownid.android-sdk`:

| App need | Add |
| --- | --- |
| Configuration, providers, Elite Flow, Passkey Enrollment, WebBridge, or passkey authentication APIs | `com.ownid.android-sdk:core:<version>` |
| Boost widgets, SDK-provided Compose operation UI, app-hosted Compose operation UI, themes, colors, or reusable Compose UI components | `com.ownid.android-sdk:compose:<version>` |

`compose` depends on `core`. Add `core` separately only when the app directly
imports Core APIs and the dependency convention requires explicit direct
dependencies, or when the app intentionally pins Core under an approved
version-management rule.

Provider helpers are maintained source-only integration files copied into the
app source set; they are not Maven artifacts.

## Version And Compatibility

For a new install, use the latest stable public OwnID SDK v4 release unless the
user, task, host policy, version catalog, lockfile, or existing integration pins
another version. Preserve an approved existing version unless the task requests
an upgrade. Verify the selected artifacts against Maven Central and the matching
public repository tag.

- Check the release's documented compatibility before changing dependencies.
- Keep `core` and `compose` on the same approved SDK release unless published
  metadata for that exact release says otherwise.

Compatibility gates to check before editing:

- minSdk 23 or higher.
- Java 11/Kotlin metadata consumption support.
- Core SDK v4 Kotlin compatibility from the selected release metadata.
- Compose SDK v4 requires a compatible host Compose setup; public docs state
  Compose 1.7.8+.

Keep minSdk, Kotlin, Compose, AGP, compile SDK, Java toolchain, repositories,
and locks on the host app's approved baselines. Confirm any required baseline
change as separate platform/dependency work.

## Apply To The Host App

Follow the host app's existing dependency style:

- version catalog: add or reuse a version key and library aliases;
- convention plugin/build logic: update the existing dependency owner;
- inline Gradle dependencies: keep the declaration in the module that uses
  OwnID;
- lockfiles or dependency verification: update only when the project requires
  it and the task allows lockfile edits.

Use `mavenCentral()` through the existing repository configuration for normal
public SDK installs.

Examples:

```kotlin
dependencies {
    implementation("com.ownid.android-sdk:core:<version>")
}
```

```kotlin
dependencies {
    implementation("com.ownid.android-sdk:compose:<version>")
}
```

## Review Checks

- The selected artifact matches the SDK surface the app uses.
- Version, repository, tag, and compatibility gates were checked for the chosen
  SDK release.
- Dependency declarations follow the host app's existing Gradle style.
- Dependency and platform changes stay within the approved integration scope.
