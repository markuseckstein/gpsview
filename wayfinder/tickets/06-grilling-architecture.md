---
name: Settle app architecture and module structure
labels: [wayfinder:grilling]
status: closed
assignee: claude
blocked-by:
  - 01-research-map-library-and-imagery.md
  - 02-research-gnss-metadata-and-height.md
  - 03-research-location-provider-and-battery.md
  - 04-research-coordinate-conversion-libraries.md
---

## Question

Given the chosen libraries and APIs, what is the app's architecture — state model, layering, and testability seams?

To resolve (HITL, `/grilling` + `/domain-modeling`):

- Domain model for a "position snapshot" (fix + derived coordinate representations + GNSS metadata) and its update flow into Compose state.
- Single-module vs light layering; where conversion logic lives so it's unit-testable offline.
- Error/absence states as domain concepts (no permission, GPS off, no fix yet, MSL unavailable).

Deliverable: resolution comment capturing the agreed architecture; feeds directly into the spec.

## Resolution

Settled via `/grilling` (HITL, 2026-07-11). The architecture is a **single Gradle `:app` module**, package-layered, with the raw/derived seam as the central testability boundary.

### Layering (single module, seam enforced by discipline, not a second Gradle module)

- **`coordinates` package — pure, zero Android imports.** Holds `LatLon`, `PositionFix`, the MGRS/UTMREF converters (`mil.nga:mgrs` + a thin BOS-spacing formatter — see [research 04](../research/04-coordinate-conversion-libraries.md)), the Plus Code converter (`com.google.openlocationcode`), and the copy/share string builders (`ShareFormatting`). Everything here is a pure `LatLon`/`Snapshot → String` function, unit-tested under `test/` with plain JUnit — no Robolectric, no device — against the Bavarian vectors (`32U NA 648 215`, the 12°E/48°N zone crossing).
- **`data` package — Android-dependent.** `FusedLocationProviderClient` and `LocationManager.registerGnssStatusCallback` (per [research 03](../research/03-location-provider-and-battery.md)) wrapped as `callbackFlow`s behind two interfaces, `LocationSource` and `GnssSource`. MSL enrichment (`AltitudeConverter`, [research 02](../research/02-gnss-metadata-and-height.md)) runs **inside the location flow**, so every emitted fix is already complete.
- **`ui` package.** Compose + ViewModel.

A second Gradle module (`:core-coordinates`) was considered and rejected: for a single-build personal app its only real gain — compiler-enforced Android-free purity — isn't worth the multi-module build config; promoting a package to a module later is cheap if reuse ever appears.

### State model

- **The snapshot holds only raw sensor truth** — WGS84 lat/lon, horizontal accuracy, ellipsoidal altitude (+ vertical accuracy), MSL altitude, timestamp, satellite counts. **No coordinate representation is stored**; UTMREF/MGRS, Plus Code, and formatted lat/lon are pure derived projections computed by the `coordinates` package as UI state is built.
- **`PositionUiState` — sealed screen states** (mutually exclusive, each renders a different screen per the Layout A prototype): `NotYetAsked`, `PermanentlyDenied`, `LocationOff`, `Acquiring`, `Live(snapshot)`.
- **Two-level absence rule:** absence of a *fix* is a top-level state; absence of a *field* is a nullable on the snapshot (`mslAltitude: Double?`, `satellites: SatelliteCount?`, `verticalAccuracy`, …), mirroring the platform's own `hasX()` guards ([research 02](../research/02-gnss-metadata-and-height.md)). This keeps `Live` rendering total — every null is a single dashed row.
- **`MapUiState` — a separate `StateFlow`**, owned by the same ViewModel but distinct from position state: independent layer toggles (base / satellite / parcel overlay), camera, and a `followMe` flag. They change for different reasons at different rates, so they are not fused. Follow-me is the one interaction point: it *gates* whether a new fix drives the camera (the map Composable moves the camera only when `followMe` is true).

### Update flow

Two cold flows (`LocationSource`, `GnssSource`) `combine`d in the ViewModel into `StateFlow<PositionUiState>`, consumed by Compose via `collectAsStateWithLifecycle()`. Lifecycle start/stop uses the research's `repeatOnLifecycle(Lifecycle.State.STARTED)` path for instant stop-when-backgrounded ([research 03](../research/03-location-provider-and-battery.md)). "Latest of each" comes free from `combine`; the GNSS source may be absent (satellite metadata null) without stalling the fix.

### Wiring & side effects

- **Manual constructor injection**, no DI framework. The ViewModel takes `LocationSource`/`GnssSource` interfaces via constructor; a `ViewModelProvider.Factory` wires the real Android-backed implementations; tests pass fakes. The interfaces *are* the enforced boundary keeping Android out of the merge logic. Hilt rejected as ceremony for a one-Activity/one-ViewModel app.
- **Copy/share:** the pure `coordinates` layer builds the string; the Composable does the Android act directly (`ClipboardManager.setPrimaryClip` / `ACTION_SEND`). No ViewModel event channel — the valuable test is on the string builder, which is pure either way.

### Testability seams (the payoff)

1. Converters + share-string builders — pure JVM tests, no device.
2. ViewModel `combine` merge — fed fake flows through the two source interfaces.

Both run with no Android runtime.

### Fog graduated by this resolution

The architecture fixes the *shape* of map interaction and permission/error UX but leaves their concrete values undecided (default zoom, rotate on/off, the exact denied/off/acquiring screens), and it placed — but did not fill — the share/format string builders. Those residual product/UX **decisions** (which an AFK spec-assembler must not invent) are graduated into a new HITL ticket, *Settle residual UX and product decisions*, now blocking the spec.
