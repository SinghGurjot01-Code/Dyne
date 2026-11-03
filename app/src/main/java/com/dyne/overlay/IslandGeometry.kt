package com.dyne.overlay

/** Camera cutout size in dp, as measured from the DisplayCutout. */
data class IslandGeometry(
    val cutoutW: Float,
    val cutoutH: Float,
    /** Full display dimensions in dp, used to clamp expanded layouts. */
    val screenW: Float = 0f,
    val screenH: Float = 0f
)

internal data class RawGeo(
    val screenW: Int, val screenH: Int,
    val cutoutCenterX: Float, val cutoutTop: Int, val cutoutW: Int, val cutoutH: Int,
    val density: Float, val portrait: Boolean, val hasCutout: Boolean
)
