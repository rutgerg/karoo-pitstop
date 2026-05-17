package dev.karoorestaurant

import dev.karoorestaurant.data.poi.OpeningHours
import dev.karoorestaurant.data.poi.Poi
import dev.karoorestaurant.data.poi.PoiCategory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
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

    @Test
    fun `tilePick returns matching-category pick when within distance threshold`() {
        val pick = nearby(PoiCategory.RESTAURANT, distanceMeters = 800.0)
        assertSame(pick, tilePick(listOf(pick), PoiCategory.RESTAURANT))
    }

    @Test
    fun `tilePick keeps a pick right at the threshold`() {
        val pick = nearby(PoiCategory.RESTAURANT, distanceMeters = MAX_PICK_DISTANCE_METERS)
        assertSame(pick, tilePick(listOf(pick), PoiCategory.RESTAURANT))
    }

    @Test
    fun `tilePick drops a pick beyond threshold so stale cross-region cache is hidden`() {
        // Mirrors issue #149: Utrecht ride with Amsterdam cache produced a 22 km pick.
        val pick = nearby(PoiCategory.RESTAURANT, distanceMeters = 22_000.0)
        assertNull(tilePick(listOf(pick), PoiCategory.RESTAURANT))
    }

    @Test
    fun `tilePick filters by category before applying distance gate`() {
        val cafe = nearby(PoiCategory.CAFE, distanceMeters = 200.0)
        val restaurant = nearby(PoiCategory.RESTAURANT, distanceMeters = 800.0)
        assertSame(restaurant, tilePick(listOf(cafe, restaurant), PoiCategory.RESTAURANT))
    }

    @Test
    fun `tilePick returns null when no pick matches the category`() {
        val cafe = nearby(PoiCategory.CAFE, distanceMeters = 200.0)
        assertNull(tilePick(listOf(cafe), PoiCategory.RESTAURANT))
    }

    private fun nearby(category: PoiCategory, distanceMeters: Double): PoiNearby = PoiNearby(
        poi = Poi(
            osmId = 1L,
            osmType = "node",
            name = "Test",
            category = category,
            lat = 0.0,
            lon = 0.0,
        ),
        distanceMeters = distanceMeters,
        status = OpeningHours.Status.Unknown(reason = "test"),
        staleness = Staleness.NEW,
    )
}
