package dev.karoorestaurant.telemetry

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
class TelemetryTest {

    private class SendRecorder(var response: Boolean = true) {
        val calls = mutableListOf<HeartbeatPayload>()
        fun record(payload: HeartbeatPayload): Boolean {
            calls += payload
            return response
        }
    }

    private fun todayUtc(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

    @Test
    fun `does not call send when canSend returns false`() = runTest {
        val recorder = SendRecorder()
        val prefs = FakeSharedPreferences()
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

        val telemetry = Telemetry(prefs, recorder::record, scope, canSend = { false })
        telemetry.recordTileRender()
        telemetry.recordPrefetch()
        advanceUntilIdle()

        assertEquals(0, recorder.calls.size)
    }

    @Test
    fun `does not send on construction — only on actual events`() = runTest {
        val recorder = SendRecorder()
        val prefs = FakeSharedPreferences()
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

        Telemetry(prefs, recorder::record, scope, canSend = { true })
        advanceUntilIdle()

        assertEquals(0, recorder.calls.size, "init must not fire a 0/0 heartbeat")
    }

    @Test
    fun `recordTileRender sends payload with the cumulative count for today`() = runTest {
        val recorder = SendRecorder(response = true)
        val prefs = FakeSharedPreferences()
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

        val telemetry = Telemetry(prefs, recorder::record, scope, canSend = { true })
        telemetry.recordTileRender()
        advanceUntilIdle()

        assertEquals(1, recorder.calls.size)
        val payload = recorder.calls.first()
        assertEquals(1, payload.tile_renders)
        assertEquals(0, payload.prefetch_count)
        assertEquals(todayUtc(), payload.day)
    }

    @Test
    fun `subsequent events on the same day each resend the latest cumulative count`() = runTest {
        val recorder = SendRecorder(response = true)
        val prefs = FakeSharedPreferences()
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

        val telemetry = Telemetry(prefs, recorder::record, scope, canSend = { true })
        telemetry.recordTileRender()
        advanceUntilIdle()
        telemetry.recordTileRender()
        advanceUntilIdle()
        telemetry.recordPrefetch()
        advanceUntilIdle()

        assertTrue(recorder.calls.size >= 2, "later events must resend, not be silently gated")
        val last = recorder.calls.last()
        assertEquals(2, last.tile_renders, "final send carries the cumulative tile_renders for today")
        assertEquals(1, last.prefetch_count, "final send carries the cumulative prefetch_count for today")
    }

    @Test
    fun `failed send leaves counters in place so the next event resends them`() = runTest {
        val recorder = SendRecorder(response = false)
        val prefs = FakeSharedPreferences()
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

        val telemetry = Telemetry(prefs, recorder::record, scope, canSend = { true })
        telemetry.recordTileRender()
        advanceUntilIdle()
        assertEquals(1, recorder.calls.size)
        assertEquals(1, recorder.calls.last().tile_renders)

        recorder.response = true
        recorder.calls.clear()
        telemetry.recordPrefetch()
        advanceUntilIdle()

        assertEquals(1, recorder.calls.size)
        val retry = recorder.calls.first()
        assertEquals(1, retry.tile_renders, "the tile render that failed to send is still in the counter")
        assertEquals(1, retry.prefetch_count)
    }

    @Test
    fun `payload uses the persisted install_id`() = runTest {
        val recorder = SendRecorder()
        val prefs = FakeSharedPreferences()
        val storedInstallId = "11111111-2222-4333-8444-555555555555"
        prefs.edit().putString(KEY_INSTALL_ID, storedInstallId).apply()
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

        val telemetry = Telemetry(prefs, recorder::record, scope, canSend = { true })
        telemetry.recordTileRender()
        advanceUntilIdle()

        assertEquals(1, recorder.calls.size)
        val payload = recorder.calls.first()
        assertEquals(storedInstallId, payload.install_id)
        assertEquals(todayUtc(), payload.day)
    }
}
