# Feature Specification: GPSView — Live Location Viewer

**Feature Branch**: `001-gpsview-android-app`

**Created**: 2026-07-11

**Status**: Draft

**Input**: User description: "I want to build an android app. Specification is quite good at SPEC.md"

**Source of truth**: This feature spec distills [SPEC.md](../../SPEC.md) (implementation-ready, assembled 2026-07-11) into user-facing scope. Where this document and SPEC.md could be read differently, SPEC.md governs the details; the project constitution governs both.

GPSView is a personal Android app that shows live details of the device's current location: coordinates in multiple systems (UTMREF/MGRS as used by Bavarian fire services, latitude/longitude, Plus Codes), position quality metadata (accuracy, satellite counts, ellipsoidal and sea-level height), and the position on a map with optional aerial imagery and an optional overlay of Bavarian cadastral parcel boundaries. It is battery-frugal and strictly foreground-only: all location activity stops the instant the app is no longer visible.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Live coordinate readout (Priority: P1)

As the user standing in the field (e.g. during a fire-service exercise), I open the app and immediately see my current position as a Bavarian-BOS-formatted UTMREF grid reference — the hero value — together with latitude/longitude, a Plus Code, both height values (ellipsoidal and above sea level), horizontal accuracy, and a satellite used/visible summary. The values update continuously (about once per second) while I watch.

**Why this priority**: This is the app's reason to exist. A user who can read an accurate, correctly formatted grid reference aloud already has the core value — even with no map on screen.

**Independent Test**: Grant location permission, stand outdoors, open the app; verify a grid reference in the form `32U NA 64846 21576` appears within a reasonable time, matches an independent reference tool, and updates as you walk.

**Acceptance Scenarios**:

1. **Given** location permission is granted and satellites are reachable, **When** the app is opened, **Then** within moments a live position appears with UTMREF (10-digit, space-grouped BOS convention), latitude/longitude (German decimal comma, 6 decimal places), Plus Code (full global code), both heights, horizontal accuracy (`±n m`), and satellite ratio (`18 / 24`).
2. **Given** a live position is displayed, **When** the device moves, **Then** all readouts update at roughly one-second cadence without user action.
3. **Given** a fix is available but a specific value is not (e.g. sea-level height cannot be derived), **When** the readout renders, **Then** that row shows an explicit dash — never a zero, stale, or fabricated value.
4. **Given** the user prefers degrees/minutes/seconds, **When** they toggle the lat/lon display mode, **Then** the row switches between decimal degrees and DMS, and the preference applies until changed within the current session (no preferences are persisted in v1).
5. **Given** no fix has been obtained yet, **When** the screen shows, **Then** a "Suche Satelliten…" acquiring state with dashed values and a `0/n` satellite ratio is displayed — clearly distinct from a live reading.

---

### User Story 2 - Position on a map (Priority: P2)

As the user, I see my position as a marker with a translucent accuracy circle on a street map that follows me as I move. I can zoom, drag away to look around (which pauses following), and tap a locate button to snap back to my position.

**Why this priority**: The map turns raw numbers into spatial context — "which side of the creek am I on" — and is the main visual surface of the chosen layout. It builds directly on the P1 position feed.

**Independent Test**: With a live fix, verify the marker sits at the true position, the accuracy circle matches the `±n m` readout, dragging the map stops auto-follow, and the locate button recentres and resumes following.

**Acceptance Scenarios**:

1. **Given** a first fix arrives, **When** the map initializes, **Then** the camera centres on the position at street-level zoom with follow-me active.
2. **Given** follow-me is active, **When** a new fix arrives, **Then** the camera recentres; **When** the user drags the map, **Then** follow-me disengages and the camera stays put on subsequent fixes.
3. **Given** follow-me is disengaged, **When** the user taps the locate button, **Then** the camera recentres on the current position and follow-me re-engages.
4. **Given** any map state, **Then** the map remains north-up — rotation and tilt are not possible.
5. **Given** the device has no connectivity, **When** the map cannot load tiles, **Then** the map area may be blank or show last-rendered tiles, but every coordinate readout continues to work.

---

### User Story 3 - Copy and share the position (Priority: P3)

As the user, I tap any value row to copy exactly that value, and use a single Share action to send a complete German-language position summary (grid reference, coordinates, Plus Code, heights, accuracy, timestamp, and a tappable maps link) through any messenger.

**Why this priority**: Relaying the position to someone else — dispatch, a colleague, a family member — is the most common follow-up action after reading it. It depends on P1 but not on the map.

**Independent Test**: Tap each row and paste the clipboard elsewhere; trigger Share into a messaging app and verify the block's content and that its link opens the correct location.

**Acceptance Scenarios**:

1. **Given** a live readout, **When** the user taps the UTMREF, Plus Code, or a height row, **Then** the displayed value is copied verbatim (e.g. `32U NA 64846 21576`, `8FWH4HX8+9C`, `487 m`) and a toast confirms it.
2. **Given** lat/lon is displayed in decimal mode (`48,137154°`), **When** the user taps that row, **Then** the clipboard receives the machine-pasteable form `48.137154, 11.575382` — dot decimals, `lat, lon`, no degree symbol; in DMS mode the DMS string is copied as displayed.
3. **Given** a live position, **When** the user taps the single Share action, **Then** a system share sheet sends one German text block with UTMREF first (at the precision currently displayed), dotted decimal coordinates, Plus Code, both heights, accuracy, the fix timestamp, and an `https` maps link that opens the position.

---

### User Story 4 - Aerial imagery and parcel boundaries (Priority: P4)

As the user in Bavaria, I toggle on official aerial photography and/or an overlay of cadastral parcel (Flurstück) boundaries to judge exactly where a position lies relative to property lines and terrain features.

**Why this priority**: High value for the fire-service and property use cases, but an enhancement layered onto the P2 map; the app is fully useful without it.

**Independent Test**: At a known Bavarian location, enable each layer independently and verify imagery appears, parcel outlines align with the base map, and each can be turned off again without affecting the other.

**Acceptance Scenarios**:

1. **Given** the map is shown, **When** the user opens the layer control, **Then** aerial imagery and parcel boundaries are two independent toggles, both off by default, with the street base map always present.
2. **Given** the parcel overlay is on, **When** the map is zoomed out beyond the level where individual parcels are legible, **Then** parcel lines are not shown; they appear at close zoom.
3. **Given** aerial imagery or the parcel overlay is visible, **Then** the attribution line "Bayerische Vermessungsverwaltung – www.geodaten.bayern.de" is displayed; the base map always carries its OpenStreetMap-derived attribution.
4. **Given** a position outside Bavaria, **When** the Bavarian layers are enabled, **Then** they simply show nothing there — this is accepted by design and must not break the map.

---

### User Story 5 - Trustworthy states and legal transparency (Priority: P5)

As the user, when something prevents a live reading — permission not granted, precise location refused, system location off — the app tells me exactly what is wrong in German and gives me a one-tap path to fix it. An "Über / Lizenzen" screen lists every component and data source with its license.

**Why this priority**: These states make the app dependable and compliant, but they frame the core experience rather than constitute it.

**Independent Test**: Walk through each permission/system state via device settings and verify the described behavior; open the licenses screen and check it against the app's actual components and data sources.

**Acceptance Scenarios**:

1. **Given** first launch, **When** the app starts, **Then** the system location permission is requested immediately, without a preceding explanation card.
2. **Given** the user declined once (re-askable), **Then** a rationale card with a retry button is shown; **Given** permission is permanently denied, **Then** the screen deep-links to the app's system settings.
3. **Given** only approximate location is granted, **Then** the app still functions with what it gets and shows a persistent, non-blocking banner „Genauer Standort empfohlen" with a button to re-request precise location.
4. **Given** the system location setting is off, **Then** a card over a dimmed map deep-links to the system location settings.
5. **Given** the About/Licenses screen is opened, **Then** every third-party component and data source is listed with its license and required attribution wording.

---

### Edge Cases

- **App leaves the foreground** (home button, task switch, screen off): every location registration is torn down immediately; no location activity continues in the background under any circumstances.
- **Connectivity lost**: all coordinate readouts keep working (computed on-device from the satellite fix); only map tiles degrade.
- **Grid-zone boundaries**: Bavaria spans four UTMREF grid zones (32T, 32U, 33T, 33U) around the 12°E / 48°N crossing — conversions must be correct in all four, including right at the seams.
- **Sea-level height unavailable or slow to derive**: the row shows a dash until a real value exists; deriving it must never block or delay the rest of the readout.
- **Satellite metadata absent** (e.g. indoors with a network-based fix): position rows still render; the satellite row shows its absence honestly.
- **Stale fix shared**: the share block always carries the fix timestamp, so an old position cannot masquerade as current.
- **Parcel service rejects or fails**: map and other layers remain functional; the overlay simply doesn't render.
- **Coarse-only fix accuracy**: the accuracy circle and `±n m` value make the imprecision visible instead of hiding it.

## Requirements *(mandatory)*

### Functional Requirements

**Position acquisition & lifecycle**

- **FR-001**: The app MUST show live position updates at approximately one-second cadence while it is visible.
- **FR-002**: The app MUST stop all location activity the instant it is no longer visible; it MUST NOT request background location capability and MUST NOT continue any location work when off-screen.
- **FR-003**: All coordinate values, heights, and codes MUST be computed on-device from the satellite fix, with no network dependency; loss of connectivity MUST NOT affect any readout.

**Coordinate presentation**

- **FR-004**: The app MUST display the position as a UTMREF/MGRS grid reference in the Bavarian BOS convention — space-separated groups *Zonenfeld · 100-km-Quadrat · Ostwert · Nordwert* — at 10-digit (1 m) precision by default, as the visually primary value.
- **FR-005**: The app MUST display latitude/longitude in decimal degrees with 6 decimal places and German decimal comma (e.g. `48,137154°`), with a user toggle to degrees/minutes/seconds.
- **FR-006**: The app MUST display the full global Plus Code (e.g. `8FWH4HX8+9C`), no locality short form.
- **FR-007**: The app MUST display both heights — ellipsoidal (WGS84) and above sea level (über NHN) — each labeled and each with its own vertical-accuracy sub-line.
- **FR-008**: The app MUST display horizontal accuracy (`±n m`) and a satellite summary as a used/visible ratio (e.g. `18 / 24`).
- **FR-009**: Any value that is unavailable MUST render as an explicit dashed row; the app MUST never show stale, zero, or fabricated values.

**Copy & share**

- **FR-010**: Every value row MUST be copyable with a single tap on the row itself, confirmed by a toast; the copied text is the displayed value verbatim, with one deliberate exception (FR-011).
- **FR-011**: Copying lat/lon in decimal mode MUST yield the machine-pasteable form `48.137154, 11.575382` — dot decimals, latitude-comma-longitude, no degree symbol — deviating from the displayed comma form; DMS mode copies the DMS string as displayed.
- **FR-012**: A single Share action MUST produce one German text block containing, in order: UTMREF (at currently displayed precision), dotted-decimal coordinates, Plus Code, both heights, accuracy, the fix timestamp, and a tappable `https` maps link to the position.

**Map**

- **FR-013**: The map MUST show the user's position as a marker with a translucent accuracy circle whose size reflects the reported horizontal accuracy.
- **FR-014**: Follow-me MUST be on by default: the camera tracks new fixes, dragging the map disengages tracking, and a locate button recentres and re-engages it. The first fix centres the map at street-level zoom.
- **FR-015**: The map MUST be locked north-up; rotation and tilt MUST NOT be possible.
- **FR-016**: The map MUST offer a street base layer (always on) plus two independent opt-in overlays, both off by default: official Bavarian aerial imagery and Bavarian cadastral parcel boundaries (outlines only, no parcel numbers).
- **FR-017**: The parcel overlay MUST NOT render at zoom levels where individual parcels are not legible; it appears only at close zoom.
- **FR-018**: Attribution MUST be visible for whichever layers are shown: the OpenStreetMap-derived line for the base map, and "Bayerische Vermessungsverwaltung – www.geodaten.bayern.de" whenever aerial imagery or parcels are visible.
- **FR-019**: The app MUST display provider tiles unmodified; it MUST NOT restyle, re-derive, or republish imagery or parcel geometry.

**States, language, and transparency**

- **FR-020**: The entire user interface MUST be in German, with German decimal commas in all displayed numbers.
- **FR-021**: On first launch the app MUST request location permission immediately (no pre-rationale). A re-askable denial shows a rationale card with retry; a permanent denial shows a deep-link to the app's system settings; a disabled system location setting shows a card with a deep-link to location settings over a dimmed map.
- **FR-022**: With only approximate location granted, the app MUST keep functioning with the data it gets and show a persistent, non-blocking banner recommending precise location, with a one-tap re-request.
- **FR-023**: Before the first fix, the app MUST show a distinct acquiring state ("Suche Satelliten…") with dashed values and the current `0/n` satellite ratio.
- **FR-024**: An "Über / Lizenzen" screen MUST list every third-party component and data source with its license and attribution wording.

### Key Entities

- **Position snapshot**: what the sensors actually reported at one instant — geographic coordinates, horizontal accuracy, ellipsoidal height, sea-level height, vertical accuracies, fix timestamp, satellite counts. Any field except the coordinates themselves may be absent. All displayed coordinate formats are derived from it on demand, never stored.
- **Coordinate representations**: the derived display/copy forms of one snapshot — BOS-grouped UTMREF (even digit counts 4/6/8/10 mapping to 1 km/100 m/10 m/1 m precision), decimal and DMS lat/lon, full Plus Code — plus the share block that combines them.
- **Screen state**: exactly one of — permission not yet requested, permanently denied, system location off, acquiring (no fix yet), or live (showing a snapshot). Field-level absence within "live" is expressed by dashed rows, not by extra states.
- **Map view state**: camera position, follow-me flag, and the two overlay toggles (aerial imagery, parcels); changes independently of the position feed.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Outdoors with clear sky and permission granted, a user sees a live grid reference within 30 seconds of opening the app (typically well under 10).
- **SC-002**: While the app is visible and moving, readouts refresh at least every 2 seconds.
- **SC-003**: The displayed UTMREF matches the official Bavarian fire-service worked example: the reference point near Gadheim (49.8431° N, 9.9019° E) renders as `32U NA 648 215` at 6-digit precision, and correctly at 10-digit; conversions are correct in all four Bavarian grid zones (32T, 32U, 33T, 33U).
- **SC-004**: Within 1 second of the app leaving the screen, no further location activity occurs — verifiable on-device.
- **SC-005**: A coordinate copied in decimal mode pastes into common map search fields and spreadsheet tools without any manual editing, resolving to the same position.
- **SC-006**: With airplane mode on (after a fix), 100% of coordinate readouts continue to display and update from the satellite fix.
- **SC-007**: Copying any displayed value takes exactly one tap; sharing the full position takes no more than two taps from the main screen.
- **SC-008**: At a known Bavarian location, enabled parcel outlines visually align with the corresponding base-map geometry, and the accuracy circle's on-map size corresponds to the numeric `±n m` readout.
- **SC-009**: Every legal attribution obligation is met whenever the corresponding layer is on screen, and the licenses screen lists 100% of shipped components and data sources.

## Assumptions

- **Single, personal user**: the app is sideloaded for personal use — no store distribution, no accounts, no analytics, no multi-user concerns.
- **Modern device**: only recent Android versions (Android 14+) need to be supported, per the standing project constraint.
- **Online base map is acceptable for v1**: map tiles require connectivity; offline tile caching is deferred (not forbidden) and must remain cleanly additive. Coordinate readouts are explicitly exempt from any connectivity need.
- **Bavaria-focused overlays**: aerial imagery and parcel boundaries cover Bavaria only; showing nothing elsewhere is accepted.
- **Out of scope for v1** (per SPEC.md §1): background location, track recording, navigation/routing, POI search, what3words, Gauß-Krüger coordinates, parcel number labels, heading indicator, per-constellation satellite breakdown.
- **Free public services**: the map and overlay services used are free, keyless public endpoints; their licenses (ODbL-derived attribution, CC BY 4.0) are honored via on-screen attribution and the licenses screen.
- **Precision default**: 10-digit (1 m) UTMREF is the right default because the app has a live satellite fix, unlike hand-plotted map readings where 6-digit is taught. In v1 the display is fixed at 10-digit — there is no precision control — so the share block always carries the 10-digit form.
