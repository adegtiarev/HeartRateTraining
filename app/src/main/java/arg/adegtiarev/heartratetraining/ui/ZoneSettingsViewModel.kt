package arg.adegtiarev.heartratetraining.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arg.adegtiarev.heartratetraining.zones.*
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

val zoneSwatches = listOf(0x757575, 0x2E7D32, 0x1565C0, 0xEF6C00, 0xC62828, 0x000000, 0xFFFFFF)

fun rgbText(rgb: Int): String = "%06X".format(Locale.ROOT, rgb)

fun parseRgb(text: String): Int? {
    val digits = text.removePrefix("#")
    if (digits.length != 6 || !digits.all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }) return null
    return digits.toInt(16)
}

data class ZoneDraft(val thresholds: List<String>, val colors: List<String>) {
    val starts = thresholds.map(::positiveInteger)
    val errors = thresholdErrors(starts)
    val palette = colors.map(::parseRgb)

    fun configuration(): ZoneConfiguration? =
        ZoneConfiguration.create(starts.filterNotNull(), palette.filterNotNull())

    fun range(index: Int): Pair<Int, Int?>? {
        val first = if (index == 0) 0 else validStart(index - 1) ?: return null
        val last = if (index == ZONE_COUNT - 1) null else upperBound(index) ?: return null
        return first to last
    }

    private fun validStart(index: Int) = starts.getOrNull(index)?.takeIf { errors[index] == null }

    fun upperBound(index: Int): Int? = validStart(index)?.minus(1)

    companion object {
        fun from(config: ZoneConfiguration?) = ZoneDraft(
            config?.thresholds?.map(Int::toString) ?: List(ZONE_COUNT - 1) { "" },
            (config?.colors ?: zoneSwatches.take(ZONE_COUNT)).map(::rgbText),
        )
    }
}

sealed interface ZoneEditorState {
    data object Finished : ZoneEditorState
    data object Loading : ZoneEditorState
    data class LoadFailed(val canReplace: Boolean) : ZoneEditorState
    data class Editing(
        val initial: ZoneDraft,
        val draft: ZoneDraft = initial,
        val source: MaximumSource = MaximumSource.Known,
        val input: String = "",
        val calculationError: Boolean = false,
        val showErrors: Boolean = false,
        val saving: Boolean = false,
        val saveFailed: Boolean = false,
        val confirmDiscard: Boolean = false,
    ) : ZoneEditorState
}

class ZoneSettingsViewModel(private val repository: ZoneSettingsRepository) : ViewModel() {
    private val current = MutableStateFlow<ZoneEditorState>(ZoneEditorState.Loading)
    val state = current.asStateFlow()
    private var read: Job? = null

    init { load() }

    private fun load() {
        read?.cancel()
        current.value = ZoneEditorState.Loading
        read = viewModelScope.launch {
            current.value = when (val settings = repository.settings.first { it != ZoneSettings.Loading }) {
                is ZoneSettings.Saved -> ZoneEditorState.Editing(ZoneDraft.from(settings.configuration))
                ZoneSettings.Unconfigured -> ZoneEditorState.Editing(ZoneDraft.from(null))
                else -> ZoneEditorState.LoadFailed(canReplace = settings == ZoneSettings.Invalid)
            }
        }
    }

    fun retryRead() {
        repository.retry()
        load()
    }

    fun replaceInvalid() {
        if (current.value == ZoneEditorState.LoadFailed(canReplace = true)) {
            current.value = ZoneEditorState.Editing(ZoneDraft.from(null))
        }
    }

    private fun edit(change: (ZoneEditorState.Editing) -> ZoneEditorState.Editing) {
        val editing = current.value as? ZoneEditorState.Editing ?: return
        if (!editing.saving) current.value = change(editing)
    }

    fun setThreshold(index: Int, value: String) = edit {
        it.copy(draft = it.draft.copy(thresholds = it.draft.thresholds.replacing(index, value)), saveFailed = false)
    }

    fun setColor(index: Int, value: String) = edit {
        it.copy(draft = it.draft.copy(colors = it.draft.colors.replacing(index, value)), saveFailed = false)
    }

    fun setSource(source: MaximumSource) = edit {
        if (source == it.source) it else it.copy(source = source, input = "", calculationError = false)
    }

    fun setInput(input: String) = edit { it.copy(input = input, calculationError = false) }

    fun calculate() = edit {
        val suggestion = suggestZones(it.source, it.input)
        if (suggestion == null) it.copy(calculationError = true)
        else it.copy(draft = it.draft.copy(thresholds = suggestion.thresholds.map(Int::toString)),
            calculationError = false, saveFailed = false)
    }

    fun save() {
        val editing = current.value as? ZoneEditorState.Editing ?: return
        if (editing.saving) return
        val configuration = editing.draft.configuration()
        if (configuration == null) {
            current.value = editing.copy(showErrors = true)
            return
        }
        current.value = editing.copy(saving = true, saveFailed = false)
        viewModelScope.launch {
            try {
                repository.save(configuration)
                current.value = ZoneEditorState.Finished
            } catch (_: IOException) {
                current.value = editing.copy(saveFailed = true)
            }
        }
    }

    fun back() {
        val editing = current.value as? ZoneEditorState.Editing
        if (editing != null && (editing.draft != editing.initial || editing.input.isNotEmpty())) {
            edit { it.copy(confirmDiscard = true) }
        } else cancel()
    }

    fun keepEditing() = edit { it.copy(confirmDiscard = false) }

    fun cancel() {
        if ((current.value as? ZoneEditorState.Editing)?.saving == true) return
        read?.cancel()
        current.value = ZoneEditorState.Finished
    }
}

private fun List<String>.replacing(index: Int, value: String) =
    mapIndexed { position, previous -> if (position == index) value else previous }
