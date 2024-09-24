package com.ownid.sdk.internal.webbridge

import com.google.common.truth.Truth
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnIdCoreImpl
import com.ownid.sdk.internal.feature.webbridge.OwnIdWebViewBridgeContext
import com.ownid.sdk.internal.feature.webbridge.OwnIdWebViewBridgeImpl
import com.ownid.sdk.internal.feature.webbridge.handler.OwnIdWebViewBridgeMetadata
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(InternalOwnIdAPI::class, ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
public class OwnIdWebViewBridgeMetadataTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    public fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    public fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    public fun `handle - GET action - success`(): TestResult = runTest {
        val ownIdCoreMockk = mockk<OwnIdCoreImpl> {
            every { correlationId } returns "testCorrelationId"
        }

        val slotResult = slot<String>()
        val bridgeContext = mockk<OwnIdWebViewBridgeContext> {
            every { ownIdCore } returns ownIdCoreMockk
            every { coroutineContext } returns Job() + testDispatcher
            every { finishWithSuccess(capture(slotResult)) } returns Unit
        }

        OwnIdWebViewBridgeMetadata.handle(bridgeContext, "GET", null)

        advanceUntilIdle()

        Truth.assertThat(slotResult.captured).isEqualTo("{\"correlationId\":\"testCorrelationId\"}")
    }

    @Test
    public fun `handle - unsupported action - throws exception`(): TestResult = runTest {
        val slotHandler = slot<OwnIdWebViewBridgeImpl.NamespaceHandler>()
        val slotError = slot<Throwable>()
        val bridgeContext = mockk<OwnIdWebViewBridgeContext> {
            every { coroutineContext } returns Job() + testDispatcher
            every { finishWithError(capture(slotHandler), capture(slotError)) } returns Unit
        }

        OwnIdWebViewBridgeMetadata.handle(bridgeContext, "unsupported", null)

        advanceUntilIdle()

        Truth.assertThat(slotHandler.captured).isInstanceOf(OwnIdWebViewBridgeMetadata::class.java)
        Truth.assertThat(slotError.captured).isInstanceOf(IllegalArgumentException::class.java)
        Truth.assertThat(slotError.captured.message)
            .isEqualTo("OwnIdWebViewBridgeMetadata: Unsupported action: 'unsupported'")
    }
}