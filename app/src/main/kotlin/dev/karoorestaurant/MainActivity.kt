package dev.karoorestaurant

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.karoorestaurant.settings.SettingsRepository
import dev.karoorestaurant.ui.RestaurantTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val app: KarooRestaurantApp
        get() = application as KarooRestaurantApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RestaurantTheme {
                SettingsScreen(
                    repository = app.settings,
                    onResetPois = { app.karoo.clearPoiCache() },
                    onBack = { finish() },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    repository: SettingsRepository,
    onResetPois: () -> Unit,
    onBack: () -> Unit,
) {
    val telemetryEnabled by repository.telemetryEnabled.collectAsState()
    val showClosedPois by repository.showClosedPois.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var showResetConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.app_name)) })
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TelemetryRow(
                    checked = telemetryEnabled,
                    onCheckedChange = { newValue ->
                        coroutineScope.launch { repository.setTelemetryEnabled(newValue) }
                    },
                )
                ShowClosedPoisRow(
                    checked = showClosedPois,
                    onCheckedChange = { newValue ->
                        coroutineScope.launch { repository.setShowClosedPois(newValue) }
                    },
                )
                if (BuildConfig.DEBUG) {
                    ResetPoisRow(onClick = { showResetConfirm = true })
                }
            }
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                )
            }
            val versionLabel = if (BuildConfig.DEBUG) {
                "${BuildConfig.VERSION_NAME}-${stringResource(R.string.git_sha)}"
            } else {
                BuildConfig.VERSION_NAME
            }
            Text(
                stringResource(R.string.settings_version, versionLabel),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            )
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(stringResource(R.string.settings_reset_pois_confirm_title)) },
            text = { Text(stringResource(R.string.settings_reset_pois_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showResetConfirm = false
                    coroutineScope.launch {
                        withContext(Dispatchers.IO) { onResetPois() }
                        Toast.makeText(
                            context,
                            context.getString(R.string.settings_reset_pois_done),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }) {
                    Text(stringResource(R.string.settings_reset_pois_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun ResetPoisRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.settings_reset_pois_title),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                stringResource(R.string.settings_reset_pois_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = onClick) {
            Text(stringResource(R.string.settings_reset_pois_button))
        }
    }
}

@Composable
private fun TelemetryRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.settings_telemetry_title),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                stringResource(R.string.settings_telemetry_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ShowClosedPoisRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.settings_show_closed_pois_title),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                stringResource(R.string.settings_show_closed_pois_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
