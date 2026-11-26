package arg.adegtiarev.heartratetraining.ui

import androidx.lifecycle.ViewModel
import arg.adegtiarev.heartratetraining.sensor.SensorRepository
import arg.adegtiarev.heartratetraining.sensor.Strap

class HomeViewModel(private val sensors: SensorRepository) : ViewModel() {
    val state = sensors.state

    fun findStraps() = sensors.findStraps()
    fun select(strap: Strap) = sensors.select(strap)
    fun retry() = sensors.retry()
    fun disconnect() = sensors.disconnect()

}
