package arg.adegtiarev.heartratetraining.overlay

import android.app.AppOpsManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.WindowManager
import arg.adegtiarev.heartratetraining.sensor.SensorRepository
import arg.adegtiarev.heartratetraining.ui.batteryAlertPhases
import arg.adegtiarev.heartratetraining.workout.OverlayPhase
import arg.adegtiarev.heartratetraining.workout.Workout
import arg.adegtiarev.heartratetraining.workout.WorkoutOverlay
import arg.adegtiarev.heartratetraining.workout.reading
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Owned by the foreground service. All window and session operations run on Main. */
internal class WorkoutOverlayWindow(
    private val context: Context,
    private val overlay: WorkoutOverlay,
    private val sensors: SensorRepository,
    private val scope: CoroutineScope,
) {
    private val appOps = context.getSystemService(AppOpsManager::class.java)
    private var accessListener: AppOpsManager.OnOpChangedListener? = null
    private var attached: PulseWindowText? = null
    private var readings: Job? = null

    private val watch = scope.launch {
        overlay.state.collect { request ->
            if (request?.phase == OverlayPhase.Visible) return@collect
            detach()
            if (request?.phase == OverlayPhase.Attaching) {
                overlay.activeRide(request.rideId)?.let(::attach)
            }
        }
    }

    private fun attach(ride: Workout) {
        try {
            val listener = AppOpsManager.OnOpChangedListener { _, _ ->
                context.mainExecutor.execute {
                    if (overlay.state.value?.windowRideId == ride.id && !Settings.canDrawOverlays(context)) {
                        overlay.failed(ride.id, accessDenied = true)
                    }
                }
            }
            appOps.startWatchingMode(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, context.packageName, listener)
            accessListener = listener
            if (!Settings.canDrawOverlays(context)) {
                overlay.failed(ride.id, accessDenied = true)
                return
            }
            sensors.expireStaleSample()
            val view = PulseWindowText(windowContext())
            view.onWindowFailure = { overlay.failed(ride.id, accessDenied = !Settings.canDrawOverlays(context)) }
            view.showReading(ride.reading(sensors.state.value.sample))
            view.context.getSystemService(WindowManager::class.java).addView(view, view.params)
            attached = view
            overlay.attached(ride.id)
            readings = scope.launch {
                launch {
                    sensors.state.map { ride.reading(it.sample) }.distinctUntilChanged().collect(view::showReading)
                }
                sensors.state.map { it.hasLowBattery }.distinctUntilChanged().collectLatest { low ->
                    batteryAlertPhases(low).collect(view::showBatteryAlert)
                }
            }
        } catch (error: RuntimeException) {
            Log.w("WorkoutOverlay", "Unable to attach the pulse window", error)
            overlay.failed(ride.id, accessDenied = !Settings.canDrawOverlays(context))
        }
    }

    private fun windowContext(): Context {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return context
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        return context.createDisplayContext(display).createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
    }

    fun refreshLabels() {
        attached?.refreshConfiguration()
    }

    private fun detach() {
        readings?.cancel()
        readings = null
        accessListener?.let(appOps::stopWatchingMode)
        accessListener = null
        val view = attached ?: return
        attached = null
        try {
            view.context.getSystemService(WindowManager::class.java).removeViewImmediate(view)
        } catch (_: IllegalArgumentException) {
            // WindowManager may already have removed the window after access was revoked.
        }
    }

    fun close() {
        watch.cancel()
        detach()
        overlay.state.value?.let { overlay.hide(it.rideId) }
    }
}
