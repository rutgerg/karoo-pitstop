package dev.karoorestaurant.data

import dev.karoorestaurant.data.route.Geo
import dev.karoorestaurant.data.route.LatLng
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class GeoBearingTest {

    private val origin = LatLng(0.0, 0.0)

    @Test
    fun `north points to 0`() {
        assertEquals(0.0, Geo.bearingDegrees(origin, LatLng(1.0, 0.0)), 1e-6)
    }

    @Test
    fun `east points to 90`() {
        assertEquals(90.0, Geo.bearingDegrees(origin, LatLng(0.0, 1.0)), 1e-6)
    }

    @Test
    fun `south points to 180`() {
        assertEquals(180.0, Geo.bearingDegrees(origin, LatLng(-1.0, 0.0)), 1e-6)
    }

    @Test
    fun `west points to 270`() {
        assertEquals(270.0, Geo.bearingDegrees(origin, LatLng(0.0, -1.0)), 1e-6)
    }

    @Test
    fun `result is normalized to non-negative range`() {
        // West-of-origin would naively give a negative atan2 result.
        val bearing = Geo.bearingDegrees(LatLng(40.0, 0.0), LatLng(40.0, -0.5))
        assertEquals(true, bearing in 0.0..360.0)
        // Roughly westward.
        assertEquals(270.0, bearing, 0.5)
    }

    @Test
    fun `northeast points to 45`() {
        // Use small steps near the equator so meridian convergence stays negligible.
        val bearing = Geo.bearingDegrees(origin, LatLng(0.001, 0.001))
        assertEquals(45.0, bearing, 0.1)
    }
}
