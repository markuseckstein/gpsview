---
name: Research Bavarian cadastral parcel overlay integration
labels: [wayfinder:research]
status: closed
assignee: claude
blocked-by: []
---

## Question

How should GPSView integrate Bavaria's free cadastral parcel data (Flurstücke boundaries, no Flurstücksnummer/Flurnummer labels — see the map's Notes) as an optional overlay layer?

To resolve (AFK research, `/research`):

- Confirm the actual free/open service (WMS/WMTS endpoint, format) for the **ALKIS-Parzellarkarte** on Bayern's OpenData portal (geodaten.bayern.de/opengeodata) — this is the boundaries-only variant without parcel-number labels; the labeled ALKIS-Flurkarte is a separate, paid LDBV/GeodatenOnline product and stays out of scope.
- Confirm license and required attribution text (expected CC BY 4.0, per the OpenData portal).
- Confirm how this layer combines with the map library and base/satellite layers chosen in [Research map library and satellite imagery sources](01-research-map-library-and-imagery.md) — MapLibre Native + `maplibre-compose`, with OpenFreeMap as the base layer and the Bavarian DOP WMS as the satellite layer. Can the parcel overlay be toggled independently as a third layer, and does it work as a MapLibre raster source like the DOP layer does?

Deliverable: markdown summary linked here, naming the endpoint, format, license/attribution text, and integration approach the spec should mandate.

## Resolution

Full findings: [wayfinder/research/08-cadastral-parcel-overlay.md](../research/08-cadastral-parcel-overlay.md).

- **Endpoint:** WMS `https://geoservices.bayern.de/od/wms/alkis/v1/parzellarkarte`, no auth, Bavaria-wide single endpoint (same integration pattern as the DOP satellite layer).
- **Layer/format:** `by_alkis_parzellarkarte_umr_schwarz` or `_umr_gelb` (outline-only, purpose-built for overlaying) — `image/png` with confirmed real alpha transparency, `STYLES` param mandatory, EPSG:3857 natively supported.
- **Integration:** third MapLibre `raster` source/layer, stacked above the base and satellite layers, toggled independently via the standard `visibility` layout property — confirmed against MapLibre's own style spec. Suggested `minzoom` gating (~z16–17) matching the provider's "optimized for 1:1000" guidance.
- **Attribution:** same portal-wide text already used for the DOP layer — "Bayerische Vermessungsverwaltung – www.geodaten.bayern.de" — shown once whenever either Bavarian layer is active.
- **License discrepancy found and flagged, not resolved here:** `GetCapabilities`/OpenData JSON say CC BY 4.0 (looks like an unedited portal-wide default — identical across all 34 products), but the dedicated LDBV product page explicitly singles this product out as **CC BY-ND 4.0**. Doesn't change what GPSView builds (unmodified tile display is "sharing" under either variant), but the About/Licenses screen text depends on which is correct. See the new follow-up task ticket for confirming this before the spec is finalized.
