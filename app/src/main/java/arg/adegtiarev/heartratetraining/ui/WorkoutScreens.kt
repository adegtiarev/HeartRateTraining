package arg.adegtiarev.heartratetraining.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import arg.adegtiarev.heartratetraining.R
import arg.adegtiarev.heartratetraining.sensor.SensorPhase
import arg.adegtiarev.heartratetraining.workout.HistoryRide
import arg.adegtiarev.heartratetraining.workout.OverlayPhase
import arg.adegtiarev.heartratetraining.workout.WorkoutPhase
import arg.adegtiarev.heartratetraining.workout.reading
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

internal fun durationText(ms: Long, locale: Locale): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    return if (seconds >= 3600) String.format(locale, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
    else String.format(locale, "%d:%02d", seconds / 60, seconds % 60)
}

@Composable
internal fun durationText(ms: Long) = durationText(ms, LocalConfiguration.current.locales[0])

@Composable
internal fun dateText(epoch: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(Date(epoch))
}

@Composable
internal fun bpmText(bpm: Int?) =
    if (bpm == null) stringResource(R.string.no_reading) else stringResource(R.string.workout_bpm, bpm)

@Composable
internal fun RideHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val back = stringResource(R.string.workout_back)
        IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = back }) {
            val color = LocalContentColor.current
            androidx.compose.foundation.Canvas(Modifier.size(22.dp)) {
                val stroke = 2.dp.toPx()
                drawLine(color, center.copy(x = size.width * .18f), center.copy(x = size.width * .85f), stroke, StrokeCap.Round)
                drawLine(color, center.copy(x = size.width * .18f), center.copy(y = size.height * .18f), stroke, StrokeCap.Round)
                drawLine(color, center.copy(x = size.width * .18f), center.copy(y = size.height * .82f), stroke, StrokeCap.Round)
            }
        }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun <T> WorkoutContent(load: WorkoutLoad<T>, retry: () -> Unit, content: @Composable (T) -> Unit) {
    when (load) {
        WorkoutLoad.Loading -> Text(stringResource(R.string.workout_loading), Modifier.padding(20.dp))
        WorkoutLoad.Failed -> Column(Modifier.padding(20.dp)) {
            Text(stringResource(R.string.workout_storage_error))
            TextButton(onClick = retry) { Text(stringResource(R.string.retry)) }
        }
        is WorkoutLoad.Ready -> content(load.value)
    }
}

@Composable
fun WorkoutScreen(live: LiveWorkout, legacyLocation: Boolean, onPause: () -> Unit, onResume: () -> Unit,
    onFinish: () -> Unit, onRetry: () -> Unit, onBack: () -> Unit, overlay: OverlayPhase?,
    onShowOverlay: () -> Unit, onHideOverlay: () -> Unit, onOverlaySettings: () -> Unit) {
    val paused = live.ride.phase == WorkoutPhase.Paused
    val reading = live.ride.reading(live.sensor.sample)
    val zoneColor = reading.zone?.let { opaqueRgb(it.rgb) }
    val bg = zoneColor ?: MaterialTheme.colorScheme.background
    val fg = zoneColor?.let(::contrastingText) ?: MaterialTheme.colorScheme.onBackground
    Scaffold(containerColor = bg, contentColor = fg) { insets ->
        LazyColumn(Modifier.fillMaxSize().padding(insets).wrapContentWidth().widthIn(max = 600.dp),
            contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            item { RideHeader(stringResource(if (paused) R.string.workout_paused else R.string.workout_recording), onBack) }
            item { PulseDisplay(reading.bpm, reading.zone) }
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.workout_active_time), style = MaterialTheme.typography.labelLarge)
                    Text(durationText(live.activeMs), style = MaterialTheme.typography.displayMedium)
                }
            }
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(statusText(live.sensor, legacyLocation)))
                    StrapDetails(live.sensor)
                }
            }
            if (live.sensor.phase in setOf(SensorPhase.Absent, SensorPhase.ConnectionFailed, SensorPhase.ScanFailed)) {
                item { TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) } }
            }
            item {
                Button(if (paused) onResume else onPause, Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = fg, contentColor = bg)) {
                    Text(stringResource(if (paused) R.string.workout_resume else R.string.workout_pause))
                }
            }
            if (paused) item {
                TextButton(onFinish, Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColors(contentColor = fg)) {
                    Text(stringResource(R.string.workout_finish))
                }
            }
            item {
                val visible = overlay == OverlayPhase.Visible
                OutlinedButton(onClick = if (visible) onHideOverlay else onShowOverlay,
                    enabled = overlay != OverlayPhase.Attaching && overlay != OverlayPhase.InSettings,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = fg)) {
                    Text(stringResource(if (visible) R.string.overlay_hide else R.string.overlay_show))
                }
            }
            if (overlay == OverlayPhase.Visible) item {
                Text(stringResource(R.string.overlay_gestures), style = MaterialTheme.typography.bodyMedium)
            }
            if (overlay == OverlayPhase.AccessDenied || overlay == OverlayPhase.WindowFailed) item {
                Text(stringResource(if (overlay == OverlayPhase.AccessDenied) R.string.overlay_access_off else R.string.overlay_error))
            }
        }
    }
    if (overlay == OverlayPhase.ExplainAccess) {
        AlertDialog(onDismissRequest = onHideOverlay,
            title = { Text(stringResource(R.string.overlay_title)) },
            text = { Text(stringResource(R.string.overlay_explanation, stringResource(R.string.app_name))) },
            confirmButton = { TextButton(onClick = onOverlaySettings) { Text(stringResource(R.string.overlay_settings)) } },
            dismissButton = { TextButton(onClick = onHideOverlay) { Text(stringResource(R.string.cancel)) } })
    }
}

@Composable
fun HistoryScreen(load: WorkoutLoad<List<HistoryRide>>, onOpen: (String) -> Unit, onBack: () -> Unit, onRetry: () -> Unit) {
    Scaffold { insets ->
        Column(Modifier.fillMaxSize().padding(insets).wrapContentWidth().widthIn(max = 680.dp)) {
            RideHeader(stringResource(R.string.workout_history), onBack)
            WorkoutContent(load, onRetry) { rides ->
                if (rides.isEmpty()) Text(stringResource(R.string.workout_history_empty), Modifier.padding(20.dp))
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(rides, key = { it.id }) { ride ->
                        Surface(onClick = { onOpen(ride.id) }, shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(dateText(ride.startedAt), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(stringResource(R.string.workout_duration_label, durationText(ride.activeMs)))
                                Text(stringResource(R.string.workout_avg_short, bpmText(ride.averageBpm?.roundToInt())),
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
