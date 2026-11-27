package arg.adegtiarev.heartratetraining

import androidx.activity.ComponentActivity
import android.os.Build
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import arg.adegtiarev.heartratetraining.sensor.*
import arg.adegtiarev.heartratetraining.ui.HomeScreen
import arg.adegtiarev.heartratetraining.ui.HomeUiState
import arg.adegtiarev.heartratetraining.ui.theme.HeartRateTrainingTheme
import java.text.NumberFormat
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun permissionPickerLiveReadingAndDisconnect() {
        val strap = Strap("fixture", "Test strap")
        val legacyLocation = Build.VERSION.SDK_INT < Build.VERSION_CODES.S
        val state = mutableStateOf(SensorState(access = SensorAccess.PermissionNeeded))
        compose.setContent {
            HeartRateTrainingTheme {
                HomeScreen(HomeUiState(state.value), legacyLocation = legacyLocation, openPermissionSettings = false,
                    onZoneSettings = {}, onRetryZones = {}, onStart = {}, onHistory = {},
                    onPermission = { state.value = SensorState(access = SensorAccess.Ready) },
                    onEnableBluetooth = {}, onEnableLocation = {}, onRetry = {},
                    onFind = { state.value = state.value.copy(phase = SensorPhase.Scanning,
                        nearby = listOf(NearbyStrap(strap, true))) },
                    onSelect = { selected ->
                        assertEquals(strap, selected)
                        state.value = state.value.copy(phase = SensorPhase.Waiting, active = selected, nearby = emptyList())
                    },
                    onDisconnect = { state.value = SensorState(access = SensorAccess.Ready) })
            }
        }
        fun button(id: Int) = compose.onNodeWithText(compose.activity.getString(id))
        fun noReading() = compose.onNodeWithContentDescription(compose.activity.getString(R.string.no_reading_description))
        noReading().assertIsDisplayed()
        button(if (legacyLocation) R.string.permission_location else R.string.permission_nearby).assertIsDisplayed()
        button(R.string.grant_permission).performClick()
        button(R.string.find_strap).performClick()
        compose.onNodeWithText("Test strap").performClick()
        button(R.string.waiting).assertIsDisplayed()
        noReading().assertIsDisplayed()
        button(R.string.strap_battery_label).assertDoesNotExist()
        compose.runOnIdle {
            state.value = state.value.copy(phase = SensorPhase.Live, sample = PulseSample(72, 1000), batteryPercent = 10)
        }
        val formatter = NumberFormat.getPercentInstance(compose.activity.resources.configuration.locales[0])
        fun battery(label: Int, value: Int) = compose.onNodeWithContentDescription(
            compose.activity.getString(label, formatter.format(value / 100.0)))
        battery(R.string.strap_battery_level, 10).assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(batteryPercent = 9) }
        val lowBattery = battery(R.string.strap_battery_low, 9)
        lowBattery.assertIsDisplayed()
        fun percentageVisible(): Boolean {
            val pixels = lowBattery.captureToImage().toPixelMap()
            val background = pixels[pixels.width - 1, 0]
            // The right edge contains the percent sign. The static label stays on the left.
            return (pixels.width * 4 / 5 until pixels.width).any { x ->
                (0 until pixels.height).any { y -> pixels[x, y] != background }
            }
        }
        compose.waitUntil(5_000) { !percentageVisible() }
        compose.waitUntil(5_000) { percentageVisible() }
        compose.onNodeWithText("Test strap").assertIsDisplayed()
        val description = compose.activity.resources.getQuantityString(R.plurals.pulse_description, 72, 72)
        compose.onNodeWithContentDescription(description).assertIsDisplayed()
        button(R.string.disconnect).performClick()
        battery(R.string.strap_battery_low, 9).assertDoesNotExist()
        noReading().assertIsDisplayed()
        button(R.string.find_strap).assertIsDisplayed()
    }
}
