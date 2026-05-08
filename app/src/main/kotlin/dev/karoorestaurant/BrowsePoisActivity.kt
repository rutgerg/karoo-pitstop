package dev.karoorestaurant

import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.TextView
import androidx.activity.ComponentActivity
import dev.karoorestaurant.data.poi.PoiCategory
import java.time.Instant

/**
 * Spike for issue #63: prove that a tile-tap PendingIntent.getActivity actually
 * launches a third-party Activity inside the Karoo Pages flow. Renders the
 * passed-in category and a launch timestamp; nothing else.
 */
class BrowsePoisActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val categoryName = intent.getStringExtra(EXTRA_CATEGORY)
        val resolved = runCatching { categoryName?.let { PoiCategory.valueOf(it) } }.getOrNull()
        Log.i(TAG, "BrowsePoisActivity onCreate category=$resolved raw=$categoryName")
        val view = TextView(this).apply {
            text = "Browse: ${resolved?.name ?: "(unknown)"}\n${Instant.now()}"
            textSize = 22f
            gravity = Gravity.CENTER
        }
        setContentView(view)
    }

    companion object {
        const val EXTRA_CATEGORY = "category"
        private const val TAG = "BrowsePoisActivity"
    }
}
