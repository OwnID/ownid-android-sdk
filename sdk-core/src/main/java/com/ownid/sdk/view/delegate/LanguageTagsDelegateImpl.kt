package com.ownid.sdk.view.delegate

import android.view.View
import androidx.core.os.ConfigurationCompat
import com.ownid.sdk.InternalOwnIdAPI

internal class LanguageTagsDelegateImpl : LanguageTagsDelegate {
    private var languageTagsProducer: (() -> List<String>) = { emptyList() }
    private var languageTags: List<String> = emptyList()

    override fun setWebAppLanguageListProducer(producer: (() -> List<String>)) {
        languageTagsProducer = producer
    }

    override fun setWebAppLanguageList(languages: List<String>) {
        languageTags = languages
    }

    @InternalOwnIdAPI
    override fun getLanguageTags(view: View): String {
        val producerList = languageTagsProducer.invoke()
        if (producerList.isNotEmpty()) return producerList.joinToString(separator = ",")

        if (languageTags.isNotEmpty()) return languageTags.joinToString(separator = ",")

        return ConfigurationCompat.getLocales(view.resources.configuration).toLanguageTags()
    }
}