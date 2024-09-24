package com.ownid.sdk.internal.webflow

import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.internal.feature.webflow.OnCloseEvent
import com.ownid.sdk.internal.feature.webflow.OnCloseWrapper
import com.ownid.sdk.internal.feature.webflow.OwnIdFlowEventBus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(InternalOwnIdAPI::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
public class OwnIdFlowEventBusTest {
    @Test
    public fun `create should create a new EventBus and add it to the map`(): TestResult = runTest {
        val oldSize = OwnIdFlowEventBus.eventBusMap.size
        val eventBus = OwnIdFlowEventBus.create(Job())
        Assert.assertEquals(oldSize + 1, OwnIdFlowEventBus.eventBusMap.size)
        Assert.assertTrue(OwnIdFlowEventBus.eventBusMap.containsKey(eventBus.id))
    }

    @Test
    public fun `send should send an event to the channel`(): TestResult = runTest {
        val eventBus = OwnIdFlowEventBus.create(Job())
        val event = OnCloseEvent(wrapper = OnCloseWrapper({}))
        eventBus.send(event)
        val receivedEvent = eventBus.consumeAsHotFlow().first()
        Assert.assertEquals(event, receivedEvent)
    }

    @Test
    public fun `close should cancel the scope and remove the EventBus from the map`(): TestResult = runTest {
        val job = Job()
        val eventBus = OwnIdFlowEventBus.create(job)
        eventBus.close()
        Assert.assertTrue(job.isActive.not())
        Assert.assertTrue(OwnIdFlowEventBus.eventBusMap.isEmpty())
    }

    @Test
    public fun `addInvokeOnClose should invoke the callback when the scope is cancelled`(): TestResult = runTest {
        val eventBus = OwnIdFlowEventBus.create(Job())
        var callbackInvoked = false
        eventBus.addInvokeOnClose { callbackInvoked = true }
        eventBus.close()
        Assert.assertTrue(callbackInvoked)
    }
}