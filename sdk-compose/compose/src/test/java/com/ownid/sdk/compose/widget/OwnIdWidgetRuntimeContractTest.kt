package com.ownid.sdk.compose.widget

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.OwnId
import com.ownid.sdk.di.register
import com.ownid.sdk.flow.FlowResult
import com.ownid.sdk.flow.boost.BoostCreatePasskeyFlowFailure
import com.ownid.sdk.flow.boost.BoostFlow
import com.ownid.sdk.flow.boost.BoostFlowContext
import com.ownid.sdk.flow.boost.BoostLoginFlowFailure
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.AuthMethod
import com.ownid.sdk.model.InstanceName
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.ProofToken
import com.ownid.sdk.model.Reason
import com.ownid.sdk.ui.capability.BoostWidgetStrings
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@OptIn(InternalOwnIdAPI::class)
// Covers: UI-PRESENT-100
internal class OwnIdWidgetRuntimeContractTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val app: Application
        get() = RuntimeEnvironment.getApplication()

    private val createdInstances = mutableListOf<InstanceName>()

    @After
    fun tearDown() {
        createdInstances.forEach(OwnId::destroy)
        createdInstances.clear()
    }

    @Test
    fun loginWidgetRetainsRunningStateForStableWidgetIdAndCreatesStateForNewWidgetId() {
        val instanceName = createInitializedInstance()
        val loginFlow = RecordingLoginFlow()
        OwnId.getInstanceContainer(instanceName)!!.register<BoostFlow.Login>(loginFlow)

        val lifecycleOwner = RecordingLifecycleOwner().started()
        var widgetId by mutableStateOf("stable-id")
        var loginId by mutableStateOf("first")

        composeRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                OwnIdLoginWidget(
                    onLogin = {},
                    loginId = loginId,
                    widgetId = widgetId,
                    instanceName = instanceName,
                    widgetStrings = BoostWidgetStrings.DEFAULT,
                    iconButton = taggedIconButton(),
                    orText = null
                )
            }
        }

        composeRule.onNodeWithTag(WIDGET_BUTTON_TAG).assertIsEnabled().performClick()
        composeRule.waitUntil { loginFlow.contexts.size == 1 }
        composeRule.onNodeWithTag(WIDGET_BUTTON_TAG).assertIsNotEnabled()

        composeRule.runOnIdle {
            loginId = "second"
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(WIDGET_BUTTON_TAG).assertIsNotEnabled()
        assertThat(loginFlow.contexts).hasSize(1)

        composeRule.runOnIdle {
            widgetId = "replacement-id"
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(WIDGET_BUTTON_TAG).assertIsEnabled().performClick()
        composeRule.waitUntil { loginFlow.contexts.size == 2 }

        assertWidgetButtonContexts(loginFlow.contexts, "first", "second")
    }

    @Test
    fun loginWidgetDeliversEffectToLatestCallbackWhenLifecycleIsActive() {
        val controller = DeferredFlowController<BoostFlow.Response.Login, BoostLoginFlowFailure>()
        val viewModel = OwnIdLoginWidgetViewModel { controller }
        val callbacks = mutableListOf<String>()
        var onLoginCallback by mutableStateOf<(BoostFlow.Response.Login) -> Unit>(
            { response -> callbacks += "initial:${response.loginId.id}" }
        )

        composeRule.setContent {
            OwnIdLoginWidget(
                onLogin = onLoginCallback,
                viewModel = viewModel,
                widgetStrings = BoostWidgetStrings.DEFAULT,
                iconButton = taggedIconButton(),
                orText = null
            )
        }

        composeRule.runOnIdle {
            viewModel.startFlow("login@example.com")
        }
        composeRule.runOnIdle {
            onLoginCallback = { response -> callbacks += "updated:${response.loginId.id}" }
        }
        composeRule.waitForIdle()
        controller.result.complete(FlowResult.Success(loginResponse("login@example.com")))

        composeRule.waitUntil { callbacks.isNotEmpty() }

        assertThat(callbacks).containsExactly("updated:login@example.com")
    }

    @Test
    fun loginWidgetCollectsEffectsOnlyAfterProvidedLifecycleOwnerStarts() {
        val controller = DeferredFlowController<BoostFlow.Response.Login, BoostLoginFlowFailure>()
        val viewModel = OwnIdLoginWidgetViewModel { controller }
        val lifecycleOwner = RecordingLifecycleOwner().created()
        val callbacks = mutableListOf<String>()

        composeRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                OwnIdLoginWidget(
                    onLogin = { response -> callbacks += response.loginId.id },
                    viewModel = viewModel,
                    widgetStrings = BoostWidgetStrings.DEFAULT,
                    iconButton = taggedIconButton(),
                    orText = null
                )
            }
        }

        composeRule.runOnIdle {
            viewModel.startFlow("gated@example.com")
        }
        controller.result.complete(FlowResult.Success(loginResponse("gated@example.com")))
        composeRule.waitForIdle()

        assertThat(callbacks).isEmpty()

        composeRule.runOnIdle {
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        }
        composeRule.waitUntil { callbacks.isNotEmpty() }

        assertThat(callbacks).containsExactly("gated@example.com")
    }

    @Test
    fun createPasskeyWidgetDeliversCreatePasskeyEffectToLatestCallback() {
        val controller = DeferredFlowController<BoostFlow.Response, BoostCreatePasskeyFlowFailure>()
        val viewModel = OwnIdCreatePasskeyWidgetViewModel { controller }
        val callbacks = mutableListOf<String>()
        var onNewPasskeyCallback by mutableStateOf<(BoostFlow.Response.CreatePasskey) -> Unit>(
            { response -> callbacks += "initial:${response.loginId.id}" }
        )

        composeRule.setContent {
            OwnIdCreatePasskeyWidget(
                onLogin = {},
                onNewPasskey = onNewPasskeyCallback,
                onReset = {},
                viewModel = viewModel,
                loginId = "new@example.com",
                widgetStrings = BoostWidgetStrings.DEFAULT,
                iconButton = taggedIconButton(),
                orText = null
            )
        }

        composeRule.runOnIdle {
            viewModel.startFlow("new@example.com")
        }
        composeRule.runOnIdle {
            onNewPasskeyCallback = { response -> callbacks += "updated:${response.loginId.id}" }
        }
        composeRule.waitForIdle()
        controller.result.complete(FlowResult.Success(createPasskeyResponse()))

        composeRule.waitUntil { callbacks.isNotEmpty() }

        assertThat(callbacks).containsExactly("updated:new@example.com")
    }

    @Test
    fun createPasskeyWidgetReflectsBusyAndCompletionStateAndResetsWhenLoginIdChanges() {
        val firstController = DeferredFlowController<BoostFlow.Response, BoostCreatePasskeyFlowFailure>()
        val contexts = mutableListOf<BoostFlowContext>()
        val viewModel = OwnIdCreatePasskeyWidgetViewModel { context ->
            contexts += context
            firstController
        }
        val callbacks = mutableListOf<String>()

        composeRule.setContent {
            OwnIdCreatePasskeyWidget(
                onLogin = {},
                onNewPasskey = { response -> callbacks += "passkey:${response.loginId.id}" },
                onReset = { callbacks += "reset" },
                viewModel = viewModel,
                loginId = "new@example.com",
                widgetStrings = BoostWidgetStrings.DEFAULT,
                iconButton = taggedIconButton(),
                checkmark = {
                    androidx.compose.material3.Text("Done", modifier = Modifier.testTag(WIDGET_CHECKMARK_TAG))
                },
                orText = null
            )
        }

        composeRule.onNodeWithTag(WIDGET_BUTTON_TAG).assertIsEnabled().performClick()
        composeRule.onNodeWithTag(WIDGET_BUTTON_TAG).performClick()
        composeRule.waitUntil { viewModel.uiState.value.isRunning }
        composeRule.onNodeWithTag(WIDGET_BUTTON_TAG).assertIsNotEnabled()
        composeRule.onNodeWithTag(WIDGET_CHECKMARK_TAG).assertDoesNotExist()
        assertThat(contexts).hasSize(1)

        firstController.result.complete(FlowResult.Success(createPasskeyResponse()))
        composeRule.waitUntil { callbacks.size == 1 }
        assertThat(callbacks).containsExactly("passkey:new@example.com")
        composeRule.onNodeWithTag(WIDGET_BUTTON_TAG).assertIsEnabled()
        composeRule.onNodeWithTag(WIDGET_CHECKMARK_TAG).assertExists()

        composeRule.runOnIdle {
            viewModel.onLoginIdChanged("other@example.com")
        }
        composeRule.waitUntil { callbacks.size == 2 }
        assertThat(callbacks).containsExactly("passkey:new@example.com", "reset").inOrder()
        composeRule.onNodeWithTag(WIDGET_CHECKMARK_TAG).assertDoesNotExist()

        composeRule.runOnIdle {
            viewModel.onLoginIdChanged("new@example.com")
        }
        composeRule.waitUntil { callbacks.size == 3 }
        assertThat(callbacks).containsExactly("passkey:new@example.com", "reset", "passkey:new@example.com").inOrder()
        composeRule.onNodeWithTag(WIDGET_CHECKMARK_TAG).assertExists()
    }

    private fun createInitializedInstance(): InstanceName {
        val instanceName = InstanceName("widget-runtime-${UUID.randomUUID()}")
        createdInstances += instanceName
        OwnId.initialize(instanceName) {
            applicationContext = app
            appId = "App123"
        }
        return instanceName
    }

    private fun assertWidgetButtonContexts(contexts: List<BoostFlowContext>, vararg rawLoginIds: String?) {
        assertThat(contexts).hasSize(rawLoginIds.size)
        contexts.zip(rawLoginIds).forEach { (context, rawLoginId) -> assertWidgetButtonContext(context, rawLoginId) }
    }

    private fun assertWidgetButtonContext(context: BoostFlowContext, rawLoginId: String?) {
        val description = context.toString()

        assertThat(context.loginId).isNull()
        assertThat(rawLoginIdDescription(description)).isEqualTo(rawLoginId)
        assertThat(description).contains("source=WIDGET_BUTTON")
    }

    private fun rawLoginIdDescription(description: String): String? =
        RAW_LOGIN_ID_PATTERN.find(description)?.groupValues?.get(1)

    private fun loginResponse(id: String): BoostFlow.Response.Login =
        BoostFlow.Response.Login(
            loginId = LoginId(id, LoginId.Type.Email),
            authMethod = AuthMethod.Passkey,
            accessToken = AccessToken("access-token-$id"),
            sessionPayload = "session-payload",
            session = null
        )

    private fun createPasskeyResponse(): BoostFlow.Response.CreatePasskey =
        BoostFlow.Response.CreatePasskey(
            loginId = LoginId("new@example.com", LoginId.Type.Email),
            proofToken = ProofToken("proof-token-new@example.com"),
            ownIdData = "own-id-data"
        )

    private fun taggedIconButton(): @Composable (onClick: () -> Unit, contentDescription: String, isBusy: Boolean, enabled: Boolean) -> Unit =
        { onClick, _, isBusy, enabled ->
            SideEffect {
                check(!isBusy || !enabled)
            }
            androidx.compose.material3.Button(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.testTag(WIDGET_BUTTON_TAG)
            ) {
                androidx.compose.material3.Text(if (isBusy) "Busy" else "Ready")
            }
        }

    private class RecordingLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)

        override val lifecycle: Lifecycle = registry

        fun created(): RecordingLifecycleOwner = apply {
            handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        }

        fun started(): RecordingLifecycleOwner = apply {
            handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            handleLifecycleEvent(Lifecycle.Event.ON_START)
            handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        fun handleLifecycleEvent(event: Lifecycle.Event) {
            registry.handleLifecycleEvent(event)
        }
    }

    private class RecordingLoginFlow : BoostFlow.Login {
        val contexts = mutableListOf<BoostFlowContext>()
        val controllers = mutableListOf<DeferredFlowController<BoostFlow.Response.Login, BoostLoginFlowFailure>>()

        override fun start(context: BoostFlowContext): BoostFlow.Controller<BoostFlow.Response.Login, BoostLoginFlowFailure> {
            contexts += context
            return DeferredFlowController<BoostFlow.Response.Login, BoostLoginFlowFailure>().also(controllers::add)
        }
    }

    private class DeferredFlowController<Success, Failure : com.ownid.sdk.flow.FlowFailure> : BoostFlow.Controller<Success, Failure> {
        val result = CompletableDeferred<FlowResult<Success, Failure>>()
        val abortReasons = mutableListOf<Reason>()

        override fun abort(reason: Reason) {
            abortReasons += reason
        }

        override suspend fun whenSettled(): FlowResult<Success, Failure> = result.await()
    }

    private companion object {
        private const val WIDGET_BUTTON_TAG = "ownid-widget-button"
        private const val WIDGET_CHECKMARK_TAG = "ownid-widget-checkmark"
        private val RAW_LOGIN_ID_PATTERN = Regex("rawLoginId=([^,)]+)")
    }
}
