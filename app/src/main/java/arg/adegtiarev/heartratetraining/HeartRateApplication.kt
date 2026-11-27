package arg.adegtiarev.heartratetraining

import android.app.Application
import android.os.SystemClock
import androidx.room.Room
import arg.adegtiarev.heartratetraining.data.WorkoutDatabase
import arg.adegtiarev.heartratetraining.data.WorkoutLibrary
import arg.adegtiarev.heartratetraining.workout.WorkoutRecorder
import arg.adegtiarev.heartratetraining.zones.ZoneSettingsRepository
import arg.adegtiarev.heartratetraining.bluetooth.AndroidSensorTransport
import arg.adegtiarev.heartratetraining.bluetooth.DataStoreStrapStore
import arg.adegtiarev.heartratetraining.sensor.SensorRepository
import arg.adegtiarev.heartratetraining.settings.DataStoreZoneSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

open class HeartRateApplication : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    open val zones: ZoneSettingsRepository by lazy { DataStoreZoneSettings(this) }
    open val sensors by lazy {
        SensorRepository(
            AndroidSensorTransport(this),
            DataStoreStrapStore(this),
            scope,
            SystemClock::elapsedRealtime,
        )
    }
    open val database by lazy { Room.databaseBuilder(this, WorkoutDatabase::class.java, "workouts.db").build() }
    val library by lazy { WorkoutLibrary(database.workouts()) }
    val recorder by lazy {
        WorkoutRecorder(database.workouts(), sensors, scope, SystemClock::elapsedRealtime, System::currentTimeMillis)
    }
}
