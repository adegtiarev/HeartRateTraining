package arg.adegtiarev.heartratetraining.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import arg.adegtiarev.heartratetraining.R
import arg.adegtiarev.heartratetraining.zones.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZoneSettingsScreen(state: ZoneEditorState, model: ZoneSettingsViewModel) {
    BackHandler(onBack = model::back)
    val editing = state as? ZoneEditorState.Editing
    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.zone_title)) }) },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    if (editing?.saveFailed == true) FieldError(R.string.zone_save_error)
                    if (editing?.showErrors == true && editing.draft.configuration() == null) {
                        FieldError(R.string.zone_form_error)
                    }
                    Row(
                        Modifier.widthIn(max = 600.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(onClick = model::cancel, enabled = editing?.saving != true,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.cancel))
                        }
                        if (editing != null) {
                            Button(onClick = model::save, enabled = !editing.saving,
                                modifier = Modifier.weight(1.3f).heightIn(min = 48.dp)) {
                                Text(stringResource(if (editing.saving) R.string.zone_saving else R.string.zone_save))
                            }
                        }
                    }
                }
            }
        },
    ) { insets ->
        Box(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.TopCenter) {
            when (state) {
                is ZoneEditorState.Editing -> ZoneForm(state, model)
                is ZoneEditorState.LoadFailed -> Column(
                    Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.zone_read_error))
                    Button(onClick = model::retryRead) { Text(stringResource(R.string.retry)) }
                    if (state.canReplace) TextButton(onClick = model::replaceInvalid) {
                        Text(stringResource(R.string.zone_replace))
                    }
                }
                ZoneEditorState.Loading -> Text(stringResource(R.string.zone_loading), Modifier.padding(20.dp))
                ZoneEditorState.Finished -> Unit
            }
        }
    }
    if (editing?.confirmDiscard == true) {
        val title = stringResource(R.string.zone_discard_title)
        val discard = stringResource(R.string.zone_discard)
        val keep = stringResource(R.string.zone_keep_editing)
        AlertDialog(
            onDismissRequest = model::keepEditing,
            title = { Text(title) },
            confirmButton = { TextButton(onClick = model::cancel) { Text(discard) } },
            dismissButton = { TextButton(onClick = model::keepEditing) { Text(keep) } },
        )
    }
}

@Composable
private fun ZoneForm(state: ZoneEditorState.Editing, model: ZoneSettingsViewModel) {
    var calculatorOpen by rememberSaveable { mutableStateOf(false) }
    var colorIndex by rememberSaveable { mutableIntStateOf(-1) }
    val scroll = rememberScrollState()
    val rowPositions = remember { List(ZONE_COUNT) { BringIntoViewRequester() } }
    val calculatorPosition = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    LaunchedEffect(state.showErrors) {
        if (state.showErrors) {
            val index = (0 until ZONE_COUNT).firstOrNull {
                state.draft.palette[it] == null || (it > 0 && state.draft.errors[it - 1] != null)
            }
            if (index != null) rowPositions[index].bringIntoView()
        }
    }
    BoxWithConstraints(Modifier.widthIn(max = 600.dp).fillMaxWidth().padding(horizontal = 16.dp)) {
        val columns = zoneTableColumns(maxWidth)
        Column(
            Modifier.fillMaxWidth().verticalScroll(scroll).padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column {
                    if (!columns.stacked) ZoneTableHeader(columns)
                    repeat(ZONE_COUNT) { index ->
                        ZoneTableRow(
                            Modifier.bringIntoViewRequester(rowPositions[index]), state, index, model, columns,
                            colorOpen = colorIndex == index || (state.showErrors && state.draft.palette[index] == null),
                            onColorClick = { colorIndex = if (colorIndex == index) -1 else index },
                        )
                    }
                }
            }
            Surface(modifier = Modifier.bringIntoViewRequester(calculatorPosition),
                shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable(enabled = !state.saving, role = Role.Button) {
                            calculatorOpen = !calculatorOpen
                        }.heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.zone_calculator), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        Icon(painterResource(R.drawable.ic_expand_more), contentDescription = null,
                            modifier = Modifier.rotate(if (calculatorOpen) 180f else 0f))
                    }
                    if (calculatorOpen) {
                        LaunchedEffect(Unit) { calculatorPosition.bringIntoView() }
                        ZoneCalculator(state, model) {
                            focus.clearFocus()
                            calculatorOpen = false
                            scope.launch { scroll.animateScrollTo(0) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoneCalculator(state: ZoneEditorState.Editing, model: ZoneSettingsViewModel, onApplied: () -> Unit) {
    val age = state.source == MaximumSource.AdultAge
    val suggestion = suggestZones(state.source, state.input)
    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (source in MaximumSource.entries) {
                FilterChip(
                    selected = state.source == source, onClick = { model.setSource(source) }, enabled = !state.saving,
                    label = { Text(stringResource(if (source == MaximumSource.Known) R.string.zone_known_max else R.string.zone_adult_age)) },
                )
            }
        }
        OutlinedTextField(
            value = state.input, onValueChange = model::setInput,
            modifier = Modifier.fillMaxWidth().keepVisibleWithKeyboard().testTag("maximum-input"),
            label = { Text(stringResource(if (age) R.string.zone_age_field else R.string.zone_max_field)) },
            isError = state.calculationError, enabled = !state.saving, singleLine = true,
            supportingText = if (state.calculationError) {
                { FieldError(if (age) R.string.zone_age_invalid else R.string.zone_max_invalid) }
            } else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        )
        if (age && suggestion != null) {
            Text(stringResource(R.string.zone_estimated_max, suggestion.maximumTenths / 10.0))
        }
        FilledTonalButton(onClick = {
            model.calculate()
            if (suggestion != null) onApplied()
        }, enabled = !state.saving) {
            Text(stringResource(R.string.zone_apply_calculation))
        }
    }
}

private val swatchNames = listOf(R.string.zone_gray, R.string.zone_green, R.string.zone_blue, R.string.zone_orange,
    R.string.zone_red, R.string.zone_black, R.string.zone_white)

@Composable
internal fun ZoneColorEditor(state: ZoneEditorState.Editing, index: Int, model: ZoneSettingsViewModel) {
    val selected = state.draft.palette[index]
    FlowRow {
        zoneSwatches.forEachIndexed { position, rgb ->
            val description = stringResource(R.string.zone_swatch, stringResource(swatchNames[position]), rgbText(rgb))
            Box(
                Modifier.size(48.dp).selectable(selected == rgb, enabled = !state.saving, role = Role.RadioButton,
                    onClick = { model.setColor(index, rgbText(rgb)) }).semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                ColorDot(rgb)
                if (selected == rgb) Text("✓", color = contrastingText(opaqueRgb(rgb)))
            }
        }
    }
    OutlinedTextField(
        value = state.draft.colors[index], onValueChange = { model.setColor(index, it) },
        modifier = Modifier.fillMaxWidth().keepVisibleWithKeyboard().testTag("rgb-${index + 1}"),
        label = { Text(stringResource(R.string.zone_color_label)) },
        isError = selected == null, enabled = !state.saving, singleLine = true,
        supportingText = if (selected == null) { { FieldError(R.string.zone_rgb_invalid) } } else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
    )
}

@Composable
internal fun ColorDot(rgb: Int?) {
    val fill = rgb?.let(::opaqueRgb) ?: MaterialTheme.colorScheme.surface
    Spacer(Modifier.size(24.dp).background(fill, CircleShape).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
}

@Composable
internal fun Modifier.keepVisibleWithKeyboard(): Modifier {
    val position = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    if (focused) {
        val keyboardHeight = WindowInsets.ime.getBottom(LocalDensity.current)
        LaunchedEffect(keyboardHeight) {
            if (keyboardHeight > 0) {
                withFrameNanos { }
                position.bringIntoView()
            }
        }
    }
    return bringIntoViewRequester(position).onFocusChanged { focused = it.hasFocus }
}

@Composable
private fun FieldError(message: Int) {
    Text(stringResource(message), color = MaterialTheme.colorScheme.error)
}
