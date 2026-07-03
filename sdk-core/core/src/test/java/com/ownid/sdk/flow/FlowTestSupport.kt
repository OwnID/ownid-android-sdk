@file:Suppress("unused")

package com.ownid.sdk.flow

import com.ownid.sdk.analytics.FlowInfo
import com.ownid.sdk.analytics.UserJourney
import com.ownid.sdk.capability.LoginIdValidator
import com.ownid.sdk.capability.Serialization
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.Availability
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.OperationID
import com.ownid.sdk.model.OperationType
import com.ownid.sdk.model.Reason
import com.ownid.sdk.model.encodeToBase64UrlSafe
import com.ownid.sdk.namespace.DIResolvable
import com.ownid.sdk.namespace.OperationEntry
import com.ownid.sdk.ops.OperationController
import com.ownid.sdk.ops.OperationFailure
import com.ownid.sdk.ops.OperationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.rules.TestWatcher
import org.junit.runner.Description

internal fun accessTokenFor(subject: String): AccessToken {
    val payload = """{"sub":"$subject"}"""
    return AccessToken("header.${payload.encodeToByteArray().encodeToBase64UrlSafe()}.signature")
}

internal object JsonSerialization : Serialization {
    override val json: Json = Json
}

internal object AcceptingLoginIdValidator : LoginIdValidator {
    override fun determineLoginIdType(loginId: String): LoginId.Type =
        if ("@" in loginId) LoginId.Type.Email else LoginId.Type.UserName

    override fun validate(loginId: LoginId): LoginId = loginId
}

@OptIn(InternalOwnIdAPI::class)
internal class RecordingUserJourney : UserJourney {
    data class StartedFlow(val name: String?, val source: FlowInfo.Source, val traceParent: String?)
    data class SwitchedFlow(val flowId: String?, val name: String?, val source: FlowInfo.Source)
    data class CompletedOperation(val operationId: OperationID, val errorCode: ErrorCode?, val source: String?, val message: String?)

    val startedFlows = mutableListOf<StartedFlow>()
    val userInfos = mutableListOf<LoginId>()
    val referers = mutableListOf<String>()
    val switchedFlows = mutableListOf<SwitchedFlow>()
    val startedOperations = mutableListOf<OperationID>()
    val operationClicks = mutableListOf<OperationID>()
    val completedOperations = mutableListOf<CompletedOperation>()
    val completedOutcomes = mutableListOf<UserJourney.Controller.Outcome>()

    override suspend fun startFlow(name: String?, source: FlowInfo.Source, traceParent: String?): UserJourney.Controller {
        startedFlows += StartedFlow(name, source, traceParent)
        return Controller()
    }

    private inner class Controller : UserJourney.Controller {
        override suspend fun setUserInfo(loginId: LoginId) {
            userInfos += loginId
        }

        override suspend fun setReferer(referer: String) {
            referers += referer
        }

        override suspend fun switchToFlow(flowId: String?, name: String?, source: FlowInfo.Source) {
            switchedFlows += SwitchedFlow(flowId, name, source)
        }

        override suspend fun startOperation(operationId: OperationID) {
            startedOperations += operationId
        }

        override suspend fun addOperationClick(operationId: OperationID) {
            operationClicks += operationId
        }

        override suspend fun completeOperation(operationId: OperationID, errorCode: ErrorCode?, source: String?, message: String?) {
            completedOperations += CompletedOperation(operationId, errorCode, source, message)
        }

        override fun completeFlow(outcome: UserJourney.Controller.Outcome) {
            completedOutcomes += outcome
        }
    }
}

@OptIn(InternalOwnIdAPI::class)
internal class RecordingOperationEntry<P, S, F : OperationFailure>(
    override val operationType: OperationType,
    var availability: Availability = Availability.Available(),
    private val unsatisfiedDependencies: List<String>? = null
) : OperationEntry<P, S, F>, DIResolvable {
    val availabilityParams = mutableListOf<P>()
    val startParams = mutableListOf<P>()
    val startedOperationIds = mutableListOf<OperationID>()
    val abortReasons = mutableListOf<Reason>()
    val results = ArrayDeque<OperationResult<S, F>>()

    override suspend fun availability(params: P): Availability {
        availabilityParams += params
        return availability
    }

    override fun start(params: P): OperationController<S, F> {
        startParams += params
        val operationId = OperationID(operationType, "${operationType.value}-${startParams.size}")
        startedOperationIds += operationId
        return CompletedOperationController(
            operationId = operationId,
            result = results.removeFirstOrNull() ?: error("No queued result for $operationType"),
            onAbort = { abortReasons += it }
        )
    }

    override fun canResolve(): Boolean = unsatisfiedDependencies == null
    override fun getUnsatisfiedDependencies(): List<String>? = unsatisfiedDependencies
}

@OptIn(InternalOwnIdAPI::class)
internal fun <P, S, F : OperationFailure> recordingEntry(
    operationType: OperationType,
    vararg results: OperationResult<S, F>,
    availability: Availability = Availability.Available()
): RecordingOperationEntry<P, S, F> =
    RecordingOperationEntry<P, S, F>(operationType, availability).apply {
        this.results.addAll(results)
    }

@OptIn(InternalOwnIdAPI::class)
internal fun <P, S, F : OperationFailure> unavailableEntry(operationType: OperationType): RecordingOperationEntry<P, S, F> =
    RecordingOperationEntry(operationType, unsatisfiedDependencies = listOf("fake missing dependency"))

private class CompletedOperationController<S, F : OperationFailure>(
    override val operationId: OperationID,
    private val result: OperationResult<S, F>,
    private val onAbort: (Reason) -> Unit
) : OperationController<S, F> {
    override suspend fun whenSettled(): OperationResult<S, F> = result
    override fun abort(reason: Reason) = onAbort(reason)
}

@OptIn(ExperimentalCoroutinesApi::class)
internal class MainDispatcherRule(
    val testDispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
