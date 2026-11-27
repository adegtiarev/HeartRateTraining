package arg.adegtiarev.heartratetraining

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnitRunner
import arg.adegtiarev.heartratetraining.data.WorkoutDatabase
import arg.adegtiarev.heartratetraining.sensor.*
import arg.adegtiarev.heartratetraining.zones.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class WorkoutTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application =
        super.newApplication(cl, WorkoutTestApplication::class.java.name, context)
}

// The opt-in test target feeds the same SensorRepository, recorder, Room and service as the app.
class WorkoutTestApplication : HeartRateApplication() {
    val synthetic get() = InstrumentationRegistry.getArguments().getString("syntheticWorkout") == "true"
    val packets = MutableSharedFlow<SensorEvent>(extraBufferCapacity = 256)
    var connections = 0
        private set
    private val strap = Strap("training-fixture", "Synthetic strap")
    override val sensors by lazy {
        if (!synthetic) super.sensors else SensorRepository(object : SensorTransport {
            override fun access() = SensorAccess.Ready
            override fun scan() = flowOf(NearbyStrap(strap, true))
            override fun connect(strap: Strap) = packets.onStart {
                connections++
                emit(SensorEvent.Subscribed)
            }
        }, object : RememberedStrapStore {
            override suspend fun read() = strap
            override suspend fun write(strap: Strap) = Unit
        }, CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate), SystemClock::elapsedRealtime)
    }

    val fixtureSettings = MutableStateFlow<ZoneSettings>(ZoneSettings.Saved(fixtureZones()))
    override val zones: ZoneSettingsRepository by lazy {
        if (!synthetic) super.zones else object : ZoneSettingsRepository {
            override val settings = fixtureSettings
            override fun retry() = Unit
            override suspend fun save(configuration: ZoneConfiguration) { settings.value = ZoneSettings.Saved(configuration) }
        }
    }

    override val database by lazy {
        if (synthetic && InstrumentationRegistry.getArguments().getString("persistentFixture") != "true")
            Room.inMemoryDatabaseBuilder(this, WorkoutDatabase::class.java).build()
        else super.database
    }
}

internal fun fixtureZones() = checkNotNull(ZoneConfiguration.create(listOf(108, 126, 144, 162),
    listOf(0x757575, 0x2E7D32, 0x1565C0, 0xEF6C00, 0xC62828)))
