package dev.karoorestaurant.data.overpass

import kotlinx.serialization.Serializable

@Serializable
data class OverpassResponse(
    val elements: List<OverpassElement> = emptyList(),
)

@Serializable
data class OverpassElement(
    val type: String,
    val id: Long,
    val lat: Double? = null,
    val lon: Double? = null,
    val center: Center? = null,
    val tags: Map<String, String> = emptyMap(),
) {
    val effectiveLat: Double? get() = lat ?: center?.lat
    val effectiveLon: Double? get() = lon ?: center?.lon
}

@Serializable
data class Center(val lat: Double, val lon: Double)
