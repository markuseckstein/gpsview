# Tasks: GPSView — Live Location Viewer

**Input**: Design documents from `/specs/001-gpsview-android-app/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: INCLUDED for the pure `coordinates` core and ViewModel merge — the constitution ("Development Workflow & Quality Gates") mandates test-first with binding fixtures there. No UI test tasks (not requested; on-device validation scenarios from quickstart.md cover the UI empirically).

**Organization**: Tasks are grouped by user story (spec.md priorities P1–P5) so each story is an independently testable increment. Package root abbreviated below: `app/src/main/kotlin/de/eckstein/gpsview` = `MAIN`, `app/src/test/kotlin/de/eckstein/gpsview` = `TEST`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: US1–US5 per spec.md; setup/foundational/polish tasks carry no story label

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Buildable, installable empty app with all pinned dependencies resolving

- [ ] T001 Create Gradle scaffold at repo root: `settings.gradle.kts` (`:app`), root `build.gradle.kts`, `gradle/libs.versions.toml` version catalog with every pin from plan.md Technical Context (maplibre 13.3.1, maplibre-compose 0.13.0, mil.nga:mgrs 2.1.3 + grid 1.1.2, openlocationcode 1.0.4, play-services-location, lifecycle ≥ 2.8.0, Compose BOM), and the Gradle wrapper
- [ ] T002 Create `app/build.gradle.kts` (Kotlin + Compose, JDK 17 toolchain, minSdk 34, target latest, applicationId `de.eckstein.gpsview`) and `app/src/main/AndroidManifest.xml` with exactly `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION`, `INTERNET` — **never** `ACCESS_BACKGROUND_LOCATION` (constitution II)
- [ ] T003 Create package skeleton `MAIN/coordinates/`, `MAIN/data/`, `MAIN/ui/` with a minimal `MAIN/ui/MainActivity.kt` (empty Compose scaffold, German app label „GPSView") so the app builds and launches
- [ ] T004 Verify `./gradlew assembleDebug` and `./gradlew testDebugUnitTest` succeed — this is empirical gate **E2** (mil.nga:mgrs resolves under current AGP); record the outcome in specs/001-gpsview-android-app/research.md (E-checks table)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Position pipeline every story consumes — pure types, source seams, ViewModel merge, lifecycle-scoped collection

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [ ] T005 [P] Create pure value types `LatLon`, `SatelliteCount`, `PositionSnapshot` (all nullable-field guards and invariants per data-model.md — raw sensor truth only, zero Android imports) in `MAIN/coordinates/PositionSnapshot.kt`
- [ ] T006 [P] Create sealed `PositionUiState` (NotYetAsked / PermanentlyDenied / LocationOff / Acquiring / Live) per data-model.md in `MAIN/ui/PositionUiState.kt`
- [ ] T007 [P] Create `LocationSource` and `GnssSource` interfaces (cold-flow contracts per contracts/location-sources.md) in `MAIN/data/LocationSource.kt` and `MAIN/data/GnssSource.kt`
- [ ] T008 Implement FLP-backed `LocationSource` in `MAIN/data/FusedLocationSource.kt`: `callbackFlow`, `PRIORITY_HIGH_ACCURACY`, `setIntervalMillis(1000)`, `setMaxUpdateDelayMillis(0)`, `setWaitForAccurateLocation(true)`, `awaitClose { removeLocationUpdates }`; MSL enrichment in-flow via single app-lifetime `AltitudeConverter` on `Dispatchers.IO` with `IOException`/`IllegalArgumentException` → null (SPEC.md §4.1–4.2)
- [ ] T009 Implement GnssStatus-backed `GnssSource` in `MAIN/data/GnssStatusSource.kt`: `registerGnssStatusCallback(Executor, Callback)` in `callbackFlow`, used = count of `usedInFix(i)`, unregister in `awaitClose`; never location extras (SPEC.md §4.3)
- [ ] T010 [P] Create `FakeLocationSource`/`FakeGnssSource` backed by `MutableSharedFlow` in `TEST/FakeSources.kt`
- [ ] T011 Write ViewModel merge tests (write FIRST, must fail): fix-without-satellites → `Live(satellites=null)`, no-fix → `Acquiring(null)`, satellite-only → `Acquiring(satellites)` carrying the `0/n` ratio, latest-of-each on interleaved emissions (contracts/location-sources.md) in `TEST/ui/PositionViewModelTest.kt`
- [ ] T012 Implement `PositionViewModel` in `MAIN/ui/PositionViewModel.kt`: constructor takes both interfaces, `combine` → `StateFlow<PositionUiState>`, manual `ViewModelProvider.Factory` wiring real implementations (no DI framework — constitution V); T011 tests go green
- [ ] T013 Wire `MainActivity` in `MAIN/ui/MainActivity.kt`: Compose theme, `collectAsStateWithLifecycle()` consumption (the entire foreground-only mechanism), immediate first-launch system permission request (no pre-rationale card), temporary debug readout of raw state
- [ ] T014 On-device foundational smoke test (quickstart.md V2 — NON-NEGOTIABLE gate): Energy Profiler + logcat confirm registrations start with the UI, stop within 1 s of home/task-switch/lock (**E5**), and a single GNSS session with FLP + GnssStatus both active (**E4**); record outcomes in research.md

**Checkpoint**: Position pipeline proven live and foreground-only — user stories can begin

---

## Phase 3: User Story 1 — Live coordinate readout (Priority: P1) 🎯 MVP

**Goal**: Open the app, see live UTMREF (hero, BOS format) + lat/lon + Plus Code + both heights + accuracy + satellite ratio, updating ~1/s

**Independent Test**: Outdoors with permission granted: grid ref like `32U NA 64846 21576` appears, matches an independent converter, updates while walking; unavailable fields show dashes (spec.md US1)

### Tests for User Story 1 (write FIRST, must fail — binding fixtures in contracts/coordinates-api.md)

- [ ] T015 [P] [US1] UTMREF tests in `TEST/coordinates/UtmrefTest.kt`: Gadheim `49.8431 N, 9.9019 E` → `32U NA 648 215` (6-digit) + frozen 10-digit form; one fixture per zone 32T/32U/33T/33U; even-split digit groups at 4/6/8/10; BOS spacing
- [ ] T016 [P] [US1] Lat/lon format tests in `TEST/coordinates/LatLonFormatTest.kt`: display `48,137154°` (comma, 6 places) vs copy `48.137154, 11.575382` (dots, no °); DMS display; DMS round-trip within 6-place tolerance
- [ ] T017 [P] [US1] Plus Code test (`48.137154, 11.575382` → `8FWH4HX8+9C`, full global) and height formatting tests (whole-meter `487 m`, dash for null) in `TEST/coordinates/PlusCodeTest.kt` and `TEST/coordinates/HeightFormatTest.kt`

### Implementation for User Story 1

- [ ] T018 [US1] Implement `MAIN/coordinates/Utmref.kt`: `mil.nga:mgrs` wrapper (`MGRS.from(Point)`, `coordinate(GridType)`) + own BOS spacing formatter, `UtmrefPrecision` enum (4/6/8/10, default 10); while here resolve **E1** (does the library emit spaced or compact strings?) and record in research.md; T015 green
- [ ] T019 [P] [US1] Implement `MAIN/coordinates/LatLonFormat.kt` (decimal display/copy split, DMS both ways); T016 green
- [ ] T020 [P] [US1] Implement `MAIN/coordinates/PlusCode.kt` (openlocationcode wrapper, global code) and `MAIN/coordinates/HeightFormat.kt`; T017 green
- [ ] T021 [US1] Build the bottom-sheet readout in `MAIN/ui/ReadoutSheet.kt` per SPEC.md §7: peek = UTMREF hero (largest element) + two equal accent-bordered height cards (ellipsoidisch / über NHN) each with vertical-accuracy sub-line; expanded = Breite/Länge, Plus Code, metadata strip (±n m + `18 / 24` satellite ratio with fill bar); dashed rows for null fields; monospaced tabular-figures face for coordinates; German labels
- [ ] T022 [US1] Assemble main screen shell in `MAIN/ui/MainScreen.kt`: top app bar (brand + live GPS-fix status chip), map placeholder area, bottom sheet; `Acquiring` rendering = chip „Kein Fix", dashes + „Suche Satelliten…" with `0/n` ratio (SPEC.md §7.1); replace T013's debug readout
- [ ] T023 [US1] Add decimal ⇄ DMS display toggle as UI-state preference (data-model.md display preferences) in `MAIN/ui/PositionViewModel.kt` + toggle affordance on the lat/lon row in `MAIN/ui/ReadoutSheet.kt`
- [ ] T024 [US1] On-device validation quickstart.md V1: first fix < 30 s outdoors, ≤ 2 s refresh while walking, UTMREF cross-checked against an independent converter, NHN plausibility (**E8**), does FLP populate MSL itself (**E3**); re-confirm V2 teardown with the real UI; record E3/E8 in research.md

**Checkpoint**: MVP — a correct, live, field-usable coordinate readout

---

## Phase 4: User Story 2 — Position on a map (Priority: P2)

**Goal**: Full-bleed MapLibre map with blue-dot + accuracy circle, follow-me, locate button, north-up lock

**Independent Test**: Marker sits at true position, circle matches ±n m, drag disengages follow, locate button recentres + re-engages (spec.md US2)

### Implementation for User Story 2

- [ ] T025 [US2] Resolve **E6** and the base style: confirm the exact OpenFreeMap style URL (most neutral, Liberty-class) from openfreemap.org and verify maplibre-compose exposes raster sources + per-layer `visibility` toggling; if not, decide the `AndroidView`-wrapped `MapView` fallback now; record both in research.md
- [ ] T026 [US2] Add `MapUiState` as a second `StateFlow` on `MAIN/ui/PositionViewModel.kt`: `followMe` (default true), camera position, `satelliteVisible`/`parcelsVisible` (default false, consumed in US4) per data-model.md
- [ ] T027 [US2] Create `MAIN/ui/MapContent.kt`: OpenFreeMap base style, camera init on first fix at ~z16, rotation + tilt locked north-up (contracts/map-services.md §1)
- [ ] T028 [US2] Add own-position blue-dot marker + translucent accuracy circle sized from `horizontalAccuracyM` in `MAIN/ui/MapContent.kt`
- [ ] T029 [US2] Implement follow-me gating in `MAIN/ui/MapContent.kt` + `MAIN/ui/PositionViewModel.kt`: camera tracks fixes only while `followMe`; map drag → disengage; floating locate button → recentre + re-engage; floating zoom controls (SPEC.md §8.2)
- [ ] T030 [US2] Integrate Layout A in `MAIN/ui/MainScreen.kt`: map full-bleed behind the bottom sheet and app bar, floating tools on the map; enable MapLibre's attribution control with the OpenFreeMap/OSM line (contracts/map-services.md attribution rules)
- [ ] T031 [US2] On-device validation quickstart.md V5 (camera behavior, follow-me, north-up, circle-vs-readout correspondence) and V3 (airplane mode: readouts keep working, tiles degrade only)

**Checkpoint**: Map + readout work together; US1 still passes independently

---

## Phase 5: User Story 3 — Copy and share the position (Priority: P3)

**Goal**: Whole-row tap-to-copy with exact payloads; one Teilen action producing the German share block

**Independent Test**: Paste each copied row elsewhere and compare against contracts/coordinates-api.md payload table; share into a messenger and open the link (spec.md US3)

### Tests for User Story 3 (write FIRST, must fail)

- [ ] T032 [P] [US3] `ShareFormatting` share-block tests in `TEST/coordinates/ShareFormattingTest.kt`: full SPEC.md §6.6 block with fixed injected timestamp — structure, German labels, UTMREF first, dotted decimals, `https://www.google.com/maps?q=…` link, dash handling for missing values (per-row copy payloads are already covered by their format tests T015–T017 per contracts/coordinates-api.md ownership rule)

### Implementation for User Story 3

- [ ] T033 [US3] Implement `MAIN/coordinates/ShareFormatting.kt`: share block builder only (takes snapshot, UTMREF precision — always 10-digit from the v1 UI — and injected clock/format; pure, zero Android imports); per-row copy forms live with their format files (T019/T020); T032 green
- [ ] T034 [US3] Add tap-to-copy to every value row in `MAIN/ui/ReadoutSheet.kt`: whole row is the tap target, `ClipboardManager.setPrimaryClip` performed by the Composable directly (no ViewModel event channel), German confirmation toast
- [ ] T035 [US3] Add the single Teilen action to the app bar in `MAIN/ui/MainScreen.kt`: `ACTION_SEND` chooser with the share block at currently displayed precision
- [ ] T036 [US3] On-device validation quickstart.md V4: one-tap copy fidelity (decimal form pastes into a maps search and resolves to the same spot), ≤ 2-tap share, link opens correctly

**Checkpoint**: Position can be relayed — copy and share exact to contract

---

## Phase 6: User Story 4 — Aerial imagery and parcel boundaries (Priority: P4)

**Goal**: Two independent opt-in raster overlays (DOP20 orthophotos, ALKIS parcel outlines) with correct attribution

**Independent Test**: At a known Bavarian location, each layer toggles independently, parcels align with base geometry and vanish when zoomed out; attribution correct per layer combination (spec.md US4)

### Implementation for User Story 4

- [ ] T037 [P] [US4] Add DOP20 raster source + layer in `MAIN/ui/MapContent.kt` per contracts/map-services.md §2 (`{bbox-epsg-3857}` WMS template), visibility bound to `MapUiState.satelliteVisible`
- [ ] T038 [P] [US4] Add ALKIS parcel raster source + layer in `MAIN/ui/MapContent.kt` per contracts/map-services.md §3: exact GetMap template with **mandatory `STYLES=Gelb`**, `TRANSPARENT=TRUE`, `minzoom` 16–17, visibility bound to `MapUiState.parcelsVisible`
- [ ] T039 [US4] Add the floating layer-toggle control on the map in `MAIN/ui/MapContent.kt` + toggle actions on `MAIN/ui/PositionViewModel.kt`: two independent switches (Satellit / Flurstücke), both default off, base layer not toggleable
- [ ] T040 [US4] Extend attribution handling in `MAIN/ui/MapContent.kt`: add "Bayerische Vermessungsverwaltung – www.geodaten.bayern.de" whenever DOP20 or parcels are visible (one line covers both), per contracts/map-services.md attribution table
- [ ] T041 [US4] On-device validation quickstart.md V6: imagery appears, parcel outlines align + respect minzoom, yellow-vs-black legibility decision (**E7** — swap to `umr_schwarz`/`Schwarz` if needed), attribution per combination, graceful nothing outside Bavaria, and WMS-failure behavior (unreachable/rejected requests → overlay simply absent, base map and app unaffected); record E7 in research.md

**Checkpoint**: All map layers done; overlays fail silently without breaking base map

---

## Phase 7: User Story 5 — Trustworthy states and legal transparency (Priority: P5)

**Goal**: Complete permission/system-state matrix in German with one-tap fixes; Über/Lizenzen screen

**Independent Test**: Walk the state matrix via device settings (deny once, deny permanently, coarse-only, location off) and verify each behavior; check licenses screen against shipped components (spec.md US5)

### Implementation for User Story 5

- [ ] T042 [US5] Extend ViewModel merge tests in `TEST/ui/PositionViewModelTest.kt` (write FIRST) for the remaining transitions per data-model.md: permission states → `Acquiring`, `LocationOff` handling, resume after settings return
- [ ] T043 [US5] Implement the full state machine + screens in `MAIN/ui/PermissionStates.kt` and `MAIN/ui/PositionViewModel.kt` per SPEC.md §7.1: rationale card + retry on re-askable denial; `PermanentlyDenied` screen deep-linking `ACTION_APPLICATION_DETAILS_SETTINGS`; `LocationOff` card + `ACTION_LOCATION_SOURCE_SETTINGS` deep-link over dimmed map; re-evaluate state on `ON_START` return from settings; T042 green
- [ ] T044 [US5] Add coarse-only handling in `MAIN/ui/MainScreen.kt`: function normally on coarse fixes + persistent non-blocking banner „Genauer Standort empfohlen" with one-tap precise re-request
- [ ] T045 [P] [US5] Create `MAIN/ui/AboutScreen.kt` (Über / Lizenzen) listing every component/data source with license + attribution wording exactly per contracts/map-services.md licenses table, reachable from the main screen
- [ ] T046 [US5] On-device validation quickstart.md V7 (full state matrix walk) and V8 (licenses screen completeness)

**Checkpoint**: All five stories independently functional

---

## Phase 8: Polish & Cross-Cutting Concerns

- [ ] T047 [P] German copy review across `MAIN/ui/`: every label/state/toast/banner German, decimal commas in all displayed numbers, wording matches SPEC.md §7 („Kein Fix", „Suche Satelliten…", „Genauer Standort empfohlen", „Teilen")
- [ ] T048 [P] Battery/lifecycle re-verification with the complete app: Energy Profiler session re-confirming single GNSS session (**E4**) and instant `ON_STOP` teardown (**E5**) now that map + overlays are active
- [ ] T049 Full release gate: `./gradlew testDebugUnitTest` green + complete quickstart.md V1–V8 pass on the physical device; fill in every E1–E8 outcome in specs/001-gpsview-android-app/research.md
- [ ] T050 Code cleanup: remove temporary debug UI remnants, dead code, and TODOs; confirm `coordinates` package still has zero Android imports (constitution I) — e.g. `grep -r "import android" app/src/main/kotlin/de/eckstein/gpsview/coordinates/` returns nothing

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: none — start immediately; T001 → T002 → T003 → T004
- **Foundational (Phase 2)**: depends on Phase 1 — **BLOCKS all user stories**. T005/T006/T007 parallel → T008/T009 (need T005+T007) → T010/T011 → T012 → T013 → T014
- **US1 (Phase 3)**: after Phase 2. Tests T015–T017 parallel first → T018 (then T019/T020 parallel) → T021 → T022 → T023 → T024
- **US2 (Phase 4)**: after Phase 2; layout integration T030 touches `MainScreen.kt` from US1 — run after US1 or coordinate the file
- **US3 (Phase 5)**: after US1 (rows exist to copy; `ShareFormatting` itself only needs Phase 2 types — T032/T033 can start right after Phase 2)
- **US4 (Phase 6)**: after US2 (extends `MapContent.kt` and `MapUiState`)
- **US5 (Phase 7)**: after Phase 2 (T042/T043/T045 independent of other stories); T044 touches `MainScreen.kt` — coordinate with US1/US2
- **Polish (Phase 8)**: after all desired stories

### Within Each User Story

- Test tasks are written first and must fail before their implementation task goes green (constitution test-first gate)
- Pure `coordinates` implementations before UI that renders them
- Each story ends with its on-device validation task — that task is the story's done-gate

### Parallel Opportunities

- Phase 2: T005, T006, T007 together; then T008 ∥ T009; T010 ∥ T011
- US1: T015 ∥ T016 ∥ T017 (three test files); then T019 ∥ T020 after T018
- US3's pure work (T032, T033) can overlap with US2's map work — different files
- US4: T037 ∥ T038 (two independent sources, coordinate the shared file or do sequentially in one sitting)
- US5: T045 (AboutScreen) parallel to everything in the phase
- Polish: T047 ∥ T048

---

## Parallel Example: User Story 1

```bash
# After Phase 2 — launch all US1 test authoring together:
Task: "UTMREF fixtures (Gadheim, 4 zones, precision, BOS spacing) in TEST/coordinates/UtmrefTest.kt"
Task: "Lat/lon display-vs-copy + DMS round-trip in TEST/coordinates/LatLonFormatTest.kt"
Task: "Plus Code + height formatting in TEST/coordinates/PlusCodeTest.kt, HeightFormatTest.kt"

# Then, after Utmref.kt (T018):
Task: "LatLonFormat.kt implementation"
Task: "PlusCode.kt + HeightFormat.kt implementation"
```

---

## Implementation Strategy

### MVP First (Phases 1–3)

1. Phase 1: Setup — buildable app, E2 resolved
2. Phase 2: Foundational — live position pipeline, **V2 foreground-only gate passed** (non-negotiable; fail here = stop and fix)
3. Phase 3: US1 — the live readout
4. **STOP and VALIDATE**: quickstart.md V1 outdoors. This is already a field-usable app (correct grid reference, no map)

### Incremental Delivery

Each story ends in a deployable sideload build: US1 (readout MVP) → US2 (map) → US3 (copy/share) → US4 (Bavarian overlays) → US5 (state matrix + licenses) → Polish (release gate T049). Story order equals priority order; US3's pure half can be pulled forward if the map work stalls on E6.

### Empirical checks woven in

E1→T018, E2→T004, E3/E8→T024, E4/E5→T014 (re-checked T048), E6→T025, E7→T041. All outcomes land in research.md so the record stays complete (constitution traceability).

---

## Notes

- Verify each test task fails before implementing its counterpart; commit after each task or logical group
- `MainScreen.kt` is touched by T022/T030/T035/T044 — sequence those or expect merge friction
- Stop at any checkpoint to validate the story independently on the device
