package com.ownid.sdk.internal

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.truth.content.IntentSubject
import com.google.common.truth.Truth
import com.ownid.sdk.Configuration
import com.ownid.sdk.InstanceName
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnId
import com.ownid.sdk.OwnIdCallback
import com.ownid.sdk.RegistrationParameters
import com.ownid.sdk.TestDataCore
import com.ownid.sdk.internal.OwnIdRequest.Companion.toBase64UrlSafeNoPadding
import io.mockk.every
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric.buildActivity
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

@OptIn(InternalOwnIdAPI::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
public class OwnIdActivityTest {

    private class OwnIdCore(instanceName: InstanceName, configuration: Configuration) :
        OwnIdCoreImpl(instanceName, configuration) {
        override fun register(
            email: String, params: RegistrationParameters?, ownIdResponse: OwnIdResponse, callback: OwnIdCallback<Unit>
        ) {

        }

        override fun login(ownIdResponse: OwnIdResponse, callback: OwnIdCallback<Unit>) {
        }
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val ownIdCore: OwnIdCore = OwnIdCore(TestDataCore.validInstanceName, TestDataCore.validServerConfig)

    @Before
    public fun prepare() {
        OwnId.putInstance(ownIdCore)
    }

    @Test
    public fun createRedirectIntent() {
        val responseUri = Uri.parse("com.ownid.demo://android")

        val redirectIntent = OwnIdActivity.createRedirectIntent(context, responseUri)

        IntentSubject.assertThat(redirectIntent)
            .hasComponent("com.ownid.sdk.test", OwnIdActivity::class.java.name)
        IntentSubject.assertThat(redirectIntent).hasData(responseUri)
        Assert.assertEquals(
            Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
            redirectIntent.flags
        )
    }

    @Test
    public fun onCreate() {
        val request =
            OwnIdRequest(ownIdCore, OwnIdRequest.Type.LOGIN, TestDataCore.validLanguage, TestDataCore.validEmail)
        val loginIntent =
            OwnIdActivity.createBaseIntent(context).putExtra(OwnIdActivity.KEY_REQUEST, request.toJsonString())

        val activityController = buildActivity(OwnIdActivity::class.java, loginIntent)
        activityController.create(null)

        val ownIdActivity = activityController.get() as OwnIdActivity

        Truth.assertThat(ownIdActivity.ownIdRequest).isEqualTo(request)
    }

    @Test
    public fun onResumeNotStarted() {
        val sessionVerifier = Random.nextBytes(32).toBase64UrlSafeNoPadding
        val request = OwnIdRequest(
            ownIdCore, OwnIdRequest.Type.LOGIN, TestDataCore.validLanguage, TestDataCore.validEmail,
            sessionVerifier = sessionVerifier
        )

        val loginIntent =
            OwnIdActivity.createBaseIntent(context).putExtra(OwnIdActivity.KEY_REQUEST, request.toJsonString())

        val activityController = buildActivity(OwnIdActivity::class.java, loginIntent)
        activityController.create(null)
        val ownIdActivity = activityController.get() as OwnIdActivity
        val ownIdRequestMockk = spyk(ownIdActivity.ownIdRequest, recordPrivateCalls = true)
        ownIdActivity.ownIdRequest = ownIdRequestMockk

        val slotOwnIdCallback = slot<OwnIdCallback<OwnIdRequest>>()
        every { ownIdRequestMockk.initRequest(capture(slotOwnIdCallback)) } answers {
            slotOwnIdCallback.captured.invoke(Result.success(ownIdRequestMockk))
        }

        activityController.resume()

        verify {
            ownIdRequestMockk.isRequestStarted()
            ownIdRequestMockk.initRequest(any())
            ownIdRequestMockk.getUriForBrowser()
        }
    }
}