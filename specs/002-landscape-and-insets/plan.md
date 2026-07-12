# Implementation Plan: Landscape Orientation & System-Bar Safe Layout

**Branch**: `002-landscape-and-insets` | **Date**: 2026-07-12 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-landscape-and-insets/spec.md`

## Summary

Reshape GPSView's single screen ("Layout A – Karte im Fokus") for two presentation concerns without touching any coordinate math, GNSS derivation, share/copy payloads, permission flow, or the foreground-only location lifecycle:

1. **Inset safety (P1)** — render the map full-bleed behind the system bars while constraining every readout text row and interactive control to the safe (inset-respecting) area, so nothing is clipped by the navigation/gesture bar (notably on Android 16 / edge-to-edge-enforced targetSdk 36) and no control near a screen edge loses its touch target.
2. **Landscape arrangement (P2)** — when the available window is wider than tall, present the readout as a fixed, always-visible side panel (~40% width, internal vertical scroll) on the leading edge with the map filling the remaining ~60%; keep the existing bottom-sheet arrangement when the window is taller than wide.

Technical approach: enable edge-to-edge in `MainActivity`, make the theme bars transparent, and choose the arrangement in `MainScreen` from the current window aspect (width vs. height) — not a device-orientation flag — so split-screen/multi-window/foldable windows are handled correctly. Both arrangements reuse the **same** `ReadoutSheetContent` and the same `MapContent`, so the readout values and every interaction are identical across orientations by construction.

## Technical Context

**Language/Version**: Kotlin 2.3.21 (JVM toolchain 17)

**Primary Dependencies**: Jetpack Compose (BOM 2026.06.01), Material 3, `maplibre-compose` 0.13.0 + MapLibre Android SDK 13.3.1, play-services-location. Manual constructor injection (no DI framework). No new runtime dependency is required — arrangement selection uses Compose's built-in `BoxWithConstraints`; inset handling uses `androidx.activity.enableEdgeToEdge` (already on the classpath via `activity-compose` 1.12.4) and Compose `WindowInsets`.

**Storage**: N/A (no persisted preference for orientation or panel side — arrangement is derived from the live window; consistent with the app's no-persisted-preferences stance).

**Testing**: JUnit on the JVM for the pure `coordinates` core and the ViewModel with fakes (unchanged). This feature is presentation-only; it adds no pure-core logic to unit-test. Verification is the on-device / emulator smoke walkthrough in `quickstart.md` (rotation, insets, tap targets, notch).

**Target Platform**: Android, minSdk 34, targetSdk 36, compileSdk 36 (edge-to-edge enforced by the platform at targetSdk ≥ 35).

**Project Type**: Mobile app — single Gradle `:app` module, one Activity, one ViewModel (package-layered: `coordinates` / `data` / `ui`).

**Performance Goals**: Maintain smooth map interaction and rotation (~60 fps); rotation must not drop the fix or reset map/follow-me/display-mode state.

**Constraints**: No Android imports may enter `coordinates`; location must remain strictly foreground-only; all changes confined to the `ui` package plus the Activity, theme XML, and (if edge-to-edge needs it) the manifest. No change to coordinate/GNSS/share/permission code.

**Scale/Scope**: A handful of `ui`-layer files (`MainActivity`, `MainScreen`, `MapContent`, theme XML) and one new landscape-panel composable path; no new screens, values, or navigation.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Assessment |
|-----------|------------|
| **I. Pure Coordinate Core (Testability Seam)** — NON-NEGOTIABLE | ✅ PASS. Feature touches only the `ui` package + Activity/theme. No change to `coordinates`; no Android import added there; the `LocationSource`/`GnssSource` boundary is untouched. FR-013 explicitly forbids altering computation. |
| **II. Strictly Foreground-Only Location** — NON-NEGOTIABLE | ✅ PASS. No change to location registration or lifecycle. `collectAsStateWithLifecycle` collection stays as-is; no service, no background permission, no batching introduced. Rotation is a config change handled by Compose state, not a new location path. |
| **III. On-Device Coordinate Truth** | ✅ PASS. Readouts and their dashed-absence semantics are reused verbatim (same `ReadoutSheetContent`); no network dependency added. Full-bleed map is a visual change to the existing basemap only. |
| **IV. German-First, BOS-Faithful Presentation** | ✅ PASS. No user-facing strings or number formats change; UTMREF hero and all rows are rendered by the existing composables. The panel is sized so the UTMREF hero never truncates (FR-010). |
| **V. Radical Simplicity & Deliberate Dependencies** | ✅ PASS. No new dependency, module, or DI. Arrangement chosen with built-in `BoxWithConstraints`; insets via APIs already on the classpath. Reuses existing composables rather than forking readout logic. |
| **VI. Data Provenance & Attribution Compliance** | ✅ PASS. Bavarian attribution overlay is preserved and must stay within the safe area in both arrangements (folded into FR-001/FR-005 verification); tiles remain unmodified. |

**Result: PASS — no violations, Complexity Tracking not required.**

## Project Structure

### Documentation (this feature)

```text
specs/002-landscape-and-insets/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output (no new entities — records the layout state model)
├── quickstart.md        # Phase 1 output (on-device validation walkthrough)
├── contracts/
│   └── ui-layout.md     # Phase 1 output (arrangement + inset UI contract)
├── checklists/
│   └── requirements.md  # pre-existing
└── tasks.md             # Phase 2 output (/speckit-tasks — NOT created here)
```

### Source Code (repository root)

```text
app/src/main/
├── AndroidManifest.xml                       # (maybe) confirm no legacy inset opt-out; no orientation lock
├── res/values/themes.xml                     # transparent system-bar background for edge-to-edge
└── kotlin/de/eckstein/gpsview/
    ├── ui/
    │   ├── MainActivity.kt                    # add enableEdgeToEdge()
    │   ├── MainScreen.kt                      # choose arrangement via BoxWithConstraints;
    │   │                                      #   apply safe-area insets to sheet/panel + controls
    │   ├── MapContent.kt                      # full-bleed map; inset the floating chips/FABs/attribution
    │   ├── ReadoutSheet.kt                    # reused unchanged (or minimal: scroll host for panel)
    │   └── (new) LandscapeReadoutPanel path   # side-panel wrapper reusing ReadoutSheetContent
    └── coordinates/  data/                    # UNCHANGED (constitution I/II, FR-013)

app/src/test/                                  # UNCHANGED — pure-core + ViewModel tests still valid
```

**Structure Decision**: Single `:app` module, changes confined to the `ui` package plus `MainActivity`, `themes.xml`, and possibly `AndroidManifest.xml`. The landscape side panel is a thin composable that hosts the **existing** `ReadoutSheetContent` inside a vertically scrollable, inset-aware container; portrait keeps `BottomSheetScaffold`. `MainScreen` becomes the single arrangement switch based on window aspect. No new module or package is introduced (Principle V).

## Complexity Tracking

> No Constitution Check violations — this section is intentionally empty.
