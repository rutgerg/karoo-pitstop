package dev.karoorestaurant.data.overpass

import dev.karoorestaurant.data.poi.PoiCategory
import dev.karoorestaurant.data.route.LatLng

object OverpassQueryBuilder {
    fun build(
        samples: List<LatLng>,
        radiusMeters: Int,
        categories: Set<PoiCategory>? = null,
    ): String {
        val coords = samples.joinToString(",") {
            "${"%.6f".format(java.util.Locale.US, it.lat)},${"%.6f".format(java.util.Locale.US, it.lon)}"
        }
        val selectors = PoiCategory.values()
            .filter { categories == null || it in categories }
            .flatMap { selectorsFor(it, radiusMeters, coords) }
            .joinToString("\n") { "              " + it }
        return """
            [out:json][timeout:60];
            (
$selectors
            );
            out center tags;
        """.trimIndent()
    }

    private fun selectorsFor(category: PoiCategory, radiusMeters: Int, coords: String): List<String> =
        when (category) {
            PoiCategory.RESTAURANT -> listOf("nwr[\"amenity\"~\"^(restaurant|fast_food)\$\"](around:$radiusMeters,$coords);")
            PoiCategory.FUEL -> listOf("nwr[\"amenity\"=\"fuel\"](around:$radiusMeters,$coords);")
            PoiCategory.SUPERMARKET -> listOf("nwr[\"shop\"~\"^(supermarket|convenience)\$\"](around:$radiusMeters,$coords);")
            PoiCategory.CAFE -> listOf("nwr[\"amenity\"~\"^(bar|cafe)\$\"](around:$radiusMeters,$coords);")
            PoiCategory.HOTEL -> listOf("nwr[\"tourism\"~\"^(hotel|guest_house|hostel|motel)\$\"](around:$radiusMeters,$coords);")
            PoiCategory.DOCTOR -> listOf("nwr[\"amenity\"~\"^(doctors|clinic)\$\"](around:$radiusMeters,$coords);")
            PoiCategory.PHARMACY -> listOf("nwr[\"amenity\"=\"pharmacy\"](around:$radiusMeters,$coords);")
            PoiCategory.BIKE_SHOP -> listOf("nwr[\"shop\"=\"bicycle\"](around:$radiusMeters,$coords);")
            PoiCategory.ATM -> listOf("nwr[\"amenity\"=\"atm\"](around:$radiusMeters,$coords);")
            PoiCategory.TRAIN_STATION -> listOf("nwr[\"railway\"~\"^(station|halt)\$\"](around:$radiusMeters,$coords);")
            PoiCategory.DRINKING_WATER -> listOf(
                "nwr[\"amenity\"=\"drinking_water\"][\"access\"!~\"^(private|no)\$\"](around:$radiusMeters,$coords);",
                "nwr[\"man_made\"~\"^(water_tap|water_well)\$\"][\"drinking_water\"!=\"no\"][\"access\"!~\"^(private|no)\$\"](around:$radiusMeters,$coords);",
            )
            PoiCategory.TOILETS -> listOf("nwr[\"amenity\"=\"toilets\"][\"access\"!~\"^(private|no)\$\"](around:$radiusMeters,$coords);")
            PoiCategory.CEMETERY -> listOf(
                "nwr[\"landuse\"=\"cemetery\"][\"access\"!~\"^(private|no)\$\"](around:$radiusMeters,$coords);",
                "nwr[\"amenity\"=\"grave_yard\"][\"access\"!~\"^(private|no)\$\"](around:$radiusMeters,$coords);",
            )
        }
}
