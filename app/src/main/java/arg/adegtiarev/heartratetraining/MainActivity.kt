package arg.adegtiarev.heartratetraining

import android.bluetooth.BluetoothAdapter
import android.Manifest
import android.content.BroadcastReceiver
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import arg.adegtiarev.heartratetraining.bluetooth.sensorPermissions
import arg.adegtiarev.heartratetraining.ui.HeartRateScreens
import arg.adegtiarev.heartratetraining.ui.HomeViewModel
import arg.adegtiarev.heartratetraining.ui.WorkoutViewModel
import arg.adegtiarev.heartratetraining.workout.WorkoutService
import arg.adegtiarev.heartratetraining.workout.OverlayPhase
import arg.adegtiarev.heartratetraining.zones.ZoneConfiguration
import arg.adegtiarev.heartratetraining.ui.contrastingText
import arg.adegtiarev.heartratetraining.ui.theme.HeartRateTrainingTheme

class MainActivity : ComponentActivity() {
    private val app get() = application as HeartRateApplication
    private val sensors get() = app.sensors
    private val zones get() = app.zones
    private val workouts by viewModels<WorkoutViewModel> {
        viewModelFactory { initializer { WorkoutViewModel(app.recorder, sensors, app.overlay, SystemClock::elapsedRealtime) } }
    }
    private var notificationVisit by mutableIntStateOf(0)
    private var explainNotifications by mutableStateOf(false)
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { startRecordingService() }
    private val model by viewModels<HomeViewModel> {
        viewModelFactory { initializer { HomeViewModel(sensors, zones) } }
    }
    private var openPermissionSettings by mutableStateOf(false)
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        openPermissionSettings = sensorPermissions().any {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED && !shouldShowRequestPermissionRationale(it)
        }
        sensors.refresh()
    }
    private val systemAction = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        sensors.refresh()
    }
    private val radioChanges = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = sensors.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensors.startVisit()
        if (savedInstanceState == null && intent.action == WorkoutService.OPEN_WORKOUT) notificationVisit++
        openPermissionSettings = savedInstanceState?.getBoolean("permissionSettings") == true
        enableEdgeToEdge()
        setContent {
            HeartRateTrainingTheme {
                HeartRateScreens(
                    home = model,
                    zones = zones,
                    workouts = workouts, library = app.library, onStartWorkout = ::startWorkout,
                    onShowOverlay = { id -> app.overlay.show(id, Settings.canDrawOverlays(this)) },
                    onOverlaySettings = ::openOverlaySettings,
                    notificationVisit = notificationVisit,
                    explainNotifications = explainNotifications,
                    legacyLocation = Build.VERSION.SDK_INT < Build.VERSION_CODES.S,
                    openPermissionSettings = openPermissionSettings,
                    onPermission = {
                        if (openPermissionSettings) {
                            systemAction.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()))
                        } else permissions.launch(sensorPermissions())
                    },
                    onEnableBluetooth = {
                        try {
                            systemAction.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                        } catch (_: SecurityException) {
                            sensors.refresh()
                        }
                    },
                    onEnableLocation = { systemAction.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
                    onBackgroundChanged = { color ->
                        val bars = WindowCompat.getInsetsController(window, window.decorView)
                        val darkIcons = contrastingText(color) == Color.Black
                        bars.isAppearanceLightStatusBars = darkIcons
                        bars.isAppearanceLightNavigationBars = darkIcons
                    },
                )
            }
        }
    }

    private fun openOverlaySettings(id: String) {
        if (!app.overlay.openSettings(id)) return
        val action = Settings.ACTION_MANAGE_OVERLAY_PERMISSION
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && launchOverlaySettings(Intent(action, "package:$packageName".toUri()))) return
        if (!launchOverlaySettings(Intent(action))) app.overlay.failed(id)
    }

    private fun launchOverlaySettings(intent: Intent): Boolean = try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    private fun startWorkout(config: ZoneConfiguration?) {
        if (!app.recorder.requestStart(config)) return
        if (Build.VERSION.SDK_INT >= 33 && shouldAskNotifications()) {
            getSharedPreferences("workout_permissions", MODE_PRIVATE).edit { putBoolean("asked_notifications", true) }
            explainNotifications = false
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else startRecordingService()
    }

    private fun shouldAskNotifications() = Build.VERSION.SDK_INT >= 33 &&
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
        !getSharedPreferences("workout_permissions", MODE_PRIVATE).getBoolean("asked_notifications", false)

    private fun startRecordingService() {
        if (!app.recorder.state.value.starting) return
        try {
            ContextCompat.startForegroundService(this, Intent(this, WorkoutService::class.java))
        } catch (_: RuntimeException) {
            app.recorder.startFailed()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == WorkoutService.OPEN_WORKOUT) notificationVisit++
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(this, radioChanges, IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(LocationManager.MODE_CHANGED_ACTION)
        }, ContextCompat.RECEIVER_EXPORTED)
        sensors.setVisible(true)
    }

    override fun onResume() {
        super.onResume()
        if (app.overlay.state.value?.phase == OverlayPhase.InSettings) {
            app.overlay.returnedFromSettings(Settings.canDrawOverlays(this))
        }
        explainNotifications = shouldAskNotifications()
        if (sensorPermissions().all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) {
            openPermissionSettings = false
        }
        sensors.refresh()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("permissionSettings", openPermissionSettings)
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        unregisterReceiver(radioChanges)
        if (!isChangingConfigurations) sensors.setVisible(false)
        super.onStop()
    }

    override fun onDestroy() {
        if (isFinishing) sensors.stopVisit()
        super.onDestroy()
    }
}
