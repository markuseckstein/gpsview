---
name: gpsview-map
labels: [wayfinder:map]
status: open
---

# GPSView — Wayfinder Map

## Destination

An **implementation-ready spec** for a personal Android app ("GPSView") that shows live details of the current location: coordinates in multiple systems (lat/lon, UTMREF/MGRS as used by Bavarian fire services, Plus Codes), GNSS metadata (accuracy, satellite counts, ellipsoidal **and** sea-level height), and the position on an open-source map with satellite imagery — battery-frugal and strictly foreground-only. The map is done when every design decision is resolved and the spec could be handed to a single build effort.

## Notes

Standing constraints, settled in the charting session (2026-07-09):

- **Stack:** Kotlin + Jetpack Compose, min SDK 34 (Android 14+), Google Play services allowed.
- **Distribution:** personal sideload only — loosest licensing constraints.
- **UI language:** German.
- **Coordinates:** lat/lon + UTMREF/MGRS; what3words only if offline conversion is genuinely possible, otherwise replaced by Plus Codes (Open Location Code).
- **Height:** show both ellipsoidal (raw GPS) and MSL/sea-level height, clearly labeled.
- **Behavior:** live updates while the app is visible, stopping instantly when backgrounded; tap-to-copy and share per coordinate format.
- Skills to use per ticket type: `/research` for research tickets, `/prototype` for prototype tickets, `/grilling` + `/domain-modeling` for grilling tickets.

**Tracker conventions (local markdown):** tickets are files in `wayfinder/tickets/`; frontmatter carries `status` (open/closed), `assignee` (the claim — empty means unclaimed), `labels`, and `blocked-by` (list of ticket file names). A ticket is on the frontier when open, unassigned, and everything in its `blocked-by` is closed. Resolutions are appended to the ticket file under `## Resolution`.

## Decisions so far

<!-- one line per closed ticket: gist + link -->

_None yet._

## Not yet specified

- **Map interaction details** — zoom/rotate/follow-me behavior, toggling between map and satellite layers, marker/accuracy-circle rendering. Sharpens once the map library and tile sources are chosen.
- **Offline behavior & tile caching** — whether/how tiles are cached for field use with no signal. Depends on imagery licensing findings.
- **Coordinate formatting details** — UTMREF precision (digit count), lat/lon display variants (decimal vs DMS), copy/share payload formats. Sharpens after conversion-library research and the UI prototype.
- **Permission & error UX** — first-run permission flow, GPS-off / permission-denied / no-fix states. Sharpens after the UI prototype.
- **Update cadence specifics** — exact request intervals and any adaptive throttling for the live-but-frugal requirement. Sharpens after the location-provider research.

## Out of scope

- Background location of any kind (explicit user requirement).
- Track recording, navigation/routing, POI search.
- Store or F-Droid publication, iOS/cross-platform.
- Building the app itself — execution happens after this map, against the finished spec.
