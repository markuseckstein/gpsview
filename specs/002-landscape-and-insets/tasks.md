---
description: "Task list for Landscape Orientation & System-Bar Safe Layout"
---

# Tasks: Landscape Orientation & System-Bar Safe Layout

**Input**: Design documents from `/specs/002-landscape-and-insets/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/ui-layout.md, quickstart.md

**Tests**: No new automated tests requested. This feature is presentation-only and adds no pure-core logic;
the existing `coordinates`/ViewModel JVM tests are re-run unchanged (Polish phase) and must stay green.
Behavioral verification is the on-device walkthrough in `quickstart.md`, run against the connected hardware
device (`adb -s 192.168.179.35:38049`).

**Organization**: Tasks are grouped by user story. US1 (inset safety) is the MVP and must land first; US2
(landscape) rests on the edge-to-edge foundation established for US1.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: US1 / US2 (Setup, Foundational, Polish carry no story label)
- All source paths are under `app/src/main/kotlin/de/eckstein/gpsview/`

## Path Conventions

Single Gradle `:app` module (mobile). All changes confined to the `ui` package plus `MainActivity`,
`res/values/themes.xml`, and (if needed) `AndroidManifest.xml`. `coordinates/` and `data/` are **not**
touched (constitution I/II, FR-013).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Establish a known-good baseline before changing layout.

- [X] T001 Build and install the current app on the connected device to capture the pre-change baseline: `./gradlew :app:installDebug` then launch and note the Android-16 bottom-row clipping and current landscape behavior (`adb -s 192.168.179.35:38049 shell am start -n de.eckstein.gpsview/.ui.MainActivity`).
- [X] T002 Run `./gradlew test` and confirm the existing `coordinates` + `PositionViewModel` JVM tests pass, recording the green baseline that Polish must preserve.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Turn on edge-to-edge rendering so the map can draw under the system bars. Both user stories depend on this.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T003 In `ui/MainActivity.kt`, call `enableEdgeToEdge()` (androidx.activity.compose) at the top of `onCreate()` before `setContent`, so the window content draws behind the status and navigation bars (research R1, FR-005).
- [X] T004 In `app/src/main/res/values/themes.xml`, make the system-bar backgrounds transparent for `Theme.GPSView` (transparent `android:statusBarColor` / `android:navigationBarColor`, `android:windowLightStatusBar`/`windowLightNavigationBar` as appropriate) so the map is visible beneath the bars with legible bar icons (research R1, FR-005).
- [X] T005 Confirm `AndroidManifest.xml` imposes no orientation lock and no legacy inset opt-out on `MainActivity` (no `android:screenOrientation`, no `fitsSystemWindows`); leave as-is if already clean, so window-driven arrangement (FR-008) is possible.

**Checkpoint**: App runs edge-to-edge; map now extends under the bars (content not yet inset — fixed in US1).

---

## Phase 3: User Story 1 - No content hidden behind the system navigation bar (Priority: P1) 🎯 MVP

**Goal**: Every readout row and every interactive control renders within the safe (inset-respecting) area in portrait; the map bleeds full-screen while the bottom row clears the nav bar and edge controls stay tappable — incl. Android 16, notch, and rounded corners.

**Independent Test**: On the device in portrait with a live fix, collapse/expand the readout — the lowest row is fully visible above the nav bar and tappable; the map shows behind the bars; layer chips, zoom/locate FABs, and the Bavarian attribution are all fully visible and activate on first tap (quickstart V1, V2, V6).

### Implementation for User Story 1

- [X] T006 [US1] In `ui/MainScreen.kt`, keep the `MaplibreMap` full-bleed but apply `WindowInsets.safeDrawing` padding to the bottom-sheet content (the `sheetContent` Column hosting `CoarseOnlyBanner` + `ReadoutSheetContent`) so the lowest readout row is never clipped by the navigation/gesture bar (FR-001, FR-002). Ensure the `BottomSheetScaffold`'s own inset handling does not double-inset or re-inset the map.
- [X] T007 [P] [US1] In `ui/MapContent.kt`, inset the floating layer chips Column (`Alignment.TopStart`) with `WindowInsets.safeDrawing` (status bar + leading cutout) so the Satellit/Flurstücke chips are not under the status bar or notch (FR-003, FR-004), while the underlying `MaplibreMap` stays `fillMaxSize()` full-bleed (FR-005).
- [X] T008 [P] [US1] In `ui/MapContent.kt`, inset the zoom/locate FAB Column (`Alignment.BottomEnd`) with `WindowInsets.safeDrawing` so the +/−/locate buttons clear the navigation bar and remain fully tappable near the bottom edge (FR-002, FR-003).
- [X] T009 [P] [US1] In `ui/MapContent.kt`, inset the Bavarian attribution `Text` (`Alignment.BottomStart`) with `WindowInsets.safeDrawing` so it stays within the safe area and unobscured whenever a Bavarian layer is on (FR-001, constitution VI attribution obligation).
- [X] T010 [US1] Verify on the connected device in **portrait** (Android 16, nav bar visible; repeat with gesture and 3-button nav): run quickstart V1 (no clipped bottom row, map under bars), V2 (all edge controls first-tap), and V6 (notch profile if available). Deploy via `./gradlew :app:installDebug`.

**Checkpoint**: Portrait is fully inset-safe on Android 16 — the reported clipping defect is fixed. MVP is shippable.

---

## Phase 4: User Story 2 - Readable landscape layout with map and metadata side by side (Priority: P2)

**Goal**: When the window is wider than tall, show a fixed leading-edge metadata panel (~40%) reusing the existing readout, with the map filling ~60%; portrait keeps its bottom sheet; all values/interactions and state survive rotation.

**Independent Test**: Rotate the device to landscape with a live fix — metadata is a fixed side panel on the left at ~40% width with the map on the right; the UTMREF hero is untruncated; all rows reachable (scroll if needed); every value/interaction from portrait works; rotating back restores the bottom sheet with no state loss or crash (quickstart V3, V4, V5, V7).

### Implementation for User Story 2

- [X] T011 [US2] In `ui/MainScreen.kt`, wrap the main content in `BoxWithConstraints` and branch on `maxWidth > maxHeight` to choose the arrangement (SidePanel vs. BottomSheet), reading all state from the same `PositionViewModel` flows so the choice is purely window-aspect driven, not a device-orientation flag (FR-006, FR-007, FR-008; contract C1).
- [X] T012 [US2] Create the landscape side-panel composable (new `ui/LandscapeReadoutPanel.kt`, or a private composable in `MainScreen.kt`): a leading-edge `Column` at `Row` weight ≈`0.40f` hosting `CoarseOnlyBanner` + the existing `ReadoutSheetContent` inside a `Modifier.verticalScroll(rememberScrollState())`, with `WindowInsets.safeDrawing` applied so all rows are reachable and unobscured; no collapse/expand state (FR-006, FR-009, FR-010; contract C3). Reuse `ReadoutSheetContent` verbatim — do not fork readout logic (SC-004, Principle V).
- [X] T013 [US2] In `ui/MainScreen.kt`, lay out the SidePanel branch as a `Row { panel(weight 0.4f); map(weight 0.6f) }` with the map full-bleed on the trailing ~60% so a usable map and the untruncated hero are visible together (FR-010, SC-003).
- [X] T014 [US2] Preserve map state across the arrangement switch: hoist the single `MapContent` into a `movableContentOf { … }` (or otherwise share one stable instance) so switching between the BottomSheet and SidePanel branches does not tear down and reset `MapContent`'s local `remember` state (camera position/zoom, `hasCenteredOnFirstFix`) or follow-me (research R5, FR-012, SC-005).
- [X] T015 [US2] Verify on the connected device: run quickstart V3 (landscape side panel ~40/60, hero untruncated, panel scroll reaches all rows), V4 (every value + tap-copy/Share/toggles/locate work in landscape), V5 (rotate portrait⇄landscape and, if available, split-screen resize preserves position/layers/follow-me/DMS/zoom with no crash), and V7 (portrait-shaped split-screen window uses the bottom sheet). Deploy via `./gradlew :app:installDebug`.

**Checkpoint**: Both orientations behave correctly and state survives rotation/resize.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Confirm no regression and finalize.

- [X] T016 Run `./gradlew test` and confirm all `coordinates` + `PositionViewModel` tests still pass unchanged, proving FR-013 (no core/derivation change) held (matches T002 baseline).
- [X] T017 [P] Full-walkthrough regression on the device in BOTH orientations: re-run the complete quickstart V1–V7 checklist end to end, confirming SC-001…SC-006 (zero clipped rows, first-tap controls, 60/40 split, no feature regression, state preserved, notch-safe).
- [X] T018 [P] Code cleanup in the touched `ui/` files: remove unused imports, keep inset handling consistent (single `safeDrawing` idiom), and confirm no Android import or logic leaked into `coordinates/` (constitution I, V).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately.
- **Foundational (Phase 2)**: Depends on Setup — edge-to-edge (T003–T005) BLOCKS both user stories.
- **User Story 1 (Phase 3)**: Depends on Foundational. This is the MVP.
- **User Story 2 (Phase 4)**: Depends on Foundational; builds on US1's edge-to-edge foundation. Best done after US1 so inset patterns are established, but the landscape arrangement itself is independently testable.
- **Polish (Phase 5)**: Depends on all desired stories being complete.

### User Story Dependencies

- **US1 (P1)**: Independent once Foundational is done — delivers the inset-safety fix on its own.
- **US2 (P2)**: Relies on the edge-to-edge foundation (Phase 2). Reuses `ReadoutSheetContent` and `MapContent` from US1's inset work; landscape behavior is independently verifiable.

### Within Each User Story

- US1: T006 (sheet inset in MainScreen) is sequential; T007/T008/T009 (three independent islands in MapContent) can parallelize; T010 verifies last.
- US2: T011 (branch) → T012 (panel) → T013 (row split) → T014 (state hoist) are largely sequential (same/adjacent files); T015 verifies last.

### Parallel Opportunities

- T007, T008, T009 touch different, independent regions of `MapContent.kt` and can be done together after T003/T004.
- T017 and T018 (Polish) can run in parallel.

---

## Parallel Example: User Story 1

```bash
# After Foundational (T003–T005), the three MapContent inset islands are independent:
Task: "T007 Inset the layer chips (TopStart) in ui/MapContent.kt"
Task: "T008 Inset the zoom/locate FABs (BottomEnd) in ui/MapContent.kt"
Task: "T009 Inset the Bavarian attribution (BottomStart) in ui/MapContent.kt"
```

---

## Implementation Strategy

### MVP First (User Story 1 only)

1. Phase 1 Setup → capture baseline (incl. reproducing the Android-16 clipping on device).
2. Phase 2 Foundational → enable edge-to-edge (CRITICAL, blocks everything).
3. Phase 3 US1 → inset the readout + all map controls; map stays full-bleed.
4. **STOP and VALIDATE**: quickstart V1/V2/V6 in portrait on the device — the reported defect is fixed.
5. Ship the MVP.

### Incremental Delivery

1. Setup + Foundational → edge-to-edge ready.
2. US1 → portrait inset safety → validate → ship (MVP, fixes the actual bug report).
3. US2 → landscape side panel + state preservation → validate → ship.
4. Polish → re-run JVM tests + full V1–V7 walkthrough.

---

## Notes

- [P] tasks = different files/regions, no dependencies.
- Device for all on-device verification: `adb -s 192.168.179.35:38049` (real hardware, connected).
- `coordinates/` and `data/` are never edited — any diff there is a red flag (constitution I/II, FR-013).
- No new dependency is added (Principle V): arrangement uses `BoxWithConstraints`, insets use APIs already
  on the classpath.
- Commit after each task or logical group; stop at either checkpoint to validate independently.
