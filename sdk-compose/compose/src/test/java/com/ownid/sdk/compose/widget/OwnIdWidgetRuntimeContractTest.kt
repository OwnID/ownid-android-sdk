package com.ownid.sdk.compose.widget

import android.app.Activity
import android.app.Application
import android.view.View
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.OwnId
import com.ownid.sdk.capability.UiContextProvider
import com.ownid.sdk.capability.UiContextResolutionException
import com.ownid.sdk.di.getOrThrow
import com.ownid.sdk.di.register
import com.ownid.sdk.di.registerFactory
import com.ownid.sdk.flow.FlowResult
import com.ownid.sdk.flow.boost.BoostCreatePasskeyFlowFailure
import com.ownid.sdk.flow.boost.BoostFlow
import com.ownid.sdk.flow.boost.BoostFlowContext
import com.ownid.sdk.flow.boost.BoostLoginFlowFailure
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.AuthMethod
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.InstanceName
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.ProofToken
import com.ownid.sdk.model.Reason
import com.ownid.sdk.ui.capability.BoostWidgetStrings
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowSystemClock
import java.util.UUID
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@OptIn(InternalOwnIdAPI::class)
// Covers: UI-PRESENT-100, UI-PRESENT-340, UI-PRESENT-350, UI-PRESENT-360
internal class OwnIdWidgetRuntimeContractTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val app: Application
        get() = RuntimeEnvironment.getApplication()

    private val createdInstances = mutableListOf<InstanceName>()
    private val attachedActivity = mockk<Activity>()
    private val attachedView = mockk<View> { every { isAttachedToWindow } returns true }

    @After
    fun tearDown() {
        createdInstances.forEach(OwnId::destroy)
        createdInstances.clear()
    }

    @Test
    fun loginWidgetKeysDefaultStateByWidgetIdAndInstance() {
        val keyPrefix = "widget-runtime-${UUID.randomUUID()}"
        val firstInstance = createInitializedInstance("$keyPrefix:a")
        val secondInstance = createInitializedInstance("$keyPrefix:a:b", appId = "App456")
        val thirdInstance = createInitializedInstance("$keyPrefix:a%3Ab", appId = "App789")
        val firstLoginFlow = RecordingLoginFlow()
        val secondLoginFlow = RecordingLoginFlow()
        val thirdLoginFlow = RecordingLoginFlow()
        OwnId.getInstanceContainer(firstInstance)!!.register<BoostFlow.Login>(firstLoginFlow)
        OwnId.getInstanceContainer(secondInstance)!!.register<BoostFlow.Login>(secondLoginFlow)
        OwnId.getInstanceContainer(thirdInstance)!!.register<BoostFlow.Login>(thirdLoginFlow)

        val lifecycleOwner = RecordingLifecycleOwner().started()
        var widgetId by mutableStateOf("stable-id")
        var loginId by mutableStateOf("first")
        var instanceName by mutableStateOf(firstInstance)

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
        composeRule.waitUntil { firstLoginFlow.contexts.size == 1 }
        composeRule.onNodeWithTag(WIDGET_BUTTON_TAG).assertIsNotEnabled()

        composeRule.runOnIdle {
            loginId = "second"
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(WIDGET_BUTTON_TAG).assertIsNotEnabled()
        assertThat(firstLoginFlow.contexts).hasSize(1)

        composeRule.runOnIdle {
            widgetId = "b:c"
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(WIDGET_BUTTON_TAG).assertIsEnabled().performClick()
        composeRule.waitUntil { firstLoginFlow.contexts.size == 2 }

        composeRule.runOnIdle {
            instanceName = secondInstance
            widgetId = "c"
            loginId = "third"
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(WIDGET_BUTTON_TAG).assertIsEnabled().performClick()
        composeRule.waitUntil { secondLoginFlow.contexts.size == 1 }

        assertThat(firstLoginFlow.contexts).hasSize(2)

        composeRule.runOnIdle {
            instanceName = thirdInstance
            loginId = "fourth"
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(WIDGET_BUTTON_TAG).assertIsEnabled().performClick()
        composeRule.waitUntil { thirdLoginFlow.contexts.size == 1 }

        assertThat(secondLoginFlow.contexts).hasSize(1)
    }

    @Test
    fun loginWidgetKeepsDefaultViewModelAcrossTemporaryRemovalWithStableWidgetId() {
        val instanceName = createInitializedInstance()
        val flow = RecordingLoginFlow()
        OwnId.getInstanceContainer(instanceName)!!.register<BoostFlow.Login>(flow)
        var showWidget by mutableStateOf(true)
        val callbacks = mutableListOf<String>()

        composeRule.setContent {
            if (showWidget) {
                OwnIdLoginWidget(
                    onLogin = { callbacks += it.loginId.id },
                    loginId = "retained@example.com",
                    widgetId = "retained-login",
                    instanceName = instanceName,
                    widgetStrings = BoostWidgetStrings.DEFAULT,
                    iconButton = taggedIconButton(),
                    orText = null
                )
            }
        }

        composeRule.onNodeWithTag(WIDGET_BUTTON_TAG).performClick()
        composeRule.waitUntil { flow.controllers.size == 1 }
        composeRule.onNodeWithTag(WIDGET_BUTTON_TAG).assertIsNotEnabled()

        composeRule.runOnIdle { showWidget = false }
        composeRule.waitForIdle()
        assertThat(flow.controllers.single().abortReasons).isEmpty()

        composeRule.runOnIdle { showWidget = true }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(WIDGET_BUTTON_TAG).assertIsNotEnabled()
        assertThat(flow.controllers).hasSize(1)

        flow.controllers.single().result.complete(FlowResult.Success(loginResponse("retained@example.com")))
        composeRule.waitUntil { callbacks.isNotEmpty() }
        assertThat(callbacks).containsExactly("retained@example.com")
    }

    @Test
    fun loginWidgetDeliversEffectToLatestCallbackWhenLifecycleIsActive() {
        val controller = DeferredFlowController<BoostFlow.Response.Login, BoostLoginFlowFailure>()
        var starts = 0
        val viewModel = OwnIdLoginWidgetViewModel { _, _ ->
            starts++
            controller
        }
        val callbacks = mutableListOf<String>()
        var instanceName by mutableStateOf(InstanceName("provided-login-a"))
        var onLoginCallback by mutableStateOf<(BoostFlow.Response.Login) -> Unit>(
            { response -> callbacks += "initial:${response.loginId.id}" }
        )

        composeRule.setContent {
            OwnIdLoginWidget(
                onLogin = onLoginCallback,
                viewModel = viewModel,
                loginId = "login@example.com",
                instanceName = instanceName,
                widgetStrings = BoostWidgetStrings.DEFAULT,
                iconButton = taggedIconButton(),
                orText = null
            )
        }

        composeRule.onNodeWithTag(WIDGET_BUTTON_TAG).performClick()
        composeRule.runOnIdle {
            instanceName = InstanceName("provided-login-b")
            onLoginCallback = { response -> callbacks += "updated:${response.loginId.id}" }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(WIDGET_BUTTON_TAG).assertIsNotEnabled()
        assertThat(viewModel.uiState.value.isRunning).isTrue()
        assertThat(starts).isEqualTo(1)
        controller.result.complete(FlowResult.Success(loginResponse("login@example.com")))

        composeRule.waitUntil { callbacks.isNotEmpty() }

        assertThat(callbacks).containsExactly("updated:login@example.com")
    }

    @Test
    fun loginWidgetSwitchesEffectsToReplacementProvidedViewModel() {
        val oldController = DeferredFlowController<BoostFlow.Response.Login, BoostLoginFlowFailure>()
        val newController = DeferredFlowController<BoostFlow.Response.Login, BoostLoginFlowFailure>()
        val oldViewModel = OwnIdLoginWidgetViewModel { _, _ -> oldController }
        val newViewModel = OwnIdLoginWidgetViewModel { _, _ -> newController }
        var viewModel by mutableStateOf(oldViewModel)
        var onLoginCallback by mutableStateOf<(BoostFlow.Response.Login) -> Unit>(
            { response -> error("Old callback must not receive ${response.loginId.id}") }
        )
        val callbacks = mutableListOf<String>()

        composeRule.setContent {
            OwnIdLoginWidget(
                onLogin = onLoginCallback,
                viewModel = viewModel,
                loginId = "login@example.com",
                widgetStrings = BoostWidgetStrings.DEFAULT,
                iconButton = taggedIconButton(),
                orText = null
            )
        }

        composeRule.onNodeWithTag(WIDGET_BUTTON_TAG).performClick()
        composeRule.waitUntil { oldViewModel.uiState.value.isRunning }
        composeRule.runOnIdle {
            viewModel = newViewModel
            onLoginCallback = { response -> callbacks += "new:${response.loginId.id}" }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(WIDGET_BUTTON_TAG).assertIsEnabled()

        oldController.result.complete(FlowResult.Success(loginResponse("late@example.com")))
        composeRule.waitUntil { !oldViewModel.uiState.value.isRunning }
        assertThat(callbacks).isEmpty()

        composeRule.onNodeWithTag(WIDGET_BUTTON_TAG).performClick()
        composeRule.waitUntil { newViewModel.uiState.value.isRunning }
        newController.result.complete(FlowResult.Success(loginResponse("current@example.com")))
        composeRule.waitUntil { callbacks.isNotEmpty() }
        assertThat(callbacks).containsExactly("new:current@example.com")
    }

    @Test
    fun loginWidgetCollectsEffectsOnlyAfterProvidedLifecycleOwnerStarts() {
        val controller = DeferredFlowController<BoostFlow.Response.Login, BoostLoginFlowFailure>()
        val viewModel = OwnIdLoginWidgetViewModel { _, _ -> controller }
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
            viewModel.startFlow(attachedSource(), "gated@example.com")
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
    fun loginWidgetCollectsNewEffectsUsingReplacementLifecycleOwner() {
        val controller = DeferredFlowController<BoostFlow.Response.Login, BoostLoginFlowFailure>()
        val viewModel = OwnIdLoginWidgetViewModel { _, _ -> controller }
        var lifecycleOwner by mutableStateOf<LifecycleOwner>(RecordingLifecycleOwner().created())
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

        composeRule.runOnIdle { lifecycleOwner = RecordingLifecycleOwner().started() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(WIDGET_BUTTON_TAG).performClick()
        controller.result.complete(FlowResult.Success(loginResponse("replacement@example.com")))
        composeRule.waitUntil { callbacks.isNotEmpty() }

        assertThat(callbacks).containsExactly("replacement@example.com")
    }

    @Test
    fun loginWidgetDropsPreviousBusyStateWhenViewModelChangesBelowStarted() {
        val oldController = DeferredFlowController<BoostFlow.Response.Login, BoostLoginFlowFailure>()
        val oldViewModel = OwnIdLoginWidgetViewModel { _, _ -> oldController }
        val replacementViewModel = OwnIdLoginWidgetViewModel { _, _ -> DeferredFlowController() }
        val lifecycleOwner = RecordingLifecycleOwner().started()
        var viewModel by mutableStateOf(oldViewModel)

        composeRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                OwnIdLoginWidget(
                    onLogin = {},
                    viewModel = viewModel,
                    widgetStrings = BoostWidgetStrings.DEFAULT,
                    iconButton = taggedIconButton(),
                    orText = null
                )
            }
        }

        composeRule.onNodeWithTag(WIDGET_BUTTON_TAG).performClick()
        composeRule.waitUntil { oldViewModel.uiState.value.isRunning }
        composeRule.onNodeWithTag(WIDGET_BUTTON_TAG).assertIsNotEnabled()

        composeRule.runOnIdle {
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            viewModel = replacementViewModel
        }
        composeRule.waitForIdle()

        assertThat(lifecycleOwner.lifecycle.currentState).isEqualTo(Lifecycle.State.CREATED)
        composeRule.onNodeWithTag(WIDGET_BUTTON_TAG).assertIsEnabled()
    }

    @Test
    fun createPasskeyWidgetDeliversCreatePasskeyEffectToLatestCallback() {
        val controller = DeferredFlowController<BoostFlow.Response, BoostCreatePasskeyFlowFailure>()
        var starts = 0
        val viewModel = OwnIdCreatePasskeyWidgetViewModel { _, _ ->
            starts++
            controller
        }
        val callbacks = mutableListOf<String>()
        var instanceName by mutableStateOf(InstanceName("provided-create-passkey-a"))
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
                instanceName = instanceName,
                widgetStrings = BoostWidgetStrings.DEFAULT,
                iconButton = taggedIconButton(),
                orText = null
            )
        }

        composeRule.onNodeWithTag(WIDGET_BUTTON_TAG).performClick()
        composeRule.runOnIdle {
            instanceName = InstanceName("provided-create-passkey-b")
            onNewPasskeyCallback = { response -> callbacks += "updated:${response.loginId.id}" }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(WIDGET_BUTTON_TAG).assertIsNotEnabled()
        assertThat(viewModel.uiState.value.isRunning).isTrue()
        assertThat(starts).isEqualTo(1)
        controller.result.complete(FlowResult.Success(createPasskeyResponse()))

        composeRule.waitUntil { callbacks.isNotEmpty() }

        assertThat(callbacks).containsExactly("updated:new@example.com")
    }

    @Test
    fun createPasskeyWidgetSwitchesEffectsToReplacementProvidedViewModel() {
        val oldController = DeferredFlowController<BoostFlow.Response, BoostCreatePasskeyFlowFailure>()
        val newController = DeferredFlowController<BoostFlow.Response, BoostCreatePasskeyFlowFailure>()
        val oldViewModel = OwnIdCreatePasskeyWidgetViewModel { _, _ -> oldController }
        val newViewModel = OwnIdCreatePasskeyWidgetViewModel { _, _ -> newController }
        var viewModel by mutableStateOf(oldViewModel)
        var onNewPasskeyCallback by mutableStateOf<(BoostFlow.Response.CreatePasskey) -> Unit>(
            { response -> error("Old callback must not receive ${response.loginId.id}") }
        )
        val callbacks = mutableListOf<String>()

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

        composeRule.onNodeWithTag(WIDGET_BUTTON_TAG).performClick()
        composeRule.waitUntil { oldViewModel.uiState.value.isRunning }
        composeRule.runOnIdle {
            viewModel = newViewModel
            onNewPasskeyCallback = { response -> callbacks += "new:${response.loginId.id}" }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(WIDGET_BUTTON_TAG).assertIsEnabled()

        oldController.result.complete(FlowResult.Success(createPasskeyResponse()))
        composeRule.waitUntil { oldViewModel.uiState.value.showCheckmark }
        assertThat(callbacks).isEmpty()

        composeRule.onNodeWithTag(WIDGET_BUTTON_TAG).performClick()
        composeRule.waitUntil { newViewModel.uiState.value.isRunning }
        newController.result.complete(FlowResult.Success(createPasskeyResponse()))
        composeRule.waitUntil { callbacks.isNotEmpty() }
        assertThat(callbacks).containsExactly("new:new@example.com")
    }

    @Test
    fun createPasskeyWidgetReflectsBusyAndCompletionStateAndResetsWhenLoginIdChanges() {
        val firstController = DeferredFlowController<BoostFlow.Response, BoostCreatePasskeyFlowFailure>()
        val contexts = mutableListOf<BoostFlowContext>()
        val viewModel = OwnIdCreatePasskeyWidgetViewModel { _, context ->
            contexts += context
            firstController
        }
        val callbacks = mutableListOf<String>()
        var loginId by mutableStateOf("new@example.com")

        composeRule.setContent {
            OwnIdCreatePasskeyWidget(
                onLogin = {},
                onNewPasskey = { response -> callbacks += "passkey:${response.loginId.id}" },
                onReset = { callbacks += "reset" },
                viewModel = viewModel,
                loginId = loginId,
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
            loginId = "other@example.com"
        }
        composeRule.waitForIdle()
        composeRule.waitUntil { callbacks.size == 2 }
        assertThat(callbacks).containsExactly("passkey:new@example.com", "reset").inOrder()
        composeRule.onNodeWithTag(WIDGET_CHECKMARK_TAG).assertDoesNotExist()

        composeRule.runOnIdle {
            loginId = "new@example.com"
        }
        composeRule.waitForIdle()
        composeRule.waitUntil { callbacks.size == 3 }
        assertThat(callbacks).containsExactly("passkey:new@example.com", "reset", "passkey:new@example.com").inOrder()
        composeRule.onNodeWithTag(WIDGET_CHECKMARK_TAG).assertExists()
    }

    @Test
    fun createPasskeyWidgetDropsPreviousCheckmarkWhenViewModelChangesBelowStarted() {
        val oldController = DeferredFlowController<BoostFlow.Response, BoostCreatePasskeyFlowFailure>()
        val oldViewModel = OwnIdCreatePasskeyWidgetViewModel { _, _ -> oldController }
        val replacementViewModel = OwnIdCreatePasskeyWidgetViewModel { _, _ -> DeferredFlowController() }
        val lifecycleOwner = RecordingLifecycleOwner().started()
        var viewModel by mutableStateOf(oldViewModel)

        composeRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                OwnIdCreatePasskeyWidget(
                    onLogin = {},
                    onNewPasskey = {},
                    onReset = {},
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
        }

        composeRule.onNodeWithTag(WIDGET_BUTTON_TAG).performClick()
        composeRule.waitUntil { oldViewModel.uiState.value.isRunning }
        oldController.result.complete(FlowResult.Success(createPasskeyResponse()))
        composeRule.waitUntil { oldViewModel.uiState.value.showCheckmark }
        composeRule.onNodeWithTag(WIDGET_CHECKMARK_TAG).assertExists()

        composeRule.runOnIdle {
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            viewModel = replacementViewModel
        }
        composeRule.waitForIdle()

        assertThat(lifecycleOwner.lifecycle.currentState).isEqualTo(Lifecycle.State.CREATED)
        composeRule.onNodeWithTag(WIDGET_CHECKMARK_TAG).assertDoesNotExist()
    }

    @Test
    fun createPasskeyFlowResultWinsWhenFormLoginIdChangesWhileRunning() {
        val controller = DeferredFlowController<BoostFlow.Response, BoostCreatePasskeyFlowFailure>()
        var starts = 0
        val viewModel = OwnIdCreatePasskeyWidgetViewModel { _, _ ->
            starts++
            controller
        }
        val callbacks = mutableListOf<String>()
        var loginId by mutableStateOf("initial@example.com")

        composeRule.setContent {
            OwnIdCreatePasskeyWidget(
                onLogin = {},
                onNewPasskey = { response ->
                    callbacks += "passkey:${response.loginId.id}"
                    loginId = response.loginId.id
                },
                onReset = { callbacks += "reset" },
                viewModel = viewModel,
                loginId = loginId,
                widgetStrings = BoostWidgetStrings.DEFAULT,
                iconButton = taggedIconButton(),
                checkmark = {
                    androidx.compose.material3.Text("Done", modifier = Modifier.testTag(WIDGET_CHECKMARK_TAG))
                },
                orText = null
            )
        }

        composeRule.onNodeWithTag(WIDGET_BUTTON_TAG).performClick()
        composeRule.waitUntil { viewModel.uiState.value.isRunning }
        composeRule.onNodeWithTag(WIDGET_CHECKMARK_TAG).assertDoesNotExist()
        assertThat(starts).isEqualTo(1)

        composeRule.runOnIdle { loginId = "edited@example.com" }
        composeRule.waitForIdle()
        assertThat(loginId).isEqualTo("edited@example.com")

        controller.result.complete(FlowResult.Success(createPasskeyResponse()))
        composeRule.waitUntil { callbacks.isNotEmpty() }
        composeRule.waitForIdle()

        assertThat(callbacks).containsExactly("passkey:new@example.com")
        assertThat(loginId).isEqualTo("new@example.com")
        assertThat(starts).isEqualTo(1)
        composeRule.onNodeWithTag(WIDGET_CHECKMARK_TAG).assertExists()
    }

    @Test
    fun createPasskeyWidgetKeysDefaultStateByWidgetIdAndInstance() {
        val keyPrefix = "widget-runtime-${UUID.randomUUID()}"
        val firstInstance = createInitializedInstance("$keyPrefix:a")
        val secondInstance = createInitializedInstance("$keyPrefix:a:b", appId = "App456")
        val thirdInstance = createInitializedInstance("$keyPrefix:a%3Ab", appId = "App789")
        val firstFlow = RecordingCreatePasskeyFlow()
        val secondFlow = RecordingCreatePasskeyFlow()
        val thirdFlow = RecordingCreatePasskeyFlow()
        OwnId.getInstanceContainer(firstInstance)!!.register<BoostFlow.CreatePasskey>(firstFlow)
        OwnId.getInstanceContainer(secondInstance)!!.register<BoostFlow.CreatePasskey>(secondFlow)
        OwnId.getInstanceContainer(thirdInstance)!!.register<BoostFlow.CreatePasskey>(thirdFlow)
        var widgetId by mutableStateOf("stable-id")
        var instanceName by mutableStateOf(firstInstance)
        val callbacks = mutableListOf<BoostFlow.Response.CreatePasskey>()

        composeRule.setContent {
            OwnIdCreatePasskeyWidget(
                onLogin = {},
                onNewPasskey = callbacks::add,
                onReset = {},
                loginId = "new@example.com",
                widgetId = widgetId,
                instanceName = instanceName,
                widgetStrings = BoostWidgetStrings.DEFAULT,
                iconButton = taggedIconButton(),
                checkmark = {
                    androidx.compose.material3.Text("Done", modifier = Modifier.testTag(WIDGET_CHECKMARK_TAG))
                },
                orText = null
            )
        }

        composeRule.onNodeWithTag(WIDGET_BUTTON_TAG).performClick()
        composeRule.waitUntil { firstFlow.controllers.size == 1 }
        val response = createPasskeyResponse()
        firstFlow.controllers.single().result.complete(FlowResult.Success(response))
        composeRule.waitUntil { callbacks.size == 1 }
        composeRule.onNodeWithTag(WIDGET_CHECKMARK_TAG).assertExists()

        composeRule.runOnIdle { widgetId = "b:c" }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(WIDGET_CHECKMARK_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(WIDGET_BUTTON_TAG).assertIsEnabled().performClick()
        composeRule.waitUntil { firstFlow.controllers.size == 2 }

        composeRule.runOnIdle {
            instanceName = secondInstance
            widgetId = "c"
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(WIDGET_BUTTON_TAG).assertIsEnabled().performClick()
        composeRule.waitUntil { secondFlow.controllers.size == 1 }

        composeRule.runOnIdle { instanceName = thirdInstance }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(WIDGET_BUTTON_TAG).assertIsEnabled().performClick()
        composeRule.waitUntil { thirdFlow.controllers.size == 1 }

        assertThat(firstFlow.controllers).hasSize(2)
        assertThat(secondFlow.controllers).hasSize(1)
        assertThat(callbacks).containsExactly(response)
    }

    @Test
    fun createPasskeyWidgetKeepsDefaultViewModelAcrossTemporaryRemovalWithStableWidgetId() {
        val instanceName = createInitializedInstance()
        val flow = RecordingCreatePasskeyFlow()
        OwnId.getInstanceContainer(instanceName)!!.register<BoostFlow.CreatePasskey>(flow)
        var showWidget by mutableStateOf(true)
        val callbacks = mutableListOf<String>()

        composeRule.setContent {
            if (showWidget) {
                OwnIdCreatePasskeyWidget(
                    onLogin = {},
                    onNewPasskey = { callbacks += it.loginId.id },
                    onReset = {},
                    loginId = "new@example.com",
                    widgetId = "retained-create-passkey",
                    instanceName = instanceName,
                    widgetStrings = BoostWidgetStrings.DEFAULT,
                    iconButton = taggedIconButton(),
                    checkmark = {
                        androidx.compose.material3.Text("Done", modifier = Modifier.testTag(WIDGET_CHECKMARK_TAG))
                    },
                    orText = null
                )
            }
        }

        composeRule.onNodeWithTag(WIDGET_BUTTON_TAG).performClick()
        composeRule.waitUntil { flow.controllers.size == 1 }
        composeRule.onNodeWithTag(WIDGET_BUTTON_TAG).assertIsNotEnabled()

        composeRule.runOnIdle { showWidget = false }
        composeRule.waitForIdle()
        assertThat(flow.controllers.single().abortReasons).isEmpty()

        composeRule.runOnIdle { showWidget = true }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(WIDGET_BUTTON_TAG).assertIsNotEnabled()
        assertThat(flow.controllers).hasSize(1)

        flow.controllers.single().result.complete(FlowResult.Success(createPasskeyResponse()))
        composeRule.waitUntil { callbacks.isNotEmpty() }
        assertThat(callbacks).containsExactly("new@example.com")
        composeRule.onNodeWithTag(WIDGET_CHECKMARK_TAG).assertExists()
    }

    @Test
    fun separateCustomWidgetViewModelsUseClickedActivitiesAndDisposeRemovedSource() {
        val firstActivity = mockk<Activity>()
        val secondActivity = mockk<Activity>()
        val startedWith = mutableListOf<Activity>()
        val controllers = mutableListOf<DeferredFlowController<BoostFlow.Response.Login, BoostLoginFlowFailure>>()
        var showFirst by mutableStateOf(true)
        var firstAction: (() -> Unit)? = null
        var firstObservedActivity: Activity? = null
        var secondObservedActivity: Activity? = null
        lateinit var firstViewModel: OwnIdLoginWidgetViewModel
        val factory = OwnIdLoginWidgetViewModel.factory(boostLoginFlowProvider = { activity, _ ->
            startedWith += activity
            DeferredFlowController<BoostFlow.Response.Login, BoostLoginFlowFailure>().also(controllers::add)
        })

        composeRule.setContent {
            val firstWidgetViewModel: OwnIdLoginWidgetViewModel = viewModel(key = "first-widget", factory = factory)
            val secondWidgetViewModel: OwnIdLoginWidgetViewModel = viewModel(key = "second-widget", factory = factory)
            SideEffect { firstViewModel = firstWidgetViewModel }
            Column {
                if (showFirst) CompositionLocalProvider(LocalActivity provides firstActivity) {
                    firstObservedActivity = LocalActivity.current
                    OwnIdLoginWidget(
                        onLogin = {},
                        viewModel = firstWidgetViewModel,
                        widgetStrings = BoostWidgetStrings.DEFAULT,
                        iconButton = taggedIconButton("first-widget") { firstAction = it },
                        orText = null
                    )
                }
                CompositionLocalProvider(LocalActivity provides secondActivity) {
                    secondObservedActivity = LocalActivity.current
                    OwnIdLoginWidget(
                        onLogin = {},
                        viewModel = secondWidgetViewModel,
                        widgetStrings = BoostWidgetStrings.DEFAULT,
                        iconButton = taggedIconButton("second-widget"),
                        orText = null
                    )
                }
            }
        }

        assertThat(firstObservedActivity).isSameInstanceAs(firstActivity)
        assertThat(secondObservedActivity).isSameInstanceAs(secondActivity)
        composeRule.onNodeWithTag("first-widget").performClick()
        composeRule.waitUntil { controllers.size == 1 }
        assertThat(startedWith).containsExactly(firstActivity)
        controllers.single().result.complete(FlowResult.Canceled(Reason.UserClose()))
        composeRule.waitUntil { !firstViewModel.uiState.value.isRunning }

        composeRule.runOnIdle {
            assertThat(firstAction).isNotNull()
            showFirst = false
        }
        composeRule.waitForIdle()
        ShadowSystemClock.advanceBy(301, TimeUnit.MILLISECONDS)
        composeRule.runOnIdle { firstAction!!.invoke() }
        composeRule.waitForIdle()
        assertThat(startedWith).containsExactly(firstActivity)

        composeRule.onNodeWithTag("second-widget").performClick()
        composeRule.waitUntil { startedWith.size == 2 }
        assertThat(startedWith).containsExactly(firstActivity, secondActivity).inOrder()
    }

    @Test
    fun widgetTapUsesReboundActivityAndRejectsActionRetainedBeforeRebind() {
        val firstActivity = mockk<Activity>()
        val secondActivity = mockk<Activity>()
        var activity by mutableStateOf<Activity?>(firstActivity)
        val startedWith = mutableListOf<Activity>()
        val controllers = mutableListOf<DeferredFlowController<BoostFlow.Response.Login, BoostLoginFlowFailure>>()
        val errors = mutableListOf<BoostLoginFlowFailure>()
        val viewModel = OwnIdLoginWidgetViewModel { host, _ ->
            startedWith += host
            DeferredFlowController<BoostFlow.Response.Login, BoostLoginFlowFailure>().also(controllers::add)
        }
        var firstAction: (() -> Unit)? = null

        composeRule.setContent {
            CompositionLocalProvider(LocalActivity provides activity) {
                OwnIdLoginWidget(
                    onLogin = {},
                    onError = errors::add,
                    viewModel = viewModel,
                    widgetStrings = BoostWidgetStrings.DEFAULT,
                    iconButton = taggedIconButton(captureOnClick = { if (firstAction == null) firstAction = it }),
                    orText = null
                )
            }
        }

        composeRule.onNodeWithTag(WIDGET_BUTTON_TAG).performClick()
        composeRule.waitUntil { startedWith.size == 1 }
        assertThat(startedWith).containsExactly(firstActivity)
        controllers.single().result.complete(FlowResult.Canceled(Reason.UserClose()))
        composeRule.waitUntil { !viewModel.uiState.value.isRunning }

        composeRule.runOnIdle {
            activity = secondActivity
        }
        composeRule.waitForIdle()
        ShadowSystemClock.advanceBy(301, TimeUnit.MILLISECONDS)
        composeRule.runOnIdle { firstAction!!.invoke() }
        composeRule.waitUntil { errors.isNotEmpty() }
        assertThat(startedWith).containsExactly(firstActivity)
        val failure = errors.single() as BoostLoginFlowFailure.Unexpected
        assertThat(failure.errorCode).isEqualTo(ErrorCode.IntegrationError)
        assertThat(failure.error).isInstanceOf(UiContextResolutionException::class.java)
        assertThat((failure.error as UiContextResolutionException).reason)
            .isEqualTo(UiContextResolutionException.Reason.Unavailable)

        ShadowSystemClock.advanceBy(301, TimeUnit.MILLISECONDS)
        composeRule.onNodeWithTag(WIDGET_BUTTON_TAG).performClick()
        composeRule.waitUntil { startedWith.size == 2 }
        assertThat(startedWith).containsExactly(firstActivity, secondActivity).inOrder()
    }

    @Test
    fun defaultLoginFactoryUsesNamedInstanceAndWidgetActivityInChildScope() {
        val instanceName = createInitializedInstance()
        val rootProvider = UiContextProvider { error("Root UI context must not be used") }
        val container = OwnId.getInstanceContainer(instanceName)!!
        container.register<UiContextProvider>(rootProvider)
        val resolutions = mutableListOf<UiContextProvider.Result>()
        container.registerFactory<BoostFlow.Login>(UiContextProvider::class.java) {
            val provider = getOrThrow<UiContextProvider>()
            object : BoostFlow.Login {
                override fun start(context: BoostFlowContext): BoostFlow.Controller<BoostFlow.Response.Login, BoostLoginFlowFailure> {
                    resolutions += provider.current()
                    return DeferredFlowController()
                }
            }
        }
        var widgetActivity: Activity? = null

        composeRule.setContent {
            widgetActivity = LocalActivity.current
            OwnIdLoginWidget(
                onLogin = {},
                instanceName = instanceName,
                widgetStrings = BoostWidgetStrings.DEFAULT,
                iconButton = taggedIconButton(),
                orText = null
            )
        }

        composeRule.onNodeWithTag(WIDGET_BUTTON_TAG).performClick()
        composeRule.waitUntil { resolutions.isNotEmpty() }
        assertThat((resolutions.single() as UiContextProvider.Result.Success).activity).isSameInstanceAs(widgetActivity)
        assertThat(container.getOrThrow<UiContextProvider>()).isSameInstanceAs(rootProvider)
    }

    @Test
    fun defaultCreatePasskeyFactoryUsesNamedInstanceAndWidgetActivityInChildScope() {
        val instanceName = createInitializedInstance()
        val rootProvider = UiContextProvider { error("Root UI context must not be used") }
        val container = OwnId.getInstanceContainer(instanceName)!!
        container.register<UiContextProvider>(rootProvider)
        val resolutions = mutableListOf<UiContextProvider.Result>()
        container.registerFactory<BoostFlow.CreatePasskey>(UiContextProvider::class.java) {
            val provider = getOrThrow<UiContextProvider>()
            object : BoostFlow.CreatePasskey {
                override fun start(context: BoostFlowContext): BoostFlow.Controller<BoostFlow.Response, BoostCreatePasskeyFlowFailure> {
                    resolutions += provider.current()
                    return DeferredFlowController()
                }
            }
        }
        var widgetActivity: Activity? = null

        composeRule.setContent {
            widgetActivity = LocalActivity.current
            OwnIdCreatePasskeyWidget(
                onLogin = {},
                onNewPasskey = {},
                onReset = {},
                instanceName = instanceName,
                widgetStrings = BoostWidgetStrings.DEFAULT,
                iconButton = taggedIconButton(),
                orText = null
            )
        }

        composeRule.onNodeWithTag(WIDGET_BUTTON_TAG).performClick()
        composeRule.waitUntil { resolutions.isNotEmpty() }
        assertThat((resolutions.single() as UiContextProvider.Result.Success).activity).isSameInstanceAs(widgetActivity)
        assertThat(container.getOrThrow<UiContextProvider>()).isSameInstanceAs(rootProvider)
    }

    private fun createInitializedInstance(
        value: String = "widget-runtime-${UUID.randomUUID()}",
        appId: String = "App123"
    ): InstanceName {
        val instanceName = InstanceName(value)
        createdInstances += instanceName
        OwnId.initialize(instanceName) {
            applicationContext = app
            this.appId = appId
        }
        return instanceName
    }

    private fun attachedSource(): WidgetUiActivitySource =
        WidgetUiActivitySource(attachedActivity, attachedView)

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

    private fun taggedIconButton(
        tag: String = WIDGET_BUTTON_TAG,
        captureOnClick: (((() -> Unit) -> Unit))? = null
    ): @Composable (onClick: () -> Unit, contentDescription: String, isBusy: Boolean, enabled: Boolean) -> Unit =
        { onClick, _, isBusy, enabled ->
            SideEffect {
                check(!isBusy || !enabled)
                captureOnClick?.invoke(onClick)
            }
            androidx.compose.material3.Button(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.testTag(tag)
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

    private class RecordingCreatePasskeyFlow : BoostFlow.CreatePasskey {
        val controllers = mutableListOf<DeferredFlowController<BoostFlow.Response, BoostCreatePasskeyFlowFailure>>()

        override fun start(context: BoostFlowContext): BoostFlow.Controller<BoostFlow.Response, BoostCreatePasskeyFlowFailure> =
            DeferredFlowController<BoostFlow.Response, BoostCreatePasskeyFlowFailure>().also(controllers::add)
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
    }
}
