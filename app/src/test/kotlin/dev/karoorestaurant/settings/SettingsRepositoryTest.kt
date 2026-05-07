package dev.karoorestaurant.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryTest {

    @TempDir
    lateinit var tempDir: File

    private fun newRepository(scope: CoroutineScope, fileName: String = "settings.preferences_pb"): SettingsRepository {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(tempDir, fileName) },
        )
        return SettingsRepository(dataStore = dataStore, scope = scope)
    }

    @Test
    fun `default telemetryEnabled is true on a fresh install`() = runTest {
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        val repo = newRepository(scope)
        advanceUntilIdle()

        assertTrue(repo.telemetryEnabled.value)
        scope.cancel()
    }

    @Test
    fun `setTelemetryEnabled persists across repository instances`() = runTest {
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        val first = newRepository(scope)
        first.setTelemetryEnabled(false)
        advanceUntilIdle()
        scope.cancel()

        val scope2 = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        val second = newRepository(scope2)
        advanceUntilIdle()

        assertFalse(second.telemetryEnabled.value, "stored value must persist across instances")
        scope2.cancel()
    }

    @Test
    fun `telemetryEnabled flow emits the new value after setTelemetryEnabled`() = runTest {
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        val repo = newRepository(scope)
        advanceUntilIdle()
        assertTrue(repo.telemetryEnabled.value)

        repo.setTelemetryEnabled(false)
        advanceUntilIdle()
        assertFalse(repo.telemetryEnabled.value)

        repo.setTelemetryEnabled(true)
        advanceUntilIdle()
        assertTrue(repo.telemetryEnabled.value)
        scope.cancel()
    }

    @Test
    fun `default exposed as a constant matches the runtime default`() {
        assertEquals(true, SettingsRepository.DEFAULT_TELEMETRY_ENABLED)
    }
}
