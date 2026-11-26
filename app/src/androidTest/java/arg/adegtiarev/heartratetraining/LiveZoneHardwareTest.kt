package arg.adegtiarev.heartratetraining

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import arg.adegtiarev.heartratetraining.sensor.PulseSample
import arg.adegtiarev.heartratetraining.ui.opaqueRgb
import arg.adegtiarev.heartratetraining.ui.rgbText
import arg.adegtiarev.heartratetraining.zones.ZoneSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeNotNull
import org.junit.Rule
import org.junit.Test

/** Opt-in stages for a test phone. Run each stage in a fresh process; only pass/fail is recorded. */
class LiveZoneHardwareTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as HeartRateApplication
    private fun label(id: Int, vararg args: Any) = compose.activity.getString(id, *args)
    private fun click(id: Int) = compose.onNodeWithText(label(id)).performClick()
    private fun stored() = runBlocking { app.zones.settings.first { it != ZoneSettings.Loading } }

    private fun liveSample(after: Long = -1): PulseSample {
        compose.waitUntil(timeoutMillis = 35_000) { (app.sensors.state.value.sample?.receivedAtMs ?: -1) > after }
        return checkNotNull(app.sensors.state.value.sample)
    }

    private fun editStart(zone: Int, bpm: Int) {
        compose.onNodeWithTag("threshold-$zone").performScrollTo().performTextReplacement(bpm.toString())
    }

    private fun saveAndWaitForHome() {
        compose.onNodeWithText(label(R.string.zone_save)).assertIsEnabled().performSemanticsAction(SemanticsActions.OnClick)
        val homeAction = hasText(label(R.string.zone_settings))
        compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodes(homeAction).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun setFirstColor(rgb: Int) {
        val config = (stored() as ZoneSettings.Saved).configuration
        click(R.string.zone_settings)
        val description = label(R.string.zone_color_description, 1, "#" + rgbText(config.colors.first()))
        compose.onNodeWithContentDescription(description).performScrollTo().performClick()
        compose.onNodeWithTag("rgb-1").performScrollTo().performTextReplacement(rgbText(rgb))
        compose.onNodeWithTag("rgb-1").assertTextContains(rgbText(rgb))
        saveAndWaitForHome()
    }

    private fun checkHome(zone: Int, rgb: Int) {
        val name = label(R.string.zone_number, zone)
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodes(hasContentDescription(name, substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasContentDescription(name, substring = true)).assertIsDisplayed()
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        assertEquals(opaqueRgb(rgb), pixels[2, pixels.height / 2])
        val config = (stored() as ZoneSettings.Saved).configuration
        assertEquals(rgb, config.colors[zone - 1])
    }

    @Test fun verifyLiveHome() {
        val stage = InstrumentationRegistry.getArguments().getString("zoneStage")
        assumeNotNull(stage)
        when (stage) {
            "configure" -> {
                val before = liveSample()
                click(R.string.zone_settings)
                listOf((before.bpm - 20).coerceAtLeast(1), before.bpm + 45, before.bpm + 65, before.bpm + 85)
                    .forEachIndexed { index, bpm -> editStart(index + 2, bpm) }
                saveAndWaitForHome()
                val current = liveSample(before.receivedAtMs)
                val saved = (stored() as ZoneSettings.Saved).configuration
                checkHome(2, saved.colors[1])
                click(R.string.zone_settings)
                editStart(2, (current.bpm + 25).coerceAtMost(saved.thresholds[1] - 1))
                saveAndWaitForHome()
                liveSample(current.receivedAtMs)
                checkHome(1, saved.colors.first())
                setFirstColor(0x205978)
                checkHome(1, 0x205978)
            }
            "editAfterRestart" -> {
                liveSample()
                checkHome(1, 0x205978)
                setFirstColor(0xF1BB6C)
                checkHome(1, 0xF1BB6C)
            }
            "verifyAfterRestart" -> {
                liveSample()
                checkHome(1, 0xF1BB6C)
            }
            else -> error("Unknown zoneStage")
        }
    }
}
