# Research: Bavarian cadastral parcel overlay (ALKIS-Parzellarkarte)

Ticket: `wayfinder/tickets/08-research-cadastral-parcel-overlay.md`
Researched: 2026-07-11
Context: personal, sideload-only Android app, Kotlin + Jetpack Compose, min SDK 34. Builds on the stack settled in `wayfinder/research/01-map-library-and-imagery.md` (MapLibre Native + `maplibre-compose`, OpenFreeMap vector base layer, Bavarian DOP WMS satellite layer). This research adds a third, independently-toggleable overlay: the free/open **ALKIS®-Parzellarkarte** (parcel boundaries, no Flurstücksnummer labels). The paid, fully-labeled **ALKIS®-Flurkarte** is explicitly out of scope and was not re-investigated.

> Methodology note: all claims below are backed by a primary source fetched directly during this session — the live WMS `GetCapabilities` XML, the JSON data file that actually backs the `geodaten.bayern.de/opengeodata` product page, the LDBV product page, the Bavarian Open Data Terms of Use page, and MapLibre's own style-spec docs — with an inline URL next to each claim. Live `GetMap` test requests were also issued against the production endpoint to verify real behavior (transparency, scale handling), not just documented behavior. Where sources conflict or something could not be verified, this is flagged explicitly rather than guessed.

---

## 1. Finding the actual service endpoint

### The `OpenDataDetail.html?pn=parzellarkarte` page is a JS-rendered SPA — the real data lives in a JSON file

The page at https://geodaten.bayern.de/opengeodata/OpenDataDetail.html?pn=parzellarkarte does not contain the product metadata in its static HTML; it loads `opendatadetail.js`, which in turn fetches `https://geodaten.bayern.de/opengeodata/json/opengeodata_datensaetze.json` and filters it by the `pn=` query-string product name (confirmed by reading `opendatadetail.js` directly: `const OPENDATA_JASON = 'json/opengeodata_datensaetze.json';`). Fetching that JSON directly and filtering for `produkt_produktname == "parzellarkarte"` gives the authoritative, structured product record — effectively the same primary source the page renders, just without needing a JS engine.

That JSON record confirms three delivery mechanisms for the Parzellarkarte, all "bayernweit" (statewide) and updated daily (`abgabe_datenaktualitaet: "täglich"`):

| Mechanism | Endpoint | Format(s) | SRS |
|---|---|---|---|
| **WMS** (recommended for GPSView) | `https://geoservices.bayern.de/od/wms/alkis/v1/parzellarkarte?` | PNG, JPEG | EPSG:25832, 25833, 31468, 4326, 4258, **3857** |
| WMTS (bundled, not standalone) | `https://geoservices.bayern.de/od/wmts/geobasis/v1/1.0.0/WMTSCapabilities.xml` | PNG | EPSG:25832, 31468, 3857 |
| Bulk polygon download | `https://geoservices.bayern.de/services/poly2metalink/metalink/parzellarkarte?data=parzellarkarte&service=polygon` | GeoTIFF (1km×1km tiling) | EPSG:25832 |

Source: `https://geodaten.bayern.de/opengeodata/json/opengeodata_datensaetze.json` (fetched directly 2026-07-11).

**The WMTS variant is not a standalone parcel layer.** Per the same JSON record's own description: "die ALKIS®-Parzellarkarte steht in den beiden Layern 'Beschriftungen Bayern' und 'Topographische Karten Bayern' in den größeren Zoomstufen zur Verfügung" — i.e. the parcel lines are baked into two general-purpose basemap WMTS layers at high zoom, not exposed as an independently-togglable parcels-only layer. This rules out WMTS for GPSView's use case (an *optional*, independently toggleable overlay) — **WMS is the right choice**, matching the pattern already used for the DOP satellite layer.

### WMS `GetCapabilities`, fetched live

Fetched directly via `curl` against `https://geoservices.bayern.de/od/wms/alkis/v1/parzellarkarte?REQUEST=GetCapabilities&SERVICE=WMS&VERSION=1.3.0` (2026-07-11, HTTP 200, `text/xml`):

- **Title**: "WMS BY ALKIS-Parzellarkarte".
- **Abstract** (verbatim): "Der ALKIS®-Parzellarkarte-WMS ist nach dem Vorbild der ALKIS®-Flurkarte gebaut. Dieser beinhaltet Objekte der Flurkarte (Gebäude, Lagebezeichnungen, TN-Objekte), jedoch keine Flurstücksnummern und keine Grenzzeichen. Zudem werden alle Flurstücksgrenzen einheitlich als durchgezogene Linie dargestellt." — confirms exactly the "boundaries only, no parcel numbers" product framing from the ticket.
- **Fees**: `kostenfrei` (free).
- **AccessConstraints**: `CC BY 4.0 vgl. https://creativecommons.org/licenses/by/4.0/deed.de` (see the license discrepancy flagged in section 2 below — this field conflicts with the LDBV product page).
- **MaxWidth/MaxHeight**: 6000×6000 pixels per `GetMap` request.
- **WMS versions**: 1.1.1 and 1.3.0 (from `<WMS_Capabilities version="1.3.0">` and the FAQ-equivalent geodatenonline page).
- **GetMap formats offered**: `image/png`, `image/jpeg`, `image/tiff`.
- **Four layers** under the parent "ALKIS-Parzellarkarte (BVV)" layer:
  - `by_alkis_parzellarkarte_farbe` — full-color rendering (fill + lines), style name `Farbe`.
  - `by_alkis_parzellarkarte_grau` — grayscale rendering, style name `SW`.
  - `by_alkis_parzellarkarte_umr_gelb` — **yellow outline only, no fill** ("Dieser Layer dient zur Überlagerung mit anderen Informationen" — "this layer is intended for overlaying with other information" — explicitly designed for exactly GPSView's use case, e.g. over the DOP satellite layer), style name `Gelb`.
  - `by_alkis_parzellarkarte_umr_schwarz` — **black outline only, no fill**, same overlay intent, style name `Schwarz`.
- **CRS supported per layer**: EPSG:25832, 25833, 5678, 31468, 4258, 4326, **3857** — same list as the JSON record, confirms **EPSG:3857 (Web Mercator) is natively supported**, which is what MapLibre's default projection uses.
- **Geographic bounding box** (from `EX_GeographicBoundingBox`): lon 8.976–14.015°E, lat 47.180–50.570°N — this is the full extent of the state of Bavaria, served from the single endpoint (see section 5).
- **`MinScaleDenominator`/`MaxScaleDenominator`**: 0–5000 on every layer. This is a *recommendation for optimal legibility* ("Die Darstellung ist für den Maßstab 1:1000 optimiert"), **not a hard server-side cutoff** — see the live test in section 3, which shows the service still returns rendered content well past 1:5000.
- Each layer also exposes a `LegendURL` (PNG legend graphic) and an ISO 19115 `MetadataURL` pointing at `geoportal.bayern.de`'s CSW catalogue.

Full captured XML: `/tmp/claude-1000/.../scratchpad/parzellarkarte_capabilities.xml` (local scratch copy of the live fetch, not committed to the repo).

### Cross-check against the `geodatenonline.bayern.de` landing page

A search for the DOP-equivalent landing page pattern turned up **https://geodatenonline.bayern.de/geodatenonline/seiten/wms_alkis_parzellarkarte**, which independently states the same endpoint (`https://geoservices.bayern.de/od/wms/alkis/v1/parzellarkarte`), the same four layers, the same CRS list including EPSG:3857, and "no authentication required" / TLS 1.2 required — consistent with the `GetCapabilities` document fetched directly. (Also found on the same domain, but out of scope per the ticket: `wms_alkis_flurkarte` — the paid, labeled variant — and `wms_alkis_planungskarte`, an unrelated planning-map product.)

---

## 2. License and attribution — **a genuine conflict between two primary sources, flagged rather than guessed away**

Three primary sources were checked, and they do not fully agree:

1. **WMS `GetCapabilities` `AccessConstraints`** (fetched live, see section 1): `"CC BY 4.0 vgl. https://creativecommons.org/licenses/by/4.0/deed.de"`.
2. **`opengeodata_datensaetze.json`** (the JSON backing the `OpenDataDetail.html?pn=parzellarkarte` page, fetched live): `"produkt_lizenz": "CC BY 4.0|https://creativecommons.org/licenses/by/4.0/deed.de"` for all three Parzellarkarte delivery mechanisms (WMS, WMTS, bulk download).
3. **The LDBV product page, https://www.ldbv.bayern.de/produkte/liegenschaftsinformationen/parzellarkarte.html** (fetched live 2026-07-11) states explicitly, as a called-out notice specific to this one product: *"Bitte beachten Sie, dass die Parzellarkarte unter der Lizenz **CC BY-ND 4.0** steht. Wenn Sie das Material remixen, verändern oder darauf anderweitig direkt aufbauen, dürfen Sie die bearbeitete Fassung des Materials nicht verbreiten."* ("Please note that the Parzellarkarte is licensed under CC BY-ND 4.0. If you remix, alter, or otherwise directly build upon the material, you may not distribute the modified version.")

**Why source 2 is likely not authoritative for this specific question:** every single one of the 34 distinct product names in `opengeodata_datensaetze.json` (DOP, DGM, DOM, ATKIS, Flurkarte-adjacent products, etc.) carries the identical literal string `"CC BY 4.0|https://creativecommons.org/licenses/by/4.0/deed.de"` with zero variation — this looks like a portal-wide default value rather than a per-product legal determination. This matches the Bavarian Open Data Terms of Use page's own framing, fetched from https://www.geodaten.bayern.de/odd/m/3/html/nutzungsbedingungen.html (2026-07-11), which documents **two possible licenses** in its portfolio — CC BY 4.0 *and* CC BY-ND 4.0 — and explicitly instructs: *"Welche Lizenz für den jeweiligen Datensatz gilt, ist aus den Produktinformationen oder der Gebühren- und Preisliste... zu entnehmen"* ("which license applies to a given dataset must be taken from the product information or the fee schedule"). The LDBV product page's explicit, singled-out ND callout for exactly this product looks like the "product information" that sentence is pointing to, whereas the generic OpenData JSON/`GetCapabilities` field appears to just inherit a portal-wide placeholder.

**Practical consequence for GPSView is small either way.** Both CC BY 4.0 and CC BY-ND 4.0 permit sharing/redistributing the material for any purpose, including commercial use, with attribution (per the same Terms of Use page's plain-language summary of both licenses). The only difference is CC BY-ND additionally forbids distributing a *modified/remixed* version. GPSView's planned use — requesting WMS tiles and compositing them, unmodified, as a raster overlay in a map view — is "sharing," not "remixing," under either license, so **this conflict does not block the integration**. It does mean GPSView must not, e.g., re-style/re-color/vectorize the parcel geometries into a new derived dataset and redistribute that. **Flagged for a human to double-check** (e.g. a quick email to `service@geodaten.bayern.de`, the Kundenservice contact listed in the `GetCapabilities` document) before treating CC BY 4.0 as settled for this specific product — this research treats **CC BY-ND 4.0 as the operative, more conservative assumption** for the About/Licenses screen.

### Attribution text

The required attribution wording is **not product-specific** — it's the single, portal-wide wording from the Bavarian Open Data Terms of Use page, which applies "als Rechteinhaberin" (as rights holder) regardless of which CC variant governs a given dataset (https://www.geodaten.bayern.de/odd/m/3/html/nutzungsbedingungen.html, verbatim):

> **"Bayerische Vermessungsverwaltung – www.geodaten.bayern.de"**

This is the exact same attribution string already used for the DOP satellite layer in `wayfinder/research/01-map-library-and-imagery.md` — one shared attribution credit covers both Bavarian Open Data layers.

---

## 3. Format, transparency, and CRS — verified with live `GetMap` requests, not just documentation

A `GetMap` request was issued directly against the production endpoint to verify real (not just documented) behavior:

```
https://geoservices.bayern.de/od/wms/alkis/v1/parzellarkarte
  ?SERVICE=WMS&VERSION=1.3.0&REQUEST=GetMap
  &LAYERS=by_alkis_parzellarkarte_umr_schwarz&STYLES=Schwarz
  &CRS=EPSG:3857&BBOX=1291000,6120000,1291200,6120200
  &WIDTH=512&HEIGHT=512&FORMAT=image/png&TRANSPARENT=TRUE
```

Result (fetched 2026-07-11): HTTP 200, `Content-Type: image/png`, and the returned file is confirmed (via Pillow) to be **`RGBA` mode with a genuinely varying alpha channel (0–255)** — i.e. this is a real transparent-background PNG with only the black parcel outlines opaque, not a flattened white/solid background. This directly confirms the layer is suitable to sit **on top of** another raster (DOP) or vector (OpenFreeMap) layer without obscuring it.

Two things worth noting from this live testing, beyond what the docs alone would tell you:

- **`STYLES` is a required parameter**, not optional — a request without it returns a `ServiceExceptionReport` (`MissingParameterValue`, locator `STYLES`). Any WMS raster-source URL template in the MapLibre style must include `&styles=Farbe` (or `Gelb`/`Schwarz`/`SW` depending on which of the four layers is chosen).
- **The documented `MaxScaleDenominator: 5000` is not enforced server-side.** A second live test at a coarser scale (1km×1km bbox at 512×512px, roughly 1:7000, past the documented "optimized for 1:1000" scale and past the stated 5000 max) still returned a valid, non-blank PNG (~10% of pixels non-transparent, i.e. parcel lines were still rendered). This is a soft rendering-quality recommendation from the data provider, not a hard cutoff GPSView needs to code around — though it's still sensible product advice: set a `minzoom` on the MapLibre raster layer so parcel-boundary requests aren't fired (and don't visually clutter the map) until the user is zoomed in close enough for individual parcels to be legible, matching the provider's own stated "optimized for 1:1000" guidance.

**Recommended layer for GPSView**: `by_alkis_parzellarkarte_umr_schwarz` (black outline, `STYLES=Schwarz`) or `by_alkis_parzellarkarte_umr_gelb` (yellow outline, `STYLES=Gelb`) — both are explicitly documented by LDBV as built "zur Überlagerung mit anderen Informationen" (for overlaying with other information), unlike the filled `farbe`/`grau` variants which are meant to stand alone as a full basemap. Yellow is likely the better choice for legibility against both the OpenFreeMap vector base (varied colors) and the DOP orthophoto (naturalistic greens/browns) — a design decision to confirm visually during implementation, not something a primary source can settle.

**CRS confirmed**: EPSG:3857 is natively supported by the WMS (present in both the `GetCapabilities` CRS list and successfully used in the live `GetMap` test above) — no reprojection needed for MapLibre's default Web Mercator tiling.

---

## 4. MapLibre layering mechanics — confirmed from the style spec itself

Fetched directly from MapLibre's own style specification (2026-07-11):

- **Raster source type** (https://maplibre.org/maplibre-style-spec/sources/#raster): supports `tiles` (an array of templated tile URLs, TileJSON-style), `tileSize`, `minzoom`, `maxzoom`, `bounds`, and `scheme`. The spec's own example (https://maplibre.org/maplibre-style-spec/sources/) shows a `"wms-imagery"` source of `"type": "raster"` whose `tiles` array contains a literal WMS `GetMap` URL template using the `{bbox-epsg-3857}` placeholder:
  ```
  "http://a.example.com/wms?bbox={bbox-epsg-3857}&format=image/png&service=WMS&version=1.1.1&request=GetMap&srs=EPSG:3857&width=256&height=256&layers=example"
  ```
  This is the exact mechanism already used for the DOP satellite layer per `01-map-library-and-imagery.md`, and it applies identically here — the parcel WMS's `GetMap` URL (with `&styles=Schwarz&transparent=true&format=image/png` appended) slots into a MapLibre `raster` source the same way.
- **Multiple raster sources in one style**: confirmed — a style's `sources` object can hold any number of named sources of mixed types (vector + multiple raster), each independently referenced by a `layer`'s `source` property (https://maplibre.org/maplibre-style-spec/sources/). Nothing in the spec caps the source or layer count — three (vector base + DOP raster + parcel raster) is unremarkable.
- **Independent visibility toggling**: the `visibility` layout property (https://maplibre.org/maplibre-style-spec/layers/) takes values `visible`/`none` (default `visible`), is settable per layer, and applies uniformly across layer types including `raster` — i.e. each of the three layers (base, satellite, parcel overlay) can be shown/hidden independently at runtime without touching the other two or reloading the style.
- **Stacking order without obscuring lower layers**: layer paint order follows the `layers` array's declaration order (later entries paint on top of earlier ones, per the same layers doc). A `raster` layer whose source tiles have a genuinely transparent (alpha-varying) background — confirmed for the parcel WMS in section 3 above — will only paint its opaque pixels (the parcel outlines) over whatever is beneath, leaving the DOP orthophoto or OpenFreeMap vector base fully visible everywhere else. This is exactly the "Überlagerung: Parzellarkarte mit Orthophoto" (overlay: Parzellarkarte over orthophoto) use case the LDBV product page itself lists as the recommended use for this product (https://www.ldbv.bayern.de/produkte/liegenschaftsinformationen/parzellarkarte.html) — GPSView's intended use matches the data provider's own suggested use case exactly.

No gaps found here — this part of the integration is solidly confirmed by the primary spec docs, consistent with the same conclusion already reached for the DOP layer in `01-map-library-and-imagery.md`.

---

## 5. Coverage

**Single, statewide WMS endpoint — no per-district or per-tile handling needed by GPSView.** Confirmed two ways:

- The JSON product record's `abgabe_gebiet` field is `"bayernweit"` (statewide) for the WMS delivery mechanism (https://geodaten.bayern.de/opengeodata/json/opengeodata_datensaetze.json).
- The live `GetCapabilities` document's `EX_GeographicBoundingBox` spans the full extent of Bavaria (lon 8.976–14.015°E, lat 47.180–50.570°N) as a single layer, with `BoundingBox` entries for the same extent repeated in every supported CRS including EPSG:3857.

A "current position, anywhere in Bavaria" live app can issue `GetMap` requests against this one endpoint/layer regardless of where in the state the user is, exactly like the DOP satellite layer already does — no tiling, sharding, or district-based endpoint switching required on GPSView's side (the WMS server handles arbitrary bounding boxes internally). Outside Bavaria there is, as expected, no coverage at all — same caveat already noted for DOP in `01-map-library-and-imagery.md`, and out of scope for this Bavaria-specific app.

---

## 6. License/attribution summary table

| Item | Value | Citation |
|---|---|---|
| WMS endpoint | `https://geoservices.bayern.de/od/wms/alkis/v1/parzellarkarte` | live `GetCapabilities` fetch, 2026-07-11 |
| WMS versions | 1.1.1, 1.3.0 | `GetCapabilities` XML |
| Recommended layer | `by_alkis_parzellarkarte_umr_schwarz` (`STYLES=Schwarz`) or `by_alkis_parzellarkarte_umr_gelb` (`STYLES=Gelb`) — outline-only, built for overlay use | `GetCapabilities` XML layer abstracts |
| Image format | `image/png` with genuine alpha transparency (confirmed via live `GetMap` test) | live test, this session |
| CRS | EPSG:3857 (Web Mercator) natively supported, plus 25832/25833/31468/5678/4258/4326 | `GetCapabilities` XML |
| Authentication | None required (TLS 1.2 required) | `GetCapabilities` XML (no `Authentication` element); corroborated by https://geodatenonline.bayern.de/geodatenonline/seiten/wms_alkis_parzellarkarte |
| Coverage | Bavaria-wide, single endpoint | JSON `abgabe_gebiet: "bayernweit"` + `GetCapabilities` bounding box |
| License (per `GetCapabilities`/OpenData JSON) | CC BY 4.0 | live `GetCapabilities` `AccessConstraints`; `opengeodata_datensaetze.json` |
| License (per LDBV product page — **treated as authoritative in this doc**) | **CC BY-ND 4.0** | https://www.ldbv.bayern.de/produkte/liegenschaftsinformationen/parzellarkarte.html |
| Required attribution | **"Bayerische Vermessungsverwaltung – www.geodaten.bayern.de"** | https://www.geodaten.bayern.de/odd/m/3/html/nutzungsbedingungen.html |

---

## Recommendation

**Add the ALKIS-Parzellarkarte as a third raster source in the MapLibre style**, alongside the existing OpenFreeMap vector base and Bavarian DOP raster satellite source from `01-map-library-and-imagery.md`:

- **Source**: a `raster` source whose `tiles` template targets `https://geoservices.bayern.de/od/wms/alkis/v1/parzellarkarte?SERVICE=WMS&VERSION=1.3.0&REQUEST=GetMap&LAYERS=by_alkis_parzellarkarte_umr_schwarz&STYLES=Schwarz&CRS=EPSG:3857&BBOX={bbox-epsg-3857}&WIDTH=256&HEIGHT=256&FORMAT=image/png&TRANSPARENT=TRUE` (swap `umr_schwarz`/`Schwarz` for `umr_gelb`/`Gelb` if yellow proves more legible against the DOP/vector backgrounds during implementation — confirm visually, not resolvable from docs alone). `STYLES` is mandatory — a request without it is rejected.
- **Layer**: a `raster` layer referencing that source, placed **after** (on top of) the DOP satellite layer and the OpenFreeMap base layer in the style's `layers` array, so its transparent-background PNG composites over both without obscuring them (confirmed live: the WMS returns genuine alpha transparency, and MapLibre's own spec confirms later `layers` entries paint on top).
- **Toggling**: independently controlled via the standard `visibility` layout property (`visible`/`none`), exactly like the existing base/satellite layer toggle already planned — no style reload needed, and no interaction with the other two layers' own visibility.
- **Zoom gating**: set a sensible `minzoom` (e.g. around z16–17) on this raster source/layer so parcel-boundary tiles aren't requested (or visually cluttered) until the user is zoomed in close enough to read individual parcels — matching the data provider's own "optimized for 1:1000" guidance, even though the server does not hard-enforce it.
- **Attribution**: append **"Bayerische Vermessungsverwaltung – www.geodaten.bayern.de"** to the attribution control whenever the parcel overlay is active — this is the same attribution string already required for the DOP layer, so in practice showing it once whenever *either* Bavarian layer is visible (satellite or parcel overlay, or both) satisfies both obligations with one line of UI text. No separate ALKIS-specific attribution text exists beyond this portal-wide wording.
- **Licensing note for the About/Licenses screen**: list this layer as **CC BY-ND 4.0** (the LDBV product page's explicit, product-specific statement, treated here as authoritative over the generic CC BY 4.0 value baked into the WMS `GetCapabilities`/OpenData JSON metadata) — flag this discrepancy for a human to confirm with LDBV's Kundenservice (`service@geodaten.bayern.de`) before shipping, though it does not change what GPSView needs to build: the app only displays unmodified WMS tiles (permitted "sharing" under either license variant), never remixes/re-derives the parcel geometry into a new dataset.
- **No per-district/tiling logic needed** — this is a single Bavaria-wide WMS endpoint, matching the DOP layer's integration pattern exactly; a live "current position" app anywhere in Bavaria works against this one endpoint unmodified.
