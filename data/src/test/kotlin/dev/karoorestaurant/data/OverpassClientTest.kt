package dev.karoorestaurant.data

import dev.karoorestaurant.data.overpass.OverpassClient
import dev.karoorestaurant.data.poi.PoiCategory
import dev.karoorestaurant.data.route.LatLng
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OverpassClientTest {

    private lateinit var server: MockWebServer

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun client() = OverpassClient(
        endpoint = server.url("/api").toString(),
        baseBackoffMs = 1L,
    )

    private fun overpassJson(elements: String): String =
        """{"elements":[$elements]}"""

    private val restaurantOne = """
        {"type":"node","id":1,"lat":52.0,"lon":4.0,"tags":{"name":"One","amenity":"restaurant"}}
    """.trimIndent()

    private val restaurantTwo = """
        {"type":"node","id":2,"lat":52.1,"lon":4.0,"tags":{"name":"Two","amenity":"restaurant"}}
    """.trimIndent()

    @Test
    fun `single window returns parsed POIs`() = runTest {
        server.enqueue(MockResponse().setBody(overpassJson(restaurantOne)).setResponseCode(200))

        val pois = client().fetchCorridor(listOf(listOf(LatLng(52.0, 4.0))), radiusMeters = 1_000)

        assertEquals(1, pois.size)
        assertEquals("One", pois.single().name)
        assertEquals(PoiCategory.RESTAURANT, pois.single().category)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `multiple windows dedupe by osm type and id`() = runTest {
        // Both windows return the same POI with id=1; final list should contain it once.
        server.enqueue(MockResponse().setBody(overpassJson(restaurantOne)).setResponseCode(200))
        server.enqueue(MockResponse().setBody(overpassJson("$restaurantOne,$restaurantTwo")).setResponseCode(200))

        val pois = client().fetchCorridor(
            listOf(listOf(LatLng(52.0, 4.0)), listOf(LatLng(52.5, 4.0))),
            radiusMeters = 1_000,
        )

        assertEquals(2, pois.size)
        assertEquals(setOf("One", "Two"), pois.map { it.name }.toSet())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `retries on 429 with backoff and then succeeds`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429))
        server.enqueue(MockResponse().setResponseCode(429))
        server.enqueue(MockResponse().setBody(overpassJson(restaurantOne)).setResponseCode(200))

        val pois = client().fetchCorridor(listOf(listOf(LatLng(52.0, 4.0))), radiusMeters = 1_000)

        assertEquals(1, pois.size)
        assertEquals(3, server.requestCount, "two 429s plus one 200 = three requests")
    }

    @Test
    fun `gives up after maxRetries 429s`() = runTest {
        repeat(4) { server.enqueue(MockResponse().setResponseCode(429)) }

        val ex = assertFailsWith<IllegalStateException> {
            client().fetchCorridor(listOf(listOf(LatLng(52.0, 4.0))), radiusMeters = 1_000)
        }
        assertTrue(ex.message?.contains("429") == true, "expected 429 in message, got ${ex.message}")
        // Initial + 3 retries = 4 requests
        assertEquals(4, server.requestCount)
    }

    @Test
    fun `non-429 HTTP error fails immediately`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("server crashed"))

        assertFailsWith<IllegalStateException> {
            client().fetchCorridor(listOf(listOf(LatLng(52.0, 4.0))), radiusMeters = 1_000)
        }
        assertEquals(1, server.requestCount)
    }
}
