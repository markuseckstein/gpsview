# Contract: Pure Coordinates Core API

**Package**: `coordinates` — zero Android imports (constitution principle I, NON-NEGOTIABLE, is enforced here). Every function below is pure `input → value/String`, unit-tested with plain JUnit on the JVM. Exact Kotlin signatures may be refined at implementation time; the **behavioral contract and fixtures are binding**.

## API surface

```kotlin
// UTMREF / MGRS (SPEC.md §6.1)
enum class UtmrefPrecision(val digits: Int) { KM(4), M100(6), M10(8), M1(10) }  // even only — no odd forms exist
fun utmref(latLon: LatLon, precision: UtmrefPrecision = UtmrefPrecision.M1): String
// → BOS grouping: "Zonenfeld 100kmQuadrat Ostwert Nordwert", single spaces

// Lat/Lon (SPEC.md §6.2)
enum class LatLonMode { DECIMAL, DMS }
fun formatLatLonDisplay(latLon: LatLon, mode: LatLonMode): String   // German comma in DECIMAL
fun latLonCopyPayload(latLon: LatLon, mode: LatLonMode): String     // DECIMAL: dots, "lat, lon", no °

// Plus Code (SPEC.md §6.3)
fun plusCode(latLon: LatLon): String                                // full global code

// Heights (SPEC.md §6.4)
fun formatHeightDisplay(meters: Double?): String                    // value or dash
fun heightCopyPayload(meters: Double): String                       // "487 m"

// Share (SPEC.md §6.6)
fun shareBlock(snapshot: PositionSnapshot, utmrefPrecision: UtmrefPrecision, /* injected clock/format */): String
```

Rounding: displayed/copied heights round to whole meters; lat/lon to 6 decimal places; accuracy to whole meters (`±4 m`).

**Ownership**: each format file owns its display **and** copy functions (`LatLonFormat.kt`, `HeightFormat.kt`); UTMREF and Plus Code copy forms are the displayed strings verbatim, so they need no separate builders. `ShareFormatting.kt` owns **only** the share block.

**Precision in v1**: the UI always displays — and therefore always shares — the 10-digit form; there is no precision control. The `utmrefPrecision` parameter exists for tests and future use.

## Golden fixtures (binding test cases)

### UTMREF — known-good reference (Merkblatt 9.008 worked example)

EU geographic midpoint near Gadheim:

| Input | Precision | Expected |
|---|---|---|
| `49.8431 N, 9.9019 E` | 6-digit (100 m) | `32U NA 648 215` |
| `49.8431 N, 9.9019 E` | 10-digit (1 m) | `32U NA 6484x 2157x` — derive exact digits from the library, then freeze as fixture |

### UTMREF — zone-crossing coverage (mandatory, SPEC.md §9)

At least one fixture in **each** of Bavaria's four grid zones around the 12°E / 48°N crossing (near Regensburg/Cham); freeze exact expected strings from a trusted independent converter at test-authoring time:

| Zone | Example fixture area |
|---|---|
| 32U | Würzburg/Gadheim (above) |
| 32T | south of 48°N, west of 12°E (e.g. Garmisch ~47.49 N, 11.10 E) |
| 33U | north of 48°N, east of 12°E (e.g. Cham ~49.22 N, 12.66 E) |
| 33T | south of 48°N, east of 12°E (e.g. Berchtesgaden ~47.63 N, 13.00 E — note: Passau at ~48.57 N is 33U, not 33T) |

### Precision behavior

For one fixed point, assert all four precisions produce even-split easting/northing digit groups (2/2, 3/3, 4/4, 5/5) and that lower precision is truncation of the grid square, not rounding artifacts inconsistent with the library.

### Lat/lon display vs copy (SPEC.md §6.2, §6.5)

| Input | Mode | Display | Copy payload |
|---|---|---|---|
| `48.137154, 11.575382` | DECIMAL | `48,137154°` / `11,575382°` (per-row layout up to UI) | `48.137154, 11.575382` |
| same | DMS | `48° 8′ 13,75″ N` / `11° 34′ 31,38″ O` | identical to display |

**DMS format (binding)**: `D° M′ S,SS″ H` — unpadded integer degrees and minutes; seconds with exactly 2 decimal places and German comma; prime U+2032 and double prime U+2033; hemisphere letters `N`/`S` for latitude, `O`/`W` (Ost/West) for longitude.

DMS round-trip: decimal → DMS → decimal within 6-decimal-place tolerance.

### Plus Code

| Input | Expected |
|---|---|
| `48.137154, 11.575382` (Munich) | `8FWH4HX8+9C` |

### Share block (fixed timestamp injected)

Given a full snapshot (Munich fixture, ±4 m accuracy, heights 487/453 m, timestamp 2026-07-11 14:32, displayed precision 6-digit):

```text
Standort (GPSView)
UTMREF: 32U PU 034 926
Koordinaten: 48.137154, 11.575382
Plus Code: 8FWH4HX8+9C
Höhe: 487 m (ellipsoidisch) · 453 m (NHN)
Genauigkeit: ±4 m · 11.07.2026 14:32
https://www.google.com/maps?q=48.137154,11.575382
```

(The UTMREF line above is illustrative — freeze the exact Munich grid ref from the library at test-authoring time. Structure, ordering, labels, separators, dotted decimals, and link form are binding as shown; SPEC.md §6.6's example block governs.)

Absence handling in the share block: missing height/accuracy values render as dashes within their line — lines are never dropped, values never fabricated.

### Per-row copy payload summary (SPEC.md §6.5 — each is a test)

| Row | Copies |
|---|---|
| UTMREF | exactly as displayed, e.g. `32U NA 64846 21576` |
| Lat/lon (decimal) | `48.137154, 11.575382` |
| Lat/lon (DMS) | DMS string as displayed |
| Höhe rows | `487 m` |
| Plus Code | `8FWH4HX8+9C` |
