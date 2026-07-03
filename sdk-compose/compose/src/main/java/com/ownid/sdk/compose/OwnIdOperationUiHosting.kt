@file:JvmName("OwnIdOperationUiEntries")

package com.ownid.sdk.compose

import androidx.annotation.CheckResult
import androidx.compose.runtime.Stable
import com.ownid.sdk.di.getOrNull
import com.ownid.sdk.di.register
import com.ownid.sdk.logger.OwnIdLogRouter
import com.ownid.sdk.model.AccessOrProofToken
import com.ownid.sdk.model.Availability
import com.ownid.sdk.model.InstanceName
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.OperationID
import com.ownid.sdk.model.OperationType
import com.ownid.sdk.model.Reason
import com.ownid.sdk.namespace.OperationEntry
import com.ownid.sdk.namespace.ScopedOperationEntry
import com.ownid.sdk.ops.EmailVerificationOperation
import com.ownid.sdk.ops.EmailVerificationOperationFailure
import com.ownid.sdk.ops.LoginIdCollectOperation
import com.ownid.sdk.ops.LoginIdCollectOperationFailure
import com.ownid.sdk.ops.OperationController
import com.ownid.sdk.ops.OperationFailure
import com.ownid.sdk.ops.OperationResult
import com.ownid.sdk.ops.PhoneVerificationOperation
import com.ownid.sdk.ops.PhoneVerificationOperationFailure
import com.ownid.sdk.ui.capability.OperationUiContainer

/**
 * Returns an email-verification operation entry whose UI is rendered by the app with [OwnIdOperationUi].
 *
 * Use this when you want to present OwnID verification inside your Compose hierarchy, sheet, dialog, or overlay
 * instead of letting the SDK present its built-in container.
 */
@get:JvmName("getEmailVerificationOperationUiEntry")
public val OperationEntry<EmailVerificationOperation.Params?, AccessOrProofToken, EmailVerificationOperationFailure>.appHostedComponent:
        OwnIdOperationUiEntry<EmailVerificationOperation.Params?, AccessOrProofToken, EmailVerificationOperationFailure>
    get() = withOperationUiHostingScope("OperationUIHosting.${OperationType.EMAIL_VERIFICATION.value}")

/**
 * Returns a phone-verification operation entry whose UI is rendered by the app with [OwnIdOperationUi].
 *
 * Use this when you want to present OwnID verification inside your Compose hierarchy, sheet, dialog, or overlay
 * instead of letting the SDK present its built-in container.
 */
@get:JvmName("getPhoneVerificationOperationUiEntry")
public val OperationEntry<PhoneVerificationOperation.Params?, AccessOrProofToken, PhoneVerificationOperationFailure>.appHostedComponent:
        OwnIdOperationUiEntry<PhoneVerificationOperation.Params?, AccessOrProofToken, PhoneVerificationOperationFailure>
    get() = withOperationUiHostingScope("OperationUIHosting.${OperationType.PHONE_NUMBER_VERIFICATION.value}")

/**
 * Returns a login-ID collection operation entry whose UI is rendered by the app with [OwnIdOperationUi].
 *
 * Use this when you want to present the collection form inside your Compose hierarchy, sheet, dialog, or overlay
 * instead of letting the SDK present its built-in container.
 */
@get:JvmName("getLoginIdCollectionOperationUiEntry")
public val OperationEntry<LoginIdCollectOperation.Params?, LoginId, LoginIdCollectOperationFailure>.appHostedComponent:
        OwnIdOperationUiEntry<LoginIdCollectOperation.Params?, LoginId, LoginIdCollectOperationFailure>
    get() = withOperationUiHostingScope("OperationUIHosting.${OperationType.LOGIN_ID_COLLECT.value}")

/**
 * OwnID operation prepared for app-hosted Compose rendering.
 *
 * Use this wrapper when your app owns the presentation for an operation. Check [availability] or [isAvailable] with
 * the same parameters you plan to pass to [start]. For optional-parameter operations, the no-argument overloads pass
 * `null`, which lets the operation resolve defaults from the current OwnID context.
 *
 * With an app-hosted entry, the SDK starts the operation but leaves presentation to your app. [start] returns an
 * [OwnIdOperationUiController] that should be kept in UI state, rendered by [OwnIdOperationUi], and observed for its
 * terminal [OperationResult]. Availability is a preflight signal only; [start] may still settle with cancellation or a
 * typed failure if runtime state changes.
 *
 * @param Params Operation parameter type.
 * @param Success Operation success payload type.
 * @param Failure Operation-specific typed failure payload returned from [OwnIdOperationUiController.whenSettled].
 */
@Stable
public class OwnIdOperationUiEntry<Params, Success, Failure : OperationFailure> internal constructor(
    private val instanceName: InstanceName,
    private val delegate: OperationEntry<Params, Success, Failure>
) {
    /** Type of operation started by this entry. */
    public val operationType: OperationType get() = delegate.operationType

    /** Checks whether this operation can start with [params]. Use the same value you plan to pass to [start]. */
    public suspend fun availability(params: Params): Availability = delegate.availability(params)

    /** Returns `true` when this operation can start with [params]. Use [availability] when the reason matters. */
    public suspend fun isAvailable(params: Params): Boolean = delegate.isAvailable(params)

    /**
     * Starts the operation for rendering in [OwnIdOperationUi].
     *
     * Keep the returned controller while the operation is relevant to your screen. Render it with [OwnIdOperationUi]
     * and observe its terminal result through [OwnIdOperationUiController.whenSettled]. Starting more than one operation
     * is app-owned; keep each operation controller tied to the UI and presentation container that owns that operation.
     *
     * This method starts the operation but does not show SDK-owned UI. If the operation cannot proceed, the returned
     * controller settles with [OperationResult.Failure] or [OperationResult.Canceled].
     */
    @CheckResult
    public fun start(params: Params): OwnIdOperationUiController<Success, Failure> =
        OwnIdOperationUiController(instanceName, delegate.start(params))
}

/**
 * Returns availability for operations whose parameter object is optional.
 *
 * This overload passes `null` parameters, allowing the operation to use values from the current OwnID context.
 */
public suspend fun <Params, Success, Failure : OperationFailure> OwnIdOperationUiEntry<Params?, Success, Failure>.availability(): Availability =
    availability(null)

/**
 * Returns `true` when an optional-parameter operation can currently start.
 *
 * This overload passes `null` parameters. Use [availability] when the unavailable reason matters.
 */
public suspend fun <Params, Success, Failure : OperationFailure> OwnIdOperationUiEntry<Params?, Success, Failure>.isAvailable(): Boolean =
    isAvailable(null)

/**
 * Starts an optional-parameter operation without passing parameters.
 *
 * This overload passes `null` parameters, allowing the operation to use values from the current OwnID context.
 */
@CheckResult
public fun <Params, Success, Failure : OperationFailure> OwnIdOperationUiEntry<Params?, Success, Failure>.start(): OwnIdOperationUiController<Success, Failure> =
    start(null)

/**
 * Controls one app-hosted OwnID operation rendered by [OwnIdOperationUi].
 *
 * The controller is tied to the OwnID SDK instance that created it. Keep it while the operation is relevant to your UI,
 * render it with [OwnIdOperationUi], and call [whenSettled] from your own coroutine when you need the terminal
 * [OperationResult].
 *
 * [OwnIdOperationUi] handles cancellation while your app keeps the controller tied to either an embedded composable or
 * a reported app-owned container lifecycle. If your UI stops owning an unsettled controller without rendering
 * [OwnIdOperationUi] or without reporting the container close, call [abort] with a meaningful [Reason] or [close] for
 * owner-driven cleanup. The controller does not retain any app UI state.
 *
 * A controller represents one started operation. It does not restart an operation after settlement; start a new entry
 * when you need another operation run.
 *
 * @param Success Operation success payload type.
 * @param Failure Operation-specific typed failure payload returned when settlement is [OperationResult.Failure].
 */
@Stable
public class OwnIdOperationUiController<Success, Failure : OperationFailure> internal constructor(
    internal val instanceName: InstanceName,
    private val delegate: OperationController<Success, Failure>
) {
    /** Unique identifier for this operation instance. */
    public val operationId: OperationID get() = delegate.operationId

    internal val operationControllerBase: OperationController<Success, Failure> get() = delegate

    /**
     * Suspends until the operation reaches a terminal result.
     *
     * Multiple coroutines may await the same controller. Canceling a coroutine that is only waiting here does not
     * cancel the operation; use [abort] or [close] when the owner needs to stop an unsettled operation.
     */
    public suspend fun whenSettled(): OperationResult<Success, Failure> = delegate.whenSettled()

    /** Cancels the operation with [reason]. Has no effect after the operation has settled. */
    public fun abort(reason: Reason): Unit = delegate.abort(reason)

    /**
     * Releases owner-held operation resources when you stop owning an unsettled controller.
     *
     * Use [abort] instead when the app has a specific cancellation reason to propagate.
     */
    public fun close(): Unit = delegate.close()
}

@OptIn(InternalOwnIdAPI::class)
private fun <Params, Success, Failure : OperationFailure> OperationEntry<Params, Success, Failure>.withOperationUiHostingScope(
    scopeName: String
): OwnIdOperationUiEntry<Params, Success, Failure> {
    val scoped = this as? ScopedOperationEntry<Params, Success, Failure>
        ?: error("appHostedComponent is only supported for OwnID operations from an OwnID SDK instance")

    val entry = scoped.withOperationScope(scopeName) {
        register<OperationUiContainer>(NoOpOperationUiContainer(getOrNull<OwnIdLogRouter>()))
    }

    return OwnIdOperationUiEntry(instanceName = scoped.instanceName, delegate = entry)
}

@OptIn(InternalOwnIdAPI::class)
private class NoOpOperationUiContainer(
    private val logger: OwnIdLogRouter?
) : OperationUiContainer {

    override fun show(
        instanceName: InstanceName,
        operationID: OperationID,
        onShowError: (error: Throwable?, details: String?) -> Unit
    ) {
        logger?.logI(this, "show", "Skipping SDK UI container for operation UI hosting: $operationID")
    }
}
