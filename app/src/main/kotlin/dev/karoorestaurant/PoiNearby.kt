package dev.karoorestaurant

import dev.karoorestaurant.data.poi.OpeningHours
import dev.karoorestaurant.data.poi.Poi

data class PoiNearby(
    val poi: Poi,
    val distanceMeters: Double,
    val status: OpeningHours.Status,
    val staleness: Staleness = Staleness.NEW,
    val isFavorite: Boolean = false,
)
