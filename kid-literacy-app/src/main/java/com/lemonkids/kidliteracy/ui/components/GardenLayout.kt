package com.lemonkids.kidliteracy.ui.components

import kotlin.math.floor

/** 只放大宽屏中的设计尺寸；标准 Pad 和窄窗口保持原有尺寸。 */
internal fun gardenLayoutScale(availableWidthDp: Float): Float =
    (availableWidthDp / 1000f).coerceIn(1f, 1.35f)

internal fun gardenContentWidth(availableWidthDp: Float, baseMaxDp: Float, scale: Float): Float =
    minOf(availableWidthDp, baseMaxDp * scale)

/** 字格按当前窗口确定，数据量和加载状态不会改变尺寸。 */
internal fun gardenGridColumns(widthDp: Float, fontScale: Float = 1f): Int {
    val minimum = 88f * fontScale.coerceAtLeast(1f)
    return floor((widthDp + 12f) / (minimum + 12f)).toInt().coerceIn(1, 6)
}

internal fun gardenMenuColumns(widthDp: Float, fontScale: Float): Int =
    if (widthDp / fontScale.coerceAtLeast(1f) >= 680f) 2 else 1

internal fun gardenPhoneticColumns(widthDp: Float, fontScale: Float): Int =
    if (widthDp >= 720f && widthDp / fontScale.coerceAtLeast(1f) >= 640f) 2 else 1

/** 首页任务卡始终两列；空间不足时只让每张卡内的字格换行。 */
internal data class HomeTileLayout(val columns: Int, val widthDp: Float, val heightDp: Float, val fontSp: Float)

internal fun homeTileLayout(contentWidthDp: Float, fontScale: Float, layoutScale: Float = 1f): HomeTileLayout {
    val scale = fontScale.coerceAtLeast(1f)
    val gap = 6f * layoutScale
    val minimumWidth = 48f * scale
    val targetWidth = 57f * scale * layoutScale
    val columns = floor((contentWidthDp + gap) / (minimumWidth + gap)).toInt().coerceIn(1, 6)
    val width = ((contentWidthDp - gap * (columns - 1)) / columns).coerceAtMost(targetWidth)
    return HomeTileLayout(columns, width, width * 1.19f, minOf(40f * layoutScale, width * .70f / scale))
}
