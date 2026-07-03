@file:JvmName("OwnIdOperationResults")

package com.ownid.sdk.ops

import androidx.annotation.CheckResult
import com.ownid.sdk.capability.Capability
import com.ownid.sdk.model.Availability
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.ErrorStrings
import com.ownid.sdk.model.InstanceName
import com.ownid.sdk.model.OperationID
import com.ownid.sdk.model.OperationType
import com.ownid.sdk.model.Reason
import com.ownid.sdk.model.UiError
import kotlinx.coroutines.flow.StateFlow

/**
 * Result of a completed operation.
 *
 * Every operation finishes with exactly one of [Success], [Canceled], or [Failure]. [Canceled] carries a [Reason] and
 * is separate from failure handling. [Failure] carries a typed [OperationFailure] with diagnostic text and a message
 * lookup key.
 */
public sealed interface OperationResult<out R, out F : OperationFailure> {
    public class Success<R>(public val success: R) : OperationResult<R, Nothing> {
        override fun toString(): String = "Success"
    }

    public class Canceled(public val reason: Reason) : OperationResult<Nothing, Nothing> {
        override fun toString(): String = "Canceled(reason=$reason)"
    }

    public class Failure<F : OperationFailure>(public val failure: F) : OperationResult<Nothing, F> {
        override fun toString(): String = "Failure(failure=$failure)"
    }
}

/**
 * Failure payload returned by completed operations.
 *
 * The concrete operation failure type identifies the semantic reason the operation stopped. Callers should branch on
 * the operation's typed failure hierarchy when deciding the next step, such as keeping the current surface open, showing
 * an error, offering another method, retrying later, or ending the current interaction.
 *
 * [message] is diagnostic text from the backend, SDK, or provider path. It is not localized end-user copy. Operation UI
 * state uses [UiError] for user-facing text. [errorCode] is a localization key, not the semantic failure contract.
 * Code that shows an OwnID error should map the failure to owned copy or call [ErrorCode.toLocalizedMessage] when the
 * SDK default text is appropriate.
 */
public interface OperationFailure {
    /** Localization key for resolving failure text. */
    public val errorCode: ErrorCode

    /** Diagnostic message associated with the failure. */
    public val message: String
}

/**
 * Returns a UI-ready error for this operation failure.
 *
 * The returned [UiError] uses this failure's [OperationFailure.errorCode]. The message is resolved from the OwnID
 * strings available for [instanceName], or from [fallbackErrorStrings] when the instance has no message for this code.
 * This conversion is only for display text; it does not change the typed failure that operation owners should branch on.
 */
public fun OperationFailure.toUiError(
    instanceName: InstanceName = InstanceName.Default,
    fallbackErrorStrings: ErrorStrings = ErrorStrings.DEFAULT
): UiError = UiError(errorCode, errorCode.toLocalizedMessage(instanceName, fallbackErrorStrings))

/**
 * Controls one active operation lifecycle.
 *
 * Use [operationId] to identify the running operation and [whenSettled] to await its terminal [OperationResult]. A
 * controller represents exactly one started operation and always settles once with [OperationResult.Success],
 * [OperationResult.Canceled], or [OperationResult.Failure].
 *
 * Cancellation semantics follow [Capability.Controller]: use [Capability.Controller.abort] when the owner has a
 * semantic cancellation reason, and [Capability.Controller.close] for owner-driven lifecycle cleanup. Cancellation is
 * best-effort; a request made after settlement does not change the terminal result.
 *
 * Operation-specific controllers may expose a state [StateFlow]. Treat its terminal state and
 * [whenSettled] as completion signals. The flow keeps the latest state available for new collectors and emits state
 * transitions while the operation is active; it is not a one-shot completion channel.
 */
public interface OperationController<Result, Failure : OperationFailure> : Capability.Controller {

    /** Unique identifier for this operation instance. */
    public val operationId: OperationID

    /**
     * Suspends until the operation completes and returns the [OperationResult].
     *
     * Multiple owners may await the same controller. After settlement, this function returns the stored terminal
     * result immediately. Use [abort] or [close] to request operation cancellation; canceling a coroutine that is only
     * waiting here does not define the operation's terminal [OperationResult].
     *
     * @return [OperationResult.Success], [OperationResult.Canceled], or [OperationResult.Failure].
     */
    public suspend fun whenSettled(): OperationResult<Result, Failure>
}

/**
 * Base interface for all OwnID operation runtimes.
 *
 * Call [start] to launch the operation and receive an [OperationController], and use the suspending [isAvailable] or
 * [availability] checks to verify runtime readiness for the given parameters.
 *
 * A runtime object owns a single operation lifecycle. Once [start] moves that runtime out of its initial state, repeated
 * [start] calls on the same object return the same controller and do not restart the operation. Public namespace
 * operation entries create a fresh operation-local runtime for each launch, so call the namespace entry again when a new
 * operation run is needed.
 *
 * Availability is a preflight signal only; [start] revalidates inputs and dependencies and may still settle with
 * [OperationResult.Failure] or [OperationResult.Canceled] if runtime state changes.
 *
 * @param Params The operation-specific parameter type.
 * @param Result The success result type produced by this operation.
 * @param Failure The typed failure payload produced by this operation.
 */
public interface OperationCapability<Params : Capability.Params, Result, Failure : OperationFailure> : Capability {

    public val operationType: OperationType

    /**
     * Launches the operation with optional [params].
     *
     * @param params Operation-specific parameters. Defaults to null.
     *
     * @return An [OperationController] to observe or cancel the operation.
     *
     * The owner of the returned controller should close it when that owner lifecycle ends.
     */
    @CheckResult
    public fun start(params: Params? = null): OperationController<Result, Failure>

    public override suspend fun isAvailable(params: Capability.Params?): Boolean = availability(params) is Availability.Available

    /**
     * Returns whether the operation can start with the given [params].
     *
     * Pass `null` to check availability without explicit parameters.
     *
     * If unavailable, the result carries a human-readable message explaining what the integrator needs to provide
     * or change before calling [start].
     */
    public suspend fun availability(params: Capability.Params?): Availability
}

/** Marker interface for SDK UI capabilities that can present or bind operation state. */
public interface OperationUi : Capability

public inline fun <R, F : OperationFailure> OperationResult<R, F>.onSuccess(action: (R) -> Unit): OperationResult<R, F> {
    if (this is OperationResult.Success) action(success)
    return this
}

public inline fun <R, F : OperationFailure> OperationResult<R, F>.onError(action: (F) -> Unit): OperationResult<R, F> {
    if (this is OperationResult.Failure) action(failure)
    return this
}

public inline fun <R, F : OperationFailure> OperationResult<R, F>.onCanceled(action: (Reason) -> Unit): OperationResult<R, F> {
    if (this is OperationResult.Canceled) action(reason)
    return this
}

public inline fun <R, F : OperationFailure, T> OperationResult<R, F>.fold(
    onSuccess: (R) -> T,
    onCanceled: (Reason) -> T,
    onError: (F) -> T
): T = when (this) {
    is OperationResult.Success -> onSuccess(success)
    is OperationResult.Canceled -> onCanceled(reason)
    is OperationResult.Failure -> onError(failure)
}

public inline fun <R, F : OperationFailure, T> OperationResult<R, F>.map(transform: (R) -> T): OperationResult<T, F> = when (this) {
    is OperationResult.Success -> OperationResult.Success(transform(success))
    is OperationResult.Canceled -> this
    is OperationResult.Failure -> this
}

public fun <R, F : OperationFailure> OperationResult<R, F>.getOrNull(): R? = when (this) {
    is OperationResult.Success -> success
    is OperationResult.Canceled, is OperationResult.Failure -> null
}

public fun <R, F : OperationFailure> OperationResult<R, F>.errorOrNull(): F? = when (this) {
    is OperationResult.Failure -> failure
    is OperationResult.Success, is OperationResult.Canceled -> null
}

public fun <R, F : OperationFailure> OperationResult<R, F>.reasonOrNull(): Reason? = when (this) {
    is OperationResult.Canceled -> reason
    is OperationResult.Success, is OperationResult.Failure -> null
}
