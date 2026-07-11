---
name: Research map library and satellite imagery sources
labels: [wayfinder:research]
status: open
assignee:
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
