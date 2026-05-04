package dev.karoorestaurant

import dev.karoorestaurant.data.poi.Poi
import dev.karoorestaurant.data.poi.PoiCategory
import dev.karoorestaurant.data.route.Geo
import dev.karoorestaurant.data.route.LatLng
import dev.karoorestaurant.db.PoiStore
import java.time.Instant

class InMemoryPoiStore : PoiStore {

    private val pois: MutableMap<Pair<String, Long>, Poi> = mutableMapOf()
    private val routeFetches: MutableSet<String> = mutableSetOf()
    var upsertCount: Int = 0
        private set

    override fun upsertAll(pois: List<Poi>, fetchedAt: Instant) {
        pois.forEach { p -> this.pois[p.osmType to p.osmId] = p }
        upsertCount++
    }

    override fun count(): Int = pois.size

    override fun nearest(
        center: LatLng,
        category: PoiCategory,
        maxMeters: Double,
        limit: Int,
    ): List<Pair<Poi, Double>> = pois.values
        .filter { it.category == category }
        .map { it to Geo.haversineMeters(center, LatLng(it.lat, it.lon)) }
        .filter { it.second <= maxMeters }
        .sortedBy { it.second }
        .take(limit)

    override fun recordRouteFetch(routeId: String, fetchedAt: Instant) {
        routeFetches.add(routeId)
    }

    override fun wasRouteFetched(routeId: String): Boolean = routeId in routeFetches
}
