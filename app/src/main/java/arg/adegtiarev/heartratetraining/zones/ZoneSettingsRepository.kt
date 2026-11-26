package arg.adegtiarev.heartratetraining.zones

import kotlinx.coroutines.flow.Flow

sealed interface ZoneSettings {
    data object Loading : ZoneSettings
    data object Unconfigured : ZoneSettings
    data class Saved(val configuration: ZoneConfiguration) : ZoneSettings
    data object Invalid : ZoneSettings
    data object ReadError : ZoneSettings
}

interface ZoneSettingsRepository {
    val settings: Flow<ZoneSettings>
    fun retry()
    suspend fun save(configuration: ZoneConfiguration)
}
