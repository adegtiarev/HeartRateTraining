package arg.adegtiarev.heartratetraining.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import arg.adegtiarev.heartratetraining.R
import arg.adegtiarev.heartratetraining.workout.WorkoutResults
import java.text.NumberFormat
import kotlin.math.ceil

@Composable
fun WorkoutResultsScreen(load: WorkoutLoad<WorkoutResults>, pending: Boolean, onBack: () -> Unit,
    onRetry: () -> Unit, onSave: () -> Unit, onDiscard: () -> Unit, storageError: Boolean) {
    Scaffold { insets ->
        Column(Modifier.fillMaxSize().padding(insets).wrapContentWidth().widthIn(max = 720.dp)) {
            RideHeader(stringResource(R.string.workout_results), onBack)
            WorkoutContent(load, onRetry) { result ->
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(dateText(result.ride.startedAt), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Metric(stringResource(R.string.workout_average), result.average, Modifier.weight(1f).fillMaxHeight())
                        Metric(stringResource(R.string.workout_maximum), result.maximum, Modifier.weight(1f).fillMaxHeight())
                    }
                    Text(stringResource(R.string.workout_duration_label, durationText(result.ride.activeMs)),
                        style = MaterialTheme.typography.titleMedium)
                    ResultPanel {
                        Text(stringResource(R.string.workout_chart), style = MaterialTheme.typography.titleMedium)
                        if (result.segments.isEmpty()) Text(stringResource(R.string.workout_no_samples))
                        else HeartRateChart(result)
                    }
                    ResultPanel {
                        Text(stringResource(R.string.workout_zone_times), style = MaterialTheme.typography.titleMedium)
                        ZoneBreakdown(result)
                    }
                    Spacer(Modifier.height(4.dp))
                }
                if (pending) {
                    if (storageError) Text(stringResource(R.string.workout_storage_error), Modifier.padding(horizontal = 16.dp))
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = onDiscard, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.workout_discard)) }
                        Button(onClick = onSave, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.zone_save)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultPanel(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun Metric(label: String, bpm: Int?, modifier: Modifier) {
    Surface(modifier, color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxHeight().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(painterResource(R.drawable.ic_workout_heart), contentDescription = null, modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.error)
                Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(bpmText(bpm), modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun HeartRateChart(result: WorkoutResults) {
    val locale = LocalConfiguration.current.locales[0]
    val ticks = remember(result) {
        val low = result.segments.minOf { segment -> segment.minOf { checkNotNull(it.bpm) } }
        val high = checkNotNull(result.maximum)
        val step = listOf(5, 10, 20, 50, 100, 250, 500, 1000, 2500, 5000, 10000, 25000)
            .first { it * 4 >= high - low }
        val start = low / step * step
        val end = maxOf(start + step, ceil(high.toDouble() / step).toInt() * step)
        (start..end step step).toList()
    }
    val time = maxOf(1L, result.ride.offsetMs)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val line = if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFFFF8E91) else Color(0xFFBF3447)
    val pauseColor = Color(0xFFBD852C)
    val lossColor = Color(0xFF687F96)
    val pauses = result.breaks.count { it.pause }
    val gaps = result.breaks.size - pauses
    val description = stringResource(R.string.workout_chart_description, stringResource(R.string.workout_chart),
        pluralStringResource(R.plurals.workout_pauses, pauses, pauses), pluralStringResource(R.plurals.workout_gaps, gaps, gaps))
    Text(stringResource(R.string.bpm_unit), style = MaterialTheme.typography.labelSmall)
    Row(Modifier.fillMaxWidth().height(152.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
            ticks.asReversed().forEach { Text(String.format(locale, "%d", it), style = MaterialTheme.typography.labelSmall) }
        }
        Spacer(Modifier.weight(1f).fillMaxHeight().padding(vertical = 7.dp).semantics { contentDescription = description }
            .drawWithCache {
                fun x(ms: Long) = (ms.toDouble() / time * size.width).toFloat()
                fun y(bpm: Int) = size.height * (ticks.last() - bpm) / (ticks.last() - ticks.first())
                val paths = mutableListOf<Path>()
                val dots = mutableListOf<Offset>()
                for (segment in result.segments) {
                    val first = segment.first()
                    if (segment.size == 1) dots += Offset(x(first.offsetMs), y(checkNotNull(first.bpm)))
                    else paths += Path().apply {
                        moveTo(x(first.offsetMs), y(checkNotNull(first.bpm)))
                        for (i in 1 until segment.size) {
                            val point = segment[i]
                            lineTo(x(point.offsetMs), y(checkNotNull(point.bpm)))
                        }
                    }
                }
                val stroke = Stroke(width = 2.dp.toPx())
                onDrawBehind {
                    result.breaks.forEach { br ->
                        val left = x(br.offsetMs)
                        drawRect((if (br.pause) pauseColor else lossColor).copy(alpha = .23f), Offset(left, 0f),
                            Size((x(br.endMs) - left).coerceAtLeast(0f), size.height))
                    }
                    ticks.forEach { tick -> drawLine(grid, Offset(0f, y(tick)), Offset(size.width, y(tick))) }
                    paths.forEach { drawPath(it, line, style = stroke) }
                    dots.forEach { drawCircle(line, 2.dp.toPx(), it) }
                }
            })
    }
    Row(Modifier.fillMaxWidth().padding(start = 24.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf(0L, time / 2, time).forEach { Text(durationText(it, locale), style = MaterialTheme.typography.labelSmall) }
    }
    if (result.breaks.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (pauses > 0) Legend(pauseColor, stringResource(R.string.workout_pause))
        if (gaps > 0) Legend(lossColor, stringResource(R.string.workout_gap))
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Spacer(Modifier.size(10.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ZoneBreakdown(result: WorkoutResults) {
    val config = result.ride.zones
    if (config == null) {
        Text(stringResource(R.string.workout_no_zones))
        return
    }
    Text(stringResource(R.string.workout_zone_share), style = MaterialTheme.typography.bodySmall)
    val total = result.zoneMillis.sum()
    val percent = NumberFormat.getPercentInstance(LocalConfiguration.current.locales[0])
    config.colors.forEachIndexed { i, rgb ->
        val duration = result.zoneMillis[i]
        val fraction = if (total == 0L) 0.0 else duration.toDouble() / total
        val share = if (duration > 0 && fraction < .01)
            stringResource(R.string.workout_less_than, percent.format(.01)) else percent.format(fraction)
        val color = opaqueRgb(rgb)
        val lower = config.thresholds.getOrNull(i - 1) ?: 0
        val upper = config.thresholds.getOrNull(i)
        val range = if (upper == null) stringResource(R.string.zone_open_range, lower)
            else stringResource(R.string.zone_closed_range, lower, upper - 1)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Spacer(Modifier.size(10.dp).background(color, CircleShape).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
                Text(stringResource(R.string.zone_number, i + 1), style = MaterialTheme.typography.titleSmall)
                Text(range, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f).height(7.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                    Spacer(Modifier.fillMaxHeight().fillMaxWidth(fraction.toFloat()).background(color, CircleShape))
                }
                Text(durationText(duration), style = MaterialTheme.typography.labelMedium)
                Text(share, style = MaterialTheme.typography.labelSmall)
            }
            if (duration == 0L && i + 1 in result.reached) Text(stringResource(R.string.workout_zone_reached),
                style = MaterialTheme.typography.labelSmall)
        }
    }
}
