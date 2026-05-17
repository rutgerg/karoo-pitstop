package dev.karoorestaurant

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TileViewTest {

    @Test
    fun `Error state shows waiting-for-wifi placeholder for empty-cache case`() {
        assertEquals(R.string.field_waiting_for_wifi, placeholderFor(RouteFetchState.Error("DNS unavailable")))
    }

    @Test
    fun `Idle state uses default placeholder so cached POIs still display on free rides`() {
        assertEquals(R.string.field_none, placeholderFor(RouteFetchState.Idle))
    }

    @Test
    fun `Fetching state uses default placeholder`() {
        assertEquals(R.string.field_none, placeholderFor(RouteFetchState.Fetching("Test Route")))
    }

    @Test
    fun `Cached state uses default placeholder`() {
        assertEquals(R.string.field_none, placeholderFor(RouteFetchState.Cached("Test Route", poiCount = 42)))
    }
}
