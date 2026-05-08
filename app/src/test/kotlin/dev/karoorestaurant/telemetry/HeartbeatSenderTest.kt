package dev.karoorestaurant.telemetry

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class HeartbeatSenderTest {

    private lateinit var server: MockWebServer
    private lateinit var sender: HeartbeatSender

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        sender = HeartbeatSender(
            supabaseUrl = server.url("").toString().trimEnd('/'),
            anonKey = "anon-test-key",
        )
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun samplePayload() = HeartbeatPayload(
        install_id = "00000000-0000-4000-8000-000000000abc",
        day = "2026-05-06",
        tile_renders = 7,
        prefetch_count = 1,
        app_version = "0.1.0-test",
    )

    @Test
    fun `posts JSON payload to heartbeats endpoint with required headers`() {
        server.enqueue(MockResponse().setResponseCode(201))

        sender.send(samplePayload())

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/rest/v1/heartbeats?on_conflict=install_id,day", recorded.path)
        assertEquals("anon-test-key", recorded.getHeader("apikey"))
        assertEquals("Bearer anon-test-key", recorded.getHeader("Authorization"))
        assertEquals("resolution=merge-duplicates,return=minimal", recorded.getHeader("Prefer"))
        assertNotNull(recorded.getHeader("Content-Type"))
        assertTrue(recorded.getHeader("Content-Type")!!.startsWith("application/json"))

        val body = Json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
        assertEquals("00000000-0000-4000-8000-000000000abc", body["install_id"]?.jsonPrimitive?.content)
        assertEquals("2026-05-06", body["day"]?.jsonPrimitive?.content)
        assertEquals("7", body["tile_renders"]?.jsonPrimitive?.content)
        assertEquals("1", body["prefetch_count"]?.jsonPrimitive?.content)
        assertEquals("0.1.0-test", body["app_version"]?.jsonPrimitive?.content)
    }

    @Test
    fun `returns true on 201 Created`() {
        server.enqueue(MockResponse().setResponseCode(201))
        assertTrue(sender.send(samplePayload()))
    }

    @Test
    fun `returns true on 200 OK (merged via on_conflict)`() {
        server.enqueue(MockResponse().setResponseCode(200))
        assertTrue(sender.send(samplePayload()))
    }

    @Test
    fun `returns false on 401 Unauthorized`() {
        server.enqueue(MockResponse().setResponseCode(401))
        assertFalse(sender.send(samplePayload()))
    }

    @Test
    fun `returns false on 403 RLS rejection`() {
        server.enqueue(MockResponse().setResponseCode(403))
        assertFalse(sender.send(samplePayload()))
    }

    @Test
    fun `returns false on 500 Server Error`() {
        server.enqueue(MockResponse().setResponseCode(500))
        assertFalse(sender.send(samplePayload()))
    }

    @Test
    fun `returns false on connection failure (server unreachable)`() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        assertFalse(sender.send(samplePayload()))
    }
}
