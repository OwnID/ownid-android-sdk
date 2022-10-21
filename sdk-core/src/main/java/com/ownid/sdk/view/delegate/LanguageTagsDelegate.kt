package com.ownid.sdk.view.delegate

import android.view.View
import com.ownid.sdk.InternalOwnIdAPI

public interface LanguageTagsDelegate {
    /**
     * Set a language list producer for OwnID Web App.
     *
     * If producer is set and returned list is not empty, then it will be used to get language values for OwnID Web App.
     * The value from [setWebAppLanguageList] will be ignored.
     *
     * @param producer  a function that returns language TAGs list for OwnID Web App
     * (well-formed [IETF BCP 47 language tag](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Accept-Language)) as a [String].
     * To remove existing producer, pass empty list as parameter.
     */
    public fun setWebAppLanguageListProducer(producer: (() -> List<String>))

    /**
     * Set a language list for OwnID Web App.
     *
     * If language list is set by this method and this list is not empty, then it will be used as the language values for
     * OwnID Web App. This language list is ignored if producer is set by [setWebAppLanguageListProducer] with non empty list.
     *
     * @param languages  language TAGs list for OwnID Web App
     * (well-formed [IETF BCP 47 language tag](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Accept-Language)) as a [String].
     * To remove language value, pass empty list as parameter.
     */
    public fun setWebAppLanguageList(languages: List<String>)

    @JvmSynthetic
    @InternalOwnIdAPI
    public fun getLanguageTags(view: View): String
}