package arg.adegtiarev.heartratetraining.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arg.adegtiarev.heartratetraining.sensor.SensorRepository
import arg.adegtiarev.heartratetraining.sensor.SensorState
import arg.adegtiarev.heartratetraining.sensor.Strap
import arg.adegtiarev.heartratetraining.zones.ZoneSettings
import arg.adegtiarev.heartratetraining.zones.ZoneSettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(val sensor: SensorState = SensorState(), val settings: ZoneSettings = ZoneSettings.Loading) {
    val zone = (settings as? ZoneSettings.Saved)?.configuration?.let { config ->
        sensor.sample?.let { config.zoneFor(it.bpm) }
    }
}

class HomeViewModel(private val sensors: SensorRepository, private val zones: ZoneSettingsRepository) : ViewModel() {
    val state = combine(sensors.state, zones.settings, ::HomeUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun findStraps() = sensors.findStraps()
    fun select(strap: Strap) = sensors.select(strap)
    fun retry() = sensors.retry()
    fun disconnect() = sensors.disconnect()
    fun retryZones() = zones.retry()
}
