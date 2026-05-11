package dev.karoorestaurant

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Local-only ring buffer of recent corridor-fetch outcomes. Persists across app restarts
 * via SharedPreferences so a failed prefetch on a ride can be inspected later, even after
 * the Karoo's logcat buffer has rolled. Inspect with
 * `adb shell run-as dev.karoorestaurant cat shared_prefs/fetch_diary.xml`.
 */
class FetchDiary internal constructor(
    private val prefs: SharedPreferences,
    private val nowEpochMillis: () -> Long = { System.currentTimeMillis() },
    private val capacity: Int = DEFAULT_CAPACITY,
) {
    constructor(context: Context) : this(
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
    )

    fun record(entry: Entry) {
        val stamped = entry.copy(atEpochMillis = nowEpochMillis())
        val updated = (recent() + stamped).takeLast(capacity)
        prefs.edit()
            .putString(KEY_ENTRIES, Json.encodeToString(EntryListSerializer, updated))
            .apply()
    }

    fun recent(): List<Entry> {
        val raw = prefs.getString(KEY_ENTRIES, null) ?: return emptyList()
        return runCatching { Json.decodeFromString(EntryListSerializer, raw) }
            .getOrDefault(emptyList())
    }

    @Serializable
    data class Entry(
        val atEpochMillis: Long = 0L,
        val kind: Kind = Kind.ROUTE,
        val routeName: String,
        val routeId: String,
        val polylineLength: Int,
        val polylineStartLat: Double?,
        val polylineStartLon: Double?,
        val polylineEndLat: Double?,
        val polylineEndLon: Double?,
        val windowCount: Int,
        val attempts: Int,
        val status: Status,
        val errorMessage: String? = null,
        val poisFetched: Int? = null,
    )

    @Serializable
    enum class Status { SUCCESS, ERROR }

    @Serializable
    enum class Kind { ROUTE, PERIODIC }

    companion object {
        private const val PREFS_NAME = "fetch_diary"
        private const val KEY_ENTRIES = "entries"
        private const val DEFAULT_CAPACITY = 20

        private val EntryListSerializer =
            kotlinx.serialization.builtins.ListSerializer(Entry.serializer())
    }
}
