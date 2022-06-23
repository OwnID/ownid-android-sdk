package com.ownis.sdk.internal

import android.annotation.SuppressLint
import android.os.Looper.getMainLooper
import com.gigya.android.sdk.Gigya
import com.gigya.android.sdk.GigyaLoginCallback
import com.gigya.android.sdk.account.models.GigyaAccount
import com.gigya.android.sdk.network.GigyaError
import com.gigya.android.sdk.session.SessionInfo
import com.google.common.truth.Truth
import com.ownid.sdk.GigyaRegistrationParameters
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.exception.GigyaException
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.internal.OwnIdGigyaImpl
import com.ownis.sdk.TestDataGigya
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@SuppressLint("CheckResult")
@OptIn(InternalOwnIdAPI::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
public class OwnIdGigyaImplTest {

    private val gigyaMockk = mockk<Gigya<GigyaAccount>>()
    private val accountMockk = mockk<GigyaAccount>()
    private val ownIdGigya = spyk(
        OwnIdGigyaImpl(
            TestDataGigya.validInstanceName,
            TestDataGigya.validServerConfig,
            gigyaMockk
        ),
        recordPrivateCalls = true
    )

    private var callbackResult: Result<Unit>? = null
    private val callback = object : (Result<Unit>) -> Unit {
        override fun invoke(result: Result<Unit>) {
            callbackResult = result
        }
    }

    @Test
    @LooperMode(LooperMode.Mode.PAUSED)
    public fun registerWithEmailSuccess() {
        val slotLoginCallback = slot<GigyaLoginCallback<GigyaAccount>>()
        val slotParams = slot<Map<String, Any>>()
        every { gigyaMockk.register(any(), any(), capture(slotParams), capture(slotLoginCallback)) } returns Unit

        ownIdGigya.register(
            TestDataGigya.validEmail,
            GigyaRegistrationParameters(emptyMap()),
            TestDataGigya.validRegistrationResponseNoEmail,
            callback
        )
        slotLoginCallback.captured.onSuccess(accountMockk)

        shadowOf(getMainLooper()).idle()

        Truth.assertThat(verify(exactly = 1) {
            ownIdGigya["generatePassword"](any<Int>(), any<Int>(), any<Int>(), any<Int>())
            gigyaMockk.register(TestDataGigya.validEmail, any(), any(), any())
        })

        Truth.assertThat(verifyOrder {
            ownIdGigya["generatePassword"](any<Int>(), any<Int>(), any<Int>(), any<Int>())
            gigyaMockk.register(TestDataGigya.validEmail, any(), any(), any())
        })

        Truth.assertThat(slotParams.captured.toString()).isEqualTo(
            "{data={\"ownId\":{\"connections\":[{\"id\":\"AcfwyOjWW8eC1\",\"source\":\"register\"}]}}}"
        )
    }

    @Test
    @LooperMode(LooperMode.Mode.PAUSED)
    public fun registerOneEmailAndNoEmailInResponseFail() {
        val slotLoginCallback = slot<GigyaLoginCallback<GigyaAccount>>()
        val slotParams = slot<Map<String, Any>>()
        every { gigyaMockk.register(any(), any(), capture(slotParams), capture(slotLoginCallback)) } returns Unit


        ownIdGigya.register(
            TestDataGigya.validEmail,
            GigyaRegistrationParameters(emptyMap()),
            TestDataGigya.validRegistrationResponseNoEmail,
            callback
        )
        val gigyaError = GigyaError(10, "TestError", "TestError")
        slotLoginCallback.captured.onError(gigyaError)

        shadowOf(getMainLooper()).idle()

        Truth.assertThat(verify(exactly = 1) {
            ownIdGigya["generatePassword"](any<Int>(), any<Int>(), any<Int>(), any<Int>())
            gigyaMockk.register(TestDataGigya.validEmail, any(), any(), any())
        })

        Truth.assertThat(verifyOrder {
            ownIdGigya["generatePassword"](any<Int>(), any<Int>(), any<Int>(), any<Int>())
            gigyaMockk.register(TestDataGigya.validEmail, any(), any(), any())
        })

        Truth.assertThat(callbackResult!!.isFailure).isTrue()
        val exception = callbackResult!!.exceptionOrNull()!!
        Truth.assertThat(exception::class.java).isEqualTo(GigyaException::class.java)
        Truth.assertThat((exception as GigyaException).gigyaError).isEqualTo(gigyaError)
    }

    @Test
    @LooperMode(LooperMode.Mode.PAUSED)
    public fun registerEmailWithNameMapOneEmailAndNoEmailInResponse() {
        val slotLoginCallback = slot<GigyaLoginCallback<GigyaAccount>>()
        val slotParams = slot<MutableMap<String, Any>>()
        every { gigyaMockk.register(any(), any(), capture(slotParams), capture(slotLoginCallback)) } returns Unit

        ownIdGigya.register(
            TestDataGigya.validEmail,
            GigyaRegistrationParameters(TestDataGigya.validProfileParams),
            TestDataGigya.validRegistrationResponseNoEmail,
            callback
        )
        slotLoginCallback.captured.onSuccess(accountMockk)

        shadowOf(getMainLooper()).idle()

        Truth.assertThat(verify(exactly = 1) {
            ownIdGigya["generatePassword"](any<Int>(), any<Int>(), any<Int>(), any<Int>())
            gigyaMockk.register(TestDataGigya.validEmail, any(), any(), any())
        })

        Truth.assertThat(verifyOrder {
            ownIdGigya["generatePassword"](any<Int>(), any<Int>(), any<Int>(), any<Int>())
            gigyaMockk.register(TestDataGigya.validEmail, any(), any(), any())
        })

        Truth.assertThat(slotParams.captured["profile"]).isEqualTo("{\"firstName\":\"${TestDataGigya.validName}\"}")

        Truth.assertThat(slotParams.captured["data"])
            .isEqualTo("{\"ownId\":{\"connections\":[{\"id\":\"AcfwyOjWW8eC1\",\"source\":\"register\"}]}}")
    }


    @Test
    @LooperMode(LooperMode.Mode.PAUSED)
    public fun registerFidoWithNameOneEmailAndSameEmailInResponseAndData() {

        val slotLoginCallback = slot<GigyaLoginCallback<GigyaAccount>>()
        val slotParams = slot<MutableMap<String, Any>>()
        every { gigyaMockk.register(any(), any(), capture(slotParams), capture(slotLoginCallback)) } returns Unit

        ownIdGigya.register(
            TestDataGigya.validEmail,
            GigyaRegistrationParameters(TestDataGigya.validDataParams),
            TestDataGigya.validRegistrationFidoOwnIdResponse,
            callback
        )
        slotLoginCallback.captured.onSuccess(accountMockk)

        shadowOf(getMainLooper()).idle()

        Truth.assertThat(verify(exactly = 1) {
            ownIdGigya["generatePassword"](any<Int>(), any<Int>(), any<Int>(), any<Int>())
            gigyaMockk.register(TestDataGigya.validEmail, any(), any(), any())
        })

        Truth.assertThat(verifyOrder {
            ownIdGigya["generatePassword"](any<Int>(), any<Int>(), any<Int>(), any<Int>())
            gigyaMockk.register(TestDataGigya.validEmail, any(), any(), any())
        })

        Truth.assertThat(slotParams.captured["data"])
            .isEqualTo("{\"firstName\":\"SomeUserName\",\"ownId\":{\"connections\":[{\"id\":\"AcfwyOjWW8eC1\",\"source\":\"register\"}]}}")
    }

    @Test
    @LooperMode(LooperMode.Mode.PAUSED)
    public fun loginWithGigyaData() {
        val slotSessionInfo = slot<SessionInfo>()

        every { gigyaMockk.setSession(capture(slotSessionInfo)) } returns Unit
        every { gigyaMockk.isLoggedIn } returns true

        ownIdGigya.login(TestDataGigya.validLoginOwnIdResponse, callback)

        shadowOf(getMainLooper()).idle()

        Truth.assertThat(verify(exactly = 1) {
            gigyaMockk.setSession(any())
        })

        Truth.assertThat(callbackResult).isEqualTo(Result.success(Unit))
        Truth.assertThat(slotSessionInfo.captured.sessionToken)
            .isEqualTo("st2.s.AcbHqtC03.sc3")
        Truth.assertThat(slotSessionInfo.captured.expirationTime).isEqualTo(0)
    }

    @Test
    @LooperMode(LooperMode.Mode.PAUSED)
    public fun loginWithGigyaDataError() {
        val slotSessionInfo = slot<SessionInfo>()

        every { gigyaMockk.setSession(capture(slotSessionInfo)) } throws IllegalArgumentException("TestError")
        every { gigyaMockk.isLoggedIn } returns true

        ownIdGigya.login(TestDataGigya.validLoginOwnIdResponse, callback)

        shadowOf(getMainLooper()).idle()

        Truth.assertThat(verify(exactly = 1) {
            gigyaMockk.setSession(any())
        })

        Truth.assertThat(verify(exactly = 0) {
            gigyaMockk.isLoggedIn
        })

        Truth.assertThat(callbackResult!!.isFailure).isTrue()
        val exception = callbackResult!!.exceptionOrNull()!!
        Truth.assertThat(exception::class.java).isEqualTo(OwnIdException::class.java)
        Truth.assertThat(exception.message).isEqualTo("Login: Error in JSON")
    }

    @Test
    @LooperMode(LooperMode.Mode.PAUSED)
    public fun loginWithGigyaValidationPendingDataError() {

        ownIdGigya.login(TestDataGigya.validLoginValidationPendingOwnIdResponse, callback)

        shadowOf(getMainLooper()).idle()

        Truth.assertThat(verify(exactly = 0) {
            gigyaMockk.setSession(any())
            gigyaMockk.isLoggedIn
        })

        Truth.assertThat(callbackResult!!.isFailure).isTrue()
        val exception = callbackResult!!.exceptionOrNull()!!
        Truth.assertThat(exception::class.java).isEqualTo(GigyaException::class.java)
        Truth.assertThat(exception.message).isEqualTo("Login: Account Pending Verification")

        val gigyaError = (exception as GigyaException).gigyaError
        Truth.assertThat(gigyaError.callId).isEqualTo("62db81f8c8a1493e98ece0458eec19a5")
        Truth.assertThat(gigyaError.localizedMessage).isEqualTo("Account Pending Verification")
        Truth.assertThat(gigyaError.errorCode).isEqualTo(206002)
    }
}