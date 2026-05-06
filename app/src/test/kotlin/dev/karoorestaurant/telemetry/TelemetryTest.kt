package dev.karoorestaurant.telemetry

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
    fun `does not call send again when last_sent_day matches today`() = runTest {
        val recorder = SendRecorder()
        val prefs = FakeSharedPreferences()
        prefs.edit().putString(KEY_LAST_SENT_DAY, todayUtc()).apply()
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

        val telemetry = Telemetry(prefs, recorder::record, scope, canSend = { true })
        telemetry.recordTileRender()
        telemetry.recordPrefetch()
        advanceUntilIdle()

        assertEquals(0, recorder.calls.size)
    }

    @Test
    fun `successful send produces payload with current counters and advances last_sent_day`() = runTest {
        val recorder = SendRecorder(response = true)
        val prefs = FakeSharedPreferences()
        prefs.edit().putString(KEY_LAST_SENT_DAY, todayUtc()).apply()
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

        val telemetry = Telemetry(prefs, recorder::record, scope, canSend = { true })
        prefs.edit().remove(KEY_LAST_SENT_DAY).apply()

        telemetry.recordTileRender()
        advanceUntilIdle()

        assertEquals(1, recorder.calls.size)
        val payload = recorder.calls.first()
        assertEquals(1, payload.tile_renders)
        assertEquals(0, payload.prefetch_count)
        assertEquals(todayUtc(), payload.day)
        assertEquals(todayUtc(), prefs.getString(KEY_LAST_SENT_DAY, null))
    }

    @Test
    fun `further increments on the same day are gated and do not produce another send`() = runTest {
        val recorder = SendRecorder(response = true)
        val prefs = FakeSharedPreferences()
        prefs.edit().putString(KEY_LAST_SENT_DAY, todayUtc()).apply()
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

        val telemetry = Telemetry(prefs, recorder::record, scope, canSend = { true })
        prefs.edit().remove(KEY_LAST_SENT_DAY).apply()

        telemetry.recordTileRender()
        advanceUntilIdle()
        assertEquals(1, recorder.calls.size)

        telemetry.recordTileRender()
        telemetry.recordPrefetch()
        advanceUntilIdle()

        assertEquals(1, recorder.calls.size, "same-day events must not trigger a second send")
    }

    @Test
    fun `successful send resets counters that drove it`() = runTest {
        val recorder = SendRecorder(response = true)
        val prefs = FakeSharedPreferences()
        prefs.edit().putString(KEY_LAST_SENT_DAY, todayUtc()).apply()
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

        val telemetry = Telemetry(prefs, recorder::record, scope, canSend = { true })
        prefs.edit().remove(KEY_LAST_SENT_DAY).apply()

        // First send: triggered by a single tile render. After this, counters are reset to (0, 0).
        telemetry.recordTileRender()
        advanceUntilIdle()
        assertEquals(1, recorder.calls.first().tile_renders)

        // Roll the day immediately, with no intervening events. The next send must reflect ONLY
        // events from this point on — anything carried over would prove the reset failed.
        prefs.edit().remove(KEY_LAST_SENT_DAY).apply()
        recorder.calls.clear()

        telemetry.recordPrefetch()
        advanceUntilIdle()

        assertEquals(1, recorder.calls.size)
        val nextPayload = recorder.calls.first()
        assertEquals(0, nextPayload.tile_renders, "tile renders were reset by the previous send")
        assertEquals(1, nextPayload.prefetch_count, "only the new prefetch is in this payload")
    }

    @Test
    fun `failed send keeps last_sent_day unset and restores counters`() = runTest {
        val recorder = SendRecorder(response = false)
        val prefs = FakeSharedPreferences()
        prefs.edit().putString(KEY_LAST_SENT_DAY, todayUtc()).apply()
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

        val telemetry = Telemetry(prefs, recorder::record, scope, canSend = { true })
        prefs.edit().remove(KEY_LAST_SENT_DAY).apply()

        telemetry.recordTileRender()
        advanceUntilIdle()

        assertEquals(1, recorder.calls.size)
        assertNull(prefs.getString(KEY_LAST_SENT_DAY, null), "failure must not advance last_sent_day")

        // Flip to success and trigger a new attempt; the restored tile render should still be there.
        recorder.response = true
        recorder.calls.clear()
        telemetry.recordPrefetch()
        advanceUntilIdle()

        assertEquals(1, recorder.calls.size)
        val payload = recorder.calls.first()
        assertEquals(1, payload.tile_renders, "restored from the failed first send")
        assertEquals(1, payload.prefetch_count, "the new prefetch")
        assertEquals(todayUtc(), prefs.getString(KEY_LAST_SENT_DAY, null))
    }

    @Test
    fun `payload uses the persisted install_id and current day`() = runTest {
        val recorder = SendRecorder()
        val prefs = FakeSharedPreferences()
        val storedInstallId = "11111111-2222-4333-8444-555555555555"
        prefs.edit().putString(KEY_INSTALL_ID, storedInstallId).apply()
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

        Telemetry(prefs, recorder::record, scope, canSend = { true })
        advanceUntilIdle()

        assertEquals(1, recorder.calls.size, "init triggers an attempt when last_sent_day is unset")
        val payload = recorder.calls.first()
        assertEquals(storedInstallId, payload.install_id)
        assertEquals(todayUtc(), payload.day)
    }
}
