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
import arg.adegtiarev.heartratetraining.zones.ZoneSettingsRepository
import kotlinx.serialization.Serializable

@Serializable
private data object HomeRoute : NavKey

@Serializable
private data object ZoneSettingsRoute : NavKey

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
) {
    val state by home.state.collectAsStateWithLifecycle()
    val backStack = rememberNavBackStack(HomeRoute)
    val zone = if (backStack.last() == HomeRoute) state.zone else null
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
                )
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
