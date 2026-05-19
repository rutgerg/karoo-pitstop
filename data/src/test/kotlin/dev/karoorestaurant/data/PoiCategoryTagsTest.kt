package dev.karoorestaurant.data

import dev.karoorestaurant.data.poi.PoiCategory
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PoiCategoryTagsTest {

    @Test
    fun `fromFlag maps debug aliases for utility categories`() {
        assertEquals(PoiCategory.DRINKING_WATER, PoiCategory.fromFlag("water"))
        assertEquals(PoiCategory.DRINKING_WATER, PoiCategory.fromFlag("drinking_water"))
        assertEquals(PoiCategory.DRINKING_WATER, PoiCategory.fromFlag("water_tap"))
        assertEquals(PoiCategory.DRINKING_WATER, PoiCategory.fromFlag("well"))
        assertEquals(PoiCategory.TOILETS, PoiCategory.fromFlag("toilet"))
        assertEquals(PoiCategory.TOILETS, PoiCategory.fromFlag("toilets"))
        assertEquals(PoiCategory.CEMETERY, PoiCategory.fromFlag("cemetery"))
        assertEquals(PoiCategory.CEMETERY, PoiCategory.fromFlag("grave_yard"))
    }

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
    fun `drinking water maps from amenity drinking_water and common tap or well tags`() {
        assertEquals(PoiCategory.DRINKING_WATER, PoiCategory.fromTags(mapOf("amenity" to "drinking_water")))
        assertEquals(PoiCategory.DRINKING_WATER, PoiCategory.fromTags(mapOf("man_made" to "water_tap")))
        assertEquals(PoiCategory.DRINKING_WATER, PoiCategory.fromTags(mapOf("man_made" to "water_well")))
    }

    @Test
    fun `drinking water excludes explicit non-drinking water sources`() {
        assertNull(PoiCategory.fromTags(mapOf("amenity" to "drinking_water", "drinking_water" to "no")))
        assertNull(PoiCategory.fromTags(mapOf("man_made" to "water_tap", "drinking_water" to "no")))
        assertNull(PoiCategory.fromTags(mapOf("man_made" to "water_tower")))
    }

    @Test
    fun `toilets map from amenity toilets`() {
        assertEquals(PoiCategory.TOILETS, PoiCategory.fromTags(mapOf("amenity" to "toilets")))
    }

    @Test
    fun `cemetery maps from landuse cemetery or amenity grave_yard`() {
        assertEquals(PoiCategory.CEMETERY, PoiCategory.fromTags(mapOf("landuse" to "cemetery")))
        assertEquals(PoiCategory.CEMETERY, PoiCategory.fromTags(mapOf("amenity" to "grave_yard")))
    }

    @Test
    fun `cemetery does not match unrelated landuse or amenity tags`() {
        assertNull(PoiCategory.fromTags(mapOf("landuse" to "churchyard")))
        assertNull(PoiCategory.fromTags(mapOf("amenity" to "place_of_worship")))
    }

    @Test
    fun `unrelated tags resolve to null`() {
        assertNull(PoiCategory.fromTags(mapOf("amenity" to "library")))
        assertNull(PoiCategory.fromTags(mapOf("shop" to "clothes")))
        assertNull(PoiCategory.fromTags(mapOf("tourism" to "museum")))
        assertNull(PoiCategory.fromTags(emptyMap()))
    }
}
