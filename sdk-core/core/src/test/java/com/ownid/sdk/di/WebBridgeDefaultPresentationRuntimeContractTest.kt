package com.ownid.sdk.di

import android.app.Activity
import android.app.Application
import android.content.Intent
import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.OwnId
import com.ownid.sdk.OwnIdConfigurationImpl
import com.ownid.sdk.model.InstanceName
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.OperationID
import com.ownid.sdk.model.OperationType
import com.ownid.sdk.model.Reason
import com.ownid.sdk.ops.OperationController
import com.ownid.sdk.ops.OperationResult
import com.ownid.sdk.ops.WebBridgeOperationFailure
import com.ownid.sdk.ops.ui.WebBridgeUi
import com.ownid.sdk.ui.OwnIdActivityHostProtocol
import com.ownid.sdk.ui.OwnIdWebViewActivity
import com.ownid.sdk.ui.capability.HiddenActivityLauncher
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@OptIn(InternalOwnIdAPI::class)
// Covers: WB-BRIDGE-290
internal class WebBridgeDefaultPresentationRuntimeContractTest {

    private val instancesToDestroy = mutableListOf<InstanceName>()

    private val app: Application
        get() = RuntimeEnvironment.getApplication()

    @After
    fun tearDown() {
        instancesToDestroy.forEach(OwnId::destroy)
        OwnIdActivityHostProtocol.LaunchLock.resetForTesting()
    }

    @Test
    fun defaultWebBridgeUiRoutesMatchingInstanceAndOperationToDedicatedActivity() {
        OwnIdRootDIContainer.injectRootDefaults(app)
        val instanceName = InstanceName("default-web-route-${UUID.randomUUID()}").also(instancesToDestroy::add)
        val container = OwnIdRootDIContainer.initializeInstanceContainer(
            instanceName,
            OwnIdConfigurationImpl(appId = "DefaultWebRouteTest")
        )
        val launcher = CapturingHiddenActivityLauncher()
        container.register<HiddenActivityLauncher>(launcher)
        val operationID = OperationID(OperationType.WEB_BRIDGE, "matching-operation")
        val startupErrors = mutableListOf<Pair<Throwable?, String?>>()

        container.getOrThrow<WebBridgeUi>().start(FakeOperationController(operationID)) { error, details ->
            startupErrors += error to details
        }

        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val intent = launcher.singleIntentProvider(activity)
        assertThat(intent.component?.className).isEqualTo(OwnIdWebViewActivity::class.java.name)
        assertThat(OwnIdActivityHostProtocol.instanceNameOrNull(intent)).isEqualTo(instanceName)
        assertThat(intent.getStringExtra(OPERATION_TYPE_KEY)).isEqualTo(OperationType.WEB_BRIDGE.name)
        assertThat(intent.getStringExtra(OPERATION_ID_KEY)).isEqualTo(operationID.id)
        assertThat(startupErrors).isEmpty()
    }

    private class CapturingHiddenActivityLauncher : HiddenActivityLauncher {
        private val intentProviders = mutableListOf<(Activity) -> Intent>()

        override fun launchActivityForResult(
            intentProvider: (Activity) -> Intent,
            onShowError: (error: Throwable?, details: String?) -> Unit
        ) {
            intentProviders += intentProvider
        }

        fun singleIntentProvider(activity: Activity): Intent {
            assertThat(intentProviders).hasSize(1)
            return intentProviders.single()(activity)
        }
    }

    private class FakeOperationController(override val operationId: OperationID) : OperationController<Unit, WebBridgeOperationFailure> {
        override fun abort(reason: Reason) = Unit

        override suspend fun whenSettled(): OperationResult<Unit, WebBridgeOperationFailure> = OperationResult.Success(Unit)
    }

    private companion object {
        private const val OPERATION_TYPE_KEY = "com.ownid.sdk.key.OPERATION_TYPE_KEY"
        private const val OPERATION_ID_KEY = "com.ownid.sdk.key.OPERATION_ID_KEY"
    }
}
