package com.ownid.sdk.view.popup.tooltip

import android.view.View
import androidx.annotation.RestrictTo
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.view.OwnIdImageButton
import com.ownid.sdk.view.popup.Popup

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class Tooltip(anchorView: OwnIdImageButton, popupView: View, position: Position) : Popup(anchorView, popupView, position) {

    @InternalOwnIdAPI
    public class Properties(
        internal val textAppearance: Int? = null,
        internal val textColor: Int? = null,
        backgroundColor: Int? = null,
        borderColor: Int? = null,
        position: Position? = null
    ) : Popup.Properties(backgroundColor, borderColor, position)
}