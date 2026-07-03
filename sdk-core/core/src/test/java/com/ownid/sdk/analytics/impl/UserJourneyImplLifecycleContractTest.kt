package com.ownid.sdk.analytics.impl

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.analytics.FlowInfo
import com.ownid.sdk.analytics.Step
import com.ownid.sdk.analytics.UserJourney
import com.ownid.sdk.analytics.UserJourneySummary
import com.ownid.sdk.api.ApiResult
import com.ownid.sdk.api.internal.EventsApi
import com.ownid.sdk.api.internal.EventsApiFailure
import com.ownid.sdk.capability.LocalInfo
import com.ownid.sdk.capability.UserRepository
import com.ownid.sdk.model.AuthMethod
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.OperationID
import com.ownid.sdk.model.OperationType
import com.ownid.sdk.model.User
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(InternalOwnIdAPI::class, ExperimentalCoroutinesApi::class)
// Covers: ANALYTICS-020, ANALYTICS-RUNTIME-010, ANALYTICS-RUNTIME-020, ANALYTICS-RUNTIME-030, ANALYTICS-RUNTIME-040, ANALYTICS-RUNTIME-050
internal class UserJourneyImplLifecycleContractTest {

    @Test
    fun startFlowUpdatesActiveFlowMetadataWithoutCompletingPreviousFlow(): Unit = runTest {
        val eventsApi = CapturingEventsApi()
        val userJourney = newJourney(eventsApi)

        val controller = userJourney.startFlow(name = "initial-flow", source = FlowInfo.Source.WIDGET_BUTTON, traceParent = null)
        val updatedController = userJourney.startFlow(name = "updated-login", source = FlowInfo.Source.EXPLICIT, traceParent = null)
        controller.completeFlow(UserJourney.Controller.Outcome.Completed(authMethod = null))

        advanceUntilIdle()

        assertThat(updatedController).isSameInstanceAs(controller)

        val flow = eventsApi.singleParams().userJourney.eventInfo.flowsInfo.single()
        assertThat(flow.name).isEqualTo("updated-login")
        assertThat(flow.source).isEqualTo(FlowInfo.Source.EXPLICIT)
        assertThat(flow.status).isEqualTo(FlowInfo.Status.COMPLETED)
        assertThat(flow.switchedToFlow).isNull()
        assertThat(flow.completedAt).isNotNull()
        assertThat(flow.insights?.duration).isNotNull()
    }

    @Test
    fun switchToFlowMarksPreviousFlowSwitchedAndAbortsInProgressOperations(): Unit = runTest {
        val eventsApi = CapturingEventsApi()
        val userJourney = newJourney(eventsApi)

        val controller = userJourney.startFlow(name = "login", source = FlowInfo.Source.WIDGET_BUTTON, traceParent = null)
        val inProgressOperation = OperationID(OperationType.PASSKEY_AUTH, "old-passkey")
        controller.startOperation(inProgressOperation)
        controller.switchToFlow(flowId = "flow-passkey", name = "passkey-enroll", source = FlowInfo.Source.ENROLL_PROMPT)
        controller.completeFlow(UserJourney.Controller.Outcome.Registered(authMethod = AuthMethod.Passkey))

        advanceUntilIdle()

        val flows = eventsApi.singleParams().userJourney.eventInfo.flowsInfo
        assertThat(flows).hasSize(2)

        val switchedFlow = flows[0]
        assertThat(switchedFlow.name).isEqualTo("login")
        assertThat(switchedFlow.status).isEqualTo(FlowInfo.Status.SWITCHED)
        assertThat(switchedFlow.switchedToFlow).isEqualTo("flow-passkey")
        assertThat(switchedFlow.completedAt).isNotNull()

        val abortedStep = switchedFlow.steps.single()
        assertThat(abortedStep.operationType).isEqualTo(OperationType.PASSKEY_AUTH)
        assertThat(abortedStep.status).isEqualTo(Step.Status.ABORTED)
        assertThat(abortedStep.completedAt).isNotNull()
        assertThat(abortedStep.insights?.duration).isNotNull()

        val targetFlow = flows[1]
        assertThat(targetFlow.id).isEqualTo("flow-passkey")
        assertThat(targetFlow.name).isEqualTo("passkey-enroll")
        assertThat(targetFlow.source).isEqualTo(FlowInfo.Source.ENROLL_PROMPT)
        assertThat(targetFlow.status).isEqualTo(FlowInfo.Status.COMPLETED)
        assertThat(targetFlow.insights?.authMethod).isEqualTo(AuthMethod.Passkey)
        assertThat(targetFlow.insights?.registered).isTrue()
    }

    @Test
    fun operationEventsRecordSuccessFailureErrorsAndClickCounts(): Unit = runTest {
        val eventsApi = CapturingEventsApi()
        val userJourney = newJourney(eventsApi)

        val controller = userJourney.startFlow(name = "operation-flow", source = FlowInfo.Source.EXPLICIT, traceParent = null)
        val completedOperation = OperationID(OperationType.LOGIN_ID_COLLECT, "login-id")
        val failedOperation = OperationID(OperationType.PASSKEY_AUTH, "passkey")
        controller.startOperation(completedOperation)
        controller.addOperationClick(completedOperation)
        controller.completeOperation(completedOperation)
        controller.startOperation(failedOperation)
        controller.addOperationClick(failedOperation)
        controller.addOperationClick(failedOperation)
        controller.completeOperation(failedOperation, ErrorCode.Timeout, source = "passkey", message = "timed out")
        controller.completeFlow(UserJourney.Controller.Outcome.Completed(authMethod = null))

        advanceUntilIdle()

        val steps = eventsApi.singleParams().userJourney.eventInfo.flowsInfo.single().steps
        assertThat(steps).hasSize(2)

        val completedStep = steps.single { it.operationType == OperationType.LOGIN_ID_COLLECT }
        assertThat(completedStep.status).isEqualTo(Step.Status.COMPLETED)
        assertThat(completedStep.errors).isNull()
        assertThat(completedStep.insights?.clicksCount).isEqualTo(1)
        assertThat(completedStep.completedAt).isNotNull()

        val failedStep = steps.single { it.operationType == OperationType.PASSKEY_AUTH }
        assertThat(failedStep.status).isEqualTo(Step.Status.FAILED)
        assertThat(failedStep.insights?.clicksCount).isEqualTo(2)
        assertThat(failedStep.completedAt).isNotNull()
        val failure = failedStep.errors?.single()
        assertThat(failure?.errorCode).isEqualTo(ErrorCode.Timeout.value)
        assertThat(failure?.source).isEqualTo("passkey")
        assertThat(failure?.message).isEqualTo("timed out")
    }

    @Test
    fun summaryIncludesReporterDeviceRefererAndReturningUserEnrichment(): Unit = runTest {
        val eventsApi = CapturingEventsApi()
        val loginId = LoginId("user@example.com", LoginId.Type.Email)
        val userJourney = newJourney(eventsApi, userRepository = FakeUserRepository(User(loginId, AuthMethod.Otp)))

        val controller = userJourney.startFlow(name = "login", source = FlowInfo.Source.RETURNING_USER_PROMPT, traceParent = null)
        controller.setReferer("android-app://com.example.app/account")
        controller.setUserInfo(loginId)
        controller.completeFlow(UserJourney.Controller.Outcome.LoggedIn(AuthMethod.Otp))

        advanceUntilIdle()

        val summary = eventsApi.singleParams().userJourney
        assertThat(summary.id).isNotEmpty()
        assertThat(summary.reporter.service).isEqualTo(UserJourneySummary.Reporter.Service.ANDROID_SDK)
        assertThat(summary.reporter.origin).isEqualTo("com.example.app")
        assertThat(summary.reporter.referer).isEqualTo("android-app://com.example.app/account")
        assertThat(summary.reporter.version).isEqualTo("1.2.3")
        assertThat(summary.deviceInfo.isPlatformAuthenticatorAvailable).isTrue()
        assertThat(summary.deviceInfo.isWebView).isFalse()
        assertThat(summary.deviceInfo.isMobileNative).isTrue()

        val returningUserInfo = summary.userInfo.single()
        assertThat(returningUserInfo.loginId).isEqualTo(loginId)
        assertThat(returningUserInfo.returningUser).isTrue()
        assertThat(returningUserInfo.lastAuthMethod).isEqualTo(AuthMethod.Otp)

        val flow = summary.eventInfo.flowsInfo.single()
        assertThat(flow.status).isEqualTo(FlowInfo.Status.COMPLETED)
        assertThat(flow.insights?.authMethod).isEqualTo(AuthMethod.Otp)
        assertThat(flow.insights?.loggedIn).isTrue()
        assertThat(flow.insights?.registered).isFalse()
    }

    @Test
    fun terminalErrorOutcomeRecordsFailedFlowWithClientError(): Unit = runTest {
        val eventsApi = CapturingEventsApi()
        val userJourney = newJourney(eventsApi)

        val controller = userJourney.startFlow(name = "login", source = FlowInfo.Source.EXPLICIT, traceParent = null)
        controller.completeFlow(UserJourney.Controller.Outcome.Error(ErrorCode.Network, source = "api", message = "offline"))

        advanceUntilIdle()

        val flow = eventsApi.singleParams().userJourney.eventInfo.flowsInfo.single()
        assertThat(flow.status).isEqualTo(FlowInfo.Status.FAILED)
        assertThat(flow.completedAt).isNotNull()
        assertThat(flow.insights?.loggedIn).isFalse()
        assertThat(flow.insights?.registered).isFalse()
        val error = flow.errors?.single()
        assertThat(error?.errorCode).isEqualTo(ErrorCode.Network.value)
        assertThat(error?.source).isEqualTo("api")
        assertThat(error?.message).isEqualTo("offline")
    }

    @Test
    fun abortedTerminalErrorRecordsAbortedFlow(): Unit = runTest {
        val eventsApi = CapturingEventsApi()
        val userJourney = newJourney(eventsApi)

        val controller = userJourney.startFlow(name = "login", source = FlowInfo.Source.EXPLICIT, traceParent = null)
        controller.completeFlow(UserJourney.Controller.Outcome.Error(ErrorCode.Aborted, source = "ui", message = "closed"))

        advanceUntilIdle()

        val flow = eventsApi.singleParams().userJourney.eventInfo.flowsInfo.single()
        assertThat(flow.status).isEqualTo(FlowInfo.Status.ABORTED)
        val error = flow.errors?.single()
        assertThat(error?.errorCode).isEqualTo(ErrorCode.Aborted.value)
        assertThat(error?.source).isEqualTo("ui")
        assertThat(error?.message).isEqualTo("closed")
    }

    @Test
    fun traceParentFromExplicitFlowUpdateIsSubmitted(): Unit = runTest {
        val eventsApi = CapturingEventsApi()
        val userJourney = newJourney(eventsApi)

        val controller = userJourney.startFlow(
            name = "initial-flow",
            source = FlowInfo.Source.WIDGET_BUTTON,
            traceParent = TRACE_PARENT
        )
        userJourney.startFlow(name = "updated-login", source = FlowInfo.Source.EXPLICIT, traceParent = REPLACEMENT_TRACE_PARENT)
        controller.completeFlow(UserJourney.Controller.Outcome.Completed(authMethod = null))

        advanceUntilIdle()

        assertThat(eventsApi.singleParams().traceParent).isEqualTo(REPLACEMENT_TRACE_PARENT)
    }

    @Test
    fun eventsSummaryUsesJourneySummaryTypeAndDefaultAndroidReferer(): Unit = runTest {
        val eventsApi = CapturingEventsApi()
        val userJourney = newJourney(eventsApi)

        val controller = userJourney.startFlow(name = "login", source = FlowInfo.Source.IMPLICIT, traceParent = TRACE_PARENT)
        controller.completeFlow(UserJourney.Controller.Outcome.Completed(authMethod = AuthMethod.Password))

        advanceUntilIdle()

        val params = eventsApi.singleParams()
        assertThat(params.traceParent).isEqualTo(TRACE_PARENT)
        assertThat(params.userJourney.eventInfo.type).isEqualTo(UserJourneySummary.EventInfo.Type.JOURNEY_SUMMARY)
        assertThat(params.userJourney.reporter.referer).isEqualTo("android-app://com.example.app")
        val flow = params.userJourney.eventInfo.flowsInfo.single()
        assertThat(flow.source).isEqualTo(FlowInfo.Source.IMPLICIT)
        assertThat(flow.insights?.authMethod).isEqualTo(AuthMethod.Password)
    }

    @Test
    fun allSupportedFlowSourcesAreSubmittedWithoutDroppingJourneyState(): Unit = runTest {
        val eventsApi = CapturingEventsApi()
        val userJourney = newJourney(eventsApi)
        val sources = FlowInfo.Source.values().toList()

        sources.forEach { source ->
            val controller = userJourney.startFlow(name = "source-${source.name}", source = source, traceParent = null)
            controller.completeFlow(UserJourney.Controller.Outcome.Completed(authMethod = null))
            advanceUntilIdle()
        }

        assertThat(eventsApi.allParams().map { it.userJourney.eventInfo.flowsInfo.single().source })
            .containsExactlyElementsIn(sources)
            .inOrder()
        assertThat(eventsApi.allParams().map { it.userJourney.eventInfo.flowsInfo.single().name })
            .containsExactlyElementsIn(sources.map { "source-${it.name}" })
            .inOrder()
    }

    @Test
    fun eventsApiFailureDoesNotRewriteTerminalStatusesAndNextJourneyStartsFresh(): Unit = runTest {
        val eventsApi = CapturingEventsApi(
            result = ApiResult.Failure(EventsApiFailure.BadRequest(ErrorCode.InvalidArgument, "bad analytics payload"))
        )
        val userJourney = newJourney(eventsApi)

        val firstController = userJourney.startFlow(name = "first", source = FlowInfo.Source.EXPLICIT, traceParent = null)
        val operation = OperationID(OperationType.SESSION_CREATION, "session")
        firstController.startOperation(operation)
        firstController.completeOperation(operation)
        firstController.completeFlow(UserJourney.Controller.Outcome.LoggedIn(AuthMethod.Passkey))

        advanceUntilIdle()

        val failedUploadSummary = eventsApi.singleParams().userJourney
        val failedUploadFlow = failedUploadSummary.eventInfo.flowsInfo.single()
        assertThat(failedUploadFlow.name).isEqualTo("first")
        assertThat(failedUploadFlow.status).isEqualTo(FlowInfo.Status.COMPLETED)
        assertThat(failedUploadFlow.insights?.loggedIn).isTrue()
        assertThat(failedUploadFlow.steps.single().status).isEqualTo(Step.Status.COMPLETED)

        eventsApi.result = ApiResult.Success(Unit)
        val secondController = userJourney.startFlow(name = "second", source = FlowInfo.Source.DEFERRED, traceParent = null)
        secondController.completeFlow(UserJourney.Controller.Outcome.Completed(authMethod = null))

        advanceUntilIdle()

        assertThat(eventsApi.allParams()).hasSize(2)
        val secondSummary = eventsApi.allParams()[1].userJourney
        assertThat(secondSummary.eventInfo.flowsInfo).hasSize(1)
        assertThat(secondSummary.eventInfo.flowsInfo.single().name).isEqualTo("second")
    }

    @Test
    fun payloadConstructionFailureSkipsUploadAndNextJourneyStartsFresh(): Unit = runTest {
        val eventsApi = CapturingEventsApi()
        val userJourney = newJourney(eventsApi, localInfo = ThrowingOncePackageNameLocalInfo())

        val firstController = userJourney.startFlow(name = "first", source = FlowInfo.Source.EXPLICIT, traceParent = null)
        firstController.completeFlow(UserJourney.Controller.Outcome.Completed(authMethod = AuthMethod.Passkey))

        advanceUntilIdle()

        assertThat(eventsApi.allParams()).isEmpty()

        val secondController = userJourney.startFlow(name = "second", source = FlowInfo.Source.DEFERRED, traceParent = null)
        secondController.completeFlow(UserJourney.Controller.Outcome.Completed(authMethod = null))

        advanceUntilIdle()

        assertThat(secondController).isNotSameInstanceAs(firstController)
        val summary = eventsApi.singleParams().userJourney
        assertThat(summary.reporter.origin).isEqualTo("com.example.app")
        assertThat(summary.eventInfo.flowsInfo).hasSize(1)
        assertThat(summary.eventInfo.flowsInfo.single().name).isEqualTo("second")
        assertThat(summary.eventInfo.flowsInfo.single().status).isEqualTo(FlowInfo.Status.COMPLETED)
    }

    @Test
    fun userRepositoryFailureKeepsJourneyCompletionAndFallsBackToNonReturningUser(): Unit = runTest {
        val eventsApi = CapturingEventsApi()
        val loginId = LoginId("user@example.com", LoginId.Type.Email)
        val userJourney = newJourney(eventsApi, userRepository = ThrowingUserRepository)

        val controller = userJourney.startFlow(name = "login", source = FlowInfo.Source.EXPLICIT, traceParent = null)
        controller.setUserInfo(loginId)
        controller.completeFlow(UserJourney.Controller.Outcome.Completed(authMethod = null))

        advanceUntilIdle()

        val summary = eventsApi.singleParams().userJourney
        val userInfo = summary.userInfo.single()
        assertThat(userInfo.loginId).isEqualTo(loginId)
        assertThat(userInfo.returningUser).isFalse()
        assertThat(userInfo.lastAuthMethod).isNull()
        assertThat(summary.eventInfo.flowsInfo.single().status).isEqualTo(FlowInfo.Status.COMPLETED)
    }

    private fun TestScope.newJourney(
        eventsApi: CapturingEventsApi,
        userRepository: UserRepository? = FakeUserRepository(null),
        localInfo: LocalInfo = FakeLocalInfo,
    ): UserJourneyImpl = UserJourneyImpl(
        eventsApi = eventsApi,
        localInfo = localInfo,
        userRepository = userRepository,
        coroutineScope = this
    )

    private class CapturingEventsApi(
        var result: ApiResult<Unit, EventsApiFailure> = ApiResult.Success(Unit)
    ) : EventsApi {
        private val params = mutableListOf<EventsApi.Params>()

        override suspend fun start(params: EventsApi.Params): ApiResult<Unit, EventsApiFailure> {
            this.params += params
            return result
        }

        fun singleParams(): EventsApi.Params = params.single()

        fun allParams(): List<EventsApi.Params> = params.toList()
    }

    private object FakeLocalInfo : LocalInfo {
        override val modules: List<Pair<String, String>> = emptyList()
        override val packageName: String = "com.example.app"
        override val appVersion: String = "1.2.3"
        override val userAgent: String = "OwnIDTest/1.2.3"
        override val correlationId: String = "correlation-id"
        override val isDebuggable: Boolean = true
        override val isSystemFidoCapable: Boolean = true
        override val isDeviceSecured: Boolean = true
        override val isFingerprintHardwarePresent: Boolean = true
        override val isFaceHardwarePresent: Boolean = false
        override val isIrisHardwarePresent: Boolean = false
        override val isStrongBiometricEnabled: Boolean = true
    }

    private class ThrowingOncePackageNameLocalInfo(
        private val delegate: LocalInfo = FakeLocalInfo
    ) : LocalInfo by delegate {
        private var packageNameReads = 0

        override val packageName: String
            get() {
                packageNameReads += 1
                if (packageNameReads == 1) error("package unavailable")
                return delegate.packageName
            }
    }

    private class FakeUserRepository(private val lastUser: User?) : UserRepository {
        override suspend fun getLastUser(): User? = lastUser
        override suspend fun setLastUser(user: User) = Unit
        override suspend fun clearLastUser() = Unit
    }

    private object ThrowingUserRepository : UserRepository {
        override suspend fun getLastUser(): User = error("storage unavailable")
        override suspend fun setLastUser(user: User) = Unit
        override suspend fun clearLastUser() = Unit
    }

    private companion object {
        private const val TRACE_PARENT = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-00"
        private const val REPLACEMENT_TRACE_PARENT = "00-11111111111111111111111111111111-2222222222222222-01"
    }
}
