package com.ownid.sdk.internal.enrollment

import com.google.common.truth.Truth
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.internal.feature.enrollment.OwnIdEnrollmentViewModelInt
import com.ownid.sdk.internal.toBase64UrlSafeNoPadding
import org.json.JSONException
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(InternalOwnIdAPI::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
public class OwnIdEnrollmentViewModelIntTest {

    private val viewModel = OwnIdEnrollmentViewModelInt()

    @Before
    public fun setUp() {

    }

    @After
    public fun tearDown() {

    }

    @Test
    public fun `adjustEnrollmentOptions - valid input`() {
        val options =
            "{\"rp\":{\"id\":\"dev.ownid.com\",\"name\":\"Demo Gigya Long Name testing\"},\"user\":{\"name\":\"sdfsf@fsfsdf.dfd\",\"id\":\"c2Rmc2ZAZnNmc2RmLmRmZA\",\"displayName\":\"sdfsf@fsfsdf.dfd\"},\"challenge\":\"dEsUb6hgwkeZVOhujtDL3g\",\"pubKeyCredParams\":[{\"type\":\"public-key\",\"alg\":-7},{\"type\":\"public-key\",\"alg\":-257}],\"excludeCredentials\":[],\"authenticatorSelection\":{\"authenticatorAttachment\":\"platform\",\"requireResidentKey\":false,\"userVerification\":\"required\",\"residentKey\":\"preferred\"},\"attestation\":\"direct\"}"

        val adjustedOptions = viewModel.adjustEnrollmentOptions(options)

        Truth.assertThat(adjustedOptions.isEmpty()).isFalse()
        // Add more assertions based on the expected behavior of the function
    }

    @Test(expected = JSONException::class)
    public fun `adjustEnrollmentOptions - invalid JSON`() {
        val invalidOptions = "invalid json string"
        viewModel.adjustEnrollmentOptions(invalidOptions)
    }

    @Test
    public fun `adjustEnrollmentOptions - updated challenge`() {
        val options =
            "{\"rp\":{\"id\":\"dev.ownid.com\",\"name\":\"Demo Gigya Long Name testing\"},\"user\":{\"name\":\"sdfsf@fsfsdf.dfd\",\"id\":\"c2Rmc2ZAZnNmc2RmLmRmZA\",\"displayName\":\"sdfsf@fsfsdf.dfd\"},\"challenge\":\"dEsUb6hgwkeZVOhujtDL3g\",\"pubKeyCredParams\":[{\"type\":\"public-key\",\"alg\":-7},{\"type\":\"public-key\",\"alg\":-257}],\"excludeCredentials\":[],\"authenticatorSelection\":{\"authenticatorAttachment\":\"platform\",\"requireResidentKey\":false,\"userVerification\":\"required\",\"residentKey\":\"preferred\"},\"attestation\":\"direct\"}"

        val challengeOriginal = JSONObject(options).getString("challenge")

        val adjustedOptions = viewModel.adjustEnrollmentOptions(options)

        val challengeUpdated = JSONObject(adjustedOptions).getString("challenge")

        Truth.assertThat(adjustedOptions.isEmpty()).isFalse()
        Truth.assertThat(adjustedOptions.contains("challenge")).isTrue()
        Truth.assertThat(challengeUpdated.equals(challengeOriginal.encodeToByteArray().toBase64UrlSafeNoPadding())).isTrue()
    }

    @Test
    public fun `adjustEnrollmentOptions - missing user id`() {
        val options =
            "{\"rp\":{\"id\":\"dev.ownid.com\",\"name\":\"Demo Gigya Long Name testing\"},\"user\":{\"name\":\"sdfsf@fsfsdf.dfd\",\"displayName\":\"sdfsf@fsfsdf.dfd\"},\"challenge\":\"dEsUb6hgwkeZVOhujtDL3g\",\"pubKeyCredParams\":[{\"type\":\"public-key\",\"alg\":-7},{\"type\":\"public-key\",\"alg\":-257}],\"excludeCredentials\":[],\"authenticatorSelection\":{\"authenticatorAttachment\":\"platform\",\"requireResidentKey\":false,\"userVerification\":\"required\",\"residentKey\":\"preferred\"},\"attestation\":\"direct\"}"

        val adjustedOptions = viewModel.adjustEnrollmentOptions(options)

        val adjustedJSON = JSONObject(adjustedOptions)

        Truth.assertThat(adjustedOptions.isEmpty()).isFalse()
        Truth.assertThat(adjustedJSON.has("user")).isTrue()
        Truth.assertThat(adjustedJSON.getJSONObject("user").has("id")).isTrue()
        Truth.assertThat(adjustedJSON.getJSONObject("user").getString("id").isNotBlank()).isTrue()
    }

    @Test
    public fun `adjustEnrollmentOptions - missing timeout`() {
        val options =
            "{\"rp\":{\"id\":\"dev.ownid.com\",\"name\":\"Demo Gigya Long Name testing\"},\"user\":{\"name\":\"sdfsf@fsfsdf.dfd\",\"id\":\"c2Rmc2ZAZnNmc2RmLmRmZA\",\"displayName\":\"sdfsf@fsfsdf.dfd\"},\"challenge\":\"dEsUb6hgwkeZVOhujtDL3g\",\"pubKeyCredParams\":[{\"type\":\"public-key\",\"alg\":-7},{\"type\":\"public-key\",\"alg\":-257}],\"excludeCredentials\":[],\"authenticatorSelection\":{\"authenticatorAttachment\":\"platform\",\"requireResidentKey\":false,\"userVerification\":\"required\",\"residentKey\":\"preferred\"},\"attestation\":\"direct\"}"

        val adjustedOptions = viewModel.adjustEnrollmentOptions(options)
        val adjustedJSON = JSONObject(adjustedOptions)

        Truth.assertThat(adjustedOptions.isEmpty()).isFalse()
        Truth.assertThat(adjustedJSON.has("timeout")).isTrue()
        Truth.assertThat(adjustedJSON.getLong("timeout")).isGreaterThan(0L)
    }

    @Test
    public fun `adjustEnrollmentOptions - missing attestation`() {
        val options =
            "{\"rp\":{\"id\":\"dev.ownid.com\",\"name\":\"Demo Gigya Long Name testing\"},\"user\":{\"name\":\"sdfsf@fsfsdf.dfd\",\"id\":\"c2Rmc2ZAZnNmc2RmLmRmZA\",\"displayName\":\"sdfsf@fsfsdf.dfd\"},\"challenge\":\"dEsUb6hgwkeZVOhujtDL3g\",\"pubKeyCredParams\":[{\"type\":\"public-key\",\"alg\":-7},{\"type\":\"public-key\",\"alg\":-257}],\"excludeCredentials\":[],\"authenticatorSelection\":{\"authenticatorAttachment\":\"platform\",\"requireResidentKey\":false,\"userVerification\":\"required\",\"residentKey\":\"preferred\"}}"

        val adjustedOptions = viewModel.adjustEnrollmentOptions(options)
        val adjustedJSON = JSONObject(adjustedOptions)

        Truth.assertThat(adjustedOptions.isEmpty()).isFalse()
        Truth.assertThat(adjustedJSON.has("attestation")).isTrue()
        Truth.assertThat(adjustedJSON.getString("attestation")).isEqualTo("none")
    }

    @Test
    public fun `adjustEnrollmentOptions - missing authenticatorAttachment`() {
        val options =
            "{\"rp\":{\"id\":\"dev.ownid.com\",\"name\":\"Demo Gigya Long Name testing\"},\"user\":{\"name\":\"sdfsf@fsfsdf.dfd\",\"id\":\"c2Rmc2ZAZnNmc2RmLmRmZA\",\"displayName\":\"sdfsf@fsfsdf.dfd\"},\"challenge\":\"dEsUb6hgwkeZVOhujtDL3g\",\"pubKeyCredParams\":[{\"type\":\"public-key\",\"alg\":-7},{\"type\":\"public-key\",\"alg\":-257}],\"excludeCredentials\":[],\"authenticatorSelection\":{\"requireResidentKey\":false,\"userVerification\":\"required\",\"residentKey\":\"preferred\"},\"attestation\":\"direct\"}"

        val adjustedOptions = viewModel.adjustEnrollmentOptions(options)
        val authenticatorSelection = JSONObject(adjustedOptions).getJSONObject("authenticatorSelection")

        Truth.assertThat(adjustedOptions.isEmpty()).isFalse()
        Truth.assertThat(authenticatorSelection.has("authenticatorAttachment")).isTrue()
        Truth.assertThat(authenticatorSelection.getString("authenticatorAttachment")).isEqualTo("platform")
    }

    @Test
    public fun `adjustEnrollmentOptions - missing userVerification`() {
        val options =
            "{\"rp\":{\"id\":\"dev.ownid.com\",\"name\":\"Demo Gigya Long Name testing\"},\"user\":{\"name\":\"sdfsf@fsfsdf.dfd\",\"id\":\"c2Rmc2ZAZnNmc2RmLmRmZA\",\"displayName\":\"sdfsf@fsfsdf.dfd\"},\"challenge\":\"dEsUb6hgwkeZVOhujtDL3g\",\"pubKeyCredParams\":[{\"type\":\"public-key\",\"alg\":-7},{\"type\":\"public-key\",\"alg\":-257}],\"excludeCredentials\":[],\"authenticatorSelection\":{\"authenticatorAttachment\":\"platform\",\"requireResidentKey\":false,\"residentKey\":\"preferred\"},\"attestation\":\"direct\"}"

        val adjustedOptions = viewModel.adjustEnrollmentOptions(options)
        val authenticatorSelection = JSONObject(adjustedOptions).getJSONObject("authenticatorSelection")

        Truth.assertThat(adjustedOptions.isEmpty()).isFalse()
        Truth.assertThat(authenticatorSelection.has("userVerification")).isTrue()
        Truth.assertThat(authenticatorSelection.getString("userVerification")).isEqualTo("required")
    }

    @Test
    public fun `adjustEnrollmentOptions - missing requireResidentKey`() {
        val options =
            "{\"rp\":{\"id\":\"dev.ownid.com\",\"name\":\"Demo Gigya Long Name testing\"},\"user\":{\"name\":\"sdfsf@fsfsdf.dfd\",\"id\":\"c2Rmc2ZAZnNmc2RmLmRmZA\",\"displayName\":\"sdfsf@fsfsdf.dfd\"},\"challenge\":\"dEsUb6hgwkeZVOhujtDL3g\",\"pubKeyCredParams\":[{\"type\":\"public-key\",\"alg\":-7},{\"type\":\"public-key\",\"alg\":-257}],\"excludeCredentials\":[],\"authenticatorSelection\":{\"authenticatorAttachment\":\"platform\",\"userVerification\":\"required\",\"residentKey\":\"preferred\"},\"attestation\":\"direct\"}"

        val adjustedOptions = viewModel.adjustEnrollmentOptions(options)
        val authenticatorSelection = JSONObject(adjustedOptions).getJSONObject("authenticatorSelection")

        Truth.assertThat(adjustedOptions.isEmpty()).isFalse()
        Truth.assertThat(authenticatorSelection.has("requireResidentKey")).isTrue()
        Truth.assertThat(authenticatorSelection.getBoolean("requireResidentKey")).isFalse()
    }

    @Test
    public fun `adjustEnrollmentOptions - missing residentKey`() {
        val options =
            "{\"rp\":{\"id\":\"dev.ownid.com\",\"name\":\"Demo Gigya Long Name testing\"},\"user\":{\"name\":\"sdfsf@fsfsdf.dfd\",\"id\":\"c2Rmc2ZAZnNmc2RmLmRmZA\",\"displayName\":\"sdfsf@fsfsdf.dfd\"},\"challenge\":\"dEsUb6hgwkeZVOhujtDL3g\",\"pubKeyCredParams\":[{\"type\":\"public-key\",\"alg\":-7},{\"type\":\"public-key\",\"alg\":-257}],\"excludeCredentials\":[],\"authenticatorSelection\":{\"authenticatorAttachment\":\"platform\",\"requireResidentKey\":false,\"userVerification\":\"required\"},\"attestation\":\"direct\"}"

        val adjustedOptions = viewModel.adjustEnrollmentOptions(options)
        val authenticatorSelection = JSONObject(adjustedOptions).getJSONObject("authenticatorSelection")

        Truth.assertThat(adjustedOptions.isEmpty()).isFalse()
        Truth.assertThat(authenticatorSelection.has("residentKey")).isTrue()
        Truth.assertThat(authenticatorSelection.getString("residentKey")).isEqualTo("preferred")
    }
}