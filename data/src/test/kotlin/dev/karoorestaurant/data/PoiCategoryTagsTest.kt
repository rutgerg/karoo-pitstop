package dev.karoorestaurant.data

import dev.karoorestaurant.data.poi.PoiCategory
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PoiCategoryTagsTest {

    @Test
    fun `restaurant fuel and supermarket original mappings still resolve`() {
        assertEquals(PoiCategory.RESTAURANT, PoiCategory.fromTags(mapOf("amenity" to "restaurant")))
        assertEquals(PoiCategory.FUEL, PoiCategory.fromTags(mapOf("amenity" to "fuel")))
        assertEquals(PoiCategory.SUPERMARKET, PoiCategory.fromTags(mapOf("shop" to "supermarket")))
        assertEquals(PoiCategory.SUPERMARKET, PoiCategory.fromTags(mapOf("shop" to "convenience")))
    }

    @Test
    fun `cafe maps from amenity bar or amenity cafe`() {
        assertEquals(PoiCategory.CAFE, PoiCategory.fromTags(mapOf("amenity" to "cafe")))
        assertEquals(PoiCategory.CAFE, PoiCategory.fromTags(mapOf("amenity" to "bar")))
    }

    @Test
    fun `hotel maps from tourism hotel guest_house hostel motel`() {
        assertEquals(PoiCategory.HOTEL, PoiCategory.fromTags(mapOf("tourism" to "hotel")))
        assertEquals(PoiCategory.HOTEL, PoiCategory.fromTags(mapOf("tourism" to "guest_house")))
        assertEquals(PoiCategory.HOTEL, PoiCategory.fromTags(mapOf("tourism" to "hostel")))
        assertEquals(PoiCategory.HOTEL, PoiCategory.fromTags(mapOf("tourism" to "motel")))
    }

    @Test
    fun `doctor maps from amenity doctors or clinic`() {
        assertEquals(PoiCategory.DOCTOR, PoiCategory.fromTags(mapOf("amenity" to "doctors")))
        assertEquals(PoiCategory.DOCTOR, PoiCategory.fromTags(mapOf("amenity" to "clinic")))
    }

    @Test
    fun `pharmacy maps from amenity pharmacy`() {
        assertEquals(PoiCategory.PHARMACY, PoiCategory.fromTags(mapOf("amenity" to "pharmacy")))
    }

    @Test
    fun `bike shop maps from shop bicycle`() {
        assertEquals(PoiCategory.BIKE_SHOP, PoiCategory.fromTags(mapOf("shop" to "bicycle")))
    }

    @Test
    fun `atm maps from amenity atm`() {
        assertEquals(PoiCategory.ATM, PoiCategory.fromTags(mapOf("amenity" to "atm")))
    }

    @Test
    fun `unrelated tags resolve to null`() {
        assertNull(PoiCategory.fromTags(mapOf("amenity" to "library")))
        assertNull(PoiCategory.fromTags(mapOf("shop" to "clothes")))
        assertNull(PoiCategory.fromTags(mapOf("tourism" to "museum")))
        assertNull(PoiCategory.fromTags(emptyMap()))
    }
}
