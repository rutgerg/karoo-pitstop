package dev.karoorestaurant

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.karoorestaurant.data.poi.OpeningHours
import dev.karoorestaurant.data.poi.Poi
import dev.karoorestaurant.data.poi.PoiCategory
import dev.karoorestaurant.ui.RestaurantTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

class BrowsePoisActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val raw = intent.getStringExtra(EXTRA_CATEGORY)
        val category = runCatching { raw?.let { PoiCategory.valueOf(it) } }.getOrNull()
        Log.i(TAG, "BrowsePoisActivity onCreate category=$category raw=$raw")
        if (category == null) {
            finish()
            return
        }
        val karoo = (application as KarooRestaurantApp).karoo
        setContent {
            RestaurantTheme {
                BrowseScreen(
                    category = category,
                    karoo = karoo,
                    onPoiTap = { poi ->
                        karoo.navigateTo(poi)
                        finish()
                    },
                    onBack = { finish() },
                )
            }
        }
    }

    companion object {
        const val EXTRA_CATEGORY = "category"
        private const val TAG = "BrowsePoisActivity"
    }
}

private data class BrowseRow(
    val poi: Poi,
    val distanceMeters: Double,
    val status: OpeningHours.Status,
    val isFavorite: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowseScreen(
    category: PoiCategory,
    karoo: KarooClient,
    onPoiTap: (Poi) -> Unit,
    onBack: () -> Unit,
) {
    var refreshKey by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val rows by produceState<List<BrowseRow>?>(initialValue = null, key1 = category, key2 = refreshKey) {
        value = withContext(Dispatchers.IO) {
            val rider = karoo.locationFlow.first()
            val nowLdt = LocalDateTime.now()
            karoo.store()
                .nearest(rider.point, category, maxMeters = 30_000.0, limit = 10)
                .mapNotNull { hit ->
                    val poi = hit.poi
                    val status = OpeningHours.evaluate(poi.openingHoursTag, nowLdt)
                    if (status is OpeningHours.Status.Closed) null
                    else BrowseRow(poi, hit.distanceMeters, status, hit.isFavorite)
                }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(category.label) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val list = rows) {
                null -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                else -> if (list.isEmpty()) {
                    Text(
                        text = stringResource(R.string.browse_empty, category.label),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(list) { row ->
                            PoiRow(
                                row = row,
                                onClick = { onPoiTap(row.poi) },
                                onLongClick = {
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            karoo.store().setFavorite(row.poi, !row.isFavorite)
                                        }
                                        refreshKey++
                                    }
                                },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun PoiRow(row: BrowseRow, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.poi.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
            )
            Text(
                text = statusLabel(row.status),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = if (row.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
            contentDescription = stringResource(
                if (row.isFavorite) R.string.poi_favorite else R.string.poi_not_favorite,
            ),
            tint = if (row.isFavorite) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Text(
            text = formatDistance(row.distanceMeters),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun statusLabel(status: OpeningHours.Status): String = when (status) {
    OpeningHours.Status.Open -> stringResource(R.string.hours_open_prefix)
    OpeningHours.Status.Closed -> stringResource(R.string.hours_closed)
    is OpeningHours.Status.Unknown -> stringResource(R.string.hours_unknown)
}

private fun formatDistance(meters: Double): String =
    if (meters < 1000.0) "${meters.toInt()} m" else "%.1f km".format(meters / 1000.0)
