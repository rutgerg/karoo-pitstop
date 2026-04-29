package dev.karoorestaurant.data.overpass

import dev.karoorestaurant.data.poi.Poi
import dev.karoorestaurant.data.route.LatLng

interface OverpassFetcher {
    suspend fun fetchCorridor(
        samples: List<LatLng>,
        radiusMeters: Int = 10_000,
    ): List<Poi>
}
