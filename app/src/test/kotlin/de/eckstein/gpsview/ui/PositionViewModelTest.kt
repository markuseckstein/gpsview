package de.eckstein.gpsview.ui

import de.eckstein.gpsview.FakeGnssSource
import de.eckstein.gpsview.FakeLocationSource
import de.eckstein.gpsview.coordinates.LatLon
import de.eckstein.gpsview.coordinates.PositionSnapshot
import de.eckstein.gpsview.coordinates.SatelliteCount
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private fun fixtureSnapshot(satellites: SatelliteCount? = null) =
    PositionSnapshot(
        latLon = LatLon(48.137154, 11.575382),
        horizontalAccuracyM = 4f,
        ellipsoidalAltitudeM = 487.0,
        verticalAccuracyM = 2f,
        mslAltitudeM = 453.0,
        mslAccuracyM = 3f,
        timestamp = 1_752_000_000_000L,
        satellites = satellites,
    )

@OptIn(ExperimentalCoroutinesApi::class)
class PositionViewModelTest {

    // --- Position/satellite merge (permission already granted) ---

    @Test
    fun `no emissions yet yields Acquiring with null satellites`() = runTest(UnconfinedTestDispatcher()) {
        val viewModel = PositionViewModel(FakeLocationSource(), FakeGnssSource())
        viewModel.updatePermissionState(PermissionState.GRANTED)
        val states = mutableListOf<PositionUiState>()
        val job = launch { viewModel.uiState.collect { states.add(it) } }

        assertEquals(PositionUiState.Acquiring(null), states.last())
        job.cancel()
    }

    @Test
    fun `fix without any satellite emission yields Live with null satellites`() =
        runTest(UnconfinedTestDispatcher()) {
            val locationSource = FakeLocationSource()
            val gnssSource = FakeGnssSource()
            val viewModel = PositionViewModel(locationSource, gnssSource)
            viewModel.updatePermissionState(PermissionState.GRANTED)
            val states = mutableListOf<PositionUiState>()
            val job = launch { viewModel.uiState.collect { states.add(it) } }

            val snapshot = fixtureSnapshot()
            locationSource.emit(snapshot)

            val last = states.last()
            assertEquals(PositionUiState.Live(snapshot.copy(satellites = null)), last)
            job.cancel()
        }

    @Test
    fun `satellite emission without a fix yields Acquiring carrying the ratio`() =
        runTest(UnconfinedTestDispatcher()) {
            val locationSource = FakeLocationSource()
            val gnssSource = FakeGnssSource()
            val viewModel = PositionViewModel(locationSource, gnssSource)
            viewModel.updatePermissionState(PermissionState.GRANTED)
            val states = mutableListOf<PositionUiState>()
            val job = launch { viewModel.uiState.collect { states.add(it) } }

            gnssSource.emit(SatelliteCount(used = 0, visible = 12))

            assertEquals(PositionUiState.Acquiring(SatelliteCount(used = 0, visible = 12)), states.last())
            job.cancel()
        }

    @Test
    fun `interleaved emissions merge latest-of-each`() = runTest(UnconfinedTestDispatcher()) {
        val locationSource = FakeLocationSource()
        val gnssSource = FakeGnssSource()
        val viewModel = PositionViewModel(locationSource, gnssSource)
        viewModel.updatePermissionState(PermissionState.GRANTED)
        val states = mutableListOf<PositionUiState>()
        val job = launch { viewModel.uiState.collect { states.add(it) } }

        // satellites arrive first, before any fix
        gnssSource.emit(SatelliteCount(used = 0, visible = 9))
        assertEquals(PositionUiState.Acquiring(SatelliteCount(used = 0, visible = 9)), states.last())

        // first fix arrives, carries the latest satellite ratio
        val firstFix = fixtureSnapshot()
        locationSource.emit(firstFix)
        assertEquals(
            PositionUiState.Live(firstFix.copy(satellites = SatelliteCount(used = 0, visible = 9))),
            states.last(),
        )

        // a satellite-only update after the first fix updates the same snapshot's ratio
        gnssSource.emit(SatelliteCount(used = 6, visible = 9))
        assertEquals(
            PositionUiState.Live(firstFix.copy(satellites = SatelliteCount(used = 6, visible = 9))),
            states.last(),
        )

        // a new fix carries the latest satellite ratio forward
        val secondFix = fixtureSnapshot().copy(timestamp = firstFix.timestamp + 1000)
        locationSource.emit(secondFix)
        assertEquals(
            PositionUiState.Live(secondFix.copy(satellites = SatelliteCount(used = 6, visible = 9))),
            states.last(),
        )

        job.cancel()
    }

    // --- Permission / location-setting state machine (data-model.md, US5/T042) ---

    @Test
    fun `default state before any permission update is NotYetAsked`() = runTest(UnconfinedTestDispatcher()) {
        val viewModel = PositionViewModel(FakeLocationSource(), FakeGnssSource())
        val states = mutableListOf<PositionUiState>()
        val job = launch { viewModel.uiState.collect { states.add(it) } }

        assertEquals(PositionUiState.NotYetAsked, states.last())
        job.cancel()
    }

    @Test
    fun `re-askable denial also yields NotYetAsked at the PositionUiState level`() =
        runTest(UnconfinedTestDispatcher()) {
            val viewModel = PositionViewModel(FakeLocationSource(), FakeGnssSource())
            val states = mutableListOf<PositionUiState>()
            val job = launch { viewModel.uiState.collect { states.add(it) } }

            viewModel.updatePermissionState(PermissionState.DENIED_REASKABLE)

            assertEquals(PositionUiState.NotYetAsked, states.last())
            assertEquals(PermissionState.DENIED_REASKABLE, viewModel.permissionState.value)
            job.cancel()
        }

    @Test
    fun `permanently denied yields PermanentlyDenied`() = runTest(UnconfinedTestDispatcher()) {
        val viewModel = PositionViewModel(FakeLocationSource(), FakeGnssSource())
        val states = mutableListOf<PositionUiState>()
        val job = launch { viewModel.uiState.collect { states.add(it) } }

        viewModel.updatePermissionState(PermissionState.PERMANENTLY_DENIED)

        assertEquals(PositionUiState.PermanentlyDenied, states.last())
        job.cancel()
    }

    @Test
    fun `granting permission after NotYetAsked transitions to Acquiring`() =
        runTest(UnconfinedTestDispatcher()) {
            val viewModel = PositionViewModel(FakeLocationSource(), FakeGnssSource())
            val states = mutableListOf<PositionUiState>()
            val job = launch { viewModel.uiState.collect { states.add(it) } }

            assertEquals(PositionUiState.NotYetAsked, states.last())
            viewModel.updatePermissionState(PermissionState.GRANTED)
            assertEquals(PositionUiState.Acquiring(null), states.last())
            job.cancel()
        }

    @Test
    fun `location setting off yields LocationOff while permission stays granted`() =
        runTest(UnconfinedTestDispatcher()) {
            val viewModel = PositionViewModel(FakeLocationSource(), FakeGnssSource())
            viewModel.updatePermissionState(PermissionState.GRANTED)
            val states = mutableListOf<PositionUiState>()
            val job = launch { viewModel.uiState.collect { states.add(it) } }

            assertEquals(PositionUiState.Acquiring(null), states.last())
            viewModel.updateLocationSettingEnabled(false)
            assertEquals(PositionUiState.LocationOff, states.last())
            job.cancel()
        }

    @Test
    fun `re-enabling the location setting resumes Acquiring`() = runTest(UnconfinedTestDispatcher()) {
        val viewModel = PositionViewModel(FakeLocationSource(), FakeGnssSource())
        viewModel.updatePermissionState(PermissionState.GRANTED)
        viewModel.updateLocationSettingEnabled(false)
        val states = mutableListOf<PositionUiState>()
        val job = launch { viewModel.uiState.collect { states.add(it) } }

        assertEquals(PositionUiState.LocationOff, states.last())
        viewModel.updateLocationSettingEnabled(true)
        assertEquals(PositionUiState.Acquiring(null), states.last())
        job.cancel()
    }

    @Test
    fun `granted via settings after PermanentlyDenied resumes Acquiring (settings-return flow)`() =
        runTest(UnconfinedTestDispatcher()) {
            val viewModel = PositionViewModel(FakeLocationSource(), FakeGnssSource())
            viewModel.updatePermissionState(PermissionState.PERMANENTLY_DENIED)
            val states = mutableListOf<PositionUiState>()
            val job = launch { viewModel.uiState.collect { states.add(it) } }

            assertEquals(PositionUiState.PermanentlyDenied, states.last())

            // user went to app settings, granted the permission, and the app resumed (ON_START)
            viewModel.updatePermissionState(PermissionState.GRANTED)
            assertEquals(PositionUiState.Acquiring(null), states.last())
            job.cancel()
        }

    @Test
    fun `a fix already in progress survives a location-setting flap and back`() =
        runTest(UnconfinedTestDispatcher()) {
            val locationSource = FakeLocationSource()
            val gnssSource = FakeGnssSource()
            val viewModel = PositionViewModel(locationSource, gnssSource)
            viewModel.updatePermissionState(PermissionState.GRANTED)
            val states = mutableListOf<PositionUiState>()
            val job = launch { viewModel.uiState.collect { states.add(it) } }

            val fix = fixtureSnapshot()
            locationSource.emit(fix)
            assertEquals(PositionUiState.Live(fix.copy(satellites = null)), states.last())

            viewModel.updateLocationSettingEnabled(false)
            assertEquals(PositionUiState.LocationOff, states.last())

            viewModel.updateLocationSettingEnabled(true)
            // fresh collection after re-enabling — no stale fix carried over, starts at Acquiring
            assertEquals(PositionUiState.Acquiring(null), states.last())
            job.cancel()
        }
}
