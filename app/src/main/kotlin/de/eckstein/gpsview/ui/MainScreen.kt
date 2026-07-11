package de.eckstein.gpsview.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.eckstein.gpsview.coordinates.UtmrefPrecision
import de.eckstein.gpsview.coordinates.shareBlock

/** Layout A „Karte im Fokus" (SPEC.md §7): full-bleed map behind a two-zone bottom sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: PositionViewModel,
    onRetryPermissionRequest: () -> Unit,
    onRequestPreciseLocation: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle(initialValue = PositionUiState.Acquiring(null))
    val permissionState by viewModel.permissionState.collectAsStateWithLifecycle()
    val coarseOnlyBanner by viewModel.coarseOnlyBanner.collectAsStateWithLifecycle()
    val latLonMode by viewModel.latLonMode.collectAsStateWithLifecycle()
    val mapUiState by viewModel.mapUiState.collectAsStateWithLifecycle()
    val scaffoldState = rememberBottomSheetScaffoldState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var showAbout by remember { mutableStateOf(false) }

    if (showAbout) {
        AboutScreen(onBack = { showAbout = false })
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetPeekHeight = 232.dp,
            topBar = {
                TopAppBar(
                    title = { Text("GPSView") },
                    actions = {
                        val liveSnapshot = (state as? PositionUiState.Live)?.snapshot
                        IconButton(
                            enabled = liveSnapshot != null,
                            onClick = {
                                val snapshot = liveSnapshot ?: return@IconButton
                                val sendIntent =
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, shareBlock(snapshot, UtmrefPrecision.M1))
                                    }
                                context.startActivity(Intent.createChooser(sendIntent, "Standort teilen"))
                            },
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Teilen")
                        }
                        IconButton(onClick = { showAbout = true }) {
                            Icon(Icons.Default.Info, contentDescription = "Über GPSView")
                        }
                        AssistChip(
                            onClick = {},
                            label = { Text(statusChipText(state)) },
                            modifier = Modifier.padding(end = 12.dp),
                        )
                    },
                )
            },
            sheetContent = {
                Column {
                    if (coarseOnlyBanner) {
                        CoarseOnlyBanner(onRequestPrecise = onRequestPreciseLocation)
                    }
                    ReadoutSheetContent(
                        state = state,
                        latLonMode = latLonMode,
                        onToggleLatLonMode = viewModel::toggleLatLonMode,
                        onRowCopy = { label, value ->
                            clipboardManager.setText(AnnotatedString(value))
                            Toast.makeText(context, "$label kopiert", Toast.LENGTH_SHORT).show()
                        },
                    )
                }
            },
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                MapContent(
                    positionState = state,
                    mapUiState = mapUiState,
                    onFollowMeChanged = viewModel::setFollowMe,
                    onToggleSatelliteLayer = viewModel::toggleSatelliteLayer,
                    onToggleParcelsLayer = viewModel::toggleParcelsLayer,
                    modifier = Modifier.fillMaxSize(),
                )

                when {
                    state is PositionUiState.LocationOff ->
                        LocationOffCard(
                            onOpenLocationSettings = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
                            modifier = Modifier.align(Alignment.Center),
                        )
                    state is PositionUiState.PermanentlyDenied ->
                        PermanentlyDeniedScreen(
                            onOpenAppSettings = { context.startActivity(appSettingsIntent(context.packageName)) },
                            modifier = Modifier.align(Alignment.Center),
                        )
                    state is PositionUiState.NotYetAsked && permissionState == PermissionState.DENIED_REASKABLE ->
                        PermissionRationaleCard(
                            onRetry = onRetryPermissionRequest,
                            modifier = Modifier.align(Alignment.Center),
                        )
                }
            }
        }
    }
}

private fun appSettingsIntent(packageName: String): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
    }

private fun statusChipText(state: PositionUiState): String =
    when (state) {
        is PositionUiState.Live -> "Live"
        is PositionUiState.Acquiring -> "Kein Fix"
        PositionUiState.NotYetAsked -> "Keine Berechtigung"
        PositionUiState.PermanentlyDenied -> "Keine Berechtigung"
        PositionUiState.LocationOff -> "Standort aus"
    }
