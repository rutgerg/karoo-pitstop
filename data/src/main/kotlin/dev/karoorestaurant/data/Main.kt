package dev.karoorestaurant.data

import dev.karoorestaurant.data.overpass.OverpassClient
import dev.karoorestaurant.data.poi.OpeningHours
import dev.karoorestaurant.data.poi.PoiCategory
import dev.karoorestaurant.data.route.CorridorSlicer
import dev.karoorestaurant.data.route.Geo
import dev.karoorestaurant.data.route.LatLng
import dev.karoorestaurant.data.store.PoiStore
import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime

private val SAMPLE_ROUTE = listOf(
    LatLng(52.3676, 4.9041),
    LatLng(52.3791, 4.8410),
    LatLng(52.3874, 4.6462),
    LatLng(52.3625, 4.5667),
    LatLng(52.3874, 4.6462),
    LatLng(52.3791, 4.8410),
    LatLng(52.3676, 4.9041),
)

fun main(args: Array<String>) = runBlocking {
    val dbPath = args.firstOrNull { it.startsWith("--db=") }?.removePrefix("--db=") ?: "pois.sqlite"
    val sampleStep = args.firstOrNull { it.startsWith("--step=") }
        ?.removePrefix("--step=")?.toDouble() ?: 2_000.0
    val radius = args.firstOrNull { it.startsWith("--radius=") }
        ?.removePrefix("--radius=")?.toInt() ?: 10_000
    val forceFetch = args.contains("--refetch")

    println("== karoo-restaurant data prototype ==")
    println("Route legs: ${SAMPLE_ROUTE.size}")

    val polylineMeters = SAMPLE_ROUTE.zipWithNext { a, b -> Geo.haversineMeters(a, b) }.sum()
    println("Total polyline length: ${"%.1f".format(polylineMeters / 1000)} km")

    val samples = CorridorSlicer.sample(SAMPLE_ROUTE, sampleStep)
    println("Sample points along corridor: ${samples.size} (every ${sampleStep.toInt()} m)")

    PoiStore.open(dbPath).use { store ->
        if (forceFetch || store.count() == 0) {
            println("\nFetching POIs from Overpass (radius ${radius} m)…")
            val pois = OverpassClient().fetchCorridor(samples, radius)
            println("Returned: ${pois.size} POIs")
            store.upsertAll(pois)
        } else {
            println("\nUsing cached POIs in $dbPath (--refetch to force).")
        }

        println("\nCached in $dbPath: ${store.count()} POIs total")
        store.countByCategory().toSortedMap().forEach { (c, n) ->
            println("  ${c.label.padEnd(12)} $n")
        }

        val now = LocalDateTime.now()
        val origin = SAMPLE_ROUTE.first()
        println("\nNearest non-closed per category from start of route ($origin):")
        for (category in PoiCategory.values()) {
            val candidates = store.nearest(origin, category, maxMeters = 30_000.0, limit = 50)
            val pick = candidates.firstNotNullOfOrNull { (poi, dist) ->
                val status = OpeningHours.evaluate(poi.openingHoursTag, now)
                if (status is OpeningHours.Status.Closed) null else Triple(poi, dist, status)
            }
            val line = pick?.let { (poi, dist, status) ->
                val badge = when (status) {
                    is OpeningHours.Status.Open -> "open"
                    is OpeningHours.Status.Unknown -> "hours unknown"
                    OpeningHours.Status.Closed -> "closed"
                }
                "${poi.name} — ${"%.1f km".format(dist / 1000)} [$badge]"
            } ?: "no candidates"
            println("  ${category.label.padEnd(12)} $line")
        }

        val tagged = store.count() // just the cache size
        println("\nopening_hours coverage in cache: ${
            "%.0f%%".format(coverageRate(store) * 100)
        }")
    }
}

private fun coverageRate(store: PoiStore): Double {
    val total = store.count().coerceAtLeast(1)
    val withTag = store.countWithOpeningHours()
    return withTag.toDouble() / total
}
