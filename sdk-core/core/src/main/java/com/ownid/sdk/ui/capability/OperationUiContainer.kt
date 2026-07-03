package com.ownid.sdk.ui.capability

import androidx.annotation.UiThread
import com.ownid.sdk.capability.Capability
import com.ownid.sdk.model.InstanceName
import com.ownid.sdk.model.OperationID

/**
 * Presents SDK operation UI by hosting an operation in the app's view hierarchy.
 *
 * Implementations are presentation bridges for SDK-owned operation UI. They locate or create a platform container
 * for [operationID] in [instanceName] and report immediate presentation startup failures through [onShowError].
 *
 * Operation state, cancellation, and terminal results remain owned by the operation controller. A container
 * implementation must not complete an operation by itself or turn dismissal into success or failure. It should only
 * present, dismiss, and surface presentation failures to the SDK.
 */
public interface OperationUiContainer : Capability {
    /**
     * Presents the operation UI for the given [operationID].
     *
     * This call is expected to return after scheduling or starting presentation. Implementations may launch an
     * activity, show a dialog, or attach another host view. Startup failures, including missing presentation
     * infrastructure or launch rejection, are reported through [onShowError] instead of being thrown.
     *
     * @param instanceName The SDK instance that owns the operation.
     * @param operationID The operation whose UI should be displayed.
     * @param onShowError Called if the container fails to present. The SDK maps that presentation failure into the
     *     operation result.
     */
    @UiThread
    public fun show(instanceName: InstanceName, operationID: OperationID, onShowError: (error: Throwable?, details: String?) -> Unit)
}
