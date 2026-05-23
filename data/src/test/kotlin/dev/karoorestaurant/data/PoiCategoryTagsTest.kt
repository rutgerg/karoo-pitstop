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
    fun `restaurant also includes fast_food so snack bars and frituren surface`() {
        assertEquals(PoiCategory.RESTAURANT, PoiCategory.fromTags(mapOf("amenity" to "fast_food")))
    }

    @Test
    fun `restaurant does not include pub or food_court`() {
        // Out of scope for now — revisit if real-ride feedback says otherwise.
        assertNull(PoiCategory.fromTags(mapOf("amenity" to "pub")))
        assertNull(PoiCategory.fromTags(mapOf("amenity" to "food_court")))
        assertNull(PoiCategory.fromTags(mapOf("amenity" to "biergarten")))
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
    fun `train station maps from railway station or halt`() {
        assertEquals(PoiCategory.TRAIN_STATION, PoiCategory.fromTags(mapOf("railway" to "station")))
        assertEquals(PoiCategory.TRAIN_STATION, PoiCategory.fromTags(mapOf("railway" to "halt")))
    }

    @Test
    fun `train station does not match other railway tags`() {
        // tram_stop, subway_entrance and similar are intentionally out of scope.
        assertNull(PoiCategory.fromTags(mapOf("railway" to "tram_stop")))
        assertNull(PoiCategory.fromTags(mapOf("railway" to "subway_entrance")))
        assertNull(PoiCategory.fromTags(mapOf("railway" to "level_crossing")))
    }

    @Test
    fun `water refill maps from drinking_water toilets cemetery and grave_yard`() {
        assertEquals(PoiCategory.WATER_REFILL, PoiCategory.fromTags(mapOf("amenity" to "drinking_water")))
        assertEquals(PoiCategory.WATER_REFILL, PoiCategory.fromTags(mapOf("amenity" to "toilets")))
        assertEquals(PoiCategory.WATER_REFILL, PoiCategory.fromTags(mapOf("landuse" to "cemetery")))
        assertEquals(PoiCategory.WATER_REFILL, PoiCategory.fromTags(mapOf("amenity" to "grave_yard")))
    }

    @Test
    fun `water refill does not match wells fountains or other water tags`() {
        // man_made=water_well often non-potable (irrigation), amenity=fountain often decorative.
        // Out of scope until real-ride feedback says otherwise.
        assertNull(PoiCategory.fromTags(mapOf("man_made" to "water_well")))
        assertNull(PoiCategory.fromTags(mapOf("man_made" to "water_tap")))
        assertNull(PoiCategory.fromTags(mapOf("amenity" to "fountain")))
    }

    @Test
    fun `unrelated tags resolve to null`() {
        assertNull(PoiCategory.fromTags(mapOf("amenity" to "library")))
        assertNull(PoiCategory.fromTags(mapOf("shop" to "clothes")))
        assertNull(PoiCategory.fromTags(mapOf("tourism" to "museum")))
        assertNull(PoiCategory.fromTags(emptyMap()))
    }
}
