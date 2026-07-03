package com.ownid.sdk.ops

import androidx.annotation.RestrictTo
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.OperationID
import kotlinx.coroutines.flow.StateFlow

/**
 * Tracks active operation controllers for one SDK instance.
 *
 * This is an internal cross-module contract for operation owners and SDK-owned UI hosts. Operation owners register a
 * controller when an operation starts and unregister it after settlement or cleanup. UI hosts observe [current] to find
 * controllers they present, but the operation owner remains responsible for settlement and registry cleanup.
 * Registration is a discovery handoff only; hosts must not treat registry removal as a cancellation request or terminal
 * operation result.
 *
 * A single [OperationID] represents one operation lifecycle. Reusing an ID for a different live controller is an
 * invariant violation by the operation owner.
 */
@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface OperationRegistry {
    /**
     * Current active operation controllers, keyed by [OperationID].
     *
     * The [StateFlow] always holds the latest registry state and emits a new immutable map when an operation owner
     * registers or unregisters a controller. Collectors should treat each emission as a full replacement state and keep
     * any presentation lifecycle tied to the controller they resolved.
     */
    public val current: StateFlow<Map<OperationID, OperationController<*, *>>>
}
