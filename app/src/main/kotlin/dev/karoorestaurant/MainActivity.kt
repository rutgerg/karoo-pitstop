package dev.karoorestaurant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.karoorestaurant.data.poi.OpeningHours
import dev.karoorestaurant.data.poi.Poi
import dev.karoorestaurant.data.poi.PoiCategory
import dev.karoorestaurant.data.route.LatLng
import dev.karoorestaurant.ui.PoiCard
import dev.karoorestaurant.ui.RestaurantTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

private val DEFAULT_CENTER = LatLng(52.3676, 4.9041)

class MainActivity : ComponentActivity() {

    private val karoo by lazy { KarooClient(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RestaurantTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NearestPoiScreen(
                        karoo = karoo,
                        onCardTap = karoo::navigateTo,
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        karoo.close()
        super.onDestroy()
    }
}

@Composable
private fun NearestPoiScreen(karoo: KarooClient, onCardTap: (Poi) -> Unit) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var statusLine by remember { mutableStateOf<String?>(null) }
    var picks by remember { mutableStateOf<List<PoiNearby>>(emptyList()) }

    suspend fun loadPicks() {
        picks = withContext(Dispatchers.IO) { computePicks(karoo) }
    }

    LaunchedEffect(Unit) {
        val cached = withContext(Dispatchers.IO) { karoo.store().count() }
        if (cached == 0) {
            statusLine = "Cache empty — tap Fetch to load POIs."
        } else {
            statusLine = "$cached POIs cached."
            loadPicks()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.screen_title),
            style = MaterialTheme.typography.headlineSmall,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    scope.launch {
                        loading = true
                        statusLine = "Fetching…"
                        try {
                            val n = withContext(Dispatchers.IO) { karoo.refreshAround(DEFAULT_CENTER) }
                            statusLine = "Fetched $n POIs."
                            loadPicks()
                        } catch (t: Throwable) {
                            statusLine = "Fetch failed: ${t.message}"
                        } finally {
                            loading = false
                        }
                    }
                },
                enabled = !loading,
            ) { Text(if (loading) "Loading…" else "Fetch") }
        }

        statusLine?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

        picks.forEach { pn ->
            PoiCard(item = pn, onClick = { onCardTap(pn.poi) })
        }
    }
}

private fun computePicks(karoo: KarooClient): List<PoiNearby> {
    val now = LocalDateTime.now()
    val store = karoo.store()
    return PoiCategory.values().mapNotNull { category ->
        val candidates = store.nearest(DEFAULT_CENTER, category, maxMeters = 30_000.0, limit = 50)
        candidates.firstNotNullOfOrNull { (poi, dist) ->
            val status = OpeningHours.evaluate(poi.openingHoursTag, now)
            if (status is OpeningHours.Status.Closed) null else PoiNearby(poi, dist, status)
        }
    }
}
