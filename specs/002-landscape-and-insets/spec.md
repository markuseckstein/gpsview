# Feature Specification: Landscape Orientation & System-Bar Safe Layout

**Feature Branch**: `002-landscape-and-insets`

**Created**: 2026-07-12

**Status**: Draft

**Input**: User description: "I want the app to also work in horizontal orientation mode correctly (so that we can see part of the map and the metadata. Maybe we should have the metadata on the left (instead from the bottom) in horizontal mode?. Also, there is a glitch of the UI on modern Android versions - the last row at the bottom of the UI is behind the android os button bar (this is especially on android 16)."

**Context**: This feature refines the existing GPSView single-screen experience ("Layout A – Karte im Fokus": a full-bleed map behind a coordinate readout). Today the readout always sits at the bottom of the screen. In landscape that bottom placement wastes the wide aspect ratio and squeezes both the map and the readout, and on recent Android versions the lowest readout row is clipped by the system navigation bar. This feature adds a landscape-appropriate arrangement and guarantees no content is ever hidden behind system bars. It changes only presentation and layout — all coordinate math, GNSS metadata, copy/share behavior, permission handling, and map layers are unchanged.

## Clarifications

### Session 2026-07-12

- Q: In landscape, how should the width be split between the metadata side panel and the map? → A: ~40% panel / 60% map (panel wide enough for the UTMREF hero without truncation; map keeps the majority)
- Q: How should the landscape metadata panel behave — fixed and always visible, or expandable like the portrait bottom sheet? → A: Fixed always-visible panel (all rows reachable via internal vertical scroll; no collapse/expand state)
- Q: Should the map render full-bleed beneath the system bars (edge-to-edge look), while text/controls stay inside the safe area? → A: Yes — map draws under the bars; only readout text and controls are inset

## User Scenarios & Testing *(mandatory)*

### User Story 1 - No content hidden behind the system navigation bar (Priority: P1)

As a user on a modern Android device (notably Android 16), when I view the coordinate readout I can see every row in full — including the lowest one — without any part being covered by the on-screen navigation/gesture bar at the bottom (or the side inset bar in landscape). Interactive elements near screen edges remain fully tappable.

**Why this priority**: This is a correctness defect: information the app exists to convey (and controls the user must tap) is currently obscured. It affects every user on affected devices regardless of orientation, so it must be fixed first and independently of the landscape work.

**Independent Test**: On a device/emulator with gesture or 3-button navigation visible, open the app in portrait and confirm the bottom-most readout row and any bottom controls are fully visible above the system bar and fully tappable; repeat with the readout expanded so its content extends to the screen edge.

**Acceptance Scenarios**:

1. **Given** a device showing the system navigation bar, **When** the readout is displayed (collapsed or expanded), **Then** the lowest row of content is rendered fully above the navigation bar with no clipping or overlap.
2. **Given** the app is running edge-to-edge behind the system bars, **When** the screen renders, **Then** all text and controls sit within the safe (inset-respecting) area while the map may still extend visually beneath the bars.
3. **Given** a bottom control or tappable row sits near the screen edge, **When** the user taps it, **Then** the tap reliably activates that control (the touch target is not stolen or blocked by the system bar).
4. **Given** a device with a display cutout/notch or rounded corners, **When** the app renders in either orientation, **Then** no readout text or control is obscured by the cutout or corner.

---

### User Story 2 - Readable landscape layout with map and metadata side by side (Priority: P2)

As a user who rotates the device to landscape (e.g. bracing the phone in the field), I see the coordinate metadata arranged along the side rather than crammed into a short strip at the bottom, so that a usable portion of the map and the key readout values are visible together at the same time.

**Why this priority**: Landscape is currently usable but poor — the bottom placement in a wide aspect ratio leaves the readout cramped and the map short. Fixing it materially improves the field experience, but the app already functions in landscape, so this ranks below the P1 clipping defect.

**Independent Test**: Rotate the device to landscape with a live fix and confirm the metadata occupies a side region while the map fills the remaining width, both usable simultaneously; rotate back to portrait and confirm the original bottom arrangement returns.

**Acceptance Scenarios**:

1. **Given** a live position, **When** the device is in landscape orientation, **Then** the coordinate metadata is presented in a side panel and the map occupies the remaining area, both visible at once.
2. **Given** the device is in portrait orientation, **When** the readout is shown, **Then** it retains the existing bottom placement and behavior.
3. **Given** the app is in landscape, **When** the user rotates to portrait (or vice versa), **Then** the layout switches to the orientation-appropriate arrangement without losing the current position, selected map layers, follow-me state, or lat/lon display mode.
4. **Given** the landscape side panel is shown, **When** the user reads the values, **Then** the hero grid reference (UTMREF) and the other coordinate/height/accuracy/satellite rows are all reachable (directly visible or via a scroll/expand affordance) — no readout value available in portrait is missing in landscape.
5. **Given** the landscape layout, **Then** every existing interaction still works: tapping a row copies its value, the Share action produces the same summary, the locate button recenters, and layer toggles function.

---

### Edge Cases

- **Which side in landscape** — the panel is placed on the leading edge (left in a left-to-right layout) per the user's suggestion; on notched devices the panel and controls still respect the cutout inset so nothing is obscured.
- **Very short landscape height** (small phones): the always-visible side panel must let the user reach all values via internal vertical scrolling rather than truncating them.
- **Split-screen / multi-window and free-form resizing**: the layout chooses its arrangement from the available window size/aspect, not a hard device-orientation flag, so a portrait-shaped window uses the bottom arrangement even on a landscape device.
- **Rotation mid-interaction** (e.g. while the readout is expanded or a layer menu is open): state is preserved across the arrangement change; nothing crashes or resets.
- **Foldables and tablets**: wide windows use the side arrangement; the behavior degrades gracefully across a range of aspect ratios rather than assuming a single phone size.
- **Landscape with the system bar on the side**: content respects the side inset just as the bottom inset is respected in portrait.
- **Reduced map visibility**: the side panel must not consume so much width that the map becomes unusable; a meaningful portion of the map remains visible in landscape.

## Requirements *(mandatory)*

### Functional Requirements

**System-bar & inset safety**

- **FR-001**: All textual readout content and all interactive controls MUST render within the area not covered by system bars (navigation bar, status bar, and any side inset), in both portrait and landscape.
- **FR-002**: The bottom-most readout row MUST be fully visible and never clipped or overlapped by the Android navigation/gesture bar, including on Android 16 and later where edge-to-edge is enforced.
- **FR-003**: Controls positioned near screen edges MUST remain fully tappable; system-bar regions MUST NOT intercept taps intended for app controls.
- **FR-004**: The layout MUST respect display cutouts (notches), rounded corners, and side insets so no content or control is obscured by them in either orientation.
- **FR-005**: The map surface MUST extend full-bleed beneath the system bars for an edge-to-edge look; only readout text and controls are constrained to the safe (inset) area, and no such text or control may be placed in the obscured region.

**Landscape layout**

- **FR-006**: When the available window is landscape-shaped (wider than tall), the app MUST present the coordinate metadata in a fixed, always-visible side panel on the leading edge and the map in the remaining area, both visible simultaneously.
- **FR-007**: When the available window is portrait-shaped, the app MUST retain the existing bottom-placed readout arrangement.
- **FR-008**: The app MUST select its arrangement from the current available window size/aspect (supporting split-screen, multi-window, foldables, and tablets), not solely from a device orientation flag.
- **FR-009**: Every readout value available in the portrait layout (UTMREF hero, latitude/longitude, Plus Code, both heights, horizontal accuracy, satellite ratio, and any acquiring/error state) MUST be reachable in the landscape side panel; when the values do not all fit at once, the panel MUST make them reachable via internal vertical scrolling (there is no collapse/expand state in landscape).
- **FR-010**: The landscape side panel MUST occupy approximately 40% of the window width, leaving approximately 60% for the map; the panel MUST be wide enough that the UTMREF hero grid reference and other rows render without truncation, and MUST NOT consume so much width that the map becomes unusable.

**Preserved behavior across the change**

- **FR-011**: All existing interactions MUST continue to work identically in both orientations: tap-a-row-to-copy, the single Share action and its summary content, the locate/follow-me control, and the aerial-imagery and parcel-boundary layer toggles.
- **FR-012**: Rotating the device or resizing the window MUST preserve current state — the live position, selected map layers, follow-me engagement, and lat/lon display mode — without resetting or crashing. The portrait bottom sheet retains its expand/collapse behavior; the landscape side panel is always fully shown (no collapse state), so switching orientation simply presents the same values in the arrangement appropriate to that window.
- **FR-013**: This feature MUST NOT alter any coordinate computation, GNSS metadata derivation, share/copy payload formatting, permission flow, or the foreground-only location lifecycle; it changes presentation only.

### Key Entities

*(No new data entities. This feature affects presentation and layout only; the existing position/GNSS snapshot and map state are unchanged.)*

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: On a device with the system navigation bar visible (including Android 16), 100% of readout rows and controls are fully visible and unobstructed in both portrait and landscape — zero clipped rows.
- **SC-002**: Every bottom-edge and side-edge control activates on first tap in a walkthrough of all controls in both orientations (no dead taps caused by system-bar overlap).
- **SC-003**: In landscape, the map occupies roughly 60% of the window width and the metadata panel roughly 40%, so a user can see a usable portion of the map and read the hero grid reference (untruncated) at the same time without rotating back to portrait.
- **SC-004**: Every readout value and interaction available in portrait is confirmed reachable and functional in landscape — no feature regression across orientation.
- **SC-005**: Rotating the device during use preserves position, map layers, follow-me state, and display mode in 100% of trials, with no crash on rotation.
- **SC-006**: On a display with a notch/cutout, no readout text or control is obscured by the cutout or rounded corners in either orientation.

## Assumptions

- The landscape metadata panel is placed on the **leading edge** (left in left-to-right locales), matching the user's suggestion; this is treated as the default rather than a separately negotiated design decision.
- "Modern Android versions / Android 16" refers to versions that enforce edge-to-edge rendering by default; the fix targets correct inset handling generally rather than any single OS version.
- The app remains a single primary screen with a map and a coordinate readout ("Layout A"); this feature reshapes that screen for orientation and insets and does not introduce new screens, values, or navigation.
- No user preference is persisted for orientation or panel side; the arrangement is derived automatically from the current window size (consistent with the app's existing no-persisted-preferences stance in v1).
- The map renders full-bleed beneath the system bars for an edge-to-edge look; only interactive/text content is constrained to the safe area.
- The landscape metadata panel is a fixed, always-visible side panel (~40% of window width) with internal scrolling — it does not adopt the portrait bottom sheet's collapse/expand behavior.
- Existing automated coverage of the pure coordinate core remains valid unchanged, since this feature does not touch that logic.
