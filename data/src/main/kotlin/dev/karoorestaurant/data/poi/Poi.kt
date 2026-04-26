package dev.karoorestaurant.data.poi

data class Poi(
    val osmId: Long,
    val osmType: String,
    val name: String,
    val category: PoiCategory,
    val lat: Double,
    val lon: Double,
    val openingHoursTag: String? = null,
)
