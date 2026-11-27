package arg.adegtiarev.heartratetraining.ui

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BatteryAlertTest {
    @Test fun lowBatteryFlashesTwicePerSecond() = runTest {
        assertEquals(listOf(BatteryAlertPhase.Inactive), batteryAlertPhases(false).toList())
        val frames = mutableListOf<Pair<Long, BatteryAlertPhase>>()
        batteryAlertPhases(true).take(5).collect { frames += testScheduler.currentTime to it }
        assertEquals(listOf(
            0L to BatteryAlertPhase.Regular, 250L to BatteryAlertPhase.Flash,
            500L to BatteryAlertPhase.Regular, 750L to BatteryAlertPhase.Flash,
            1_000L to BatteryAlertPhase.Regular,
        ), frames)
    }
}
