package com.ownid.sdk.compose.components

import android.app.Application
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.createComposeRule
import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.OwnId
import com.ownid.sdk.di.remove
import com.ownid.sdk.di.register
import com.ownid.sdk.model.InstanceName
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.ui.capability.BoostWidgetStrings
import com.ownid.sdk.ui.capability.BoostWidgetStringsParams
import com.ownid.sdk.ui.capability.BoostWidgetStringsProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@OptIn(InternalOwnIdAPI::class)
// Covers: UI-PRESENT-300
internal class BoostWidgetStringsRuntimeContractTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val app: Application
        get() = RuntimeEnvironment.getApplication()

    private val createdInstances = mutableSetOf<InstanceName>()
    private var renderedStrings: BoostWidgetStrings? = null
    private var renderCount: Int = 0

    @After
    fun tearDown() {
        createdInstances.forEach(OwnId::destroy)
        createdInstances.clear()
    }

    @Test
    fun boostStringsRetainNonNullUpdatesAndResetAcrossInstanceLifecycle() {
        val uniqueId = UUID.randomUUID().toString().replace("-", "")
        val instanceName = InstanceName("boost-strings-$uniqueId")
        val appId = "App$uniqueId"
        val firstProvider = ControlledBoostWidgetStringsProvider()
        val secondProvider = ControlledBoostWidgetStringsProvider()
        createdInstances += instanceName

        composeRule.setContent {
            val strings = widgetStrings(instanceName)
            SideEffect {
                renderedStrings = strings
                renderCount += 1
            }
        }

        assertRendered("no instance", BoostWidgetStrings.DEFAULT)

        val renderCountBeforeMissingProvider = renderCount
        initializeWithoutProvider(instanceName, appId)
        assertRenderedAgain("active instance without provider", renderCountBeforeMissingProvider, BoostWidgetStrings.DEFAULT)

        val renderCountBeforeDestroyingMissingProvider = renderCount
        composeRule.runOnIdle {
            OwnId.destroy(instanceName)
        }
        assertRenderedAgain("destroy after missing provider", renderCountBeforeDestroyingMissingProvider, BoostWidgetStrings.DEFAULT)

        initializeWithProvider(instanceName, appId, firstProvider)
        awaitSubscribed("first provider", firstProvider)
        assertRendered("provider before first value", BoostWidgetStrings.DEFAULT)

        val first = strings("First")
        emit(firstProvider, first)
        assertRendered("first provider value", first)

        emit(firstProvider, null)
        assertRenderedAfterIdle(first)

        val second = strings("Second")
        emit(firstProvider, second)
        assertRendered("later provider value", second)

        initializeWithProvider(instanceName, appId, secondProvider)
        awaitUnsubscribed("replaced first provider", firstProvider)
        awaitSubscribed("replacement provider", secondProvider)
        assertRendered("same-name reinitialization", BoostWidgetStrings.DEFAULT)

        emit(firstProvider, strings("Late from replaced instance"))
        assertRenderedAfterIdle(BoostWidgetStrings.DEFAULT)

        val replacement = strings("Replacement")
        emit(secondProvider, replacement)
        assertRendered("replacement provider value", replacement)

        emit(firstProvider, strings("Another late value"))
        assertRenderedAfterIdle(replacement)

        composeRule.runOnIdle {
            OwnId.destroy(instanceName)
        }
        awaitUnsubscribed("destroyed replacement provider", secondProvider)
        assertRendered("destroy", BoostWidgetStrings.DEFAULT)

        emit(secondProvider, strings("Late after destroy"))
        assertRenderedAfterIdle(BoostWidgetStrings.DEFAULT)
    }

    private fun initializeWithProvider(instanceName: InstanceName, appId: String, provider: BoostWidgetStringsProvider) {
        composeRule.runOnIdle {
            OwnId.initialize(instanceName) {
                applicationContext = app
                this.appId = appId
            }
            val instanceContainer = checkNotNull(OwnId.getInstanceContainer(instanceName)) {
                "Expected initialized instance container for $instanceName"
            }
            instanceContainer.register<BoostWidgetStringsProvider>(provider)
        }
    }

    private fun initializeWithoutProvider(instanceName: InstanceName, appId: String) {
        composeRule.runOnIdle {
            OwnId.initialize(instanceName) {
                applicationContext = app
                this.appId = appId
            }
            val instanceContainer = checkNotNull(OwnId.getInstanceContainer(instanceName)) {
                "Expected initialized instance container for $instanceName"
            }
            instanceContainer.remove<BoostWidgetStringsProvider>()
        }
    }

    private fun emit(provider: ControlledBoostWidgetStringsProvider, value: BoostWidgetStrings?) {
        composeRule.runOnIdle {
            provider.emit(value)
        }
        composeRule.waitForIdle()
    }

    private fun assertRendered(transition: String, expected: BoostWidgetStrings) {
        composeRule.waitUntil(conditionDescription = "Expected $transition to render $expected, but rendered $renderedStrings") {
            renderedStrings == expected
        }
        assertRenderedAfterIdle(expected)
    }

    private fun assertRenderedAgain(transition: String, previousRenderCount: Int, expected: BoostWidgetStrings) {
        composeRule.waitUntil(
            conditionDescription = "Expected $transition to trigger a render after $previousRenderCount, but render count is $renderCount"
        ) {
            renderCount > previousRenderCount
        }
        assertRenderedAfterIdle(expected)
    }

    private fun awaitSubscribed(description: String, provider: ControlledBoostWidgetStringsProvider) {
        composeRule.waitUntil(conditionDescription = "Expected $description to be subscribed") {
            provider.hasSubscribers
        }
    }

    private fun awaitUnsubscribed(description: String, provider: ControlledBoostWidgetStringsProvider) {
        composeRule.waitUntil(conditionDescription = "Expected $description to be unsubscribed") {
            provider.hasSubscribers.not()
        }
    }

    private fun assertRenderedAfterIdle(expected: BoostWidgetStrings) {
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertThat(renderedStrings).isEqualTo(expected)
        }
    }

    private fun strings(label: String): BoostWidgetStrings =
        BoostWidgetStrings(skipPassword = "$label skip", or = "$label or")

    private class ControlledBoostWidgetStringsProvider : BoostWidgetStringsProvider {
        private val strings = MutableStateFlow<BoostWidgetStrings?>(null)

        val hasSubscribers: Boolean
            get() = strings.subscriptionCount.value > 0

        override fun getStrings(params: BoostWidgetStringsParams): StateFlow<BoostWidgetStrings?> = strings

        fun emit(value: BoostWidgetStrings?) {
            strings.value = value
        }
    }
}
