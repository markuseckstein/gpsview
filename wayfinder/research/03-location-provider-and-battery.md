# Research: Location provider strategy and battery behavior for GPSView

Ticket: `wayfinder/tickets/03-research-location-provider-and-battery.md`
Researched: 2026-07-11
Context: personal, strictly foreground-only Android app, Kotlin + Jetpack Compose, min SDK 34 (Android 14+), Google Play services allowed. GPSView shows live coordinates, GNSS metadata, and height while visible on screen, and must stop all location activity the instant it is backgrounded — no background location of any kind. This research builds directly on [wayfinder/research/02-gnss-metadata-and-height.md](02-gnss-metadata-and-height.md), which established that satellite counts (`GnssStatus`) and MSL height (`AltitudeConverter`) are platform APIs, architecturally separate from whichever API supplies the position fix itself.

> Methodology note: as in research file 02, plain `WebFetch` against `developer.android.com/reference/...` and `developers.google.com/android/reference/...` pages returned only the site's navigation shell, not the actual method documentation — confirmed again in this session (tested on `FusedLocationProviderClient` and `LocationManager` reference pages). The same workaround was used: `curl -A "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)" <url>`, which returned full server-rendered HTML including every method signature, "Added in API level" banner, and doc paragraph. Guide-style pages (not class references) — e.g. `/develop/sensors-and-location/location/battery`, the Compose side-effects guide, the Kotlin-coroutines-with-lifecycle guide — rendered correctly under plain `curl` with the same crawler UA and did not need any further workaround. One page (`/training/location/permissions`) turned out to be a redirect stub; it was re-fetched with `curl -L` (follow redirects) to land on its current canonical path `/develop/sensors-and-location/location/permissions`. A couple of URLs guessed by pattern-matching (`/battery/recommendations`, `/battery/use-cases`) 404'd; the real sibling-page URLs (`/battery/optimize`, `/battery/scenarios`) were instead found via a web search restricted to `developer.android.com`, then fetched directly and verified to contain the expected content. Every non-obvious claim below is a near-verbatim quote from that fetched HTML, not from training-data memory; anything not confirmed from a primary source in this session is flagged explicitly rather than guessed.

---

## Summary table

| Decision point | Recommendation | Primary source |
|---|---|---|
| Position-fix API | `FusedLocationProviderClient` | [FusedLocationProviderClient](https://developers.google.com/android/reference/com/google/android/gms/location/FusedLocationProviderClient) |
| Satellite/height metadata API | `LocationManager.registerGnssStatusCallback` (unchanged from file 02) — runs alongside FLP, not instead of it | [GnssStatus.Callback](https://developer.android.com/reference/android/location/GnssStatus.Callback), [LocationManager](https://developer.android.com/reference/android/location/LocationManager) |
| Priority | `Priority.PRIORITY_HIGH_ACCURACY` | [Priority](https://developers.google.com/android/reference/com/google/android/gms/location/Priority), [battery/scenarios guide](https://developer.android.com/develop/sensors-and-location/location/battery/scenarios) |
| Interval (`setIntervalMillis`) | 1–2 seconds | [LocationRequest.Builder](https://developers.google.com/android/reference/com/google/android/gms/location/LocationRequest.Builder), [battery/scenarios guide](https://developer.android.com/develop/sensors-and-location/location/battery/scenarios) |
| `setMinUpdateIntervalMillis` | leave at implicit default (half the interval) | [LocationRequest.Builder](https://developers.google.com/android/reference/com/google/android/gms/location/LocationRequest.Builder) |
| `setMaxUpdateDelayMillis` | 0 (no batching) | [LocationRequest.Builder](https://developers.google.com/android/reference/com/google/android/gms/location/LocationRequest.Builder) |
| Adaptive throttling (slow down when stationary) | Not a documented pattern for this use case — bespoke complexity, not recommended | [battery/scenarios guide](https://developer.android.com/develop/sensors-and-location/location/battery/scenarios) |
| Lifecycle wiring | `DisposableEffect` + `LifecycleEventObserver` on `LocalLifecycleOwner.current.lifecycle` (imperative start/stop calls); `repeatOnLifecycle(Lifecycle.State.STARTED)` inside `lifecycleScope.launch` (if location is exposed as a `Flow` from a ViewModel) | [Compose side-effects guide](https://developer.android.com/develop/ui/compose/side-effects), [Kotlin coroutines with lifecycle-aware components (Views)](https://developer.android.com/topic/libraries/architecture/views/coroutines-views), [RepeatOnLifecycleKt reference](https://developer.android.com/reference/androidx/lifecycle/RepeatOnLifecycleKt) |
| Manifest permissions | `ACCESS_COARSE_LOCATION` **and** `ACCESS_FINE_LOCATION`; **not** `ACCESS_BACKGROUND_LOCATION` | [Permissions guide](https://developer.android.com/develop/sensors-and-location/location/permissions) |

---

## 1. `FusedLocationProviderClient` vs raw `LocationManager.GPS_PROVIDER`

### What each one actually is, per its own reference doc

Fetched from https://developers.google.com/android/reference/com/google/android/gms/location/FusedLocationProviderClient (2026-07-11). Page summary, verbatim: *"FusedLocationProviderClient is the main entry point for interacting with the Fused Location Provider (FLP) and requires either ACCESS_COARSE_LOCATION or ACCESS_FINE_LOCATION permission."* Class doc: *"The main entry point for interacting with the Fused Location Provider (FLP)."*

Fetched from https://developer.android.com/reference/android/location/LocationManager (2026-07-11) — `GPS_PROVIDER` constant doc, verbatim: *"Standard name of the GNSS location provider. If present, this provider determines location using GNSS satellites. The responsiveness and accuracy of location fixes may depend on GNSS signal conditions."* By contrast, `FUSED_PROVIDER`'s doc (Added API 31) reads: *"Standard name of the fused location provider. If present, this provider may combine inputs from several other location providers to provide the best possible location fix. It is implicitly used for all requestLocationUpdates APIs that involve a Criteria."*

### Fix quality and warm-up

The official power-optimization guide is explicit that FLP's `PRIORITY_HIGH_ACCURACY` setting is a *multi-sensor fusion*, not GPS alone. From https://developer.android.com/develop/sensors-and-location/location/battery (2026-07-11), verbatim: *"PRIORITY_HIGH_ACCURACY provides the most accurate location possible, which is computed using as many inputs as necessary (it enables GPS, Wi-Fi, and cell, and uses a variety of Sensors), and may cause significant battery drain."* Raw `GPS_PROVIDER`, per its own doc quoted above, is GNSS-only by construction — no documented fallback to Wi-Fi/cell for a faster initial fix while the GNSS chipset is still acquiring lock.

**On warm-up time / time-to-first-fix specifically:** neither reference page states a numeric time-to-first-fix figure for either provider, so no exact seconds-to-fix comparison can be sourced from primary docs. What *is* documented is the mechanism difference that would explain a warm-up-time gap: FLP is described as capable of using non-GNSS inputs (Wi-Fi/cell) to produce a fix while GNSS is still cold-starting, whereas `GPS_PROVIDER`'s own doc makes no such claim and is scoped purely to GNSS satellites. **Treat "FLP reaches a usable first fix faster than raw `GPS_PROVIDER` on a cold GNSS start" as a reasonable inference from this documented mechanism difference, not a directly quoted number — this would be worth a quick empirical check on a real device but is not required to make the recommendation below**, since FLP is the recommended choice regardless (see Verdict).

Also relevant: `FusedLocationProviderClient.getCurrentLocation(...)` doc explicitly favors itself over rolling your own updates loop for freshness: *"This is the recommended way to get a fresh location, whenever possible, and is safer than alternatives like starting and managing location updates yourself using requestLocationUpdates(). If your app calls requestLocationUpdates(), your app can sometimes consume large amounts of power if location isn't available, or if the request isn't stopped correctly after obtaining a fresh location."* (from https://developer.android.com/develop/sensors-and-location/location/retrieve-current, fetched 2026-07-11). This doesn't apply verbatim to GPSView's continuous "live" case (which does need `requestLocationUpdates`, not a single fix), but it reinforces that FLP is Google's own recommended default across single-fix and continuous cases alike, with `requestLocationUpdates` flagged as the thing that needs careful stop-discipline — directly relevant to §3's lifecycle requirement.

### Battery cost

Both APIs expose the same accuracy/power dials (`Priority`/quality constants — see §2), so the *battery cost of a given priority+interval combination* is not meaningfully different in what's documented; the difference is architectural: FLP centralizes location arbitration across apps and providers in one Play-services-managed component, while raw `GPS_PROVIDER` talks to the GNSS chipset directly with no fusion. Google's own guidance (`retrieve-current` page, quoted above) frames the raw `requestLocationUpdates` approach — which raw `LocationManager` also exposes — as the riskier one for accidental battery drain if not stopped correctly, not as being cheaper.

### Does GPSView need both FusedLocationProviderClient (position) AND GnssStatus (satellites) running simultaneously?

Yes — this is unchanged from file 02's finding and is reconfirmed here from the FLP side. The FLP reference page (fetched above) makes **no mention of `GnssStatus`, per-satellite data, or satellite counts anywhere in its documented method surface** (`getCurrentLocation`, `getLastLocation`, `requestLocationUpdates`, `getLocationAvailability`, `flushLocations` — none of these return or relate to satellite visibility). `GnssStatus.Callback` registration is exclusively a `LocationManager` API (`registerGnssStatusCallback(Executor, GnssStatus.Callback)`, confirmed again this session at https://developer.android.com/reference/android/location/LocationManager) and is **not exposed anywhere on `FusedLocationProviderClient` or any other Play-services `com.google.android.gms.location.*` class**. This is the same conclusion file 02 reached and it holds after specifically re-checking the FLP page for this research: **the two subsystems are separate APIs from separate packages (`com.google.android.gms.location` vs `android.location`) and must both be instantiated independently — GPSView cannot get satellite data through FLP no matter which `LocationRequest`/priority it uses.**

**Compatibility / duplicate-activation question:** Can both run at once without conflict or wasted battery? Nothing in either reference page documents an explicit conflict between an active FLP request and a concurrent `registerGnssStatusCallback` registration — they are independent registrations against independent client objects (`FusedLocationProviderClient` obtained from `LocationServices`, vs `LocationManager` obtained from `Context.getSystemService`). The `GnssStatus.Callback` precondition, reconfirmed this session, is: *"GNSS status information will only be received while the GPS_PROVIDER is enabled, and while the client app is in the foreground"* (https://developer.android.com/reference/android/location/LocationManager). Since GPSView's FLP request will use `PRIORITY_HIGH_ACCURACY`, and that priority is documented to *"enable GPS"* (battery guide, quoted above), the FLP request itself should already satisfy the GnssStatus precondition — i.e., the GNSS chipset session that FLP's high-accuracy request activates is very likely the *same* underlying chipset session that feeds `GnssStatus.Callback`, not a second independent one. **This is inferred from the documented behavior of `PRIORITY_HIGH_ACCURACY` ("enables GPS") combined with the documented `GnssStatus.Callback` precondition ("while GPS_PROVIDER is enabled"), not from an explicit primary-source sentence stating "no duplicate chipset activation occurs" — no such sentence was found in either reference page, so this should still be smoke-tested on a real device, consistent with the same caveat file 02 already flagged.**

**Verdict:** Use `FusedLocationProviderClient` for the position fix (documented to fuse GPS/Wi-Fi/cell/sensors under `PRIORITY_HIGH_ACCURACY`, and it's Google's stated recommended default over manually driving `GPS_PROVIDER`). Run `LocationManager.registerGnssStatusCallback(Executor, GnssStatus.Callback)` concurrently and independently for satellite/height metadata — there is no single API providing both, confirming file 02's finding from the FLP side as well. The two should not meaningfully double-activate the GNSS chipset given `PRIORITY_HIGH_ACCURACY`'s documented behavior, though this exact non-duplication claim is an inference, not a quoted guarantee.

---

## 2. Recommended `LocationRequest` parameters for "live but frugal"

### Priority levels — exact documented semantics

Fetched from https://developers.google.com/android/reference/com/google/android/gms/location/Priority (2026-07-11). Interface doc: *"Location power vs accuracy priority levels to be used with APIs within FusedLocationProviderClient. Priority values have been intentionally chosen to match the framework QUALITY constants, and the values are specified such that higher priorities should always have lower values and vice versa."*

- **`PRIORITY_HIGH_ACCURACY`** (constant value `100`) — *"Requests a tradeoff that favors highly accurate locations at the possible expense of additional power usage."*
- **`PRIORITY_BALANCED_POWER_ACCURACY`** (constant value `102`, the documented default — see below) — *"Requests a tradeoff that is balanced between location accuracy and power usage."*
- **`PRIORITY_LOW_POWER`** (constant value `104`) — *"Requests a tradeoff that favors low power usage at the possible expense of location accuracy."*
- **`PRIORITY_PASSIVE`** (constant value `105`) — *"Ensures that no extra power will be used to derive locations. This enforces that the request will act as a passive listener that will only receive 'free' locations calculated on behalf of other clients, and no locations will be calculated on behalf of only this request."*

The platform equivalent, `android.location.LocationRequest`'s `QUALITY_*` constants (fetched from https://developer.android.com/reference/android/location/LocationRequest, Added API 31), mirror these almost word for word: `QUALITY_HIGH_ACCURACY` — *"...providing very accurate locations at the expense of potentially increased power usage"*; `QUALITY_BALANCED_POWER_ACCURACY` — *"...equally balancing power and accuracy constraints"*; `QUALITY_LOW_POWER` — *"...providing less accurate locations in order to save power."* (There is no platform `QUALITY_PASSIVE`; passive-style requests on the platform API instead use the special `PASSIVE_INTERVAL` constant on `getIntervalMillis()`.)

### The official use-case guidance for exactly GPSView's scenario

This is the strongest primary-source hit of this research: Google's own "Optimize location use for real-world scenarios" guide has a scenario that is a near-exact description of GPSView. Fetched from https://developer.android.com/develop/sensors-and-location/location/battery/scenarios (2026-07-11), verbatim:

> **User visible or foreground updates**
> Example: A mapping app that needs frequent, accurate updates with very low latency. All updates happen in the foreground: the user starts an activity, consumes location data, and then stops the activity after a short time.
> - Use the `setPriority()` method with a value of `PRIORITY_HIGH_ACCURACY` or `PRIORITY_BALANCED_POWER_ACCURACY`.
> - The interval specified in the `setInterval()` method depends on the use case: for real time scenarios, set the value to few seconds; otherwise, limit to a few minutes (approximately two minutes or greater is recommended to minimize battery usage).

(Note: this guide page still uses the pre-Builder method names `setPriority()`/`setInterval()`, which predate the current `LocationRequest.Builder` API; the semantics carry over directly to `Builder(priority, intervalMillis)`/`setIntervalMillis()` on the modern API used elsewhere in this document — confirmed by cross-referencing the sibling `/battery/optimize` guide page, which *does* use the current Builder syntax, e.g. `LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10 * 60 * 1000)`.)

This directly answers the ticket's "is a 1-2 second interval reasonable" question: yes — Google's own guidance for this exact "mapping app, foreground-only, frequent accurate low-latency updates" scenario says to use `PRIORITY_HIGH_ACCURACY` and "a few seconds" for real-time cases.

### `setIntervalMillis`, `setMinUpdateIntervalMillis`, `setMaxUpdateDelayMillis` — exact contracts and defaults

All fetched from https://developers.google.com/android/reference/com/google/android/gms/location/LocationRequest.Builder (2026-07-11); the platform `android.location.LocationRequest.Builder` (API 31, fetched from https://developer.android.com/reference/android/location/LocationRequest.Builder) documents the same three methods with materially identical wording, confirming this isn't a GMS-only quirk.

- **`setIntervalMillis(long intervalMillis)`** — *"Sets the desired interval of location updates. Location updates may arrive faster than this interval (but no faster than specified by setMinUpdateIntervalMillis(long)) or slower than this interval (if the request is being throttled for example)... An interval of 0 implies that location should be delivered as fast as the device can support, and some Android devices can deliver location much faster than 1Hz. Prefer never to use an interval of 0 unless your application can support much faster than anticipated location deliveries. An interval of 1 second is likely sufficient for the vast majority of 'high location rate' applications."* No documented global default (it's a required Builder constructor argument).
- **`setMinUpdateIntervalMillis(long minUpdateIntervalMillis)`** — *"Sets the fastest allowed interval of location updates... This may be set to the special value IMPLICIT_MIN_UPDATE_INTERVAL in which case the minimum update interval will be half the interval... The default value is IMPLICIT_MIN_UPDATE_INTERVAL."*
- **`setMaxUpdateDelayMillis(long maxUpdateDelayMillis)`** — *"Sets the longest a location update may be delayed. This parameter controls location batching behavior. If this is set to a value greater than 0 and at least 2x larger than the interval specified by setIntervalMillis(long), then a device may (but is not required to) save power by delivering locations in batches... The default value is 0."*
- **`setPriority(int priority)`** — *"The default value is Priority.PRIORITY_BALANCED_POWER_ACCURACY."* (i.e., if GPSView doesn't explicitly set high accuracy, it silently gets the balanced tier, not high accuracy — worth calling out since the ticket's live-display requirement needs an explicit override.)
- **`setWaitForAccurateLocation(boolean)`** — default `true`: *"If set to true and this request is Priority.PRIORITY_HIGH_ACCURACY, this will delay delivery of initial low accuracy locations for a small amount of time in case a high accuracy location can be delivered instead."* Relevant to perceived "warm-up": leaving this at its default trades a small amount of extra initial latency for not flashing a low-accuracy fix first — reasonable for a live display that should look right from the first shown fix.

### Concrete recommended numbers

Given the above, and directly following Google's own "User visible or foreground updates" scenario:

- **Priority: `Priority.PRIORITY_HIGH_ACCURACY`** — matches the documented scenario recommendation and the app's "live" requirement (GPSView isn't a background tracker where `BALANCED_POWER_ACCURACY` would also fit; it's explicitly the "real time" sub-case the guide calls out).
- **`setIntervalMillis`: 1000–2000 ms (1–2 seconds)** — directly within the documented "for real time scenarios, set the value to few seconds" guidance, and consistent with the `Builder`'s own doc that *"an interval of 1 second is likely sufficient for the vast majority of 'high location rate' applications"*.
- **`setMinUpdateIntervalMillis`: leave at the implicit default** (half the interval) unless testing shows the GNSS chipset delivering fixes fast enough to be worth capturing — there's no documented reason to override this for a single-app, non-passive, foreground-only request.
- **`setMaxUpdateDelayMillis`: 0 (no batching)** — batching is explicitly a background/latency-tolerant power-saving feature (*"If your app doesn't immediately need a location update, you should pass the largest possible value to the setMaxUpdateDelayMillis() method, effectively trading latency for more data and battery efficiency"* — from https://developer.android.com/develop/sensors-and-location/location/battery, fetched 2026-07-11). GPSView's whole point is showing the *live* fix the instant it's available, so batching would work directly against the product requirement.

### Raw-platform equivalents

- **`android.location.LocationRequest` / `LocationRequest.Builder`** (API 31+, fetched from https://developer.android.com/reference/android/location/LocationRequest and .../LocationRequest.Builder, 2026-07-11) exposes the same method names (`setIntervalMillis`, `setMinUpdateIntervalMillis`, `setMaxUpdateDelayMillis`, `setQuality`) with materially identical contracts and defaults (e.g. `setQuality`: *"Defaults to LocationRequest.QUALITY_BALANCED_POWER_ACCURACY"*; `setMaxUpdateDelayMillis`: *"Defaults to 0, which represents no batching allowed"*). Since GPSView already recommends FLP (§1), this platform class is only relevant if the spec later wants a Play-services-free path via `LocationManager.requestLocationUpdates(String provider, LocationRequest locationRequest, Executor executor, LocationListener listener)` (Added API 31, confirmed at https://developer.android.com/reference/android/location/LocationManager).
- **`LocationManager.requestLocationUpdates(String provider, long minTimeMs, float minDistanceM, LocationListener listener)`** (Added API level 1, still current — confirmed at https://developer.android.com/reference/android/location/LocationManager, 2026-07-11): *"Register for location updates from the given provider with the given arguments... Prior to Jellybean, the minTime parameter was only a hint, and some location provider implementations ignored it. For Jellybean and onwards however, it is mandatory for Android compatible devices to observe both the minTime and minDistance parameters."* This is the true legacy raw-platform equivalent, but it is coarser than the modern `LocationRequest`-based overloads (no priority/quality control, no batching control) — not recommended over FLP for GPSView.

### Is adaptive throttling (slowing down when stationary) a standard, documented pattern?

**No** — this is the clearest finding of this section. Google's own scenario-based guidance (`/battery/scenarios`, quoted above) enumerates several distinct throttling *patterns* it does document and recommend:
- Geofence-gated start/stop ("Start updates when a user is at a specific location").
- Activity-Recognition-gated start/stop ("Start updates based on the user's activity state" — e.g. only requesting updates while the Activity Recognition API reports the user is driving/biking).
- Passive listening with `PRIORITY_PASSIVE`/old `PRIORITY_NO_POWER` for background use cases where the app opportunistically consumes other apps' fixes.
- Batching via `setMaxUpdateDelayMillis` for latency-tolerant background cases.

**None of these is "detect the device is stationary and slow the polling interval while the screen is on and the app is foregrounded and already actively displaying location."** The one scenario that matches GPSView exactly — "User visible or foreground updates" — gives a single flat interval recommendation ("a few seconds") with no stationary-detection carve-out, and every throttling mechanism the guide *does* document (geofencing, Activity Recognition, passive mode, batching) is explicitly framed as a **background** power-saving technique, not a foreground-live-display one. Building bespoke stationary-detection throttling (e.g. via the accelerometer/significant-motion sensor, gated on top of the FLP interval) would therefore be extra complexity with no official pattern backing it for this specific use case, and would trade off against the live-display's whole purpose (showing the current fix promptly, including the fact that the device *hasn't* moved). **Verdict for this sub-question: skip it. Use a flat 1–2 second `PRIORITY_HIGH_ACCURACY` interval; do not spec adaptive/motion-based throttling for a personal single-user app.**

**Verdict:** `Priority.PRIORITY_HIGH_ACCURACY`, `setIntervalMillis(1000–2000)`, implicit-default `setMinUpdateIntervalMillis`, `setMaxUpdateDelayMillis(0)`. No adaptive throttling — not a documented pattern for this exact scenario, and the one official use-case that matches GPSView doesn't call for it.

---

## 3. Compose/lifecycle pattern for foreground-only start/stop, and manifest permissions

### `repeatOnLifecycle(Lifecycle.State.STARTED)` — current documentation status

Searching the primary, currently-live "Use Kotlin coroutines with lifecycle-aware components" doc (https://developer.android.com/topic/libraries/architecture/coroutines, fetched 2026-07-11, "Last updated 2026-06-16") found that **this page has been rewritten to a Compose-first structure** (`ViewModelScope`, `LaunchedEffect`, `collectAsStateWithLifecycle`) and **no longer mentions `repeatOnLifecycle` directly** — it instead recommends `collectAsStateWithLifecycle()` for Compose flow collection, stating: *"By default, collection begins when the lifecycle is STARTED and stops when the lifecycle is STOPPED. To override this default behavior, pass in the minActiveState parameter..."* (`collectAsStateWithLifecycle` uses `repeatOnLifecycle` internally, but that implementation detail isn't spelled out on this page). The page links out to a **Views-specific sibling page**, "Use Kotlin coroutines with lifecycle-aware components (Views)" (https://developer.android.com/topic/libraries/architecture/views/coroutines-views, fetched 2026-07-11), which **does** document `repeatOnLifecycle` explicitly, verbatim:

> "Restartable Lifecycle-aware coroutines... For these cases, Lifecycle and LifecycleOwner provide the suspend `repeatOnLifecycle` API that does exactly that. The following example contains a code block that runs every time the associated Lifecycle is at least in the STARTED state and cancels when the Lifecycle is STOPPED":
> ```kotlin
> viewLifecycleOwner.lifecycleScope.launch {
>     // repeatOnLifecycle launches the block in a new coroutine every time the
>     // lifecycle is in the STARTED state (or above) and cancels it when it's STOPPED.
>     viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
>         viewModel.someDataFlow.collect { /* Process item */ }
>     }
> }
> ```
> "Warning: Prefer collecting flows using the repeatOnLifecycle API instead of collecting inside the launchWhenX APIs. As the latter APIs suspend the coroutine instead of canceling it when the Lifecycle is STOPPED, upstream flows are kept active in the background, potentially emitting new items and wasting resources."

The underlying function is confirmed live and current at the class reference: https://developer.android.com/reference/androidx/lifecycle/RepeatOnLifecycleKt (fetched 2026-07-11), Added in Jetpack Lifecycle version **2.8.0**:
```
public static final void repeatOnLifecycle(@NonNull Lifecycle receiver, @NonNull Lifecycle.State state, @NonNull SuspendFunction1<@NonNull CoroutineScope, Unit> block)
```
Sample from the reference page itself, verbatim: *"Runs the block of code in a coroutine when the lifecycle is at least STARTED. The coroutine will be cancelled when the ON_STOP event happens and will restart executing if the lifecycle receives the ON_START event again."*

**Takeaway on this sub-point:** `repeatOnLifecycle(Lifecycle.State.STARTED)` is real, current (Jetpack Lifecycle 2.8.0+), and exactly matches the "restart on ON_START, cancel on ON_STOP" semantics GPSView needs — but it's now documented as the *Views-world* pattern (or the low-level primitive behind `collectAsStateWithLifecycle`), not the headline Compose pattern in Google's newest docs. It is the right tool if GPSView exposes location as a `Flow`/`StateFlow` from a ViewModel and needs to *collect* it only while visible. It is not, by itself, the tool for *starting and stopping the underlying `FusedLocationProviderClient.requestLocationUpdates`/`registerGnssStatusCallback` calls themselves* — those are one-shot register/unregister calls, not a stream to "collect," so they fit more naturally into the `DisposableEffect` pattern below.

### `DisposableEffect` + `LifecycleEventObserver` — the Compose-native pattern for imperative start/stop

Fetched from https://developer.android.com/develop/ui/compose/side-effects (2026-07-11), section "DisposableEffect: effects that require cleanup", verbatim:

> "As an example, you might want to send analytics events based on Lifecycle events by using a LifecycleObserver. To listen for those events in Compose, use a DisposableEffect to register and unregister the observer when needed."
> ```kotlin
> @Composable
> fun HomeScreen(
>     lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
>     onStart: () -> Unit, // Send the 'started' analytics event
>     onStop: () -> Unit   // Send the 'stopped' analytics event
> ) {
>     val currentOnStart by rememberUpdatedState(onStart)
>     val currentOnStop by rememberUpdatedState(onStop)
>     DisposableEffect(lifecycleOwner) {
>         val observer = LifecycleEventObserver { _, event ->
>             if (event == Lifecycle.Event.ON_START) {
>                 currentOnStart()
>             } else if (event == Lifecycle.Event.ON_STOP) {
>                 currentOnStop()
>             }
>         }
>         lifecycleOwner.lifecycle.addObserver(observer)
>         onDispose {
>             lifecycleOwner.lifecycle.removeObserver(observer)
>         }
>     }
> }
> ```
> "A DisposableEffect must include an onDispose clause as the final statement in its block of code. Otherwise, the IDE displays a build-time error."

This maps directly onto GPSView's requirement: swap the analytics `onStart`/`onStop` lambdas for calls that (a) create/reuse a `FusedLocationProviderClient` and call `requestLocationUpdates(locationRequest, executor, callback)` plus `LocationManager.registerGnssStatusCallback(executor, callback)` on `ON_START`, and (b) call `removeLocationUpdates(callback)` plus `unregisterGnssStatusCallback(callback)` on `ON_STOP` (or equivalently in the `onDispose` block, to also cover the composable leaving composition entirely). Because `ON_STOP` fires the instant the Activity/screen is no longer visible (backgrounded, screen off, or another app covers it), this gives exactly the "stops instantly when backgrounded" behavior the map requires, using an officially documented Compose idiom.

### Which pattern should GPSView actually use?

Both are legitimate, official patterns for different shapes of work, per the docs fetched above:
- **`DisposableEffect` + `LifecycleEventObserver`** — best fit for the *imperative register/unregister calls themselves* (`requestLocationUpdates`/`removeLocationUpdates`, `registerGnssStatusCallback`/`unregisterGnssStatusCallback`), directly in the composable or a small wrapper composable, with no ViewModel required.
- **`repeatOnLifecycle(Lifecycle.State.STARTED)` inside `lifecycleScope.launch`** (or its Compose-idiomatic wrapper, `collectAsStateWithLifecycle()`) — best fit if GPSView's architecture instead exposes location/GNSS updates as a `Flow`/`StateFlow` from a ViewModel (e.g. wrapping `requestLocationUpdates` in a `callbackFlow`), and the screen just needs to *collect* that flow only while `STARTED`. In that architecture the flow's own `awaitClose { removeLocationUpdates(...) }` block (the standard `callbackFlow` cleanup mechanism) is what actually calls `removeLocationUpdates`, and `repeatOnLifecycle`/`collectAsStateWithLifecycle` is what starts/stops the *collection* that keeps the flow alive.

Either is a legitimate, primary-source-documented choice; the pick is a matter of GPSView's broader architecture (ViewModel-based `Flow` exposure vs. direct composable-level effect), not something the docs mandate one way. **Given GPSView is a small personal app with (per the map) a single main screen, `DisposableEffect` + `LifecycleEventObserver` directly wrapping the FLP/GnssStatus register-unregister calls is the simpler of the two documented options and doesn't require standing up a ViewModel+Flow layer purely to satisfy the lifecycle requirement.**

### Manifest permissions

Fetched from https://developer.android.com/develop/sensors-and-location/location/permissions (2026-07-11; this URL is the current canonical location — the older `/training/location/permissions` path now 301-redirects here).

- **Foreground location, verbatim:** *"You declare a need for foreground location when your app requests either the ACCESS_COARSE_LOCATION permission or the ACCESS_FINE_LOCATION permission"*, followed immediately by this exact manifest snippet from the guide:
  ```xml
  <manifest ...>
      <!-- Always include this permission -->
      <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
      <!-- Include only if your app benefits from precise location access. -->
      <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
  </manifest>
  ```
  This directly answers the ticket's coarse-as-co-requisite question: **yes** — the official guide's own reference snippet declares `ACCESS_COARSE_LOCATION` unconditionally ("Always include this permission") *alongside* `ACCESS_FINE_LOCATION`, not fine-only. GPSView clearly "benefits from precise location access" (its entire purpose is precise coordinates/GNSS metadata), so both lines apply, matching the guide's own annotated example exactly.

- **Background location, verbatim:** *"On Android 10 (API level 29) and higher, you must declare the ACCESS_BACKGROUND_LOCATION permission in your app's manifest in order to request background location access at runtime."* And under "The system considers your app to be using foreground location if..." the two qualifying conditions given are: *"An activity that belongs to your app is visible"* or *"Your app is running a foreground service"* (with a persistent notification) — GPSView's design (visible-activity-only, no service) falls squarely into the first, non-background bucket, so `ACCESS_BACKGROUND_LOCATION` is correctly **excluded** per the app's own explicit foreground-only requirement; nothing in the guide suggests it's needed, and declaring it would in fact contradict the "loosest licensing / simplest permission surface" posture implied by the personal-sideload distribution model.

- **Accuracy note, verbatim, relevant to why fine (not just coarse) is needed:** *"Approximate ... Your app can receive locations at this level of accuracy when you declare the ACCESS_COARSE_LOCATION permission but not the ACCESS_FINE_LOCATION permission... Precise ... Your app can receive locations at this level of accuracy when you declare the ACCESS_FINE_LOCATION permission."* Confirms `ACCESS_FINE_LOCATION` is the permission actually gating the GNSS-grade fix and metadata (satellite counts, sub-meter accuracy) GPSView displays; `ACCESS_COARSE_LOCATION` alone would silently degrade the app to city-block-level Wi-Fi/cell fixes, which is inconsistent with the whole GNSS-metadata feature set researched in file 02.

**Verdict:** Use `DisposableEffect` with a `LifecycleEventObserver` added to `LocalLifecycleOwner.current.lifecycle`, starting `FusedLocationProviderClient.requestLocationUpdates(...)` and `LocationManager.registerGnssStatusCallback(...)` on `ON_START`/inside the effect body, and calling `removeLocationUpdates(...)` / `unregisterGnssStatusCallback(...)` in `onDispose` (mirroring `ON_STOP`). If GPSView's architecture later grows a ViewModel+Flow layer for location, `repeatOnLifecycle(Lifecycle.State.STARTED)` (via `lifecycleScope.launch` or the Compose `collectAsStateWithLifecycle()` wrapper) is the documented alternative for gating the *collection* side. Declare both `ACCESS_COARSE_LOCATION` and `ACCESS_FINE_LOCATION` in the manifest, per the permissions guide's own annotated snippet; do **not** declare `ACCESS_BACKGROUND_LOCATION`, consistent with the app's foreground-only requirement and the guide's own foreground/background boundary definition.

---

## Recommendation for the spec

1. **Position fix:** Use `FusedLocationProviderClient` (`com.google.android.gms.location`), not raw `LocationManager.GPS_PROVIDER`. It is Google's documented default, fuses GPS/Wi-Fi/cell/sensors under high accuracy, and is explicitly recommended over hand-rolled `requestLocationUpdates` management in Google's own guidance. Run it **concurrently and independently** with `LocationManager.registerGnssStatusCallback(Executor, GnssStatus.Callback)` for satellite/height metadata (per file 02) — there is no single API that provides both; this is confirmed again from the FLP side in this research (no `GnssStatus` reference anywhere in the FLP class docs).
2. **Request parameters:**
   - `Priority.PRIORITY_HIGH_ACCURACY` — matches Google's own "User visible or foreground updates / mapping app" scenario recommendation exactly.
   - `setIntervalMillis(1000L)` to `setIntervalMillis(2000L)` (1–2 seconds) — within the documented "few seconds for real-time scenarios" guidance and the Builder doc's own "an interval of 1 second is likely sufficient for the vast majority of 'high location rate' applications."
   - `setMinUpdateIntervalMillis` — leave unset (implicit default: half the interval).
   - `setMaxUpdateDelayMillis(0)` — no batching; batching is a documented background/latency-tolerant technique that directly conflicts with "live" display.
   - `setWaitForAccurateLocation(true)` (the default) — avoids flashing a low-accuracy fix first.
   - **No adaptive/motion-based throttling.** Not a documented pattern for this exact foreground-live-display scenario; every throttling technique Google documents (geofencing, Activity Recognition gating, passive mode, batching) targets background use cases. Spec should explicitly note this was considered and rejected as unwarranted bespoke complexity for a personal single-user app.
3. **Lifecycle wiring:** Use `DisposableEffect(lifecycleOwner) { ... LifecycleEventObserver ... onDispose { ... } }` with `lifecycleOwner = LocalLifecycleOwner.current` (the documented Compose side-effects pattern) to start `requestLocationUpdates`/`registerGnssStatusCallback` on `ON_START` and stop both (`removeLocationUpdates`/`unregisterGnssStatusCallback`) in `onDispose`/on `ON_STOP`. If a ViewModel+`Flow` layer is introduced later, `Lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED)` inside `lifecycleScope.launch` (or `collectAsStateWithLifecycle()`) is the documented alternative for gating flow collection — both are legitimate per current Android docs; `DisposableEffect` is the simpler starting point for GPSView's single-screen scope.
4. **Manifest permissions:** declare exactly
   ```xml
   <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
   <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
   ```
   Do **not** declare `android.permission.ACCESS_BACKGROUND_LOCATION` — GPSView's foreground-only design (visible-activity-only, no foreground service) means it doesn't qualify as "background location" per the platform's own definition, and adding the permission would contradict the app's explicit requirement.

### Open items to verify empirically

- **Numeric time-to-first-fix comparison** between `FusedLocationProviderClient` (`PRIORITY_HIGH_ACCURACY`) and raw `GPS_PROVIDER` on a real device — no primary source gives seconds-to-fix numbers for either; the recommendation to prefer FLP rests on documented sensor-fusion behavior, not a measured warm-up-time delta.
- **Whether an active FLP `PRIORITY_HIGH_ACCURACY` request and a concurrent `GnssStatus.Callback` registration share a single GNSS chipset session or could ever double-activate/duplicate power draw** — inferred from the "enables GPS" wording on `PRIORITY_HIGH_ACCURACY` plus the "while GPS_PROVIDER is enabled" precondition on `GnssStatus.Callback`, but no primary source states this non-duplication explicitly. This is the same class of gap file 02 flagged from the GnssStatus side; worth a single combined smoke test (register both, confirm only one GNSS "cold start" indicator/onFirstFix event, watch power via Android Studio's Energy Profiler) once a build exists.
- **Exact behavior on `ON_STOP` timing across device manufacturers/launchers** — the `DisposableEffect`/`LifecycleEventObserver` pattern is documented to fire `ON_STOP` when the Activity is no longer visible, but real-world instant-ness (e.g. multi-window/split-screen edge cases, or a quick task-switcher swipe) wasn't separately verified against a primary source beyond the standard `Lifecycle` state-machine docs; should be confirmed on-device during the prototype phase given the ticket's "stops instantly" wording is a hard product requirement.
