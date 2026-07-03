@file:JvmName("OwnIdComposeIcons")
@file:JvmMultifileClass

package com.ownid.sdk.compose.components.icon

import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Checkmark icon asset.
 *
 * The icon does not define accessibility text. The consuming surface owns any completion semantics.
 */
public val OwnIdIcons.Checkmark: ImageVector
    get() {
        if (_Checkmark != null) {
            return _Checkmark!!
        }
        _Checkmark = materialIcon(name = "OwnID.Icons.Checkmark") {
            materialPath {
                moveTo(10.0f, 17.0f)
                lineTo(5.0f, 12.0f)
                lineTo(6.41f, 10.59f)
                lineTo(10.0f, 14.17f)
                lineTo(17.59f, 6.58f)
                lineTo(19.0f, 8.0f)
                lineTo(10.0f, 17.0f)
                close()
            }
        }
        return _Checkmark!!
    }

private var _Checkmark: ImageVector? = null
