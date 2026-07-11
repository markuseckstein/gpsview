# Research: Map library and satellite imagery for GPSView

Ticket: `wayfinder/tickets/01-research-map-library-and-imagery.md`
Researched: 2026-07-11
Context: personal, sideload-only Android app, Kotlin + Jetpack Compose, min SDK 34, Google Play services allowed, UI in German. No Play Store distribution, but the app still redistributes third-party tile/map content, so ToS/attribution obligations of any provider still apply.

> Methodology note: all claims below are backed by a primary source (official docs, repo, GitHub API, or ToS page) fetched directly during this research session, with an inline URL next to the claim. Where a primary source was unreachable or ambiguous, this is flagged explicitly rather than guessed.

---

## 1. Map library comparison: MapLibre Native (Android) vs osmdroid

### osmdroid — archived, unmaintained

- Repo: https://github.com/osmdroid/osmdroid — license **Apache-2.0** (https://github.com/osmdroid/osmdroid/blob/master/LICENSE).
- **The repository was archived on 2024-11-20** and is now read-only; GitHub's own repo metadata confirms `"archived": true`, `"pushed_at": "2024-11-20T06:40:38Z"` (via `gh api repos/osmdroid/osmdroid`, checked 2026-07-11 against https://github.com/osmdroid/osmdroid).
- Latest (final) release: `osmdroid-parent-6.1.20`, published 2024-08-18 (https://github.com/osmdroid/osmdroid/releases). No releases since.
- Open issues at time of archiving/since: **306** (GitHub API, `open_issues_count`), i.e. permanently unresolved — the project "will no longer receive updates or new releases" (GitHub archive banner, https://github.com/osmdroid/osmdroid).
- Confirms real-world consequence: the ODK Collect project has an open issue explicitly about migrating away from osmdroid because of this: *"OSMDroid has now been archived, so we need to replace it to prevent running into bugs/issues with new Android versions in the future... it seems like the best candidate to replace it would be MapLibre."* (https://github.com/getodk/collect/issues/7092)
- Compose interop: osmdroid is a classic Android `View` (`MapView` extends a `View`); it has no native Compose API. It would have to be wrapped in Compose's `AndroidView` interop layer. No Jetpack Compose support is mentioned anywhere in the repo's README (checked https://github.com/osmdroid/osmdroid).
- Raster/vector: osmdroid's tile provider system is raster-tile only (XYZ/WMS raster tile overlays); it predates the vector-tile ecosystem and has no vector renderer.
- Footprint: the published `osmdroid-android-6.1.20.aar` on Maven Central is **~842 KB** (`content-length: 861704` bytes, `https://repo1.maven.org/maven2/org/osmdroid/osmdroid-android/6.1.20/osmdroid-android-6.1.20.aar`, checked directly via HTTP HEAD).

**Verdict on osmdroid: do not use.** It is a dead project (archived, no fixes for API 34+ issues going forward, 306 unresolved issues frozen in place), it has no Compose story, and it only does raster tiles — a strictly worse fit than MapLibre for a new 2026 app.

### MapLibre Native (Android) — actively maintained

- Current canonical repo for the native Android/iOS SDK is **https://github.com/maplibre/maplibre-native** (not `maplibre-gl-native`). `maplibre-gl-native` still exists and is **not archived** at the API level (`"archived": false`, checked via `gh api repos/maplibre/maplibre-gl-native`), but its own README describes the project's continuation as `maplibre-native`, so treat `maplibre-native` as the actively developed repo (https://github.com/maplibre/maplibre-native).
- License: **BSD-2-Clause** (`gh api repos/maplibre/maplibre-native` → `"license":{"spdx_id":"BSD-2-Clause"}`, https://github.com/maplibre/maplibre-native).
- Maintenance health (checked 2026-07-11 via GitHub API against https://github.com/maplibre/maplibre-native):
  - `archived: false`
  - `open_issues_count: 560`
  - `stargazers_count: 2072`, `forks: 552`
  - `pushed_at: 2026-07-10T23:19:08Z` — commit activity **the day before this research**, i.e. actively developed.
  - Latest Android release tag: `android-v13.3.1`, published **2026-06-24** (https://github.com/maplibre/maplibre-native/releases); 226 total releases in the repo.
  - Origin: "This project originated as a fork of Mapbox GL Native, before their switch to a non-OSS license in December 2020" (MapLibre Native docs/README, https://github.com/maplibre/maplibre-native).
- Published Maven artifact: `org.maplibre.gl:android-sdk`, current release **13.3.1** confirmed via Maven Central metadata (`https://repo1.maven.org/maven2/org/maplibre/gl/android-sdk/maven-metadata.xml`).
- minSdk: MapLibre Native for Android raised `minSdkVersion` from 14 to 21; some sources note 23 is recommended for the map surface to reliably render on all devices (secondary aggregation of MapLibre's own changelog/docs at https://maplibre.org/maplibre-native/android/examples/getting-started/ and https://github.com/maplibre/maplibre-native/blob/main/platform/android/docs/getting-started.md — **not independently re-verified word-for-word in this session; verify exact minSdk against the current `android-sdk` AAR manifest before committing**, but since GPSView's own min SDK is 34, this is a non-issue either way).
- Footprint: the `android-sdk-13.3.1.aar` on Maven Central is **~17.0 MB** (`content-length: 17854994` bytes, checked via direct HTTP HEAD against `https://repo1.maven.org/maven2/org/maplibre/gl/android-sdk/13.3.1/android-sdk-13.3.1.aar`). This AAR bundles native `.so` libraries for multiple ABIs; actual per-device APK impact after ABI splitting/App Bundle slicing is much lower than the raw AAR size, but it is still meaningfully heavier than osmdroid's ~842 KB pure-Java/Kotlin AAR. This is the real tradeoff: a native (C++) renderer with GPU-accelerated vector tiles vs a lightweight pure-JVM raster-only library.
- Vector + raster support: the MapLibre style spec has a first-class **raster source type** for XYZ/WMS/WMTS raster tiles, explicitly documented with a WMS URL example, alongside vector sources — both can coexist in one style and layers can be toggled/shown-hidden at runtime through the standard layer visibility layout property (https://maplibre.org/maplibre-style-spec/sources/#raster). This directly satisfies the "switch/toggle between a base layer and a satellite raster layer" requirement — model the satellite orthophoto as a `raster` source/layer and toggle its visibility, or swap the active style.

#### Compose interop

- There is **no first-party Jetpack Compose API inside `maplibre-native` itself** — the base Android SDK is still View-based (`MapView`), same integration model as classic Mapbox/Google Maps SDKs.
- However, the MapLibre org now owns and publishes an **official Compose wrapper: https://github.com/maplibre/maplibre-compose** ("Add interactive vector tile maps to your Compose app"), documented at https://maplibre.org/maplibre-compose/.
  - License: **BSD-3-Clause** (per repo, checked https://github.com/maplibre/maplibre-compose).
  - It's a Compose **Multiplatform** wrapper (Android, iOS, Desktop, Web) around the native MapLibre SDKs, published to Maven Central, latest release **v0.13.0** (2026-05-20 per fetched repo/release data — cross-check exact version at release time, e.g. via `https://github.com/maplibre/maplibre-compose/releases`, before pinning a version in `build.gradle`).
  - Per the project's own roadmap/readme, **Android and iOS are the most complete targets (~90%)**, Desktop and Web are far behind (~15-20%) (https://github.com/maplibre/maplibre-compose, https://maplibre.org/maplibre-compose/roadmap/) — i.e. for an Android-only app this library is in its most mature/battle-tested configuration, not its experimental one.
  - There is also a community project, **Rallista/maplibre-compose-playground** ("Composable MapLibre for Android Kotlin"), which the org's own docs reference as a precursor/alternative integration path (https://github.com/Rallista/maplibre-compose-playground) — worth knowing about as a fallback/reference implementation, but the org-adopted `maplibre/maplibre-compose` is the one to depend on.
  - I could not fully verify from the fetched docs pages exactly which raster/satellite-layer-switching APIs `maplibre-compose` exposes today (the getting-started page didn't cover styling API surface) — **before committing, read `maplibre-compose`'s "Styling the map" / "Adding data to the map" docs pages directly** to confirm raster source + layer-visibility toggling is exposed through the Compose API (or fall back to an `AndroidView`-wrapped classic `MapView` + MapLibre's Kotlin Style DSL, which definitely supports it since it's the same underlying native SDK).
- Fallback interop path if `maplibre-compose` proves insufficient: wrap the classic View-based `org.maplibre.gl:android-sdk` `MapView` in Compose's `AndroidView`, which is a well-established, low-risk pattern used by many Compose apps embedding legacy Android Views.

**Verdict: MapLibre Native (Android SDK) + the official `maplibre-compose` wrapper (with a raw `AndroidView`-wrapped classic `MapView` as fallback) is the only viable modern choice.** osmdroid is dead.

---

## 2. OSM-based map tile source usage policy

### OSM Foundation's own tile servers (`tile.openstreetmap.org`) — not usable for app distribution

Fetched directly from https://operations.osmfoundation.org/policies/tiles/ (2026-07-11):

- **Valid User-Agent required, no library defaults**: "Do not use a library default User-Agent" — "Traffic using generic defaults... may be blocked without notice."
- **Caching mandatory**: must honour server cache headers, or otherwise cache locally for **at least 7 days**; sending no-cache headers is disallowed.
- **Bulk/offline downloading explicitly prohibited**: "Pre-seeding large areas or multiple zoom levels in advance" is forbidden, and the policy states plainly: **"Offline use is not permitted on tile.openstreetmap.org."** Any "download for offline" feature is explicitly disallowed.
- **No SLA, best-effort service**: heavy/inappropriate use "that degrades the service" can get an app blocked without notice; there is no guarantee of availability.
- **Attribution**: "© OpenStreetMap contributors" must be visibly displayed on the map, not hidden.
- This confirms the ticket's premise directly: **OSM's own tile service is a shared, best-effort infrastructure resource for OSM's own web map, not a CDN meant for third-party app distribution.** It is explicitly not designed or intended to back a redistributed application's map layer, and its ToS forbids the caching/offline behaviors a normal mobile app would want. GPSView should not point directly at `tile.openstreetmap.org`.

### OpenFreeMap — recommended primary candidate

Fetched directly from https://openfreemap.org (2026-07-11):

- **No API key, no registration, no cookies**: "There's no registration, no user database, no API keys, and no cookies."
- **Free, unlimited**: "Using our public instance is completely free: there are no limits on the number of map views or requests."
- **Self-hostable**: "You can either self-host or use our public instance. Everything is open-source" — a good future fallback if the public instance ever becomes unavailable.
- **Commercial use allowed** too ("Yes"), so personal/non-commercial use is trivially within scope.
- **Attribution required**: "Attribution is required. If you are using MapLibre, they [attribution controls] are automatically added, you have nothing to do." For custom integrations, the required text is: **"OpenFreeMap © OpenMapTiles Data from OpenStreetMap"** (verbatim from https://openfreemap.org).
- Because OpenFreeMap ships **vector** tiles (OpenMapTiles schema) and is designed to be consumed by MapLibre GL, this pairs naturally with the MapLibre Native library choice above — no separate raster tile provider needed for the base map layer.

### MapTiler Cloud — viable paid-tier-capable fallback, has usage caps

Fetched from https://www.maptiler.com/cloud/pricing/ (2026-07-11):

- Free plan: **5,000 map sessions/month, 100,000 API requests/month**, 5 GB storage, etc.
- **API key required** for all services.
- Free plan **displays a MapTiler logo/attribution on the map** — an unremovable branding element on the free tier.
- Free plan is explicitly framed as **"suitable for testing, personal or non-commercial use"** (MapTiler's own wording) — usable for GPSView, but capped (**"service will pause until the next month without an upgrade"** if the monthly quota is exceeded) — a real risk for a "personal but always-on" app if usage patterns are unpredictable, though 5,000 sessions/month is generous for single-user personal use.

### Thunderforest — viable paid-tier-capable fallback

Fetched from https://www.thunderforest.com/pricing/ (2026-07-11):

- Free "Hobby Project" tier: **150,000 tile requests/month**, global coverage.
- Signup required to get an API key (implied by needing an account; explicit API-key requirement not itself quoted on the pricing page — verify in their docs before shipping).
- **Attribution is mandatory and may not be removed**: "it's not permitted to remove the attribution from your app or your website" — must show both Thunderforest and OpenStreetMap attribution.
- Raster only (classic XYZ raster styles like OpenCycleMap, Transport, etc.) — would require osmdroid-style raster handling even under MapLibre (as a raster source), losing vector-tile benefits.

### Stadia Maps — free tier exists but is non-commercial only, and requires an API key with binary-exposure risk

Fetched from https://stadiamaps.com/pricing/ and https://docs.stadiamaps.com/authentication/ (2026-07-11):

- Free plan: **200,000 credits/month**, standard basemaps, "Basic APIs only", and explicitly **"Commercial use not allowed"** on the free tier — fine for GPSView's stated personal/non-commercial use.
- **Authentication**: for **distributed mobile apps specifically**, Stadia's own docs say **API keys are required** (domain/referrer-based auth is only for browser/web use, not apps). Their own guidance warns: **"Avoid shipping API keys in an app binary when possible"** and recommends storing any long-term key in the **Android Keystore** if it must be embedded (https://docs.stadiamaps.com/authentication/). This is a real operational obligation to plan for — an embedded key in a sideloaded APK is extractable, and Stadia's own advice acknowledges this risk without offering a purely keyless mobile path.

### Self-hosted vector tiles (Protomaps/PMTiles + Planetiler) — feasible fallback, more ops effort

- **PMTiles** is a single-file tile archive format (raster or vector) designed for "serverless" static hosting, using HTTP range requests so a plain static file host (even a local file on-device) can serve tiles without a live tile server (https://docs.protomaps.com/pmtiles/, https://protomaps.com/).
- **Planetiler** (https://github.com/onthegomap/planetiler) can build a `.pmtiles`/`.mbtiles` vector tileset directly from an OSM `.osm.pbf` extract, and per its own docs a full-planet build takes only 2-3 hours on modest hardware — a small Bavaria-only extract would be far faster/smaller.
- The **pmtiles CLI's `extract` subcommand** can cut a bounding-box (e.g. Bavaria) subset out of a larger PMTiles archive (https://docs.protomaps.com/guide/getting-started).
- Feasibility for GPSView: fully doable (build a Bavaria `.pmtiles` file once, ship it in `assets/` or download it once on first run, serve locally via a PMTiles-aware source in MapLibre). Tradeoffs: this is meaningfully more engineering/ops effort (build pipeline, periodic OSM-data refresh, bundling a multi-MB/GB file, licensing pass-through of OSM's ODbL data) for a "personal sideload" app where a hosted free tier (OpenFreeMap) already solves the problem with zero ops burden. **Recommended only as a documented fallback if OpenFreeMap's public instance ever becomes unavailable or unacceptable**, not as the initial implementation.
- Underlying OSM data license regardless of self-hosting: **ODbL** — attribution "© OpenStreetMap contributors" still applies (this is the same attribution baseline as OpenFreeMap above, since OpenFreeMap's data is itself sourced from OSM).

---

## 3. Satellite/orthophoto layer for Bavaria

### Bavarian DOP (Digitale Orthophotos) via LDBV / Bayern Open Data — recommended primary candidate

- Endpoint (WMS, no authentication): **`https://geoservices.bayern.de/od/wms/dop/v1/dop20`** — confirmed via direct fetch of https://geodatenonline.bayern.de/geodatenonline/seiten/wms_dop20cm (2026-07-11): "supports OGC WMS versions 1.1.1 and 1.3.0, requires TLS 1.2, does not require authentication."
- A historical-imagery variant is also available: `https://geoservices.bayern.de/od/wms/histdop/v1/histdop` (per the same open-data search results, from https://geodatenonline.bayern.de/geodatenonline/seiten/wms_dop_hist — not independently re-fetched in this session, treat as needing a quick sanity check before use).
- Product landing page: https://geodaten.bayern.de/opengeodata/OpenDataDetail.html?pn=dop40 (DOP40 RGB variant) confirms these are part of the Bavarian Surveying Administration's **OpenData initiative** ("Kostenfreie Geodaten der Bayerischen Vermessungsverwaltung" / free geodata).
- **License: CC BY 4.0, not Datenlizenz Deutschland Zero 2.0.** Confirmed directly from the Bavarian Open Data Terms of Use page **https://www.geodaten.bayern.de/odd/m/3/html/nutzungsbedingungen.html** (2026-07-11): "Creative Commons Namensnennung 4.0 International (CC BY 4.0)".
- **Required attribution (Quellenvermerk), verbatim from the same Terms of Use page**:
  > **"Bayerische Vermessungsverwaltung – www.geodaten.bayern.de"**
- Practical note: WMS (not WMTS) means GPSView will need to construct `GetMap` requests (bbox/CRS/width/height/format params) rather than consume pre-tiled XYZ/WMTS tiles directly; if the chosen map library wants a simple XYZ raster source, a thin WMS-to-XYZ tile URL template (MapLibre's raster source supports templated WMS `{bbox-epsg-3857}` URLs directly per the style spec, https://maplibre.org/maplibre-style-spec/sources/#raster) should work without a proxy.
- Coverage caveat: this is Bavaria-only imagery. If GPSView is ever used outside Bavaria, this layer will have no coverage — worth deciding up front whether that's acceptable for a "personal" app (probably yes, given the ticket frames this as Bavaria-specific) or whether a global fallback (Esri World Imagery, below) should also be wired in.

### Esri World Imagery — usable only with real caveats; not a clean drop-in for a non-ArcGIS app

- Esri's own attribution guidance (https://developers.arcgis.com/documentation/esri-and-data-attribution/, fetched 2026-07-11) requires:
  - **"Powered by Esri"** clearly displayed on the map or reachable from a menu/button.
  - Layer-specific data-source attribution (e.g. for World Imagery: "Esri, Vantor, GeoEye, Earthstar Geographics, CNES/Airbus DS, USDA, USGS...", which is a **dynamic** attribution string served with the tile response, not a fixed piece of text — an app must fetch and display the live attribution data, not hardcode a static credit line).
  - Accessing the **ArcGIS Static Basemap Tiles service requires an ArcGIS Location Platform account** (i.e. an API key), per the same doc.
- Pricing/free-tier terms, from https://location.arcgis.com/pricing/ (fetched 2026-07-11): basemap tile requests (vector, map, and static basemap tiles via `basemaps-api.arcgis.com` / `static-map-tiles-api.arcgis.com`) get **2,000,000 free tiles/month, then $0.15 per 1,000 tiles** — the free allotment is large and the page as fetched did not itself state a blanket exclusion of commercial use at this pricing tier (unlike, e.g., Stadia's explicit "no commercial use" free-tier clause). **However**, I was unable to fetch or confirm the actual legal text of Esri's Master License Agreement / product-specific "E300" terms document that would state the definitive contractual conditions (the page at https://www.esri.com/en-us/legal/terms/full-master-agreement only exposes links to PDFs, not their content, in this session) — **treat the exact commercial/non-commercial conditions and any revenue-based restrictions as unverified; a human should open the actual E300 PDF (or equivalent current product-specific terms) before shipping a build that uses World Imagery**, since secondary community-forum sources (Esri Community threads, not independently re-verified here as primary) suggest conditions like "not generating revenue from the app" may apply to the free/developer tier.
- Given (a) the unresolved ambiguity in Esri's actual contract terms, (b) the mandatory ArcGIS Location Platform account/API key, and (c) the fact that Bavaria is already covered natively and with a fully clear CC BY 4.upstream license by the LDBV DOP service — **Esri World Imagery is not needed as the primary satellite source for this app.** It remains worth keeping in your back pocket only as a *global* fallback (outside Bavaria) if a future need arises, with the above verification done first.

---

## 4. License/attribution summary table

| Candidate | License | Attribution obligation | Citation |
|---|---|---|---|
| MapLibre Native (Android SDK) | BSD-2-Clause | None (permissive; standard NOTICE/license inclusion only) | https://github.com/maplibre/maplibre-native |
| maplibre-compose | BSD-3-Clause | None (permissive) | https://github.com/maplibre/maplibre-compose |
| osmdroid (not chosen) | Apache-2.0 | None (permissive) — moot, project is dead | https://github.com/osmdroid/osmdroid/blob/master/LICENSE |
| OSM data (via any OSM-derived source) | ODbL | "© OpenStreetMap contributors" visible on map | https://operations.osmfoundation.org/policies/tiles/ |
| OpenFreeMap (tiles + hosting) | Open-source, free to use | **"OpenFreeMap © OpenMapTiles Data from OpenStreetMap"** (auto-added if using MapLibre's built-in attribution control) | https://openfreemap.org |
| MapTiler Cloud (free tier) | Proprietary service ToS | MapTiler logo/attribution on map (non-removable on free tier) | https://www.maptiler.com/cloud/pricing/ |
| Thunderforest (free tier) | Proprietary service ToS | Thunderforest + OpenStreetMap attribution, non-removable | https://www.thunderforest.com/pricing/ |
| Stadia Maps (free tier) | Proprietary service ToS, non-commercial only | Standard basemap attribution (exact text not independently re-verified beyond pricing page — check in-product) | https://stadiamaps.com/pricing/, https://docs.stadiamaps.com/authentication/ |
| Bavarian DOP (LDBV / geodaten.bayern.de) | CC BY 4.0 | **"Bayerische Vermessungsverwaltung – www.geodaten.bayern.de"** | https://www.geodaten.bayern.de/odd/m/3/html/nutzungsbedingungen.html |
| Esri World Imagery | Proprietary ArcGIS ToS (exact clauses unverified, see caveat above) | "Powered by Esri" + dynamic per-tile data-source credit | https://developers.arcgis.com/documentation/esri-and-data-attribution/ |
| Self-hosted OSM extract (Planetiler/PMTiles) | Underlying data still ODbL | "© OpenStreetMap contributors" | https://docs.protomaps.com/pmtiles/, https://operations.osmfoundation.org/policies/tiles/ |

---

## Recommendation

**(a) Map library: MapLibre Native (Android SDK, `org.maplibre.gl:android-sdk`) via the official `maplibre-compose` wrapper (https://github.com/maplibre/maplibre-compose), falling back to a classic `MapView` wrapped in Compose's `AndroidView` if `maplibre-compose`'s current API surface turns out to be missing something GPSView needs (e.g. fine-grained raster-layer control).** Reasoning: osmdroid is archived (2024-11-20) and dead — 306+ frozen open issues, no future Android-14+ compatibility fixes, raster-only, no Compose story. MapLibre Native is actively developed (commits as recent as the day before this research, releases roughly monthly, BSD-2-Clause), supports both vector and raster sources in one style with runtime layer toggling — exactly what's needed to switch between an OSM base layer and a satellite raster overlay — and now has an org-maintained Compose wrapper that is most mature specifically on Android. The tradeoff is footprint (~17 MB AAR vs osmdroid's ~842 KB) and being a native/C++ library rather than pure JVM, which is an acceptable cost for a modern, maintained map renderer.

**(b) Map tile source: OpenFreeMap (https://openfreemap.org) as primary.** No API key, no request caps, free (including commercial use, so personal use is trivially fine), self-hostable if ever needed, and it serves OpenMapTiles-schema vector tiles that plug directly into MapLibre. Do **not** point at `tile.openstreetmap.org` directly — OSMF's own tile usage policy (https://operations.osmfoundation.org/policies/tiles/) is explicit that offline/bulk use is disallowed and that it is a best-effort service not meant for app distribution. **Attribution UI requirement:** if using MapLibre's built-in attribution control, this is automatic per OpenFreeMap's own statement; if building a custom attribution UI element, it must literally show **"OpenFreeMap © OpenMapTiles Data from OpenStreetMap"** somewhere visible on the map screen (a small attribution corner overlay, matching MapLibre's default UX pattern, is the natural fit). Keep MapTiler (personal/non-commercial free tier, but capped and logo-branded) and Thunderforest (150k req/month, mandatory attribution) noted as paid/quota-bound fallbacks, and self-hosted Planetiler/PMTiles Bavaria extract as the no-dependency fallback of last resort.

**(c) Satellite tile source: Bavarian DOP via LDBV/Bayern Open Data, WMS endpoint `https://geoservices.bayern.de/od/wms/dop/v1/dop20`, no authentication required.** It's free, unauthenticated, high-resolution (20 cm), officially open-data licensed under CC BY 4.0 with clean, unambiguous attribution wording, and matches the ticket's Bavaria-specific framing exactly — a strictly better fit than Esri World Imagery, whose exact commercial-use contract terms I could not fully verify from primary sources in this session and which requires an ArcGIS Location Platform account/API key plus dynamic "Powered by Esri" + per-tile data-provider attribution. **Attribution UI requirement:** display the exact text **"Bayerische Vermessungsverwaltung – www.geodaten.bayern.de"** (per https://www.geodaten.bayern.de/odd/m/3/html/nutzungsbedingungen.html) whenever the satellite layer is the active/visible layer — e.g. in the same attribution corner overlay, swapped in/appended when the user toggles to the satellite layer.

**(d) Attribution/licensing obligations to implement in the UI:**
1. A persistent, always-visible map attribution overlay (small corner text/link, standard map-UX pattern) showing:
   - **"OpenFreeMap © OpenMapTiles Data from OpenStreetMap"** whenever the OSM base layer is shown (https://openfreemap.org).
   - **"Bayerische Vermessungsverwaltung – www.geodaten.bayern.de"** whenever the Bavarian DOP satellite layer is shown (https://www.geodaten.bayern.de/odd/m/3/html/nutzungsbedingungen.html).
2. An "About / Licenses" screen or dialog (common in German-language apps as "Über / Lizenzen") listing:
   - MapLibre Native — BSD-2-Clause (https://github.com/maplibre/maplibre-native).
   - maplibre-compose — BSD-3-Clause (https://github.com/maplibre/maplibre-compose).
   - OpenStreetMap data — ODbL, "© OpenStreetMap contributors" (https://operations.osmfoundation.org/policies/tiles/, https://openfreemap.org).
   - Bavarian DOP orthophoto data — CC BY 4.0, "Bayerische Vermessungsverwaltung – www.geodaten.bayern.de" (https://www.geodaten.bayern.de/odd/m/3/html/nutzungsbedingungen.html).
   - (If added later) any of MapTiler/Thunderforest/Stadia/Esri, each with their own mandatory, non-removable attribution text as documented above.
3. **Before shipping**, manually verify: (i) the exact `maplibre-compose` API for raster-layer/satellite-toggle support by reading its "Styling the map"/"Adding data to the map" docs directly (not fully confirmed in this session); (ii) the historical-DOP WMS endpoint URL if that layer is used; (iii) if Esri World Imagery is ever added, obtain and read the actual current Esri Master Agreement / product-specific terms PDF rather than relying on secondary summaries, since this session could not fetch their content directly.
