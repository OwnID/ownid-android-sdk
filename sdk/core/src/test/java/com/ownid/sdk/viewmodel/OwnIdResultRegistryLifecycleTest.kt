package com.ownid.sdk.viewmodel

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.ViewModelProvider
import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.Configuration
import com.ownid.sdk.InstanceName
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnIdCallback
import com.ownid.sdk.OwnIdCoreImpl
import com.ownid.sdk.OwnIdInstance
import com.ownid.sdk.OwnIdResponse
import com.ownid.sdk.TestDataCore
import com.ownid.sdk.exception.OwnIdFlowCanceled
import com.ownid.sdk.getOwnIdViewModel
import com.ownid.sdk.ownIdViewModel
import com.ownid.sdk.event.OwnIdLoginFlow
import com.ownid.sdk.event.OwnIdRegisterFlow
import com.ownid.sdk.internal.component.config.OwnIdConfigurationService
import com.ownid.sdk.internal.component.events.Metric
import com.ownid.sdk.internal.component.events.OwnIdInternalEventsService
import com.ownid.sdk.internal.feature.OwnIdActivity
import com.ownid.sdk.internal.feature.nativeflow.OwnIdNativeFlowError
import com.ownid.sdk.internal.feature.nativeflow.OwnIdNativeFlowType
import com.ownid.sdk.internal.feature.nativeflow.parcelCopy
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@OptIn(InternalOwnIdAPI::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
public class OwnIdResultRegistryLifecycleTest {

    private val controllers: MutableList<ActivityController<*>> = mutableListOf()

    @After
    public fun tearDown() {
        controllers.asReversed().forEach { controller -> runCatching { controller.close() } }
    }

    @Test
    public fun loginViewModelsRouteResultsIndependently() {
        val controller = createActivity()
        val activity = controller.get()
        val factory = OwnIdLoginViewModel.Factory(createOwnIdInstance())
        val first = activity.viewModel("login.first", factory)
        val second = activity.viewModel("login.second", factory)
        val registry = RecordingActivityResultRegistry()

        first.createResultLauncher(registry, activity)
        second.createResultLauncher(registry, activity)
        controller.start().resume()

        first.auth(activity, "first@example.com")
        second.auth(activity, "second@example.com")

        assertThat(registry.requestCodes).hasSize(2)
        assertThat(registry.requestCodes[0]).isNotEqualTo(registry.requestCodes[1])

        assertThat(registry.dispatchCanceledAt(1)).isTrue()
        assertThat(first.flowEvents.value).isInstanceOf(OwnIdLoginFlow.Busy::class.java)
        assertThat(first.busyFlow.value).isTrue()
        assertThat(second.flowEvents.value).isInstanceOf(OwnIdLoginFlow.Error::class.java)
        assertThat(second.busyFlow.value).isFalse()

        assertThat(registry.dispatchCanceledAt(0)).isTrue()
        assertThat(first.flowEvents.value).isInstanceOf(OwnIdLoginFlow.Error::class.java)
        assertThat(first.busyFlow.value).isFalse()
    }

    @Test
    public fun registerViewModelsRouteResultsIndependently() {
        val controller = createActivity()
        val activity = controller.get()
        val factory = OwnIdRegisterViewModel.Factory(createOwnIdInstance())
        val first = activity.viewModel("register.first", factory)
        val second = activity.viewModel("register.second", factory)
        val registry = RecordingActivityResultRegistry()

        first.createResultLauncher(registry, activity)
        second.createResultLauncher(registry, activity)
        controller.start().resume()

        first.startRegistration(activity, "first@example.com")
        second.startRegistration(activity, "second@example.com")

        assertThat(registry.requestCodes).hasSize(2)
        assertThat(registry.requestCodes[0]).isNotEqualTo(registry.requestCodes[1])

        assertThat(registry.dispatchCanceledAt(1)).isTrue()
        assertThat(first.flowEvents.value).isInstanceOf(OwnIdRegisterFlow.Busy::class.java)
        assertThat(first.busyFlow.value).isTrue()
        assertThat(second.flowEvents.value).isInstanceOf(OwnIdRegisterFlow.Error::class.java)
        assertThat(second.busyFlow.value).isFalse()

        assertThat(registry.dispatchCanceledAt(0)).isTrue()
        assertThat(first.flowEvents.value).isInstanceOf(OwnIdRegisterFlow.Error::class.java)
        assertThat(first.busyFlow.value).isFalse()
    }

    @Test
    public fun enrollmentViewModelsUseUniqueResultRegistryKeys() {
        val activity = createActivity().get()
        val factory = OwnIdEnrollmentViewModel.Factory(createOwnIdInstance())
        val first = activity.viewModel("enrollment.first", factory)
        val second = activity.viewModel("enrollment.second", factory)
        val registry = RecordingActivityResultRegistry()

        first.createResultLauncher(registry)
        second.createResultLauncher(registry)

        assertThat(registry.registeredKeys()).hasSize(2)
        assertThat(registry.registeredKeys().distinct()).hasSize(2)
    }

    @Test
    public fun socialViewModelsRouteAndUnregisterResultsIndependently() {
        val activity = createActivity().get()
        val factory = OwnIdSocialViewModel.Factory(createOwnIdInstance())
        val first = activity.viewModel("social.first", factory)
        val second = activity.viewModel("social.second", factory)
        val registry = RecordingActivityResultRegistry()

        first.createResultLauncher(registry)
        val firstRegistryKey = registry.registeredKeys().single()
        second.createResultLauncher(registry)
        val secondRegistryKey = registry.registeredKeys().single { it != firstRegistryKey }

        first.launchForTest()
        second.launchForTest()

        assertThat(registry.registeredKeys()).containsExactly(firstRegistryKey, secondRegistryKey)
        assertThat(registry.requestCodes).hasSize(2)
        assertThat(registry.requestCodes[0]).isNotEqualTo(registry.requestCodes[1])

        assertThat(registry.dispatchCanceledAt(1)).isTrue()
        assertThat(first.socialResultFlow.value).isNull()
        assertThat(second.socialResultFlow.value).isInstanceOf(OwnIdSocialViewModel.State.Error::class.java)

        assertThat(registry.dispatchCanceledAt(0)).isTrue()
        assertThat(first.socialResultFlow.value).isInstanceOf(OwnIdSocialViewModel.State.Error::class.java)

        first.unregisterResultLauncher()
        assertThat(registry.registeredKeys()).containsExactly(secondRegistryKey)
    }

    @Test
    public fun socialResultRegistryKeyRestoresWithSameViewModelProviderKey() {
        val factory = OwnIdSocialViewModel.Factory(createOwnIdInstance())
        val originalController = createActivity()
        val original = originalController.get().viewModel("social.operation", factory)
        val originalRegistry = RecordingActivityResultRegistry()
        original.createResultLauncher(originalRegistry)
        val originalRegistryKey = originalRegistry.registeredKeys().single()
        val ownerState = savedOwnerState(originalController)
        originalController.destroy()

        val restoredController = createActivity(ownerState)
        val restored = restoredController.get().viewModel("social.operation", factory)
        val restoredRegistry = RecordingActivityResultRegistry()
        restored.createResultLauncher(restoredRegistry)

        assertThat(restored).isNotSameInstanceAs(original)
        assertThat(restoredRegistry.registeredKeys().single()).isEqualTo(originalRegistryKey)
    }

    @Test
    public fun enrollmentViewModelKeepsResultRegistryKeyAcrossConfigurationRecreation() {
        val controller = createActivity()
        val factory = OwnIdEnrollmentViewModel.Factory(createOwnIdInstance())
        val original = controller.get().viewModel("enrollment.operation", factory)
        val originalRegistry = RecordingActivityResultRegistry()
        original.createResultLauncher(originalRegistry)
        val originalRegistryKey = originalRegistry.registeredKeys().single()

        controller.start().resume().recreate()

        val restored = controller.get().viewModel("enrollment.operation", factory)
        val restoredRegistry = RecordingActivityResultRegistry()
        restored.createResultLauncher(restoredRegistry)

        assertThat(restored).isSameInstanceAs(original)
        assertThat(restoredRegistry.registeredKeys().single()).isEqualTo(originalRegistryKey)
    }

    @Test
    public fun enrollmentResultRegistryKeyRestoresWithSameViewModelProviderKey() {
        val factory = OwnIdEnrollmentViewModel.Factory(createOwnIdInstance())
        val originalController = createActivity()
        val original = originalController.get().viewModel("enrollment.operation", factory)
        val originalRegistry = RecordingActivityResultRegistry()
        original.createResultLauncher(originalRegistry)
        val originalRegistryKey = originalRegistry.registeredKeys().single()
        val ownerState = savedOwnerState(originalController)
        originalController.destroy()

        val restoredController = createActivity(ownerState)
        val restored = restoredController.get().viewModel("enrollment.operation", factory)
        val restoredRegistry = RecordingActivityResultRegistry()
        restored.createResultLauncher(restoredRegistry)

        assertThat(restored).isNotSameInstanceAs(original)
        assertThat(restoredRegistry.registeredKeys().single()).isEqualTo(originalRegistryKey)
    }

    @Test
    public fun freshEnrollmentViewModelDoesNotReuseOldPendingResultKey() {
        val factory = OwnIdEnrollmentViewModel.Factory(createOwnIdInstance())
        val originalActivity = createActivity().get()
        val original = originalActivity.viewModel("enrollment.old", factory)
        val originalRegistry = RecordingActivityResultRegistry()
        original.createResultLauncher(originalRegistry, originalActivity)
        val originalRegistryKey = originalRegistry.registeredKeys().single()
        val originalRequestCode = originalRegistry.registeredRequestCodes().single()
        assertThat(originalRegistry.dispatchCanceledRequest(originalRequestCode)).isTrue()
        val registryState = originalRegistry.savedState()

        val freshController = createActivity()
        val freshActivity = freshController.get()
        val restoredRegistry = RecordingActivityResultRegistry().apply { onRestoreInstanceState(registryState) }
        val fresh = freshActivity.viewModel("enrollment.fresh", factory)
        fresh.createResultLauncher(restoredRegistry, freshActivity)
        freshController.start().resume()

        val freshRegistryKey = restoredRegistry.registeredKeys().single { it != originalRegistryKey }
        assertThat(freshRegistryKey).isNotEqualTo(originalRegistryKey)
        assertThat(fresh.enrollmentResultFlow.value).isNull()
        assertThat(restoredRegistry.pendingResultKeys()).containsExactly(originalRegistryKey)
    }

    @Test
    public fun unregisteringOneLoginViewModelDoesNotAffectAnother() {
        val controller = createActivity()
        val activity = controller.get()
        val factory = OwnIdLoginViewModel.Factory(createOwnIdInstance())
        val first = activity.viewModel("login.first", factory)
        val second = activity.viewModel("login.second", factory)
        val registry = RecordingActivityResultRegistry()

        first.createResultLauncher(registry, activity)
        second.createResultLauncher(registry, activity)
        controller.start().resume()
        first.auth(activity, "first@example.com")
        second.auth(activity, "second@example.com")

        first.unregisterResultLauncher()

        assertThat(registry.dispatchCanceledAt(0)).isTrue()
        assertThat(first.flowEvents.value).isInstanceOf(OwnIdLoginFlow.Busy::class.java)
        assertThat(first.busyFlow.value).isTrue()
        assertThat(registry.dispatchCanceledAt(1)).isTrue()
        assertThat(second.flowEvents.value).isInstanceOf(OwnIdLoginFlow.Error::class.java)
        assertThat(second.busyFlow.value).isFalse()
    }

    @Test
    public fun kotlinActivityAcquisitionDeliversInFlightResultAfterRestoration() {
        val eventsService = mockk<OwnIdInternalEventsService>(relaxed = true)
        KotlinLoginViewModelActivity.ownIdInstance = createOwnIdInstance(eventsService)
        val originalController = createKotlinActivity()
        val originalActivity = originalController.get()
        originalController.start().resume()
        originalActivity.loginViewModel.auth(originalActivity, "test@example.com")

        val requestCode = requireNotNull(shadowOf(originalActivity).nextStartedActivityForResult).requestCode
        val ownerState = savedOwnerState(originalController)
        val original = originalActivity.loginViewModel
        originalController.destroy()

        val restoredController = createKotlinActivity(ownerState)
        val restoredActivity = restoredController.get()
        val restored = restoredActivity.loginViewModel
        restoredController.start().resume()

        assertThat(restored).isNotSameInstanceAs(original)
        assertThat(restoredActivity.activityResultRegistry.dispatchInterrupted(requestCode)).isTrue()
        assertThat(restored.flowEvents.value).isInstanceOf(OwnIdLoginFlow.Error::class.java)
        assertThat(restored.busyFlow.value).isFalse()
        verifyInterruptedMetric(eventsService, OwnIdNativeFlowType.LOGIN)
    }

    @Test
    public fun javaActivityAcquisitionDeliversInFlightResultAfterRestoration() {
        val eventsService = mockk<OwnIdInternalEventsService>(relaxed = true)
        JavaRegisterViewModelActivity.ownIdInstance = createOwnIdInstance(eventsService)
        val originalController = createJavaActivity()
        val originalActivity = originalController.get()
        originalController.start().resume()
        originalActivity.registerViewModel.startRegistration(originalActivity, "test@example.com")

        val requestCode = requireNotNull(shadowOf(originalActivity).nextStartedActivityForResult).requestCode
        val ownerState = savedOwnerState(originalController)
        val original = originalActivity.registerViewModel
        originalController.destroy()

        val restoredController = createJavaActivity(ownerState)
        val restoredActivity = restoredController.get()
        val restored = restoredActivity.registerViewModel
        restoredController.start().resume()

        assertThat(restored).isNotSameInstanceAs(original)
        assertThat(restoredActivity.activityResultRegistry.dispatchInterrupted(requestCode)).isTrue()
        assertThat(restored.flowEvents.value).isInstanceOf(OwnIdRegisterFlow.Error::class.java)
        assertThat(restored.busyFlow.value).isFalse()
        verifyInterruptedMetric(eventsService, OwnIdNativeFlowType.REGISTER)
    }

    private fun createActivity(savedState: Bundle? = null): ActivityController<ComponentActivity> =
        Robolectric.buildActivity(ComponentActivity::class.java).create(savedState).also(controllers::add)

    private fun createKotlinActivity(savedState: Bundle? = null): ActivityController<KotlinLoginViewModelActivity> =
        Robolectric.buildActivity(KotlinLoginViewModelActivity::class.java).create(savedState).also(controllers::add)

    private fun createJavaActivity(savedState: Bundle? = null): ActivityController<JavaRegisterViewModelActivity> =
        Robolectric.buildActivity(JavaRegisterViewModelActivity::class.java).create(savedState).also(controllers::add)

    private fun <T : ComponentActivity> savedOwnerState(controller: ActivityController<T>): Bundle {
        controller.pause().stop()
        return parcelCopy(Bundle().also(controller::saveInstanceState))
    }

    private fun ActivityResultRegistry.dispatchInterrupted(requestCode: Int): Boolean = dispatchResult(
        requestCode,
        Activity.RESULT_OK,
        Intent().putExtra(
            OwnIdActivity.KEY_RESULT,
            Result.failure<OwnIdResponse>(OwnIdFlowCanceled(OwnIdFlowCanceled.RESULT_PENDING)),
        ),
    )

    private fun verifyInterruptedMetric(eventsService: OwnIdInternalEventsService, flowType: OwnIdNativeFlowType) {
        verify(exactly = 1) {
            eventsService.sendMetric(
                flowType = flowType,
                type = Metric.EventType.Track,
                action = "OwnID flow was interrupted before completion",
                metadata = any(),
                source = null,
                errorMessage = "OwnID flow was interrupted before completion",
                errorCode = OwnIdNativeFlowError.CodeLocal.FLOW_CANCELED,
            )
        }
    }

    private fun ComponentActivity.viewModel(
        key: String,
        factory: OwnIdLoginViewModel.Factory
    ): OwnIdLoginViewModel = ViewModelProvider(this, factory)[key, OwnIdLoginViewModel::class.java]

    private fun ComponentActivity.viewModel(
        key: String,
        factory: OwnIdRegisterViewModel.Factory
    ): OwnIdRegisterViewModel = ViewModelProvider(this, factory)[key, OwnIdRegisterViewModel::class.java]

    private fun ComponentActivity.viewModel(
        key: String,
        factory: OwnIdEnrollmentViewModel.Factory
    ): OwnIdEnrollmentViewModel = ViewModelProvider(this, factory)[key, OwnIdEnrollmentViewModel::class.java]

    private fun ComponentActivity.viewModel(
        key: String,
        factory: OwnIdSocialViewModel.Factory
    ): OwnIdSocialViewModel = ViewModelProvider(this, factory)[key, OwnIdSocialViewModel::class.java]

    private fun OwnIdSocialViewModel.launchForTest() {
        OwnIdBaseViewModel::class.java.getDeclaredMethod("launchActivity", Intent::class.java)
            .apply { isAccessible = true }
            .invoke(this, Intent())
    }

    private fun OwnIdRegisterViewModel.startRegistration(activity: ComponentActivity, loginId: String) {
        View(activity).also { view ->
            attachToView(view, activity) { loginId }
            view.performClick()
        }
    }

    private fun createOwnIdInstance(eventsService: OwnIdInternalEventsService? = null): OwnIdInstance {
        val configuration = Configuration(
            TestDataCore.validAppId,
            "${TestDataCore.validEnv}.",
            TestDataCore.validRegion,
            TestDataCore.validRedirectUrl,
            TestDataCore.validVersion,
            TestDataCore.validUserAgent,
            TestDataCore.validPackageName,
            TestDataCore.validHashSet
        ).apply {
            setServerConfiguration(TestDataCore.validServerConfig)
        }
        val configurationService = mockk<OwnIdConfigurationService>(relaxed = true)
        every { configurationService.ensureConfigurationSet(any()) } answers {
            firstArg<OwnIdCallback<Unit>>().invoke(Result.success(Unit))
        }
        val core = mockk<OwnIdCoreImpl>(relaxed = true) {
            every { this@mockk.configuration } returns configuration
            every { this@mockk.configurationService } returns configurationService
            every { this@mockk.instanceName } returns InstanceName("ResultRegistryTest")
            eventsService?.let { every { this@mockk.eventsService } returns it }
        }
        return mockk(relaxed = true) {
            every { ownIdCore } returns core
            every { ownIdIntegration } returns null
        }
    }

    private class RecordingActivityResultRegistry : ActivityResultRegistry() {
        val requestCodes: MutableList<Int> = mutableListOf()

        override fun <I : Any?, O : Any?> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?
        ) {
            requestCodes += requestCode
        }

        fun dispatchCanceledAt(index: Int): Boolean = dispatchCanceledRequest(requestCodes[index])

        fun dispatchCanceledRequest(requestCode: Int): Boolean = dispatchResult(requestCode, Activity.RESULT_CANCELED, null)

        fun registeredKeys(): List<String> = savedState()
            .getStringArrayList("KEY_COMPONENT_ACTIVITY_REGISTERED_KEYS")
            .orEmpty()

        fun registeredRequestCodes(): List<Int> = savedState()
            .getIntegerArrayList("KEY_COMPONENT_ACTIVITY_REGISTERED_RCS")
            .orEmpty()

        fun pendingResultKeys(): Set<String> = savedState()
            .getBundle("KEY_COMPONENT_ACTIVITY_PENDING_RESULT")
            ?.keySet()
            .orEmpty()

        fun savedState(): Bundle = Bundle().also(::onSaveInstanceState)

    }
}

@OptIn(InternalOwnIdAPI::class)
internal class KotlinLoginViewModelActivity : ComponentActivity() {
    internal val loginViewModel: OwnIdLoginViewModel by ownIdViewModel(ownIdInstance)

    internal companion object {
        lateinit var ownIdInstance: OwnIdInstance
    }
}

@OptIn(InternalOwnIdAPI::class)
internal class JavaRegisterViewModelActivity : ComponentActivity() {
    internal lateinit var registerViewModel: OwnIdRegisterViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerViewModel = getOwnIdViewModel(this, OwnIdRegisterViewModel::class.java, ownIdInstance)
    }

    internal companion object {
        lateinit var ownIdInstance: OwnIdInstance
    }
}
