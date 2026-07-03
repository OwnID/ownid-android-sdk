package com.ownid.sdk.ops.impl

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LoginIdConfiguration
import com.ownid.sdk.model.OperationType
import com.ownid.sdk.ops.LoginIdCollectOperation
import com.ownid.sdk.ops.LoginIdCollectOperationFailure
import com.ownid.sdk.ops.OperationController
import com.ownid.sdk.ops.ui.LoginIdCollectUi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(InternalOwnIdAPI::class)
// Covers: OPS-090
internal class LoginIdCollectOperationImplTest {

    @Test
    fun validParamLoginIdCompletesWithoutStartingUi() = runTest {
        val ui = RecordingLoginIdCollectUi()
        val operation = LoginIdCollectOperationImpl(
            operationType = OperationType.LOGIN_ID_COLLECT,
            operationRegistry = OperationRegistryImpl(),
            loginIdConfigurationProvider = TestLoginIdConfigurationProvider(LoginIdConfiguration.DEFAULT_EMAIL),
            loginIdValidator = TestLoginIdValidator(),
            ui = ui,
            errorStringsProvider = null,
            defaultDispatcher = operationTestDispatcher(),
            instanceJob = Job(),
            context = null,
            unsatisfiedDependencies = null,
            logger = null
        )

        val controller = operation.start(LoginIdCollectOperation.Params(loginId = emailLoginId))
        drainOperations()

        assertThat(controller.whenSettled().assertSuccessResult()).isEqualTo(emailLoginId)
        assertThat(ui.startCount).isEqualTo(0)
    }

    private class RecordingLoginIdCollectUi : LoginIdCollectUi {
        var startCount = 0

        override fun start(
            controller: OperationController<com.ownid.sdk.model.LoginId, LoginIdCollectOperationFailure>,
            onStartError: (error: Throwable?, details: String?) -> Unit
        ) {
            startCount++
        }
    }
}
