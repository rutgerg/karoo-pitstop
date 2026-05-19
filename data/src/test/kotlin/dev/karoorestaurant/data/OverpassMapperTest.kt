package dev.karoorestaurant.data

import dev.karoorestaurant.data.overpass.Center
import dev.karoorestaurant.data.overpass.OverpassElement
import dev.karoorestaurant.data.overpass.OverpassMapper
import dev.karoorestaurant.data.poi.PoiCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class OverpassMapperTest {

    @Test
    fun `unnamed drinking water uses category label fallback`() {
        val poi = OverpassMapper.toPoi(
            OverpassElement(
                type = "node",
                id = 1L,
                lat = 52.0,
                lon = 4.0,
                tags = mapOf("amenity" to "drinking_water"),
            ),
        )

        assertNotNull(poi)
        assertEquals(PoiCategory.DRINKING_WATER, poi.category)
        assertEquals("Drinking Water", poi.name)
    }

    @Test
    fun `unnamed toilets use category label fallback`() {
        val poi = OverpassMapper.toPoi(
            OverpassElement(
                type = "node",
                id = 2L,
                lat = 52.0,
                lon = 4.0,
                tags = mapOf("amenity" to "toilets"),
            ),
        )

        assertNotNull(poi)
        assertEquals(PoiCategory.TOILETS, poi.category)
        assertEquals("Toilets", poi.name)
    }

    @Test
    fun `cemetery way uses center coordinates and category label fallback`() {
        val poi = OverpassMapper.toPoi(
            OverpassElement(
                type = "way",
                id = 3L,
                center = Center(lat = 52.1, lon = 4.1),
                tags = mapOf("landuse" to "cemetery"),
            ),
        )

        assertNotNull(poi)
        assertEquals(PoiCategory.CEMETERY, poi.category)
        assertEquals("Cemetery", poi.name)
        assertEquals(52.1, poi.lat)
        assertEquals(4.1, poi.lon)
    }

    @Test
    fun `unnamed existing categories are still dropped`() {
        val poi = OverpassMapper.toPoi(
            OverpassElement(
                type = "node",
                id = 4L,
                lat = 52.0,
                lon = 4.0,
                tags = mapOf("amenity" to "restaurant"),
            ),
        )

        assertNull(poi)
    }
}
