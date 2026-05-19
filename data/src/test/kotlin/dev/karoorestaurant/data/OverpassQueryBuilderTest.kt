package dev.karoorestaurant.data

import dev.karoorestaurant.data.overpass.OverpassQueryBuilder
import dev.karoorestaurant.data.poi.PoiCategory
import dev.karoorestaurant.data.route.LatLng
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OverpassQueryBuilderTest {

    private val sample = listOf(LatLng(52.0, 4.0))

    @Test
    fun `drinking water emits amenity and man_made selectors`() {
        val query = OverpassQueryBuilder.build(sample, radiusMeters = 250, categories = setOf(PoiCategory.DRINKING_WATER))

        assertContainsSelector(
            query,
            """nwr["amenity"="drinking_water"]["access"!~"^(private|no)$"](around:250,52.000000,4.000000);""",
        )
        assertContainsSelector(
            query,
            """nwr["man_made"~"^(water_tap|water_well)$"]["drinking_water"!="no"]["access"!~"^(private|no)$"](around:250,52.000000,4.000000);""",
        )
        assertFalse(query.contains("""nwr["amenity"="toilets"]"""), query)
    }

    @Test
    fun `toilets category filter emits only toilets selector`() {
        val query = OverpassQueryBuilder.build(sample, radiusMeters = 300, categories = setOf(PoiCategory.TOILETS))

        assertContainsSelector(
            query,
            """nwr["amenity"="toilets"]["access"!~"^(private|no)$"](around:300,52.000000,4.000000);""",
        )
        assertFalse(query.contains("""nwr["amenity"~"^(restaurant|fast_food)$"]"""), query)
        assertFalse(query.contains("""nwr["landuse"="cemetery"]"""), query)
    }

    @Test
    fun `cemetery emits cemetery and grave yard selectors`() {
        val query = OverpassQueryBuilder.build(sample, radiusMeters = 400, categories = setOf(PoiCategory.CEMETERY))

        assertContainsSelector(
            query,
            """nwr["landuse"="cemetery"]["access"!~"^(private|no)$"](around:400,52.000000,4.000000);""",
        )
        assertContainsSelector(
            query,
            """nwr["amenity"="grave_yard"]["access"!~"^(private|no)$"](around:400,52.000000,4.000000);""",
        )
        assertFalse(query.contains("""nwr["amenity"="drinking_water"]"""), query)
    }

    private fun assertContainsSelector(query: String, selector: String) {
        assertTrue(
            query.contains(selector),
            "Expected query to contain:\n$selector\nActual query:\n$query",
        )
    }
}
