package arg.adegtiarev.heartratetraining.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import arg.adegtiarev.heartratetraining.R
import arg.adegtiarev.heartratetraining.sensor.*

@Composable
fun HomeScreen(
    state: SensorState,
    legacyLocation: Boolean,
    openPermissionSettings: Boolean,
    onPermission: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onEnableLocation: () -> Unit,
    onFind: () -> Unit,
    onRetry: () -> Unit,
    onDisconnect: () -> Unit,
    onSelect: (Strap) -> Unit,
) {
    val picker = state.access == SensorAccess.Ready &&
        state.phase in setOf(SensorPhase.Scanning, SensorPhase.ScanFinished)
    BackHandler(enabled = picker, onBack = onDisconnect)
    Scaffold { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(insets).wrapContentWidth().widthIn(max = 600.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(stringResource(R.string.heart_rate), style = MaterialTheme.typography.headlineMedium)
            }
            item { PulseDisplay(state.sample) }
            item { Text(stringResource(statusText(state, legacyLocation)), style = MaterialTheme.typography.bodyLarge) }
            val strapName = (state.active ?: state.remembered)?.name
            if (strapName != null && !picker) {
                item { Text(strapName, style = MaterialTheme.typography.labelLarge) }
            }
            if (state.storageError) {
                item { Text(stringResource(R.string.storage_error), color = MaterialTheme.colorScheme.error) }
            }
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    when (state.access) {
                        SensorAccess.PermissionNeeded -> Action(
                            if (openPermissionSettings) R.string.open_settings else R.string.grant_permission, onPermission)
                        SensorAccess.BluetoothOff -> Action(R.string.enable_bluetooth, onEnableBluetooth)
                        SensorAccess.LocationOff -> Action(R.string.enable_location, onEnableLocation)
                        SensorAccess.Ready -> when (state.phase) {
                            SensorPhase.Scanning -> SecondaryAction(R.string.cancel, onDisconnect)
                            SensorPhase.ScanFinished -> {
                                Action(R.string.scan_again, onFind)
                                SecondaryAction(R.string.cancel, onDisconnect)
                            }
                            SensorPhase.Searching, SensorPhase.Connecting, SensorPhase.Waiting, SensorPhase.Live,
                            SensorPhase.ContactLost, SensorPhase.Stale -> Action(R.string.disconnect, onDisconnect)
                            SensorPhase.Idle -> {
                                if (state.remembered != null) {
                                    Action(R.string.connect, onRetry)
                                    SecondaryAction(R.string.choose_strap, onFind)
                                } else Action(R.string.find_strap, onFind)
                            }
                            else -> {
                                Action(R.string.retry, onRetry)
                                if (state.active != null || state.remembered != null) {
                                    SecondaryAction(R.string.choose_strap, onFind)
                                }
                            }
                        }
                        SensorAccess.Checking, SensorAccess.Unavailable -> Unit
                    }
                }
            }
            if (picker) {
                items(state.nearby, key = { it.strap.id }) { device ->
                    OutlinedButton(onClick = { onSelect(device.strap) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(device.strap.name ?: stringResource(R.string.unnamed_sensor))
                            val suffix = device.strap.id.takeLast(5)
                            Text(
                                stringResource(if (device.advertisesHeartRate) R.string.heart_sensor_id else R.string.device_id, suffix),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PulseDisplay(sample: PulseSample?) {
    val reading = if (sample == null) stringResource(R.string.no_reading) else stringResource(R.string.number, sample.bpm)
    val description = if (sample == null) stringResource(R.string.no_reading_description)
        else pluralStringResource(R.plurals.pulse_description, sample.bpm, sample.bpm)
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.extraLarge) {
        Column(
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description }
                .padding(horizontal = 12.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(reading, style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 78.sp, lineHeight = 86.sp, fontWeight = FontWeight.Medium))
            Text(stringResource(R.string.bpm_unit), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun Action(@StringRes label: Int, onClick: () -> Unit) {
    Button(onClick = onClick) { Text(stringResource(label)) }
}

@Composable
private fun SecondaryAction(@StringRes label: Int, onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(stringResource(label)) }
}

@StringRes
private fun statusText(state: SensorState, legacyLocation: Boolean): Int = when (state.access) {
    SensorAccess.Checking -> R.string.checking
    SensorAccess.Unavailable -> R.string.ble_unavailable
    SensorAccess.PermissionNeeded -> if (legacyLocation) R.string.permission_location else R.string.permission_nearby
    SensorAccess.BluetoothOff -> R.string.bluetooth_off
    SensorAccess.LocationOff -> R.string.location_off
    SensorAccess.Ready -> when (state.phase) {
        SensorPhase.Idle -> R.string.disconnected
        SensorPhase.Scanning -> R.string.scanning
        SensorPhase.ScanFinished -> if (state.nearby.isEmpty()) R.string.empty_scan else R.string.pick_sensor
        SensorPhase.Searching -> R.string.searching
        SensorPhase.Connecting -> R.string.connecting
        SensorPhase.Waiting -> R.string.waiting
        SensorPhase.Live -> R.string.live
        SensorPhase.ContactLost -> R.string.contact_lost
        SensorPhase.Stale -> R.string.stale
        SensorPhase.Absent -> R.string.absent
        SensorPhase.ScanFailed -> R.string.scan_failed
        SensorPhase.ConnectionFailed -> R.string.connection_failed
        SensorPhase.Unsupported -> R.string.unsupported
        SensorPhase.SubscriptionFailed -> R.string.subscription_failed
    }
}
