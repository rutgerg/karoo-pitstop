package dev.karoorestaurant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.karoorestaurant.PoiNearby
import dev.karoorestaurant.data.poi.OpeningHours

@Composable
fun PoiCard(item: PoiNearby, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = item.poi.category.label,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = item.poi.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = formatDistance(item.distanceMeters),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = statusLabel(item.status),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private fun formatDistance(meters: Double): String =
    if (meters < 1000) "${meters.toInt()} m" else "%.1f km".format(meters / 1000.0)

private fun statusLabel(status: OpeningHours.Status): String = when (status) {
    is OpeningHours.Status.Open -> "open"
    is OpeningHours.Status.Unknown -> "hours unknown"
    OpeningHours.Status.Closed -> "closed"
}
