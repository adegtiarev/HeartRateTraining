package arg.adegtiarev.heartratetraining.workout

import arg.adegtiarev.heartratetraining.sensor.SensorRepository
import arg.adegtiarev.heartratetraining.zones.ZONE_COUNT
import arg.adegtiarev.heartratetraining.zones.ZoneConfiguration
import kotlin.math.roundToInt

enum class WorkoutPhase { Recording, Paused, Review, Saved }
enum class EventKind { Pulse, Pause, Resume, SignalLost }

data class Workout(
    val id: String,
    val startedAt: Long,
    val zones: ZoneConfiguration?,
    val phase: WorkoutPhase = WorkoutPhase.Recording,
    val offsetMs: Long = 0,
    val activeMs: Long = 0,
) {
    fun advance(offset: Long): Workout {
        val delta = (offset - offsetMs).coerceAtLeast(0)
        return copy(offsetMs = offsetMs + delta, activeMs = activeMs + if (phase == WorkoutPhase.Recording) delta else 0)
    }
}

data class WorkoutEvent(val offsetMs: Long, val kind: EventKind, val bpm: Int? = null)
data class HistoryRide(val id: String, val startedAt: Long, val activeMs: Long, val averageBpm: Double?)

data class TraceBreak(val offsetMs: Long, val endMs: Long, val pause: Boolean)
data class WorkoutResults(
    val ride: Workout,
    val average: Int?,
    val maximum: Int?,
    val segments: List<List<WorkoutEvent>>,
    val breaks: List<TraceBreak>,
    val zoneMillis: List<Long>,
    val reached: Set<Int>,
)

fun summarizeWorkout(ride: Workout, events: List<WorkoutEvent>): WorkoutResults {
    val segments = mutableListOf<MutableList<WorkoutEvent>>()
    val breaks = mutableListOf<TraceBreak>()
    val zones = LongArray(ZONE_COUNT)
    val reached = mutableSetOf<Int>()
    var previous: WorkoutEvent? = null
    var breakStart: Long? = null
    var paused = false
    var samples = 0L
    var total = 0L
    var maximum: Int? = null

    fun closeBreak(at: Long) {
        breakStart?.let { if (at > it) breaks += TraceBreak(it, at, paused) }
        breakStart = null
    }

    for (event in events) {
        when (event.kind) {
            EventKind.Pause -> {
                closeBreak(event.offsetMs)
                previous = null
                paused = true
                breakStart = event.offsetMs
            }
            EventKind.Resume -> {
                closeBreak(event.offsetMs)
                paused = false
            }
            EventKind.SignalLost -> if (!paused && breakStart == null) {
                breakStart = previous?.offsetMs ?: event.offsetMs
                previous = null
            }
            EventKind.Pulse -> {
                val bpm = event.bpm ?: continue
                if (paused) continue
                closeBreak(event.offsetMs)
                val last = previous
                val delta = event.offsetMs - (last?.offsetMs ?: event.offsetMs)
                if (last == null || delta >= SensorRepository.FRESH_MS) {
                    if (last != null) breaks += TraceBreak(last.offsetMs, event.offsetMs, false)
                    segments.add(mutableListOf())
                } else {
                    ride.zones?.zoneFor(checkNotNull(last.bpm))?.let { zones[it.number - 1] += delta }
                }
                segments.last().add(event)
                previous = event
                ride.zones?.zoneFor(bpm)?.let { reached.add(it.number) }
                maximum = maxOf(maximum ?: bpm, bpm)
                total += bpm
                samples++
            }
        }
    }
    closeBreak(ride.offsetMs)
    return WorkoutResults(ride, if (samples > 0) (total.toDouble() / samples).roundToInt() else null,
        maximum, segments, breaks, zones.toList(), reached)
}
