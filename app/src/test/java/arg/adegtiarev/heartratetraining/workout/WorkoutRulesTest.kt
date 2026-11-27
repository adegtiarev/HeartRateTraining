package arg.adegtiarev.heartratetraining.workout

import arg.adegtiarev.heartratetraining.zones.ZoneConfiguration
import org.junit.Assert.*
import org.junit.Test

class WorkoutRulesTest {
    private val zones = ZoneConfiguration.create(listOf(108, 126, 144, 162), listOf(0, 1, 2, 3, 4))!!
    private val ride = Workout("test", 100, zones)
    private fun pulse(ms: Long, bpm: Int) = WorkoutEvent(ms, EventKind.Pulse, bpm)

    @Test fun referenceRideSeparatesPausedTimeUnknownTimeAndZoneTime() {
        val events = listOf(pulse(0, 125), pulse(2000, 125), pulse(4000, 126),
            WorkoutEvent(5000, EventKind.Pause), WorkoutEvent(8000, EventKind.Resume), pulse(9000, 126),
            pulse(11000, 144), WorkoutEvent(12000, EventKind.SignalLost), pulse(18000, 162),
            pulse(20000, 162), WorkoutEvent(21000, EventKind.Pause))
        val stopped = ride.advance(5000).copy(phase = WorkoutPhase.Paused).advance(8000)
            .copy(phase = WorkoutPhase.Recording).advance(21000).copy(phase = WorkoutPhase.Paused).advance(24000)
        val result = summarizeWorkout(stopped, events)
        assertEquals(18000L, stopped.activeMs)
        assertEquals(listOf(0L, 4000L, 2000L, 0L, 2000L), result.zoneMillis)
        assertEquals(139, result.average)
        assertEquals(162, result.maximum)
        assertEquals(listOf(3, 2, 2), result.segments.map { it.size })
        assertEquals(listOf(true, false, true), result.breaks.map { it.pause })
        assertEquals(setOf(2, 3, 4, 5), result.reached)
    }

    @Test fun exactFreshnessLimitBreaksTheTraceAndDuplicatesRemainSeparate() {
        val result = summarizeWorkout(ride.advance(10000), listOf(pulse(0, 108), pulse(0, 108),
            pulse(4999, 126), pulse(9999, 200)))
        assertEquals(4999L, result.zoneMillis[1])
        assertEquals(listOf(3, 1), result.segments.map { it.size })
        assertEquals(136, result.average)
        assertEquals(1, result.breaks.size)
    }

    @Test fun emptyRideMissingZonesAndZeroBpmHaveDistinctResults() {
        val empty = summarizeWorkout(ride, emptyList())
        assertNull(empty.average)
        assertNull(empty.maximum)
        val zero = summarizeWorkout(ride.copy(zones = null), listOf(pulse(0, 0)))
        assertEquals(0, zero.average)
        assertEquals(0, zero.maximum)
        assertTrue(zero.reached.isEmpty())
    }
}
