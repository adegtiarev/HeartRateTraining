package arg.adegtiarev.heartratetraining.workout

import arg.adegtiarev.heartratetraining.sensor.PulseSample
import arg.adegtiarev.heartratetraining.zones.HeartRateZone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WorkoutReading(val bpm: Int?, val zone: HeartRateZone?)

fun Workout.reading(sample: PulseSample?): WorkoutReading =
    WorkoutReading(sample?.bpm, sample?.let { zones?.zoneFor(it.bpm) })

enum class OverlayPhase { ExplainAccess, InSettings, Attaching, Visible, AccessDenied, WindowFailed }

data class OverlayRequest(val rideId: String, val phase: OverlayPhase) {
    val windowRideId: String?
        get() = rideId.takeIf { phase == OverlayPhase.Attaching || phase == OverlayPhase.Visible }
}

/** One in-memory request shared by the workout UI and its service on the main thread. */
class WorkoutOverlay(private val recording: StateFlow<RecordingState>) {
    private val current = MutableStateFlow<OverlayRequest?>(null)
    val state = current.asStateFlow()

    fun activeRide(id: String): Workout? = recording.value.ride?.takeIf {
        it.id == id && (it.phase == WorkoutPhase.Recording || it.phase == WorkoutPhase.Paused)
    }

    fun show(id: String, accessGranted: Boolean) {
        if (activeRide(id) == null) return
        val existing = current.value?.takeIf { it.rideId == id }
        if (existing != null && existing.phase != OverlayPhase.AccessDenied && existing.phase != OverlayPhase.WindowFailed) return
        current.value = OverlayRequest(id, if (accessGranted) OverlayPhase.Attaching else OverlayPhase.ExplainAccess)
    }

    fun openSettings(id: String): Boolean {
        if (current.value != OverlayRequest(id, OverlayPhase.ExplainAccess) || activeRide(id) == null) return false
        current.value = OverlayRequest(id, OverlayPhase.InSettings)
        return true
    }

    fun returnedFromSettings(granted: Boolean) {
        val request = current.value ?: return
        if (request.phase == OverlayPhase.InSettings) {
            current.value = activeRide(request.rideId)?.let {
                request.copy(phase = if (granted) OverlayPhase.Attaching else OverlayPhase.AccessDenied)
            }
        }
    }

    fun attached(id: String) {
        if (current.value == OverlayRequest(id, OverlayPhase.Attaching)) {
            current.value = OverlayRequest(id, OverlayPhase.Visible)
        }
    }

    fun failed(id: String, accessDenied: Boolean = false) {
        val request = current.value ?: return
        if (request.rideId == id) {
            current.value = request.copy(phase = if (accessDenied) OverlayPhase.AccessDenied else OverlayPhase.WindowFailed)
        }
    }

    fun hide(id: String) {
        if (current.value?.rideId == id) current.value = null
    }
}
