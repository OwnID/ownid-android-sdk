# Configuration

Use this reference to initialize OwnID Android SDK v4 during client app startup.
It covers startup placement, configuration sources, app ID, environment, region,
root URL, language, and logging.

Primary public docs: `../../../../docs/setup/configuration.md`.

## Contents

- [Evidence](#evidence)
- [Integration Task Boundary](#integration-task-boundary)
- [Startup Placement](#startup-placement)
- [Choose One Configuration Source](#choose-one-configuration-source)
- [Configuration Values](#configuration-values)
- [Environment And Tenant Setup](#environment-and-tenant-setup)
- [Language](#language)
- [Logging](#logging)
- [Minimal Review Checklist](#minimal-review-checklist)

## Evidence

- Exact API and lifecycle contracts: `OwnId`, `OwnIdConfiguration`,
  `OwnIdConfigurationBuilder`, `OwnIdJsonConfigurationBuilder`,
  `OwnIdFileConfigurationBuilder`, `OwnIdLogger`, and
  `LanguageTagsProvider`.
- Flow and terminology docs: `../../../../README.md`,
  `../../../../docs/README.md`, and
  `../../../../docs/setup/configuration.md`.
- Working startup/provider composition:
  `../../../../demoBase/app/src/main/java/com/ownid/demo/DemoBaseApp.kt`
  and
  `../../../../demoAdvanced/app/src/custom/java/com/ownid/demo/DemoAdvancedApp.kt`.

Use setup docs for configuration flows and terminology, production API/source
for exact builders and behavior, and DemoBase/DemoAdvanced for working startup
placement and JSON configuration patterns. Reconcile them with package
metadata for the selected release.

## Integration Task Boundary

Configuration work establishes:

- Where in app startup should `OwnId` be initialized?
- Which SDK initialization source should the app use?
- Where do the OwnID application ID and environment-specific values come from?
- Which values are required or optional?
- Should language or logging be configured?

## Startup Placement

Initialize OwnID once during normal app startup, before using any SDK
functionality. In an Android app this is usually `Application.onCreate()` or
the app's existing startup/DI module that runs from `Application.onCreate()`.

Rules:

- Use the app's existing configuration source and dependency-injection pattern.
- Pass any Android `Context`; prefer the app/application context at startup.
  The SDK stores `Context.getApplicationContext`.
- Configure `OwnId.logger { ... }` before initialization when startup or config
  diagnostics are needed.

DemoBase and DemoAdvanced both initialize from JSON in app startup. Use those
demos as JSON startup examples.

## Choose One Configuration Source

All SDK initialization sources create the same SDK configuration. Choose the
source that matches the host app's existing environment-management model.

Use programmatic configuration when values already live in Gradle build config,
flavor config, dependency injection, or the app's startup configuration object:

```kotlin
import com.ownid.sdk.OwnId

OwnId.initialize {
    applicationContext = appContext.applicationContext
    appId = "<OWNID_APP_ID>"
}
```

Use JSON when the app already stores, receives, or generates OwnID configuration
as a JSON string:

```kotlin
OwnId.initializeFromJson {
    applicationContext = appContext.applicationContext
    json = ownIdConfigJson
}
```

Use file configuration when OwnID config is bundled as an Android asset. The
default asset name is `OwnIdConfig.json`:

```kotlin
OwnId.initializeFromFile {
    applicationContext = appContext.applicationContext
    // Optional; defaults to OwnIdConfig.json.
    fileName = "OwnIdConfig.json"
}
```

Use one configuration source for the app startup path.

## Configuration Values

`appId` is required. All other SDK configuration values are optional.

- `appId`: OwnID application ID from the OwnID Console. It must be non-empty and
  alphanumeric. JSON accepts `appId` or `appID`.
- `env`: OwnID environment. Defaults to `OwnIdConfiguration.Env.Prod`; use
  `OwnIdConfiguration.Env.Uat` only for UAT tenants. JSON values are decoded
  case-insensitively, for example `"prod"` or `"uat"`.
- `region`: OwnID data-residency region. Defaults to
  `OwnIdConfiguration.Region.US`; use `OwnIdConfiguration.Region.EU` only for
  EU tenants. JSON values are decoded case-insensitively.
- `rootUrl`: Optional custom HTTPS OwnID server-routing root supplied by OwnID.
  JSON and file configuration accept `rootUrl` or `rootURL`; programmatic
  configuration uses `rootUrl`. The SDK strips query and fragment values and
  appends its own paths. Configure the root as the HTTPS routing base, without
  SDK-owned path segments.
- `languages`: Optional explicit language-code list with optional regions, such
  as `en` or `en-US`. Omit it to keep the current language mode; on fresh
  startup this means automatic app/device language tracking.

Unknown JSON keys are ignored. Invalid configuration is logged and leaves the
current SDK runtime unchanged.

## Environment And Tenant Setup

Treat environment selection as tenant configuration, not business logic:

- Get `appId`, `env`, `region`, and any `rootUrl` from the OwnID tenant setup or
  approved app configuration.
- Keep production and UAT values separated using the app's existing
  flavor/build-type/configuration system.
- Prefer the app's existing non-source configuration path for private tenant
  values.
- Use the host app's existing secure remote-config startup path when OwnID
  config is fetched remotely; define failure behavior before changing startup.

OwnID configuration contains tenant and startup values. Passwords, access
tokens, provider tokens, and session payloads remain in their app-owned secure
runtime paths rather than configuration files, build fields, logs, or
comments.

## Language

By default, OwnID follows the app/device language list. Android locale changes
are observed while the SDK remains in automatic mode.

Use a non-empty `languages` list during initialization only when the app must
force OwnID UI text to a specific language list:

```kotlin
OwnId.initialize {
    applicationContext = appContext.applicationContext
    appId = "<OWNID_APP_ID>"
    languages = listOf("en-US")
}
```

Use `OwnId.setLanguage(...)` for a later language switch:

```kotlin
OwnId.setLanguage(listOf("en-US", "fr-FR"))
```

Pass an empty list to return to automatic app/device language tracking:

```kotlin
OwnId.setLanguage(emptyList())
```

Rules:

- Use language codes with optional regions, such as `en` or `en-US`.
- Passing a non-empty `languages` list during initialization or calling
  `OwnId.setLanguage(...)` with a non-empty list switches the process from
  automatic app/device language tracking to the explicit list.
- Omitting `languages` during initialization does not clear an existing explicit
  language override.
- Passing an empty list keeps or restores automatic app/device language
  tracking.
- Calling `OwnId.setLanguage(...)` before successful initialization is a no-op.

## Logging

SDK-wide logging is optional and is not installed by default. Configure logging
before initialization when startup or configuration diagnostics are needed.

```kotlin
import com.ownid.sdk.OwnId
import com.ownid.sdk.logger.OwnIdLogger

OwnId.logger {
    level = OwnIdLogger.Level.Warn
    tag = "OwnID"
}
```

Use a custom sink only when the app already has a logging pipeline:

```kotlin
OwnId.logger {
    level = OwnIdLogger.Level.Debug
    tag = "OwnID"
    log { level, className, message, cause ->
        appLogger.log(
            level = level.name,
            tag = "OwnID",
            message = "$className: $message",
            throwable = cause
        )
    }
}
```

Rules:

- The most recent `OwnId.logger { ... }` call replaces the previous logger.
- A custom sink runs synchronously on the emitting thread and may be called from any thread. It must be thread-safe and
  return promptly; offload blocking work to app-owned background execution.
- Exceptions from a custom sink are reported to Android Log and are not
  rethrown.
- Use `Debug` or `Verbose` only for a local diagnostic run or a time-boxed
  diagnostic task.
- Keep production logging at `Warn`, `Error`, or `Off` unless the app's support
  process explicitly requires more detail.
- Keep passwords, OwnID access tokens, provider tokens, session payloads, full
  auth responses, and personally identifying login IDs out of logs.

## Minimal Review Checklist

Before finishing configuration work in a host app, verify by inspection:

- OwnID is initialized before any SDK use.
- The startup location is owned by the app's existing initialization path.
- Android passes `applicationContext`.
- Exactly one configuration source is used for the app startup path.
- `appId`, `env`, `region`, and `rootUrl` come from an approved tenant/config
  source.
- Language mode matches the app's intent: automatic tracking, explicit override,
  or reset to automatic.
- Logging level and sink match the app's environment and privacy policy.
