package dev.karoorestaurant

/**
 * Map a compass angle in degrees (0 = ahead, clockwise) to one of eight arrows.
 * Useful for rendering relative direction to a POI on a data tile.
 */
internal fun arrowFor(relativeBearingDegrees: Double): Char {
    val normalized = ((relativeBearingDegrees % 360.0) + 360.0) % 360.0
    val sector = ((normalized + 22.5) / 45.0).toInt() % 8
    return ARROWS[sector]
}

private val ARROWS = charArrayOf('↑', '↗', '→', '↘', '↓', '↙', '←', '↖')
