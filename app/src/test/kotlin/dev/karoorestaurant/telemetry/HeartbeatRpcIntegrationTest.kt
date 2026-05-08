package dev.karoorestaurant.telemetry

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Hits a real Supabase project to verify that the migration's grants, RLS, and
 * SECURITY DEFINER function all line up with what HeartbeatSender expects on
 * the wire. Catches the bug class from #129 where the server side was missing
 * the privileges the client's upsert needed and every send returned 401.
 *
 * Skipped unless both env vars are set, so normal `./gradlew :app:test` runs
 * stay offline. To run:
 *
 *     SUPABASE_INTEGRATION_URL=https://<ref>.supabase.co \
 *     SUPABASE_INTEGRATION_ANON_KEY=<anon-key> \
 *     ./gradlew :app:test --tests HeartbeatRpcIntegrationTest
 */
@EnabledIfEnvironmentVariable(named = "SUPABASE_INTEGRATION_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "SUPABASE_INTEGRATION_ANON_KEY", matches = ".+")
class HeartbeatRpcIntegrationTest {

    private val supabaseUrl = System.getenv("SUPABASE_INTEGRATION_URL")!!.trimEnd('/')
    private val anonKey = System.getenv("SUPABASE_INTEGRATION_ANON_KEY")!!

    private val sender = HeartbeatSender(
        supabaseUrl = supabaseUrl,
        anonKey = anonKey,
        http = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build(),
    )

    private fun todayUtc(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

    private fun freshPayload(tileRenders: Int = 1, prefetchCount: Int = 0) = HeartbeatPayload(
        install_id = UUID.randomUUID().toString(),
        day = todayUtc(),
        tile_renders = tileRenders,
        prefetch_count = prefetchCount,
        app_version = "integ-test",
    )

    @Test
    fun `fresh install_id upserts via the RPC and the function returns 204`() {
        assertTrue(sender.send(freshPayload()), "first send for a fresh install_id must succeed")
    }

    @Test
    fun `re-sending the same install_id and day with new counters returns 204 again — the on-conflict update path works`() {
        val installId = UUID.randomUUID().toString()
        val day = todayUtc()
        val initial = HeartbeatPayload(installId, day, tile_renders = 1, prefetch_count = 0, app_version = "integ-test")
        val updated = HeartbeatPayload(installId, day, tile_renders = 5, prefetch_count = 2, app_version = "integ-test")

        assertTrue(sender.send(initial), "first send (insert path) must succeed")
        assertTrue(sender.send(updated), "second send (update path) must succeed — 401 here means anon's upsert privileges regressed")
    }

    @Test
    fun `day older than yesterday is rejected by the function's day guard`() {
        val twoDaysAgo = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(System.currentTimeMillis() - 2 * 24 * 60 * 60 * 1000L))
        val out = sender.send(
            HeartbeatPayload(
                install_id = UUID.randomUUID().toString(),
                day = twoDaysAgo,
                tile_renders = 1,
                prefetch_count = 0,
                app_version = "integ-test",
            ),
        )
        assertEquals(false, out, "function must reject day < current_date - 1")
    }
}
