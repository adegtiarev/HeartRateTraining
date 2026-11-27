package arg.adegtiarev.heartratetraining.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import arg.adegtiarev.heartratetraining.R
import arg.adegtiarev.heartratetraining.data.WorkoutLibrary
import arg.adegtiarev.heartratetraining.sensor.SensorAccess
import arg.adegtiarev.heartratetraining.workout.WorkoutError
import arg.adegtiarev.heartratetraining.workout.WorkoutPhase
import arg.adegtiarev.heartratetraining.workout.reading
import arg.adegtiarev.heartratetraining.zones.ZoneConfiguration
import arg.adegtiarev.heartratetraining.zones.ZoneSettings
import arg.adegtiarev.heartratetraining.zones.ZoneSettingsRepository
import kotlinx.serialization.Serializable

@Serializable
private data object HomeRoute : NavKey

@Serializable
private data object ZoneSettingsRoute : NavKey

@Serializable private data object WorkoutRoute : NavKey
@Serializable private data object HistoryRoute : NavKey
@Serializable private data class ResultsRoute(val id: String, val pending: Boolean) : NavKey

@Composable
fun HeartRateScreens(
    home: HomeViewModel,
    zones: ZoneSettingsRepository,
    legacyLocation: Boolean,
    openPermissionSettings: Boolean,
    onPermission: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onEnableLocation: () -> Unit,
    onBackgroundChanged: (Color) -> Unit,
    workouts: WorkoutViewModel,
    library: WorkoutLibrary,
    onStartWorkout: (ZoneConfiguration?) -> Unit,
    onShowOverlay: (String) -> Unit,
    onOverlaySettings: (String) -> Unit,
    notificationVisit: Int = 0,
    explainNotifications: Boolean = false,
) {
    val state by home.state.collectAsStateWithLifecycle()
    val session by workouts.session.collectAsStateWithLifecycle()
    val ride = session.ride
    val backStack = rememberNavBackStack(HomeRoute)
    fun back() { if (backStack.size > 1) backStack.removeLastOrNull() }
    fun openRide() {
        if (ride == null) return
        while (backStack.size > 1) backStack.removeLastOrNull()
        backStack.add(if (ride.phase == WorkoutPhase.Review) ResultsRoute(ride.id, true) else WorkoutRoute)
    }
    val review = ride?.phase == WorkoutPhase.Review
    LaunchedEffect(session.loading, ride?.id, review, notificationVisit) {
        if (session.loading) return@LaunchedEffect
        val top = backStack.lastOrNull()
        if (ride != null) {
            if (top == HomeRoute || top == WorkoutRoute || notificationVisit > 0) openRide()
        } else if (top == WorkoutRoute || (top is ResultsRoute && top.pending)) {
            back()
            if (top is ResultsRoute && top.id == session.savedId) backStack.add(HistoryRoute)
        }
    }
    val zone = when (backStack.lastOrNull()) {
        HomeRoute -> state.zone
        WorkoutRoute -> ride?.reading(state.sensor.sample)?.zone
        else -> null
    }
    val background = zone?.let { opaqueRgb(it.rgb) } ?: MaterialTheme.colorScheme.background
    LaunchedEffect(background) { onBackgroundChanged(background) }
    NavDisplay(
        backStack = backStack,
        onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
        entryProvider = entryProvider {
            entry<HomeRoute> {
                HomeScreen(
                    home = state, legacyLocation = legacyLocation, openPermissionSettings = openPermissionSettings,
                    onPermission = onPermission, onEnableBluetooth = onEnableBluetooth, onEnableLocation = onEnableLocation,
                    onFind = home::findStraps, onRetry = home::retry, onDisconnect = home::disconnect, onSelect = home::select,
                    onZoneSettings = { if (backStack.last() == HomeRoute) backStack.add(ZoneSettingsRoute) },
                    onRetryZones = home::retryZones,
                    onHistory = { if (backStack.lastOrNull() == HomeRoute) backStack.add(HistoryRoute) },
                    onStart = {
                        if (ride != null) openRide()
                        else when (state.sensor.access) {
                            SensorAccess.PermissionNeeded -> onPermission()
                            SensorAccess.BluetoothOff -> onEnableBluetooth()
                            SensorAccess.LocationOff -> onEnableLocation()
                            SensorAccess.Ready -> onStartWorkout((state.settings as? ZoneSettings.Saved)?.configuration)
                            else -> Unit
                        }
                    },
                    workoutAction = when {
                        review -> R.string.workout_results
                        ride != null -> R.string.workout_return
                        else -> R.string.workout_start
                    },
                    startEnabled = ride != null || (!session.loading && !session.starting &&
                        session.error != WorkoutError.Storage && (state.settings is ZoneSettings.Saved ||
                        state.settings == ZoneSettings.Unconfigured) && state.sensor.access !in
                        setOf(SensorAccess.Unavailable, SensorAccess.Checking)),
                    workoutError = when (session.error) {
                        WorkoutError.Service -> R.string.workout_service_error
                        WorkoutError.Storage -> R.string.workout_storage_error
                        null -> null
                    },
                    onRetryWorkout = workouts::retry,
                    explainNotifications = explainNotifications && ride == null,
                )
            }
            entry<WorkoutRoute> {
                val live by workouts.live.collectAsStateWithLifecycle()
                val overlay by workouts.overlayState.collectAsStateWithLifecycle()
                live?.let { current ->
                    val id = current.ride.id
                    WorkoutScreen(current, legacyLocation, { workouts.pause(id) }, { workouts.resume(id) },
                        { workouts.finish(id) }, home::retry, ::back,
                        overlay = overlay?.takeIf { it.rideId == id }?.phase,
                        onShowOverlay = { onShowOverlay(id) }, onHideOverlay = { workouts.hideOverlay(id) },
                        onOverlaySettings = { onOverlaySettings(id) })
                }
            }
            entry<HistoryRoute> {
                val model = viewModel { HistoryViewModel(library) }
                val history by model.state.collectAsStateWithLifecycle()
                HistoryScreen(history, { id ->
                    if (backStack.lastOrNull() == HistoryRoute) backStack.add(ResultsRoute(id, false))
                }, ::back, model::retry)
            }
            entry<ResultsRoute> { route ->
                val model = viewModel { WorkoutResultsViewModel(route.id, library) }
                val result by model.state.collectAsStateWithLifecycle()
                WorkoutResultsScreen(result, route.pending, ::back, model::retry,
                    { workouts.decide(route.id, true) }, { workouts.decide(route.id, false) },
                    session.error == WorkoutError.Storage)
            }
            entry<ZoneSettingsRoute> {
                val model = viewModel { ZoneSettingsViewModel(zones) }
                val editor by model.state.collectAsStateWithLifecycle()
                LaunchedEffect(editor == ZoneEditorState.Finished) {
                    if (editor == ZoneEditorState.Finished && backStack.lastOrNull() == ZoneSettingsRoute) {
                        backStack.removeLastOrNull()
                    }
                }
                ZoneSettingsScreen(editor, model)
            }
        },
    )
}
