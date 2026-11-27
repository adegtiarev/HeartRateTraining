package arg.adegtiarev.heartratetraining.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arg.adegtiarev.heartratetraining.data.WorkoutLibrary
import arg.adegtiarev.heartratetraining.sensor.SensorRepository
import arg.adegtiarev.heartratetraining.sensor.SensorState
import arg.adegtiarev.heartratetraining.workout.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class LiveWorkout(val ride: Workout, val sensor: SensorState, val activeMs: Long)

class WorkoutViewModel(
    private val recorder: WorkoutRecorder,
    sensors: SensorRepository,
    private val overlay: WorkoutOverlay,
    clock: () -> Long,
) : ViewModel() {
    val session = recorder.state
    val overlayState = overlay.state
    val live = combine(session, sensors.state, flow {
        while (true) {
            emit(clock())
            delay(250)
        }
    }) { state, sensor, now ->
        state.ride?.let {
            val active = if (it.phase == WorkoutPhase.Recording) it.advance(now - state.originMs).activeMs else it.activeMs
            LiveWorkout(it, sensor, active)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun pause(id: String) = recorder.pause(id)
    fun resume(id: String) = recorder.resume(id)
    fun finish(id: String) = recorder.finish(id)
    fun decide(id: String, save: Boolean) = recorder.decide(id, save)
    fun retry() = recorder.retryRecovery()
    fun hideOverlay(id: String) = overlay.hide(id)
}

sealed interface WorkoutLoad<out T> {
    data object Loading : WorkoutLoad<Nothing>
    data object Failed : WorkoutLoad<Nothing>
    data class Ready<T>(val value: T) : WorkoutLoad<T>
}

class HistoryViewModel(private val library: WorkoutLibrary) : ViewModel() {
    private val mutable = MutableStateFlow<WorkoutLoad<List<HistoryRide>>>(WorkoutLoad.Loading)
    val state = mutable.asStateFlow()
    private var read: Job? = null
    init { retry() }
    fun retry() {
        read?.cancel()
        read = viewModelScope.launch {
            library.history.catch { mutable.value = WorkoutLoad.Failed }
                .collect { mutable.value = WorkoutLoad.Ready(it) }
        }
    }
}

class WorkoutResultsViewModel(private val id: String, private val library: WorkoutLibrary) : ViewModel() {
    private val mutable = MutableStateFlow<WorkoutLoad<WorkoutResults>>(WorkoutLoad.Loading)
    val state = mutable.asStateFlow()
    init { retry() }
    fun retry() {
        viewModelScope.launch {
            mutable.value = WorkoutLoad.Loading
            mutable.value = try {
                library.results(id)?.let { WorkoutLoad.Ready(it) } ?: WorkoutLoad.Failed
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) { WorkoutLoad.Failed }
        }
    }
}
