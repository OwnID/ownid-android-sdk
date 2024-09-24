package com.ownid.sdk.internal.webbridge

import com.google.common.truth.Truth
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.internal.feature.webbridge.handler.OwnIdWebViewBridgeFlow
import com.ownid.sdk.internal.feature.webflow.AccountProviderWrapper
import com.ownid.sdk.internal.feature.webflow.AccountRegisterEvent
import com.ownid.sdk.internal.feature.webflow.OwnIdFlowAction
import com.ownid.sdk.internal.feature.webflow.OwnIdFlowEventBus
import com.ownid.sdk.internal.feature.webflow.SessionProviderWrapper
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(InternalOwnIdAPI::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
public class OwnIdWebViewBridgeFlowConfigTests {
    private val eventBus: OwnIdFlowEventBus.EventBus = mockk()

    @Test
    internal fun `create should create Config with correct actions and wrappers`() {
        val wrapper1 = mockk<AccountProviderWrapper>()
        val wrapper2 = mockk<SessionProviderWrapper>()

        val wrappers = listOf(wrapper1, wrapper2)

        val config = OwnIdWebViewBridgeFlow.Config.create(wrappers, eventBus)

        Truth.assertThat(config.actions).hasLength(2)
        Truth.assertThat(config.actions.asList()).containsExactly("account_register", "session_create")
        Truth.assertThat(config.actionWrapperMap).hasSize(2)
        Truth.assertThat(config.actionWrapperMap.values).containsExactly(wrapper1, wrapper2)
    }

    @Test
    internal fun `getFlowEvent should return correct event for valid action`() {
        val wrapper = mockk<AccountProviderWrapper>()
        val wrappers = listOf(wrapper)
        val config = OwnIdWebViewBridgeFlow.Config.create(wrappers, eventBus)
        val action = OwnIdFlowAction.ACCOUNT_REGISTER.webAction
        val params = """{loginId:"some@email.com", profile:"profiledata", ownIdData:"ownIdData"}"""
        val webViewCallback = mockk<(String?) -> Unit>()

        val event = config.getFlowEvent(action, params, webViewCallback)

        Truth.assertThat(event).isInstanceOf(AccountRegisterEvent::class.java)
        Truth.assertThat(event.wrapper).isEqualTo(wrapper)
        Truth.assertThat((event.payload as AccountRegisterEvent.Payload).loginId).isEqualTo("some@email.com")
        Truth.assertThat((event.payload as AccountRegisterEvent.Payload).rawProfile).isEqualTo("profiledata")
        Truth.assertThat((event.payload as AccountRegisterEvent.Payload).ownIdData).isEqualTo("ownIdData")
    }

    @Test
    internal fun `getFlowEvent should throw IllegalArgumentException for invalid action`() {
        val wrapper = mockk<AccountProviderWrapper>()
        val wrappers = listOf(wrapper)
        val config = OwnIdWebViewBridgeFlow.Config.create(wrappers, eventBus)
        val action = "invalid_action"
        val params = "{}"
        val webViewCallback = mockk<(String?) -> Unit>()

        try {
            config.getFlowEvent(action, params, webViewCallback)
            assert(false)
        } catch (e: IllegalArgumentException) {
            Truth.assertThat(e.message).isEqualTo("OwnIdWebViewBridgeFlow: Unsupported action: $action")
        }
    }
}