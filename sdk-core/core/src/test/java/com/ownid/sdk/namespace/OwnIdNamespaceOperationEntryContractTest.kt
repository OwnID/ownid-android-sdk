package com.ownid.sdk.namespace

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.api.ApiResult
import com.ownid.sdk.capability.Capability
import com.ownid.sdk.di.DIContainer
import com.ownid.sdk.di.MissingDependencyException
import com.ownid.sdk.di.getOrThrow
import com.ownid.sdk.di.impl.DIContainerImpl
import com.ownid.sdk.di.register
import com.ownid.sdk.di.registerFactory
import com.ownid.sdk.model.Availability
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.InstanceName
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.OperationID
import com.ownid.sdk.model.OperationType
import com.ownid.sdk.model.Reason
import com.ownid.sdk.ops.OperationCapability
import com.ownid.sdk.ops.OperationController
import com.ownid.sdk.ops.OperationFailure
import com.ownid.sdk.ops.OperationResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test

@OptIn(InternalOwnIdAPI::class)
// Covers: API-010, API-020, OPS-010, OPS-020, OPS-060, OPS-070
internal class OwnIdNamespaceOperationEntryContractTest {

    @Test
    fun apiEntryExposesStartOnlyAndResolvesRuntimeAtStartTime(): Unit = runTest {
        val container = DIContainerImpl(scopeName = "api-entry")
        val createdRuntimes = mutableListOf<TestApiRuntime>()
        container.registerFactory<TestApiRuntime> {
            TestApiRuntime().also(createdRuntimes::add)
        }
        val entry = apiEntry(container, TestApiRuntime::class.java, TestApiRuntime::start)

        assertThat(entry).isNotInstanceOf(OperationEntry::class.java)
        assertThat(entry).isNotInstanceOf(PreflightFlowEntry::class.java)
        assertThat(createdRuntimes).isEmpty()

        val first = entry.start(TestParams("first")).successValue()
        val second = entry.start(TestParams("second")).successValue()

        assertThat(first).isEqualTo("started:first")
        assertThat(second).isEqualTo("started:second")
        assertThat(createdRuntimes).hasSize(2)
        assertThat(createdRuntimes[0].startParams).containsExactly(TestParams("first"))
        assertThat(createdRuntimes[1].startParams).containsExactly(TestParams("second"))
    }

    @Test
    fun operationEntryAvailabilityResolvesRuntimeWithoutStartingOperation(): Unit = runTest {
        val runtime = TestOperationRuntime()
        val entry = operationEntry(
            container = containerWithRuntime(runtime),
            runtimeClass = TestOperationRuntime::class.java,
            operationType = OperationType.SESSION_CREATION
        )
        val params = TestParams("preflight")

        val availability = entry.availability(params)

        assertThat(availability).isInstanceOf(Availability.Available::class.java)
        assertThat(runtime.availabilityParams).containsExactly(params)
        assertThat(runtime.startParams).isEmpty()
    }

    @Test
    fun operationEntryStartResolvesRuntimeAndReturnsRuntimeController() {
        val runtime = TestOperationRuntime()
        val entry = operationEntry(
            container = containerWithRuntime(runtime),
            runtimeClass = TestOperationRuntime::class.java,
            operationType = OperationType.SESSION_CREATION
        )
        val params = TestParams("start")

        val controller = entry.start(params)

        assertThat(controller).isSameInstanceAs(runtime.controller)
        assertThat(runtime.startParams).containsExactly(params)
    }

    @Test
    fun operationEntryAvailabilityReportsMissingRuntimeButStartStillThrows(): Unit = runTest {
        val container = DIContainerImpl(scopeName = "missing-runtime")
        val entry = operationEntry(
            container = container,
            runtimeClass = TestOperationRuntime::class.java,
            operationType = OperationType.SESSION_CREATION
        )

        val availability = entry.availability(TestParams("preflight"))

        assertThat(availability).isInstanceOf(Availability.Unavailable::class.java)
        assertThat((availability as Availability.Unavailable).message).contains("Missing dependencies")
        assertThat(availability.message).contains(TestOperationRuntime::class.java.name)
        assertThrows(MissingDependencyException::class.java) {
            entry.start(TestParams("start"))
        }
    }

    @Test
    fun operationEntryUsesFactoryRuntimePerResolutionAndRegisteredRuntimeWhenShared() {
        val factoryContainer = DIContainerImpl(scopeName = "factory")
        val factoryRuntimes = mutableListOf<TestOperationRuntime>()
        factoryContainer.registerFactory<TestOperationRuntime> {
            TestOperationRuntime().also(factoryRuntimes::add)
        }
        val factoryEntry = operationEntry(
            container = factoryContainer,
            runtimeClass = TestOperationRuntime::class.java,
            operationType = OperationType.SESSION_CREATION
        )

        val firstFactoryController = factoryEntry.start(TestParams("first"))
        val secondFactoryController = factoryEntry.start(TestParams("second"))

        assertThat(factoryRuntimes).hasSize(2)
        assertThat(firstFactoryController).isSameInstanceAs(factoryRuntimes[0].controller)
        assertThat(secondFactoryController).isSameInstanceAs(factoryRuntimes[1].controller)

        val sharedRuntime = TestOperationRuntime()
        val sharedEntry = operationEntry(
            container = containerWithRuntime(sharedRuntime),
            runtimeClass = TestOperationRuntime::class.java,
            operationType = OperationType.SESSION_CREATION
        )

        assertThat(sharedEntry.start(TestParams("shared-1"))).isSameInstanceAs(sharedRuntime.controller)
        assertThat(sharedEntry.start(TestParams("shared-2"))).isSameInstanceAs(sharedRuntime.controller)
        assertThat(sharedRuntime.startParams.map { it?.value }).containsExactly("shared-1", "shared-2").inOrder()
    }

    @Test
    fun scopedOperationEntryUsesOperationLocalScopeWithoutMutatingParentOrSiblingScopes() {
        val parentMarker = ScopeMarker("parent")
        val scopedMarker = ScopeMarker("scoped")
        val siblingMarker = ScopeMarker("sibling")
        val container = DIContainerImpl(scopeName = "parent").apply {
            register(InstanceName.Default)
            register(parentMarker)
            registerFactory<TestOperationRuntime>(ScopeMarker::class.java) {
                TestOperationRuntime(scopeMarker = getOrThrow())
            }
        }
        val scopedEntry = scopedOperationEntry(
            container = container,
            runtimeClass = TestOperationRuntime::class.java,
            operationType = OperationType.LOGIN_ID_COLLECT
        )

        val plainRuntime = scopedEntry.start(TestParams("plain")).runtime()
        val scopedRuntime = scopedEntry.withOperationScope(scopeName = "operation-scope") {
            register(scopedMarker)
        }.start(TestParams("scoped")).runtime()
        val siblingRuntime = scopedEntry.withOperationScope(scopeName = "sibling-scope") {
            register(siblingMarker)
        }.start(TestParams("sibling")).runtime()
        val plainAgainRuntime = scopedEntry.start(TestParams("plain-again")).runtime()

        assertThat(scopedEntry.instanceName).isEqualTo(InstanceName.Default)
        assertThat(plainRuntime.scopeMarker).isSameInstanceAs(parentMarker)
        assertThat(scopedRuntime.scopeMarker).isSameInstanceAs(scopedMarker)
        assertThat(siblingRuntime.scopeMarker).isSameInstanceAs(siblingMarker)
        assertThat(plainAgainRuntime.scopeMarker).isSameInstanceAs(parentMarker)
        assertThat(container.getOrThrow<ScopeMarker>()).isSameInstanceAs(parentMarker)
    }

    private fun containerWithRuntime(runtime: TestOperationRuntime): DIContainer =
        DIContainerImpl(scopeName = "operation-entry").apply {
            register(runtime)
        }

    private fun OperationController<String, TestFailure>.runtime(): TestOperationRuntime =
        (this as TestOperationController).runtime

    private data class TestParams(val value: String) : Capability.Params

    private class ScopeMarker(val value: String)

    private class TestApiRuntime {
        val startParams = mutableListOf<TestParams>()

        suspend fun start(params: TestParams): ApiResult<String, Nothing> {
            startParams += params
            return ApiResult.Success("started:${params.value}")
        }
    }

    private class TestOperationRuntime(
        val scopeMarker: ScopeMarker? = null
    ) : OperationCapability<TestParams, String, TestFailure> {
        val availabilityParams = mutableListOf<Capability.Params?>()
        val startParams = mutableListOf<TestParams?>()
        val controller = TestOperationController(this)

        override val operationType: OperationType = OperationType.SESSION_CREATION

        override fun start(params: TestParams?): OperationController<String, TestFailure> {
            startParams += params
            return controller
        }

        override suspend fun availability(params: Capability.Params?): Availability {
            availabilityParams += params
            return Availability.Available()
        }
    }

    private class TestOperationController(
        val runtime: TestOperationRuntime
    ) : OperationController<String, TestFailure> {
        override val operationId: OperationID = OperationID(OperationType.SESSION_CREATION, "test")

        override suspend fun whenSettled(): OperationResult<String, TestFailure> = OperationResult.Success("ok")

        override fun abort(reason: Reason): Unit = Unit
    }

    private object TestFailure : OperationFailure {
        override val errorCode: ErrorCode = ErrorCode.Unknown
        override val message: String = "unused"
    }

    private fun <Success, Failure> ApiResult<Success, Failure>.successValue(): Success {
        assertThat(this).isInstanceOf(ApiResult.Success::class.java)
        @Suppress("UNCHECKED_CAST")
        return (this as ApiResult.Success<Success>).success
    }
}
