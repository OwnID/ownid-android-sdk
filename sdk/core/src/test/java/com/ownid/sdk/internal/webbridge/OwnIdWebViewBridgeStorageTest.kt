package com.ownid.sdk.internal.webbridge

import com.google.common.truth.Truth
import com.ownid.sdk.AuthMethod
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnIdCoreImpl
import com.ownid.sdk.internal.OwnIdLoginIdData
import com.ownid.sdk.internal.feature.webbridge.OwnIdWebViewBridgeContext
import com.ownid.sdk.internal.feature.webbridge.OwnIdWebViewBridgeImpl
import com.ownid.sdk.internal.feature.webbridge.handler.OwnIdWebViewBridgeStorage
import io.mockk.coEvery
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
public class OwnIdWebViewBridgeStorageTest {
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
    public fun `handle - SET_LAST_USER action - success`(): TestResult = runTest {
        val slotLoginId = slot<String>()
        val slotAuthMethod = slot<AuthMethod>()

        val ownIdCoreMockk = mockk<OwnIdCoreImpl> {
            every { repository } returns mockk {
                coEvery { saveLoginId(capture(slotLoginId), capture(slotAuthMethod)) } returns Unit
            }
        }
        val slotResult = slot<String>()
        val bridgeContext = mockk<OwnIdWebViewBridgeContext> {
            every { ownIdCore } returns ownIdCoreMockk
            every { coroutineContext } returns Job() + testDispatcher
            every { finishWithSuccess(capture(slotResult)) } returns Unit
        }

        OwnIdWebViewBridgeStorage.handle(
            bridgeContext, "setlastuser", "{\"loginId\":\"testLoginId\",\"authMethod\":\"email-fallback\"}"
        )

        advanceUntilIdle()

        Truth.assertThat(slotLoginId.captured).isEqualTo("testLoginId")
        Truth.assertThat(slotAuthMethod.captured).isInstanceOf(AuthMethod.Otp::class.java)
        Truth.assertThat(slotResult.captured).isEqualTo("{}")
    }

    @Test
    public fun `handle - SET_LAST_USER action - no params - throws exception`(): TestResult = runTest {
        val slotHandler = slot<OwnIdWebViewBridgeImpl.NamespaceHandler>()
        val slotError = slot<Throwable>()
        val bridgeContext = mockk<OwnIdWebViewBridgeContext> {
            every { coroutineContext } returns Job() + testDispatcher
            every { finishWithError(capture(slotHandler), capture(slotError)) } returns Unit
        }

        OwnIdWebViewBridgeStorage.handle(bridgeContext, "setlastuser", null)

        advanceUntilIdle()

        Truth.assertThat(slotHandler.captured).isInstanceOf(OwnIdWebViewBridgeStorage::class.java)
        Truth.assertThat(slotError.captured).isInstanceOf(IllegalArgumentException::class.java)
        Truth.assertThat(slotError.captured.message)
            .isEqualTo("OwnIdWebViewBridgeStorage.invoke: No params set for 'setlastuser'")
    }

    @Test
    public fun `handle - GET_LAST_USER action - success`(): TestResult = runTest {
        val ownIdCoreMockk = mockk<OwnIdCoreImpl> {
            every { repository } returns mockk {
                coEvery { getLoginId() } returns "testLoginId"
                coEvery { getLoginIdData(any()) } returns OwnIdLoginIdData(AuthMethod.Password, 0)
            }
        }
        val slotResult = slot<String>()
        val bridgeContext = mockk<OwnIdWebViewBridgeContext> {
            every { ownIdCore } returns ownIdCoreMockk
            every { coroutineContext } returns Job() + testDispatcher
            every { finishWithSuccess(capture(slotResult)) } returns Unit
        }

        OwnIdWebViewBridgeStorage.handle(bridgeContext, "getlastuser", null)

        advanceUntilIdle()

        Truth.assertThat(slotResult.captured).isEqualTo("{\"loginId\":\"testLoginId\",\"authMethod\":\"password\"}")
    }

    @Test
    public fun `handle - GET_LAST_USER action - no saved user - returns null`(): TestResult = runTest {
        val ownIdCoreMockk = mockk<OwnIdCoreImpl> {
            every { repository } returns mockk {
                coEvery { getLoginId() } returns null
            }
        }
        val slotResult = slot<String>()
        val bridgeContext = mockk<OwnIdWebViewBridgeContext> {
            every { ownIdCore } returns ownIdCoreMockk
            every { coroutineContext } returns Job() + testDispatcher
            every { finishWithSuccess(capture(slotResult)) } returns Unit
        }

        OwnIdWebViewBridgeStorage.handle(bridgeContext, "getlastuser", null)

        advanceUntilIdle()

        Truth.assertThat(slotResult.captured).isEqualTo("null")
    }

    @Test
    public fun `handle - unsupported action - throws exception`(): TestResult = runTest {
        val slotHandler = slot<OwnIdWebViewBridgeImpl.NamespaceHandler>()
        val slotError = slot<Throwable>()
        val bridgeContext = mockk<OwnIdWebViewBridgeContext> {
            every { coroutineContext } returns Job() + testDispatcher
            every { finishWithError(capture(slotHandler), capture(slotError)) } returns Unit
        }

        OwnIdWebViewBridgeStorage.handle(bridgeContext, "unsupported", null)

        advanceUntilIdle()

        Truth.assertThat(slotHandler.captured).isInstanceOf(OwnIdWebViewBridgeStorage::class.java)
        Truth.assertThat(slotError.captured).isInstanceOf(IllegalArgumentException::class.java)
        Truth.assertThat(slotError.captured.message)
            .isEqualTo("OwnIdWebViewBridgeStorage: Unsupported action: 'unsupported'")
    }
}