package dev.karoorestaurant.telemetry

import android.util.Log
import dev.karoorestaurant.BuildConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class HeartbeatSender(
    private val supabaseUrl: String = BuildConfig.SUPABASE_URL,
    private val anonKey: String = BuildConfig.SUPABASE_ANON_KEY,
    private val http: OkHttpClient = defaultHttp(),
) {

    fun send(payload: HeartbeatPayload): Boolean {
        val body = Json.encodeToString(HeartbeatPayload.serializer(), payload)
            .toRequestBody(JSON_MEDIA)
        val request = Request.Builder()
            .url("$supabaseUrl/rest/v1/heartbeats")
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $anonKey")
            .header("Prefer", "return=minimal")
            .post(body)
            .build()
        return try {
            http.newCall(request).execute().use { response ->
                // 201 = inserted; 409 = duplicate (install_id, day) — already submitted, treat as success
                val ok = response.code == 201 || response.code == 409
                if (!ok) Log.w(TAG, "heartbeat failed status=${response.code}")
                ok
            }
        } catch (e: IOException) {
            Log.w(TAG, "heartbeat IO error: ${e.message}")
            false
        }
    }

    companion object {
        private val JSON_MEDIA = "application/json".toMediaType()
        private const val TAG = "HeartbeatSender"

        private fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}

@Serializable
data class HeartbeatPayload(
    val install_id: String,
    val day: String,
    val tile_renders: Int,
    val prefetch_count: Int,
    val app_version: String,
)
