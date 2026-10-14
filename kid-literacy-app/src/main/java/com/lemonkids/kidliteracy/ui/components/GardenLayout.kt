package com.lemonkids.kidliteracy.ui.components

import kotlin.math.floor

/** 字格按当前窗口确定，数据量和加载状态不会改变尺寸。 */
internal fun gardenGridColumns(widthDp: Float, fontScale: Float = 1f): Int {
    val minimum = 88f * fontScale.coerceAtLeast(1f)
    return floor((widthDp + 12f) / (minimum + 12f)).toInt().coerceIn(1, 6)
}

internal fun gardenMenuColumns(widthDp: Float, fontScale: Float): Int =
    if (widthDp / fontScale.coerceAtLeast(1f) >= 680f) 2 else 1

internal fun gardenPhoneticColumns(widthDp: Float, fontScale: Float): Int =
    if (widthDp >= 720f && widthDp / fontScale.coerceAtLeast(1f) >= 640f) 2 else 1

/** 内容不足一屏时，只在末尾导航前补空白；长内容维持正常间距。 */
internal fun gardenPrimaryPositions(totalSize: Int, sizes: IntArray, gap: Int): IntArray {
    val extra = (totalSize - sizes.sum() - gap * (sizes.size - 1).coerceAtLeast(0)).coerceAtLeast(0)
    var next = 0
    return IntArray(sizes.size) { index ->
        if (index == sizes.lastIndex) next += extra
        val position = next
        next += sizes[index] + gap
        position
    }
}
