package de.eckstein.gpsview.ui

import de.eckstein.gpsview.coordinates.PositionSnapshot
import de.eckstein.gpsview.coordinates.SatelliteCount

/**
 * Exactly one of these applies at a time (data-model.md, SPEC.md §5.2). Coarse-only permission
 * grant is deliberately not modeled here — the app functions normally on coarse fixes and the
 * banner is a separate UI-state flag (data-model.md).
 */
sealed interface PositionUiState {
    /** Permission not yet requested. */
    data object NotYetAsked : PositionUiState

    /** Denied with "don't ask again"; only a settings deep-link can recover. */
    data object PermanentlyDenied : PositionUiState

    /** System location setting is off. */
    data object LocationOff : PositionUiState

    /**
     * Permission granted, no fix yet. [satellites] carries the live `0/n` ratio when GnssStatus
     * has reported before the first fix arrives.
     */
    data class Acquiring(val satellites: SatelliteCount?) : PositionUiState

    /** A live fix is available. */
    data class Live(val snapshot: PositionSnapshot) : PositionUiState
}
