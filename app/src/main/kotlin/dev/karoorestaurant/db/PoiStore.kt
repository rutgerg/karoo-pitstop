package dev.karoorestaurant.db

import dev.karoorestaurant.data.poi.Poi
import dev.karoorestaurant.data.poi.PoiCategory
import dev.karoorestaurant.data.route.LatLng
import java.time.Instant

data class NearbyHit(val poi: Poi, val distanceMeters: Double, val fetchedAt: Instant)

interface PoiStore {
    fun upsertAll(pois: List<Poi>, fetchedAt: Instant = Instant.now())
    fun count(): Int

    /**
     * Return the nearest POIs in [category] within [maxMeters] of [center], up to [limit].
     * Rows older than [maxAgeDays] (relative to [now]) are filtered out at the store level.
     */
    fun nearest(
        center: LatLng,
        category: PoiCategory,
        maxMeters: Double = 30_000.0,
        limit: Int = 25,
        now: Instant = Instant.now(),
        maxAgeDays: Long = 60L,
    ): List<NearbyHit>

    fun recordRouteFetch(
        routeId: String,
        categories: Set<PoiCategory>,
        fetchedAt: Instant = Instant.now(),
    )

    /**
     * Categories that have been successfully fetched for [routeId], or empty if the
     * route has never been fetched. RouteWatcher uses this to compute which categories
     * still need a query when the active category set has grown since the prior fetch
     * (e.g., a category added in a new app release).
     */
    fun fetchedCategories(routeId: String): Set<PoiCategory>

    /**
     * Wipe every cached POI and route-fetch record. Used by the debug-only
     * "Reset POI cache" action so the next route load forces a fresh Overpass
     * fetch instead of reading stale cross-region rows.
     */
    fun clearAll()
}
