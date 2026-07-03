package com.ownid.sdk.ops.impl

import com.ownid.sdk.logger.OwnIdLogRouter
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.OperationID
import com.ownid.sdk.ops.OperationController
import com.ownid.sdk.ops.OperationRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Default instance-scoped [OperationRegistry] implementation.
 *
 * Registry updates publish a full active-controller state through [OperationRegistry.current]. Duplicate registrations
 * are recovery-only and do not relax the owner contract: one [OperationID] belongs to one controller lifecycle, and the
 * operation owner unregisters that controller after settlement or cleanup.
 */
@InternalOwnIdAPI
internal class OperationRegistryImpl(
    private val logger: OwnIdLogRouter? = null
) : OperationRegistry {
    private val _current = MutableStateFlow<Map<OperationID, OperationController<*, *>>>(emptyMap())

    override val current: StateFlow<Map<OperationID, OperationController<*, *>>> = _current.asStateFlow()

    @Synchronized
    internal fun register(controller: OperationController<*, *>) {
        logger?.logD(this, "register", "${controller.operationId}")

        if (_current.value.containsKey(controller.operationId)) {
            logger?.logW(this, "register", "[${controller.operationId}] is already registered")
        }

        _current.update { it + (controller.operationId to controller) }
    }

    @Synchronized
    internal fun unregister(id: OperationID) {
        logger?.logD(this, "unregister", "$id")

        _current.update { it.minus(id) }
    }
}
