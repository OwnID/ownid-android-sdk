package com.ownid.sdk.exception

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.InternalOwnIdAPI
import org.junit.Test

@OptIn(InternalOwnIdAPI::class)
public class OwnIdFlowCanceledTest {

    @Test
    public fun userCanceledSteps_keepUserCanceledMessage() {
        val steps = listOf(
            OwnIdFlowCanceled.ID_COLLECT,
            OwnIdFlowCanceled.FIDO_REGISTER,
            OwnIdFlowCanceled.FIDO_LOGIN,
            OwnIdFlowCanceled.OTP,
            OwnIdFlowCanceled.WEB_APP,
        )

        steps.forEach { step ->
            assertThat(OwnIdFlowCanceled(step).message).isEqualTo("User canceled OwnID flow")
        }
    }

    @Test
    public fun pendingResult_usesInterruptedMessage() {
        assertThat(OwnIdFlowCanceled(OwnIdFlowCanceled.RESULT_PENDING).message)
            .isEqualTo("OwnID flow was interrupted before completion")
        assertThat(OwnIdFlowCanceled("${OwnIdFlowCanceled.RESULT_PENDING}:-1").message)
            .isEqualTo("OwnID flow was interrupted before completion")
    }
}
