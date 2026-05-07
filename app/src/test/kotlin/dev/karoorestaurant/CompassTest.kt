package dev.karoorestaurant

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class CompassTest {

    @Test
    fun `each cardinal and ordinal bearing maps to its arrow`() {
        assertEquals('↑', arrowFor(0.0))
        assertEquals('↗', arrowFor(45.0))
        assertEquals('→', arrowFor(90.0))
        assertEquals('↘', arrowFor(135.0))
        assertEquals('↓', arrowFor(180.0))
        assertEquals('↙', arrowFor(225.0))
        assertEquals('←', arrowFor(270.0))
        assertEquals('↖', arrowFor(315.0))
    }

    @Test
    fun `sector boundaries snap to the nearer arrow`() {
        // Just inside the up sector (337.5..22.5) on either side.
        assertEquals('↑', arrowFor(22.4))
        assertEquals('↑', arrowFor(337.6))
        // Just past the boundary, into NE / NW.
        assertEquals('↗', arrowFor(22.6))
        assertEquals('↖', arrowFor(337.4))
    }

    @Test
    fun `negative and over-360 inputs are normalized`() {
        assertEquals('←', arrowFor(-90.0))
        assertEquals('→', arrowFor(450.0))
    }
}
