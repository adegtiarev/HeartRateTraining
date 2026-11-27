package arg.adegtiarev.heartratetraining

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.Display
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.WindowMetrics
import androidx.annotation.RequiresApi
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inspector.WindowInspector
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import arg.adegtiarev.heartratetraining.overlay.WorkoutOverlayWindow
import arg.adegtiarev.heartratetraining.overlay.OverlayPlacement
import arg.adegtiarev.heartratetraining.overlay.OverlayPlacementStore
import arg.adegtiarev.heartratetraining.sensor.HeartRateMeasurement
import arg.adegtiarev.heartratetraining.sensor.SensorEvent
import arg.adegtiarev.heartratetraining.sensor.SensorRepository
import arg.adegtiarev.heartratetraining.workout.*
import arg.adegtiarev.heartratetraining.zones.ZoneConfiguration
import arg.adegtiarev.heartratetraining.zones.ZoneSettings
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*

class WorkoutOverlayWindowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation
    private val args get() = InstrumentationRegistry.getArguments()
    private val app get() = instrumentation.targetContext.applicationContext as WorkoutTestApplication
    private val pkg get() = app.packageName
    private val zones = ZoneConfiguration.create(listOf(108, 126, 144, 162),
        listOf(0x555555, 0x006633, 0, 0xffffff, 0x990000))!!
    private var originalMode: String? = null
    private lateinit var originalPlacement: OverlayPlacement
    private var rideId: String? = null
    private var steadyReadings: Job? = null

    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        automation.executeShellCommand(command)).bufferedReader().use { it.readText() }
    private fun waitFor(message: String, check: () -> Boolean) = compose.waitUntil(message, 12_000, check)
    private fun button(id: Int) = compose.onNodeWithText(app.getString(id))
    private fun scrollTo(id: Int) = compose.onNode(hasScrollAction()).performScrollToNode(hasText(app.getString(id)))
    private fun click(id: Int) {
        scrollTo(id)
        button(id).performClick()
    }
    private fun mode(value: String) { shell("appops set $pkg SYSTEM_ALERT_WINDOW $value") }
    private fun send(value: HeartRateMeasurement) {
        check(app.packets.tryEmit(SensorEvent.Measurement(value, SystemClock.elapsedRealtime())))
    }
    private fun pulse(bpm: Int) = send(HeartRateMeasurement.Pulse(bpm))
    private fun keepPulse(bpm: Int) {
        steadyReadings = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                pulse(bpm)
                delay(1_000)
            }
        }
    }
    private fun count() = runBlocking { app.database.workouts().events(rideId!!).count { it.bpm != null } }
    private fun windows() = automation.windows.mapNotNull { it.root }.flatMap { root ->
        root.findAccessibilityNodeInfosByViewId("$pkg:id/overlay_pulse")
    }
    private fun start() {
        waitFor("sensor subscribed") { app.packets.subscriptionCount.value > 0 }
        click(R.string.workout_start)
        waitFor("recording") { app.recorder.state.value.ride?.phase == WorkoutPhase.Recording }
        rideId = app.recorder.state.value.ride!!.id
    }
    private fun returnToWorkout() {
        shell("am start -W -n $pkg/.MainActivity -a ${WorkoutService.OPEN_WORKOUT} -f 0x24000000")
        waitFor("Workout resumed") { compose.activityRule.scenario.state == androidx.lifecycle.Lifecycle.State.RESUMED }
        scrollTo(R.string.workout_active_time)
    }

    @Before fun prepare() {
        Assume.assumeTrue(app.synthetic)
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        originalMode = Regex("SYSTEM_ALERT_WINDOW: (\\w+)").find(shell("appops get $pkg SYSTEM_ALERT_WINDOW"))
            ?.groupValues?.get(1) ?: "default"
        val positions = OverlayPlacementStore(app)
        originalPlacement = positions.read()
        app.getSharedPreferences("workout_overlay_placement", Context.MODE_PRIVATE).edit().clear().apply()
        val perms = if (Build.VERSION.SDK_INT >= 31) listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
            else listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        perms.forEach { automation.grantRuntimePermission(pkg, it) }
        if (Build.VERSION.SDK_INT >= 33) {
            if (args.getString("denyNotifications") == "true") {
                assertEquals(PackageManager.PERMISSION_DENIED, app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS))
                app.getSharedPreferences("workout_permissions", Context.MODE_PRIVATE).edit().putBoolean("asked_notifications", true).commit()
            } else automation.grantRuntimePermission(pkg, Manifest.permission.POST_NOTIFICATIONS)
        }
        runBlocking { app.zones.save(zones) }
    }

    @After fun restore() {
        steadyReadings?.cancel()
        val original = originalMode ?: return
        rideId?.let { id ->
            instrumentation.runOnMainSync {
                app.recorder.pause(id)
                app.recorder.finish(id)
                app.recorder.decide(id, false)
            }
            waitFor("draft closed") { app.recorder.state.value.ride?.id != id }
        }
        runBlocking { app.zones.save(fixtureZones()) }
        OverlayPlacementStore(app).save(originalPlacement)
        mode(original)
    }

    private fun expectReading(bpm: Int?, zone: Int? = null, lowBattery: Boolean = false): Rect {
        val res = app.resources
        val digits = if (bpm == null) res.getString(R.string.no_reading) else res.getString(R.string.number, bpm)
        val pulse = if (bpm == null) res.getString(R.string.no_reading_description)
            else res.getQuantityString(R.plurals.pulse_description, bpm, bpm)
        val reading = if (zone == null) pulse else res.getString(R.string.zone_reading, pulse, res.getString(R.string.zone_number, zone))
        val description = if (lowBattery) res.getString(R.string.overlay_low_battery, reading) else reading
        val bounds = Rect()
        waitFor("one overlay showing $description") {
            val node = windows().singleOrNull() ?: return@waitFor false
            if (!node.refresh()) return@waitFor false
            node.getBoundsInScreen(bounds)
            node.text?.toString() == digits && node.contentDescription?.toString() == description && !bounds.isEmpty
        }
        return bounds
    }

    private fun checkPixels(bpm: Int, zone: Int?, background: Int, ink: Int, capture: Boolean = false, lowBattery: Boolean = false) {
        expectReading(bpm, zone, lowBattery)
        val digits = app.getString(R.string.number, bpm)
        waitFor("opaque background and contrasting digits") {
            val node = windows().singleOrNull() ?: return@waitFor false
            if (!node.refresh()) return@waitFor false
            if (node.text?.toString() != digits) return@waitFor false
            val bounds = Rect().also(node::getBoundsInScreen)
            val bitmap = automation.takeScreenshot() ?: return@waitFor false
            try {
                if (bounds.isEmpty || !Rect(0, 0, bitmap.width, bitmap.height).contains(bounds)) return@waitFor false
                val painted = bitmap.getPixel(bounds.left + 3, bounds.centerY()) == background &&
                    (bounds.top until bounds.bottom).any { y ->
                        (bounds.left until bounds.right).any { x -> bitmap.getPixel(x, y) == ink }
                    }
                if (painted && capture && args.getString("captureOverlay") == "true") {
                    File(app.cacheDir, "synthetic-map-overlay.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
                painted
            } finally { bitmap.recycle() }
        }
    }

    @Test fun lowBatteryFlashesOverTheNavigatorAndStopsAtTenPercent() {
        fun battery(percent: Int) { check(app.packets.tryEmit(SensorEvent.BatteryLevel(percent))) }
        mode("allow")
        start()
        click(R.string.overlay_show)
        if (args.getString("openMaps") == "true") {
            shell("am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p com.google.android.apps.maps")
            waitFor("Maps in front") { automation.rootInActiveWindow?.packageName == "com.google.android.apps.maps" }
        } else openSurface(false)
        val green = Color.rgb(0x00, 0x66, 0x33)
        keepPulse(125)
        battery(10)
        checkPixels(125, 2, green, Color.WHITE)
        battery(9)
        checkPixels(125, 2, Color.WHITE, Color.BLACK, lowBattery = true)
        checkPixels(125, 2, green, Color.WHITE, lowBattery = true)
        steadyReadings?.cancel()
        keepPulse(144) // This ride has a white zone.
        checkPixels(144, 4, Color.BLACK, Color.WHITE, lowBattery = true)
        checkPixels(144, 4, Color.WHITE, Color.BLACK, lowBattery = true)
        battery(10)
        checkPixels(144, 4, Color.WHITE, Color.BLACK)
        returnToWorkout()
        click(R.string.overlay_hide)
        waitFor("battery warning removed with the window") { windows().isEmpty() }
    }

    private fun openSurface(suppress: Boolean) {
        val testPkg = instrumentation.context.packageName
        shell("am start -W --activity-clear-task --activity-no-animation -n $testPkg/${OverlayTestSurface::class.java.name} --ez suppress $suppress")
        waitFor("external test window") { automation.rootInActiveWindow?.packageName == testPkg }
        automation.waitForIdle(250, 5_000)
    }

    private fun displayBounds(): Rect = checkNotNull(automation.takeScreenshot()).let {
        try { Rect(0, 0, it.width, it.height) } finally { it.recycle() }
    }

    private fun awaitBounds(message: String, matches: (Rect) -> Boolean): Rect {
        val bounds = Rect()
        val previous = Rect()
        val location = IntArray(2)
        var stableSince = SystemClock.uptimeMillis()
        try {
            waitFor(message) {
                // Accessibility window bounds can remain cached after a move on Android 10.
                instrumentation.runOnMainSync {
                    val view = WindowInspector.getGlobalWindowViews()
                        .mapNotNull { it.findViewById<View>(R.id.overlay_pulse) }.singleOrNull()
                    if (view == null || !view.isShown) bounds.setEmpty() else {
                        view.getLocationOnScreen(location)
                        bounds.set(location[0], location[1], location[0] + view.width, location[1] + view.height)
                    }
                }
                if (bounds != previous) {
                    previous.set(bounds)
                    stableSince = SystemClock.uptimeMillis()
                }
                !bounds.isEmpty && matches(bounds) && SystemClock.uptimeMillis() - stableSince >= 250
            }
        } catch (timeout: ComposeTimeoutException) {
            throw AssertionError("$message. Last bounds $bounds, saved ${OverlayPlacementStore(app).read()}", timeout)
        }
        return bounds
    }

    private fun drag(from: Rect, toX: Int, toY: Int) {
        val down = SystemClock.uptimeMillis()
        fun send(action: Int, fraction: Float) {
            val x = from.exactCenterX() + (toX - from.exactCenterX()) * fraction
            val y = from.exactCenterY() + (toY - from.exactCenterY()) * fraction
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { assertTrue(automation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
        send(MotionEvent.ACTION_DOWN, 0f)
        for (step in 1..10) {
            SystemClock.sleep(25)
            send(MotionEvent.ACTION_MOVE, step / 10f)
        }
        send(MotionEvent.ACTION_UP, 1f)
    }

    private fun digitBounds(badge: Rect): Rect {
        val bitmap = checkNotNull(automation.takeScreenshot())
        return try {
            val ink = Rect()
            // Exclude pixels of the underlying map visible through the rounded corners.
            val inset = minOf(badge.width(), badge.height()) / 12
            for (y in badge.top + inset until badge.bottom - inset) {
                for (x in badge.left + inset until badge.right - inset) {
                    if (bitmap.getPixel(x, y) == Color.WHITE) ink.union(x, y, x + 1, y + 1)
                }
            }
            ink
        } finally { bitmap.recycle() }
    }

    @Test fun dragAndPinchPersistAcrossWorkoutsAndRotation() {
        mode("allow")
        start()
        val firstRide = rideId
        click(R.string.overlay_show)
        if (args.getString("openMaps") == "true") {
            shell("am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p com.google.android.apps.maps")
            waitFor("Maps in front") { automation.rootInActiveWindow?.packageName == "com.google.android.apps.maps" }
        } else openSurface(false)
        val screen = displayBounds()
        keepPulse(126)
        checkPixels(126, 3, Color.BLACK, Color.WHITE)
        val initial = awaitBounds("initial window ready") { true }
        val originalInk = digitBounds(initial)
        assertFalse(originalInk.isEmpty)
        assertEquals(2f, OverlayPlacementStore(app).read().scale, 0f)
        val density = app.resources.displayMetrics.density
        val sideInset = (30f * density).roundToInt()
        assertTrue("First-use digits have room on both sides",
            originalInk.left - initial.left >= sideInset && initial.right - originalInk.right >= sideInset)
        assertTrue("First-use window reserves vertical space", initial.height() >= (120f * density).roundToInt())
        val targetX = screen.width() / 2
        val targetY = screen.height() * 11 / 20
        fun atTarget(bounds: Rect) = abs(bounds.exactCenterX() - targetX) <= 5 && abs(bounds.exactCenterY() - targetY) <= 5
        drag(initial, targetX, targetY)
        val moved = awaitBounds("drag ended at ($targetX, $targetY)", ::atTarget)
        val span = moved.width() * 0.5f
        automation.pinch(moved, span, span / 2)
        val small = awaitBounds("pinch halved the window around its midpoint") {
            abs(it.width() - moved.width() / 2f) <= 4 && abs(it.height() - moved.height() / 2f) <= 4 && atTarget(it)
        }
        checkPixels(126, 3, Color.BLACK, Color.WHITE)
        val smallInk = digitBounds(small).height()
        assertTrue("Digits shrank with the window", smallInk in 1 until (originalInk.height() * 0.6f).toInt())
        automation.pinch(small, span / 2, span * 0.75f)
        val resized = awaitBounds("pinch enlarged the window around its midpoint") {
            abs(it.width() - moved.width() * 0.75f) <= 4 && atTarget(it)
        }
        assertTrue("Digits grew with the window", digitBounds(resized).height() in (smallInk + 1) until originalInk.height())
        val saved = resized
        val preferences = OverlayPlacementStore(app).read()
        assertEquals(1.5f, preferences.scale, 0.04f)
        if (args.getString("captureOverlay") == "true" && args.getString("openMaps") == "true") {
            val bitmap = checkNotNull(automation.takeScreenshot())
            try { File(app.cacheDir, "synthetic-map-overlay-resized.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            finally { bitmap.recycle() }
        }
        // A foreground navigator may lock its own orientation. Rotate a freely resizable map surface.
        openSurface(false)
        val system = app.contentResolver
        val rotation = Settings.System.getInt(system, Settings.System.USER_ROTATION, 0)
        val autoRotate = Settings.System.getInt(system, Settings.System.ACCELEROMETER_ROTATION, 0) != 0
        val actualRotation = app.getSystemService(android.hardware.display.DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY).rotation
        try {
            automation.setRotation((actualRotation + 1) % 4)
            waitFor("rotated display") { (displayBounds().width() > displayBounds().height()) != (screen.width() > screen.height()) }
            pulse(126)
            val rotated = expectReading(126, 3)
            val landscape = displayBounds()
            assertTrue(landscape.contains(rotated))
            assertTrue(abs(rotated.exactCenterX() / landscape.width() - saved.exactCenterX() / screen.width()) < 0.14f)
            assertTrue(abs(rotated.exactCenterY() / landscape.height() - saved.exactCenterY() / screen.height()) < 0.14f)
        } finally {
            automation.setRotation(rotation)
            if (autoRotate) automation.setRotation(UiAutomation.ROTATION_UNFREEZE)
        }
        returnToWorkout()
        click(R.string.overlay_hide)
        waitFor("hidden after resizing") { windows().isEmpty() }
        click(R.string.overlay_show)
        pulse(126)
        assertEquals(saved, expectReading(126, 3))
        click(R.string.workout_pause)
        click(R.string.workout_finish)
        waitFor("Results") { button(R.string.workout_discard).isDisplayed() }
        button(R.string.workout_discard).performClick()
        waitFor("first ride closed") { app.recorder.state.value.ride == null && windows().isEmpty() }
        start()
        assertNotEquals(firstRide, rideId)
        assertTrue(windows().isEmpty())
        click(R.string.overlay_show)
        pulse(126)
        assertEquals(saved, expectReading(126, 3))
        assertEquals(preferences, OverlayPlacementStore(app).read())
        drag(saved, screen.right - 2, screen.bottom - 2)
        val atEdge = awaitBounds("drag reached the lower-right edge") {
            it.right > screen.width() * 0.9 && it.bottom > screen.height() * 0.85
        }
        assertTrue("Edge kept on-screen: $atEdge in $screen", screen.contains(atEdge))
        drag(atEdge, targetX, targetY)
        val returned = awaitBounds("drag back from the lower-right edge", ::atTarget)
        assertEquals(saved.width(), returned.width())
        assertEquals(saved.height(), returned.height())
    }

    @Test fun displaysSnapshotOverOtherAppsAndCleansUpOnFinish() {
        mode("allow")
        start()
        val connections = app.connections
        click(R.string.overlay_show)
        expectReading(null)
        runBlocking { app.zones.save(fixtureZones()) }
        val maps = args.getString("openMaps") == "true"
        if (maps) {
            shell("am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p com.google.android.apps.maps")
            waitFor("Maps in front") { automation.rootInActiveWindow?.packageName == "com.google.android.apps.maps" }
        } else openSurface(false)
        pulse(125)
        expectReading(125, 2)
        pulse(126)
        checkPixels(126, 3, Color.BLACK, Color.WHITE, capture = maps)
        expectReading(null)
        pulse(144)
        checkPixels(144, 4, Color.WHITE, Color.BLACK)
        send(HeartRateMeasurement.ContactLost)
        expectReading(null)
        pulse(126)
        expectReading(126, 3)
        val power = app.getSystemService(PowerManager::class.java)
        try {
            shell("input keyevent KEYCODE_SLEEP")
            waitFor("display asleep") { !power.isInteractive }
            SystemClock.sleep(SensorRepository.FRESH_MS + 100)
        } finally {
            shell("input keyevent KEYCODE_WAKEUP")
            shell("wm dismiss-keyguard")
        }
        expectReading(null)
        openSurface(false)
        val control = automation.rootInActiveWindow.findAccessibilityNodeInfosByText("Map control 0").single()
        val target = Rect().also(control::getBoundsInScreen)
        assertFalse(expectReading(null).contains(target.centerX(), target.centerY()))
        shell("input tap ${target.centerX()} ${target.centerY()}")
        waitFor("tap at $target reached other app") { automation.rootInActiveWindow?.findAccessibilityNodeInfosByText("Map control 1")?.size == 1 }
        if (Build.VERSION.SDK_INT >= 31) {
            openSurface(true)
            waitFor("overlay suppressed") { windows().isEmpty() }
            assertEquals(OverlayPhase.Visible, app.overlay.state.value?.phase)
            openSurface(false)
            expectReading(null)
        }
        returnToWorkout()
        click(R.string.overlay_hide)
        waitFor("hidden") { windows().isEmpty() }
        pulse(130)
        waitFor("captured while hidden") { count() == 5 }
        click(R.string.overlay_show)
        waitFor("reattached without interrupting the ride") { app.overlay.state.value?.phase == OverlayPhase.Visible }
        click(R.string.workout_pause)
        waitFor("paused") { app.recorder.state.value.ride?.phase == WorkoutPhase.Paused }
        val frozen = app.recorder.state.value.ride!!.activeMs
        pulse(130)
        expectReading(130, 3)
        pulse(172)
        expectReading(172, 5)
        assertEquals(5, count())
        assertEquals(frozen, app.recorder.state.value.ride!!.activeMs)
        click(R.string.workout_resume)
        waitFor("resumed") { app.recorder.state.value.ride?.phase == WorkoutPhase.Recording }
        pulse(98)
        expectReading(98, 1)
        waitFor("resumed sample") { count() == 6 }
        assertEquals(connections, app.connections)
        click(R.string.workout_pause)
        click(R.string.workout_finish)
        waitFor("Results with window removed") { button(R.string.workout_average).isDisplayed() && windows().isEmpty() }
        assertNull(app.overlay.state.value)
    }

    @Test fun permissionRoundTripAndRevocationPreserveRecording() {
        mode("ignore")
        start()
        val resolver = app.contentResolver
        val originalRotation = Settings.System.getInt(resolver, Settings.System.USER_ROTATION, 0)
        val autoRotate = Settings.System.getInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 0) != 0
        click(R.string.overlay_show)
        button(R.string.cancel).performClick()
        assertNull(app.overlay.state.value)
        fun openSettings() {
            click(R.string.overlay_show)
            button(R.string.overlay_settings).performClick()
            waitFor("system settings") { app.overlay.state.value?.phase == OverlayPhase.InSettings &&
                automation.rootInActiveWindow?.packageName?.toString()?.contains("settings") == true }
        }
        openSettings()
        shell("input keyevent KEYCODE_BACK")
        waitFor("denial") { app.overlay.state.value?.phase == OverlayPhase.AccessDenied }
        pulse(125)
        waitFor("recorded after denial") { count() == 1 }
        openSettings()
        try {
            automation.setRotation((originalRotation + 1) % 4)
            mode("allow")
            shell("input keyevent KEYCODE_BACK")
            waitFor("granted after rotation") { app.overlay.state.value?.phase == OverlayPhase.Visible }
            pulse(143)
            expectReading(143, 3)
        } finally {
            automation.setRotation(originalRotation)
            if (autoRotate) automation.setRotation(UiAutomation.ROTATION_UNFREEZE)
        }
        openSurface(false)
        mode("ignore")
        waitFor("revoked in background") { app.overlay.state.value?.phase == OverlayPhase.AccessDenied && windows().isEmpty() }
        pulse(144)
        waitFor("recorded after revocation") { count() == 3 }
        assertEquals(rideId, app.recorder.state.value.ride?.id)
        returnToWorkout()
        openSettings()
        instrumentation.runOnMainSync {
            app.recorder.pause(rideId!!)
            app.recorder.finish(rideId!!)
        }
        waitFor("finished during Settings round trip") { app.recorder.state.value.ride?.phase == WorkoutPhase.Review }
        mode("allow")
        shell("input keyevent KEYCODE_BACK")
        waitFor("late grant leaves Results without an overlay") { button(R.string.workout_average).isDisplayed() && windows().isEmpty() }
        assertNull(app.overlay.state.value)
    }

    @Test fun unconfiguredRideKeepsItsNeutralWindowWhenPulseGainsADigit() {
        app.fixtureSettings.value = ZoneSettings.Unconfigured
        compose.waitForIdle()
        mode("allow")
        start()
        assertNull(app.recorder.state.value.ride!!.zones)
        click(R.string.overlay_show)
        runBlocking { app.zones.save(zones) }
        pulse(98)
        checkPixels(98, null, Color.BLACK, Color.WHITE)
        val twoDigits = expectReading(98)
        pulse(126)
        checkPixels(126, null, Color.BLACK, Color.WHITE)
        assertEquals(twoDigits, expectReading(126))
        compose.activityRule.scenario.recreate()
        pulse(172)
        expectReading(172)
        assertEquals(1, windows().size)
    }

    @Test fun windowManagerFailureLeavesRideAvailableForRetry() {
        mode("allow")
        start()
        val ride = rideId!!
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val request = WorkoutOverlay(app.recorder.state)
        var attempted = false
        val activity = compose.activity
        val base = if (Build.VERSION.SDK_INT >= 30)
            activity.createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null) else app
        val realManager = base.getSystemService(WindowManager::class.java)
        val manager = object : WindowManager by realManager {
            @RequiresApi(30)
            override fun getCurrentWindowMetrics(): WindowMetrics = realManager.currentWindowMetrics

            override fun addView(view: View, params: ViewGroup.LayoutParams) {
                if (!attempted) {
                    attempted = true
                    throw WindowManager.BadTokenException("Synthetic window failure")
                }
                realManager.addView(view, params)
            }
        }
        val context = object : ContextWrapper(base) {
            override fun createDisplayContext(display: Display): Context = this
            override fun createWindowContext(type: Int, options: Bundle?): Context = this
            override fun getSystemService(name: String): Any? = if (name == WINDOW_SERVICE) manager else super.getSystemService(name)
        }
        lateinit var host: WorkoutOverlayWindow
        try {
            instrumentation.runOnMainSync {
                host = WorkoutOverlayWindow(context, request, app.sensors, scope)
                request.show(ride, true)
            }
            waitFor("recoverable attachment failure") { request.state.value?.phase == OverlayPhase.WindowFailed }
            assertTrue(attempted)
            pulse(126)
            waitFor("still recording") { count() == 1 }
            instrumentation.runOnMainSync { request.show(ride, true) }
            waitFor("retry attached") { request.state.value?.phase == OverlayPhase.Visible }
            keepPulse(126)
            expectReading(126, 3)
        } finally {
            instrumentation.runOnMainSync { host.close(); scope.cancel() }
        }
    }
}

internal fun UiAutomation.pinch(badge: Rect, fromSpan: Float, toSpan: Float) {
    val down = SystemClock.uptimeMillis()
    val fingers = Array(2) { i -> MotionEvent.PointerProperties().apply { id = i; toolType = MotionEvent.TOOL_TYPE_FINGER } }
    fun send(action: Int, count: Int, span: Float) {
        val positions = Array(count) { i -> MotionEvent.PointerCoords().apply {
            x = badge.exactCenterX() + (if (i == 0) -0.5f else 0.5f) * span
            y = badge.exactCenterY()
            pressure = 1f
            size = 1f
        } }
        val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, count, fingers, positions,
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        try { assertTrue(injectInputEvent(event, true)) } finally { event.recycle() }
    }
    val index = 1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT
    send(MotionEvent.ACTION_DOWN, 1, fromSpan)
    send(MotionEvent.ACTION_POINTER_DOWN or index, 2, fromSpan)
    for (step in 1..6) {
        SystemClock.sleep(16)
        send(MotionEvent.ACTION_MOVE, 2, fromSpan + (toSpan - fromSpan) * step / 6)
    }
    send(MotionEvent.ACTION_POINTER_UP or index, 2, toSpan)
    send(MotionEvent.ACTION_UP, 1, toSpan)
    waitForIdle(120, 2_000)
}
