package dev.karoorestaurant

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TileViewTest {

    @Test
    fun `Error state maps to WaitingForWifi and skips pick computation`() {
        val view = tileViewFor(RouteFetchState.Error("DNS unavailable"))
        assertEquals(TileView.WaitingForWifi, view)
        assertEquals(R.string.field_waiting_for_wifi, view.placeholderRes)
        assertFalse(view.showPick, "pick is not displayed while waiting for Wi-Fi")
    }

    @Test
    fun `Idle state maps to Active so cached POIs still display on free rides`() {
        val view = tileViewFor(RouteFetchState.Idle)
        assertEquals(TileView.Active, view)
        assertEquals(R.string.field_none, view.placeholderRes)
        assertTrue(view.showPick)
    }

    @Test
    fun `Fetching state maps to Active`() {
        val view = tileViewFor(RouteFetchState.Fetching("Test Route"))
        assertEquals(TileView.Active, view)
        assertTrue(view.showPick)
    }

    @Test
    fun `Cached state maps to Active`() {
        val view = tileViewFor(RouteFetchState.Cached("Test Route", poiCount = 42))
        assertEquals(TileView.Active, view)
        assertTrue(view.showPick)
    }
}
