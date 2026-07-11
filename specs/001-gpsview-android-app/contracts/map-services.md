# Contract: External Map & Tile Services

GPSView consumes three external services, all keyless. Declaration order = paint order (SPEC.md §8.1). Each raster overlay toggles via the standard `visibility` layout property — independent, no style reload. GPSView displays tiles **unmodified** — no re-styling, re-derivation, or republication (constitution VI).

## 1. Base map — OpenFreeMap vector tiles (always on)

| Aspect | Value |
|---|---|
| Service | OpenFreeMap public instance, published MapLibre style URL |
| Style | most neutral available (Liberty-class); **confirm exact URL from openfreemap.org at build time** |
| Auth / caps | none / none |
| License / attribution | tiles free; data ODbL → **"OpenFreeMap © OpenMapTiles Data from OpenStreetMap"** (covers the "© OpenStreetMap contributors" obligation); auto-added by MapLibre's attribution control |

## 2. Aerial imagery — Bavarian DOP20 orthophotos (opt-in, default off)

| Aspect | Value |
|---|---|
| Endpoint | `https://geoservices.bayern.de/od/wms/dop/v1/dop20` |
| Protocol | WMS 1.1.1/1.3.0 `GetMap`, TLS 1.2, no authentication |
| MapLibre wiring | `raster` source, templated URL with `{bbox-epsg-3857}` |
| Coverage | Bavaria only — blank elsewhere, accepted by design |
| License / attribution | CC BY 4.0 → **"Bayerische Vermessungsverwaltung – www.geodaten.bayern.de"** whenever visible |

## 3. Parcel overlay — ALKIS-Parzellarkarte (opt-in, default off)

Template (line breaks for readability only):

```text
https://geoservices.bayern.de/od/wms/alkis/v1/parzellarkarte
  ?SERVICE=WMS&VERSION=1.3.0&REQUEST=GetMap
  &LAYERS=by_alkis_parzellarkarte_umr_gelb&STYLES=Gelb
  &CRS=EPSG:3857&BBOX={bbox-epsg-3857}
  &WIDTH=256&HEIGHT=256&FORMAT=image/png&TRANSPARENT=TRUE
```

| Aspect | Value |
|---|---|
| **`STYLES` is mandatory** | requests without it are rejected (`MissingParameterValue`) |
| Layer/style | yellow outlines (`umr_gelb`/`Gelb`) first; sanctioned alternative `umr_schwarz`/`Schwarz` if visual testing prefers (empirical check E7) |
| `minzoom` | ≈ 16–17 on the source/layer — no parcel lines before individual parcels are legible |
| Content | outline-only (no Flurstück numbers — labeled Flurkarte is a paid product, out of scope) |
| Transparency | genuine alpha (live-verified) |
| Scope | single Bavaria-wide endpoint, no district/tiling logic |
| License / attribution | **CC BY 4.0, confirmed with LDBV Kundenservice** ([ticket 09](../../../wayfinder/tickets/09-task-confirm-parzellarkarte-license.md)) — same attribution line as DOP20; one line covers both Bavarian layers |

## Attribution rules (runtime obligations)

| Visible layers | Required attribution |
|---|---|
| base only | "OpenFreeMap © OpenMapTiles Data from OpenStreetMap" |
| + satellite and/or parcels | additionally "Bayerische Vermessungsverwaltung – www.geodaten.bayern.de" |

Persistent small attribution corner; use MapLibre's built-in attribution control where possible.

## Failure behavior

- No connectivity: map shows blank/last-rendered tiles; **all coordinate readouts keep working** (constitution III).
- WMS errors/rejections: the affected overlay silently doesn't render; base map and app remain functional.

## Über / Lizenzen screen content (SPEC.md §11)

| Component / data | License | Note |
|---|---|---|
| MapLibre Native | BSD-2-Clause | |
| maplibre-compose | BSD-3-Clause | |
| `mil.nga:mgrs`, `mil.nga:grid` | MIT | NGA-authored |
| `com.google.openlocationcode` | Apache-2.0 | |
| OpenStreetMap data | ODbL | "© OpenStreetMap contributors" |
| OpenFreeMap tiles | free service | "OpenFreeMap © OpenMapTiles Data from OpenStreetMap" |
| Bavarian DOP20 orthophotos | CC BY 4.0 | "Bayerische Vermessungsverwaltung – www.geodaten.bayern.de" |
| ALKIS-Parzellarkarte | CC BY 4.0 (LDBV-confirmed) | same attribution line as DOP20 |
