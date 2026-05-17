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
            .joinToString("\n") { "              " + selectorFor(it, radiusMeters, coords) }
        return """
            [out:json][timeout:60];
            (
$selectors
            );
            out center tags;
        """.trimIndent()
    }

    private fun selectorFor(category: PoiCategory, radiusMeters: Int, coords: String): String =
        when (category) {
            PoiCategory.RESTAURANT -> "nwr[\"amenity\"~\"^(restaurant|fast_food)\$\"](around:$radiusMeters,$coords);"
            PoiCategory.FUEL -> "nwr[\"amenity\"=\"fuel\"](around:$radiusMeters,$coords);"
            PoiCategory.SUPERMARKET -> "nwr[\"shop\"~\"^(supermarket|convenience)\$\"](around:$radiusMeters,$coords);"
            PoiCategory.CAFE -> "nwr[\"amenity\"~\"^(bar|cafe)\$\"](around:$radiusMeters,$coords);"
            PoiCategory.HOTEL -> "nwr[\"tourism\"~\"^(hotel|guest_house|hostel|motel)\$\"](around:$radiusMeters,$coords);"
            PoiCategory.DOCTOR -> "nwr[\"amenity\"~\"^(doctors|clinic)\$\"](around:$radiusMeters,$coords);"
            PoiCategory.PHARMACY -> "nwr[\"amenity\"=\"pharmacy\"](around:$radiusMeters,$coords);"
            PoiCategory.BIKE_SHOP -> "nwr[\"shop\"=\"bicycle\"](around:$radiusMeters,$coords);"
            PoiCategory.ATM -> "nwr[\"amenity\"=\"atm\"](around:$radiusMeters,$coords);"
            PoiCategory.TRAIN_STATION -> "nwr[\"railway\"~\"^(station|halt)\$\"](around:$radiusMeters,$coords);"
        }
}
