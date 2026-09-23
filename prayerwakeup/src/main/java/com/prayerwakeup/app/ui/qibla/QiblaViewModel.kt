package com.prayerwakeup.app.ui.qibla

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prayerwakeup.app.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

data class QiblaUiState(val hasLocation: Boolean = false, val bearingDegrees: Float = 0f)

@HiltViewModel
class QiblaViewModel @Inject constructor(
    settingsRepository: SettingsRepository
) : ViewModel() {

    val uiState: StateFlow<QiblaUiState> = settingsRepository.settingsFlow
        .map { settings ->
            if (!settings.hasLocation) {
                QiblaUiState(hasLocation = false)
            } else {
                QiblaUiState(hasLocation = true, bearingDegrees = qiblaBearing(settings.latitude, settings.longitude))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QiblaUiState())

    // Great-circle initial bearing from the device's location to the Kaaba.
    private fun qiblaBearing(latitude: Double, longitude: Double): Float {
        val kaabaLat = Math.toRadians(21.4225)
        val kaabaLon = Math.toRadians(39.8262)
        val fromLat = Math.toRadians(latitude)
        val fromLon = Math.toRadians(longitude)
        val deltaLon = kaabaLon - fromLon

        val y = sin(deltaLon) * cos(kaabaLat)
        val x = cos(fromLat) * sin(kaabaLat) - sin(fromLat) * cos(kaabaLat) * cos(deltaLon)
        val bearing = Math.toDegrees(atan2(y, x))
        return ((bearing + 360) % 360).toFloat()
    }
}
