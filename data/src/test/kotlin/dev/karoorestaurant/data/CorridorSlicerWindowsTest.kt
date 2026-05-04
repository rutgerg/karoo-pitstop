package dev.karoorestaurant.data

import dev.karoorestaurant.data.route.CorridorSlicer
import dev.karoorestaurant.data.route.LatLng
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CorridorSlicerWindowsTest {

    @Test
    fun `empty polyline returns empty windows`() {
        assertEquals(emptyList(), CorridorSlicer.windows(emptyList()))
    }

    @Test
    fun `single point returns one window with one sample`() {
        val p = LatLng(52.0, 4.0)
        val windows = CorridorSlicer.windows(listOf(p))
        assertEquals(1, windows.size)
        assertEquals(listOf(p), windows.single())
    }

    @Test
    fun `short polyline fits in one window`() {
        val a = LatLng(52.0, 4.0)
        val b = LatLng(52.18, 4.0) // ~20 km
        val windows = CorridorSlicer.windows(listOf(a, b), windowMeters = 50_000.0, stepMeters = 2_000.0)
        assertEquals(1, windows.size)
    }

    @Test
    fun `200 km polyline produces at least 4 windows`() {
        // 1 degree of latitude ~ 111 km; 1.8 degrees ~ 200 km.
        val a = LatLng(52.0, 4.0)
        val b = LatLng(53.8, 4.0)
        val windows = CorridorSlicer.windows(listOf(a, b), windowMeters = 50_000.0, stepMeters = 2_000.0)
        assertTrue(windows.size >= 4, "expected 4+ windows, got ${windows.size}")
    }

    @Test
    fun `windows preserve all sampled points across chunks`() {
        val a = LatLng(52.0, 4.0)
        val b = LatLng(53.8, 4.0)
        val samples = CorridorSlicer.sample(listOf(a, b), stepMeters = 2_000.0)
        val windows = CorridorSlicer.windows(listOf(a, b), windowMeters = 50_000.0, stepMeters = 2_000.0)
        assertEquals(samples, windows.flatten())
    }

    @Test
    fun `each window has at most windowMeters slash stepMeters samples`() {
        val a = LatLng(52.0, 4.0)
        val b = LatLng(53.8, 4.0)
        val windows = CorridorSlicer.windows(listOf(a, b), windowMeters = 50_000.0, stepMeters = 2_000.0)
        // 50_000 / 2_000 = 25 samples per window (last one may be smaller)
        assertTrue(windows.dropLast(1).all { it.size == 25 })
        assertTrue(windows.last().size <= 25)
    }
}
