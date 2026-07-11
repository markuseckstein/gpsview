# Data Model: GPSView — Live Location Viewer

**Date**: 2026-07-11 · **Source**: [SPEC.md §5](../../SPEC.md), [spec.md Key Entities](spec.md)

GPSView has no persistence — every entity below is an in-memory value. The load-bearing design rule (constitution I, III): **the snapshot stores raw sensor truth only**; every coordinate representation is a pure derived projection computed by the `coordinates` package when UI state is built. Field-level absence is a nullable field rendered as a dashed row; fix-level absence is a distinct top-level UI state ("two-level absence rule").

## Core value types (`coordinates` package — pure, zero Android imports)

### LatLon

WGS84 geographic coordinate.

| Field | Type | Constraints |
|---|---|---|
| `latitude` | `Double` | −90.0 … 90.0 (degrees) |
| `longitude` | `Double` | −180.0 … 180.0 (degrees) |

### PositionSnapshot

One instant of sensor truth. Only `latLon` and `timestamp` are guaranteed; everything else is nullable — populated only when the platform reported it (each getter guarded by its `has…()` check; SPEC.md §5.1).

| Field | Type | Source & guard |
|---|---|---|
| `latLon` | `LatLon` | `location.latitude/longitude` |
| `horizontalAccuracyM` | `Float?` | `getAccuracy()` iff `hasAccuracy()` — 68th-percentile radius |
| `ellipsoidalAltitudeM` | `Double?` | `getAltitude()` iff `hasAltitude()` — always WGS84 ellipsoid |
| `verticalAccuracyM` | `Float?` | `getVerticalAccuracyMeters()` iff `hasVerticalAccuracy()` |
| `mslAltitudeM` | `Double?` | `getMslAltitudeMeters()` iff `hasMslAltitude()` — after in-flow enrichment |
| `mslAccuracyM` | `Float?` | `getMslAltitudeAccuracyMeters()` iff `hasMslAltitudeAccuracy()` |
| `timestamp` | `Long` (epoch ms) | `location.time` |
| `satellites` | `SatelliteCount?` | from `GnssSource`; null until first status arrives |

**Invariants**:
- No formatted strings, no derived coordinates, no display preferences on this type.
- A field that is null stays null — never defaulted to 0 or carried over from a previous fix.

### SatelliteCount

| Field | Type | Constraints |
|---|---|---|
| `used` | `Int` | 0 ≤ used ≤ visible |
| `visible` | `Int` | ≥ 0 |

### Derived representations (computed, never stored)

Pure functions of `PositionSnapshot` (+ display preferences); full signatures and golden fixtures in [contracts/coordinates-api.md](contracts/coordinates-api.md).

| Representation | Derived from | Notes |
|---|---|---|
| UTMREF/BOS string | `latLon` + precision | even digits 4/6/8/10 → 1 km/100 m/10 m/1 m; default 10; grouping `32U NA 64846 21576` |
| Lat/lon display string | `latLon` + mode (decimal/DMS) | decimal: 6 places, German comma, `°`; DMS per mode |
| Lat/lon copy payload | `latLon` + mode | decimal mode: `48.137154, 11.575382` (dots — the one deviation); DMS mode: as displayed |
| Plus Code | `latLon` | full global code |
| Height row strings + copy payloads | altitude fields | bare `487 m` copy form; dash when null |
| Share block | whole snapshot + displayed UTMREF precision + clock formatting | German block, UTMREF first, `https` maps link, timestamp (SPEC.md §6.6) |

## UI state (`ui` package)

### PositionUiState — sealed screen state

Exactly one at a time (SPEC.md §5.2):

```kotlin
sealed interface PositionUiState {
    data object NotYetAsked : PositionUiState        // permission not yet requested
    data object PermanentlyDenied : PositionUiState  // denied with "don't ask again"
    data object LocationOff : PositionUiState        // system location setting off
    data class Acquiring(val satellites: SatelliteCount?) : PositionUiState
                                                     // permission granted, no fix yet;
                                                     // carries the live 0/n ratio for the acquiring UI
    data class Live(val snapshot: PositionSnapshot) : PositionUiState
}
```

**State transitions**:

```text
NotYetAsked ──permission granted──────────────▶ Acquiring
NotYetAsked ──denied (re-askable)─────────────▶ NotYetAsked (+ rationale card variant)
NotYetAsked ──denied permanently──────────────▶ PermanentlyDenied
PermanentlyDenied ──granted via settings──────▶ Acquiring
Acquiring ──system location turned off────────▶ LocationOff
LocationOff ──setting re-enabled──────────────▶ Acquiring
Acquiring ──first fix emitted─────────────────▶ Live(snapshot)
Live ──new fix / satellite update─────────────▶ Live(new snapshot)   // via combine
Live ──location turned off────────────────────▶ LocationOff
any ──ON_STOP─────────────────────────────────▶ collection cancelled (state frozen, sources torn down)
ON_START──────────────────────────────────────▶ re-evaluate permission/setting → resume
```

Notes:
- Coarse-only grant is **not** a state: the app functions on coarse fixes (`Acquiring`/`Live`) with a persistent non-blocking banner flag alongside.
- `Live` rendering is total: any nullable snapshot field renders as a dashed row; there is no "partial fix" state.
- Satellite updates before the first fix update `Acquiring(satellites)` — the acquiring UI renders the live `0/n` ratio; after the first fix they produce a new `Live` via `combine` (latest-of-each).

### MapUiState — separate StateFlow, same ViewModel

Changes for different reasons at different rates than position (SPEC.md §5.3).

| Field | Type | Default | Rules |
|---|---|---|---|
| `satelliteVisible` | `Boolean` | `false` | DOP20 raster layer visibility toggle |
| `parcelsVisible` | `Boolean` | `false` | ALKIS raster layer visibility toggle |
| `followMe` | `Boolean` | `true` | camera moves to new fixes only while true; drag → false; locate button → true + recentre |
| camera position | (map library type) | first fix @ ~z16 | north-up locked; rotation/tilt disabled |

### Display preferences (UI state, not snapshot)

| Preference | Values | Default |
|---|---|---|
| lat/lon display mode | decimal ⇄ DMS | decimal |

UTMREF precision is **not** a preference in v1: the UI always displays — and therefore always shares — the 10-digit form (no precision control exists on any screen). The pure API's precision parameter (4/6/8/10) remains for tests and future use.

Not persisted in v1 — process-lifetime only.

## Data-layer interfaces (`data` package — the enforced boundary)

Contracts in [contracts/location-sources.md](contracts/location-sources.md):

- **`LocationSource`** — `fun positions(): Flow<PositionSnapshot>`, always emitted with `satellites = null`; the ViewModel merges in the latest `SatelliteCount` (cold; FLP-backed; MSL-enriched before emission)
- **`GnssSource`** — `fun satellites(): Flow<SatelliteCount>` (cold; GnssStatus-backed; may never emit)

The ViewModel `combine`s these into `StateFlow<PositionUiState>`; tests substitute fakes through the constructor.
