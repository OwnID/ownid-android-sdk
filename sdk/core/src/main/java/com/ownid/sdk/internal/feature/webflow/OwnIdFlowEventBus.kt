package com.ownid.sdk.internal.feature.webflow

import androidx.annotation.VisibleForTesting
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.internal.component.OwnIdInternalLogger
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@InternalOwnIdAPI
internal object OwnIdFlowEventBus {

    internal class EventBus(
        internal val job: Job,
        internal val id: String = UUID.randomUUID().toString()
    ) {
        private val actionsChannel = Channel<OwnIdFlowEvent>(Channel.UNLIMITED)

        internal fun send(flowEvent: OwnIdFlowEvent) {
            val result = actionsChannel.trySend(flowEvent)
            if (result.isClosed) {
                OwnIdInternalLogger.logW(this@EventBus, "EventBus.send", "Channel is closed [${flowEvent::class.simpleName}]")
            }
        }

        internal fun consumeAsHotFlow(): Flow<OwnIdFlowEvent> = actionsChannel.consumeAsFlow()

        internal fun close() {
            OwnIdInternalLogger.logD(this@EventBus, "EventBus.close", "Bus id: $id")
            if (job.isActive.not()) {
                OwnIdInternalLogger.logW(this@EventBus, "EventBus.close", "Scope not active")
            }
            job.cancel()
            eventBusMap.remove(id)?.actionsChannel?.cancel()
        }

        internal fun addInvokeOnClose(callback: () -> Unit) {
            job.invokeOnCompletion { callback() }
        }
    }

    /**
     * Min API must be 23, see [b/3704246](https://issuetracker.google.com/issues/37042460)
     */
    @VisibleForTesting
    internal val eventBusMap = ConcurrentHashMap<String, EventBus>()

    internal fun create(job: Job): EventBus = EventBus(job).apply {
        OwnIdInternalLogger.logD(this, "create", "Bus id: $id")
        eventBusMap[id] = this
    }
}