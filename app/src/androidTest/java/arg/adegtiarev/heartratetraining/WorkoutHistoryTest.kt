package arg.adegtiarev.heartratetraining

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import arg.adegtiarev.heartratetraining.workout.*
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.roundToInt
import kotlin.math.sin

class WorkoutHistoryTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as WorkoutTestApplication
    private fun text(id: Int, vararg args: Any) = compose.onNodeWithText(app.getString(id, *args))

    @After fun restoreFixtureZones() {
        if (app.synthetic) runBlocking { app.zones.save(fixtureZones()) }
    }

    @Test fun opensTwoSavedRidesWithTheirOwnStatisticsAndOriginalColors() {
        assumeTrue(app.synthetic)
        compose.waitUntil(5_000) { !app.recorder.state.value.loading }
        val long = seed("synthetic-intervals", 1795784400000L, 2220, 137)
        val short = seed("synthetic-recovery", 1795698000000L, 480, 105)
        runBlocking {
            val changed = arg.adegtiarev.heartratetraining.zones.ZoneConfiguration.create(
                listOf(70, 80, 90, 100), List(5) { 0xffffff })!!
            app.zones.save(changed)
        }
        text(R.string.workout_history).performScrollTo().performClick()
        for (ride in listOf(long, short)) {
            val avg = app.getString(R.string.workout_bpm, ride.average!!)
            val row = text(R.string.workout_avg_short, avg)
            compose.waitUntil(10_000) { row.isDisplayed() }
            if (ride == long) screenshot("workout-history")
            row.performClick()
            compose.waitUntil(10_000) { text(R.string.workout_average).isDisplayed() }
            text(R.string.workout_bpm, ride.average).assertExists()
            text(R.string.workout_bpm, ride.maximum!!).assertExists()
            text(R.string.workout_discard).assertDoesNotExist()
            text(R.string.zone_closed_range, 126, 143).assertExists()
            if (ride == long) {
                val locale = app.resources.configuration.locales[0]
                text(R.string.workout_less_than, java.text.NumberFormat.getPercentInstance(locale).format(.01)).assertExists()
                screenshot("workout-results")
            }
            text(R.string.zone_open_range, 162).performScrollTo().assertIsDisplayed()
            compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        }
    }

    private fun seed(id: String, start: Long, seconds: Int, baseline: Int): WorkoutResults = runBlocking {
        val dao = app.database.workouts()
        app.database.withTransaction {
            if (dao.workout(id) == null) {
                var ride = Workout(id, start, fixtureZones())
                dao.create(ride)
                for (second in 0..seconds step 2) {
                    val offset = second * 1000L
                    ride = ride.advance(offset)
                    val event = when (second) {
                        600 -> WorkoutEvent(offset, EventKind.Pause)
                        660 -> WorkoutEvent(offset, EventKind.Resume)
                        1210 -> WorkoutEvent(offset, EventKind.SignalLost)
                        in 602..658, in 1212..1230 -> continue
                        else -> {
                            val bpm = baseline + (14 * sin(second / 43.0) + 9 * sin(second / 13.0) +
                                6 * sin(second / 5.0) + 4 * sin(second / 190.0)).roundToInt()
                            WorkoutEvent(offset, EventKind.Pulse, bpm)
                        }
                    }
                    if (event.kind == EventKind.Pause) ride = ride.copy(phase = WorkoutPhase.Paused)
                    if (event.kind == EventKind.Resume) ride = ride.copy(phase = WorkoutPhase.Recording)
                    dao.commit(ride, event)
                }
                dao.commit(ride.copy(phase = WorkoutPhase.Review), WorkoutEvent(ride.offsetMs, EventKind.Pause))
                dao.save(id)
            }
        }
        checkNotNull(app.library.results(id))
    }

    private fun screenshot(name: String) {
        if (InstrumentationRegistry.getArguments().getString("captureScreens") != "true") return
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(app.cacheDir, name + ".png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
