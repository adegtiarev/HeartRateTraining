package arg.adegtiarev.heartratetraining.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import arg.adegtiarev.heartratetraining.zones.*
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update

private val Context.zonePreferences by preferencesDataStore("heart_rate_zones")

class DataStoreZoneSettings(private val store: DataStore<Preferences>) : ZoneSettingsRepository {
    constructor(context: Context) : this(context.applicationContext.zonePreferences)

    private val schema = intPreferencesKey("version")
    private val thresholds = (2..ZONE_COUNT).map { intPreferencesKey("zone_${it}_start") }
    private val colors = (1..ZONE_COUNT).map { intPreferencesKey("zone_${it}_rgb") }
    private val attempt = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    override val settings = attempt.flatMapLatest {
        store.data.map(::decode).onStart { emit(ZoneSettings.Loading) }.catch { error ->
            if (error !is IOException) throw error
            emit(ZoneSettings.ReadError)
        }
    }

    override fun retry() = attempt.update { it + 1 }

    override suspend fun save(configuration: ZoneConfiguration) {
        store.edit { prefs ->
            prefs[schema] = 1
            thresholds.forEachIndexed { index, key -> prefs[key] = configuration.thresholds[index] }
            colors.forEachIndexed { index, key -> prefs[key] = configuration.colors[index] }
        }
    }

    private fun decode(prefs: Preferences): ZoneSettings {
        val values = prefs.asMap()
        if (values.isEmpty()) return ZoneSettings.Unconfigured
        if (values[schema] != 1) return ZoneSettings.Invalid
        val starts = thresholds.map { values[it] as? Int ?: return ZoneSettings.Invalid }
        val palette = colors.map { values[it] as? Int ?: return ZoneSettings.Invalid }
        val config = ZoneConfiguration.create(starts, palette) ?: return ZoneSettings.Invalid
        return ZoneSettings.Saved(config)
    }
}
