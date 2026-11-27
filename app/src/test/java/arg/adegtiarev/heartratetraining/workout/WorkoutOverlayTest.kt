package arg.adegtiarev.heartratetraining.workout

import arg.adegtiarev.heartratetraining.sensor.PulseSample
import arg.adegtiarev.heartratetraining.zones.ZoneConfiguration
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class WorkoutOverlayTest {
    private val zones = ZoneConfiguration.create(listOf(108, 126, 144, 162), listOf(1, 2, 0, 0xffffff, 5))!!
    private val ride = Workout("first", 0, zones)
    private val recording = MutableStateFlow(RecordingState(loading = false, ride = ride))
    private val overlay = WorkoutOverlay(recording)

    @Test fun digitsAndZoneComeFromTheRideSnapshot() {
        fun reading(bpm: Int) = ride.reading(PulseSample(bpm, 0))
        assertEquals(2, reading(125).zone!!.number)
        assertEquals(0, reading(126).zone!!.rgb)
        assertEquals(0xffffff, reading(144).zone!!.rgb)
        assertEquals(98, reading(98).bpm)
        assertEquals(5, reading(172).zone!!.number)
        assertEquals(WorkoutReading(null, null), ride.reading(null))
        assertEquals(WorkoutReading(126, null), ride.copy(zones = null).reading(PulseSample(126, 0)))
    }

    @Test fun lateGrantCannotShowAnEndedRideOrItsSuccessor() {
        overlay.show(ride.id, false)
        assertTrue(overlay.openSettings(ride.id))
        recording.value = RecordingState(ride = ride.copy(phase = WorkoutPhase.Review))
        overlay.returnedFromSettings(true)
        assertNull(overlay.state.value)
        recording.value = RecordingState(ride = ride.copy(id = "next"))
        overlay.show(ride.id, true)
        overlay.attached(ride.id)
        assertNull(overlay.state.value)
    }

    @Test fun cancellationDenialAndRepeatedActionsAreScopedToTheCurrentRide() {
        overlay.show(ride.id, false)
        overlay.hide(ride.id)
        overlay.returnedFromSettings(true)
        assertNull(overlay.state.value)
        overlay.show(ride.id, false)
        assertTrue(overlay.openSettings(ride.id))
        assertFalse(overlay.openSettings(ride.id))
        overlay.returnedFromSettings(false)
        assertEquals(OverlayPhase.AccessDenied, overlay.state.value!!.phase)
        recording.value = RecordingState(ride = ride.copy(phase = WorkoutPhase.Paused))
        overlay.show(ride.id, true)
        overlay.attached(ride.id)
        overlay.show(ride.id, true)
        overlay.hide("other")
        assertEquals(OverlayPhase.Visible, overlay.state.value!!.phase)
        overlay.hide(ride.id)
        overlay.attached(ride.id)
        assertNull(overlay.state.value)
    }
}
