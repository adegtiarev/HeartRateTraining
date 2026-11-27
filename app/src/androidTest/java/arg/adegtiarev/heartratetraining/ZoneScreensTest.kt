package arg.adegtiarev.heartratetraining

import android.content.res.Configuration
import android.content.res.Resources
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import arg.adegtiarev.heartratetraining.data.WorkoutDatabase
import arg.adegtiarev.heartratetraining.data.WorkoutLibrary
import arg.adegtiarev.heartratetraining.workout.WorkoutRecorder
import arg.adegtiarev.heartratetraining.workout.WorkoutOverlay
import androidx.test.espresso.Espresso.closeSoftKeyboard
import arg.adegtiarev.heartratetraining.sensor.*
import arg.adegtiarev.heartratetraining.ui.*
import arg.adegtiarev.heartratetraining.ui.theme.HeartRateTrainingTheme
import arg.adegtiarev.heartratetraining.zones.*
import java.util.Locale
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ZoneScreensTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var now = 1000L
    private val events = MutableSharedFlow<SensorEvent>(extraBufferCapacity = 4)
    private val sensors = SensorRepository(object : SensorTransport {
        override fun access() = SensorAccess.Ready
        override fun scan() = emptyFlow<NearbyStrap>()
        override fun connect(strap: Strap) = flow {
            emit(SensorEvent.Subscribed)
            emit(SensorEvent.Measurement(HeartRateMeasurement.Pulse(130), now))
            emitAll(events)
        }
    }, object : RememberedStrapStore {
        override suspend fun read(): Strap? = null
        override suspend fun write(strap: Strap) = Unit
    }, scope) { now }
    private val zones = object : ZoneSettingsRepository {
        override val settings = MutableStateFlow<ZoneSettings>(ZoneSettings.Unconfigured)
        override fun retry() = Unit
        override suspend fun save(configuration: ZoneConfiguration) { settings.value = ZoneSettings.Saved(configuration) }
    }
    private lateinit var resources: Resources
    private lateinit var database: WorkoutDatabase

    @After fun close() {
        scope.cancel()
        if (::database.isInitialized) database.close()
    }

    private fun launch(language: String, fontScale: Float = 1.4f) {
        val activity = compose.activity
        database = Room.inMemoryDatabaseBuilder(activity, WorkoutDatabase::class.java).build()
        val library = WorkoutLibrary(database.workouts())
        val recorder = WorkoutRecorder(database.workouts(), sensors, scope, { now }, { now })
        val configuration = Configuration(activity.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
            this.fontScale = fontScale
        }
        val context = activity.createConfigurationContext(configuration)
        resources = context.resources
        compose.runOnUiThread {
            sensors.startVisit()
            sensors.setVisible(true)
            sensors.select(Strap("fixture", "Test strap"))
        }
        compose.setContent {
            CompositionLocalProvider(
                LocalContext provides context, LocalConfiguration provides configuration,
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
            ) {
                HeartRateTrainingTheme {
                    val home = viewModel { HomeViewModel(sensors, zones) }
                    val workouts = viewModel { WorkoutViewModel(recorder, sensors, WorkoutOverlay(recorder.state)) { now } }
                    HeartRateScreens(home, zones, false, false, {}, {}, {}, {}, workouts, library, {}, {}, {})
                }
            }
        }
    }

    private fun label(id: Int, vararg args: Any) = resources.getString(id, *args)
    private fun click(id: Int) = compose.onNodeWithText(label(id)).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
    private fun threshold(number: Int) = compose.onNodeWithTag("threshold-$number").performScrollTo()
    private fun save() = compose.onNodeWithText(label(R.string.zone_save)).assertIsEnabled().performSemanticsAction(SemanticsActions.OnClick)
    private fun background(): Color {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        return pixels[2, pixels.height / 2]
    }

    private fun assertZone(number: Int, color: Color) {
        val pulse = resources.getQuantityString(R.plurals.pulse_description, 130, 130)
        val description = label(R.string.zone_reading, pulse, label(R.string.zone_number, number))
        val reading = compose.onNodeWithContentDescription(description)
        compose.waitUntil(5_000) { reading.isDisplayed() }
        reading.assertIsDisplayed()
        assertEquals(color, background())
    }

    @Test fun savedBoundaryChangesTheRenderedBackgroundWithoutAnotherSample() {
        launch("en")
        val sample = compose.runOnIdle { sensors.state.value.sample!! }
        val neutral = background()
        click(R.string.zone_settings)
        click(R.string.zone_calculator)
        compose.onNodeWithTag("maximum-input").performTextReplacement("180")
        click(R.string.zone_apply_calculation)
        save()
        assertZone(3, Color(0xFF1565C0))

        click(R.string.zone_settings)
        threshold(3).performTextReplacement("135")
        compose.onNodeWithContentDescription(label(R.string.zone_color_description, 2, "#2E7D32")).performScrollTo().performClick()
        compose.onNodeWithContentDescription(label(R.string.zone_swatch, label(R.string.zone_black), "000000")).performScrollTo().performClick()
        save()
        assertZone(2, Color.Black)
        assertEquals(sample, compose.runOnIdle { sensors.state.value.sample })

        click(R.string.zone_settings)
        threshold(3).performTextReplacement("126")
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText(label(R.string.zone_keep_editing)).performClick()
        threshold(3).assertTextContains("126")
        compose.onNodeWithText(label(R.string.cancel)).performSemanticsAction(SemanticsActions.OnClick)
        assertZone(2, Color.Black)
        compose.runOnIdle { now += SensorRepository.FRESH_MS; sensors.refresh() }
        compose.onNodeWithContentDescription(label(R.string.no_reading_description)).assertIsDisplayed()
        assertEquals(neutral, background())
        compose.runOnIdle { events.tryEmit(SensorEvent.Measurement(HeartRateMeasurement.Pulse(130), now)) }
        assertZone(2, Color.Black)
        click(R.string.disconnect)
        click(R.string.zone_settings)
        compose.onNodeWithText(label(R.string.cancel)).performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText(label(R.string.disconnected)).assertIsDisplayed()
        compose.onNodeWithContentDescription(label(R.string.no_reading_description)).assertIsDisplayed()
    }

    private fun localizedWhiteZone(language: String) {
        launch(language)
        click(R.string.zone_settings)
        click(R.string.zone_calculator)
        click(R.string.zone_adult_age)
        compose.onNodeWithTag("maximum-input").performTextReplacement("35")
        closeSoftKeyboard()
        compose.onNodeWithText(label(R.string.zone_estimated_max, 183.5)).performScrollTo().assertIsDisplayed()
        click(R.string.zone_apply_calculation)
        compose.onNodeWithContentDescription(label(R.string.zone_color_description, 3, "#1565C0")).performScrollTo().performClick()
        compose.onNodeWithTag("rgb-3").performScrollTo().performTextReplacement("FFFFFF")
        save()
        assertZone(3, Color.White)
        assertEquals(Color.Black, contrastingText(Color.White))
        assertEquals(Color.White, contrastingText(Color.Black))
    }

    @Test fun russianLabelsAndCustomWhiteZoneWithLargeText() = localizedWhiteZone("ru")
    @Test fun spanishLabelsAndCustomWhiteZoneWithLargeText() = localizedWhiteZone("es")

    @Test fun tableDerivesTheAdjacentEndAndKeepsTheFifthZoneOpenAtLargeText() {
        launch("ru", fontScale = 2f)
        click(R.string.zone_settings)
        fun enter(zone: Int, value: String) {
            threshold(zone).performSemanticsAction(SemanticsActions.SetText) { it(AnnotatedString(value)) }
        }
        enter(2, "120")
        val first = label(R.string.zone_number, 1)
        fun firstRange(last: Int) = label(R.string.zone_reading, first, label(R.string.zone_closed_range, 0, last))
        compose.onNodeWithContentDescription(firstRange(119)).performScrollTo().assertIsDisplayed()

        enter(2, "118")
        compose.onNodeWithContentDescription(firstRange(117)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription(firstRange(119)).assertDoesNotExist()

        enter(5, "172")
        threshold(5).assertTextContains("172")
        val last = label(R.string.zone_reading, label(R.string.zone_number, 5), label(R.string.zone_open_range, 172))
        compose.onNodeWithContentDescription(last).performScrollTo().assertIsDisplayed()
    }
}
