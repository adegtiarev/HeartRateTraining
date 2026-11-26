package arg.adegtiarev.heartratetraining

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import arg.adegtiarev.heartratetraining.bluetooth.sensorPermissions
import arg.adegtiarev.heartratetraining.ui.HomeScreen
import arg.adegtiarev.heartratetraining.ui.HomeViewModel
import arg.adegtiarev.heartratetraining.ui.theme.HeartRateTrainingTheme

class MainActivity : ComponentActivity() {
    private val sensors get() = (application as HeartRateApplication).sensors
    private val model by viewModels<HomeViewModel> {
        viewModelFactory { initializer { HomeViewModel(sensors) } }
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
        openPermissionSettings = savedInstanceState?.getBoolean("permissionSettings") == true
        enableEdgeToEdge()
        setContent {
            HeartRateTrainingTheme {
                val state by model.state.collectAsStateWithLifecycle()
                HomeScreen(
                    state = state,
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
                    onFind = model::findStraps,
                    onRetry = model::retry,
                    onDisconnect = model::disconnect,
                    onSelect = model::select,
                )
            }
        }
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
