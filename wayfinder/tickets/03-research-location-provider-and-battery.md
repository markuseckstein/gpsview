---
name: Research location provider strategy and battery behavior
labels: [wayfinder:research]
status: closed
assignee: claude
blocked-by: [02-research-gnss-metadata-and-height.md]
---

## Question

What location-request strategy gives fluid live updates in the foreground with minimal battery cost, and how is "stops instantly when backgrounded" implemented cleanly?

To resolve (AFK research, `/research`) — builds on the GNSS-metadata findings (fused vs `GPS_PROVIDER` may already be constrained by what satellite/height data requires):

- Fused location provider vs raw `LocationManager.GPS_PROVIDER` for a live foreground display: fix quality, warm-up time, battery cost, and whether both must run simultaneously (fused for position, GnssStatus for satellites).
- Recommended request parameters (interval, priority) for a "live but frugal" screen-on use case; whether adaptive throttling is worth speccing.
- Lifecycle pattern in Compose (e.g. `repeatOnLifecycle(STARTED)`) guaranteeing no background access — and what manifest permissions to request (`ACCESS_FINE_LOCATION` only, explicitly no background permission).

Deliverable: markdown summary linked here, with the concrete strategy the spec should mandate.

## Resolution

Findings: [wayfinder/research/03-location-provider-and-battery.md](../research/03-location-provider-and-battery.md).

- **Position fix:** `FusedLocationProviderClient` (Google Play services), not raw `LocationManager.GPS_PROVIDER` — documented to fuse GPS/Wi-Fi/cell/sensors under high accuracy and is Google's recommended default over hand-rolled `requestLocationUpdates` management. Runs concurrently and independently with `LocationManager.registerGnssStatusCallback` for satellite/height metadata (confirming file 02's finding from the FLP side too) — no single API provides both.
- **Request parameters:** `Priority.PRIORITY_HIGH_ACCURACY`, `setIntervalMillis` 1–2 seconds, implicit-default `setMinUpdateIntervalMillis`, `setMaxUpdateDelayMillis(0)` (no batching), `setWaitForAccurateLocation(true)` (default). This matches Google's own "User visible or foreground updates" scenario almost verbatim. **No adaptive/motion-based throttling** — not a documented pattern for foreground-live display; every official throttling technique (geofencing, Activity Recognition, passive mode, batching) targets background use, so it's rejected as unwarranted complexity.
- **Lifecycle wiring:** `DisposableEffect(lifecycleOwner) { LifecycleEventObserver ... onDispose { ... } }` on `LocalLifecycleOwner.current.lifecycle` — start `requestLocationUpdates`/`registerGnssStatusCallback` on `ON_START`, stop both in `onDispose`/`ON_STOP`. Simpler than the alternative documented pattern (`repeatOnLifecycle(STARTED)` + ViewModel/Flow), which is only worth it if a ViewModel+Flow layer is introduced later.
- **Manifest permissions:** declare both `ACCESS_COARSE_LOCATION` and `ACCESS_FINE_LOCATION` (per the official guide's own annotated snippet — coarse is "always include", fine is needed for the GNSS-grade accuracy this app displays). Explicitly **no** `ACCESS_BACKGROUND_LOCATION` — GPSView's visible-activity-only, no-service design doesn't qualify as background use per the platform's own definition.
- **Open items flagged for later on-device verification:** numeric time-to-first-fix (FLP vs raw GPS_PROVIDER), whether FLP + GnssStatus share a single GNSS chipset session or could double-activate, and real-world `ON_STOP` instant-ness across manufacturers/launchers (split-screen, quick task-switch).
