package com.ownid.sdk.viewmodel

import android.content.Intent
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.CallSuper
import androidx.annotation.MainThread
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnIdCoreImpl
import com.ownid.sdk.OwnIdInstance
import com.ownid.sdk.internal.component.OwnIdInternalLogger

/**
 * Base ViewModel class for any OwnID ViewModel.
 */
public abstract class OwnIdBaseViewModel @InternalOwnIdAPI constructor(internal val ownIdInstance: OwnIdInstance) : ViewModel() {

    @JvmField
    @InternalOwnIdAPI
    internal val ownIdCore: OwnIdCoreImpl = ownIdInstance.ownIdCore as OwnIdCoreImpl

    @JvmField
    @InternalOwnIdAPI
    protected val hasIntegration: Boolean = ownIdInstance.ownIdIntegration != null

    @InternalOwnIdAPI
    protected abstract val resultRegistryKey: String

    @InternalOwnIdAPI
    protected abstract fun onActivityResult(result: ActivityResult)

    private var resultLauncher: ActivityResultLauncher<Intent>? = null

    @InternalOwnIdAPI
    @Throws(IllegalArgumentException::class)
    protected fun launchActivity(intent: Intent) {
        requireNotNull(resultLauncher) { "${this::class.java.simpleName}: resultLauncher is not set" }.launch(intent)
    }

    @MainThread
    @JvmSynthetic
    @InternalOwnIdAPI
    internal fun createResultLauncher(resultRegistry: ActivityResultRegistry, owner: LifecycleOwner) {
        resultLauncher = resultRegistry.register(resultRegistryKey, owner, ActivityResultContracts.StartActivityForResult()) { result ->
            onActivityResult(result)
        }
    }

    @MainThread
    @JvmSynthetic
    @InternalOwnIdAPI
    public fun createResultLauncher(resultRegistry: ActivityResultRegistry) {
        unregisterResultLauncher()
        resultLauncher = resultRegistry.register(resultRegistryKey, ActivityResultContracts.StartActivityForResult()) { result ->
            onActivityResult(result)
        }
    }

    @MainThread
    @JvmSynthetic
    @InternalOwnIdAPI
    public fun unregisterResultLauncher() {
        resultLauncher?.unregister()
    }

    /**
     * Set a language TAGs provider for OwnID SDK.
     *
     * If provider is set and returned TAGs string is not empty, then it will be used in OwnID SDK.
     * The value from [setLanguageTags] will be ignored.
     *
     * @param provider  A function that returns language TAGs (well-formed [IETF BCP 47 language tag](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Accept-Language)) as a [String].
     * To remove existing provider, pass `null` as parameter.
     */
    @MainThread
    @OptIn(InternalOwnIdAPI::class)
    public fun setLanguageTagsProvider(provider: (() -> String)?) {
        ownIdCore.localeService.setLanguageTagsProvider(provider)
    }

    /**
     * Set a language TAGs for OwnID SDK.
     *
     * If language TAGs are set by this method and [languageTags] is not empty string, then it will be used in OwnID SDK.
     *
     * @param languageTags  Language TAGs [String] (well-formed [IETF BCP 47 language tag](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Accept-Language)).
     * To remove existing language TAGs, pass `null` as parameter.
     */
    @MainThread
    @OptIn(InternalOwnIdAPI::class)
    public fun setLanguageTags(languageTags: String?) {
        ownIdCore.localeService.setLanguageTags(languageTags)
    }

    @CallSuper
    @InternalOwnIdAPI
    override fun onCleared() {
        OwnIdInternalLogger.logD(this, "onCleared", "Invoked")
        resultLauncher = null
    }
}