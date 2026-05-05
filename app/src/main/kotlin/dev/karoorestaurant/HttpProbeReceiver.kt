package dev.karoorestaurant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

class HttpProbeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val isDebug = (app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!isDebug) {
            Log.w(TAG, "ignoring HTTP_PROBE on non-debug build")
            return
        }
        val url = intent.getStringExtra(EXTRA_URL) ?: DEFAULT_URL
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "karoo-pitstop-probe/0.2")
                    .header("Accept", "*/*")
                    .get()
                    .build()
                var status = -1
                var size = 0
                var bodyHead = ""
                val ms = measureTimeMillis {
                    client.newCall(request).execute().use { resp ->
                        status = resp.code
                        val bytes = resp.body?.bytes() ?: ByteArray(0)
                        size = bytes.size
                        bodyHead = String(bytes, Charsets.UTF_8).take(200)
                    }
                }
                Log.i(TAG, "HTTP_PROBE $url -> $status ${size}B in ${ms}ms head=${bodyHead.replace("\n", " ")}")
            } catch (t: Throwable) {
                Log.e(TAG, "HTTP_PROBE failed: ${t.javaClass.simpleName}: ${t.message}", t)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION = "dev.karoorestaurant.HTTP_PROBE"
        const val EXTRA_URL = "url"
        private const val DEFAULT_URL = "https://overpass-api.de/api/status"
        private const val TAG = "HttpProbeRcvr"
    }
}
