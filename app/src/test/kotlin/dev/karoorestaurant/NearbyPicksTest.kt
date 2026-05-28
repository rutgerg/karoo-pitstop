package dev.karoorestaurant

import dev.karoorestaurant.data.poi.OpeningHours
import dev.karoorestaurant.data.poi.Poi
import dev.karoorestaurant.data.poi.PoiCategory
import dev.karoorestaurant.data.route.LatLng
import org.junit.jupiter.api.Assertions.assertEquals
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
}
