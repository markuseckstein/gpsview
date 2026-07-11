# Contract: Location & GNSS Source Interfaces

**Package**: `data` — these two interfaces are the **enforced boundary** keeping Android out of the merge/derivation logic (constitution I). The ViewModel depends only on the interfaces; real implementations are wired by a `ViewModelProvider.Factory`, tests pass fakes.

## `LocationSource`

```kotlin
interface LocationSource {
    /** Cold flow of enriched position fixes. Registers on collect, unregisters on cancel. */
    fun positions(): Flow<PositionSnapshot>   // always emitted with satellites = null;
                                              // the ViewModel merges in GnssSource's latest count
}
```

**Behavioral contract** (SPEC.md §4.1–4.2):

1. **Cold**: no location registration exists before collection; `awaitClose { removeLocationUpdates(...) }` tears down on cancellation. Cancellation → unregistration is immediate (constitution II; empirical check E5).
2. **Request shape**: `PRIORITY_HIGH_ACCURACY` (explicit), `setIntervalMillis(1000)`, min-update interval left at implicit default, `setMaxUpdateDelayMillis(0)` (no batching), `setWaitForAccurateLocation(true)`.
3. **Every emission is already MSL-enriched**:
   - `hasMslAltitude()` already true → pass through.
   - else `addMslAltitudeToLocation(context, location)` via a **single app-lifetime `AltitudeConverter`** on `Dispatchers.IO`; `IOException`/`IllegalArgumentException` → MSL fields null. Never the API-35 `tryAddMslAltitudeToLocation`.
   - Re-check `hasMslAltitude()`/`hasMslAltitudeAccuracy()` after conversion; map to nullable fields.
4. **Field mapping guards**: every optional field mapped iff its `has…()` returns true (see [data-model.md](../data-model.md)).
5. Enrichment latency (first geoid load can take seconds) may delay **that fix's emission**, never block the collector thread, and must not be re-paid per fix (converter reused).

## `GnssSource`

```kotlin
interface GnssSource {
    /** Cold flow of satellite summaries. May never emit; must never error the UI. */
    fun satellites(): Flow<SatelliteCount>
}
```

**Behavioral contract** (SPEC.md §4.3):

1. **Cold**: `registerGnssStatusCallback(Executor, Callback)` (API-30 overload) on collect; unregister in `awaitClose`.
2. Per `GnssStatus`: `visible = satelliteCount`, `used = (0 until satelliteCount).count { usedInFix(it) }`.
3. **Never** read `Location.getExtras().getInt("satellites")` (deprecated in API 34).
4. Absence-tolerant: the flow being silent (no GNSS status yet, indoors, no permission edge) must leave position emissions unaffected — satellites stay null on the combined snapshot.

## ViewModel merge contract (tested with fakes)

- `combine(positions, satellites)` → `StateFlow<PositionUiState>`; latest-of-each semantics.
- Fix without any satellite emission → `Live` with `satellites = null`.
- Satellite emission without fix → `Acquiring(satellites)` — the acquiring UI renders the live `0/n` ratio.
- No fix yet, permission granted → `Acquiring`.
- Collection is lifecycle-scoped by the UI (`collectAsStateWithLifecycle()`); the ViewModel never launches unscoped collection of the sources.

## Fake implementations for tests

`FakeLocationSource` / `FakeGnssSource` backed by `MutableSharedFlow`, letting tests script emission order and assert: merge behavior, `Acquiring → Live` transition, null-satellite `Live`, and teardown via flow-collection cancellation.
