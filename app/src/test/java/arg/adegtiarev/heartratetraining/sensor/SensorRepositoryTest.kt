package arg.adegtiarev.heartratetraining.sensor

import arg.adegtiarev.heartratetraining.workout.Workout
import arg.adegtiarev.heartratetraining.workout.WorkoutReading
import arg.adegtiarev.heartratetraining.workout.reading
import arg.adegtiarev.heartratetraining.zones.ZoneConfiguration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SensorRepositoryTest {
    private val strap = Strap("first", "Test strap")
    private val other = Strap("second", null)

    private class FakeTransport : SensorTransport {
        var availability = SensorAccess.Ready
        var searches = 0
        val connections = mutableListOf<Strap>()
        lateinit var results: Channel<NearbyStrap>
        lateinit var notifications: Channel<SensorEvent>
        override fun access() = availability
        override fun scan(): Flow<NearbyStrap> {
            searches++
            results = Channel(Channel.UNLIMITED)
            return results.receiveAsFlow()
        }
        override fun connect(strap: Strap): Flow<SensorEvent> {
            connections += strap
            notifications = Channel(Channel.UNLIMITED)
            return notifications.receiveAsFlow()
        }
    }

    private class Rig(val scope: TestScope, var saved: Strap? = null) {
        val transport = FakeTransport()
        private val store = object : RememberedStrapStore {
            override suspend fun read() = saved
            override suspend fun write(strap: Strap) { saved = strap }
        }
        var sleepMs = 0L
        val repo = SensorRepository(transport, store, scope.backgroundScope) { scope.testScheduler.currentTime + sleepMs }
        val state get() = repo.state.value
        fun launch() {
            repo.stopVisit()
            repo.startVisit()
            repo.setVisible(true)
            scope.runCurrent()
        }
        fun advertise(strap: Strap, hrs: Boolean = true) {
            transport.results.trySend(NearbyStrap(strap, hrs))
            scope.runCurrent()
        }
        fun event(event: SensorEvent) {
            transport.notifications.trySend(event)
            scope.runCurrent()
        }
        fun packet(vararg bytes: Byte) {
            decodeHeartRate(bytes)?.let { event(SensorEvent.Measurement(it, scope.testScheduler.currentTime)) }
        }
    }

    @Test fun blackZoneReadingExpiresAtFiveSecondsAndOnWake() = runTest {
        val rig = Rig(this)
        val ride = Workout("fixture", 0, ZoneConfiguration.create(listOf(108, 126, 144, 162),
            listOf(1, 2, 0, 0xffffff, 5))!!)
        fun reading() = ride.reading(rig.state.sample)
        rig.launch()
        rig.repo.select(strap)
        runCurrent()
        rig.event(SensorEvent.Subscribed)
        rig.packet(0, 125)
        assertEquals(2, reading().zone!!.number)
        advanceTimeBy(1000)
        rig.packet(0, 126)
        advanceTimeBy(1000)
        rig.packet(0, 126)
        advanceTimeBy(4999)
        assertEquals(126, reading().bpm)
        assertEquals(0, reading().zone!!.rgb)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(WorkoutReading(null, null), reading())
        rig.event(SensorEvent.Measurement(HeartRateMeasurement.Pulse(144), testScheduler.currentTime))
        assertEquals(0xffffff, reading().zone!!.rgb)
        rig.sleepMs += 5000
        rig.repo.expireStaleSample()
        assertEquals(WorkoutReading(null, null), reading())
    }

    @Test fun pickerMergesAdvertisementsAndKeepsResultsAfterTimeout() = runTest {
        val rig = Rig(this)
        rig.launch()
        assertEquals(0, rig.transport.searches)
        rig.repo.findStraps()
        runCurrent()
        rig.advertise(other, false)
        rig.advertise(strap)
        rig.advertise(strap.copy(name = null), false)
        assertEquals(listOf(strap, other), rig.state.nearby.map { it.strap })
        advanceTimeBy(SensorRepository.SCAN_MS)
        runCurrent()
        assertEquals(SensorPhase.ScanFinished, rig.state.phase)
        rig.repo.select(other)
        runCurrent()
        assertNull(rig.saved)
        rig.event(SensorEvent.Subscribed)
        assertEquals(other, rig.saved)
        assertEquals(SensorPhase.Waiting, rig.state.phase)
    }

    @Test fun repeatedBpmIsFreshButMalformedNotificationDoesNotDelayExpiry() = runTest {
        val rig = Rig(this)
        rig.launch()
        rig.repo.select(strap)
        runCurrent()
        rig.event(SensorEvent.Subscribed)
        rig.packet(0, 72)
        advanceTimeBy(4000)
        rig.packet(0, 72)
        assertEquals(PulseSample(72, 4000), rig.state.sample)
        advanceTimeBy(4000)
        rig.packet(1, 72)
        advanceTimeBy(999)
        assertNotNull(rig.state.sample)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(SensorPhase.Stale, rig.state.phase)
        assertNull(rig.state.sample)
        rig.packet(0, 73)
        rig.packet(4, 73)
        assertEquals(SensorPhase.ContactLost, rig.state.phase)
        assertNull(rig.state.sample)
        rig.packet(6, 72)
        assertEquals(72, rig.state.sample?.bpm)
    }

    @Test fun relaunchFindsOnlySavedDeviceAndFailedReplacementPreservesIt() = runTest {
        val rig = Rig(this, strap)
        rig.launch()
        rig.advertise(other)
        assertTrue(rig.transport.connections.isEmpty())
        rig.advertise(strap)
        rig.event(SensorEvent.Subscribed)
        rig.repo.select(other)
        runCurrent()
        rig.transport.notifications.close(SensorFailure(SensorPhase.SubscriptionFailed))
        runCurrent()
        assertEquals(SensorPhase.SubscriptionFailed, rig.state.phase)
        assertEquals(strap, rig.saved)
        rig.launch()
        advanceTimeBy(SensorRepository.SCAN_MS)
        runCurrent()
        assertEquals(SensorPhase.Absent, rig.state.phase)
        assertNull(rig.state.sample)
    }

    @Test fun disconnectDuringRecoveryIgnoresLateEventsUntilNextVisit() = runTest {
        val rig = Rig(this, strap)
        rig.launch()
        rig.advertise(strap)
        rig.event(SensorEvent.Subscribed)
        rig.packet(0, 72)
        rig.transport.notifications.close(SensorFailure(SensorPhase.ConnectionFailed))
        runCurrent()
        assertEquals(SensorPhase.Searching, rig.state.phase)
        assertNull(rig.state.sample)
        rig.repo.disconnect()
        rig.repo.startVisit() // Activity recreation in the same visit.
        rig.repo.setVisible(false)
        rig.repo.setVisible(true)
        rig.advertise(strap)
        assertEquals(SensorPhase.Idle, rig.state.phase)
        assertEquals(1, rig.transport.connections.size)
        rig.launch()
        rig.advertise(strap)
        assertEquals(2, rig.transport.connections.size)
    }

    @Test fun replacesSubscriptionAndRestoresReadingsAfterDropout() = runTest {
        val rig = Rig(this)
        rig.launch()
        rig.repo.select(other)
        runCurrent()
        val oldConnection = rig.transport.notifications
        rig.repo.select(strap)
        runCurrent()
        oldConnection.trySend(SensorEvent.Subscribed)
        oldConnection.trySend(SensorEvent.Measurement(HeartRateMeasurement.Pulse(99), 0))
        runCurrent()
        assertNull(rig.saved)
        assertNull(rig.state.sample)
        rig.event(SensorEvent.Subscribed)
        rig.packet(0, 72)
        rig.transport.notifications.close(SensorFailure(SensorPhase.ConnectionFailed))
        runCurrent()
        rig.advertise(strap)
        rig.event(SensorEvent.Subscribed)
        assertNull(rig.state.sample)
        rig.packet(0, 75)
        assertEquals(75, rig.state.sample?.bpm)
    }

    @Test fun manualDisconnectSurvivesPermissionChangesAndRotation() = runTest {
        val rig = Rig(this, strap)
        rig.transport.availability = SensorAccess.PermissionNeeded
        rig.launch()
        assertEquals(0, rig.transport.searches)
        rig.transport.availability = SensorAccess.Ready
        rig.repo.refresh()
        runCurrent()
        rig.advertise(strap)
        rig.event(SensorEvent.Subscribed)
        rig.repo.setVisible(true)
        assertEquals(1, rig.transport.connections.size)
        rig.repo.disconnect()
        rig.transport.availability = SensorAccess.BluetoothOff
        rig.repo.refresh()
        rig.transport.availability = SensorAccess.Ready
        rig.repo.refresh()
        runCurrent()
        assertEquals(1, rig.transport.searches)
        rig.repo.retry()
        runCurrent()
        rig.advertise(strap)
        assertEquals(2, rig.transport.connections.size)
    }

    @Test fun recorderReceivesEveryPacketAndReconnectsAfterTheActivityFinishes() = runTest {
        val rig = Rig(this)
        rig.launch()
        rig.repo.select(strap)
        runCurrent()
        rig.event(SensorEvent.Subscribed)
        val captured = mutableListOf<PulseSample>()
        val losses = mutableListOf<Long>()
        rig.repo.recordWith(object : SensorRecorder {
            override fun pulse(sample: PulseSample) { captured += sample }
            override fun signalLost(atMs: Long) { losses += atMs }
        })
        rig.repo.setVisible(false)
        rig.repo.stopVisit()
        repeat(100) {
            rig.transport.notifications.trySend(SensorEvent.Measurement(HeartRateMeasurement.Pulse(130), 0))
        }
        runCurrent()
        assertEquals(100, captured.size)
        assertEquals(1, rig.transport.connections.size)
        rig.transport.notifications.close(SensorFailure(SensorPhase.ConnectionFailed))
        runCurrent()
        advanceTimeBy(1000)
        runCurrent()
        assertEquals(listOf(strap, strap), rig.transport.connections)
        assertEquals(0, rig.transport.searches)
        rig.event(SensorEvent.Subscribed)
        rig.packet(0, 110)
        assertEquals(110, captured.last().bpm)
        assertEquals(listOf(0L), losses)
        rig.repo.releaseRecorder()
        assertEquals(SensorPhase.Idle, rig.state.phase)
    }

    @Test fun radioLossDuringHandshakeCanRecoverWhenBluetoothIsEnabledAgain() = runTest {
        val rig = Rig(this, strap)
        rig.launch()
        rig.advertise(strap)
        rig.transport.availability = SensorAccess.BluetoothOff
        rig.transport.notifications.close(SensorFailure(SensorPhase.ConnectionFailed))
        runCurrent()
        assertEquals(SensorAccess.BluetoothOff, rig.state.access)
        rig.transport.availability = SensorAccess.Ready
        rig.repo.refresh()
        runCurrent()
        rig.advertise(strap)
        rig.event(SensorEvent.Subscribed)
        rig.packet(0, 72)
        assertEquals(SensorPhase.Live, rig.state.phase)
    }
}
