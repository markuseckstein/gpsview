# Phase 0 Research: Landscape Orientation & System-Bar Safe Layout

All items from the spec's clarifications are resolved; the remaining unknowns were purely
*how* to implement inside the existing Compose stack. No NEEDS CLARIFICATION remain.

## R1 — Edge-to-edge enablement & the Android 16 clipping defect

**Decision**: Call `enableEdgeToEdge()` in `MainActivity.onCreate()` (before `setContent`) and
make the theme's system-bar backgrounds transparent in `themes.xml`. Draw the map full-bleed and
apply `WindowInsets.safeDrawing` (or `systemBars` + `displayCutout`) padding only to the readout
container and the floating controls.

**Rationale**: The app targets `targetSdk = 36`; on Android 15+ (SDK 35+) the platform **enforces**
edge-to-edge and ignores legacy `fitsSystemWindows`/`setDecorFitsSystemWindows` opt-outs. That is
exactly why the bottom readout row is currently drawn behind the navigation bar on Android 16 — the
window already extends under the bars, but the content is not inset. The fix is not to opt out
(impossible at this target) but to opt *in* deliberately: full-bleed map + explicitly inset content.
`enableEdgeToEdge()` also sets transparent/adaptive system-bar scrims, which is what we want for the
"map under the bars" look (FR-005).

**Alternatives considered**:
- *Legacy `fitsSystemWindows` / window flags* — rejected: no longer honored at targetSdk ≥ 35, would
  not fix Android 16, and fights the platform.
- *Lowering targetSdk to dodge enforcement* — rejected: violates the platform constraint and only
  defers the defect; the constitution mandates SDK-34+ platform APIs used directly.

## R2 — Which Compose inset to apply, and where

**Decision**: Inset **only** the content that must stay readable/tappable, not the map:
- Map (`MaplibreMap`) fills the whole window — no inset (FR-005 full-bleed).
- Readout container (portrait bottom sheet content / landscape side panel) gets bottom + horizontal +
  cutout insets so the lowest row clears the nav bar (FR-002) and side insets in landscape (FR-001).
- `MapContent`'s floating layer chips (top-start), zoom/locate FABs (bottom-end), and the Bavarian
  attribution text (bottom-start) each get `safeDrawing`/`systemBars` padding so they remain fully
  tappable and unobscured (FR-003, FR-006 attribution within safe area).

Use `Modifier.windowInsetsPadding(WindowInsets.safeDrawing)` (which unions system bars + display
cutout) on those elements, or consume insets via the scaffold and pad the map's siblings accordingly.

**Rationale**: `safeDrawing` is the single inset that already accounts for system bars **and** display
cutouts/rounded corners, satisfying FR-004 (notch) in one API. Applying it per-island (readout, chips,
FABs, attribution) rather than to a global container is what lets the map alone bleed full-screen while
everything else stays safe.

**Alternatives considered**:
- *Global `Scaffold(contentWindowInsets = safeDrawing)`* — rejected: would inset the map too, defeating
  the edge-to-edge look. We need the map and its overlays to have *different* inset treatment.
- *Manual `WindowInsetsCompat` reads in the Activity* — rejected: Compose `WindowInsets` are the idiomatic
  and simpler path here (Principle V).

## R3 — Portrait vs. landscape arrangement selection (window-driven, not orientation flag)

**Decision**: Select the arrangement in `MainScreen` using `BoxWithConstraints` and comparing
`maxWidth` vs. `maxHeight`: when `maxWidth > maxHeight` (landscape-shaped window) use the side-panel
arrangement, otherwise the bottom-sheet arrangement.

**Rationale**: The spec (FR-008, edge cases) explicitly requires deriving the arrangement from the
**available window size/aspect**, so split-screen, multi-window, foldables, and free-form windows behave
correctly — a portrait-shaped window on a landscape device must still use the bottom arrangement.
`BoxWithConstraints` reports the actual composable's available size and recomposes on resize/rotation,
so it tracks the live window, not a static device flag. It requires **no new dependency** (Principle V).

**Alternatives considered**:
- *`LocalConfiguration.orientation`* — rejected: reflects device orientation, not window shape; wrong for
  split-screen/free-form (fails FR-008).
- *`material3-window-size-class` (`WindowWidthSizeClass`)* — rejected for v1: adds a dependency to get
  breakpoint buckets we don't need; a simple width-vs-height comparison expresses the "wider than tall"
  rule directly. Left as a cleanly additive future option if richer breakpoints are ever wanted.

## R4 — Landscape side panel: fixed, scrollable, reusing existing readout

**Decision**: Build a thin `LandscapeReadoutPanel` composable that places a `Column` occupying ~40% of
the window width on the leading edge, hosting the **existing** `ReadoutSheetContent` inside a
`verticalScroll` container, with the coarse-only banner above it (as in portrait). The map fills the
remaining ~60%. No collapse/expand state.

**Rationale**: Clarifications fixed the panel as fixed-always-visible with internal scroll and ~40/60
split (FR-006, FR-009, FR-010). Reusing `ReadoutSheetContent` verbatim guarantees every value and every
tap-to-copy/toggle interaction is identical to portrait (FR-011, SC-004) with zero duplicated readout
logic. `verticalScroll` satisfies the very-short-landscape edge case (all rows reachable). The 40% width
must be validated to fit the UTMREF hero (32sp monospace) without truncation — checked in quickstart.

**Alternatives considered**:
- *`Row(weight 0.4f / 0.6f)`* — viable and simplest for the split; use weights for the 40/60 division.
- *Reusing `BottomSheetScaffold` rotated* — rejected: the sheet's collapse/expand semantics are explicitly
  unwanted in landscape; a plain scrollable panel is simpler and matches the clarified design.
- *Forking a landscape-specific readout* — rejected: would risk value/interaction drift (violates SC-004
  and Principle V).

## R5 — State preservation across rotation / resize

**Decision**: Rely on the existing ViewModel-held state (`uiState`, `mapUiState`, `latLonMode`,
follow-me) surviving configuration changes, and keep map camera state resilient. Switching arrangement is
a recomposition reading the same ViewModel flows; no state is owned by the arrangement branch that would
be lost on the switch. Verify the map camera/zoom and follow-me survive rotation on-device.

**Rationale**: FR-012/SC-005 require position, layers, follow-me, and display mode to persist across
rotation with no crash. Because both arrangements subscribe to the same ViewModel, the data is preserved
by construction. The only risk is Compose-local `remember` state inside `MapContent` (e.g.
`hasCenteredOnFirstFix`, camera state) being reset if the map composable is torn down and recreated when
the arrangement branch changes — this must be verified, and if needed the map should be hoisted so it is a
stable sibling shared by both arrangements rather than re-created per branch.

**Alternatives considered**:
- *`android:configChanges` orientation lock in the manifest to avoid recreation* — rejected: masks rather
  than fixes state handling, and the requirement is explicitly to support arbitrary window resizes
  (multi-window), which `configChanges` does not fully cover. Proper hoisting/`rememberSaveable` is the
  correct tool.

## Summary of decisions

| # | Decision |
|---|----------|
| R1 | `enableEdgeToEdge()` in `MainActivity`; transparent bars in `themes.xml`; opt *in*, don't opt out |
| R2 | Apply `WindowInsets.safeDrawing` per-island (readout, chips, FABs, attribution); map stays full-bleed |
| R3 | Arrangement chosen by `BoxWithConstraints` width-vs-height; no new dependency |
| R4 | New thin `LandscapeReadoutPanel` reusing existing `ReadoutSheetContent` in a `verticalScroll`, 40/60 `Row` weights |
| R5 | State preserved via existing ViewModel flows; hoist map if branch-switch resets its `remember` state |
