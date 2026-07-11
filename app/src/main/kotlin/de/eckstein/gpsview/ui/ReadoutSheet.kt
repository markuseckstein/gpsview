package de.eckstein.gpsview.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.eckstein.gpsview.coordinates.LatLonMode
import de.eckstein.gpsview.coordinates.PositionSnapshot
import de.eckstein.gpsview.coordinates.SatelliteCount
import de.eckstein.gpsview.coordinates.formatHeightDisplay
import de.eckstein.gpsview.coordinates.formatLatLonDisplay
import de.eckstein.gpsview.coordinates.latLonCopyPayload
import de.eckstein.gpsview.coordinates.plusCode
import de.eckstein.gpsview.coordinates.utmref

private val monospaceTabularFigures =
    androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.Monospace,
        fontFeatureSettings = "tnum",
    )

/**
 * Bottom-sheet readout content (SPEC.md §7). Peek section (UTMREF hero + both height cards) comes
 * first so a collapsed sheet with a small `sheetPeekHeight` shows exactly that; the expanded
 * section (lat/lon, Plus Code, metadata strip) follows. [onRowCopy] performs the actual clipboard
 * write and toast — the Composable acts directly, no ViewModel event channel (constitution/SPEC.md §6.5).
 */
@Composable
fun ReadoutSheetContent(
    state: PositionUiState,
    latLonMode: LatLonMode,
    onToggleLatLonMode: () -> Unit,
    onRowCopy: (label: String, value: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snapshot = (state as? PositionUiState.Live)?.snapshot
    val acquiringSatellites = (state as? PositionUiState.Acquiring)?.satellites

    Column(modifier = modifier.padding(16.dp)) {
        UtmrefHero(snapshot, onRowCopy)

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HeightCard(
                label = "Ellipsoidisch",
                heightM = snapshot?.ellipsoidalAltitudeM,
                accuracyM = snapshot?.verticalAccuracyM,
                onCopy = onRowCopy,
                modifier = Modifier.weight(1f),
            )
            HeightCard(
                label = "Über NHN",
                heightM = snapshot?.mslAltitudeM,
                accuracyM = snapshot?.mslAccuracyM,
                onCopy = onRowCopy,
                modifier = Modifier.weight(1f),
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        LatLonRow(snapshot, latLonMode, onToggleLatLonMode, onRowCopy)
        PlusCodeRow(snapshot, onRowCopy)
        MetadataStrip(snapshot, acquiringSatellites)
    }
}

@Composable
private fun UtmrefHero(snapshot: PositionSnapshot?, onCopy: (String, String) -> Unit) {
    val value = snapshot?.let { utmref(it.latLon) } ?: "— —— ————— ——————"
    Column(
        modifier =
            Modifier.fillMaxWidth().clickable(enabled = snapshot != null) {
                if (snapshot != null) onCopy("UTMREF", utmref(snapshot.latLon))
            }
    ) {
        Text(
            text = value,
            style = monospaceTabularFigures,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
        )
        if (snapshot == null) {
            Text(text = "Suche Satelliten…", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun HeightCard(
    label: String,
    heightM: Double?,
    accuracyM: Float?,
    onCopy: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier =
            modifier.clickable(enabled = heightM != null) {
                if (heightM != null) onCopy(label, formatHeightDisplay(heightM))
            },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = label, style = MaterialTheme.typography.labelMedium)
            Text(
                text = formatHeightDisplay(heightM),
                style = monospaceTabularFigures,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "±${accuracyM?.let { kotlin.math.round(it).toInt() } ?: "—"} m",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun LatLonRow(
    snapshot: PositionSnapshot?,
    mode: LatLonMode,
    onToggleMode: () -> Unit,
    onCopy: (String, String) -> Unit,
) {
    val display = snapshot?.let { formatLatLonDisplay(it.latLon, mode) } ?: "— / —"
    Row(
        modifier =
            Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable(enabled = snapshot != null) {
                if (snapshot != null) onCopy("Breite / Länge", latLonCopyPayload(snapshot.latLon, mode))
            },
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(text = "Breite / Länge", style = MaterialTheme.typography.labelMedium)
            Text(text = display, style = monospaceTabularFigures)
        }
        Text(
            text = if (mode == LatLonMode.DECIMAL) "DMS" else "Dezimal",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.clickable { onToggleMode() },
        )
    }
}

@Composable
private fun PlusCodeRow(snapshot: PositionSnapshot?, onCopy: (String, String) -> Unit) {
    val value = snapshot?.let { plusCode(it.latLon) } ?: "—"
    Column(
        modifier =
            Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable(enabled = snapshot != null) {
                if (snapshot != null) onCopy("Plus Code", plusCode(snapshot.latLon))
            }
    ) {
        Text(text = "Plus Code", style = MaterialTheme.typography.labelMedium)
        Text(text = value, style = monospaceTabularFigures)
    }
}

@Composable
private fun MetadataStrip(snapshot: PositionSnapshot?, acquiringSatellites: SatelliteCount?) {
    val satellites = snapshot?.satellites ?: acquiringSatellites
    val accuracyText = snapshot?.horizontalAccuracyM?.let { "±${kotlin.math.round(it).toInt()} m" } ?: "—"
    val ratioText = satellites?.let { "${it.used} / ${it.visible}" } ?: "0 / 0"
    val fraction =
        satellites?.let { if (it.visible > 0) it.used.toFloat() / it.visible else 0f } ?: 0f

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = accuracyText, style = MaterialTheme.typography.bodyMedium)
        Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
            Text(text = ratioText, style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
