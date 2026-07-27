package com.ownid.sdk.compose.capability

import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.capability.LanguageTagsProvider
import com.ownid.sdk.compose.LocalOwnIdColors
import com.ownid.sdk.compose.MapResolver
import com.ownid.sdk.compose.MutableOperationRegistry
import com.ownid.sdk.compose.OwnIdDefaultColorsLight
import com.ownid.sdk.compose.OwnIdTheme
import com.ownid.sdk.compose.OwnIdThemeStore
import com.ownid.sdk.compose.OwnIdUiContainerController
import com.ownid.sdk.compose.RecordingLoginIdCollectOperationController
import com.ownid.sdk.compose.RecordingLoginIdCollectUiProvider
import com.ownid.sdk.compose.StaticLoginIdCollectStringsProvider
import com.ownid.sdk.compose.loginStrings
import com.ownid.sdk.compose.loginUiState
import com.ownid.sdk.compose.ops.LoginIdCollectUiContent
import com.ownid.sdk.compose.ops.LoginIdCollectUiProvider
import com.ownid.sdk.model.InstanceName
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LanguageTag
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.OperationID
import com.ownid.sdk.model.OperationType
import com.ownid.sdk.model.Reason
import com.ownid.sdk.ops.OperationResult
import com.ownid.sdk.ui.OwnIdHiddenActivity
import com.ownid.sdk.ui.capability.LoginIdCollectStringsProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import kotlin.time.Duration.Companion.milliseconds

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalMaterial3Api::class, InternalOwnIdAPI::class)
// Covers: UI-PRESENT-060, UI-PRESENT-190
internal class HiddenActivityComposeDelegateRuntimeContractTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun createIntentMarksComposeDelegateAndRequestsImeHandoffBeforeLaunch() {
        val operationID = OperationID(OperationType.LOGIN_ID_COLLECT, "intent-extras")
        val intent = HiddenActivityComposeDelegate.createIntent(
            context = RuntimeEnvironment.getApplication(),
            instanceName = InstanceName("compose-intent"),
            operationID = operationID
        )

        assertThat(HiddenActivityComposeDelegate().isThis(intent)).isTrue()
        assertThat(intent.component?.className).isEqualTo(OwnIdHiddenActivity::class.java.name)
        assertThat(intent.getBooleanExtra(COMPOSE_DELEGATE_KEY, false)).isTrue()
        assertThat(intent.getBooleanExtra(OwnIdHiddenActivity.PREPARE_IME_HANDOFF_BEFORE_LAUNCH_KEY, false)).isTrue()
    }

    @Test
    fun operationContentClosesHostAfterContainerCloses() {
        val operationID = OperationID(OperationType.LOGIN_ID_COLLECT, "missing-host-close")
        val registry = MutableOperationRegistry(emptyMap())
        var closeHostCalls = 0

        composeRule.setContent {
            OperationContent(
                operationID = operationID,
                operationRegistry = registry,
                instanceResolver = MapResolver(),
                ownIdLogRouter = null,
                themeStore = null,
                languageTagsProvider = null,
                onCloseActivityForTesting = { closeHostCalls += 1 }
            )
        }

        composeRule.waitUntil { closeHostCalls == 1 }
    }

    @Test
    fun operationContentConsumesThemeStoreAndLanguageLayoutDirectionForSdkPresentedContent() {
        val operationController = RecordingLoginIdCollectOperationController(
            operationId = OperationID(OperationType.LOGIN_ID_COLLECT, "sdk-presented-theme-language"),
            initialUiState = loginUiState(loginIdValue = "sdk-presented@example.com")
        )
        val registry = MutableOperationRegistry(mapOf(operationController.operationId to operationController))
        val themeStore = OwnIdThemeStore().apply {
            set(
                OwnIdTheme(
                    colorScheme = lightColorScheme(
                        primary = Color(0xFF224466),
                        onPrimary = Color(0xFFFFFFFF),
                        surface = Color(0xFFFAFBFC),
                        onSurface = Color(0xFF101010)
                    ),
                    shapes = Shapes(),
                    typography = Typography(),
                    colors = OwnIdDefaultColorsLight.copy(primary = Color(0xFF446688))
                )
            )
        }
        val languageTagsProvider = FakeLanguageTagsProvider(arLanguageTag())
        val probes = mutableListOf<SdkPresentedThemeProbe>()
        val content: LoginIdCollectUiContent = { uiState, strings, _, _, _, _ ->
            val materialPrimary = MaterialTheme.colorScheme.primary
            val ownIdPrimary = LocalOwnIdColors.current.primary
            val layoutDirection = LocalLayoutDirection.current
            SideEffect {
                probes += SdkPresentedThemeProbe(
                    materialPrimary = materialPrimary,
                    ownIdPrimary = ownIdPrimary,
                    layoutDirection = layoutDirection,
                    loginIdValue = uiState.loginIdValue,
                    stringsTitle = strings.title
                )
            }
        }

        composeRule.setContent {
            OperationContent(
                operationID = operationController.operationId,
                operationRegistry = registry,
                instanceResolver = loginResolver(content),
                ownIdLogRouter = null,
                themeStore = themeStore,
                languageTagsProvider = languageTagsProvider
            )
        }

        composeRule.waitUntil { probes.isNotEmpty() }

        assertThat(probes.last()).isEqualTo(
            SdkPresentedThemeProbe(
                materialPrimary = Color(0xFF224466),
                ownIdPrimary = Color(0xFF446688),
                layoutDirection = LayoutDirection.Rtl,
                loginIdValue = "sdk-presented@example.com",
                stringsTitle = "Title"
            )
        )

        composeRule.runOnIdle {
            languageTagsProvider.update(enLanguageTag())
        }

        composeRule.waitUntil { probes.any { probe -> probe.layoutDirection == LayoutDirection.Ltr } }

        assertThat(probes.last().layoutDirection).isEqualTo(LayoutDirection.Ltr)
    }

    @Test
    fun operationLookupFailureDismissesContainerWithoutAbortReason() {
        val operationID = OperationID(OperationType.LOGIN_ID_COLLECT, "missing")
        val registry = MutableOperationRegistry(emptyMap())
        val closeReasons = mutableListOf<Reason?>()
        var closeCalls = 0
        val containerController = OwnIdUiContainerController(closeAction = { closeCalls += 1 })
        containerController.addClosedHandler { reason -> closeReasons += reason }

        composeRule.setContent {
            OperationBody(
                operationID = operationID,
                operationRegistry = registry,
                instanceResolver = MapResolver(),
                logWarning = { _, _ -> },
                uiContainerController = containerController
            )
        }

        composeRule.waitUntil { closeCalls == 1 }
        containerController.markClosed()

        assertThat(closeReasons).containsExactly(null)
    }

    @Test
    fun missingUiProviderAbortsOperationAndDismissesWithoutUserCloseReason() {
        val operationController = RecordingLoginIdCollectOperationController(
            operationId = OperationID(OperationType.LOGIN_ID_COLLECT, "missing-provider"),
            initialUiState = loginUiState()
        )
        val registry = MutableOperationRegistry(mapOf(operationController.operationId to operationController))
        val closeReasons = mutableListOf<Reason?>()
        var closeCalls = 0
        val logs = mutableListOf<LogRecord>()
        val containerController = OwnIdUiContainerController(closeAction = { closeCalls += 1 })
        containerController.addClosedHandler { reason -> closeReasons += reason }
        val resolver = MapResolver(
            mapOf(LoginIdCollectStringsProvider::class.java to StaticLoginIdCollectStringsProvider(loginStrings()))
        )

        composeRule.setContent {
            OperationBody(
                operationID = operationController.operationId,
                operationRegistry = registry,
                instanceResolver = resolver,
                logWarning = { prefix, message -> logs += LogRecord(prefix, message) },
                uiContainerController = containerController
            )
        }

        composeRule.waitUntil { operationController.abortReasons.isNotEmpty() && closeCalls == 1 }
        containerController.markClosed()

        val reason = operationController.abortReasons.single()
        assertThat(reason).isInstanceOf(Reason.SystemError::class.java)
        assertThat((reason as Reason.SystemError).details).contains("no UI content provider")
        assertThat(closeReasons).containsExactly(null)
        assertThat(logs.single().prefix).isEqualTo("LoginIdCollectUi")
        assertThat(logs.single().message).contains("no UI content provider")
    }

    @Test
    fun containerDismissalBeforeSettlementAbortsOperationAsUserClose() {
        val contentCalls = mutableListOf<Unit>()
        val operationController = RecordingLoginIdCollectOperationController(
            operationId = OperationID(OperationType.LOGIN_ID_COLLECT, "dismiss-before-settlement"),
            initialUiState = loginUiState()
        )
        val registry = MutableOperationRegistry(mapOf(operationController.operationId to operationController))
        val containerController = OwnIdUiContainerController(closeAction = {})

        composeRule.setContent {
            OperationBody(
                operationID = operationController.operationId,
                operationRegistry = registry,
                instanceResolver = loginResolver(recordingContent(contentCalls)),
                logWarning = { _, _ -> },
                uiContainerController = containerController
            )
        }

        composeRule.waitUntil { contentCalls.isNotEmpty() }
        containerController.close()
        containerController.markClosed()
        composeRule.waitUntil { operationController.abortReasons.isNotEmpty() }

        assertThat(operationController.abortReasons).hasSize(1)
        assertThat(operationController.abortReasons.single()).isInstanceOf(Reason.UserClose::class.java)
    }

    @Test
    fun operationSettlementDismissesContainerWithoutUserCloseCancellation() {
        val contentCalls = mutableListOf<Unit>()
        val operationController = RecordingLoginIdCollectOperationController(
            operationId = OperationID(OperationType.LOGIN_ID_COLLECT, "settled"),
            initialUiState = loginUiState()
        )
        val registry = MutableOperationRegistry(mapOf(operationController.operationId to operationController))
        val closeReasons = mutableListOf<Reason?>()
        var closeCalls = 0
        val containerController = OwnIdUiContainerController(closeAction = { closeCalls += 1 })
        containerController.addClosedHandler { reason -> closeReasons += reason }

        composeRule.setContent {
            OperationBody(
                operationID = operationController.operationId,
                operationRegistry = registry,
                instanceResolver = loginResolver(recordingContent(contentCalls)),
                logWarning = { _, _ -> },
                uiContainerController = containerController
            )
        }

        composeRule.waitUntil { contentCalls.isNotEmpty() }
        composeRule.runOnIdle {
            operationController.complete(OperationResult.Success(LoginId("person@example.com", LoginId.Type.Email)))
        }
        shadowOf(Looper.getMainLooper()).idle()
        composeRule.waitUntil { closeCalls == 1 }

        containerController.markClosed()

        assertThat(operationController.abortReasons).isEmpty()
        assertThat(closeReasons).containsExactly(null)
    }

    @Test
    fun modalBottomSheetContainerMarksOpenedThroughTimeoutFallback() {
        var opened = false

        composeRule.setContent {
            ModalBottomSheetContainer(
                openedFallbackTimeoutForTesting = 1.milliseconds,
            ) { uiContainerController ->
                LaunchedEffect(uiContainerController) {
                    uiContainerController.awaitOpened()
                    opened = true
                }
            }
        }

        composeRule.waitUntil { opened }
    }

    @Test
    fun modalBottomSheetContainerReconcilesVisibleSheetThatStartsHidden() {
        lateinit var sheetState: SheetState

        composeRule.setContent {
            val density = LocalDensity.current
            val state = remember {
                SheetState(
                    skipPartiallyExpanded = true,
                    density = density,
                    initialValue = SheetValue.Hidden
                )
            }
            SideEffect {
                sheetState = state
            }

            ModalBottomSheetContainer(sheetState = state) {
                Box(Modifier.size(64.dp))
            }
        }

        composeRule.waitUntil {
            sheetState.hasExpandedState && sheetState.targetValue == SheetValue.Expanded
        }
    }

    private fun loginResolver(content: LoginIdCollectUiContent): MapResolver =
        MapResolver(
            mapOf(
                LoginIdCollectUiProvider::class.java to RecordingLoginIdCollectUiProvider(content),
                LoginIdCollectStringsProvider::class.java to StaticLoginIdCollectStringsProvider(loginStrings())
            )
        )

    private fun recordingContent(calls: MutableList<Unit>): LoginIdCollectUiContent =
        { _, _, _, _, _, _ ->
            SideEffect {
                calls += Unit
            }
        }

    private data class SdkPresentedThemeProbe(
        val materialPrimary: Color,
        val ownIdPrimary: Color,
        val layoutDirection: LayoutDirection,
        val loginIdValue: String,
        val stringsTitle: String
    )

    private data class LogRecord(val prefix: String, val message: String)

    private class FakeLanguageTagsProvider(languageTag: LanguageTag) : LanguageTagsProvider {
        private val tags = MutableStateFlow(listOf(languageTag))

        override fun setLanguageTags(tags: List<String>): Unit = error("Unexpected language update")

        override val languageTagsFlow: StateFlow<List<LanguageTag>>
            get() = tags

        fun update(languageTag: LanguageTag) {
            tags.value = listOf(languageTag)
        }
    }

    private fun arLanguageTag(): LanguageTag = Json.decodeFromString("\"ar\"")
    private fun enLanguageTag(): LanguageTag = Json.decodeFromString("\"en\"")

    private companion object {
        private const val COMPOSE_DELEGATE_KEY = "com.ownid.sdk.key.ui.KEY_COMPOSE_DELEGATE"
    }
}
