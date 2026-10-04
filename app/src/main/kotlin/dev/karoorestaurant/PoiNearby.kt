package dev.karoorestaurant

import dev.karoorestaurant.data.poi.OpeningHours
import dev.karoorestaurant.data.poi.Poi

data class PoiNearby(
    val poi: Poi,
    val distanceMeters: Double,
    val status: OpeningHours.Status,
    val staleness: Staleness = Staleness.NEW,
    /** 0 for the nearest eligible candidate, 1 for the next one after a skip, and so on. */
    val rank: Int = 0,
)
