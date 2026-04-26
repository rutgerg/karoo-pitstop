package dev.karoorestaurant.data

import dev.karoorestaurant.data.route.CorridorSlicer
import dev.karoorestaurant.data.route.Geo
import dev.karoorestaurant.data.route.LatLng
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CorridorSlicerTest {

    @Test
    fun `empty polyline returns empty`() {
        assertEquals(emptyList(), CorridorSlicer.sample(emptyList()))
    }

    @Test
    fun `single point returns single point`() {
        val p = LatLng(52.0, 4.0)
        assertEquals(listOf(p), CorridorSlicer.sample(listOf(p)))
    }

    @Test
    fun `samples are spaced approximately step meters apart`() {
        val a = LatLng(52.0, 4.0)
        val b = LatLng(52.09, 4.0)
        val samples = CorridorSlicer.sample(listOf(a, b), stepMeters = 1_000.0)
        val gaps = samples.zipWithNext { x, y -> Geo.haversineMeters(x, y) }
        assertTrue(gaps.dropLast(1).all { it in 950.0..1_050.0 }, "gaps were $gaps")
    }

    @Test
    fun `last point is always included`() {
        val a = LatLng(52.0, 4.0)
        val b = LatLng(52.001, 4.0)
        val samples = CorridorSlicer.sample(listOf(a, b), stepMeters = 1_000.0)
        assertEquals(b, samples.last())
    }
}
