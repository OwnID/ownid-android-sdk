# SDK Configuration

Initialize OwnID once during app startup before using SDK features.

Pass any Android `Context` to the initialization builder; the SDK stores the application context.

## Configuration Values

[`appId`](../../sdk-core/core/src/main/java/com/ownid/sdk/OwnIdConfiguration.kt) is required. All other values are optional.

- `appId` (**required**): OwnID application ID from the OwnID Console.
- `env` (**optional**): OwnID environment. Defaults to `Prod`; use `Uat` for UAT tenants.
- `region` (**optional**): OwnID data-residency region. Defaults to `US`; use `EU` for EU tenants.
- `rootUrl` (**optional**): Custom HTTPS routing root provided by OwnID. Use it only when OwnID gives you a custom routing URL.
- `languages` (**optional**): Explicit SDK language list. Omit it to keep the current language mode; on fresh startup this means automatic app/device language tracking.

JSON and file configuration accept both platform casing variants, such as `appId`/`appID` and `rootUrl`/`rootURL`. `env` and `region` values are case-insensitive. Unknown JSON keys are ignored.

If configuration fails, the SDK logs the error and keeps the current runtime unchanged. If initialization later succeeds, reacquire namespace handles before starting more work.

## From Code

```kotlin
import android.app.Application
import com.ownid.sdk.OwnId

class App : Application() {
    override fun onCreate() {
        super.onCreate()

        OwnId.initialize {
            applicationContext = this@App.applicationContext
            appId = "<OWNID_APP_ID>"
        }
    }
}
```

Most apps only need the application ID. Add optional values only when OwnID provides them for your tenant:

```kotlin
import com.ownid.sdk.OwnIdConfiguration

OwnId.initialize {
    applicationContext = applicationContext
    appId = "<OWNID_APP_ID>"

    // Optional.
    env = OwnIdConfiguration.Env.Uat
    region = OwnIdConfiguration.Region.EU
    rootUrl = "https://auth.example.com"
}
```

## From JSON String

```kotlin
val ownIdConfigJson = """
{
  "appId": "<OWNID_APP_ID>",
  "env": "prod",
  "region": "us"
}
"""

OwnId.initializeFromJson {
    applicationContext = applicationContext
    json = ownIdConfigJson
}
```

## From File

`initializeFromFile` reads a JSON file from app assets. The default asset name is `OwnIdConfig.json`.

```json
{
  "appId": "<OWNID_APP_ID>",
  "env": "prod",
  "region": "us"
}
```

```kotlin
OwnId.initializeFromFile {
    applicationContext = applicationContext

    // Optional. The default asset name is OwnIdConfig.json.
    fileName = "OwnIdConfig.json"
}
```

## Language

By default, the SDK follows the app/device language list.

Set language during initialization only when the app should force OwnID text to a specific locale:

```kotlin
OwnId.initialize {
    applicationContext = applicationContext
    appId = "<OWNID_APP_ID>"
    languages = listOf("en-US")
}
```

You can also update language later:

```kotlin
OwnId.setLanguage(listOf("en-US", "fr-FR"))
```

Use BCP 47 language tags such as `en-US` or `fr-FR`. Pass an empty list to restore automatic app/device language tracking.

For UI text customization, see [Localization](../customization/localization.md).

## Logging

Logging is optional and disabled by default. Configure it before initialization when you need SDK logs.

```kotlin
import com.ownid.sdk.OwnId
import com.ownid.sdk.logger.OwnIdLogger

OwnId.logger {
    level = OwnIdLogger.Level.Warn
    tag = "OwnID"
}
```

Use a custom sink when SDK logs should go through your app logging pipeline:

```kotlin
import com.ownid.sdk.OwnId
import com.ownid.sdk.logger.OwnIdLogger

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

Use `Debug` or `Verbose` only for local development. Keep production logging at `Warn`, `Error`, or `Off` unless your support process requires more detail.

> [!WARNING]
> Do not log passwords, OwnID tokens, provider tokens, or session payloads.
