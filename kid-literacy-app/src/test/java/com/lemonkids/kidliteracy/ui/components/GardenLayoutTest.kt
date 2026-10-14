package com.lemonkids.kidliteracy.ui.components

import org.junit.Assert.*
import org.junit.Test

class GardenLayoutTest {
    @Test fun standardTabletGridHasAtMostSixColumns() {
        listOf(752f, 976f, 1152f).forEach { assertEquals(6, gardenGridColumns(it)) }
        assertEquals(6, gardenGridColumns(2000f))
    }

    @Test fun narrowAndScaledGridProtectsMinimumTileWidth() {
        listOf(320f, 480f, 640f, 976f).forEach { width ->
            listOf(1f, 1.3f, 1.5f).forEach { scale ->
                val columns = gardenGridColumns(width, scale)
                val tile = (width - 12f * (columns - 1)) / columns
                assertTrue("width=$width scale=$scale tile=$tile", tile >= 88f * scale)
                assertTrue(columns in 1..6)
            }
        }
        assertEquals(1, gardenGridColumns(80f))
    }

    @Test fun menuAndPhoneticColumnsCollapseWhenFontsNeedSpace() {
        assertEquals(2, gardenMenuColumns(976f, 1f))
        assertEquals(1, gardenMenuColumns(640f, 1f))
        assertEquals(1, gardenMenuColumns(976f, 1.5f))
        assertEquals(2, gardenPhoneticColumns(900f, 1f))
        assertEquals(1, gardenPhoneticColumns(719f, 1f))
        assertEquals(1, gardenPhoneticColumns(900f, 1.5f))
    }

    @Test fun shortPagesKeepNavigationAtBottomAndLongPagesKeepSpacing() {
        val sizes = intArrayOf(100, 200, 72)
        assertArrayEquals(intArrayOf(0, 116, 728), gardenPrimaryPositions(800, sizes, 16))
        assertArrayEquals(intArrayOf(0, 116, 332), gardenPrimaryPositions(400, sizes, 16))
        assertArrayEquals(intArrayOf(), gardenPrimaryPositions(800, intArrayOf(), 16))
    }
}
