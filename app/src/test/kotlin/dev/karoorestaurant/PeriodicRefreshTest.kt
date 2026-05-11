package dev.karoorestaurant

import dev.karoorestaurant.data.poi.Poi
import dev.karoorestaurant.data.poi.PoiCategory
import dev.karoorestaurant.data.route.LatLng
import dev.karoorestaurant.telemetry.FakeSharedPreferences
import io.hammerhead.karooext.models.OnLocationChanged
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PeriodicRefreshTest {

    private val fixturePois = listOf(
        Poi(osmId = 1L, osmType = "node", name = "Café One", category = PoiCategory.CAFE, lat = 52.0, lon = 4.0),
    )

    private fun TestScope.newRefreshScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

    @Test
    fun `refreshes around latest location after each interval`() = runTest {
        val port = FakeKarooSystemPort()
        val store = InMemoryPoiStore()
        var fetchCount = 0
        val capturedCenters = mutableListOf<LatLng>()
        var capturedRadius = 0
        val client = KarooClient(port, store, overpass = { windows, radius, _ ->
            fetchCount++
            capturedCenters += windows.flatten()
            capturedRadius = radius
            fixturePois
        })
        val refreshScope = newRefreshScope()
        PeriodicRefresh(
            karoo = client,
            intervalMs = 1_000L,
            radiusMeters = 5_000,
            scope = refreshScope,
        ).start()

        port.emitLocation(OnLocationChanged(lat = 52.5, lng = 4.5, orientation = null))
        advanceTimeBy(1_100L)

        assertEquals(1, fetchCount)
        assertEquals(LatLng(52.5, 4.5), capturedCenters.single())
        assertEquals(5_000, capturedRadius)

        port.emitLocation(OnLocationChanged(lat = 53.0, lng = 5.0, orientation = null))
        advanceTimeBy(1_000L)

        assertEquals(2, fetchCount, "second tick must refresh again")
        assertEquals(LatLng(53.0, 5.0), capturedCenters.last(), "refresh uses the latest location")

        refreshScope.cancel()
    }

    @Test
    fun `skips refresh when no location has been received yet`() = runTest {
        var fetchCount = 0
        val client = KarooClient(
            FakeKarooSystemPort(),
            InMemoryPoiStore(),
            overpass = { _, _, _ ->
                fetchCount++
                emptyList()
            },
        )
        val refreshScope = newRefreshScope()
        PeriodicRefresh(client, intervalMs = 1_000L, scope = refreshScope).start()

        advanceTimeBy(2_500L)

        assertEquals(0, fetchCount, "no location yet — refresh must not fire")

        refreshScope.cancel()
    }

    @Test
    fun `records a periodic SUCCESS diary entry per successful tick`() = runTest {
        val port = FakeKarooSystemPort()
        val client = KarooClient(port, InMemoryPoiStore(), overpass = { _, _, _ -> fixturePois })
        val diary = FetchDiary(FakeSharedPreferences(), nowEpochMillis = { 42L })
        val refreshScope = newRefreshScope()
        PeriodicRefresh(client, intervalMs = 1_000L, diary = diary, scope = refreshScope).start()

        port.emitLocation(OnLocationChanged(lat = 52.5, lng = 4.5, orientation = null))
        advanceTimeBy(1_100L)

        val entry = diary.recent().single()
        assertEquals(FetchDiary.Kind.PERIODIC, entry.kind)
        assertEquals(FetchDiary.Status.SUCCESS, entry.status)
        assertEquals(fixturePois.size, entry.poisFetched)
        assertEquals(52.5, entry.polylineStartLat)
        assertEquals(4.5, entry.polylineStartLon)
        assertNull(entry.polylineEndLat)
        assertNull(entry.errorMessage)
        assertEquals(42L, entry.atEpochMillis)

        refreshScope.cancel()
    }

    @Test
    fun `records a periodic ERROR diary entry when the fetch throws`() = runTest {
        val port = FakeKarooSystemPort()
        val client = KarooClient(
            port,
            InMemoryPoiStore(),
            overpass = { _, _, _ -> error("offline") },
        )
        val diary = FetchDiary(FakeSharedPreferences(), nowEpochMillis = { 1L })
        val refreshScope = newRefreshScope()
        PeriodicRefresh(client, intervalMs = 1_000L, diary = diary, scope = refreshScope).start()

        port.emitLocation(OnLocationChanged(lat = 52.0, lng = 4.0, orientation = null))
        advanceTimeBy(1_100L)

        val entry = diary.recent().single()
        assertEquals(FetchDiary.Kind.PERIODIC, entry.kind)
        assertEquals(FetchDiary.Status.ERROR, entry.status)
        assertNull(entry.poisFetched)
        assertNotNull(entry.errorMessage)
        assertTrue(entry.errorMessage!!.contains("offline"))

        refreshScope.cancel()
    }

    @Test
    fun `does not record a diary entry when there is no location yet`() = runTest {
        val client = KarooClient(
            FakeKarooSystemPort(),
            InMemoryPoiStore(),
            overpass = { _, _, _ -> fixturePois },
        )
        val diary = FetchDiary(FakeSharedPreferences(), nowEpochMillis = { 1L })
        val refreshScope = newRefreshScope()
        PeriodicRefresh(client, intervalMs = 1_000L, diary = diary, scope = refreshScope).start()

        advanceTimeBy(2_500L)

        assertEquals(emptyList<FetchDiary.Entry>(), diary.recent())

        refreshScope.cancel()
    }

    @Test
    fun `keeps ticking after a fetch failure`() = runTest {
        val port = FakeKarooSystemPort()
        var fetchCount = 0
        val client = KarooClient(
            port,
            InMemoryPoiStore(),
            overpass = { _, _, _ ->
                fetchCount++
                if (fetchCount == 1) error("offline") else fixturePois
            },
        )
        val refreshScope = newRefreshScope()
        PeriodicRefresh(client, intervalMs = 1_000L, scope = refreshScope).start()

        port.emitLocation(OnLocationChanged(lat = 52.0, lng = 4.0, orientation = null))
        advanceTimeBy(1_100L)
        assertEquals(1, fetchCount)

        advanceTimeBy(1_000L)
        assertEquals(2, fetchCount, "swallowed failure must not stop the timer")

        refreshScope.cancel()
    }
}
