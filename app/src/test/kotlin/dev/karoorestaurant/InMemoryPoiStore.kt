package dev.karoorestaurant

import dev.karoorestaurant.data.poi.Poi
import dev.karoorestaurant.data.poi.PoiCategory
import dev.karoorestaurant.data.route.Geo
import dev.karoorestaurant.data.route.LatLng
import dev.karoorestaurant.db.NearbyHit
import dev.karoorestaurant.db.PoiStore
import java.time.Instant

class InMemoryPoiStore : PoiStore {

    private data class Record(val poi: Poi, val fetchedAt: Instant, val isFavorite: Boolean)

    private val pois: MutableMap<Pair<String, Long>, Record> = mutableMapOf()
    private val routeFetches: MutableSet<String> = mutableSetOf()
    var upsertCount: Int = 0
        private set

    override fun upsertAll(pois: List<Poi>, fetchedAt: Instant) {
        pois.forEach { p ->
            val key = p.osmType to p.osmId
            this.pois[key] = Record(p, fetchedAt, this.pois[key]?.isFavorite ?: false)
        }
        upsertCount++
    }

    override fun setFavorite(poi: Poi, isFavorite: Boolean) {
        val key = poi.osmType to poi.osmId
        val existing = pois[key]
        if (existing != null) {
            pois[key] = existing.copy(isFavorite = isFavorite)
        }
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
            .filter { record ->
                record.poi.category == category && (record.fetchedAt.isAfter(cutoff) || record.isFavorite)
            }
            .map { record ->
                NearbyHit(
                    record.poi,
                    Geo.haversineMeters(center, LatLng(record.poi.lat, record.poi.lon)),
                    record.fetchedAt,
                    record.isFavorite,
                )
            }
            .filter { it.distanceMeters <= maxMeters }
            .sortedWith(compareByDescending<NearbyHit> { it.isFavorite }.thenBy { it.distanceMeters })
            .take(limit)
    }

    override fun recordRouteFetch(routeId: String, fetchedAt: Instant) {
        routeFetches.add(routeId)
    }

    override fun wasRouteFetched(routeId: String): Boolean = routeId in routeFetches
}
