package dev.karoorestaurant.telemetry

import android.content.SharedPreferences
import java.util.UUID

internal const val KEY_INSTALL_ID = "install_id"

internal fun installId(prefs: SharedPreferences): String {
    prefs.getString(KEY_INSTALL_ID, null)?.let { return it }
    val fresh = UUID.randomUUID().toString()
    prefs.edit().putString(KEY_INSTALL_ID, fresh).apply()
    return fresh
}
