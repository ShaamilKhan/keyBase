package com.keybase.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AsciiArtTest {
    private val all = AsciiArt.NAME + AsciiArt.BY

    @Test fun everyVisibleCharacterOfTheLetteringHasStrokesSoNothingIsDrawnAsAGap() {
        val missing = all.flatMap { it.toList() }.filter { it != ' ' && AsciiGlyphs.STROKES[it] == null }.toSet()
        assertEquals("no strokes for $missing", emptySet<Char>(), missing)
    }

    @Test fun strokesStayInsideTheirCellAndAreWellFormed() {
        for ((c, polys) in AsciiGlyphs.STROKES) {
            assertTrue("'$c' has no polyline", polys.isNotEmpty())
            for (p in polys) {
                assertTrue("'$c' needs an even number of coordinates, at least two points", p.size >= 4 && p.size % 2 == 0)
                p.forEach { v -> assertTrue("'$c' leaves its cell: $v", v in 0f..1f) }
            }
        }
    }

    @Test fun bothLinesHaveTheSameNumberOfRowsSoTheyStack() {
        assertEquals(6, AsciiArt.NAME.size)
        assertEquals(6, AsciiArt.BY.size)
    }

    @Test fun theNameIsNarrowerThanTheTaglineDrawnAtTheSameCellSize() {
        // BrandLettering shrinks the tagline to a share of the name's width, so the name must be the narrower art.
        assertTrue(AsciiArt.NAME.maxOf { it.length } < AsciiArt.BY.maxOf { it.length })
    }
}
