package dev.karoorestaurant.db

import dev.karoorestaurant.data.poi.Poi
import dev.karoorestaurant.data.poi.PoiCategory
import dev.karoorestaurant.data.route.LatLng
import java.time.Instant

data class NearbyHit(
    val poi: Poi,
    val distanceMeters: Double,
    val fetchedAt: Instant,
    val isFavorite: Boolean = false,
)

interface PoiStore {
    fun upsertAll(pois: List<Poi>, fetchedAt: Instant = Instant.now())
    fun setFavorite(poi: Poi, isFavorite: Boolean)
    fun count(): Int

    /**
     * Return the nearest POIs in [category] within [maxMeters] of [center], up to [limit].
     * Rows older than [maxAgeDays] (relative to [now]) are filtered out at the store level
     * unless marked favorite.
     */
    fun nearest(
        center: LatLng,
        category: PoiCategory,
        maxMeters: Double = 30_000.0,
        limit: Int = 25,
        now: Instant = Instant.now(),
        maxAgeDays: Long = 60L,
    ): List<NearbyHit>

    fun recordRouteFetch(routeId: String, fetchedAt: Instant = Instant.now())
    fun wasRouteFetched(routeId: String): Boolean
}
