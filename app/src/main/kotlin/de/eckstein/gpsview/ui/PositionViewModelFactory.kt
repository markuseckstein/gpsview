package de.eckstein.gpsview.ui

import android.content.Context
import android.location.LocationManager
import androidx.core.content.getSystemService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.google.android.gms.location.LocationServices
import de.eckstein.gpsview.data.FusedLocationSource
import de.eckstein.gpsview.data.GnssStatusSource

/** Manual constructor injection for [PositionViewModel] — no DI framework (constitution V). */
class PositionViewModelFactory(private val appContext: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        val locationSource =
            FusedLocationSource(appContext, LocationServices.getFusedLocationProviderClient(appContext))
        val gnssSource = GnssStatusSource(appContext.getSystemService<LocationManager>()!!)

        @Suppress("UNCHECKED_CAST")
        return PositionViewModel(locationSource, gnssSource) as T
    }
}
