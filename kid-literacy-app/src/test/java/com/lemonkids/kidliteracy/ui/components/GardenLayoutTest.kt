package com.lemonkids.kidliteracy.ui.components

import org.junit.Assert.*
import org.junit.Test

class GardenLayoutTest {
    @Test fun wideTabletGrowsContentAndTilesWithoutChangingReferenceSize() {
        assertEquals(1f, gardenLayoutScale(1000f), 0.001f)
        assertEquals(1.205f, gardenLayoutScale(1205f), 0.001f)
        assertEquals(1.35f, gardenLayoutScale(1800f), 0.001f)
        assertEquals(815f, gardenContentWidth(1000f, 815f, gardenLayoutScale(1000f)), 0.001f)
        assertEquals(980f, gardenContentWidth(1205f, 815f, gardenLayoutScale(1205f)), 3f)
        val standard = homeTileLayout(371f, 1f)
        val large = homeTileLayout(450f, 1f, gardenLayoutScale(1205f))
        assertEquals(6, large.columns)
        assertTrue(large.widthDp > standard.widthDp)
        assertTrue(large.fontSp > standard.fontSp)
    }

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

    @Test fun homeTilesFitSixAcrossReferenceTabletCard() {
        val layout = homeTileLayout(371f, 1f)
        assertEquals(6, layout.columns)
        assertTrue(layout.widthDp in 56f..57f)
        assertTrue(layout.heightDp in 66f..69f)
        assertTrue(layout.widthDp * 6 + 6f * 5 <= 371f)
    }

    @Test fun homeCardsStayTwoColumnsWhileTilesWrapInsideNarrowCards() {
        listOf(240f, 190f, 110f).forEach { width ->
            val layout = homeTileLayout(width, 1f)
            assertTrue(layout.columns in 1..4)
            assertTrue(layout.widthDp >= 48f)
            assertTrue(layout.widthDp * layout.columns + 6f * (layout.columns - 1) <= width)
        }
        val largeFont = homeTileLayout(240f, 1.5f)
        assertTrue(largeFont.columns < homeTileLayout(240f, 1f).columns)
        assertTrue(largeFont.widthDp >= 72f)
    }
}
