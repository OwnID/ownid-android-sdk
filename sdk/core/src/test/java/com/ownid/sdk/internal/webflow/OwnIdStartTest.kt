package com.ownid.sdk.internal.webflow

import com.google.common.truth.Truth
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnId
import com.ownid.sdk.OwnIdProvider
import com.ownid.sdk.OwnIdProviders
import com.ownid.sdk.internal.feature.webflow.AccountProviderWrapper
import com.ownid.sdk.internal.feature.webflow.OnCloseWrapper
import com.ownid.sdk.internal.feature.webflow.OnErrorWrapper
import com.ownid.sdk.internal.feature.webflow.OnFinishWrapper
import com.ownid.sdk.internal.feature.webflow.OwnIdFlowWrapper
import com.ownid.sdk.internal.feature.webflow.SessionProviderWrapper
import com.ownid.sdk.internal.feature.webflow.combineWrappers
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(InternalOwnIdAPI::class)
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
public class OwnIdStartTest {

    @Test
    internal fun `combineWrappers should merge global and local providers with local overriding global`() {
        val globalSessionProvider = mockk<OwnIdProvider.SessionProvider>()
        val localSessionProvider = mockk<OwnIdProvider.SessionProvider>()
        val accountProvider = mockk<OwnIdProvider.AccountProvider>()

        OwnId.providers = OwnIdProviders(
            session = globalSessionProvider,
            account = accountProvider
        )

        val eventWrappers = listOf<OwnIdFlowWrapper<*>>()
        val providers = OwnIdProviders(session = localSessionProvider)

        val result = combineWrappers(providers, eventWrappers)

        Truth.assertThat(result[0]).isInstanceOf(SessionProviderWrapper::class.java)
        Truth.assertThat((result[0] as SessionProviderWrapper).provider).isEqualTo(localSessionProvider)
        Truth.assertThat(result[1]).isInstanceOf(AccountProviderWrapper::class.java)
        Truth.assertThat((result[1] as AccountProviderWrapper).provider).isEqualTo(accountProvider)

        Truth.assertThat(result)
            .containsExactly(
                result[0],
                result[1],
                OnFinishWrapper.DEFAULT,
                OnErrorWrapper.DEFAULT,
                OnCloseWrapper.DEFAULT
            )
            .inOrder()
    }

    @Test
    internal fun `combineWrappers should use only global providers when local is null`() {
        val globalSessionProvider = mockk<OwnIdProvider.SessionProvider>()
        val accountProvider = mockk<OwnIdProvider.AccountProvider>()

        OwnId.providers = OwnIdProviders(
            session = globalSessionProvider,
            account = accountProvider
        )

        val eventWrappers = listOf<OwnIdFlowWrapper<*>>()

        val result = combineWrappers(null, eventWrappers)

        Truth.assertThat(result[0]).isInstanceOf(SessionProviderWrapper::class.java)
        Truth.assertThat((result[0] as SessionProviderWrapper).provider).isEqualTo(globalSessionProvider)
        Truth.assertThat(result[1]).isInstanceOf(AccountProviderWrapper::class.java)
        Truth.assertThat((result[1] as AccountProviderWrapper).provider).isEqualTo(accountProvider)

        Truth.assertThat(result)
            .containsExactly(
                result[0],
                result[1],
                OnFinishWrapper.DEFAULT,
                OnErrorWrapper.DEFAULT,
                OnCloseWrapper.DEFAULT
            )
            .inOrder()
    }

    @Test
    internal fun `combineWrappers should add default wrappers when not present`() {
        val eventWrappers = listOf<OwnIdFlowWrapper<*>>()

        val result = combineWrappers(null, eventWrappers)

        Truth.assertThat(result)
            .containsExactly(
                OnFinishWrapper.DEFAULT,
                OnErrorWrapper.DEFAULT,
                OnCloseWrapper.DEFAULT
            )
            .inOrder()
    }

    @Test
    internal fun `combineWrappers should not add duplicate providers`() {
        val sessionProvider = mockk<OwnIdProvider.SessionProvider>()

        OwnId.providers = OwnIdProviders(session = sessionProvider)

        val eventWrappers = listOf(OnFinishWrapper.DEFAULT)

        val result = combineWrappers(OwnIdProviders(session = sessionProvider), eventWrappers)

        Truth.assertThat(result[0]).isInstanceOf(SessionProviderWrapper::class.java)
        Truth.assertThat((result[0] as SessionProviderWrapper).provider).isEqualTo(sessionProvider)

        Truth.assertThat(result)
            .containsExactly(
                result[0],
                OnFinishWrapper.DEFAULT,
                OnErrorWrapper.DEFAULT,
                OnCloseWrapper.DEFAULT
            )
            .inOrder()
    }

    @Test
    internal fun `combineWrappers should add event wrappers`() {
        val sessionProvider = mockk<OwnIdProvider.SessionProvider>()
        OwnId.providers = OwnIdProviders(session = sessionProvider)

        val customWrapper = mockk<OwnIdFlowWrapper<*>>()
        val eventWrappers = listOf(customWrapper)

        val result = combineWrappers(null, eventWrappers)

        Truth.assertThat(result)
            .containsExactly(
                result[0],
                customWrapper,
                OnFinishWrapper.DEFAULT,
                OnErrorWrapper.DEFAULT,
                OnCloseWrapper.DEFAULT
            )
            .inOrder()
    }

    @Test
    internal fun `combineWrappers should not add default event wrappers if custom ones are provided`() {
        val sessionProvider = mockk<OwnIdProvider.SessionProvider>()
        OwnId.providers = OwnIdProviders(session = sessionProvider)

        val onFinishWrapper = mockk<OnFinishWrapper>()
        val onErrorWrapper = mockk<OnErrorWrapper>()
        val onCloseWrapper = mockk<OnCloseWrapper>()
        val eventWrappers = listOf(onFinishWrapper, onErrorWrapper, onCloseWrapper)

        val result = combineWrappers(null, eventWrappers)

        Truth.assertThat(result)
            .containsExactly(
                result[0],
                onFinishWrapper,
                onErrorWrapper,
                onCloseWrapper
            )
            .inOrder()
    }
}