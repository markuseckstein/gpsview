package de.eckstein.gpsview.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private data class LicenseEntry(val component: String, val license: String, val note: String? = null)

/** Über / Lizenzen — every component and data source, per contracts/map-services.md §Licenses. */
private val licenseEntries =
    listOf(
        LicenseEntry("MapLibre Native", "BSD-2-Clause"),
        LicenseEntry("maplibre-compose", "BSD-3-Clause"),
        LicenseEntry("mil.nga:mgrs, mil.nga:grid", "MIT", "NGA-authored"),
        LicenseEntry("com.google.openlocationcode", "Apache-2.0"),
        LicenseEntry("OpenStreetMap-Daten", "ODbL", "© OpenStreetMap contributors"),
        LicenseEntry("OpenFreeMap-Kacheln", "freier Dienst", "OpenFreeMap © OpenMapTiles Data from OpenStreetMap"),
        LicenseEntry(
            "Bayerische DOP20-Luftbilder",
            "CC BY 4.0",
            "Bayerische Vermessungsverwaltung – www.geodaten.bayern.de",
        ),
        LicenseEntry(
            "ALKIS-Parzellarkarte",
            "CC BY 4.0 (LDBV-bestätigt)",
            "Bayerische Vermessungsverwaltung – www.geodaten.bayern.de",
        ),
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Über / Lizenzen") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        }
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.padding(innerPadding)) {
            items(licenseEntries) { entry ->
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(text = entry.component, style = MaterialTheme.typography.titleSmall)
                    Text(text = entry.license, style = MaterialTheme.typography.bodyMedium)
                    entry.note?.let { Text(text = it, style = MaterialTheme.typography.bodySmall) }
                }
                HorizontalDivider()
            }
        }
    }
}
