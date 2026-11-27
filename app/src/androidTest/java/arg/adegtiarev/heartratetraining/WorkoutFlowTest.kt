package arg.adegtiarev.heartratetraining

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import arg.adegtiarev.heartratetraining.overlay.OverlayPlacementStore
import arg.adegtiarev.heartratetraining.sensor.*
import arg.adegtiarev.heartratetraining.workout.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class WorkoutFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as WorkoutTestApplication
    private val dao get() = app.database.workouts()
    private fun text(id: Int, vararg args: Any) = compose.onNodeWithText(app.getString(id, *args))
    private fun click(id: Int) = text(id).performScrollTo().performClick()
    private fun await(check: () -> Boolean) = compose.waitUntil(10_000, check)
    private fun event(value: HeartRateMeasurement) {
        check(app.packets.tryEmit(SensorEvent.Measurement(value, SystemClock.elapsedRealtime())))
    }
    private fun pulse(bpm: Int) = event(HeartRateMeasurement.Pulse(bpm))
    private fun count(id: String) = runBlocking { dao.events(id).count { it.bpm != null } }
    private fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).use {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText()
    }
    private fun start(): String {
        await { app.sensors.state.value.phase == SensorPhase.Waiting || app.sensors.state.value.phase == SensorPhase.Live }
        click(R.string.workout_start)
        await { app.recorder.state.value.ride?.phase == WorkoutPhase.Recording }
        return app.recorder.state.value.ride!!.id
    }

    @Before fun prepare() {
        Assume.assumeTrue(app.synthetic)
        val permissions = if (Build.VERSION.SDK_INT >= 31) listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
            else listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        permissions.forEach { instrumentation.uiAutomation.grantRuntimePermission(app.packageName, it) }
        if (Build.VERSION.SDK_INT >= 33) {
            if (InstrumentationRegistry.getArguments().getString("denyNotifications") == "true") {
                assertEquals(PackageManager.PERMISSION_DENIED, app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS))
            } else instrumentation.uiAutomation.grantRuntimePermission(app.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    @Test fun recordsAcrossPauseScreenLockAndRecreationThenOpensFromHistory() {
        val id = start()
        pulse(125)
        pulse(125)
        await { count(id) == 2 }
        click(R.string.workout_pause)
        await { app.recorder.state.value.ride?.phase == WorkoutPhase.Paused }
        val frozen = app.recorder.state.value.ride!!.activeMs
        pulse(180)
        await { app.sensors.state.value.sample?.bpm == 180 }
        assertEquals(2, count(id))
        assertEquals(frozen, app.recorder.state.value.ride!!.activeMs)
        click(R.string.workout_resume)
        await { app.recorder.state.value.ride?.phase == WorkoutPhase.Recording }
        compose.activityRule.scenario.recreate()
        pulse(145)
        await { count(id) == 3 }
        val pkg = app.packageName
        val maps = InstrumentationRegistry.getArguments().getString("openMaps") == "true"
        val pacedSamples = if (InstrumentationRegistry.getArguments().getString("pacedRide") == "true") 15 else 0
        fun sendAtStrapCadence() = repeat(pacedSamples) {
            SystemClock.sleep(1000)
            pulse(143)
        }
        if (maps) shell("am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p com.google.android.apps.maps")
        else shell("am start -W -a android.settings.SETTINGS")
        try {
            await { compose.activityRule.scenario.state == androidx.lifecycle.Lifecycle.State.CREATED }
            pulse(155)
            sendAtStrapCadence()
            await { count(id) == 4 + pacedSamples }
            shell("input keyevent KEYCODE_SLEEP")
            await { !app.getSystemService(PowerManager::class.java).isInteractive }
            event(HeartRateMeasurement.ContactLost)
            if (pacedSamples > 0) SystemClock.sleep(SensorRepository.FRESH_MS + 100)
            pulse(165)
            sendAtStrapCadence()
            await { count(id) == 5 + 2 * pacedSamples }
        } finally {
            shell("input keyevent KEYCODE_WAKEUP")
            shell("wm dismiss-keyguard")
            shell("am start -n " + pkg + "/.MainActivity -a " + WorkoutService.OPEN_WORKOUT + " -f 0x24000000")
        }
        await { text(R.string.workout_pause).isDisplayed() }
        click(R.string.workout_pause)
        click(R.string.workout_finish)
        await { text(R.string.workout_average).isDisplayed() }
        text(R.string.workout_bpm, 143).assertExists()
        text(R.string.workout_bpm, 165).assertExists()
        assertTrue(runBlocking { dao.events(id).any { it.kind == EventKind.SignalLost } })
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        await { manager.activeNotifications.none { it.notification.channelId == "workout_recording" } }
        text(R.string.zone_save).performClick()
        await { text(R.string.workout_history).isDisplayed() && app.recorder.state.value.savedId == id }
        val avg = app.getString(R.string.workout_bpm, 143)
        text(R.string.workout_avg_short, avg).performClick()
        await { text(R.string.workout_chart).isDisplayed() }
        text(R.string.workout_bpm, 165).assertExists()
        text(R.string.workout_discard).assertDoesNotExist()
        assertEquals(1, runBlocking { dao.history().first().count { it.id == id } })
    }

    @Test fun discardingAnEmptyRideRemovesTheDraftAndItsEvents() {
        val id = start()
        click(R.string.workout_pause)
        click(R.string.workout_finish)
        await { text(R.string.workout_no_samples).isDisplayed() }
        text(R.string.workout_discard).performClick()
        await { app.recorder.state.value.ride == null }
        assertNull(runBlocking { dao.workout(id) })
        assertTrue(runBlocking { dao.events(id).isEmpty() })
    }

    @Test fun captureForSystemStop() {
        val args = InstrumentationRegistry.getArguments()
        Assume.assumeTrue(args.getString("externalStop") == "true" && args.getString("persistentFixture") == "true")
        val id = start()
        listOf(120, 135, 150).forEach(::pulse)
        await { count(id) == 3 }
        if (args.getString("showOverlay") == "true") {
            click(R.string.overlay_show)
            await { app.overlay.state.value?.phase == OverlayPhase.Visible }
            val automation = instrumentation.uiAutomation
            automation.serviceInfo = automation.serviceInfo.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            }
            fun badge() = automation.windows.mapNotNull { it.root }.flatMap {
                it.findAccessibilityNodeInfosByViewId("${app.packageName}:id/overlay_pulse")
            }.singleOrNull()?.let { node -> Rect().also(node::getBoundsInScreen) }
            await { badge()?.isEmpty == false }
            val initial = badge()!!
            val screen = checkNotNull(automation.takeScreenshot())
            val x = screen.width / 3
            val y = screen.height * 2 / 3
            screen.recycle()
            shell("input swipe ${initial.centerX()} ${initial.centerY()} $x $y 300")
            await { badge()?.let { kotlin.math.abs(it.centerX() - x) < 5 } == true }
            val moved = badge()!!
            val originalScale = OverlayPlacementStore(app).read().scale
            val targetScale = if (originalScale < 1.3f) 1.5f else 1f
            val span = moved.width() * 0.35f
            automation.pinch(moved, span, span * targetScale / originalScale)
            await { kotlin.math.abs(OverlayPlacementStore(app).read().scale - targetScale) < 0.04f }
            instrumentation.sendStatus(0, Bundle().apply { putString("stream", "Fixture overlay bounds: ${badge()!!.flattenToString()}\n") })
        }
        instrumentation.sendStatus(0, Bundle().apply { putString("stream", "Captured three fixture samples for system Stop.\n") })
        // The host can exercise Android's Active apps Stop during this window.
        SystemClock.sleep(25_000)
        assertEquals(WorkoutPhase.Recording, runBlocking { dao.workout(id) }?.phase)
    }
}
