package com.ownid.sdk.internal.webflow

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.ResultReceiver
import com.google.common.truth.Truth
import com.ownid.sdk.FlowResult
import com.ownid.sdk.InstanceName
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.SessionAdapter
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.internal.feature.webflow.OwnIdFlowFeature
import com.ownid.sdk.internal.feature.webflow.OwnIdFlowFeatureImpl
import io.mockk.every
import io.mockk.mockk
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@OptIn(InternalOwnIdAPI::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
public class OwnIdNativeFlowFeatureImplTest {
    private lateinit var context: Context
    private lateinit var resultReceiver: ResultReceiver
    private lateinit var instanceName: InstanceName

    @Before
    public fun setUp() {
        context = RuntimeEnvironment.getApplication()
        resultReceiver = mockk(relaxed = true)
        instanceName = InstanceName("testInstance")
    }

    @Test
    public fun `createIntent - creates intent with correct flags and extras`() {
        val intent = OwnIdFlowFeature.createIntent(context, instanceName, resultReceiver)

        Truth.assertThat(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isEqualTo(Intent.FLAG_ACTIVITY_NEW_TASK)
        Truth.assertThat(intent.getBooleanExtra(OwnIdFlowFeatureImpl.KEY_FLOW_INTENT, false)).isTrue()
        Truth.assertThat(intent.getStringExtra(OwnIdFlowFeatureImpl.KEY_INSTANCE_NAME)).isEqualTo(instanceName.toString())
        Truth.assertThat(intent.getParcelableExtra<ResultReceiver>(OwnIdFlowFeatureImpl.KEY_RESULT_RECEIVER)).isNotEqualTo(null)
    }

    @Test
    public fun `isThisFeature - returns true for intent with correct extra`() {
        val intent = Intent().putExtra(OwnIdFlowFeatureImpl.KEY_FLOW_INTENT, true)

        Truth.assertThat(OwnIdFlowFeature.isThisFeature(intent)).isTrue()
    }

    @Test
    public fun `isThisFeature - returns false for intent without extra`() {
        val intent = Intent()

        Truth.assertThat(OwnIdFlowFeature.isThisFeature(intent)).isFalse()
    }

    @Test
    public fun `sendCloseRequest - sends broadcast with correct action and package`() {
        val shadowApplication = Shadows.shadowOf(RuntimeEnvironment.getApplication())
        OwnIdFlowFeature.sendCloseRequest(context)

        val broadcastIntent = shadowApplication.broadcastIntents[0]
        Truth.assertThat(broadcastIntent.action).isEqualTo(OwnIdFlowFeatureImpl.KEY_BROADCAST_CLOSE_REQUEST)
        Truth.assertThat(broadcastIntent.`package`).isEqualTo(context.packageName)
    }

    @Test
    public fun `decodeResult- decodes OnAccountNotFound result correctly`() {
        val resultData = Bundle().apply {
            putSerializable(OwnIdFlowFeatureImpl.KEY_RESPONSE_RESULT_TYPE, OwnIdFlowFeature.Result.Type.ON_ACCOUNT_NOT_FOUND)
            putString(
                OwnIdFlowFeatureImpl.KEY_RESPONSE_RESULT,
                """{"loginId":"test@example.com","authToken":"someToken","ownIdData":"someData"}"""
            )
        }
        val adapter = mockk<SessionAdapter<Any>>(relaxed = true)

        val result = OwnIdFlowFeature.decodeResult<Any>(Activity.RESULT_OK, resultData, adapter)

        Truth.assertThat(result).isInstanceOf(FlowResult.OnAccountNotFound::class.java)
        val onAccountNotFoundResult = result as FlowResult.OnAccountNotFound
        Truth.assertThat(onAccountNotFoundResult.loginId).isEqualTo("test@example.com")
        Truth.assertThat(onAccountNotFoundResult.authToken).isEqualTo("someToken")
        Truth.assertThat(onAccountNotFoundResult.ownIdData).isEqualTo("someData")
    }

    @Test
    public fun `decodeResult - decodes OnLogin result correctly`() {
        val resultData = Bundle().apply {
            putSerializable(OwnIdFlowFeatureImpl.KEY_RESPONSE_RESULT_TYPE, OwnIdFlowFeature.Result.Type.ON_LOGIN)
            putString(
                OwnIdFlowFeatureImpl.KEY_RESPONSE_RESULT,
                """{"session":"someSessionData","metadata":{"loginId":"test@example.com","authToken":"someToken"}}"""
            )
        }
        val adapter = mockk<SessionAdapter<String>> {
            every { transformOrThrow(any()) } returns "transformedSession"
        }

        val result = OwnIdFlowFeature.decodeResult<String>(Activity.RESULT_OK, resultData, adapter)

        Truth.assertThat(result).isInstanceOf(FlowResult.OnLogin::class.java)
        val onLoginResult = result as FlowResult.OnLogin
        Truth.assertThat(onLoginResult.session).isEqualTo("transformedSession")
        Truth.assertThat(onLoginResult.loginId).isEqualTo("test@example.com")
        Truth.assertThat(onLoginResult.authToken).isEqualTo("someToken")
    }

    @Test
    public fun `decodeResult - decodes OnClose result correctly`() {
        val resultData = Bundle().apply {
            putSerializable(OwnIdFlowFeatureImpl.KEY_RESPONSE_RESULT_TYPE, OwnIdFlowFeature.Result.Type.ON_CLOSE)
        }
        val adapter = mockk<SessionAdapter<Any>>(relaxed = true)

        val result = OwnIdFlowFeature.decodeResult<Any>(Activity.RESULT_OK, resultData, adapter)

        Truth.assertThat(result).isEqualTo(FlowResult.OnClose)
    }

    @Test
    public fun `decodeResult - decodes OnError result correctly`() {
        val resultData = Bundle().apply {
            putSerializable(OwnIdFlowFeatureImpl.KEY_RESPONSE_RESULT_TYPE, OwnIdFlowFeature.Result.Type.ON_ERROR)
            putString(OwnIdFlowFeatureImpl.KEY_RESPONSE_RESULT, "Some error message")
        }
        val adapter = mockk<SessionAdapter<Any>>(relaxed = true)

        val result = OwnIdFlowFeature.decodeResult<Any>(Activity.RESULT_OK, resultData, adapter)

        Truth.assertThat(result).isInstanceOf(FlowResult.OnError::class.java)
        Truth.assertThat((result as FlowResult.OnError).cause.message).isEqualTo("Some error message")
    }

    @Test
    public fun `decodeResult - handles error with result code Int_MAX_VALUE`() {
        val exception = OwnIdException("Test exception")
        val resultData = Bundle().apply {
            putSerializable(OwnIdFlowFeatureImpl.KEY_RESPONSE_RESULT, exception)
        }
        val adapter = mockk<SessionAdapter<Any>>(relaxed = true)

        val result = OwnIdFlowFeature.decodeResult<Any>(Int.MAX_VALUE, resultData, adapter)

        Truth.assertThat(result).isInstanceOf(FlowResult.OnError::class.java)
        Truth.assertThat((result as FlowResult.OnError).cause).isEqualTo(exception)
    }

    @Test
    public fun `decodeResult - handles error when result type is missing`() {
        val resultData = Bundle()
        val adapter = mockk<SessionAdapter<Any>>(relaxed = true)

        val result = OwnIdFlowFeature.decodeResult<Any>(Activity.RESULT_OK, resultData, adapter)

        Truth.assertThat(result).isInstanceOf(FlowResult.OnError::class.java)
        Truth.assertThat((result as FlowResult.OnError).cause.message).contains("Error decoding result")
    }
}