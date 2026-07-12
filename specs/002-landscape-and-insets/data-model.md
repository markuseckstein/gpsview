# Phase 1 Data Model: Landscape Orientation & System-Bar Safe Layout

**No new domain data entities.** Per the spec's Key Entities section, this feature is presentation-only:
the existing position/GNSS snapshot (`PositionSnapshot`, `PositionUiState`) and map state (`MapUiState`,
`LatLonMode`, follow-me) are unchanged. This document records the **derived UI layout state** the feature
introduces — none of it is persisted and none of it enters the `coordinates` core.

## Derived layout state (transient, UI-only)

| Concept | Type / source | Derivation | Notes |
|---------|---------------|------------|-------|
| `LayoutArrangement` | conceptual enum `{ BottomSheet, SidePanel }` — may be inlined as a boolean `isWide` | `maxWidth > maxHeight` from `BoxWithConstraints` in `MainScreen` | Not stored; recomputed on every recomposition/resize. Drives which branch renders (FR-006/FR-007/FR-008). |
| Safe-area insets | `WindowInsets.safeDrawing` (system bars ∪ display cutout) | Provided by the platform via Compose | Applied per-island to readout + controls; **not** to the map (FR-001–FR-005). |
| Panel width fraction | constant `≈0.40f` | Static split via `Row` weights (0.4 / 0.6) | Landscape only; map gets the complement (FR-010). |
| Landscape panel scroll offset | `rememberScrollState()` inside the panel | Compose-local | Lets all rows be reached when the panel is taller than the window (FR-009, short-landscape edge case). |

## Reused, unchanged state (for reference)

These are read by both arrangements from the existing `PositionViewModel`; the feature does not modify
their shape or lifecycle:

- `PositionUiState` (`Acquiring` / `Live` / `NotYetAsked` / `PermanentlyDenied` / `LocationOff`)
- `PositionSnapshot` (lat/lon, heights, accuracies, satellites) — rendered by `ReadoutSheetContent`
- `MapUiState` (`followMe`, `satelliteVisible`, `parcelsVisible`)
- `LatLonMode` (`DECIMAL` / `DMS`)
- `PermissionState`, `coarseOnlyBanner`

## Invariant

The set of readout values and interactions is **identical** across both arrangements because both render
the same `ReadoutSheetContent` and the same `MapContent`. There is no arrangement-specific data model, so
no value can exist in one orientation and not the other (guarantees SC-004 / FR-009 / FR-011 by construction).

## State transitions

The only "transition" is a recomposition when the window aspect crosses the square threshold
(`maxWidth` vs `maxHeight`), e.g. on rotation or multi-window resize:

```
BottomSheet  ⇄  SidePanel      (triggered purely by window aspect; no user action, no persisted flag)
```

All ViewModel-held state survives this transition (config-change-surviving ViewModel); map camera and
follow-me must be verified to survive (research R5). No state is created or destroyed by the transition
itself.
