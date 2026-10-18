package com.example.c001apk.compose.ui.main

internal data class RailItemSpacing(
    val topPaddingDp: Float,
    val startPaddingDp: Float,
    val additionalItemPaddingDp: Float,
)

/** Fractions match the approved tablet layout; system insets are excluded by the caller. */
internal fun mainRailItemSpacing(availableHeightDp: Float, availableWidthDp: Float): RailItemSpacing {
    val height = availableHeightDp.coerceAtLeast(0f)
    // Material 3 1.3 supplies 56dp items and a 4dp gap. Keep their touch areas apart.
    val centerDistance = (height * 0.131f).coerceAtLeast(60f)
    val remainingTopSpace = (height - 56f - 2 * centerDistance).coerceAtLeast(0f)
    return RailItemSpacing(
        topPaddingDp = (height * 0.081f).coerceAtMost(remainingTopSpace),
        startPaddingDp = availableWidthDp.coerceAtLeast(0f) * 0.01085f,
        additionalItemPaddingDp = centerDistance - 60f,
    )
}
