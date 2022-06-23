package com.ownid.sdk.internal

import android.content.Intent
import android.net.Uri
import androidx.test.ext.truth.content.IntentSubject
import com.google.common.truth.Truth
import com.ownid.sdk.InternalOwnIdAPI
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@OptIn(InternalOwnIdAPI::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
public class OwnIdRedirectActivityTest {
    @Test
    public fun testForwardsRedirectToOwnIdActivity() {
        val redirectUri = Uri.parse("com.ownid.sdk:/android")
        val redirectIntent = Intent()
        redirectIntent.data = redirectUri

        val redirectController: ActivityController<*> =
            Robolectric.buildActivity(OwnIdRedirectActivity::class.java, redirectIntent).create()

        val redirectActivity = redirectController.get() as OwnIdRedirectActivity
        val nextIntent = Shadows.shadowOf(redirectActivity).nextStartedActivity
        IntentSubject.assertThat(nextIntent).hasData(redirectUri)
        Truth.assertThat(redirectActivity.isFinishing).isTrue()
    }
}