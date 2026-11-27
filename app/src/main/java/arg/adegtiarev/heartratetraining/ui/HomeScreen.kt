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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import android.content.res.Resources
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import arg.adegtiarev.heartratetraining.R
import arg.adegtiarev.heartratetraining.sensor.*
import arg.adegtiarev.heartratetraining.zones.HeartRateZone
import arg.adegtiarev.heartratetraining.zones.ZoneSettings

@Composable
fun HomeScreen(
    home: HomeUiState,
    legacyLocation: Boolean,
    openPermissionSettings: Boolean,
    onPermission: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onEnableLocation: () -> Unit,
    onFind: () -> Unit,
    onRetry: () -> Unit,
    onDisconnect: () -> Unit,
    onSelect: (Strap) -> Unit,
    onZoneSettings: () -> Unit,
    onRetryZones: () -> Unit,
    onStart: () -> Unit,
    onHistory: () -> Unit,
    workoutAction: Int = R.string.workout_start,
    startEnabled: Boolean = true,
    workoutError: Int? = null,
    onRetryWorkout: () -> Unit = {},
    explainNotifications: Boolean = false,
) {
    val state = home.sensor
    val zoneColor = home.zone?.let { opaqueRgb(it.rgb) }
    val background = zoneColor ?: MaterialTheme.colorScheme.background
    val foreground = zoneColor?.let(::contrastingText) ?: MaterialTheme.colorScheme.onBackground
    val picker = state.access == SensorAccess.Ready &&
        state.phase in setOf(SensorPhase.Scanning, SensorPhase.ScanFinished)
    BackHandler(enabled = picker, onBack = onDisconnect)
    Scaffold(containerColor = background, contentColor = foreground) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(insets).wrapContentWidth().widthIn(max = 600.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(stringResource(R.string.heart_rate), style = MaterialTheme.typography.headlineMedium)
            }
            item { PulseDisplay(state.sample?.bpm, home.zone) }
            if (!picker) item {
                Button(onClick = onStart, enabled = startEnabled,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(workoutAction)) }
                if (explainNotifications) Text(stringResource(R.string.workout_notification_permission),
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    SecondaryAction(R.string.workout_history, onHistory)
                    SecondaryAction(R.string.zone_settings, onZoneSettings)
                }
            }
            if (workoutError != null) item {
                Text(stringResource(workoutError))
                if (workoutError == R.string.workout_storage_error) SecondaryAction(R.string.retry, onRetryWorkout)
            }
            item { Text(stringResource(statusText(state, legacyLocation)), style = MaterialTheme.typography.bodyLarge) }
            val strapName = (state.active ?: state.remembered)?.name
            if (strapName != null && !picker) {
                item { Text(strapName, style = MaterialTheme.typography.labelLarge) }
            }
            if (state.storageError) {
                item { Text(stringResource(R.string.storage_error)) }
            }
            if (home.settings == ZoneSettings.Invalid || home.settings == ZoneSettings.ReadError) {
                item { Text(stringResource(R.string.zone_read_error)) }
                item { SecondaryAction(R.string.retry, onRetryZones) }
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
                            SensorPhase.ContactLost, SensorPhase.Stale -> Action(R.string.disconnect, onDisconnect, zoneColor)
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
internal fun PulseDisplay(bpm: Int?, zone: HeartRateZone?) {
    val resources = LocalResources.current
    val reading = resources.pulseNumber(bpm)
    val zoneName = zone?.let { stringResource(R.string.zone_number, it.number) }
    val description = resources.pulseDescription(bpm, zone)
    Surface(
        color = if (zone == null) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent,
        contentColor = if (zone == null) MaterialTheme.colorScheme.onSurface else LocalContentColor.current,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description }
                .padding(horizontal = 12.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(reading, style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 78.sp, lineHeight = 86.sp, fontWeight = FontWeight.Medium))
            Text(stringResource(R.string.bpm_unit), style = MaterialTheme.typography.titleMedium)
            if (zoneName != null) Text(zoneName, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

internal fun Resources.pulseNumber(bpm: Int?): String =
    if (bpm == null) getString(R.string.no_reading) else getString(R.string.number, bpm)

internal fun Resources.pulseDescription(bpm: Int?, zone: HeartRateZone?): String {
    if (bpm == null) return getString(R.string.no_reading_description)
    val pulse = getQuantityString(R.plurals.pulse_description, bpm, bpm)
    return if (zone == null) pulse else getString(R.string.zone_reading, pulse, getString(R.string.zone_number, zone.number))
}

@Composable
private fun Action(@StringRes label: Int, onClick: () -> Unit, zoneColor: Color? = null) {
    val colors = if (zoneColor == null) ButtonDefaults.buttonColors()
        else ButtonDefaults.buttonColors(containerColor = contrastingText(zoneColor), contentColor = zoneColor)
    Button(onClick = onClick, colors = colors) { Text(stringResource(label)) }
}

@Composable
private fun SecondaryAction(@StringRes label: Int, onClick: () -> Unit) {
    TextButton(onClick = onClick, colors = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current)) {
        Text(stringResource(label))
    }
}

internal fun opaqueRgb(rgb: Int) = Color(rgb or 0xFF000000.toInt())

internal fun contrastingText(background: Color): Color {
    val luminance = background.luminance()
    return if ((luminance + 0.05f) / 0.05f >= 1.05f / (luminance + 0.05f)) Color.Black else Color.White
}

@StringRes
internal fun statusText(state: SensorState, legacyLocation: Boolean): Int = when (state.access) {
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
