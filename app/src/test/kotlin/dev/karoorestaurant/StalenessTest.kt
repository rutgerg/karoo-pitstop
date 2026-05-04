package dev.karoorestaurant

import dev.karoorestaurant.data.poi.Poi
import dev.karoorestaurant.data.poi.PoiCategory
import dev.karoorestaurant.data.route.LatLng
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class StalenessTest {

    private val now: Instant = Instant.parse("2026-05-04T12:00:00Z")
    private fun daysAgo(days: Long): Instant = now.minus(Duration.ofDays(days))

    @Test
    fun `under 14 days is NEW`() {
        assertEquals(Staleness.NEW, stalenessOf(daysAgo(0), now))
        assertEquals(Staleness.NEW, stalenessOf(daysAgo(1), now))
        assertEquals(Staleness.NEW, stalenessOf(daysAgo(13), now))
    }

    @Test
    fun `at exactly 14 days is AGING`() {
        assertEquals(Staleness.AGING, stalenessOf(daysAgo(14), now))
    }

    @Test
    fun `between 14 and 60 days is AGING`() {
        assertEquals(Staleness.AGING, stalenessOf(daysAgo(30), now))
        assertEquals(Staleness.AGING, stalenessOf(daysAgo(59), now))
    }

    @Test
    fun `at exactly 60 days is EXPIRED`() {
        assertEquals(Staleness.EXPIRED, stalenessOf(daysAgo(60), now))
    }

    @Test
    fun `beyond 60 days is EXPIRED`() {
        assertEquals(Staleness.EXPIRED, stalenessOf(daysAgo(61), now))
        assertEquals(Staleness.EXPIRED, stalenessOf(daysAgo(365), now))
    }

    @Test
    fun `nearest filters out expired rows from the store`() {
        val store = InMemoryPoiStore()
        val now = Instant.parse("2026-05-04T12:00:00Z")
        // Insert with two different timestamps via two upsertAll calls.
        store.upsertAll(
            listOf(makePoi(osmId = 1L, name = "Fresh")),
            fetchedAt = now.minus(Duration.ofDays(5)),
        )
        store.upsertAll(
            listOf(makePoi(osmId = 2L, name = "Aging")),
            fetchedAt = now.minus(Duration.ofDays(30)),
        )
        store.upsertAll(
            listOf(makePoi(osmId = 3L, name = "Expired")),
            fetchedAt = now.minus(Duration.ofDays(90)),
        )

        val hits = store.nearest(
            center = LatLng(52.0, 4.0),
            category = PoiCategory.RESTAURANT,
            maxMeters = 50_000.0,
            limit = 50,
            now = now,
        )

        assertEquals(setOf("Fresh", "Aging"), hits.map { it.poi.name }.toSet())
    }

    @Test
    fun `staleness boundary survives round trip through computeNearbyPicks via store`() {
        val store = InMemoryPoiStore()
        val now = Instant.parse("2026-05-04T12:00:00Z")
        store.upsertAll(listOf(makePoi(osmId = 1L, name = "Fresh")), fetchedAt = now.minus(Duration.ofDays(5)))
        store.upsertAll(listOf(makePoi(osmId = 2L, name = "Aging")), fetchedAt = now.minus(Duration.ofDays(30)))

        val hits = store.nearest(
            center = LatLng(52.0, 4.0),
            category = PoiCategory.RESTAURANT,
            now = now,
        )
        val byName = hits.associateBy { it.poi.name }

        assertEquals(Staleness.NEW, stalenessOf(byName.getValue("Fresh").fetchedAt, now))
        assertEquals(Staleness.AGING, stalenessOf(byName.getValue("Aging").fetchedAt, now))
    }

    private fun makePoi(osmId: Long, name: String): Poi = Poi(
        osmId = osmId,
        osmType = "node",
        name = name,
        category = PoiCategory.RESTAURANT,
        lat = 52.0,
        lon = 4.0,
        openingHoursTag = null,
    )
}
