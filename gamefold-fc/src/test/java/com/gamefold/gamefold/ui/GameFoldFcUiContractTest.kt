package com.gamefold.gamefold.ui

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class GameFoldFcUiContractTest {
    @Test
    fun gameLibraryWordmark_usesTheSharedFiveBySevenPixelGlyphs() {
        val glyphs = PixelWordmarkSpec.glyphRowsFor("GAMEFOLD FC")

        assertEquals(11, glyphs.size)
        assertArrayEquals(
            intArrayOf(0b01110, 0b10001, 0b10000, 0b10000, 0b10000, 0b10001, 0b01110),
            glyphs.last(),
        )
    }

    @Test
    fun actionButtons_swapAWithBAndXWithY_withoutMovingTheFourSlots() {
        val labelsBySlot = defaultActionButtonPlacements().associate { it.slot to it.label }

        assertEquals(
            mapOf(
                ActionButtonSlot.Top to "X",
                ActionButtonSlot.Left to "Y",
                ActionButtonSlot.Right to "A",
                ActionButtonSlot.Bottom to "B",
            ),
            labelsBySlot,
        )
    }
}
