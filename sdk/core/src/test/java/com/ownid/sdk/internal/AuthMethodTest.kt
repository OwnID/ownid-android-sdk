import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.internal.AuthMethod
import org.junit.Test

@OptIn(InternalOwnIdAPI::class)
public class AuthMethodTest {
    @Test
    public fun `fromString returns Passkey for valid aliases`() {
        assertThat(AuthMethod.fromString("biometrics")).isEqualTo(AuthMethod.Passkey)
        assertThat(AuthMethod.fromString("desktop-biometrics")).isEqualTo(AuthMethod.Passkey)
        assertThat(AuthMethod.fromString("passkey")).isEqualTo(AuthMethod.Passkey)
        assertThat(AuthMethod.fromString("PaSsKeY")).isEqualTo(AuthMethod.Passkey) // Case-insensitive
    }

    @Test
    public fun `fromString returns Otp for valid aliases`() {
        assertThat(AuthMethod.fromString("email-fallback")).isEqualTo(AuthMethod.Otp)
        assertThat(AuthMethod.fromString("sms-fallback")).isEqualTo(AuthMethod.Otp)
        assertThat(AuthMethod.fromString("otp")).isEqualTo(AuthMethod.Otp)
        assertThat(AuthMethod.fromString("OTP")).isEqualTo(AuthMethod.Otp) // Case-insensitive
    }

    @Test
    public fun `fromString returns Password for valid aliases`() {
        assertThat(AuthMethod.fromString("password")).isEqualTo(AuthMethod.Password)
        assertThat(AuthMethod.fromString("PASSWORD")).isEqualTo(AuthMethod.Password) // Case-insensitive
    }

    @Test
    public fun `fromString returns null for invalid aliases`() {
        assertThat(AuthMethod.fromString("invalid")).isNull()
        assertThat(AuthMethod.fromString("")).isNull()
        assertThat(AuthMethod.fromString(" ")).isNull()
    }
}