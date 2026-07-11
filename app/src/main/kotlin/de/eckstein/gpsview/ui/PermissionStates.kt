package de.eckstein.gpsview.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Re-askable denial (SPEC.md §7.1): rationale card + retry, shown over the map. First launch
 * itself shows no pre-rationale card — the system prompt fires immediately (`MainActivity`).
 */
@Composable
fun PermissionRationaleCard(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Standortzugriff benötigt",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text =
                        "GPSView zeigt deine aktuelle Position als UTMREF-Kartenkoordinate und auf der Karte. " +
                            "Dafür wird die Standortberechtigung benötigt.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                    Text("Erneut versuchen")
                }
            }
        }
    }
}

/** `PermanentlyDenied` (SPEC.md §7.1): full screen, deep-link to app settings. */
@Composable
fun PermanentlyDeniedScreen(onOpenAppSettings: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Standortberechtigung dauerhaft abgelehnt",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text =
                        "Um GPSView zu nutzen, aktiviere die Standortberechtigung in den App-Einstellungen.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = onOpenAppSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("App-Einstellungen öffnen")
                }
            }
        }
    }
}

/** `LocationOff` (SPEC.md §7.1): card over a dimmed map, deep-link to location settings. */
@Composable
fun LocationOffCard(onOpenLocationSettings: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "Standort ist deaktiviert", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Bitte aktiviere den Standort in den Systemeinstellungen, um GPSView zu nutzen.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onOpenLocationSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("Standorteinstellungen öffnen")
                }
            }
        }
    }
}

/** Coarse-only grant banner (SPEC.md §7.1) — non-blocking, function normally underneath it. */
@Composable
fun CoarseOnlyBanner(onRequestPrecise: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth().padding(12.dp)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "Genauer Standort empfohlen", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onRequestPrecise) { Text("Genauen Standort anfragen") }
        }
    }
}
