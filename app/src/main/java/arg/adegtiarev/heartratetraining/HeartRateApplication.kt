package arg.adegtiarev.heartratetraining

import android.app.Application
import android.os.SystemClock
import arg.adegtiarev.heartratetraining.bluetooth.AndroidSensorTransport
import arg.adegtiarev.heartratetraining.bluetooth.DataStoreStrapStore
import arg.adegtiarev.heartratetraining.sensor.SensorRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class HeartRateApplication : Application() {
    val sensors by lazy {
        SensorRepository(
            AndroidSensorTransport(this),
            DataStoreStrapStore(this),
            CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            SystemClock::elapsedRealtime,
        )
    }
}
