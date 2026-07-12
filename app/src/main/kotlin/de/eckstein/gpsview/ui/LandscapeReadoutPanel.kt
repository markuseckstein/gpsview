package de.eckstein.gpsview.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import de.eckstein.gpsview.coordinates.LatLonMode

/**
 * Fixed leading-edge landscape panel (contracts/ui-layout.md C3): hosts the same
 * [ReadoutSheetContent] as the portrait bottom sheet inside a vertical scroll, so every row stays
 * reachable when the panel is taller than the available window height (FR-009).
 */
@Composable
fun LandscapeReadoutPanel(
    state: PositionUiState,
    latLonMode: LatLonMode,
    coarseOnlyBanner: Boolean,
    onToggleLatLonMode: () -> Unit,
    onRequestPreciseLocation: () -> Unit,
    onRowCopy: (label: String, value: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxHeight()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState()),
    ) {
        if (coarseOnlyBanner) {
            CoarseOnlyBanner(onRequestPrecise = onRequestPreciseLocation)
        }
        ReadoutSheetContent(
            state = state,
            latLonMode = latLonMode,
            onToggleLatLonMode = onToggleLatLonMode,
            onRowCopy = onRowCopy,
        )
    }
}
