package com.dyne.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class IslandGeometryTest {
    @Test
    fun expandedWidthStaysInsideTypicalPhoneDisplay() {
        assertEquals(365f, maxExpandedIslandWidth(420f, 393f), 0.001f)
    }

    @Test
    fun requestedWidthIsPreservedWhenItFits() {
        assertEquals(350f, maxExpandedIslandWidth(350f, 393f), 0.001f)
    }

    @Test
    fun veryNarrowDisplayNeverProducesNegativeWidth() {
        assertEquals(1f, maxExpandedIslandWidth(350f, 20f), 0.001f)
    }

    @Test
    fun unknownDisplayWidthKeepsConfiguredWidth() {
        assertEquals(350f, maxExpandedIslandWidth(350f, 0f), 0.001f)
    }
}
