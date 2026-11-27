package arg.adegtiarev.heartratetraining

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import arg.adegtiarev.heartratetraining.data.*
import arg.adegtiarev.heartratetraining.workout.*
import arg.adegtiarev.heartratetraining.zones.ZoneConfiguration
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class WorkoutPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "test-workout-" + UUID.randomUUID() + ".db"
    private fun open() = Room.databaseBuilder(context, WorkoutDatabase::class.java, name).build()
    private var database = open()
    private val dao get() = database.workouts()

    @After fun cleanup() {
        database.close()
        context.deleteDatabase(name)
    }

    @Test fun recoveryUsesTheLastCheckpointAndEachDecisionCanBeRetried() = runBlocking {
        val zones = ZoneConfiguration.create(listOf(100, 120, 140, 160), listOf(0, 1, 2, 3, 4))!!
        for (phase in listOf(WorkoutPhase.Recording, WorkoutPhase.Paused, WorkoutPhase.Review)) {
            val ride = Workout(phase.name, 100, zones)
            dao.create(ride)
            dao.commit(ride.advance(1000), WorkoutEvent(1000, EventKind.Pulse, 150))
            val checkpoint = ride.advance(8000).copy(phase = phase)
            dao.commit(checkpoint, null)
            database.close()
            database = open()
            assertEquals(checkpoint.copy(phase = WorkoutPhase.Review), dao.recover())
            assertTrue(dao.history().first().none { it.id == ride.id })
            if (phase == WorkoutPhase.Recording) {
                assertEquals(1, dao.save(ride.id))
                assertEquals(0, dao.save(ride.id))
                assertEquals(0, dao.discard(ride.id))
                assertEquals(150.0, dao.history().first().single().averageBpm!!, 0.0)
            } else {
                assertEquals(1, dao.discard(ride.id))
                assertEquals(0, dao.discard(ride.id))
                assertEquals(0, dao.save(ride.id))
                assertTrue(dao.events(ride.id).isEmpty())
            }
        }
        assertNull(dao.recover())
        val result = WorkoutLibrary(dao).results(WorkoutPhase.Recording.name)!!
        assertEquals(8000L, result.ride.activeMs)
        assertEquals(zones, result.ride.zones)
        assertEquals(150, result.maximum)
    }

    @Test fun failedMeasurementInsertRollsBackItsTimeCheckpoint() = runBlocking {
        val ride = Workout("rollback", 123, null)
        dao.create(ride)
        dao.commit(ride.advance(1000), WorkoutEvent(1000, EventKind.Pulse, 125))
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER interrupt_write BEFORE INSERT ON workout_events BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        val failed = runCatching { dao.commit(ride.advance(2000), WorkoutEvent(2000, EventKind.Pulse, 140)) }
        assertTrue(failed.isFailure)
        assertEquals(1000L, dao.recover()!!.activeMs)
        assertEquals(listOf(125), dao.events(ride.id).map { it.bpm })
    }
}
