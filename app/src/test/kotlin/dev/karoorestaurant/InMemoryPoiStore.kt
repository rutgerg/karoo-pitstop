package dev.karoorestaurant

import dev.karoorestaurant.data.poi.Poi
import dev.karoorestaurant.data.poi.PoiCategory
import dev.karoorestaurant.data.route.Geo
import dev.karoorestaurant.data.route.LatLng
import dev.karoorestaurant.db.NearbyHit
import dev.karoorestaurant.db.PoiStore
import java.time.Instant

class InMemoryPoiStore : PoiStore {

    private val pois: MutableMap<Pair<String, Long>, Pair<Poi, Instant>> = mutableMapOf()
    private val routeFetches: MutableMap<String, Set<PoiCategory>> = mutableMapOf()
    var upsertCount: Int = 0
        private set

    override fun upsertAll(pois: List<Poi>, fetchedAt: Instant) {
        pois.forEach { p -> this.pois[p.osmType to p.osmId] = p to fetchedAt }
        upsertCount++
    }

    override fun count(): Int = pois.size

    override fun nearest(
        center: LatLng,
        category: PoiCategory,
        maxMeters: Double,
        limit: Int,
        now: Instant,
        maxAgeDays: Long,
    ): List<NearbyHit> {
        val cutoff = now.minusSeconds(maxAgeDays * 86_400L)
        return pois.values
            .filter { (poi, fetchedAt) -> poi.category == category && fetchedAt.isAfter(cutoff) }
            .map { (poi, fetchedAt) ->
                NearbyHit(poi, Geo.haversineMeters(center, LatLng(poi.lat, poi.lon)), fetchedAt)
            }
            .filter { it.distanceMeters <= maxMeters }
            .sortedBy { it.distanceMeters }
            .take(limit)
    }

    override fun recordRouteFetch(
        routeId: String,
        categories: Set<PoiCategory>,
        fetchedAt: Instant,
    ) {
        routeFetches[routeId] = categories
    }

    override fun fetchedCategories(routeId: String): Set<PoiCategory> =
        routeFetches[routeId] ?: emptySet()
}
