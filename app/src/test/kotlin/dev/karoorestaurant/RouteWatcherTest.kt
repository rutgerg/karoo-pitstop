package dev.karoorestaurant

import dev.karoorestaurant.data.poi.Poi
import dev.karoorestaurant.data.poi.PoiCategory
import io.hammerhead.karooext.models.OnNavigationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RouteWatcherTest {

    private val testPolyline = "_p~iF~ps|U_ulLnnqC_mqNvxq`@"

    private val fixturePois = listOf(
        Poi(osmId = 1L, osmType = "node", name = "Café One", category = PoiCategory.RESTAURANT, lat = 38.5, lon = -120.2),
        Poi(osmId = 2L, osmType = "node", name = "Mercadona Centro", category = PoiCategory.SUPERMARKET, lat = 40.7, lon = -120.95),
        Poi(osmId = 3L, osmType = "node", name = "Repsol", category = PoiCategory.FUEL, lat = 43.252, lon = -126.453),
    )

    private fun navigatingRoute(polyline: String, name: String = "Test Route") =
        OnNavigationState(
            OnNavigationState.NavigationState.NavigatingRoute(
                routePolyline = polyline,
                routeDistance = 1234.0,
                rejoinPolyline = null,
                rejoinDistance = null,
                name = name,
                reversed = false,
                breadcrumb = false,
                pois = emptyList(),
            ),
        )

    @Test
    fun `upserts POIs on new route`() = runTest {
        val port = FakeKarooSystemPort()
        val store = InMemoryPoiStore()
        var fetchCount = 0
        val client = KarooClient(port, store, overpass = { _, _ ->
            fetchCount++
            fixturePois
        })
        val watcherScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        RouteWatcher(client, scope = watcherScope).start()

        port.emitNavigationState(navigatingRoute(testPolyline))
        advanceUntilIdle()

        assertEquals(1, fetchCount)
        assertEquals(fixturePois.size, store.count())
        assertEquals(1, store.upsertCount)
    }

    @Test
    fun `records route fetch in dedup table`() = runTest {
        val port = FakeKarooSystemPort()
        val store = InMemoryPoiStore()
        val client = KarooClient(port, store, overpass = { _, _ -> fixturePois })
        val watcherScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        RouteWatcher(client, scope = watcherScope).start()

        port.emitNavigationState(navigatingRoute(testPolyline))
        advanceUntilIdle()

        // The route id is the polyline's hashCode.toString().
        val routeId = testPolyline.hashCode().toString()
        assertTrue(store.wasRouteFetched(routeId))
    }

    @Test
    fun `does not refetch when same route is re-emitted`() = runTest {
        val port = FakeKarooSystemPort()
        val store = InMemoryPoiStore()
        var fetchCount = 0
        val client = KarooClient(port, store, overpass = { _, _ ->
            fetchCount++
            fixturePois
        })
        val watcherScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        RouteWatcher(client, scope = watcherScope).start()

        port.emitNavigationState(navigatingRoute(testPolyline))
        advanceUntilIdle()
        assertEquals(1, fetchCount)

        // The KarooClient.routeFlow distinctUntilChangedBy id collapses identical re-emits,
        // and an Idle-then-same-route still hits the dedup table check.
        port.emitNavigationState(OnNavigationState(OnNavigationState.NavigationState.Idle))
        advanceUntilIdle()
        port.emitNavigationState(navigatingRoute(testPolyline))
        advanceUntilIdle()

        assertEquals(1, fetchCount, "second emit of the same route id must not trigger a refetch")
    }

    @Test
    fun `refetches when a different route is emitted`() = runTest {
        val port = FakeKarooSystemPort()
        val store = InMemoryPoiStore()
        var fetchCount = 0
        val client = KarooClient(port, store, overpass = { _, _ ->
            fetchCount++
            fixturePois
        })
        val watcherScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        RouteWatcher(client, scope = watcherScope).start()

        port.emitNavigationState(navigatingRoute(testPolyline, name = "Route A"))
        advanceUntilIdle()

        val otherPolyline = "u{~vFvyys@fS]"  // different encoded polyline
        port.emitNavigationState(navigatingRoute(otherPolyline, name = "Route B"))
        advanceUntilIdle()

        assertEquals(2, fetchCount)
        assertNotEquals(testPolyline.hashCode(), otherPolyline.hashCode())
        assertTrue(store.wasRouteFetched(testPolyline.hashCode().toString()))
        assertTrue(store.wasRouteFetched(otherPolyline.hashCode().toString()))
    }
}
