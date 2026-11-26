package arg.adegtiarev.heartratetraining.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import arg.adegtiarev.heartratetraining.R
import arg.adegtiarev.heartratetraining.zones.MAX_BPM
import arg.adegtiarev.heartratetraining.zones.ThresholdError
import arg.adegtiarev.heartratetraining.zones.ZONE_COUNT

private val rowInset = 14.dp
private val rangeGap = 20.dp
internal data class ZoneTableColumns(val bpmWidth: Dp, val stacked: Boolean)

@Composable
internal fun zoneTableColumns(availableWidth: Dp): ZoneTableColumns {
    val measure = rememberTextMeasurer()
    val numberStyle = MaterialTheme.typography.titleMedium
    val headerStyle = MaterialTheme.typography.labelMedium
    val from = stringResource(R.string.zone_column_from)
    val to = stringResource(R.string.zone_column_to)
    val longestBpm = stringResource(R.string.number, MAX_BPM)
    val name = stringResource(R.string.zone_number, ZONE_COUNT)
    return with(LocalDensity.current) {
        val bpmWidth = maxOf(
            80.dp, measure.measure(longestBpm, numberStyle).size.width.toDp() + 36.dp,
            measure.measure(from, headerStyle).size.width.toDp(), measure.measure(to, headerStyle).size.width.toDp(),
        )
        val nameWidth = 24.dp + 10.dp + measure.measure(name, numberStyle).size.width.toDp() + 8.dp
        ZoneTableColumns(bpmWidth, availableWidth < rowInset * 2 + nameWidth + rangeGap + bpmWidth * 2)
    }
}

@Composable
internal fun ZoneTableHeader(columns: ZoneTableColumns) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = rowInset, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ColumnLabel(R.string.zone_column_name, Modifier.weight(1f), TextAlign.Start)
        ColumnLabel(R.string.zone_column_from, Modifier.width(columns.bpmWidth))
        Spacer(Modifier.width(rangeGap))
        ColumnLabel(R.string.zone_column_to, Modifier.width(columns.bpmWidth))
    }
}

@Composable
private fun ColumnLabel(label: Int, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Center) {
    Text(stringResource(label), modifier, style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = align)
}

@Composable
internal fun ZoneTableRow(
    modifier: Modifier,
    state: ZoneEditorState.Editing,
    index: Int,
    model: ZoneSettingsViewModel,
    columns: ZoneTableColumns,
    colorOpen: Boolean,
    onColorClick: () -> Unit,
) {
    val number = index + 1
    val input = state.draft.thresholds.getOrNull(index - 1).orEmpty()
    val issue = state.draft.errors.getOrNull(index - 1).takeIf { state.showErrors || input.isNotEmpty() }
    val message = when (issue) {
        ThresholdError.OutOfOrder -> stringResource(R.string.zone_threshold_order)
        ThresholdError.OutOfRange -> stringResource(R.string.zone_threshold_invalid)
        null -> null
    }
    Column(modifier.fillMaxWidth().padding(horizontal = rowInset)) {
        val cells: @Composable () -> Unit = {
            ZoneColorButton(number, state.draft.palette[index], !state.saving, onColorClick)
            val bounds = if (columns.stacked) Modifier.fillMaxWidth() else Modifier.width(columns.bpmWidth * 2 + rangeGap)
            ZoneBounds(bounds, state, index, input, message, columns.stacked) { model.setThreshold(index - 1, it) }
        }
        val row = Modifier.fillMaxWidth().padding(vertical = 5.dp)
        if (columns.stacked) {
            Column(row) { cells() }
        } else {
            Row(row, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                cells()
            }
        }
        if (message != null) {
            Text(message, Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error)
        }
        if (colorOpen) {
            Column(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ZoneColorEditor(state, index, model)
            }
        }
        if (number < ZONE_COUNT) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun ZoneColorButton(number: Int, rgb: Int?, enabled: Boolean, onClick: () -> Unit) {
    val choose = stringResource(R.string.zone_choose_color)
    val color = rgb?.let { "#" + rgbText(it) } ?: choose
    val label = stringResource(R.string.zone_color_description, number, color)
    Row(
        Modifier.clip(MaterialTheme.shapes.small).clickable(enabled = enabled, role = Role.Button,
            onClickLabel = choose, onClick = onClick).semantics { contentDescription = label }.heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ColorDot(rgb)
        Text(stringResource(R.string.zone_number, number), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ZoneBounds(
    modifier: Modifier, state: ZoneEditorState.Editing, index: Int, input: String, issue: String?,
    showLabels: Boolean, onChange: (String) -> Unit,
) {
    val number = index + 1
    val typography = MaterialTheme.typography
    val style = typography.titleMedium.copy(fontFeatureSettings = "tnum", textAlign = TextAlign.Center)
    val upper = state.draft.upperBound(index)
    val end = when {
        number == ZONE_COUNT -> "∞"
        upper != null -> stringResource(R.string.number, upper)
        else -> stringResource(R.string.no_reading)
    }
    val range = state.draft.range(index)
    val last = range?.second
    val spokenEnd = when {
        range != null && last != null -> stringResource(R.string.zone_closed_range, range.first, last)
        range != null -> stringResource(R.string.zone_open_range, range.first)
        number == ZONE_COUNT -> stringResource(R.string.zone_unlimited)
        else -> stringResource(R.string.zone_column_to) + " " + end
    }
    val zoneRange = stringResource(R.string.zone_reading, stringResource(R.string.zone_number, number), spokenEnd)
    Column(modifier) {
        if (showLabels) Row(verticalAlignment = Alignment.CenterVertically) {
            ColumnLabel(R.string.zone_column_from, Modifier.weight(1f))
            Spacer(Modifier.width(rangeGap))
            ColumnLabel(R.string.zone_column_to, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            if (index == 0) {
                Text(stringResource(R.string.number, 0), Modifier.weight(1f), style = style)
            } else {
                BpmField(Modifier.weight(1f), number, input, issue, !state.saving, onChange)
            }
            Text("–", Modifier.width(rangeGap).clearAndSetSemantics {}, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.outline)
            Text(end, Modifier.weight(1f).clearAndSetSemantics { contentDescription = zoneRange }, style = style,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BpmField(
    modifier: Modifier, zone: Int, value: String, issue: String?, enabled: Boolean, onChange: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val label = stringResource(R.string.zone_start_field, zone)
    val style = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center, fontFeatureSettings = "tnum")
    OutlinedTextField(
        value = value, onValueChange = onChange, enabled = enabled, singleLine = true,
        modifier = modifier.keepVisibleWithKeyboard().testTag("threshold-$zone").semantics {
            contentDescription = label
            if (issue != null) error(issue)
        },
        textStyle = style, shape = MaterialTheme.shapes.small, isError = issue != null,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = colors.surfaceContainerLowest, unfocusedContainerColor = colors.surfaceContainerLowest,
            unfocusedBorderColor = colors.outlineVariant,
        ),
        placeholder = { Text(stringResource(R.string.no_reading), Modifier.fillMaxWidth(), style = style) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number,
            imeAction = if (zone == ZONE_COUNT) ImeAction.Done else ImeAction.Next),
    )
}
