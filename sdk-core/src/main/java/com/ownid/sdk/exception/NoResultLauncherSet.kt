package com.ownid.sdk.exception

import androidx.activity.result.ActivityResultLauncher
import com.ownid.sdk.viewmodel.OwnIdLifecycleObserver

/**
 * Exception when OwnID ViewModel does not have [ActivityResultLauncher] set. See [OwnIdLifecycleObserver]
 */
public class NoResultLauncherSet : OwnIdException("No ActivityResultLauncher set")