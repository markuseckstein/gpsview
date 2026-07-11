package de.eckstein.gpsview.ui

/**
 * Map-specific UI state (data-model.md §MapUiState) — changes for different reasons at different
 * rates than [PositionUiState], hence its own `StateFlow`. Camera position itself is owned by the
 * Composable that hosts the MapLibre map (`MapContent.kt`), driven by [followMe]; it is not a
 * plain value type suited to living on the ViewModel.
 */
data class MapUiState(
    val followMe: Boolean = true,
    val satelliteVisible: Boolean = false,
    val parcelsVisible: Boolean = false,
)
