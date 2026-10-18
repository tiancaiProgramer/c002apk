package com.example.c001apk.compose.ui.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RailLayoutPolicyTest {
    @Test fun approvedTabletKeepsItsPixelPositions() {
        val density = 2.75f
        val spacing = mainRailItemSpacing((1904f - 90f) / density - 8f, 3040f / density)
        val firstTopPx = 90f + (4f + spacing.topPaddingDp) * density
        assertEquals(246f, firstTopPx, 0.5f)
        assertEquals(235f, (60f + spacing.additionalItemPaddingDp) * density, 0.5f)
        assertEquals(33f, spacing.startPaddingDp * density, 0.5f)
    }

    @Test fun largerWindowsScaleOffsetsAndSpacingByTheirDimensions() {
        val smaller = mainRailItemSpacing(600f, 900f)
        val larger = mainRailItemSpacing(900f, 1350f)
        assertEquals(smaller.topPaddingDp * 1.5f, larger.topPaddingDp, 0.001f)
        assertEquals(smaller.startPaddingDp * 1.5f, larger.startPaddingDp, 0.001f)
        assertEquals((60f + smaller.additionalItemPaddingDp) * 1.5f,
            60f + larger.additionalItemPaddingDp, 0.001f)
    }

    @Test fun compactHeightKeepsTouchAreasSeparateAndAllThreeItemsVisible() {
        for (height in listOf(180f, 240f, 320f, 600f, 900f)) {
            val spacing = mainRailItemSpacing(height, 800f)
            val centerDistance = 60f + spacing.additionalItemPaddingDp
            assertTrue(spacing.topPaddingDp >= 0f)
            assertTrue(centerDistance >= 60f)
            assertTrue(spacing.topPaddingDp + 2 * centerDistance + 56f <= height)
        }
    }
}
