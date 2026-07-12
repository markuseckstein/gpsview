# UI Layout Contract: Arrangement & Inset Behavior

GPSView is an application, not a library or service, so its "interface contract" is the on-screen layout
behavior. This document specifies the observable contract the implementation MUST satisfy; it is the basis
for the `quickstart.md` validation walkthrough and for `/speckit-tasks`.

## C1 — Arrangement selection

- **Input**: the current available window size (width `W`, height `H`) of the app's content area.
- **Rule**: if `W > H` → **SidePanel** arrangement; otherwise → **BottomSheet** arrangement.
- **MUST** be recomputed live from the window (via `BoxWithConstraints`), not from a device-orientation
  flag, so split-screen / multi-window / free-form / foldable windows select correctly (FR-008).

## C2 — BottomSheet arrangement (portrait-shaped window)

- Retains the existing `BottomSheetScaffold` with its collapse/expand peek behavior (FR-007, FR-012).
- Readout content = `ReadoutSheetContent` (unchanged), optionally preceded by the coarse-only banner.
- The sheet content MUST be inset from the bottom system/navigation bar so the lowest row is never clipped
  (FR-002).

## C3 — SidePanel arrangement (landscape-shaped window)

- A single `Row`: leading-edge **panel** at weight ≈ `0.40`, **map** at weight ≈ `0.60` (FR-010).
- The panel is **fixed and always visible** — no collapse/expand state (FR-006).
- The panel hosts the **same** `ReadoutSheetContent` inside a vertical-scroll container; when content is
  taller than the panel, all rows MUST be reachable by scrolling (FR-009, short-landscape edge case).
- The panel MUST be wide enough that the UTMREF hero (32sp monospace) and every row render without
  truncation (FR-010, SC-003).
- Placed on the leading edge (left in LTR); MUST respect the side/cutout inset so nothing is obscured
  (FR-004, edge cases).

## C4 — Inset / edge-to-edge contract (both arrangements)

- The map surface MUST extend full-bleed beneath the system bars (edge-to-edge look) (FR-005).
- Every textual readout row and every interactive control MUST render within the safe (inset-respecting)
  area — system bars AND display cutout/rounded corners (FR-001, FR-004).
- The bottom-most readout row MUST be fully visible above the navigation/gesture bar, incl. Android 16
  (FR-002).
- Controls near screen edges MUST remain fully tappable; system-bar regions MUST NOT steal their taps
  (FR-003). This covers `MapContent`'s layer chips, zoom/locate FABs, and the Bavarian attribution text.

## C5 — Preserved-behavior contract (both arrangements)

- Tap-a-row-to-copy, the single Share action + identical summary, locate/follow-me, and the DOP20 &
  ALKIS layer toggles MUST work identically in both arrangements (FR-011, SC-004).
- Rotating/resizing MUST preserve live position, selected layers, follow-me engagement, and lat/lon
  display mode, with no crash (FR-012, SC-005).
- NO change to coordinate computation, GNSS derivation, share/copy payloads, permission flow, or the
  foreground-only lifecycle (FR-013).

## C6 — Non-goals (explicit)

- No persisted preference for orientation or panel side.
- No new screens, values, or navigation.
- No `material3-window-size-class` or other new dependency for v1 (research R3).
- Bavarian attribution overlay content is unchanged; only its inset position may adjust (Principle VI).
