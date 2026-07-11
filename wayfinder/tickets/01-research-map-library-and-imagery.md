---
name: Research map library and satellite imagery sources
labels: [wayfinder:research]
status: closed
assignee: claude
blocked-by: []
---

## Question

Which open-source map component should GPSView use, and which tile sources for the map and satellite layers?

To resolve (AFK research, `/research`):

- Compare **MapLibre GL Native (Android)** vs **osmdroid** for a Compose app on min SDK 34: Compose interop, raster + vector support, layer switching, maintenance health, footprint.
- Map layer: OpenStreetMap-based source whose tile-usage policy permits a personal app (OSM's own tile servers have restrictive usage policies — check alternatives like OpenFreeMap or self-styled vector tiles).
- Satellite layer: find a usable orthophoto/satellite source for Bavaria. Specifically evaluate the **Bavarian open-data DOP (Digitale Orthophotos) WMS/WMTS** from the LDBV geoportal (open data since 2021?) and Esri World Imagery terms for a personal sideloaded app.
- Note licensing/attribution obligations for each candidate.

Deliverable: markdown summary linked here, with a recommendation.

## Resolution

Full findings: [wayfinder/research/01-map-library-and-imagery.md](../research/01-map-library-and-imagery.md).

- **Map library:** MapLibre Native (Android SDK, `org.maplibre.gl:android-sdk`, BSD-2-Clause) via the official `maplibre-compose` wrapper (BSD-3-Clause). osmdroid is archived/dead (2024-11-20), raster-only, no Compose support — ruled out.
- **Map tile source:** OpenFreeMap (openfreemap.org) — no API key, no caps, free, self-hostable, vector tiles native to MapLibre. `tile.openstreetmap.org` ruled out — its own usage policy forbids offline/bulk app use.
- **Satellite tile source:** Bavarian DOP via LDBV/Bayern Open Data, WMS `https://geoservices.bayern.de/od/wms/dop/v1/dop20`, no auth, 20cm resolution, CC BY 4.0. Preferred over Esri World Imagery (unverified commercial ToS, requires ArcGIS account).
- **Attribution obligations to implement:** persistent map-corner attribution — "OpenFreeMap © OpenMapTiles Data from OpenStreetMap" (base layer) and "Bayerische Vermessungsverwaltung – www.geodaten.bayern.de" (satellite layer, CC BY 4.0) — plus an About/Licenses screen listing MapLibre, maplibre-compose, OSM/ODbL, and the DOP license.
- Two items flagged for a pre-build check rather than resolved here: `maplibre-compose`'s exact raster/satellite-layer-toggle API surface, and the historical-DOP WMS endpoint if that layer is ever used.
