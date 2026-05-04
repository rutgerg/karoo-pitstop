package dev.karoorestaurant

import dev.karoorestaurant.data.poi.PoiCategory
import dev.karoorestaurant.data.route.LatLng
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class KarooOverpassFetcherTest {

    private val restaurantOne = """
        {"elements":[{"type":"node","id":1,"lat":52.0,"lon":4.0,"tags":{"name":"One","amenity":"restaurant"}}]}
    """.trimIndent().toByteArray(Charsets.UTF_8)

    private val restaurantTwo = """
        {"elements":[{"type":"node","id":2,"lat":52.1,"lon":4.0,"tags":{"name":"Two","amenity":"restaurant"}}]}
    """.trimIndent().toByteArray(Charsets.UTF_8)

    private val restaurantOneAndTwo = """
        {"elements":[
          {"type":"node","id":1,"lat":52.0,"lon":4.0,"tags":{"name":"One","amenity":"restaurant"}},
          {"type":"node","id":2,"lat":52.1,"lon":4.0,"tags":{"name":"Two","amenity":"restaurant"}}
        ]}
    """.trimIndent().toByteArray(Charsets.UTF_8)

    @Test
    fun `single window 200 response returns parsed POIs`() = runTest {
        val port = FakeKarooSystemPort()
        val fetcher = KarooOverpassFetcher(port, baseBackoffMs = 1L, timeoutMs = 30_000L)
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

        val deferred = scope.async {
            fetcher(listOf(listOf(LatLng(52.0, 4.0))), 1_000)
        }
        assertEquals(1, port.httpRequests.size)
        port.completeLatestHttp(statusCode = 200, body = restaurantOne)

        val pois = deferred.await()
        assertEquals(1, pois.size)
        assertEquals("One", pois.single().name)
        assertEquals(PoiCategory.RESTAURANT, pois.single().category)
    }

    @Test
    fun `multiple windows dedupe across windows by osm id`() = runTest {
        val port = FakeKarooSystemPort()
        val fetcher = KarooOverpassFetcher(port, baseBackoffMs = 1L, timeoutMs = 30_000L)
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

        val deferred = scope.async {
            fetcher(
                listOf(listOf(LatLng(52.0, 4.0)), listOf(LatLng(52.5, 4.0))),
                1_000,
            )
        }

        assertEquals(1, port.httpRequests.size)
        port.completeLatestHttp(statusCode = 200, body = restaurantOne)
        // Under unconfined, resume runs synchronously and the for-loop advances to window 2.
        assertEquals(2, port.httpRequests.size)
        port.completeLatestHttp(statusCode = 200, body = restaurantOneAndTwo)

        val pois = deferred.await()
        assertEquals(2, pois.size)
        assertEquals(setOf("One", "Two"), pois.map { it.name }.toSet())
    }

    @Test
    fun `429 then 200 succeeds after retry`() = runTest {
        val port = FakeKarooSystemPort()
        val fetcher = KarooOverpassFetcher(port, baseBackoffMs = 1L, timeoutMs = 30_000L)
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

        val deferred = scope.async {
            fetcher(listOf(listOf(LatLng(52.0, 4.0))), 1_000)
        }

        assertEquals(1, port.httpRequests.size)
        port.completeLatestHttp(statusCode = 429)
        // After 429 resume, fetcher is suspended in delay(1ms). Step past it.
        advanceTimeBy(2L)

        assertEquals(2, port.httpRequests.size, "fetcher must re-issue the request after a 429")
        port.completeLatestHttp(statusCode = 200, body = restaurantTwo)

        val pois = deferred.await()
        assertEquals(1, pois.size)
        assertEquals("Two", pois.single().name)
    }

    @Test
    fun `gives up after exhausting retries`() = runTest {
        val port = FakeKarooSystemPort()
        val fetcher = KarooOverpassFetcher(port, baseBackoffMs = 1L, timeoutMs = 30_000L, maxRetries = 2)
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

        val deferred = scope.async {
            assertFailsWith<IllegalStateException> {
                fetcher(listOf(listOf(LatLng(52.0, 4.0))), 1_000)
            }
        }

        // Initial + 2 retries = 3 attempts total. Each delay between retries is 1-3 ms.
        for (i in 0 until 3) {
            assertEquals(i + 1, port.httpRequests.size)
            port.completeLatestHttp(statusCode = 429)
            if (i < 2) advanceTimeBy(8L)  // long enough for backoff at attempt 0..1
        }

        val ex = deferred.await()
        assertTrue(ex.message?.contains("429") == true, "expected 429 in message, got ${ex.message}")
    }

    @Test
    fun `non-retryable error fails immediately`() = runTest {
        val port = FakeKarooSystemPort()
        val fetcher = KarooOverpassFetcher(port, baseBackoffMs = 1L, timeoutMs = 30_000L)
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

        val deferred = scope.async {
            assertFailsWith<IllegalStateException> {
                fetcher(listOf(listOf(LatLng(52.0, 4.0))), 1_000)
            }
        }

        port.completeLatestHttp(statusCode = 500)

        val ex = deferred.await()
        assertTrue(ex.message?.contains("500") == true)
        assertEquals(1, port.httpRequests.size, "must not retry non-429 errors")
    }
}
