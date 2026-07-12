# Quickstart: Validating Landscape & Inset-Safe Layout

This is a **validation / run guide**, not an implementation guide. It proves the feature end-to-end on a
device/emulator. Implementation detail belongs in `tasks.md`. All checks map to the spec's Success Criteria
(SC-00x) and the UI Layout Contract (`contracts/ui-layout.md`).

## Prerequisites

- Android Studio + JDK 17; an emulator or device on **Android 16 (API 36)** with the system navigation
  bar visible (test both gesture and 3-button nav). A device/emulator profile **with a display cutout /
  notch** for SC-006.
- Location enabled and permission granted so a live fix is available (this feature does not change the
  permission flow).

## Build & run

```bash
./gradlew :app:assembleDebug        # build
./gradlew :app:installDebug         # install on the connected device/emulator
./gradlew test                      # pure-core + ViewModel JVM tests MUST still pass unchanged
```

Launch the app and wait for a live fix (UTMREF hero populated).

## Validation scenarios

### V1 — No content hidden behind the navigation bar (SC-001, FR-002) — portrait

1. In portrait, view the readout collapsed, then drag the bottom sheet up to expand it fully.
2. **Expect**: the lowest readout row is fully visible above the navigation/gesture bar in both states —
   no clipping or overlap. The map is visible *behind* the bars (edge-to-edge look).

### V2 — Edge controls are tappable (SC-002, FR-003)

1. Tap each control that sits near a screen edge: the layer chips (Satellit / Flurstücke, top-leading),
   the zoom + and −, and the locate FAB (bottom-trailing), plus the bottom-most tappable readout row.
2. **Expect**: every control activates on the **first** tap in both portrait and landscape — no dead taps
   caused by system-bar overlap.

### V3 — Landscape side panel with map (SC-003, FR-006/FR-010) 

1. Rotate to landscape with a live fix.
2. **Expect**: the coordinate metadata is a fixed side panel on the leading (left) edge at ~40% width; the
   map fills the remaining ~60% and both are usable at once. The UTMREF hero grid reference renders **without
   truncation**. If the panel is taller than the window, scrolling the panel reaches every row.

### V4 — No feature regression across orientation (SC-004, FR-009/FR-011)

1. In landscape, confirm every value present in portrait is present: UTMREF hero, lat/lon (toggle
   Dezimal ⇄ DMS), Plus Code, both heights, horizontal accuracy, satellite ratio, and any acquiring/error
   state.
2. Tap a row to copy → toast appears and clipboard holds the value. Trigger Share → identical summary.
   Toggle Satellit and Flurstücke → layers change. Press locate → recenters and re-engages follow-me.
3. **Expect**: all behave identically to portrait.

### V5 — State preserved on rotation (SC-005, FR-012)

1. Engage follow-me, enable the Satellit layer, switch lat/lon to DMS, zoom the map in a few steps.
2. Rotate portrait → landscape → portrait, and (if available) enter split-screen and resize the window
   across the square threshold.
3. **Expect**: live position, the enabled layer, follow-me engagement, DMS mode, and map zoom/center are
   preserved; **no crash** on any rotation/resize.

### V6 — Notch / cutout safety (SC-006, FR-004)

1. On the notched profile, view the app in both orientations.
2. **Expect**: no readout text or control is obscured by the cutout or rounded corners; in landscape the
   leading-edge panel and controls sit inside the cutout inset.

### V7 — Window-driven arrangement, not device flag (FR-008)

1. On a landscape device, place the app in a **portrait-shaped** split-screen window.
2. **Expect**: it uses the **bottom-sheet** arrangement (chosen from window aspect, not device orientation).

## Done when

Every V1–V7 check passes in both orientations on an Android 16 device with the nav bar visible and on a
notched profile, and `./gradlew test` is green (no core regression).
