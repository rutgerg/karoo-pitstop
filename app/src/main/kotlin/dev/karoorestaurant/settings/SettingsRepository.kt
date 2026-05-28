package dev.karoorestaurant.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

internal val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

internal val KEY_TELEMETRY_ENABLED = booleanPreferencesKey("telemetry_enabled")
internal val KEY_SHOW_CLOSED_POIS = booleanPreferencesKey("show_closed_pois")

class SettingsRepository internal constructor(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    constructor(context: Context) : this(context.settingsDataStore)

    val telemetryEnabled: StateFlow<Boolean> = dataStore.data
        .map { it[KEY_TELEMETRY_ENABLED] ?: DEFAULT_TELEMETRY_ENABLED }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = DEFAULT_TELEMETRY_ENABLED,
        )

    val showClosedPois: StateFlow<Boolean> = dataStore.data
        .map { it[KEY_SHOW_CLOSED_POIS] ?: DEFAULT_SHOW_CLOSED_POIS }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = DEFAULT_SHOW_CLOSED_POIS,
        )

    suspend fun setTelemetryEnabled(value: Boolean) {
        dataStore.edit { it[KEY_TELEMETRY_ENABLED] = value }
    }

    suspend fun setShowClosedPois(value: Boolean) {
        dataStore.edit { it[KEY_SHOW_CLOSED_POIS] = value }
    }

    companion object {
        const val DEFAULT_TELEMETRY_ENABLED: Boolean = true
        const val DEFAULT_SHOW_CLOSED_POIS: Boolean = false
    }
}
