package dev.karoorestaurant

import dev.karoorestaurant.data.poi.Poi
import dev.karoorestaurant.data.poi.PoiCategory
import dev.karoorestaurant.telemetry.FakeSharedPreferences
import io.hammerhead.karooext.models.OnNavigationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
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
        val client = KarooClient(port, store, overpass = { _, _, _ ->
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
        val client = KarooClient(port, store, overpass = { _, _, _ -> fixturePois })
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
        val client = KarooClient(port, store, overpass = { _, _, _ ->
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
    fun `retries on cooldown elapse after fetch failure`() = runTest {
        val port = FakeKarooSystemPort()
        val store = InMemoryPoiStore()
        var fetchCount = 0
        val client = KarooClient(port, store, overpass = { _, _, _ ->
            fetchCount++
            if (fetchCount == 1) error("simulated network failure")
            fixturePois
        })
        val watcherScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        val watcher = RouteWatcher(client, scope = watcherScope, retryCooldownMs = 100L)
        watcher.start()

        port.emitNavigationState(navigatingRoute(testPolyline))
        assertTrue(watcher.state.value is RouteFetchState.Error)
        assertEquals(1, fetchCount)

        // Cooldown elapse alone must trigger the retry — no location signal required.
        advanceTimeBy(150L)

        assertEquals(2, fetchCount, "the cooldown elapse must trigger a retry")
        assertTrue(watcher.state.value is RouteFetchState.Cached)
    }

    @Test
    fun `gives up after maxAttempts failed retries`() = runTest {
        val port = FakeKarooSystemPort()
        val store = InMemoryPoiStore()
        var fetchCount = 0
        val client = KarooClient(port, store, overpass = { _, _, _ ->
            fetchCount++
            error("always fails")
        })
        val watcherScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        RouteWatcher(client, scope = watcherScope, retryCooldownMs = 100L, maxAttempts = 3).start()

        port.emitNavigationState(navigatingRoute(testPolyline))
        assertEquals(1, fetchCount)

        // 1st retry fires after the cooldown.
        advanceTimeBy(150L)
        assertEquals(2, fetchCount)

        // 2nd retry — total 3 attempts.
        advanceTimeBy(150L)
        assertEquals(3, fetchCount)

        // No 4th attempt: the watcher has given up.
        advanceTimeBy(500L)
        assertEquals(3, fetchCount, "must not retry beyond maxAttempts")
    }

    @Test
    fun `does not retry before cooldown elapses`() = runTest {
        val port = FakeKarooSystemPort()
        val store = InMemoryPoiStore()
        var fetchCount = 0
        val client = KarooClient(port, store, overpass = { _, _, _ ->
            fetchCount++
            if (fetchCount == 1) error("simulated failure") else fixturePois
        })
        val watcherScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        RouteWatcher(client, scope = watcherScope, retryCooldownMs = 100L).start()

        port.emitNavigationState(navigatingRoute(testPolyline))
        assertEquals(1, fetchCount)

        // Inside delay(cooldown): no retry yet.
        advanceTimeBy(50L)
        assertEquals(1, fetchCount, "must not retry while inside the cooldown")

        // Past the cooldown: retry fires.
        advanceTimeBy(60L)
        assertEquals(2, fetchCount)
    }

    @Test
    fun `diary records a success entry after a successful fetch`() = runTest {
        val port = FakeKarooSystemPort()
        val store = InMemoryPoiStore()
        val client = KarooClient(port, store, overpass = { _, _, _ -> fixturePois })
        val diary = FetchDiary(FakeSharedPreferences(), nowEpochMillis = { 1L })
        val watcherScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        RouteWatcher(client, scope = watcherScope, diary = diary).start()

        port.emitNavigationState(navigatingRoute(testPolyline, name = "Diary Route"))
        advanceUntilIdle()

        val entries = diary.recent()
        assertEquals(1, entries.size)
        val entry = entries.single()
        assertEquals(FetchDiary.Status.SUCCESS, entry.status)
        assertEquals("Diary Route", entry.routeName)
        assertEquals(1, entry.attempts)
        assertEquals(fixturePois.size, entry.poisFetched)
        assertNull(entry.errorMessage)
        assertNotNull(entry.polylineStartLat)
        assertNotNull(entry.polylineEndLat)
    }

    @Test
    fun `diary records a single error entry after maxAttempts failures`() = runTest {
        val port = FakeKarooSystemPort()
        val store = InMemoryPoiStore()
        val client = KarooClient(port, store, overpass = { _, _, _ -> error("Overpass 429") })
        val diary = FetchDiary(FakeSharedPreferences(), nowEpochMillis = { 1L })
        val watcherScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        RouteWatcher(
            client,
            scope = watcherScope,
            retryCooldownMs = 100L,
            maxAttempts = 3,
            diary = diary,
        ).start()

        port.emitNavigationState(navigatingRoute(testPolyline))
        advanceUntilIdle()

        val entries = diary.recent()
        assertEquals(1, entries.size, "retries collapse into a single terminal diary entry")
        val entry = entries.single()
        assertEquals(FetchDiary.Status.ERROR, entry.status)
        assertEquals(3, entry.attempts)
        assertTrue(entry.errorMessage!!.contains("Overpass 429"))
        assertNull(entry.poisFetched)
    }

    @Test
    fun `diary records nothing when route was already cached`() = runTest {
        val port = FakeKarooSystemPort()
        val store = InMemoryPoiStore()
        // Pre-mark the route as fetched so handleRoute short-circuits.
        store.recordRouteFetch(testPolyline.hashCode().toString())
        val client = KarooClient(port, store, overpass = { _, _, _ -> fixturePois })
        val diary = FetchDiary(FakeSharedPreferences(), nowEpochMillis = { 1L })
        val watcherScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        RouteWatcher(client, scope = watcherScope, diary = diary).start()

        port.emitNavigationState(navigatingRoute(testPolyline))
        advanceUntilIdle()

        assertEquals(emptyList<FetchDiary.Entry>(), diary.recent())
    }

    @Test
    fun `retries fetch when connectivity becomes available after error state`() = runTest {
        val port = FakeKarooSystemPort()
        val store = InMemoryPoiStore()
        var fetchCount = 0
        val client = KarooClient(port, store, overpass = { _, _, _ ->
            fetchCount++
            if (fetchCount <= 3) error("offline") else fixturePois
        })
        val connectivity = FakeConnectivityWatcher()
        val watcherScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        val watcher = RouteWatcher(
            client,
            scope = watcherScope,
            retryCooldownMs = 100L,
            maxAttempts = 3,
            connectivity = connectivity,
        )
        watcher.start()

        port.emitNavigationState(navigatingRoute(testPolyline))
        advanceUntilIdle()
        assertEquals(3, fetchCount)
        assertTrue(watcher.state.value is RouteFetchState.Error)

        connectivity.emitAvailable()
        advanceUntilIdle()

        assertEquals(4, fetchCount, "network availability must retrigger the fetch from error state")
        assertTrue(watcher.state.value is RouteFetchState.Cached)
    }

    @Test
    fun `ignores connectivity events when not in error state`() = runTest {
        val port = FakeKarooSystemPort()
        val store = InMemoryPoiStore()
        var fetchCount = 0
        val client = KarooClient(port, store, overpass = { _, _, _ ->
            fetchCount++
            fixturePois
        })
        val connectivity = FakeConnectivityWatcher()
        val watcherScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        RouteWatcher(client, scope = watcherScope, connectivity = connectivity).start()

        port.emitNavigationState(navigatingRoute(testPolyline))
        advanceUntilIdle()
        assertEquals(1, fetchCount)

        connectivity.emitAvailable()
        connectivity.emitAvailable()
        advanceUntilIdle()

        assertEquals(1, fetchCount, "connectivity events must not refetch a cached route")
    }

    @Test
    fun `ignores connectivity events when no route has been loaded`() = runTest {
        val port = FakeKarooSystemPort()
        val store = InMemoryPoiStore()
        var fetchCount = 0
        val client = KarooClient(port, store, overpass = { _, _, _ ->
            fetchCount++
            fixturePois
        })
        val connectivity = FakeConnectivityWatcher()
        val watcherScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        RouteWatcher(client, scope = watcherScope, connectivity = connectivity).start()

        connectivity.emitAvailable()
        advanceUntilIdle()

        assertEquals(0, fetchCount)
    }

    @Test
    fun `same route re-emitted after a terminal failure triggers a fresh fetch`() = runTest {
        // Matches today's diary: route loads, fetch fails all maxAttempts (DNS down),
        // rider re-loads the same route a minute later, second fetch succeeds.
        val port = FakeKarooSystemPort()
        val store = InMemoryPoiStore()
        var fetchCount = 0
        val client = KarooClient(port, store, overpass = { _, _, _ ->
            fetchCount++
            if (fetchCount <= 3) error("DNS unavailable") else fixturePois
        })
        val watcherScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        val watcher = RouteWatcher(
            client,
            scope = watcherScope,
            retryCooldownMs = 100L,
            maxAttempts = 3,
        )
        watcher.start()

        port.emitNavigationState(navigatingRoute(testPolyline))
        advanceUntilIdle()
        assertEquals(3, fetchCount, "first emit exhausts maxAttempts")
        assertTrue(watcher.state.value is RouteFetchState.Error)
        assertFalse(
            store.wasRouteFetched(testPolyline.hashCode().toString()),
            "terminal failure must not record the route as fetched",
        )

        // Idle in between is what allows distinctUntilChangedBy { it?.id } to let the
        // same route id through on re-emit.
        port.emitNavigationState(OnNavigationState(OnNavigationState.NavigationState.Idle))
        advanceUntilIdle()
        port.emitNavigationState(navigatingRoute(testPolyline))
        advanceUntilIdle()

        assertEquals(4, fetchCount, "re-emit after Idle must trigger a fresh attempt on the same route")
        assertTrue(watcher.state.value is RouteFetchState.Cached)
        assertTrue(store.wasRouteFetched(testPolyline.hashCode().toString()))
    }

    @Test
    fun `refetches when a different route is emitted`() = runTest {
        val port = FakeKarooSystemPort()
        val store = InMemoryPoiStore()
        var fetchCount = 0
        val client = KarooClient(port, store, overpass = { _, _, _ ->
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

    private class FakeConnectivityWatcher : ConnectivityWatcher {
        private val events = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
        override val onAvailable: Flow<Unit> = events
        fun emitAvailable() {
            events.tryEmit(Unit)
        }
    }
}
