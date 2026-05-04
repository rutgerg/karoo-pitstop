package dev.karoorestaurant

import dev.karoorestaurant.data.poi.Poi
import dev.karoorestaurant.data.poi.PoiCategory
import io.hammerhead.karooext.models.LaunchPinDrop
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KarooClientNavigateToTest {

    @Test
    fun `dispatches LaunchPinDrop with POI coordinates`() {
        val port = FakeKarooSystemPort()
        val store = InMemoryPoiStore()
        val client = KarooClient(port, store, overpass = { _, _ -> emptyList() })

        val poi = Poi(
            osmType = "node",
            osmId = 12345L,
            name = "Café Mosel",
            category = PoiCategory.RESTAURANT,
            lat = 52.3676,
            lon = 4.9041,
            openingHoursTag = "Mo-Sa 08:00-22:00",
        )
        client.navigateTo(poi)

        assertEquals(1, port.dispatched.size)
        val effect = port.dispatched.single()
        assertTrue(effect is LaunchPinDrop, "expected LaunchPinDrop, got ${effect::class.simpleName}")
        val pin = (effect as LaunchPinDrop).pin
        assertEquals(52.3676, pin.lat, 1e-9)
        assertEquals(4.9041, pin.lng, 1e-9)
        assertEquals("Café Mosel", pin.name)
        assertEquals("osm-node-12345", pin.id)
    }

    @Test
    fun `pin id encodes osm type and id`() {
        val port = FakeKarooSystemPort()
        val store = InMemoryPoiStore()
        val client = KarooClient(port, store, overpass = { _, _ -> emptyList() })

        val poi = Poi(
            osmType = "way",
            osmId = 987654321L,
            name = "Mercadona",
            category = PoiCategory.SUPERMARKET,
            lat = 0.0,
            lon = 0.0,
            openingHoursTag = null,
        )
        client.navigateTo(poi)

        val pin = (port.dispatched.single() as LaunchPinDrop).pin
        assertEquals("osm-way-987654321", pin.id)
    }

    @Test
    fun `category maps to Symbol type`() {
        val port = FakeKarooSystemPort()
        val store = InMemoryPoiStore()
        val client = KarooClient(port, store, overpass = { _, _ -> emptyList() })

        PoiCategory.values().forEach { category ->
            val poi = Poi(
                osmType = "node",
                osmId = category.ordinal.toLong(),
                name = category.name,
                category = category,
                lat = 0.0,
                lon = 0.0,
                openingHoursTag = null,
            )
            client.navigateTo(poi)
        }

        val pins = port.dispatched.map { (it as LaunchPinDrop).pin }
        assertEquals(PoiCategory.values().size, pins.size)
        // Each pin should have a non-blank type string mapped from its category.
        pins.forEach { assertTrue(it.type.isNotBlank()) }
    }
}
