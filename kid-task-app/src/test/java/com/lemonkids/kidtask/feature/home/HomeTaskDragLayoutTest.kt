package com.lemonkids.kidtask.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeTaskDragLayoutTest {
    private fun slots(order: List<String>, heights: List<Float>, gap: Float = 14f): Map<String, DragSlot> {
        var top = 0f
        return order.zip(heights).associate { (id, height) ->
            val slot = DragSlot(top, height)
            top += height + gap
            id to slot
        }
    }

    @Test fun repeatedSessionsUseOnlyTheirOwnStartingOrder() {
        val first = listOf("a", "b", "c")
        val afterFirst = dragPreviewOrder(first, "a", dragTargetIndex(first, "a", 114f,
            slots(first, listOf(100f, 100f, 100f))))
        val second = dragPreviewOrder(afterFirst, "a", dragTargetIndex(afterFirst, "a", 114f,
            slots(afterFirst, listOf(100f, 100f, 100f))))
        assertEquals(listOf("b", "a", "c"), afterFirst)
        assertEquals(listOf("b", "c", "a"), second)
        assertEquals(0, dragTargetIndex(second, "b", 0f, slots(second, listOf(100f, 100f, 100f))))
        assertEquals(second, dragPreviewOrder(second, "b", 0))
    }

    @Test fun unCrossedThirdCardNeverMoves() {
        val order = listOf("a", "b", "c")
        val geometry = slots(order, listOf(100f, 160f, 80f))
        val preview = dragPreviewOrder(order, "a", 1)
        val offsets = dragSlotOffsets(order, preview, geometry, 14f)
        assertEquals(174f, offsets.getValue("a"), 0.001f)
        assertEquals(-114f, offsets.getValue("b"), 0.001f)
        assertEquals(0f, offsets.getValue("c"), 0.001f)
    }

    @Test fun thresholdAndReturnAreSymmetricWithUnequalHeights() {
        val order = listOf("a", "b", "c")
        val geometry = slots(order, listOf(100f, 160f, 80f))
        assertEquals(0, dragTargetIndex(order, "a", 143f, geometry))
        assertEquals(1, dragTargetIndex(order, "a", 144f, geometry))
        assertEquals(1, dragTargetIndex(order, "b", -143f, geometry))
        assertEquals(0, dragTargetIndex(order, "b", -144f, geometry))
        assertEquals(0, dragTargetIndex(order, "a", 100f, geometry))
        assertEquals(order, dragPreviewOrder(order, "a", 0))
    }

    @Test fun invalidGeometryCannotCommitAFalsePreview() {
        val order = listOf("a", "b", "c")
        assertEquals(0, dragTargetIndex(order, "a", 1000f,
            mapOf("a" to DragSlot(0f, 100f))))
        assertTrue(dragSlotOffsets(order, listOf("b", "a", "c"),
            mapOf("a" to DragSlot(0f, 100f)), 14f).isEmpty())
    }

    @Test fun scrollingIsCountedOnceAndOnlyAfterActiveDrag() {
        assertEquals(0f, dragDistanceFromPointer(320f, 320f, 0f), 0.001f)
        assertEquals(26f, dragDistanceFromPointer(320f, 330f, 16f), 0.001f)
        assertEquals(0f, dragEdgeScrollDelta(false, 10f, 0f, 1000f, 80f, 18f), 0.001f)
        assertEquals(-18f, dragEdgeScrollDelta(true, 10f, 0f, 1000f, 80f, 18f), 0.001f)
        assertEquals(18f, dragEdgeScrollDelta(true, 990f, 0f, 1000f, 80f, 18f), 0.001f)
    }
}
