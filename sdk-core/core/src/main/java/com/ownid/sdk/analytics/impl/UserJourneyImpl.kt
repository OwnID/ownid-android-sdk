package com.ownid.sdk.analytics.impl

import android.os.SystemClock
import com.ownid.sdk.analytics.ClientError
import com.ownid.sdk.analytics.FlowInfo
import com.ownid.sdk.analytics.Step
import com.ownid.sdk.analytics.UserJourney
import com.ownid.sdk.analytics.UserJourneySummary
import com.ownid.sdk.api.internal.EventsApi
import com.ownid.sdk.api.onError
import com.ownid.sdk.api.onSuccess
import com.ownid.sdk.capability.LocalInfo
import com.ownid.sdk.capability.UserRepository
import com.ownid.sdk.logger.OwnIdLogRouter
import com.ownid.sdk.model.AuthMethod
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.OperationID
import com.ownid.sdk.model.OperationType
import com.ownid.sdk.model.runCatchingExceptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * Default [UserJourney] collector for an SDK instance.
 *
 * The collector accepts telemetry updates for one active journey and schedules a single summary submission when the
 * active flow completes. Completion clears the retained journey state before the next [startFlow] call creates a fresh
 * journey. Submission failures are logged and do not affect the SDK flow that already produced the analytics outcome.
 */
@OptIn(InternalOwnIdAPI::class)
internal class UserJourneyImpl(
    private val eventsApi: EventsApi,
    private val localInfo: LocalInfo,
    private val userRepository: UserRepository?,
    coroutineScope: CoroutineScope,
    private val logger: OwnIdLogRouter? = null,
) : UserJourney, CoroutineScope by coroutineScope {

    private val mutex = Mutex()
    private var controller: JourneyControllerImpl? = null

    override suspend fun startFlow(name: String?, source: FlowInfo.Source, traceParent: String?): UserJourney.Controller =
        mutex.withLock {
            val controller = (controller ?: JourneyControllerImpl().also { controller = it })
            controller.ensureFlowStartedLocked(name, source, traceParent)
            controller
        }

    private inner class JourneyControllerImpl : UserJourney.Controller {

        private inner class StepData(
            val operationType: OperationType,
            val name: String? = null,
            val startedAtWallMs: Long = System.currentTimeMillis(),
            val startedAtElapsedMs: Long = SystemClock.elapsedRealtime(),
            var clicks: Int = 0,
            var status: Step.Status = Step.Status.IN_PROGRESS,
            var completedAtWallMs: Long? = null,
            var completedAtElapsedMs: Long? = null,
            var errors: MutableList<ClientError>? = null,
        )

        private inner class FlowData(
            val id: String,
            var name: String?,
            var source: FlowInfo.Source,
            val startedAtWallMs: Long = System.currentTimeMillis(),
            val startedAtElapsedMs: Long = SystemClock.elapsedRealtime(),
            var completedAtWallMs: Long? = null,
            var completedAtElapsedMs: Long? = null,
            var status: FlowInfo.Status = FlowInfo.Status.IN_PROGRESS,
            var switchedToFlow: String? = null,
            val steps: LinkedHashMap<OperationID, StepData> = LinkedHashMap(),
            var errors: MutableList<ClientError>? = null,
            var insightsAuthMethod: AuthMethod? = null,
            var insightsLoggedIn: Boolean? = null,
            var insightsRegistered: Boolean? = null,
        )

        private val isoFormatter: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }

        private var journeyId: String = UUID.randomUUID().toString()
        private var traceParent: String? = null
        private var referer: String? = null
        private val userInfo: MutableList<UserJourneySummary.UserInfo> = mutableListOf()
        private val flows: MutableList<FlowData> = mutableListOf()
        private var currentFlow: FlowData? = null
        private var submitted: Boolean = false

        fun ensureFlowStartedLocked(name: String?, source: FlowInfo.Source, traceParent: String?) {
            val activeFlow = currentFlow
            if (traceParent != null) this.traceParent = traceParent
            if (activeFlow == null) {
                val flowId = UUID.randomUUID().toString()
                val newFlow = FlowData(id = flowId, name = name, source = source)
                flows.add(newFlow)
                currentFlow = newFlow
                return
            }
            if (name != null) activeFlow.name = name
            activeFlow.source = source
        }

        override suspend fun switchToFlow(flowId: String?, name: String?, source: FlowInfo.Source) = mutex.withLock {
            if (submitted) {
                logger?.logD(this@UserJourneyImpl, "switchToFlow", "Ignored: journey already submitted")
                return
            }
            val newFlowId = flowId ?: UUID.randomUUID().toString()
            currentFlow?.let { flow ->
                flow.status = FlowInfo.Status.SWITCHED
                flow.switchedToFlow = newFlowId
                val currentTimeMillis = System.currentTimeMillis()
                flow.completedAtWallMs = currentTimeMillis
                val elapsedRealtime = SystemClock.elapsedRealtime()
                flow.completedAtElapsedMs = elapsedRealtime
                flow.steps.values.forEach { step ->
                    if (step.status == Step.Status.IN_PROGRESS) {
                        step.status = Step.Status.ABORTED
                        step.completedAtWallMs = currentTimeMillis
                        step.completedAtElapsedMs = elapsedRealtime
                    }
                }
            }
            val newFlow = FlowData(id = newFlowId, name = name, source = source)
            flows.add(newFlow)
            currentFlow = newFlow
        }

        override suspend fun setUserInfo(loginId: LoginId) {
            mutex.lock()
            try {
                if (userInfo.any { it.loginId.id == loginId.id }) return

                userInfo.add(UserJourneySummary.UserInfo(loginId = loginId, returningUser = false, lastAuthMethod = null))
                val lastUser = runCatchingExceptions { userRepository?.getLastUser() }
                    .onFailure { cause -> logger?.logI(this@UserJourneyImpl, "setUserInfo", "Failed to enrich journey user info from repository", cause) }
                    .getOrNull()

                if (lastUser != null && lastUser.loginId.id == loginId.id) {
                    val idx = userInfo.indexOfFirst { it.loginId.id == loginId.id }
                    if (idx >= 0) {
                        val existing = userInfo[idx]
                        userInfo[idx] = UserJourneySummary.UserInfo(
                            loginId = existing.loginId,
                            returningUser = true,
                            lastAuthMethod = lastUser.authMethod
                        )
                    }
                }
            } finally {
                mutex.unlock()
            }
        }

        override suspend fun setReferer(referer: String) = mutex.withLock {
            this.referer = referer
        }

        override suspend fun startOperation(operationId: OperationID) = mutex.withLock {
            val flow = currentFlow
            if (flow == null) {
                logger?.logW(this@UserJourneyImpl, "startOperation", "No active flow for operation: $operationId")
                return
            }
            if (flow.steps.containsKey(operationId)) {
                logger?.logW(this@UserJourneyImpl, "startOperation", "Duplicate startOperation: $operationId")
            }
            flow.steps[operationId] = StepData(operationType = operationId.type)
        }

        override suspend fun addOperationClick(operationId: OperationID) = mutex.withLock {
            val data = currentFlow?.steps?.get(operationId)
            if (data == null) {
                logger?.logW(this@UserJourneyImpl, "addOperationClick", "Unknown: $operationId")
                return
            }
            data.clicks += 1
        }

        override suspend fun completeOperation(operationId: OperationID, errorCode: ErrorCode?, source: String?, message: String?) = mutex.withLock {
            val data = currentFlow?.steps?.get(operationId)
            if (data == null) {
                logger?.logW(this@UserJourneyImpl, "completeOperation", "Unknown: $operationId")
                return
            }
            data.status = when (errorCode) {
                null -> Step.Status.COMPLETED
                ErrorCode.Aborted -> Step.Status.ABORTED
                else -> Step.Status.FAILED
            }
            data.completedAtWallMs = System.currentTimeMillis()
            data.completedAtElapsedMs = SystemClock.elapsedRealtime()
            if (errorCode != null) {
                val err = ClientError(errorCode.value, source = source, message = message)
                if (data.errors == null) data.errors = mutableListOf(err) else data.errors!!.add(err)
            }
        }

        override fun completeFlow(outcome: UserJourney.Controller.Outcome) {
            launch {
                completeFlowInternal(outcome)
            }
        }

        private suspend fun completeFlowInternal(outcome: UserJourney.Controller.Outcome) {
            val submissionResult = mutex.withLock {
                if (submitted) {
                    logger?.logD(this@UserJourneyImpl, "complete", "Duplicate completeFlow ignored: ${currentFlow?.id}")
                    return@withLock null
                }
                submitted = true
                val flow = currentFlow
                if (flow != null) {
                    flow.status = when (outcome) {
                        is UserJourney.Controller.Outcome.Error -> if (outcome.errorCode == ErrorCode.Aborted) FlowInfo.Status.ABORTED else FlowInfo.Status.FAILED
                        is UserJourney.Controller.Outcome.Completed -> FlowInfo.Status.COMPLETED
                        is UserJourney.Controller.Outcome.LoggedIn -> FlowInfo.Status.COMPLETED
                        is UserJourney.Controller.Outcome.Registered -> FlowInfo.Status.COMPLETED
                    }
                    flow.completedAtWallMs = System.currentTimeMillis()
                    flow.completedAtElapsedMs = SystemClock.elapsedRealtime()
                    flow.insightsAuthMethod = when (outcome) {
                        is UserJourney.Controller.Outcome.LoggedIn -> outcome.authMethod
                        is UserJourney.Controller.Outcome.Registered -> outcome.authMethod
                        is UserJourney.Controller.Outcome.Completed -> outcome.authMethod
                        is UserJourney.Controller.Outcome.Error -> null
                    }
                    flow.insightsLoggedIn = outcome is UserJourney.Controller.Outcome.LoggedIn
                    flow.insightsRegistered = outcome is UserJourney.Controller.Outcome.Registered
                    if (outcome is UserJourney.Controller.Outcome.Error) {
                        val err = ClientError(outcome.errorCode.value, source = outcome.source, message = outcome.message)
                        if (flow.errors == null) flow.errors = mutableListOf(err) else flow.errors!!.add(err)
                    }
                }

                try {
                    runCatchingExceptions { buildSubmissionLocked() }
                } finally {
                    // Clear active journey state before upload so telemetry delivery or payload construction cannot block the next journey.
                    this@UserJourneyImpl.controller = null
                }
            } ?: return

            val submission = submissionResult
                .onFailure { cause -> logger?.logW(this@UserJourneyImpl, "complete", "Failed to build journey summary", cause) }
                .getOrNull() ?: return

            launch {
                eventsApi.start(EventsApi.Params(submission.summary, submission.traceParent))
                    .onSuccess { logger?.logD(this@UserJourneyImpl, "complete", "EventsApi success for journey with ${submission.flowCount} flow(s)") }
                    .onError { logger?.logW(this@UserJourneyImpl, "complete", "EventsApi failure for journey: $it") }
            }
        }

        private fun buildSubmissionLocked(): Submission {
            val flowsInfo: List<FlowInfo> = flows.map { flowData ->
                FlowInfo(
                    id = flowData.id,
                    source = flowData.source,
                    status = flowData.status,
                    startedAt = isoFormatter.format(Date(flowData.startedAtWallMs)),
                    steps = flowData.steps.values.map { step ->
                        Step(
                            operationType = step.operationType,
                            status = step.status,
                            startedAt = isoFormatter.format(Date(step.startedAtWallMs)),
                            name = step.name,
                            completedAt = step.completedAtWallMs?.let { wall -> isoFormatter.format(Date(wall)) },
                            errors = step.errors,
                            insights = Step.Insights(
                                duration = step.completedAtElapsedMs?.let { end -> (end - step.startedAtElapsedMs).takeIf { it >= 0 } },
                                clicksCount = step.clicks.takeIf { it > 0 }
                            )
                        )
                    },
                    name = flowData.name,
                    completedAt = flowData.completedAtWallMs?.let { wall -> isoFormatter.format(Date(wall)) },
                    errors = flowData.errors,
                    switchedToFlow = flowData.switchedToFlow,
                    insights = FlowInfo.Insights(
                        duration = flowData.completedAtElapsedMs?.let { end -> (end - flowData.startedAtElapsedMs).takeIf { it >= 0 } },
                        clicksCount = flowData.steps.values.sumOf { it.clicks },
                        authMethod = flowData.insightsAuthMethod,
                        loggedIn = flowData.insightsLoggedIn,
                        registered = flowData.insightsRegistered,
                    )
                )
            }

            val summary = UserJourneySummary(
                id = journeyId,
                reporter = UserJourneySummary.Reporter(
                    service = UserJourneySummary.Reporter.Service.ANDROID_SDK,
                    origin = localInfo.packageName,
                    referer = referer ?: "android-app://${localInfo.packageName}",
                    version = localInfo.appVersion
                ),
                eventInfo = UserJourneySummary.EventInfo(
                    type = UserJourneySummary.EventInfo.Type.JOURNEY_SUMMARY,
                    flowsInfo = flowsInfo
                ),
                deviceInfo = UserJourneySummary.ClientDeviceInfo(
                    isPlatformAuthenticatorAvailable = localInfo.isSystemFidoCapable,
                    isWebView = false,
                    isMobileNative = true,
                ),
                userInfo = userInfo.toList()
            )

            return Submission(summary = summary, traceParent = traceParent, flowCount = flows.size)
        }
    }

    private data class Submission(val summary: UserJourneySummary, val traceParent: String?, val flowCount: Int)
}
