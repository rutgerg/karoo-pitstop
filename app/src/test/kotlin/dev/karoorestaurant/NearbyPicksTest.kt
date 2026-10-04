package dev.karoorestaurant

import dev.karoorestaurant.data.poi.OpeningHours
import dev.karoorestaurant.data.poi.Poi
import dev.karoorestaurant.data.poi.PoiCategory
import dev.karoorestaurant.data.route.LatLng
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NearbyPicksTest {

    private fun client(store: InMemoryPoiStore): KarooClient =
        KarooClient(FakeKarooSystemPort(), store, overpass = { _, _, _ -> emptyList() })

    private fun poi(osmId: Long, name: String, lat: Double, hours: String?): Poi = Poi(
        osmId = osmId,
        osmType = "node",
        name = name,
        category = PoiCategory.RESTAURANT,
        lat = lat,
        lon = 4.0,
        openingHoursTag = hours,
    )

    @Test
    fun `showClosed=true returns the nearest pick even when closed`() {
        val store = InMemoryPoiStore()
        store.upsertAll(listOf(poi(1L, "Closed near", lat = 52.001, hours = "closed")))
        store.upsertAll(listOf(poi(2L, "Open far", lat = 52.010, hours = "24/7")))

        val picks = computeNearbyPicks(client(store), center = LatLng(52.0, 4.0), showClosed = true)

        assertEquals(1, picks.size)
        assertEquals("Closed near", picks.single().poi.name)
        assertEquals(OpeningHours.Status.Closed, picks.single().status)
    }

    @Test
    fun `showClosed=false skips Closed and falls back to next Open`() {
        val store = InMemoryPoiStore()
        store.upsertAll(listOf(poi(1L, "Closed near", lat = 52.001, hours = "closed")))
        store.upsertAll(listOf(poi(2L, "Open far", lat = 52.010, hours = "24/7")))

        val picks = computeNearbyPicks(client(store), center = LatLng(52.0, 4.0), showClosed = false)

        assertEquals(1, picks.size)
        assertEquals("Open far", picks.single().poi.name)
        assertEquals(OpeningHours.Status.Open, picks.single().status)
    }

    @Test
    fun `showClosed=false falls back to Unknown when next candidate has no hours`() {
        val store = InMemoryPoiStore()
        store.upsertAll(listOf(poi(1L, "Closed near", lat = 52.001, hours = "closed")))
        store.upsertAll(listOf(poi(2L, "Unknown far", lat = 52.010, hours = null)))

        val picks = computeNearbyPicks(client(store), center = LatLng(52.0, 4.0), showClosed = false)

        assertEquals(1, picks.size)
        assertEquals("Unknown far", picks.single().poi.name)
    }

    @Test
    fun `showClosed=false returns empty for category when every candidate is Closed`() {
        val store = InMemoryPoiStore()
        store.upsertAll(listOf(poi(1L, "Closed A", lat = 52.001, hours = "closed")))
        store.upsertAll(listOf(poi(2L, "Closed B", lat = 52.002, hours = "off")))

        val picks = computeNearbyPicks(client(store), center = LatLng(52.0, 4.0), showClosed = false)

        assertNull(picks.firstOrNull { it.poi.category == PoiCategory.RESTAURANT })
    }

    // --- skip to next alternative (issue #63) ---

    private fun threeOpen(): InMemoryPoiStore = InMemoryPoiStore().apply {
        upsertAll(listOf(poi(1L, "Nearest", lat = 52.001, hours = "24/7")))
        upsertAll(listOf(poi(2L, "Second", lat = 52.002, hours = "24/7")))
        upsertAll(listOf(poi(3L, "Third", lat = 52.003, hours = "24/7")))
    }

    private fun restaurantPick(store: InMemoryPoiStore, skipped: Set<Long>, showClosed: Boolean = true): PoiNearby =
        computeNearbyPicks(client(store), center = LatLng(52.0, 4.0), showClosed = showClosed, skipped = mapOf(PoiCategory.RESTAURANT to skipped))
            .single { it.poi.category == PoiCategory.RESTAURANT }

    @Test
    fun `no skips picks the nearest with rank 0`() {
        val pick = restaurantPick(threeOpen(), skipped = emptySet())
        assertEquals("Nearest", pick.poi.name)
        assertEquals(0, pick.rank)
    }

    @Test
    fun `skipping the nearest advances to the second-nearest with rank 1`() {
        val pick = restaurantPick(threeOpen(), skipped = setOf(1L))
        assertEquals("Second", pick.poi.name)
        assertEquals(1, pick.rank)
    }

    @Test
    fun `skipping two advances to the third with rank 2`() {
        val pick = restaurantPick(threeOpen(), skipped = setOf(1L, 2L))
        assertEquals("Third", pick.poi.name)
        assertEquals(2, pick.rank)
    }

    @Test
    fun `skipping every candidate wraps back to the nearest`() {
        val pick = restaurantPick(threeOpen(), skipped = setOf(1L, 2L, 3L))
        assertEquals("Nearest", pick.poi.name)
        assertEquals(0, pick.rank)
    }

    @Test
    fun `skips for another category do not affect this one`() {
        val picks = computeNearbyPicks(client(threeOpen()), center = LatLng(52.0, 4.0), skipped = mapOf(PoiCategory.CAFE to setOf(1L)))
        assertEquals("Nearest", picks.single { it.poi.category == PoiCategory.RESTAURANT }.poi.name)
    }

    @Test
    fun `rank counts only eligible candidates when closed POIs are hidden`() {
        val store = InMemoryPoiStore()
        store.upsertAll(listOf(poi(1L, "Closed near", lat = 52.001, hours = "closed")))
        store.upsertAll(listOf(poi(2L, "Open mid", lat = 52.002, hours = "24/7")))
        store.upsertAll(listOf(poi(3L, "Open far", lat = 52.003, hours = "24/7")))

        val pick = restaurantPick(store, skipped = setOf(2L), showClosed = false)

        assertEquals("Open far", pick.poi.name)
        assertEquals(1, pick.rank)
        assertFalse(pick.status == OpeningHours.Status.Closed)
    }

    @Test
    fun `with closed POIs shown a skip may land on a closed alternative`() {
        val store = InMemoryPoiStore()
        store.upsertAll(listOf(poi(1L, "Open near", lat = 52.001, hours = "24/7")))
        store.upsertAll(listOf(poi(2L, "Closed mid", lat = 52.002, hours = "closed")))

        val pick = restaurantPick(store, skipped = setOf(1L), showClosed = true)

        assertEquals("Closed mid", pick.poi.name)
        assertEquals(OpeningHours.Status.Closed, pick.status)
    }
}
