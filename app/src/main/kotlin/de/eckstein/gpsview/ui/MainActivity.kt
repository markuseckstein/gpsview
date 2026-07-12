package de.eckstein.gpsview.ui

import android.Manifest
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {

    private val viewModel: PositionViewModel by viewModels {
        PositionViewModelFactory(applicationContext)
    }

    // Guards against onStart racing the system permission dialog: while a request is in flight,
    // permission legitimately reads "not granted" with no rationale yet, which would otherwise be
    // misread as PermanentlyDenied.
    private var permissionRequestInFlight = false

    private val requestLocationPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            permissionRequestInFlight = false
            refreshPermissionState()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (!hasAnyLocationPermission()) {
            // First launch (SPEC.md §7.1): request immediately, no pre-rationale card.
            requestBothLocationPermissions()
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(
                        viewModel,
                        onRetryPermissionRequest = ::requestBothLocationPermissions,
                        onRequestPreciseLocation = ::requestPreciseLocationOnly,
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Re-evaluate on every resume, including a return from Settings (data-model.md).
        if (!permissionRequestInFlight) {
            refreshPermissionState()
        }
        viewModel.updateLocationSettingEnabled(isLocationSettingEnabled())
    }

    private fun requestBothLocationPermissions() {
        permissionRequestInFlight = true
        requestLocationPermissions.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    private fun requestPreciseLocationOnly() {
        permissionRequestInFlight = true
        requestLocationPermissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
    }

    private fun refreshPermissionState() {
        val fineGranted =
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        val coarseGranted =
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

        viewModel.updateCoarseOnlyBanner(coarseGranted && !fineGranted)

        val state =
            when {
                fineGranted || coarseGranted -> PermissionState.GRANTED
                shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) ->
                    PermissionState.DENIED_REASKABLE
                else -> PermissionState.PERMANENTLY_DENIED
            }
        viewModel.updatePermissionState(state)
    }

    private fun hasAnyLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    private fun isLocationSettingEnabled(): Boolean =
        getSystemService<LocationManager>()?.isLocationEnabled ?: false
}
