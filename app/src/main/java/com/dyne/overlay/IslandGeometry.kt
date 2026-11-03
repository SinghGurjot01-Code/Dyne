package com.dyne.overlay

/** Camera cutout size in dp, as measured from the DisplayCutout. */
data class IslandGeometry(val cutoutW: Float, val cutoutH: Float)

internal data class RawGeo(
    val screenW: Int, val screenH: Int,
    val cutoutCenterX: Float, val cutoutTop: Int, val cutoutW: Int, val cutoutH: Int,
    val density: Float, val portrait: Boolean, val hasCutout: Boolean
)
