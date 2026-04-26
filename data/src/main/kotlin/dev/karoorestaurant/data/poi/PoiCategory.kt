package dev.karoorestaurant.data.poi

enum class PoiCategory(val label: String) {
    RESTAURANT("Restaurant"),
    SUPERMARKET("Supermarket"),
    FUEL("Fuel");

    companion object {
        fun fromTags(tags: Map<String, String>): PoiCategory? = when {
            tags["amenity"] == "restaurant" -> RESTAURANT
            tags["amenity"] == "fuel" -> FUEL
            tags["shop"] in SUPERMARKET_SHOPS -> SUPERMARKET
            else -> null
        }

        private val SUPERMARKET_SHOPS = setOf("supermarket", "convenience")
    }
}
