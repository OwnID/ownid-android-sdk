package com.ownid.sdk.view.tooltip

import com.ownid.sdk.InternalOwnIdAPI

@InternalOwnIdAPI
public enum class TooltipPosition {
    TOP, BOTTOM, START, END;

    public fun isHorizontal(): Boolean = this == START || this == END
    public fun isVertical(): Boolean = this == TOP || this == BOTTOM
}