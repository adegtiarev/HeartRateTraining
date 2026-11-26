package arg.adegtiarev.heartratetraining.zones

import arg.adegtiarev.heartratetraining.ui.ZoneEditorState
import arg.adegtiarev.heartratetraining.ui.ZoneSettingsViewModel
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ZoneSettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val saved = ZoneConfiguration.create(listOf(100, 120, 145, 170), listOf(1, 2, 0, 0xFFFFFF, 2))!!
    private var fail = false
    private var writes = 0
    private val gate = CompletableDeferred<Unit>()
    private val repository = object : ZoneSettingsRepository {
        override val settings = MutableStateFlow<ZoneSettings>(ZoneSettings.Saved(saved))
        override fun retry() = Unit
        override suspend fun save(configuration: ZoneConfiguration) {
            writes++
            gate.await()
            if (fail) throw IOException("fixture")
            settings.value = ZoneSettings.Saved(configuration)
        }
    }
    private lateinit var model: ZoneSettingsViewModel
    private val editor get() = model.state.value as ZoneEditorState.Editing

    @Before fun start() { Dispatchers.setMain(dispatcher) }
    @After fun stop() { Dispatchers.resetMain() }

    @Test fun recalculationAndFailedSavePreserveColorsAndRetryCommitsOnce() = runTest {
        model = ZoneSettingsViewModel(repository)
        runCurrent()
        model.setColor(2, "1234ab")
        model.setSource(MaximumSource.AdultAge)
        model.setInput("35")
        assertEquals(saved.thresholds, editor.draft.starts)
        model.calculate()
        assertEquals(listOf(110, 128, 147, 165), editor.draft.starts)
        val expected = editor.draft.configuration()!!
        assertEquals(listOf(1, 2, 0x1234AB, 0xFFFFFF, 2), expected.colors)
        fail = true
        model.save()
        model.save()
        model.cancel()
        runCurrent()
        assertTrue(editor.saving)
        gate.complete(Unit)
        runCurrent()
        assertEquals(1, writes)
        assertTrue(editor.saveFailed)
        assertEquals(expected, editor.draft.configuration())
        assertEquals(ZoneSettings.Saved(saved), repository.settings.value)
        fail = false
        model.save()
        runCurrent()
        assertEquals(ZoneEditorState.Finished, model.state.value)
        assertEquals(ZoneSettings.Saved(expected), repository.settings.value)
    }

    @Test fun invalidOrCancelledEditsNeverWriteAndReopeningStartsFromDisk() = runTest {
        model = ZoneSettingsViewModel(repository)
        runCurrent()
        model.setThreshold(0, "")
        model.save()
        assertTrue(editor.showErrors)
        model.back()
        assertTrue(editor.confirmDiscard)
        model.keepEditing()
        assertEquals("", editor.draft.thresholds[0])
        model.cancel()
        model = ZoneSettingsViewModel(repository)
        runCurrent()
        assertEquals(saved, editor.draft.configuration())
        assertEquals(0, writes)
    }

    @Test fun failedReadsStayDistinctFromFirstUseAndRetryCanRecover() = runTest {
        repository.settings.value = ZoneSettings.ReadError
        model = ZoneSettingsViewModel(repository)
        runCurrent()
        assertEquals(ZoneEditorState.LoadFailed(false), model.state.value)
        repository.settings.value = ZoneSettings.Invalid
        model.retryRead()
        runCurrent()
        assertEquals(ZoneEditorState.LoadFailed(true), model.state.value)
        model.replaceInvalid()
        assertNull(editor.draft.configuration())
        assertEquals(0, writes)
        model.cancel()
        repository.settings.value = ZoneSettings.Unconfigured
        model = ZoneSettingsViewModel(repository)
        runCurrent()
        assertTrue(editor.draft.thresholds.all(String::isEmpty))
        assertEquals(5, editor.draft.palette.size)
    }
}
