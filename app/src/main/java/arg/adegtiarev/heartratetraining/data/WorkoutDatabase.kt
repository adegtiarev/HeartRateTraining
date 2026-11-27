package arg.adegtiarev.heartratetraining.data

import androidx.room.*
import arg.adegtiarev.heartratetraining.workout.*
import arg.adegtiarev.heartratetraining.zones.ZoneConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

@Entity(tableName = "workouts", primaryKeys = ["id"],
    indices = [Index(value = ["pendingSlot"], unique = true), Index(value = ["phase", "startedAt"])])
data class StoredWorkout(@Embedded val ride: Workout, val pendingSlot: Int?, val averageBpm: Double? = null)

@Entity(tableName = "workout_events", indices = [Index("rideId")], foreignKeys = [
    ForeignKey(entity = StoredWorkout::class, parentColumns = ["id"], childColumns = ["rideId"],
        onDelete = ForeignKey.CASCADE)
])
data class StoredEvent(val rideId: String, @Embedded val event: WorkoutEvent,
    @PrimaryKey(autoGenerate = true) val sequence: Long = 0)

class ZoneSnapshotConverter {
    @TypeConverter fun encode(config: ZoneConfiguration?): String? =
        config?.let { (it.thresholds + it.colors).joinToString(",") }

    @TypeConverter fun decode(text: String?): ZoneConfiguration? = text?.let {
        val values = it.split(',').map(String::toInt)
        checkNotNull(ZoneConfiguration.create(values.take(4), values.drop(4)))
    }
}

@Dao
@RewriteQueriesToDropUnusedColumns
abstract class WorkoutDao {
    @Insert protected abstract suspend fun insertRide(row: StoredWorkout)
    @Update protected abstract suspend fun updateRide(row: StoredWorkout)
    @Insert protected abstract suspend fun insertEvent(row: StoredEvent)

    @Query("SELECT * FROM workouts WHERE pendingSlot = 1")
    abstract suspend fun pending(): Workout?

    @Query("SELECT * FROM workouts WHERE id = :id")
    abstract suspend fun workout(id: String): Workout?

    @Query("SELECT offsetMs, kind, bpm FROM workout_events WHERE rideId = :id ORDER BY sequence")
    abstract suspend fun events(id: String): List<WorkoutEvent>

    @Query("SELECT id, startedAt, activeMs, averageBpm FROM workouts WHERE phase = 'Saved' ORDER BY startedAt DESC, id")
    abstract fun history(): Flow<List<HistoryRide>>

    @Query("""UPDATE workouts SET phase = 'Saved', pendingSlot = NULL,
        averageBpm = (SELECT AVG(bpm) FROM workout_events WHERE rideId = :id)
        WHERE id = :id AND phase = 'Review'""")
    abstract suspend fun save(id: String): Int

    @Query("DELETE FROM workouts WHERE id = :id AND phase = 'Review'")
    abstract suspend fun discard(id: String): Int

    @Transaction
    open suspend fun recover(): Workout? {
        val ride = pending() ?: return null
        if (ride.phase == WorkoutPhase.Review) return ride
        return ride.copy(phase = WorkoutPhase.Review).also { updateRide(StoredWorkout(it, 1)) }
    }

    suspend fun create(ride: Workout) = insertRide(StoredWorkout(ride, 1))

    @Transaction
    open suspend fun commit(ride: Workout, event: WorkoutEvent?) {
        updateRide(StoredWorkout(ride, 1))
        if (event != null) insertEvent(StoredEvent(ride.id, event))
    }
}

@Database(entities = [StoredWorkout::class, StoredEvent::class], version = 1)
@TypeConverters(ZoneSnapshotConverter::class)
abstract class WorkoutDatabase : RoomDatabase() {
    abstract fun workouts(): WorkoutDao
}

class WorkoutLibrary(private val dao: WorkoutDao) {
    val history = dao.history()
    suspend fun results(id: String): WorkoutResults? = withContext(Dispatchers.Default) {
        val ride = dao.workout(id) ?: return@withContext null
        summarizeWorkout(ride, dao.events(id))
    }
}
