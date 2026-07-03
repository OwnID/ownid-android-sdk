package com.ownid.sdk.capability

import com.ownid.sdk.model.LanguageTag
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages and observes the active language tags for the SDK.
 *
 * The root provider is shared by SDK instances in the current process. It supplies normalized [LanguageTag] values for
 * locale-sensitive SDK behavior, including the `Accept-Language` header and UI strings.
 *
 * Passing a non-empty list to [setLanguageTags] installs an explicit process-wide override. The provider stops Android
 * configuration tracking and updates only through later [setLanguageTags] calls until an empty list restores tracking.
 * Duplicate resolved tags are removed, and [LanguageTag.DEFAULT] is used when no usable tag can be resolved.
 */
public interface LanguageTagsProvider : Capability {

    /**
     * Sets the active language tags, replacing or restoring configuration-based language detection.
     *
     * @param tags BCP 47 language tags (for example, `["en-US", "fr-FR"]`). An empty list restores Android
     * configuration-based tracking and immediately publishes the current configuration languages.
     */
    public fun setLanguageTags(tags: List<String>)

    /**
     * Emits the current list of [LanguageTag]s.
     *
     * This hot flow always has the latest value. It updates on Android configuration changes while automatic tracking
     * is active and on [setLanguageTags] calls.
     */
    public val languageTagsFlow: StateFlow<List<LanguageTag>>
}
