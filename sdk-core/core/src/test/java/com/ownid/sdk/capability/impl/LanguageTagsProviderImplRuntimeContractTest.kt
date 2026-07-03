package com.ownid.sdk.capability.impl

import android.app.Application
import android.content.ComponentCallbacks
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LanguageTag
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@OptIn(InternalOwnIdAPI::class)
// Covers: CFG-RUNTIME-060
internal class LanguageTagsProviderImplRuntimeContractTest {

    private val app: Application
        get() = RuntimeEnvironment.getApplication()

    @Test
    fun configurationCallbacksUpdateAutomaticLanguageTags() {
        val context = CallbackHarnessContext(app)
        context.setConfiguration(Locale.US)

        languageTagsProvider(context).use { provider ->
            assertThat(context.registeredComponentCallbackCount).isEqualTo(1)
            assertThat(provider.languageTagsFlow.value).containsExactly(LanguageTag("en", "US"))

            context.dispatchConfigurationChanged(Locale.CANADA_FRENCH, Locale.US)

            assertThat(provider.languageTagsFlow.value).containsExactly(LanguageTag("fr", "CA"), LanguageTag("en", "US")).inOrder()
        }

        assertThat(context.registeredComponentCallbackCount).isEqualTo(0)
    }

    @Test
    fun explicitLanguageOverrideUnregistersUntilEmptyResetRestoresAutomaticTracking() {
        val context = CallbackHarnessContext(app)
        context.setConfiguration(Locale.US)
        languageTagsProvider(context).use { provider ->
            provider.setLanguageTags(listOf("de-DE"))

            assertThat(provider.languageTagsFlow.value).containsExactly(LanguageTag("de", "DE"))
            assertThat(context.registeredComponentCallbackCount).isEqualTo(0)

            context.dispatchConfigurationChanged(Locale.ITALY)

            assertThat(provider.languageTagsFlow.value).containsExactly(LanguageTag("de", "DE"))

            provider.setLanguageTags(emptyList())

            assertThat(context.registeredComponentCallbackCount).isEqualTo(1)
            assertThat(provider.languageTagsFlow.value).containsExactly(LanguageTag("it", "IT"))

            context.dispatchConfigurationChanged(Locale.JAPAN)

            assertThat(provider.languageTagsFlow.value).containsExactly(LanguageTag("ja", "JP"))
        }

        assertThat(context.registeredComponentCallbackCount).isEqualTo(0)
    }

    private fun languageTagsProvider(context: Context): LanguageTagsProviderImpl =
        LanguageTagsProviderImpl(context = context, logger = null)

    private class CallbackHarnessContext(base: Context) : ContextWrapper(base) {
        private val callbackApplication = CallbackApplication()

        val registeredComponentCallbackCount: Int
            get() = callbackApplication.registeredComponentCallbackCount

        override fun getApplicationContext(): Context = callbackApplication

        fun setConfiguration(vararg locales: Locale) {
            val configuration = configurationFor(*locales)
            @Suppress("DEPRECATION")
            resources.updateConfiguration(configuration, resources.displayMetrics)
        }

        fun dispatchConfigurationChanged(vararg locales: Locale) {
            val configuration = configurationFor(*locales)
            @Suppress("DEPRECATION")
            resources.updateConfiguration(configuration, resources.displayMetrics)
            callbackApplication.dispatchConfigurationChanged(configuration)
        }

        private fun configurationFor(vararg locales: Locale): Configuration =
            Configuration(resources.configuration).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    setLocales(LocaleList(*locales))
                } else {
                    @Suppress("DEPRECATION")
                    locale = locales.first()
                }
            }
    }

    private class CallbackApplication : Application() {
        private val componentCallbacks = mutableListOf<ComponentCallbacks>()

        val registeredComponentCallbackCount: Int
            get() = componentCallbacks.size

        override fun registerComponentCallbacks(callback: ComponentCallbacks) {
            componentCallbacks += callback
        }

        override fun unregisterComponentCallbacks(callback: ComponentCallbacks) {
            componentCallbacks -= callback
        }

        fun dispatchConfigurationChanged(configuration: Configuration) {
            componentCallbacks.toList().forEach { it.onConfigurationChanged(configuration) }
        }
    }
}
