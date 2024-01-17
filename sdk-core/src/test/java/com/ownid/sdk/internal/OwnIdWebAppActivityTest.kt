package com.ownid.sdk.internal

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.truth.content.IntentSubject
import com.google.common.truth.Truth
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.internal.flow.steps.webapp.BrowserHelper
import com.ownid.sdk.internal.flow.steps.webapp.OwnIdWebAppActivity
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.slot
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric.buildActivity
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.*

@OptIn(InternalOwnIdAPI::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
public class OwnIdWebAppActivityTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val webAppUri =
        """https://passwordless.dev.ownid.com/sign?q=https%3a%2f%2fgephu5k2dnff2v.server.dev.ownid.com%2fownid%2f00J5rGLhYU2-SNaX80RWQQ%2fstart&ll=3&l=en-US&redirectURI=com.ownid.demo.gigya%3A%2F%2Fownid%2Fredirect%2F%3Fcontext%3D00J5rGLhYU2-SNaX80RWQQ"""
    private val redirectURI = """com.ownid.demo.gigya://ownid/redirect/?context=00J5rGLhYU2-SNaX80RWQQ"""

    @Test
    public fun createRedirectIntent() {
        val responseUri = Uri.parse("com.ownid.demo://android")

        val redirectIntent = OwnIdWebAppActivity.createRedirectIntent(context, responseUri)

        IntentSubject.assertThat(redirectIntent).hasComponent("com.ownid.sdk.test", OwnIdWebAppActivity::class.java.name)
        IntentSubject.assertThat(redirectIntent).hasData(responseUri)
        Assert.assertEquals(
            Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION,
            redirectIntent.flags
        )
    }

    @Test
    public fun onCreate() {
        val loginIntent = OwnIdWebAppActivity.createIntent(context, webAppUri)

        val activityController = buildActivity(OwnIdWebAppActivity::class.java, loginIntent)
        activityController.create(null)

        val ownIdWebAppActivity = activityController.get() as OwnIdWebAppActivity

        Truth.assertThat(ownIdWebAppActivity.isWebAppLaunched).isFalse()
        Truth.assertThat(ownIdWebAppActivity.webAppUri).isEqualTo(webAppUri)
    }

    @Test
    public fun onResumeNotStarted() {
        mockkObject(BrowserHelper)
        val slotUri = slot<String>()
        every { BrowserHelper.launchUri(any(), capture(slotUri)) } returns Unit

        val loginIntent = OwnIdWebAppActivity.createIntent(context, webAppUri)

        val activityController = buildActivity(OwnIdWebAppActivity::class.java, loginIntent)
        activityController.create(null)

        val ownIdWebAppActivity = activityController.get() as OwnIdWebAppActivity

        activityController.resume()

        Truth.assertThat(ownIdWebAppActivity.isWebAppLaunched).isTrue()
        Truth.assertThat(slotUri.captured).isEqualTo(webAppUri)
    }

//    @Test
//    public fun onResumeStarted() {
//        val loginIntent = OwnIdWebAppActivity.createIntent(context, webAppUri)
//        val activityController = buildActivity(OwnIdWebAppActivity::class.java, loginIntent)
//        activityController.create()
//
//        val ownIdWebAppActivity = activityController.get()
//
//        val ownIdWebAppActivitySpyk = spyk(ownIdWebAppActivity, recordPrivateCalls = true)
//        ownIdWebAppActivitySpyk.intent = Intent().setData(Uri.parse(redirectURI))
//        ownIdWebAppActivitySpyk.isWebAppLaunched = true
//
//        val slotResult = slot<Result<String>>()
//        every { ownIdWebAppActivitySpyk["sendResult"](capture(slotResult)) } returns Unit
//
//        activityController.resume()
//
//        verify(exactly = 1) {
//            ownIdWebAppActivitySpyk["sendResult"](any<Result<String?>>())
//        }
//
//        Truth.assertThat(slotResult.captured.isSuccess).isTrue()
//        Truth.assertThat(slotResult.captured.getOrNull()).isEqualTo(redirectURI)
//    }
}