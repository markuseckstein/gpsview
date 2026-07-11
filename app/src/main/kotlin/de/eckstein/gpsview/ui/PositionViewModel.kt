package de.eckstein.gpsview.ui

import androidx.lifecycle.ViewModel
import de.eckstein.gpsview.coordinates.LatLonMode
import de.eckstein.gpsview.coordinates.PositionSnapshot
import de.eckstein.gpsview.coordinates.SatelliteCount
import de.eckstein.gpsview.data.GnssSource
import de.eckstein.gpsview.data.LocationSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * Merges permission state, the system location setting, [LocationSource], and [GnssSource] into
 * [PositionUiState] (data-model.md state transitions). Deliberately exposes [uiState] as a plain
 * cold [Flow] rather than a `viewModelScope`-backed `StateFlow`: the UI collects it via
 * `collectAsStateWithLifecycle()`, so the underlying `callbackFlow` registrations in the sources
 * start and stop exactly with the UI's STARTED lifecycle — the ViewModel itself never launches
 * unscoped collection (constitution II). The position/satellite flows are only ever collected
 * while permission is granted and the location setting is on (`flatMapLatest`) — collecting them
 * without permission would throw `SecurityException` inside the sources.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PositionViewModel(
    private val locationSource: LocationSource,
    private val gnssSource: GnssSource,
) : ViewModel() {

    private val _permissionState = MutableStateFlow(PermissionState.NOT_YET_ASKED)
    val permissionState: StateFlow<PermissionState> = _permissionState.asStateFlow()

    fun updatePermissionState(state: PermissionState) {
        _permissionState.value = state
    }

    private val _locationSettingEnabled = MutableStateFlow(true)

    fun updateLocationSettingEnabled(enabled: Boolean) {
        _locationSettingEnabled.value = enabled
    }

    // Coarse-only grant is not a PositionUiState (data-model.md) — a separate non-blocking banner flag.
    private val _coarseOnlyBanner = MutableStateFlow(false)
    val coarseOnlyBanner: StateFlow<Boolean> = _coarseOnlyBanner.asStateFlow()

    fun updateCoarseOnlyBanner(showBanner: Boolean) {
        _coarseOnlyBanner.value = showBanner
    }

    private val positionAndSatellites: Flow<PositionUiState> =
        combine(
            locationSource.positions().map<PositionSnapshot, PositionSnapshot?> { it }.onStart { emit(null) },
            gnssSource.satellites().map<SatelliteCount, SatelliteCount?> { it }.onStart { emit(null) },
        ) { snapshot, satellites ->
            if (snapshot != null) {
                PositionUiState.Live(snapshot.copy(satellites = satellites))
            } else {
                PositionUiState.Acquiring(satellites)
            }
        }

    val uiState: Flow<PositionUiState> =
        _permissionState.flatMapLatest { permission ->
            when (permission) {
                PermissionState.NOT_YET_ASKED,
                PermissionState.DENIED_REASKABLE -> flowOf(PositionUiState.NotYetAsked)
                PermissionState.PERMANENTLY_DENIED -> flowOf(PositionUiState.PermanentlyDenied)
                PermissionState.GRANTED ->
                    _locationSettingEnabled.flatMapLatest { enabled ->
                        if (!enabled) flowOf(PositionUiState.LocationOff) else positionAndSatellites
                    }
            }
        }

    // Display preference, not sensor data (data-model.md) — process-lifetime only, not persisted.
    private val _latLonMode = MutableStateFlow(LatLonMode.DECIMAL)
    val latLonMode: StateFlow<LatLonMode> = _latLonMode.asStateFlow()

    fun toggleLatLonMode() {
        _latLonMode.value = if (_latLonMode.value == LatLonMode.DECIMAL) LatLonMode.DMS else LatLonMode.DECIMAL
    }

    private val _mapUiState = MutableStateFlow(MapUiState())
    val mapUiState: StateFlow<MapUiState> = _mapUiState.asStateFlow()

    /** Drag disengages follow-me; the locate button re-engages it (SPEC.md §8.2). */
    fun setFollowMe(followMe: Boolean) {
        _mapUiState.value = _mapUiState.value.copy(followMe = followMe)
    }

    fun toggleSatelliteLayer() {
        _mapUiState.value = _mapUiState.value.copy(satelliteVisible = !_mapUiState.value.satelliteVisible)
    }

    fun toggleParcelsLayer() {
        _mapUiState.value = _mapUiState.value.copy(parcelsVisible = !_mapUiState.value.parcelsVisible)
    }
}
