@file:JvmName("OwnIdNamespaceEntries")

package com.ownid.sdk.namespace

import androidx.annotation.CheckResult
import com.ownid.sdk.api.ApiResult
import com.ownid.sdk.capability.Capability
import com.ownid.sdk.di.DIContainer
import com.ownid.sdk.di.DependencyResolutionException
import com.ownid.sdk.di.MissingDependencyException
import com.ownid.sdk.model.Availability
import com.ownid.sdk.model.InstanceName
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.OperationType
import com.ownid.sdk.ops.OperationCapability
import com.ownid.sdk.ops.OperationController
import com.ownid.sdk.ops.OperationFailure

/**
 * Direct namespace entry for a single OwnID API call.
 *
 * Use a direct API entry from an initialized OwnID instance when you want to call one API directly instead of going
 * through a higher-level namespace helper or flow.
 * Direct API entries return [ApiResult] and do not create an operation or flow controller.
 * Coroutine cancellation is propagated to the caller and is not represented as an [ApiResult].
 * [start] resolves the bound API runtime and returns [ApiResult] for the request outcome.
 *
 * @param Params API parameter type. Use a nullable parameter type for APIs that support `start()` without explicit
 * params.
 * @param Success Success payload returned by the API.
 * @param Failure Failure payload returned by the API.
 */
public interface APIEntry<Params, Success, Failure> {
    /**
     * Starts this API request.
     *
     * The request is launched from the caller's coroutine. Cancel the surrounding coroutine to cancel the request.
     *
     * @param params API parameters. For APIs that support omitted params, use the zero-argument [start] overload.
     *
     * @return [ApiResult.Success] with the API result, or [ApiResult.Failure] with a handled failure payload.
     *
     * @throws MissingDependencyException If the API is unavailable in the bound scope.
     * @throws DependencyResolutionException If API dependencies cannot be resolved.
     */
    public suspend fun start(params: Params): ApiResult<Success, Failure>
}

/**
 * Starts an API whose parameter type is nullable without passing an explicit parameter object.
 *
 * @throws MissingDependencyException If the API is unavailable in the bound scope.
 * @throws DependencyResolutionException If API dependencies cannot be resolved.
 */
public suspend fun <Params, Success, Failure> APIEntry<Params?, Success, Failure>.start(): ApiResult<Success, Failure> = start(null)

/**
 * Direct namespace entry for a single OwnID operation.
 *
 * Use [availability] or [isAvailable] to check readiness for specific params, and call [start] to launch the operation.
 * Each [start] call resolves a fresh operation runtime from the bound scope and returns a caller-owned
 * [OperationController] for that run. Keep the controller while the operation is active, use it to observe settlement,
 * and cancel or close it when the owning lifecycle ends.
 *
 * @param Params Operation parameter type. Use a nullable parameter type for operations that support `start()` without
 * explicit params.
 * @param Success Success payload returned by the operation.
 * @param Failure Failure payload returned by the operation.
 */
public interface OperationEntry<Params, Success, Failure : OperationFailure> {
    /** Type of the operation exposed by this entry point. */
    public val operationType: OperationType

    /**
     * Returns whether the operation can start with [params].
     *
     * If unavailable, the result carries a human-readable message explaining what the integrator needs to provide
     * or change before calling [start]. Missing or unresolved entry dependencies are returned as
     * [Availability.Unavailable].
     *
     * Availability is a preflight check only. [start] still resolves the bound scope at launch time and may throw if
     * dependencies are missing or cannot be resolved.
     */
    public suspend fun availability(params: Params): Availability

    public suspend fun isAvailable(params: Params): Boolean = availability(params) is Availability.Available

    /**
     * Starts the operation.
     *
     * @return A controller for this operation instance. Keep it while the operation is active and call
     * [OperationController.whenSettled] to await the terminal result.
     *
     * @throws MissingDependencyException If the operation is unavailable in the bound scope.
     * @throws DependencyResolutionException If operation dependencies cannot be resolved.
     */
    @CheckResult
    public fun start(params: Params): OperationController<Success, Failure>
}

/**
 * Operation entry that can create operation-local dependency scopes.
 *
 * This is an internal SDK module contract, not a public app integration contract. Optional UI modules use it to bind
 * operation-local dependencies without mutating the owning instance scope or sibling operation scopes.
 */
@InternalOwnIdAPI
public interface ScopedOperationEntry<Params, Success, Failure : OperationFailure> : OperationEntry<Params, Success, Failure> {
    /** Name of the OwnID SDK instance that owns this operation entry. */
    public val instanceName: InstanceName

    /**
     * Creates an operation-local scope and returns an entry that starts operations from that scope.
     *
     * Dependencies registered in [configure] are visible only to operations started from the returned entry.
     * The parent instance scope and sibling operation scopes are not mutated.
     */
    public fun withOperationScope(scopeName: String, configure: DIContainer.() -> Unit): OperationEntry<Params, Success, Failure>
}

/**
 * Checks operation availability without passing an explicit parameter object.
 */
public suspend fun <Params, Success, Failure : OperationFailure> OperationEntry<Params?, Success, Failure>.availability(): Availability = availability(null)

/**
 * Returns `true` when the operation can be started without passing an explicit parameter object.
 */
public suspend fun <Params, Success, Failure : OperationFailure> OperationEntry<Params?, Success, Failure>.isAvailable(): Boolean = isAvailable(null)

/**
 * Starts an operation whose parameter type is nullable without passing an explicit parameter object.
 *
 * @throws MissingDependencyException If the operation is unavailable in the bound scope.
 * @throws DependencyResolutionException If operation dependencies cannot be resolved.
 */
@CheckResult
public fun <Params, Success, Failure : OperationFailure> OperationEntry<Params?, Success, Failure>.start(): OperationController<Success, Failure> = start(null)

/**
 * Direct namespace entry for a single OwnID flow.
 *
 * Use [start] to launch the flow for the selected namespace member.
 * Flow controllers are owned by the caller; keep the returned controller while the flow is active and use its
 * settlement API to await the final flow result.
 * Entry creation does not resolve the underlying runtime; [start] resolves it from the bound scope.
 *
 * @param FlowContext Flow context type. Use a nullable context type for flows that support `start()` without explicit
 * context.
 * @param Controller Controller type returned from the flow.
 */
public interface FlowEntry<FlowContext, Controller> {
    /**
     * Starts the flow.
     *
     * @return A controller for the running flow.
     *
     * @throws MissingDependencyException If the flow is unavailable in the bound scope.
     * @throws DependencyResolutionException If flow dependencies cannot be resolved.
     */
    @CheckResult
    public fun start(context: FlowContext): Controller
}

/**
 * Starts a flow whose context type is nullable without passing an explicit context object.
 *
 * @throws MissingDependencyException If the flow is unavailable in the bound scope.
 * @throws DependencyResolutionException If flow dependencies cannot be resolved.
 */
@CheckResult
public fun <Context, Controller> FlowEntry<Context?, Controller>.start(): Controller = start(null)

/**
 * Direct namespace entry for a flow that supports runtime preflight checks.
 *
 * Use [availability] or [isAvailable] to check readiness for a specific context before calling [start].
 * Entry creation does not resolve the underlying runtime; [availability] and [start] resolve it from the bound scope.
 *
 * @param FlowContext Flow context type. Use a nullable context type for flows that support `start()` without explicit
 * context.
 * @param Controller Controller type returned from the flow.
 */
public interface PreflightFlowEntry<FlowContext, Controller> : FlowEntry<FlowContext, Controller> {
    /**
     * Returns whether the flow can start with [context].
     *
     * If unavailable, the result carries a human-readable message explaining what the integrator needs to provide
     * or change before calling [start]. Missing or unresolved entry dependencies are returned as
     * [Availability.Unavailable].
     *
     * Availability is a preflight check only. [start] still resolves the bound scope at launch time and may throw if
     * dependencies are missing or cannot be resolved.
     */
    public suspend fun availability(context: FlowContext): Availability

    public suspend fun isAvailable(context: FlowContext): Boolean = availability(context) is Availability.Available
}

/**
 * Checks preflight flow availability without passing an explicit context object.
 */
public suspend fun <FlowContext, Controller> PreflightFlowEntry<FlowContext?, Controller>.availability(): Availability = availability(null)

/**
 * Returns `true` when the preflight flow can be started without passing an explicit context object.
 */
public suspend fun <FlowContext, Controller> PreflightFlowEntry<FlowContext?, Controller>.isAvailable(): Boolean = isAvailable(null)

@InternalOwnIdAPI
internal interface DIResolvable {
    fun canResolve(): Boolean

    fun getUnsatisfiedDependencies(): List<String>?
}

@InternalOwnIdAPI
internal fun <Runtime : Any, Params, Success, Failure> apiEntry(
    container: DIContainer,
    runtimeClass: Class<Runtime>,
    start: suspend Runtime.(Params) -> ApiResult<Success, Failure>
): APIEntry<Params, Success, Failure> = object : APIEntry<Params, Success, Failure>, DIResolvable {
    private fun runtime(): Runtime = container.getOrThrow(runtimeClass)

    override suspend fun start(params: Params): ApiResult<Success, Failure> = runtime().start(params)

    override fun canResolve(): Boolean = container.canResolve(runtimeClass)

    override fun getUnsatisfiedDependencies(): List<String>? = container.getUnsatisfiedDependencies(runtimeClass)
}

@InternalOwnIdAPI
internal fun <Runtime, RuntimeParams, Params, Success, Failure> operationEntry(
    container: DIContainer,
    runtimeClass: Class<Runtime>,
    operationType: OperationType
): OperationEntry<Params, Success, Failure>
        where Runtime : OperationCapability<RuntimeParams, Success, Failure>, RuntimeParams : Capability.Params, Params : RuntimeParams?, Failure : OperationFailure =
    object : OperationEntry<Params, Success, Failure>, DIResolvable {

        private fun runtime(): Runtime = container.getOrThrow(runtimeClass)

        override val operationType: OperationType = operationType

        override suspend fun availability(params: Params): Availability = try {
            runtime().availability(params)
        } catch (error: MissingDependencyException) {
            val details = container.getUnsatisfiedDependencies(runtimeClass)?.joinToString() ?: error.message ?: error.toString()
            Availability.Unavailable("Missing dependencies: $details")
        } catch (error: DependencyResolutionException) {
            Availability.Unavailable(error.message ?: error.toString())
        }

        override fun start(params: Params): OperationController<Success, Failure> = runtime().start(params)

        override fun canResolve(): Boolean = container.canResolve(runtimeClass)

        override fun getUnsatisfiedDependencies(): List<String>? = container.getUnsatisfiedDependencies(runtimeClass)
    }

@InternalOwnIdAPI
internal fun <Runtime, RuntimeParams, Params, Success, Failure> scopedOperationEntry(
    container: DIContainer,
    runtimeClass: Class<Runtime>,
    operationType: OperationType
): ScopedOperationEntry<Params, Success, Failure>
        where Runtime : OperationCapability<RuntimeParams, Success, Failure>, RuntimeParams : Capability.Params, Params : RuntimeParams?, Failure : OperationFailure {

    val delegate = operationEntry<Runtime, RuntimeParams, Params, Success, Failure>(
        container = container,
        runtimeClass = runtimeClass,
        operationType = operationType
    )

    return object : ScopedOperationEntry<Params, Success, Failure>, OperationEntry<Params, Success, Failure> by delegate, DIResolvable {
        private val resolvable = delegate as DIResolvable

        override val instanceName: InstanceName get() = container.getOrThrow(InstanceName::class.java)

        override fun withOperationScope(scopeName: String, configure: DIContainer.() -> Unit): OperationEntry<Params, Success, Failure> =
            scopedOperationEntry<Runtime, RuntimeParams, Params, Success, Failure>(
                container = container.createScope(scopeName).apply(configure),
                runtimeClass = runtimeClass,
                operationType = operationType
            )

        override fun canResolve(): Boolean = resolvable.canResolve()

        override fun getUnsatisfiedDependencies(): List<String>? = resolvable.getUnsatisfiedDependencies()
    }
}

@InternalOwnIdAPI
internal fun <Runtime : Any, FlowContext, Controller> flowEntry(
    container: DIContainer,
    runtimeClass: Class<Runtime>,
    start: Runtime.(FlowContext) -> Controller
): FlowEntry<FlowContext, Controller> = object : FlowEntry<FlowContext, Controller>, DIResolvable {
    private fun runtime(): Runtime = container.getOrThrow(runtimeClass)

    override fun start(context: FlowContext): Controller = runtime().start(context)

    override fun canResolve(): Boolean = container.canResolve(runtimeClass)

    override fun getUnsatisfiedDependencies(): List<String>? = container.getUnsatisfiedDependencies(runtimeClass)
}

@InternalOwnIdAPI
internal fun <Runtime : Any, FlowContext, Controller> preflightFlowEntry(
    container: DIContainer,
    runtimeClass: Class<Runtime>,
    availability: suspend Runtime.(FlowContext) -> Availability,
    start: Runtime.(FlowContext) -> Controller
): PreflightFlowEntry<FlowContext, Controller> = object : PreflightFlowEntry<FlowContext, Controller>, DIResolvable {
    private fun runtime(): Runtime = container.getOrThrow(runtimeClass)

    override suspend fun availability(context: FlowContext): Availability = try {
        runtime().availability(context)
    } catch (error: MissingDependencyException) {
        val details = container.getUnsatisfiedDependencies(runtimeClass)?.joinToString() ?: error.message ?: error.toString()
        Availability.Unavailable("Missing dependencies: $details")
    } catch (error: DependencyResolutionException) {
        Availability.Unavailable(error.message ?: error.toString())
    }

    override fun start(context: FlowContext): Controller = runtime().start(context)

    override fun canResolve(): Boolean = container.canResolve(runtimeClass)

    override fun getUnsatisfiedDependencies(): List<String>? = container.getUnsatisfiedDependencies(runtimeClass)
}
