package dev.karoorestaurant

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.karoorestaurant.data.poi.OpeningHours
import dev.karoorestaurant.data.poi.Poi
import dev.karoorestaurant.data.poi.PoiCategory
import dev.karoorestaurant.data.route.LatLng
import dev.karoorestaurant.ui.PoiCard
import dev.karoorestaurant.ui.RestaurantTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

class MainActivity : ComponentActivity() {

    private val app: KarooRestaurantApp
        get() = application as KarooRestaurantApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RestaurantTheme {
                MainScaffold(
                    karoo = app.karoo,
                    watcher = app.routeWatcher,
                    onCardTap = app.karoo::navigateTo,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(
    karoo: KarooClient,
    watcher: RouteWatcher,
    onCardTap: (Poi) -> Unit,
) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(
                        onClick = { context.startActivity(Intent(context, SettingsActivity::class.java)) },
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings_open),
                        )
                    }
                },
            )
        },
    ) { padding ->
        NearestPoiScreen(
            karoo = karoo,
            watcher = watcher,
            onCardTap = onCardTap,
            modifier = Modifier.padding(padding),
        )
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun NearestPoiScreen(
    karoo: KarooClient,
    watcher: RouteWatcher,
    onCardTap: (Poi) -> Unit,
    modifier: Modifier = Modifier,
) {
    val routeState by watcher.state.collectAsState()
    val sampledLocation = remember(karoo) { karoo.locationFlow.sample(LOCATION_SAMPLE_MS) }
    val location by sampledLocation.collectAsState(initial = null)

    val picks by produceState<List<PoiNearby>>(initialValue = emptyList(), routeState, location) {
        value = if (routeState is RouteFetchState.Cached && location != null) {
            withContext(Dispatchers.IO) { computeNearbyPicks(karoo, location!!) }
        } else {
            emptyList()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.screen_title),
            style = MaterialTheme.typography.headlineSmall,
        )

        when (val state = routeState) {
            RouteFetchState.Idle -> EmptyState()
            is RouteFetchState.Fetching -> FetchingState(state.routeName)
            is RouteFetchState.Cached -> CachedState(state, location, picks, onCardTap)
            is RouteFetchState.Error -> ErrorState(state.message)
        }
    }
}

@Composable
private fun EmptyState() {
    Text(
        stringResource(R.string.state_idle),
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun FetchingState(routeName: String) {
    Box(modifier = Modifier.fillMaxSize().padding(top = 24.dp), contentAlignment = Alignment.TopCenter) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp))
            Text(
                stringResource(R.string.state_fetching, routeName),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun CachedState(
    state: RouteFetchState.Cached,
    location: LatLng?,
    picks: List<PoiNearby>,
    onCardTap: (Poi) -> Unit,
) {
    Text(
        stringResource(R.string.state_cached, state.poiCount, state.routeName),
        style = MaterialTheme.typography.bodySmall,
    )
    if (location == null) {
        Text(
            stringResource(R.string.state_no_location),
            style = MaterialTheme.typography.bodyMedium,
        )
    } else {
        picks.forEach { pn ->
            PoiCard(item = pn, onClick = { onCardTap(pn.poi) })
        }
    }
}

@Composable
private fun ErrorState(message: String) {
    Text(
        stringResource(R.string.state_error, message),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
    )
}

private const val LOCATION_SAMPLE_MS = 30_000L
