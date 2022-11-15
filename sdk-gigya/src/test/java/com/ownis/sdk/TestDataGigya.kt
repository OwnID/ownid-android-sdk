package com.ownis.sdk

import android.net.Uri
import com.ownid.sdk.Configuration
import com.ownid.sdk.InstanceName
import com.ownid.sdk.internal.OwnIdFlowInfo
import com.ownid.sdk.internal.OwnIdPayload
import com.ownid.sdk.internal.OwnIdResponse
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.io.File

internal object TestDataGigya {
    internal const val validVersion: String = "OwnID-Firebase/0.4.0 OwnID-Core/0.4.0"
    internal const val validUserAgent: String =
        "OwnID-Firebase/0.4.0 (Linux; Android 12; Pixel 4a (5G)) com.ownid.demo.firebase.dev"
    internal val validServerUrl: HttpUrl = "https://firebase.single.demo.dev.ownid.com".toHttpUrl()
    internal val validRedirectionUri: Uri = Uri.parse("com.ownid.demo:/android")

    internal const val validEmail: String = "email@test.com"

    internal val validJsonConfig: String = """{
  "server_url": "https://firebase.single.demo.dev.ownid.com",
  "redirection_uri": "com.ownid.demo:/android"
}"""
    internal val validLocaleUri: HttpUrl = "https://i18n.ownid.com".toHttpUrl()
    internal val validCacheDir: File = File("./build/tmp/cache_test")

    internal val validServerConfig: Configuration = Configuration(
        validVersion,
        validUserAgent,
        validServerUrl,
        validRedirectionUri,
        validLocaleUri,
        validCacheDir
    )

    internal val validInstanceName = InstanceName("TestInstance")
    internal const val validName = "SomeUserName"
    val validProfileParams = mutableMapOf<String, Any>().apply {
        this["profile"] = JSONObject().put("firstName", validName).toString()
    }

    val validProfileParamsWithLocales = mutableMapOf<String, Any>().apply {
        this["profile"] = JSONObject()
            .put("firstName", validName)
            .put("locale", "ru")
            .toString()
    }

    val validDataParams = mutableMapOf<String, Any>().apply {
        this["data"] = JSONObject().put("firstName", validName).toString()
    }

    internal val validRegistrationPayload = OwnIdPayload(
        OwnIdPayload.Type.Registration,
        "{\"connections\": [{\"id\": \"AcfwyOjWW8eC1\",\"source\": \"register\"}]}",
        "{ \"dataField\": \"ownId\"}"
    )

    internal val validLoginPayload = OwnIdPayload(
        OwnIdPayload.Type.Login,
        "{\n" +
                "      \"sessionInfo\": {\n" +
                "        \"sessionToken\": \"st2.s.AcbHqtC03.sc3\",\n" +
                "        \"sessionSecret\": \"U3bBUs5/DiG/v\\u002BW/EVaywsMxi8g=\",\n" +
                "        \"expires_in\": \"0\"\n" +
                "      },\n" +
                "      \"uid\": \"7d7897ded8d74d8d9b9b8b5688206204\"\n" +
                "    }",
        ""
    )

    internal val validRegistrationResponseNoEmail = OwnIdResponse(
        context = "TfPoXfcYbk6j_SrUBGhdMA-Q",
        loginId = "",
        payload = validRegistrationPayload,
        flowInfo = OwnIdFlowInfo(OwnIdFlowInfo.Event.Register, "mobile-biometric"),
        languageTags = "en-US,uk-UA,ru-UA"
    )

    internal val validRegistrationFidoOwnIdResponse = OwnIdResponse(
        context = "TfPoXfcYbk6j_SrUBGhdMA-Q",
        loginId = validEmail,
        payload = validRegistrationPayload,
        flowInfo = OwnIdFlowInfo(OwnIdFlowInfo.Event.Register, "mobile-biometric"),
        languageTags = "en-US,uk-UA,ru-UA"
    )

    internal val validLoginOwnIdResponse = OwnIdResponse(
        context = "3gAY1aXiYUClaN59E1C8-Q",
        loginId = "",
        payload = validLoginPayload,
        flowInfo = OwnIdFlowInfo(OwnIdFlowInfo.Event.Login, "mobile-biometric"),
        languageTags = "en"
    )

    internal val validLoginValidationPendingOwnIdResponse = OwnIdResponse(
        context = "3gAY1aXiYUClaN59E1C8-Q",
        loginId = "",
        payload = OwnIdPayload(
            OwnIdPayload.Type.Login,
            """{
      "errorJson": "{\r\n  \u0022callId\u0022: \u002262db81f8c8a1493e98ece0458eec19a5\u0022,\r\n  \u0022errorCode\u0022: 206002,\r\n  \u0022errorDetails\u0022: \u0022Account Pending Verification\u0022,\r\n  \u0022errorMessage\u0022: \u0022Account Pending Verification\u0022,\r\n  \u0022apiVersion\u0022: 2,\r\n  \u0022statusCode\u0022: 206,\r\n  \u0022statusReason\u0022: \u0022Partial Content\u0022,\r\n  \u0022time\u0022: \u00222022-04-01T07:58:11.630Z\u0022,\r\n  \u0022registeredTimestamp\u0022: 1648727443,\r\n  \u0022UID\u0022: \u00223b2e5837af3b45e6915a4b8749ad5356\u0022,\r\n  \u0022created\u0022: \u00222022-03-31T11:50:43.446Z\u0022,\r\n  \u0022createdTimestamp\u0022: 1648727443,\r\n  \u0022identities\u0022: [\r\n    {\r\n      \u0022provider\u0022: \u0022site\u0022,\r\n      \u0022providerUID\u0022: \u00223b2e5837af3b45e6915a4b8749ad5356\u0022,\r\n      \u0022allowsLogin\u0022: true,\r\n      \u0022isLoginIdentity\u0022: true,\r\n      \u0022isExpiredSession\u0022: false,\r\n      \u0022lastUpdated\u0022: \u00222022-04-01T07:58:11.5771562Z\u0022,\r\n      \u0022lastUpdatedTimestamp\u0022: 1648799891577,\r\n      \u0022oldestDataUpdated\u0022: \u00222022-03-31T11:50:43.446Z\u0022,\r\n      \u0022oldestDataUpdatedTimestamp\u0022: 1648727443446,\r\n      \u0022firstName\u0022: \u0022jfjrj\u0022,\r\n      \u0022nickname\u0022: \u0022jfjrj\u0022,\r\n      \u0022email\u0022: \u0022bfjdj@jdjjf.fff\u0022\r\n    }\r\n  ],\r\n  \u0022isActive\u0022: true,\r\n  \u0022isRegistered\u0022: true,\r\n  \u0022isVerified\u0022: false,\r\n  \u0022lastLogin\u0022: \u00222022-04-01T07:58:11.577Z\u0022,\r\n  \u0022lastLoginTimestamp\u0022: 1648799891,\r\n  \u0022lastUpdated\u0022: \u00222022-04-01T07:58:11.320Z\u0022,\r\n  \u0022lastUpdatedTimestamp\u0022: 1648799891320,\r\n  \u0022loginProvider\u0022: \u0022site\u0022,\r\n  \u0022oldestDataUpdated\u0022: \u00222022-03-31T11:50:43.446Z\u0022,\r\n  \u0022oldestDataUpdatedTimestamp\u0022: 1648727443446,\r\n  \u0022profile\u0022: {\r\n    \u0022firstName\u0022: \u0022jfjrj\u0022,\r\n    \u0022email\u0022: \u0022bfjdj@jdjjf.fff\u0022\r\n  },\r\n  \u0022registered\u0022: \u00222022-03-31T11:50:43.632Z\u0022,\r\n  \u0022socialProviders\u0022: \u0022site\u0022,\r\n  \u0022newUser\u0022: false,\r\n  \u0022id_token\u0022: \u0022eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsImtpZCI6IlJFUTBNVVE1TjBOQ1JUSkVNemszTTBVMVJrTkRRMFUwUTBNMVJFRkJSamhETWpkRU5VRkJRZyIsImRjIjoidXMxIn0.eyJpc3MiOiJodHRwczovL2ZpZG0uZ2lneWEuY29tL2p3dC80X0hZVVlwM043SVNPVnVFd0p1UzVZSWcvIiwic3ViIjoiM2IyZTU4MzdhZjNiNDVlNjkxNWE0Yjg3NDlhZDUzNTYiLCJpYXQiOjE2NDg3OTk4OTEsImV4cCI6MTY0ODc5OTk1MSwiaXNMb2dnZWRJbiI6ZmFsc2V9.OO12XpmCGF-CtypX85Rr3SsnYHpoYouF6qehkS2TRzVQJZ-mrR8LzwOIY_vr0nT9KHUe-09w4B7ofRZEPq9-BFBdnlEb-J7spPyFsRmLQrVqv_Pr1h6WOmiRyCB6wT-nAkwLKsIc-Ov_rA76zonWxxMsEpSaGJ1hEnIxxU8z1QFCWPIk2ZRDAMBUI_JC1XbVJf8xOQh2O04Mmwq4FdTTnxfz1yD6Yif36hSI1WgNesUIvdSCHhHgThS1hQx3Mh0w_EkzgSnqCcE7_FmlPOqoky6BHdFU3XTCff7IM78ozfUszjq16VGMZ23GrLj8M_CJMofp0Soe9Ld3w1gz0n8Qwg\u0022,\r\n  \u0022regToken\u0022: \u0022st2.s.AcbHr7oFgQ.sYArI-ur6v2P5XU-RPLWftiH4QT3NxXhMZQ78gZCtDSILqq6eguxvdk0OqW6E9D7-3ETocYHBtnvhTW-rOTmP4PMNaK0q0h1QS6uOtjRdkI.3IWzB_THlYrTp5xbIGPLZqrl5tNZSsc2mV-twS1eNXalftQGDczjmvUYDnZrTcP2bjyRmJt4sD_P_fxSUzalnQ.sc3\u0022\r\n}",
      "errorMessage": "Account Pending Verification"
    }""", ""
        ),
        flowInfo = OwnIdFlowInfo(OwnIdFlowInfo.Event.Login, "mobile-biometric"),
        languageTags = "en"
    )
}
