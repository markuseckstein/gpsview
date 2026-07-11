package de.eckstein.gpsview.ui

/**
 * Finer-grained than [PositionUiState.NotYetAsked] alone: distinguishes the plain first-ask from
 * the re-askable-denial rationale-card variant, even though both map to the same top-level
 * [PositionUiState] (data-model.md — "NotYetAsked (+ rationale card variant)").
 */
enum class PermissionState {
    NOT_YET_ASKED,
    DENIED_REASKABLE,
    PERMANENTLY_DENIED,
    GRANTED,
}
