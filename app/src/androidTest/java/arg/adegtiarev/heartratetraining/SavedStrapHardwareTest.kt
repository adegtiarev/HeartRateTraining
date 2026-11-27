package arg.adegtiarev.heartratetraining

import android.content.Intent
import android.os.Bundle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in check with a previously selected, worn strap. Reports only timing, never BPM or identity. */
class SavedStrapHardwareTest {
    @Test fun receivesFreshSamplesOnRelaunchAndWhileHomeIsStopped() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("wornStrap") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as HeartRateApplication
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var lastStatus = ""
            val timestamps = app.sensors.state.onEach {
                val status = "${it.access} / ${it.phase}"
                if (status != lastStatus) {
                    instrumentation.sendStatus(0, Bundle().apply { putString("stream", "$status\n") })
                    lastStatus = status
                }
            }.map { it.sample?.receivedAtMs }.filterNotNull().distinctUntilChanged()
            val foreground = runBlocking { withTimeout(35_000) { timestamps.take(12).toList() } }
            if (InstrumentationRegistry.getArguments().getString("expectBattery") == "true") {
                val battery = runBlocking { withTimeout(15_000) {
                    app.sensors.state.map { it.batteryPercent }.filterNotNull().first()
                } }
                assertTrue(battery in 0..100)
                instrumentation.sendStatus(0, Bundle().apply { putString("stream", "Sensor battery: $battery%\n") })
            }
            val gaps = foreground.zipWithNext { a, b -> b - a }.sorted()
            instrumentation.sendStatus(0, Bundle().apply {
                putString("stream", "HRS notification intervals (ms): median=" + gaps[gaps.size / 2] + ", max=" + gaps.last() + "\n")
            })
            val useMaps = InstrumentationRegistry.getArguments().getString("openMaps") == "true"
            val destination = if (useMaps) Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MAPS)
                else Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            scenario.onActivity { it.startActivity(destination.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            instrumentation.waitForIdleSync()
            val background = runBlocking { withTimeout(15_000) { timestamps.take(4).toList() } }
            assertTrue(background.last() > foreground.last())
            assertTrue(scenario.state == androidx.lifecycle.Lifecycle.State.CREATED)
        }
    }
}
