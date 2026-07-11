# Research: GNSS metadata and height APIs for GPSView

Ticket: `wayfinder/tickets/02-research-gnss-metadata-and-height.md`
Researched: 2026-07-11
Context: personal, foreground-only Android app, Kotlin + Jetpack Compose, min SDK 34 (Android 14+), Google Play services allowed. The app displays live location, GNSS metadata, and coordinates in multiple formats, including satellite counts and both ellipsoidal (WGS84) and Mean Sea Level (MSL) height.

> Methodology note: all claims below are backed by a primary source (the actual `developer.android.com` / `developers.google.com` API reference page for the class in question) fetched directly during this research session, with an inline URL next to the claim. The standard `WebFetch` tool could not retrieve the real page bodies for `developer.android.com/reference/...` pages (it only returned the site's navigation shell, likely because the reference pages are heavily client-rendered / gated behind an optional sign-in redirect for the default fetch path); this was worked around by fetching the same URLs directly with `curl` using a crawler user-agent, which returned the full server-rendered HTML including every method's "Added in API level" annotation. Every non-obvious claim below is a near-verbatim quote or close paraphrase of text found in that HTML, not from training-data memory. Where something could not be confirmed from a primary source, it is flagged explicitly below rather than guessed.

---

## Summary table

| Displayed metadatum | API the spec should mandate | Precondition / gating |
|---|---|---|
| Horizontal accuracy | `Location.getAccuracy()` (float, meters) | Valid only if `Location.hasAccuracy()` is true. Available since API level 1. 68th-percentile confidence radius. [source](https://developer.android.com/reference/android/location/Location) |
| Vertical accuracy (ellipsoidal) | `Location.getVerticalAccuracyMeters()` | Valid only if `Location.hasVerticalAccuracy()` is true. **Added in API level 26.** 68th-percentile confidence, relative to `getAltitude()`. [source](https://developer.android.com/reference/android/location/Location) |
| Vertical accuracy (MSL) | `Location.getMslAltitudeAccuracyMeters()` | Valid only if `Location.hasMslAltitudeAccuracy()` is true. **Added in API level 34.** Populated automatically as a side effect of calling `AltitudeConverter.addMslAltitudeToLocation()` if the location already has a finite, non-negative vertical accuracy. [source](https://developer.android.com/reference/android/location/Location), [source](https://developer.android.com/reference/android/location/altitude/AltitudeConverter) |
| Satellites visible | `GnssStatus.getSatelliteCount()` inside a registered `GnssStatus.Callback` | Requires `LocationManager.registerGnssStatusCallback(...)` + `ACCESS_FINE_LOCATION`; only delivers data while `GPS_PROVIDER` is enabled and the app is in the foreground. Added in API level 24 (current non-deprecated overload added API 30). [source](https://developer.android.com/reference/android/location/GnssStatus), [source](https://developer.android.com/reference/android/location/LocationManager) |
| Satellites used-in-fix | `GnssStatus.usedInFix(int satelliteIndex)`, count the `true`s across `0 until getSatelliteCount()` | Same registration precondition as above. Added in API level 24. Do **not** use `Location.getExtras().getInt("satellites")` — deprecated in API 34 in favor of this exact mechanism. [source](https://developer.android.com/reference/android/location/GnssStatus), [source](https://developer.android.com/reference/android/location/Location) |
| Height, ellipsoidal (WGS84) | `Location.getAltitude()` | Valid only if `Location.hasAltitude()` is true. Available since API level 1. Always WGS84-ellipsoid-referenced when present. [source](https://developer.android.com/reference/android/location/Location) |
| Height, MSL (sea level) | `Location.getMslAltitudeMeters()`, obtained either (a) directly from the provider if already present, or (b) by calling `android.location.altitude.AltitudeConverter.addMslAltitudeToLocation(Context, Location)` on a background thread | Valid only if `Location.hasMslAltitude()` is true. **`Location.hasMslAltitude()`/`getMslAltitudeMeters()` added in API level 34.** `AltitudeConverter` itself is a **platform class added in API level 34** (not a Play services / GMS class), backed by a local, bundled geoid-height dataset — no network call is documented anywhere in its reference. [source](https://developer.android.com/reference/android/location/Location), [source](https://developer.android.com/reference/android/location/altitude/AltitudeConverter) |

---

## 1. `GnssStatus` and `GnssStatus.Callback`

Fetched directly from https://developer.android.com/reference/android/location/GnssStatus and https://developer.android.com/reference/android/location/GnssStatus.Callback (2026-07-11).

### Per-satellite data on `GnssStatus`

All per-satellite accessor methods take a `satelliteIndex` parameter ("An index from zero to `getSatelliteCount()` - 1"):

- `getConstellationType(int satelliteIndex)` — Added API 24. Returns one of `CONSTELLATION_UNKNOWN/GPS/SBAS/GLONASS/QZSS/BEIDOU/GALILEO/IRNSS` (the constellation-type constants themselves were also added in API 24, except `CONSTELLATION_IRNSS` added API 29).
- `getCn0DbHz(int satelliteIndex)` — Added API 24. "Retrieves the carrier-to-noise density at the antenna of the satellite at the specified index in dB-Hz." Value is between 0.0f and 63.0f inclusive.
- `getBasebandCn0DbHz(int satelliteIndex)` — Added API 30 (baseband C/N0, distinct from antenna C/N0; guarded by `hasBasebandCn0DbHz(int)`).
- `getElevationDegrees(int satelliteIndex)` — Added API 24. Value between -90.0f and 90.0f inclusive.
- `getAzimuthDegrees(int satelliteIndex)` — Added API 24. Value between 0.0f and 360.0f inclusive.
- `getSvid(int satelliteIndex)` — Added API 24. Per-constellation satellite ID (GPS 1-32, GLONASS OSN/FCN encoding, Galileo 1-36, Beidou 1-63, etc.)
- `hasEphemerisData(int)`, `hasAlmanacData(int)` — Added API 24.
- `getCarrierFrequencyHz(int)` (Added API 26, guarded by `hasCarrierFrequencyHz(int)`), `getCodeType(int)` (Added API 37), `getElapsedRealtimeNanos(int)` / `getElapsedRealtimeUncertaintyNanos(int)` (Added API 37, 68% confidence) are also present but not central to GPSView's stated display requirements.

### The "visible" vs "used in fix" distinction — the exact API answer

- **`getSatelliteCount()`** — Added API 24. "Gets the total number of satellites in satellite list." This is the count of satellites the receiver currently has in its tracked/visible list — i.e., **"visible"**.
- **`usedInFix(int satelliteIndex)`** — Added API 24. "Reports whether the satellite at the specified index was used in the calculation of the most recent position fix." Returns `boolean`. This is exactly the per-satellite "used in fix" flag.
- There is no separate "used-in-fix count" method — the app must iterate `0 until status.satelliteCount` and count `status.usedInFix(i) == true` itself.

Both quotes/method signatures verified verbatim against https://developer.android.com/reference/android/location/GnssStatus.

### `GnssStatus.Callback` and registration

`GnssStatus.Callback` (https://developer.android.com/reference/android/location/GnssStatus.Callback, added API 24) is an abstract class with four overridable methods, all present since API 24:

- `onStarted()` — "Called when GNSS system has started."
- `onStopped()` — "Called when GNSS system has stopped."
- `onFirstFix(int ttffMillis)` — "Called when the GNSS system has received its first fix since starting."
- `onSatelliteStatusChanged(GnssStatus status)` — "Called periodically to report GNSS satellite status."

It is registered via `LocationManager`, not requested as a stream of `Location` updates. From https://developer.android.com/reference/android/location/LocationManager, the current (non-deprecated) registration method is:

```
public boolean registerGnssStatusCallback (Executor executor, GnssStatus.Callback callback)
```
— **Added in API level 30**. Doc text, verbatim: *"Registers a GNSS status callback. GNSS status information will only be received while the `GPS_PROVIDER` is enabled, and while the client app is in the foreground."* Requires `Manifest.permission.ACCESS_FINE_LOCATION`; throws `SecurityException` if not held.

Two older overloads exist and are documented identically in substance:
- `registerGnssStatusCallback(GnssStatus.Callback callback)` — Added API 24, **deprecated API 30** (must be called from a Looper thread).
- `registerGnssStatusCallback(GnssStatus.Callback callback, Handler handler)` — Added API 24 (still current, not deprecated).

**Does it require an active `LocationManager` GPS-provider location request running concurrently?** Based on the documented text, **no** — the precondition stated by Android's own reference is that `GPS_PROVIDER` is *enabled* (a system/user setting, checkable via `LocationManager.isProviderEnabled(GPS_PROVIDER)`) and that the *app* is in the foreground. There is no documented requirement that the same app must also be running a concurrent `requestLocationUpdates()` call against `GPS_PROVIDER`. The identically-worded precondition also appears verbatim on the sibling `addNmeaListener(Executor, OnNmeaMessageListener)` method ("GNSS NMEA information will only be received while the `GPS_PROVIDER` is enabled, and while the client app is in the foreground"), confirming this is Android's standard boilerplate for "GNSS-callback-class" APIs rather than an accident of one method's docs. In practice this is favorable for GPSView's foreground-only design: a `GnssStatus.Callback` registered while the app is foregrounded and GPS is on should receive satellite data on its own, without also needing to separately open a `requestLocationUpdates(GPS_PROVIDER, ...)` stream — **however, I could not find a primary-source statement that registering the callback itself is sufficient to *power on* the GNSS chipset if nothing else has requested it; this is a reasonable inference from the documented wording, not a directly quoted guarantee, and should be smoke-tested on a real device before the spec asserts it as fact.**

**Verdict:** Use `LocationManager.registerGnssStatusCallback(Executor, GnssStatus.Callback)` (API 30 overload) for satellite metadata. Read `getSatelliteCount()` for "visible" and iterate `usedInFix(i)` for "used in fix" — do not rely on the deprecated `Location.getExtras()["satellites"]` key (see §6 below).

---

## 2. Height — ellipsoidal (WGS84)

Fetched from https://developer.android.com/reference/android/location/Location (2026-07-11).

- **`getAltitude()`** — Added API level 1. Doc text, verbatim: *"The altitude of this location in meters above the WGS84 reference ellipsoid. This is only valid if `hasAltitude()` is true."* Returns `double`.
- **`hasAltitude()`** — Added API level 1. *"Returns true if this location has an altitude, false otherwise."*

So `getAltitude()` is **unconditionally WGS84-ellipsoidal** whenever it is present — there is no alternate datum mode. The value is only meaningful when `hasAltitude()` is true; on network-derived or otherwise altitude-less fixes it may be stale/zero and must not be trusted without the guard.

**Provider-dependent differences:** the `LocationManager.GPS_PROVIDER` constant's own doc (https://developer.android.com/reference/android/location/LocationManager) adds an important nuance: *"Locations returned from this provider are with respect to the primary GNSS antenna position within the device. `getGnssAntennaInfos()` may be used to determine the GNSS antenna position with respect to the Android Coordinate System, and convert between them if necessary. This is generally only necessary for high accuracy applications."* — i.e. raw `GPS_PROVIDER` altitude (and position) is referenced to the physical GNSS antenna, not to some notional "device center." `NETWORK_PROVIDER`'s doc (*"determines location based on nearby cell tower and WiFi access points"*) makes no altitude claim at all — network-provider fixes commonly omit altitude entirely (or report stale/inaccurate values) since cell/Wi-Fi trilateration is a fundamentally 2D technique; nothing in the primary docs promises altitude support here. `FUSED_PROVIDER`'s doc (*"this provider may combine inputs from several other location providers to provide the best possible location fix"*) is likewise silent on whether/how altitude is fused or antenna-corrected — **not verified further in primary sources; treat FUSED_PROVIDER's altitude-population behavior as an implementation detail that should be empirically confirmed on-device**, not assumed identical to raw GPS_PROVIDER.

**Verdict:** Use `Location.getAltitude()` guarded by `hasAltitude()` for the ellipsoidal height display, sourced from whichever provider GPSView ultimately requests locations from (see §6). Label it clearly as WGS84 ellipsoidal height in the UI, since that's the only datum this getter can ever return.

---

## 3. Height — MSL (Mean Sea Level)

Fetched from https://developer.android.com/reference/android/location/Location (2026-07-11).

- **`hasMslAltitude()`** — **Added in API level 34.** *"Returns true if this location has a Mean Sea Level altitude, false otherwise."*
- **`getMslAltitudeMeters()`** — **Added in API level 34.** *"Returns the Mean Sea Level altitude of this location in meters. This is only valid if `hasMslAltitude()` is true."* Returns `double`.
- **`hasMslAltitudeAccuracy()`** — **Added in API level 34.** *"Returns true if this location has a Mean Sea Level altitude accuracy, false otherwise."*
- **`getMslAltitudeAccuracyMeters()`** — **Added in API level 34.** *"Returns the estimated Mean Sea Level altitude accuracy in meters of this location at the 68th percentile confidence level. This means that there is 68% chance that the true Mean Sea Level altitude of this location falls within `getMslAltitudeMeters()` +/- this uncertainty. This is only valid if `hasMslAltitudeAccuracy()` is true."* Returns `float`.

This **confirms the ticket's suspicion exactly**: MSL altitude support was added in Android 14 / API level 34, both as a set of `Location` fields and (per §4) as a converter API. Since GPSView's min SDK is already 34, no version-gating branch is needed for these four methods — they are always present in the SDK surface the app compiles against. What is **not** guaranteed is that any given `Location` instance actually carries an MSL value — that depends on the provider or on the app explicitly running it through `AltitudeConverter` (§4).

**GNSS chipset capability check:** I searched https://developer.android.com/reference/android/location/GnssCapabilities (class itself "Added in API level 30", obtained via `LocationManager.getGnssCapabilities()` — Added API level 30, per https://developer.android.com/reference/android/location/LocationManager) for any MSL-related capability flag (by analogy with `hasAntennaInfo()`, `hasGeofencing()`, `hasLowPowerMode()`, etc., which follow a `CAPABILITY_SUPPORTED`/`CAPABILITY_UNSUPPORTED`/`CAPABILITY_UNKNOWN` or plain-boolean pattern). **No MSL-, altitude-, or geoid-related method exists on `GnssCapabilities`** in the fetched reference (methods present: `getGnssSignalTypes()`, `hasAccumulatedDeltaRange()`, `hasAntennaInfo()`, `hasGeofencing()`, `hasGnssEngineRestartAfterPowerModeChange()`, `hasLowPowerMode()`, and similar low-level chipset traits — none about MSL/altitude). This means **there is no raw-chipset capability gate for MSL altitude to check** — `hasMslAltitude()` on the specific `Location` instance is the only gate the app needs, and it is entirely a per-fix property (either the provider populated it, or `AltitudeConverter` was run against the fix and populated it), not a hardware capability question.

**Verdict:** Guard every MSL display with `location.hasMslAltitude()` (and separately `hasMslAltitudeAccuracy()` for the accuracy figure); do not attempt any `GnssCapabilities`-based pre-check, because none exists.

---

## 4. `AltitudeConverter` — important correction to the ticket's premise

The ticket frames this as a **Play services** API (`com.google.android.gms.location.altitude.AltitudeConverter`). **That class does not exist.** I attempted to fetch `https://developers.google.com/android/reference/com/google/android/gms/location/altitude/AltitudeConverter` directly and got an HTTP 404. A web search for `com.google.android.gms.location.altitude` turned up no such Play-services class either — every hit pointed back to the platform SDK class below. Google's actual MSL-conversion offering ships as **two SDK-surface, non-Play-services classes**, both fetched and verified directly:

### `android.location.altitude.AltitudeConverter` — platform class, API 34+

Fetched from https://developer.android.com/reference/android/location/altitude/AltitudeConverter (2026-07-11):

- Class doc, verbatim: *"Converts altitudes reported above the World Geodetic System 1984 (WGS84) reference ellipsoid into ones above Mean Sea Level."* Cites as its reference: *"Brian Julian and Michael Angermann. 'Resource efficient and accurate altitude conversion to Mean Sea Level.' 2023 IEEE/ION Position, Location and Navigation Symposium (PLANS)."*
- **Added in API level 34** (whole class, per the page's "Added in API level 34" header banner).
- Constructor: `public AltitudeConverter()` — *"Creates an instance that manages an independent cache to optimize conversions of locations in proximity to one another."* (i.e., keep one instance around and reuse it rather than constructing a new one per fix.)
- **`public void addMslAltitudeToLocation(Context context, Location location)`** — Added API 34. *"Adds a Mean Sea Level altitude to the location. In addition, adds a Mean Sea Level altitude accuracy if the location has a finite and non-negative vertical accuracy; otherwise, does not add a corresponding accuracy. Must be called off the main thread as data may be loaded from raw assets. This method may take several seconds to complete, so it should only be called from a worker thread."* Throws `IOException` ("if an I/O error occurs when loading data from raw assets") and `IllegalArgumentException` if lat/lon/altitude are out of valid range or non-finite.
- **`public boolean tryAddMslAltitudeToLocation(Location location)`** — Added API **35** (i.e. this specific overload is Android 15+, not available at GPSView's min SDK 34). *"Same as `addMslAltitudeToLocation(Context,Location)` except that this method can be called on the main thread as data will not be loaded from raw assets. Returns true if a Mean Sea Level altitude is added to the location; otherwise, returns false and leaves the location unchanged. Prior calls to `addMslAltitudeToLocation(Context,Location)` off the main thread are necessary to load data from raw assets."* The reference page's own sample code shows the intended pattern: call `tryAddMslAltitudeToLocation` on the main thread first; if it returns false (data not yet cached), kick off a one-time background `addMslAltitudeToLocation` call to warm the cache, guarded by an idle flag so only one background load is in flight at a time.

**Network / offline behavior:** the only I/O the documented API surface describes is *"data may be loaded from raw assets"* and the only checked exception is `IOException` for asset-loading errors — **there is no network call, download step, remote geoid-data-expansion fetch, or "first run requires connectivity" behavior anywhere in this class's documented API.** This directly contradicts the ticket's premise of a "network access for the first call... downloads a geoid data expansion into a cache" model. I could not find, in the platform reference page itself, an explicit sentence stating "this works fully offline," but the complete absence of any network-related method, exception, or permission requirement in the documented surface — combined with the "raw assets" wording, which in Android is the standard term for data bundled inside an app/library package (`assets/` folder), not a network resource — is strong circumstantial primary-source evidence that this is a **local, bundled-data, offline-capable conversion**, not a network service.

### `androidx.core.location.altitude.AltitudeConverterCompat` — AndroidX Jetpack compat wrapper

Fetched from https://developer.android.com/reference/androidx/core/location/altitude/AltitudeConverterCompat (2026-07-11):

- Artifact: **`androidx.core:core-location-altitude`**. Added in AndroidX version **1.0.0**.
- Identical purpose/doc string to the platform class (*"Converts altitudes reported above the World Geodetic System 1984 (WGS84) reference ellipsoid into ones above Mean Sea Level"*), same PLANS-2023 citation — this is a backport for apps targeting pre-API-34 devices.
- Single method: `public static void addMslAltitudeToLocation(@NonNull Context context, @NonNull Location location)`, annotated `@WorkerThread`. Same doc text as the platform method verbatim (*"Must be called off the main thread as data may be loaded from raw assets"*), same `IOException`/`IllegalArgumentException` throws clauses.
- Since **GPSView's min SDK is already 34**, this compat artifact is **not needed** — the platform `android.location.altitude.AltitudeConverter` class is always available. It's worth knowing about only if GPSView ever lowers its min SDK in the future.
- A secondary (non-primary, not independently re-verified) code-hosting source (`android.googlesource.com`, AndroidX's own Gerrit/source tree) surfaced in search results indicates the geoid dataset ships as a bundled database asset, e.g. a file named `geoid-height-map-v0.db` under the library's `assets/database` directory — consistent with the "raw assets" wording above, but **this specific filename/path detail comes from a source-code search result, not from the reference doc page itself, so treat the exact asset name/size as unverified** even though the broader "it's a bundled local dataset, not a network fetch" conclusion is well supported by the primary reference doc.

**Accuracy figures / coverage area / Bavaria:** neither the platform reference page nor the AndroidX compat reference page states a documented accuracy figure (e.g. "±X meters typical error") or an explicit geographic coverage boundary/exclusion zone for the geoid model. **This could not be verified from the primary API reference pages in this session** — both pages describe *what* the API does and its method contracts, not the underlying geoid dataset's precision or spatial coverage limits. Given the cited academic reference is a general "resource efficient... conversion to Mean Sea Level" paper (implying a global, not regionally-scoped, geoid dataset) and that geoid models of this kind are essentially always built for global/near-global coverage, it is reasonable to expect Bavaria is covered, but **this is an inference, not a confirmed fact — flagging explicitly rather than asserting coverage as verified.**

**Verdict:** GPSView should use the platform `android.location.altitude.AltitudeConverter` class directly (min SDK is 34, so no compat dependency is needed). Treat it as an entirely local, offline-capable operation once its backing asset data has been loaded — no network permission or connectivity check should be built around it based on the documented API surface. The ticket's framing as a "Play services Altitude API" should be corrected in the spec: it is a platform SDK class, not a Play-services/GMS dependency, and adds no Play-services runtime requirement.

---

## 5. Vertical/horizontal accuracy fields — confirmed semantics

Fetched from https://developer.android.com/reference/android/location/Location (2026-07-11):

- **`getAccuracy()`** — Added API level 1. Doc text, verbatim: *"Returns the estimated horizontal accuracy radius in meters of this location at the 68th percentile confidence level. This means that there is a 68% chance that the true location of the device is within a distance of this uncertainty of the reported location... This accuracy value is only valid for horizontal positioning, and not vertical positioning. This is only valid if `hasAccuracy()` is true. All locations generated by the `LocationManager` include horizontal accuracy."* Confirms the user's belief of 68% confidence exactly, and confirms it is horizontal-only (never a proxy for vertical accuracy).
- **`getVerticalAccuracyMeters()`** — **Added in API level 26**, confirming the user's belief exactly. Doc text, verbatim: *"Returns the estimated altitude accuracy in meters of this location at the 68th percentile confidence level. This means that there is 68% chance that the true altitude of this location falls within `getAltitude()` +/- this uncertainty. This is only valid if `hasVerticalAccuracy()` is true."* This is the accuracy of the **ellipsoidal** altitude (`getAltitude()`), not MSL.
- **`hasVerticalAccuracy()`** — Added API level 26.
- MSL altitude accuracy is a **separate** field (`getMslAltitudeAccuracyMeters()` / `hasMslAltitudeAccuracy()`, both Added API 34, 68th-percentile confidence relative to `getMslAltitudeMeters()`) — see §3. The two accuracy figures (ellipsoidal vs MSL) are not interchangeable and both should be surfaced independently if GPSView shows both height flavors.

**Verdict:** all three accuracy displays (horizontal, vertical-ellipsoidal, vertical-MSL) map onto distinct, independently-guarded `Location` fields at exactly the API levels the user suspected. No surprises here beyond confirming the exact wording.

---

## 6. Fused provider interaction

Fetched from https://developers.google.com/android/reference/com/google/android/gms/location/FusedLocationProviderClient (2026-07-11).

- `FusedLocationProviderClient.getLastLocation()` and the `getCurrentLocation`/`requestLocationUpdates` family all return/deliver plain **`android.location.Location`** objects (e.g. `public abstract Task<Location> getLastLocation()`), the exact same class documented in §§2-3-5 above — there is no special Play-services `Location` subclass. This means, in principle, every getter/hasser discussed above (`hasAltitude`, `hasMslAltitude`, `hasVerticalAccuracy`, `hasMslAltitudeAccuracy`, `getAccuracy`) is available on a fused-provider `Location` the same way it is on a raw `LocationManager` one — it is the same object type.
- **What the FLP reference page does *not* state:** whether the fused provider's internal implementation actually *populates* `hasMslAltitude()`/`getMslAltitudeMeters()` on the `Location` objects it hands back, or whether the app must always run every fused-provider fix through `AltitudeConverter.addMslAltitudeToLocation()` itself to get an MSL value. **This is not documented in the FLP reference page and could not be verified from a primary source in this session — flag this explicitly rather than assume either way.** The safe, documented-API-only approach is for GPSView to always check `hasMslAltitude()` first and only fall back to running `AltitudeConverter` itself if that guard is false (see Recommendation, below).
- **GnssStatus association:** nothing in the FLP reference page mentions `GnssStatus`, per-satellite data, or any built-in linkage between a fused location fix and satellite visibility/used-in-fix counts. This confirms the two subsystems are **architecturally separate and must both be wired up independently**: (a) a position/altitude source — either `LocationManager.requestLocationUpdates(GPS_PROVIDER, ...)` or `FusedLocationProviderClient.requestLocationUpdates(...)` — for position, ellipsoidal altitude, MSL altitude, and their accuracies; and (b) a **separate** `LocationManager.registerGnssStatusCallback(...)` registration (which is a `LocationManager` API, not exposed at all through `FusedLocationProviderClient`/Play services) purely for satellite visible/used-in-fix/C-N0/constellation data. There is no single call that returns both.
- **Deprecated `getExtras()` "satellites" key** — directly confirms the split above from the other side: `Location.getExtras()`'s doc (https://developer.android.com/reference/android/location/Location) states, verbatim: *"satellites - the number of satellites used to derive a GNSS fix. This key was deprecated in API 34 because the information can be obtained through more accurate means, such as by referencing `GnssStatus.usedInFix`."* This is Android's own documentation explicitly telling app authors to stop reading satellite counts off the `Location` object's extras bundle and instead use the separate `GnssStatus` subsystem — i.e. even historically, satellite-count data was never meant to travel bundled inside a `Location`/FLP fix; it has always been `GnssStatus`'s job, and that job is now formalized via `usedInFix(int)`.

**Verdict:** Treat position/altitude (via `FusedLocationProviderClient` or raw `LocationManager.GPS_PROVIDER`) and satellite metadata (via `LocationManager.registerGnssStatusCallback`) as two independent subsystems that GPSView must both instantiate and keep running concurrently — there is no cross-subsystem convenience API. Whether `FusedLocationProviderClient` populates MSL altitude by itself is unverified and should be treated as "maybe not" until confirmed empirically on-device.

---

## Recommendation for the spec

1. **Position + ellipsoidal height + horizontal/vertical accuracy:** request locations via `FusedLocationProviderClient` (or raw `LocationManager.GPS_PROVIDER` if the spec prefers to avoid the Play-services dependency for this particular subsystem — both return the same `android.location.Location` type). For each fix, read:
   - Horizontal accuracy: `location.accuracy`, guarded by `location.hasAccuracy()`.
   - Ellipsoidal height: `location.altitude`, guarded by `location.hasAltitude()`.
   - Vertical (ellipsoidal) accuracy: `location.verticalAccuracyMeters`, guarded by `location.hasVerticalAccuracy()`.

2. **MSL height + MSL accuracy:** first check `location.hasMslAltitude()` on the fix as received. **If false** (e.g. because the provider didn't populate it, or because `FusedLocationProviderClient`'s population behavior turns out to be inconsistent — see §6's flagged gap), run it through a single, app-lifetime-scoped `android.location.altitude.AltitudeConverter` instance:
   ```
   // one instance, reused across fixes, per the class's own "manages an independent cache" doc
   val altitudeConverter = AltitudeConverter()
   // off the main thread:
   altitudeConverter.addMslAltitudeToLocation(context, location)
   ```
   This must run off the main thread (per the documented `@WorkerThread`-equivalent contract — it can throw `IOException` and "may take several seconds"); do this in a coroutine on `Dispatchers.IO` or equivalent, not inline in a location callback. Since min SDK is 34, no `AltitudeConverterCompat` dependency is required. After this call, re-check `hasMslAltitude()`/`hasMslAltitudeAccuracy()` before displaying — `addMslAltitudeToLocation` can still leave them unset if the input location itself is invalid (non-finite altitude, out-of-range lat/lon throws `IllegalArgumentException` instead, which should be caught).
   - **Fallback if MSL is unavailable even after conversion:** this should be rare (only if `location.hasAltitude()` was itself false, since the converter needs a WGS84 altitude as input) — but if it happens, the spec should say to hide/gray out the MSL row rather than show a stale or zero value, mirroring the same guard pattern used everywhere else in this API family.

3. **Satellites visible / used-in-fix:** register a **separate** `LocationManager.registerGnssStatusCallback(Executor, GnssStatus.Callback)` (the API-30 overload) alongside the position stream, requiring `ACCESS_FINE_LOCATION`. In `onSatelliteStatusChanged(status)`, compute:
   - Visible: `status.satelliteCount`
   - Used in fix: `(0 until status.satelliteCount).count { status.usedInFix(it) }`
   Since GPSView is stated to be foreground-only already, the documented precondition ("received while `GPS_PROVIDER` is enabled, and while the client app is in the foreground") is naturally satisfied by the app's own architecture — no extra foreground-service plumbing is needed purely for this API, though double-check this empirically once GPSView's location foreground-service pattern is implemented, since the "sufficient on its own, without a concurrent `requestLocationUpdates(GPS_PROVIDER,...)`" reading in §1 is an inference from doc wording, not a directly quoted guarantee.

4. **Do not** use `Location.getExtras().getInt("satellites")` anywhere — it is explicitly deprecated since API 34 in favor of `GnssStatus.usedInFix`.

5. **Do not** treat `AltitudeConverter` as a Play-services / GMS dependency in the spec's dependency list — it is a plain platform SDK class (`android.location.altitude.AltitudeConverter`, API 34+) requiring no extra Gradle dependency beyond the Android SDK itself, and (per the documented API surface) no network permission.

6. **Open items to verify empirically before finalizing the spec** (not resolvable from reference docs alone):
   - Whether `FusedLocationProviderClient` fixes come with `hasMslAltitude()` already true in practice, or whether GPSView will need to run the `AltitudeConverter` fallback on every single fix.
   - Whether merely registering a `GnssStatus.Callback` (with no separate `requestLocationUpdates(GPS_PROVIDER, ...)` call active) reliably powers on/keeps the GNSS chipset active and yields status callbacks, or whether a concurrent `GPS_PROVIDER` location request is needed in practice despite the docs not stating this as a requirement.
   - The geoid model's actual accuracy figure and coverage extent (including explicit confirmation of Bavaria coverage) — not stated in either `AltitudeConverter` reference page; would require either testing on real Bavaria coordinates or finding the cited PLANS 2023 paper (Julian & Angermann) directly.
