package arg.adegtiarev.heartratetraining.bluetooth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import arg.adegtiarev.heartratetraining.sensor.RememberedStrapStore
import arg.adegtiarev.heartratetraining.sensor.Strap
import kotlinx.coroutines.flow.first

private val Context.sensorPreferences by preferencesDataStore("sensor")

class DataStoreStrapStore(context: Context) : RememberedStrapStore {
    private val preferences = context.sensorPreferences
    private val id = stringPreferencesKey("device_id")
    private val name = stringPreferencesKey("display_name")

    override suspend fun read(): Strap? {
        val saved = preferences.data.first()
        return saved[id]?.let { Strap(it, saved[name]) }
    }

    override suspend fun write(strap: Strap) {
        preferences.edit { saved ->
            saved[id] = strap.id
            if (strap.name == null) saved.remove(name) else saved[name] = strap.name
        }
    }
}
