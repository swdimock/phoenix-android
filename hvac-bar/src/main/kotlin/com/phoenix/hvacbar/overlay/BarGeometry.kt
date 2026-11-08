package com.phoenix.hvacbar.overlay

import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager

/** Size and window placement of the bar. Matches the stock `com.syu.air` bar on the 1200x1600 panel. */
object BarGeometry {

    /** Bar height in physical pixels. */
    const val HEIGHT_PX = 227

    /** Window parameters for the bottom-anchored overlay. Touches outside the bar pass through. */
    fun overlayLayoutParams(): WindowManager.LayoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        HEIGHT_PX,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.BOTTOM
        title = "HvacBar"
    }
}
