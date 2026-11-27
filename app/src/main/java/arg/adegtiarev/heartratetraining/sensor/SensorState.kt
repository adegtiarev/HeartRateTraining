package arg.adegtiarev.heartratetraining.sensor

import kotlinx.coroutines.flow.Flow

data class Strap(val id: String, val name: String?)
data class NearbyStrap(val strap: Strap, val advertisesHeartRate: Boolean)
data class PulseSample(val bpm: Int, val receivedAtMs: Long)

interface SensorRecorder {
    fun pulse(sample: PulseSample)
    fun signalLost(atMs: Long)
}

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
    val batteryPercent: Int? = null,
) {
    val hasLowBattery: Boolean get() = batteryPercent in 0..9
}

sealed interface SensorEvent {
    data object Subscribed : SensorEvent
    data class Measurement(val value: HeartRateMeasurement, val receivedAtMs: Long) : SensorEvent
    data class BatteryLevel(val percent: Int) : SensorEvent
}

internal fun decodeBatteryPercent(bytes: ByteArray): Int? =
    bytes.singleOrNull()?.toUByte()?.toInt()?.takeIf { it <= 100 }

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
