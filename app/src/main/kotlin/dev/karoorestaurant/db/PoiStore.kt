package dev.karoorestaurant.db

import dev.karoorestaurant.data.poi.Poi
import dev.karoorestaurant.data.poi.PoiCategory
import dev.karoorestaurant.data.route.LatLng
import java.time.Instant

interface PoiStore {
    fun upsertAll(pois: List<Poi>, fetchedAt: Instant = Instant.now())
    fun count(): Int
    fun nearest(
        center: LatLng,
        category: PoiCategory,
        maxMeters: Double = 30_000.0,
        limit: Int = 25,
    ): List<Pair<Poi, Double>>
    fun recordRouteFetch(routeId: String, fetchedAt: Instant = Instant.now())
    fun wasRouteFetched(routeId: String): Boolean
}
