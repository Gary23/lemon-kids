package com.lemonkids.kidtask.feature.home

/** Measured, fixed slot at the start of one gesture, in parent coordinates. */
internal data class DragSlot(val top: Float, val height: Float) {
    val centre: Float get() = top + height / 2f
}

internal fun dragPreviewOrder(originalOrder: List<String>, draggedId: String, targetIndex: Int): List<String> {
    if (draggedId !in originalOrder || targetIndex !in originalOrder.indices) return originalOrder
    return originalOrder.toMutableList().apply {
        remove(draggedId)
        add(targetIndex, draggedId)
    }
}

/** Every decision comes from the same measured snapshot, never from the previous preview. */
internal fun dragTargetIndex(
    originalOrder: List<String>,
    draggedId: String,
    distancePx: Float,
    slots: Map<String, DragSlot>
): Int {
    val start = originalOrder.indexOf(draggedId)
    val origin = slots[draggedId] ?: return start
    if (start < 0 || originalOrder.any { it !in slots }) return start
    val centre = origin.centre + distancePx
    var target = start
    if (distancePx > 0f) {
        for (index in start + 1..originalOrder.lastIndex) {
            if (centre < slots.getValue(originalOrder[index]).centre) break
            target = index
        }
    } else if (distancePx < 0f) {
        for (index in start - 1 downTo 0) {
            if (centre > slots.getValue(originalOrder[index]).centre) break
            target = index
        }
    }
    return target
}

/** Offset of each card from its original fixed slot into the preview order. */
internal fun dragSlotOffsets(
    originalOrder: List<String>,
    previewOrder: List<String>,
    slots: Map<String, DragSlot>,
    spacingPx: Float
): Map<String, Float> {
    if (originalOrder.isEmpty() || originalOrder.toSet() != previewOrder.toSet() ||
        originalOrder.size != previewOrder.size || originalOrder.any { it !in slots }) return emptyMap()
    var top = slots.getValue(originalOrder.first()).top
    return buildMap {
        previewOrder.forEach { id ->
            put(id, top - slots.getValue(id).top)
            top += slots.getValue(id).height + spacingPx
        }
    }
}

internal fun dragEdgeScrollDelta(
    isActiveDrag: Boolean,
    pointerY: Float,
    viewportTop: Float,
    viewportBottom: Float,
    edgePx: Float,
    stepPx: Float
): Float = when {
    !isActiveDrag || viewportBottom <= viewportTop -> 0f
    pointerY < viewportTop + edgePx -> -stepPx
    pointerY > viewportBottom - edgePx -> stepPx
    else -> 0f
}

internal fun dragDistanceFromPointer(startWindowY: Float, currentWindowY: Float, scrolledPx: Float): Float =
    currentWindowY - startWindowY + scrolledPx
