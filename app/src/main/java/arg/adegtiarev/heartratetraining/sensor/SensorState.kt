package arg.adegtiarev.heartratetraining.sensor

import kotlinx.coroutines.flow.Flow

data class Strap(val id: String, val name: String?)
data class NearbyStrap(val strap: Strap, val advertisesHeartRate: Boolean)
data class PulseSample(val bpm: Int, val receivedAtMs: Long)

enum class SensorAccess { Checking, Ready, Unavailable, PermissionNeeded, BluetoothOff, LocationOff }

enum class SensorPhase {
    Idle, Scanning, ScanFinished, Searching, Connecting, Waiting, Live, ContactLost, Stale,
    Absent, ScanFailed, ConnectionFailed, Unsupported, SubscriptionFailed
}

data class SensorState(
    val access: SensorAccess = SensorAccess.Checking,
    val phase: SensorPhase = SensorPhase.Idle,
    val remembered: Strap? = null,
    val active: Strap? = null,
    val nearby: List<NearbyStrap> = emptyList(),
    val sample: PulseSample? = null,
    val storageError: Boolean = false,
)

sealed interface SensorEvent {
    data object Subscribed : SensorEvent
    data class Measurement(val value: HeartRateMeasurement, val receivedAtMs: Long) : SensorEvent
}

interface SensorTransport {
    fun access(): SensorAccess
    fun scan(): Flow<NearbyStrap>
    fun connect(strap: Strap): Flow<SensorEvent>
}

class SensorFailure(val phase: SensorPhase) : Exception(phase.name)

interface RememberedStrapStore {
    suspend fun read(): Strap?
    suspend fun write(strap: Strap)
}
