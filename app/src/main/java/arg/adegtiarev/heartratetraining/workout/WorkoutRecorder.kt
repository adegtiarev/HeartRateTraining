package arg.adegtiarev.heartratetraining.workout

import arg.adegtiarev.heartratetraining.data.WorkoutDao
import arg.adegtiarev.heartratetraining.sensor.PulseSample
import arg.adegtiarev.heartratetraining.sensor.SensorRecorder
import arg.adegtiarev.heartratetraining.sensor.SensorRepository
import arg.adegtiarev.heartratetraining.zones.ZoneConfiguration
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class WorkoutError { Service, Storage }
data class RecordingState(
    val loading: Boolean = true,
    val starting: Boolean = false,
    val ride: Workout? = null,
    val originMs: Long = 0,
    val error: WorkoutError? = null,
    val savedId: String? = null,
)

// Commands, sensor callbacks and UI state run on Main. Room suspends on its own executor.
// A single writer preserves the receipt order across slow writes and rider actions.
class WorkoutRecorder(
    private val dao: WorkoutDao,
    private val sensors: SensorRepository,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val wallClock: () -> Long,
) : SensorRecorder {
    private val mutable = MutableStateFlow(RecordingState())
    val state = mutable.asStateFlow()
    private val writes = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private var snapshot: ZoneConfiguration? = null
    private var endService: (() -> Unit)? = null
    private var heartbeat: Job? = null
    private var recordingFrom = 0L

    init {
        scope.launch {
            for (write in writes) {
                try {
                    write()
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (_: Exception) {
                    stopCapture()
                    val recovered = try { dao.recover() } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (_: Exception) { null }
                    mutable.value = RecordingState(loading = false, ride = recovered, error = WorkoutError.Storage)
                }
            }
        }
        retryRecovery()
    }

    fun retryRecovery() {
        if (endService != null || mutable.value.starting) return
        mutable.value = RecordingState()
        submit { mutable.value = RecordingState(loading = false, ride = dao.recover()) }
    }

    fun requestStart(zones: ZoneConfiguration?): Boolean {
        val current = mutable.value
        if (current.loading || current.starting || current.ride != null || current.error == WorkoutError.Storage) return false
        snapshot = zones
        mutable.value = RecordingState(loading = false, starting = true)
        return true
    }

    fun startFailed() {
        if (mutable.value.starting) mutable.value = RecordingState(loading = false, error = WorkoutError.Service)
    }

    fun startInService(onEnd: () -> Unit): Boolean {
        if (endService != null) return true
        if (!mutable.value.starting) return false
        endService = onEnd
        val origin = clock()
        val ride = Workout(UUID.randomUUID().toString(), wallClock(), snapshot)
        recordingFrom = origin
        submit {
            dao.create(ride)
            mutable.value = RecordingState(loading = false, ride = ride, originMs = origin)
        }
        sensors.recordWith(this)
        heartbeat = scope.launch {
            while (isActive) {
                delay(5_000)
                val at = clock()
                submit {
                    val live = mutable.value
                    val current = live.ride
                    if (current?.phase == WorkoutPhase.Recording && at - live.originMs - current.offsetMs >= 5_000) {
                        commit(current.advance(at - live.originMs))
                    }
                }
            }
        }
        return true
    }

    override fun pulse(sample: PulseSample) = record(sample.receivedAtMs, EventKind.Pulse, sample.bpm)
    override fun signalLost(atMs: Long) = record(atMs, EventKind.SignalLost, null)

    private fun record(at: Long, kind: EventKind, bpm: Int?) = submit {
        val live = mutable.value
        val ride = live.ride ?: return@submit
        if (ride.phase != WorkoutPhase.Recording || at < recordingFrom) return@submit
        val offset = at - live.originMs
        commit(ride.advance(offset), WorkoutEvent(offset, kind, bpm))
    }

    fun pause(id: String) = transition(id, WorkoutPhase.Recording, WorkoutPhase.Paused, EventKind.Pause)
    fun resume(id: String) = transition(id, WorkoutPhase.Paused, WorkoutPhase.Recording, EventKind.Resume)
    fun finish(id: String) = transition(id, WorkoutPhase.Paused, WorkoutPhase.Review, null)

    private fun transition(id: String, from: WorkoutPhase, to: WorkoutPhase, kind: EventKind?) {
        val at = clock()
        submit {
            val live = mutable.value
            val ride = live.ride ?: return@submit
            if (ride.id != id || ride.phase != from) return@submit
            val next = ride.advance(at - live.originMs).copy(phase = to)
            if (to == WorkoutPhase.Recording) recordingFrom = at
            dao.commit(next, kind?.let { WorkoutEvent(next.offsetMs, it) })
            if (to == WorkoutPhase.Review) stopCapture()
            mutable.value = live.copy(ride = next, error = null)
        }
    }

    fun decide(id: String, save: Boolean) = submit {
        val ride = mutable.value.ride ?: return@submit
        if (ride.id != id || ride.phase != WorkoutPhase.Review) return@submit
        if (save) dao.save(id) else dao.discard(id)
        mutable.value = RecordingState(loading = false, savedId = id.takeIf { save })
    }

    fun serviceDestroyed() {
        if (endService == null) return
        stopCapture(stopService = false)
        submit { mutable.value = RecordingState(loading = false, ride = dao.recover()) }
    }

    private fun stopCapture(stopService: Boolean = true) {
        val end = endService ?: return
        endService = null
        sensors.releaseRecorder()
        heartbeat?.cancel()
        if (stopService) end()
    }

    private suspend fun commit(ride: Workout, event: WorkoutEvent? = null) {
        dao.commit(ride, event)
        mutable.value = mutable.value.copy(ride = ride)
    }

    private fun submit(block: suspend () -> Unit) { writes.trySend(block).getOrThrow() }
}
